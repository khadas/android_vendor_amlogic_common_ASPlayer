package com.amlogic.asplayer.core;


import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Surface;

import com.amlogic.asplayer.api.TransitionSettings;
import com.amlogic.asplayer.api.WorkMode;
import com.amlogic.asplayer.core.utils.StringUtils;

import java.nio.ByteBuffer;

import static android.media.MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback;
import static android.media.MediaFormat.KEY_HARDWARE_AV_SYNC_ID;
import static com.amlogic.asplayer.core.MediaContainerExtractor.INVALID_AV_SYNC_HW_ID;
import static com.amlogic.asplayer.core.MediaContainerExtractor.INVALID_FILTER_ID;
import static com.amlogic.asplayer.core.VideoMediaFormatEvent.EVENT_FLAGS_AFD;
import static com.amlogic.asplayer.core.VideoMediaFormatEvent.EVENT_FLAGS_FRAME_RATES;
import static com.amlogic.asplayer.core.VideoMediaFormatEvent.EVENT_FLAGS_PIXEL_ASPECT_RATIO;
import static com.amlogic.asplayer.core.VideoMediaFormatEvent.EVENT_FLAGS_RESOLUTION;
import static com.amlogic.asplayer.core.VideoMediaFormatEvent.EVENT_FLAGS_VIDEO_VF_TYPE;
import static com.amlogic.asplayer.core.VideoMediaFormatEvent.EVENT_TYPE_AFD;
import static com.amlogic.asplayer.core.VideoMediaFormatEvent.EVENT_TYPE_ASPECT_RATIO;
import static com.amlogic.asplayer.core.VideoMediaFormatEvent.EVENT_TYPE_FRAME_RATES;
import static com.amlogic.asplayer.core.VideoMediaFormatEvent.EVENT_TYPE_RESOLUTION;
import static com.amlogic.asplayer.core.VideoMediaFormatEvent.EVENT_TYPE_VIDEO_VF_TYPE;
import static com.amlogic.asplayer.core.VideoMediaFormatEvent.KEY_EVENT_FLAGS;


class VideoOutputPathV3 extends VideoOutputPath {
    private static final boolean DEBUG = true;

    public static final String KEY_VIDEO_FILTER_ID = "vendor.tunerhal.video-filter-id";
    public static final String KEY_AV_SYNC_HW_ID = "vendor.tunerhal." + KEY_HARDWARE_AV_SYNC_ID;

    public static final String KEY_INSTANCE_NO = "vendor.tunerhal.passthrough.instance-no";

    /*
        trick modes
     */
    public static final int TRICK_MODE_NONE = 0;
    public static final int TRICK_MODE_SMOOTH = 1;
    public static final int TRICK_MODE_BY_SEEK = 2;

    public static final String PARAM_TRICK_MODE = "vendor.tunerhal.passthrough.trick-mode";
    public static final String PARAM_TRICK_SPEED = "vendor.tunerhal.passthrough.trick-speed";

    public static final String PARAM_TRANSITION_BEFORE =
            "vendor.tunerhal.passthrough.transition-mode-before";
    public static final String PARAM_TRANSITION_AFTER =
            "vendor.tunerhal.passthrough.transition-mode-after";

    private static final int CHECK_DATA_LOSS_PERIOD = 100; // 100 millisecond
    private static final int DATA_LOSS_DURATION_MILLISECOND = 2 * 1000; // 2 second

    public static final Bundle PARAMS_TRICK_NONE;
    public static final Bundle PARAMS_TRICK_BY_SEEK;

    public static final Bundle PARAMS_TRANSITION_MODE_BEFORE;

    static {
        PARAMS_TRICK_NONE = new Bundle();
        PARAMS_TRICK_NONE.putInt(PARAM_TRICK_MODE, TRICK_MODE_NONE);
        PARAMS_TRICK_NONE.putInt(PARAM_TRICK_SPEED, 1000);

        PARAMS_TRICK_BY_SEEK = new Bundle();
        PARAMS_TRICK_BY_SEEK.putInt(PARAM_TRICK_MODE, TRICK_MODE_BY_SEEK);
        PARAMS_TRICK_BY_SEEK.putInt(PARAM_TRICK_SPEED, 0);

        PARAMS_TRANSITION_MODE_BEFORE = new Bundle();
        PARAMS_TRANSITION_MODE_BEFORE.putInt(PARAM_TRANSITION_BEFORE,
                TransitionSettings.TransitionModeBefore.BLACK);
    }

    int mPlaybackMode;
    long mLastRenderedTimeUs;

    private CheckDataLossRunnable mCheckDataLossRunnable;
    private long mLastFrameTimestampMillisecond = -1;
    private boolean mDataLossReported = false;
    private long mLastDataLossReportTimestamp = 0;

    int mTrackFilterId = INVALID_FILTER_ID;
    int mAvSyncHwId = INVALID_AV_SYNC_HW_ID;

    private class VideoMediaCodecOnFrameCallback implements MediaCodec.OnFrameRenderedListener {
        @Override
        public void onFrameRendered(MediaCodec codec, long presentationTimeUs, long nanoTime) {
            if (mMediaCodec != codec) {
                return;
            }

            if (VideoMediaFormatEvent.isEventData(presentationTimeUs)) {
                ASPlayerLog.i("%s media format event: %d", getTag(), presentationTimeUs);
                handleMediaFormatEvent(presentationTimeUs);
                return;
            }

//            if (DEBUG) ASPlayerLog.i("%s onFrameRendered pts: %d", getTag(), presentationTimeUs);
            notifyFrameDisplayed(presentationTimeUs, nanoTime / 1000);
            if (!mFirstFrameDisplayed) {
                ASPlayerLog.i("%s [KPI-FCC] onFrameRendered pts: %d, nanoTime: %d",
                        getTag(), presentationTimeUs, nanoTime);
            }
            mLastRenderedTimeUs = presentationTimeUs;
            mLastFrameTimestampMillisecond = System.nanoTime() / 1000000;
            mFirstFrameDisplayed = true;

            if (mDataLossReported) {
                ASPlayerLog.i("%s decoder data resume", getTag());
                notifyDecoderDataResume();
                mDataLossReported = false;
            }
        }

        private void handleMediaFormatEvent(long eventData) {
            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
            buffer.putLong(eventData);
            if (DEBUG) {
                StringUtils.dumpBytes(getTag(), buffer.array(), 0, Long.BYTES);
            }

            VideoMediaFormatEvent event = VideoMediaFormatEvent.parse(buffer.array());
            if (event != null) {
                switch (event.type) {
                    case EVENT_TYPE_RESOLUTION:
                        handleResolutionEvent((VideoMediaFormatEvent.ResolutionEvent) event);
                        break;
                    case EVENT_TYPE_ASPECT_RATIO:
                        handleAspectRatioEvent((VideoMediaFormatEvent.AspectRatioEvent) event);
                        break;
                    case EVENT_TYPE_AFD:
                        handleAfdEvent((VideoMediaFormatEvent.AfdEvent) event);
                        break;
                    case EVENT_TYPE_FRAME_RATES:
                        handleFrameRateEvent((VideoMediaFormatEvent.FrameRateEvent) event);
                        break;
                    case EVENT_TYPE_VIDEO_VF_TYPE:
                        handleVideoVFTypeEvent((VideoMediaFormatEvent.VideoVFTypeEvent) event);
                        break;
                    default:
                        break;
                }
            }
        }

        private void handleResolutionEvent(VideoMediaFormatEvent.ResolutionEvent event) {
            if (event != null) {
                updateVideoResolutionInfo(event.width, event.height);
            }
        }

        private void handleFrameRateEvent(VideoMediaFormatEvent.FrameRateEvent event) {
            if (event != null) {
                updateVideoFrameRateInfo(event.frameRate);
            }
        }

        private void handleAspectRatioEvent(VideoMediaFormatEvent.AspectRatioEvent event) {
            if (event != null) {
                updateVideoAspectRatioInfo(event.aspectRatio);
            }
        }

        private void handleAfdEvent(VideoMediaFormatEvent.AfdEvent afdEvent) {
            if (afdEvent != null) {
                updateAfdInfo(afdEvent.activeFormat);
            }
        }

        private void handleVideoVFTypeEvent(VideoMediaFormatEvent.VideoVFTypeEvent vfTypeEvent) {
            if (vfTypeEvent != null) {
                updateVFTypeInfo(vfTypeEvent.vfType);
            }
        }
    }

    VideoOutputPathV3(int id, int playbackMode) {
        super(id);
        mMediaCodecOnFrameCallback = new VideoMediaCodecOnFrameCallback();
        mPlaybackMode = playbackMode;
    }

    @Override
    public String getName() {
        return "VideoOutputPathV3";
    }

    void setTrackFilterId(int filterId) {
        this.mTrackFilterId = filterId;
    }

    void setAvSyncHwId(int avSyncHwId) {
        this.mAvSyncHwId = avSyncHwId;
    }

    @Override
    public boolean configure() {
        if (DEBUG) ASPlayerLog.i("%s configure", getTag());
        if (isConfigured()) {
            ASPlayerLog.w("%s mediacodec is not null as it should be", getTag());
            return false;
        }
        if (waitForConfigurationRetry()) {
            return false;
        }
        if (mSurface == null && mTargetWorkMode == WorkMode.NORMAL) {
            ASPlayerLog.i("%s configure failed, surface is null, normal mode", getTag());
            return false;
        } else if (mDummySurface == null && mTargetWorkMode == WorkMode.CACHING_ONLY) {
            ASPlayerLog.i("%s configure failed, dummy surface is null, cache mode", getTag());
            return false;
        }

        if (mMediaCodec != null && mChangeWorkMode && switchMediaCodecWorkMode()) {
            setConfigured(true);
            return true;
        }

        if (mMediaCodec != null) {
            ASPlayerLog.w("%s release mediacodec: %s", getTag(), mMediaCodec);
            releaseMediaCodec();
        }

        MediaFormat format = mMediaFormat;
        if (format == null) {
            if (DEBUG) ASPlayerLog.i("%s configure failed, video format is null", getTag());
            return false;
        }

        mMimeType = format.getString(MediaFormat.KEY_MIME);
        mSecurePlayback = format.containsFeature(FEATURE_SecurePlayback) &&
                format.getFeatureEnabled(FEATURE_SecurePlayback);

        if (TextUtils.isEmpty(mMimeType)) {
            if (DEBUG) ASPlayerLog.i("%s configure failed, mimeType is null", getTag());
            return false;
        }

        ASPlayerLog.i("%s mSecurePlayback: %b", getTag(), mSecurePlayback);

        mNbDecodedFrames = 0;
        mFirstFrameDisplayed = false;
        mInputBufferQueue = null;

        MediaCodec mediaCodec = null;
        boolean configured = false;
        try {
            if (mMediaCodec != null) {
                mediaCodec = mMediaCodec;
            } else {
                mediaCodec = MediaCodecUtils.findMediaCodec(format, mTunneledPlayback, mSecurePlayback);
            }

            if (mediaCodec == null) {
                ASPlayerLog.i("%s can not create mediacodec", getTag());
                return false;
            }

            if (mTunneledPlayback) {
                ASPlayerLog.i("%s mTunneledPlayback", getTag());
                // get video size from input
                mVideoWidth = format.getInteger(MediaFormat.KEY_WIDTH);
                mVideoHeight = format.getInteger(MediaFormat.KEY_HEIGHT);

                // make a copy
                format = MediaFormat.createVideoFormat(mMimeType, mVideoWidth, mVideoHeight);

                onSetVideoFormat(format);

                ASPlayerLog.i("%s video filter id: 0x%016x, avSyncHwId: 0x%x",
                        getTag(), mTrackFilterId, mAvSyncHwId);

                mediaCodec.setOnFrameRenderedListener(mMediaCodecOnFrameCallback, mHandler);
            }
            mediaCodec.setCallback(mMediaCodecCallback, mHandler);

            Surface surface = mSurface;
            if (mTargetWorkMode == WorkMode.NORMAL) {
                format.setInteger(FccWorkMode.MEDIA_FORMAT_KEY_FCC_WORKMODE, FccWorkMode.MEDIA_FORMAT_FCC_WORKMODE_NORMAL);
                surface = mSurface;
            } else if (mTargetWorkMode == WorkMode.CACHING_ONLY) {
                format.setInteger(FccWorkMode.MEDIA_FORMAT_KEY_FCC_WORKMODE, FccWorkMode.MEDIA_FORMAT_FCC_WORKMODE_CACHE);
                surface = mDummySurface;
            }

            String surfaceTag = (mDummySurface != null && surface == mDummySurface) ? "dummy surface" : "normal surface";

            ASPlayerLog.i("%s codec:%s, mime_type:%s, format:%s",
                    getTag(), mediaCodec.getName(), mMimeType, format);

            long beginTime = System.nanoTime();
            long startTime = beginTime;
            if (mPlaybackMode == ASPlayerConfig.PLAYBACK_MODE_ES_SECURE) {
                ASPlayerLog.i("%s mediacodec configure PLAYBACK_MODE_ES_SECURE, surface: %s, mediacodec: %s",
                        getTag(), surface, mediaCodec);
                startTime = System.nanoTime();
                mediaCodec.configure(format, surface, null,
                        MediaCodec.CONFIGURE_FLAG_USE_BLOCK_MODEL);
                ASPlayerLog.i("%s configure end, workMode: %d, surface: %s, cost: %d ms, mediacodec: %s",
                        getTag(), mTargetWorkMode, surfaceTag, getCostTime(startTime), mediaCodec);
            } else {
                ASPlayerLog.i("%s mediacodec configure before, normal, surface: %s, format: %s",
                        getTag(), surfaceTag, format);
                startTime = System.nanoTime();
                mediaCodec.configure(format, surface, null, 0);
                ASPlayerLog.i("%s [KPI-FCC] configure end workMode: %d, surface: %s, cost: %d ms, mediacodec: %s",
                        getTag(), mTargetWorkMode, surfaceTag, getCostTime(startTime), mediaCodec);
            }

            ASPlayerLog.i("%s [KPI-FCC] configure mediacodec start before, mediacodec: %s", getTag(), mediaCodec);
            startTime = System.nanoTime();
            mMediaCodec = mediaCodec;
            startMediaCodec();
            long endTime = System.nanoTime();
            ASPlayerLog.i("%s [KPI-FCC] configure mediacodec start end, workMode: %d, cost: %d ms",
                    getTag(), mTargetWorkMode, getCostTime(startTime, endTime));
            ASPlayerLog.i("%s [KPI-FCC] configure mediacodec totalTime: %d ms, mediacodec: %s",
                    getTag(), getCostTime(beginTime, endTime), mediaCodec);

            mFirstFrameDisplayed = false;
            setConfigured(true);
            mLastWorkMode = mTargetWorkMode;
            ASPlayerLog.i("%s change last WorkMode to %d", getTag(), mLastWorkMode);
            configured = true;
            setRequestChangeWorkMode(false);
            notifyDecoderInitCompleted();
        } catch (Exception exception) {
            ASPlayerLog.w("%s can't create mediacodec error:%s", getTag(), exception.getMessage());
            if (mediaCodec != null) {
                releaseMediaCodec(mediaCodec);
            }
            mMediaCodec = null;
            handleConfigurationError(exception.toString());
            exception.printStackTrace();
        }

        return configured;
    }

    @Override
    public void reset() {
        super.reset();

        stopCheckDataLoss();

        mLastRenderedTimeUs = 0;
        mLastFrameTimestampMillisecond = -1;

        mDataLossReported = false;
        mLastDataLossReportTimestamp = -1;
    }

    @Override
    public void release() {
        super.release();

        mTrackFilterId = INVALID_FILTER_ID;
        mAvSyncHwId = INVALID_AV_SYNC_HW_ID;
    }

    @Override
    protected void pushInputBuffer() {
        if (!isConfigured() && !configure()) {
            if (DEBUG) ASPlayerLog.i("%s not configured", getTag());
            return;
        }

        notifyBufferPushed();
    }

    @Override
    long getDisplayPositionUs() {
        return mLastRenderedTimeUs;
    }

    @Override
    void setTrickMode(int trickMode) {
        super.setTrickMode(trickMode);
    }

    @Override
    void setTrickModeSpeed(double speed) {
        mTrickModeSpeed = speed;
    }

    @Override
    protected void onSetVideoFormat(MediaFormat format) {
        super.onSetVideoFormat(format);

        format.setLong(KEY_EVENT_FLAGS, EVENT_FLAGS_RESOLUTION
                | EVENT_FLAGS_PIXEL_ASPECT_RATIO
                | EVENT_FLAGS_AFD
                | EVENT_FLAGS_FRAME_RATES
                | EVENT_FLAGS_VIDEO_VF_TYPE
        );

        ASPlayerLog.i("%s track filter id: 0x%016x, avSyncHwId: 0x%x, scrambled: %b",
                getTag(), mTrackFilterId, mAvSyncHwId, mSecurePlayback);
        if (mTrackFilterId >= 0) {
            format.setInteger(KEY_VIDEO_FILTER_ID, mTrackFilterId);
        }

        if (mAvSyncHwId >= 0) {
            format.setInteger(KEY_AV_SYNC_HW_ID, mAvSyncHwId);
        }

        if (mSecurePlayback) {
            format.setFeatureEnabled(FEATURE_SecurePlayback, true);
        }

        // set player instance no
        format.setInteger(KEY_INSTANCE_NO, mId);
    }

    @Override
    void setTransitionModeBefore(int transitionModeBefore) {
        super.setTransitionModeBefore(transitionModeBefore);
        ASPlayerLog.i("%s setTransitionModeBefore %d", getTag(), transitionModeBefore);

        PARAMS_TRANSITION_MODE_BEFORE.putInt(PARAM_TRANSITION_BEFORE, transitionModeBefore);

        if (mMediaCodec != null) {
            mMediaCodec.setParameters(PARAMS_TRANSITION_MODE_BEFORE);
            mRequestTransitionModeBefore = false;
        }
    }

    @Override
    protected void handleSetTransitionModeBefore() {
        super.handleSetTransitionModeBefore();

        mMediaCodec.setParameters(PARAMS_TRANSITION_MODE_BEFORE);
    }

    @Override
    public void setWorkMode(int workMode) {
        if (workMode == mLastWorkMode) {
            return;
        }

        ASPlayerLog.i("%s setWorkMode: %d, last mode: %d", getTag(), workMode, mLastWorkMode);
        if (workMode == WorkMode.CACHING_ONLY) {
            mLastRenderedTimeUs = 0;
        }

        super.setWorkMode(workMode);
    }

    private class CheckDataLossRunnable implements Runnable {
        @Override
        public void run() {
            if (mMediaCodec == null || !mMediaCodecStarted) {
                return;
            }

            boolean isDataLoss = false;

            long currentTime = System.nanoTime() / 1000000;
            if (mLastFrameTimestampMillisecond == -1) {
                // no frame rendered
                ASPlayerLog.i("%s check data loss, no last pts", getTag());
                long mediaCodecStartTime =
                        mMediaCodecStartTimeMillisecond >= 0 ? mMediaCodecStartTimeMillisecond : 0;
                long delayTime = currentTime - mediaCodecStartTime;
                if (delayTime >= DATA_LOSS_DURATION_MILLISECOND) {
                    isDataLoss = true;
                }
            } else if (mLastFrameTimestampMillisecond > 0
                && currentTime >= (mLastFrameTimestampMillisecond + DATA_LOSS_DURATION_MILLISECOND)) {
                ASPlayerLog.i("%s check data loss, pts gap: %d", getTag(), currentTime - mLastFrameTimestampMillisecond);
                isDataLoss = true;
            }

            if (isDataLoss) {
                ASPlayerLog.i("%s check data loss, data loss found", getTag());
                boolean reportDataLoss = !mDataLossReported;
                if (currentTime >= (mLastDataLossReportTimestamp + DATA_LOSS_DURATION_MILLISECOND)) {
                    reportDataLoss = true;
                }

                if (reportDataLoss) {
                    notifyDecoderDataLoss();
                    mDataLossReported = true;
                    mLastDataLossReportTimestamp = currentTime;
                }
            }

            if (mHandler != null) {
                if (mHandler.hasCallbacks(this)) {
                    mHandler.removeCallbacks(this);
                }
                mHandler.postDelayed(this, CHECK_DATA_LOSS_PERIOD);
            }
        }
    }

    @Override
    protected void onMediaCodecStarted() {
        super.onMediaCodecStarted();

        startCheckDataLoss();
    }

    private void startCheckDataLoss() {
        if (mHandler == null) {
            return;
        }

        if (mCheckDataLossRunnable != null) {
            mHandler.removeCallbacks(mCheckDataLossRunnable);
        } else {
            mCheckDataLossRunnable = new CheckDataLossRunnable();
        }
        mHandler.postDelayed(mCheckDataLossRunnable, CHECK_DATA_LOSS_PERIOD);
    }

    private void stopCheckDataLoss() {
        if (mCheckDataLossRunnable != null) {
            if (mHandler != null) {
                mHandler.removeCallbacks(mCheckDataLossRunnable);
            }
            mCheckDataLossRunnable = null;
        }
    }

    @Override
    protected void onMediaCodecStopped() {
        super.onMediaCodecStopped();

        stopCheckDataLoss();
    }

    @Override
    protected void onMediaCodecReleased() {
        super.onMediaCodecReleased();
    }
}