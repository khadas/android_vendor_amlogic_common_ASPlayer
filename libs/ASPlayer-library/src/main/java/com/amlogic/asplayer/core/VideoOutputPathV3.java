package com.amlogic.asplayer.core;


import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
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
import static com.amlogic.asplayer.core.VideoMediaFormatEvent.EVENT_TYPE_AFD;
import static com.amlogic.asplayer.core.VideoMediaFormatEvent.EVENT_TYPE_ASPECT_RATIO;
import static com.amlogic.asplayer.core.VideoMediaFormatEvent.EVENT_TYPE_FRAME_RATES;
import static com.amlogic.asplayer.core.VideoMediaFormatEvent.EVENT_TYPE_RESOLUTION;
import static com.amlogic.asplayer.core.VideoMediaFormatEvent.KEY_EVENT_FLAGS;


class VideoOutputPathV3 extends VideoOutputPath {
    private static final boolean DEBUG = true;

    public static final String KEY_VIDEO_FILTER_ID = "vendor.tunerhal.video-filter-id";
    public static final String KEY_AV_SYNC_HW_ID = "vendor.tunerhal." + KEY_HARDWARE_AV_SYNC_ID;

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
            if (mPlaybackMode != ASPlayerConfig.PLAYBACK_MODE_PASSTHROUGH) {
                if (mTimestampKeeper.isEmpty()) {
                    return;
                }
                mTimestampKeeper.removeTimestamp(presentationTimeUs);
            }
            notifyFrameDisplayed(presentationTimeUs, nanoTime / 1000);
            if (!mFirstFrameDisplayed) {
                ASPlayerLog.i("%s [KPI-FCC] onFrameRendered", getTag());
            }
            mLastRenderedTimeUs = presentationTimeUs;
            mFirstFrameDisplayed = true;
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

                mediaCodec.setOnFrameRenderedListener(mMediaCodecOnFrameCallback, getHandler());
            }
            mediaCodec.setCallback(mMediaCodecCallback, getHandler());

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
                ASPlayerLog.i("%s configure mediacodec %s, workMode: %d, surface: %s, cost: %d ms",
                        getTag(), mediaCodec, mTargetWorkMode, surfaceTag, getCostTime(startTime));
            } else {
                ASPlayerLog.i("%s mediacodec configure before, normal, surface: %s, format: %s",
                        getTag(), surfaceTag, format);
                startTime = System.nanoTime();
                mediaCodec.configure(format, surface, null, 0);
                ASPlayerLog.i("%s [KPI-FCC] configure end mediacodec %s, workMode: %d, surface: %s, cost: %d ms",
                        getTag(), mediaCodec, mTargetWorkMode, surfaceTag, getCostTime(startTime));
            }

            ASPlayerLog.i("%s [KPI-FCC] configure mediacodec start before, mediacodec: %s", getTag(), mediaCodec);
            startTime = System.nanoTime();
            mediaCodec.start();
            long endTime = System.nanoTime();
            ASPlayerLog.i("%s [KPI-FCC] configure mediacodec start end, workMode: %d, cost: %d ms",
                    getTag(), mTargetWorkMode, getCostTime(startTime, endTime));
            ASPlayerLog.i("%s [KPI-FCC] configure mediacodec totalTime: %d ms, mediacodec: %s",
                    getTag(), getCostTime(beginTime, endTime), mediaCodec);
            mMediaCodec = mediaCodec;
            mMediaCodecStarted = true;

            mFirstFrameDisplayed = false;
            setConfigured(true);
            mLastWorkMode = mTargetWorkMode;
            ASPlayerLog.i("%s change last WorkMode to %d", getTag(), mLastWorkMode);
            configured = true;
            setRequestChangeWorkMode(false);
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
        mLastRenderedTimeUs = 0;
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
        );

        ASPlayerLog.i("%s track filter id: 0x%016x, avSyncHwId: 0x%x", getTag(), mTrackFilterId, mAvSyncHwId);
        if (mTrackFilterId >= 0) {
            format.setInteger(KEY_VIDEO_FILTER_ID, mTrackFilterId);
        }

        if (mAvSyncHwId >= 0) {
            format.setInteger(KEY_AV_SYNC_HW_ID, mAvSyncHwId);
        }
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
}