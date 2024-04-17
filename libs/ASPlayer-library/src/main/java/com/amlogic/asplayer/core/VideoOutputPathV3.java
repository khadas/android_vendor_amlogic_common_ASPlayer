package com.amlogic.asplayer.core;


import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Surface;

import com.amlogic.asplayer.api.PlaybackControl.TransitionModeBefore;
import com.amlogic.asplayer.api.PlaybackControl.TransitionModeAfter;
import com.amlogic.asplayer.api.PlaybackControl.VideoMute;
import com.amlogic.asplayer.api.VideoParams;
import com.amlogic.asplayer.api.VideoTrickMode;
import com.amlogic.asplayer.api.WorkMode;
import com.amlogic.asplayer.core.utils.DataLossChecker;
import com.amlogic.asplayer.core.utils.StringUtils;

import java.nio.ByteBuffer;

import static android.media.MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback;
import static android.media.MediaFormat.KEY_HARDWARE_AV_SYNC_ID;
import static com.amlogic.asplayer.core.Constant.INVALID_AV_SYNC_ID;
import static com.amlogic.asplayer.core.Constant.INVALID_FILTER_ID;
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
import static com.amlogic.asplayer.core.VideoPassthroughParameters.ScreenColorMode.SCREEN_COLOR_MODE_ONCE_TRANSITION;
import static com.amlogic.asplayer.core.VideoPassthroughParameters.ScreenColorMode.SCREEN_COLOR_MODE_ONCE_SOLID;


class VideoOutputPathV3 extends VideoOutputPath {
    private static final boolean DEBUG = false;

    public static final String KEY_VIDEO_FILTER_ID = "vendor.tunerhal.video-filter-id";
    public static final String KEY_AV_SYNC_HW_ID = "vendor.tunerhal." + KEY_HARDWARE_AV_SYNC_ID;

    public static final String KEY_INSTANCE_NO = "vendor.tunerhal.passthrough.instance-no";

    /*
        trick modes
     */
    public static final int TRICK_MODE_NONE = VideoTrickMode.NONE;
    public static final int TRICK_MODE_SMOOTH = VideoTrickMode.TRICK_MODE_SMOOTH;
    public static final int TRICK_MODE_BY_SEEK = VideoTrickMode.TRICK_MODE_BY_SEEK;
    public static final int TRICK_MODE_IONLY = VideoTrickMode.TRICK_MODE_IONLY;

    public static final String KEY_TRICK_MODE = "vendor.tunerhal.passthrough.trick-mode";
    public static final String KEY_TRICK_SPEED = "vendor.tunerhal.passthrough.trick-speed";

    public static final String KEY_VIDEO_MUTE = "vendor.tunerhal.passthrough.mute";
    public static final String KEY_TRANSITION_BEFORE =
            "vendor.tunerhal.passthrough.transition-mode-before";
    public static final String KEY_TRANSITION_AFTER =
            "vendor.tunerhal.passthrough.transition-mode-after";

    /**
     * int value (0~900) scaled by 1000 from float
     *
     * 0: show first image before sync and show video after sync (default)
     * 500: show video from first image with 0.5x before sync and show 1x video after sync
     * higher value takes longer for av sync
     */
    public static final String KEY_TRANSITION_PREROLL_RATE =
            "vendor.tunerhal.passthrough.transition-preroll-rate";

    /**
     * int, maximum a/v time difference in ms to start preroll.
     * This value limits the max time of preroll duration.
     *
     * 0: no limit, preroll starts directly from first image
     * n: show first image and preroll starts from when a/v diff < n
     * ex) preroll-rate: 900, preroll-av-tolerance: 0
     * If first a/v time difference: 1000ms => it takes 10 seconds for preroll (0.9x)
     * ex) preroll-rate: 900, preroll-av-tolerance: 500ms
     * If first a/v time difference: 1000ms => show first image for 500ms and it takes 5 seconds for
     * preroll (0.9x)
     */
    public static final String KEY_TRANSITION_PREROLL_AV_TOLERANCE =
            "vendor.tunerhal.passthrough.transition-preroll-av-tolerance";

    public static final String KEY_SCREEN_COLOR_MODE = "vendor.tunerhal.passthrough.screencolor-mode";
    public static final String KEY_SCREEN_COLOR = "vendor.tunerhal.passthrough.screencolor-color";

    public static final String KEY_VIDEO_PLAYBACK_MODE = "vendor.tunerhal.passthrough.playback.status";
    public static final String KEY_VIDEO_PLAYBACK_SPEED = "vendor.playback.speed";

    private static final int CHECK_DATA_LOSS_PERIOD = 100; // 100 millisecond
    private static final int DATA_LOSS_DURATION_MILLISECOND = 2 * 1000; // 2 second

    private static final int PRE_ROLL_RATE_SCALE = 1000;

    public static final Bundle PARAMS_TRICK_NONE;
    public static final Bundle PARAMS_TRICK_BY_SEEK;

    public static final Bundle PARAMS_TRANSITION_MODE_BEFORE;
    public static final Bundle PARAMS_TRANSITION_MODE_AFTER;
    public static final Bundle PARAMS_TRANSITION_PREROLL_RATE;
    public static final Bundle PARAMS_TRANSITION_PREROLL_AV_TOLERANCE;
    public static final Bundle PARAMS_VIDEO_MUTE;

    static {
        PARAMS_TRICK_NONE = new Bundle();
        PARAMS_TRICK_NONE.putInt(KEY_TRICK_MODE, TRICK_MODE_NONE);
        PARAMS_TRICK_NONE.putInt(KEY_TRICK_SPEED, 1000);

        PARAMS_TRICK_BY_SEEK = new Bundle();
        PARAMS_TRICK_BY_SEEK.putInt(KEY_TRICK_MODE, TRICK_MODE_BY_SEEK);
        PARAMS_TRICK_BY_SEEK.putInt(KEY_TRICK_SPEED, 0);

        PARAMS_TRANSITION_MODE_BEFORE = new Bundle();
        PARAMS_TRANSITION_MODE_BEFORE.putInt(KEY_TRANSITION_BEFORE,
                TransitionModeBefore.BLACK);

        PARAMS_TRANSITION_MODE_AFTER = new Bundle();
        PARAMS_TRANSITION_MODE_AFTER.putInt(KEY_TRANSITION_AFTER,
                TransitionModeAfter.PREROLL_FROM_FIRST_IMAGE);

        PARAMS_TRANSITION_PREROLL_RATE = new Bundle();
        PARAMS_TRANSITION_PREROLL_RATE.putInt(KEY_TRANSITION_PREROLL_RATE, 0);

        PARAMS_TRANSITION_PREROLL_AV_TOLERANCE = new Bundle();
        PARAMS_TRANSITION_PREROLL_AV_TOLERANCE.putInt(KEY_TRANSITION_PREROLL_AV_TOLERANCE, 0);

        PARAMS_VIDEO_MUTE = new Bundle();
        PARAMS_VIDEO_MUTE.putInt(KEY_VIDEO_MUTE, VideoMute.UN_MUTE);
    }

    private int mPlaybackMode;
    private long mLastRenderedTimeUs;

    private DataLossChecker mDataLossChecker;
    private VideoDataLossListener mDataLossListener;

    private int mTrackFilterId = INVALID_FILTER_ID;
    private int mAvSyncHwId = INVALID_AV_SYNC_ID;

    private boolean mAudioOnly = false;

    private class MediaCodecCallback extends MediaCodec.Callback {

        @Override
        public void onInputBufferAvailable(MediaCodec codec, int index) {

        }

        @Override
        public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {

        }

        @Override
        public void onError(MediaCodec codec, MediaCodec.CodecException e) {
            if (codec != mMediaCodec)
                return;

            ASPlayerLog.w("%s error=%s", getTag(), e.getMessage());
            setError(e.getMessage());
            setConfigurationError(e.toString());
            setConfigured(false);
        }

        @Override
        public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {

        }
    }

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

            final long renderTime = nanoTime / 1000;
            if (!mFirstFrameDisplayed) {
                ASPlayerLog.i("%s [KPI-FCC] onFrameRendered pts: %d, 0x%x, pts90: %d, nanoTime: %d, 0x%x",
                        getTag(), presentationTimeUs, presentationTimeUs, presentationTimeUs * 90 / 1000,
                        nanoTime, nanoTime);
            }
            if (DEBUG) {
                ASPlayerLog.i("%s onFrameRendered pts: %d, 0x%x, pts90: %d, renderTime: %d, 0x%x",
                        getTag(), presentationTimeUs, presentationTimeUs, presentationTimeUs * 90 / 1000,
                        renderTime, renderTime);
            }
            notifyFrameDisplayed(presentationTimeUs, renderTime);
            mLastRenderedTimeUs = presentationTimeUs;
            mFirstFrameDisplayed = true;

            if (mDataLossChecker != null) {
                mDataLossChecker.onFrameArrived(renderTime);
            }
        }

        private void handleMediaFormatEvent(long eventData) {
            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
            buffer.putLong(eventData);
            StringUtils.dumpBytes(getTag(), buffer.array(), 0, Long.BYTES);

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
        mMediaCodecCallback = new MediaCodecCallback();
        mMediaCodecOnFrameCallback = new VideoMediaCodecOnFrameCallback();
        mPlaybackMode = playbackMode;
    }

    @Override
    public String getName() {
        return "VideoOutputPathV3";
    }

    @Override
    void setVideoParams(VideoParams videoParams) {
        super.setVideoParams(videoParams);
        if (videoParams != null) {
            mTrackFilterId = videoParams.getTrackFilterId();
            mAvSyncHwId = videoParams.getAvSyncHwId();
            mAudioOnly = !videoParams.getHasVideo();
        } else {
            mTrackFilterId = INVALID_FILTER_ID;
            mAvSyncHwId = INVALID_AV_SYNC_ID;
            mAudioOnly = true;
        }
    }

    @Override
    boolean hasVideoParams() {
        return mVideoParams != null && mVideoParams.getTrackFilterId() > 0;
    }

    @Override
    public boolean configure() {
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

        VideoParams videoParams = mVideoParams;
        if (videoParams == null) {
            ASPlayerLog.i("%s configure failed, video params is null", getTag());
            return false;
        }

        String mimeType = videoParams.getMimeType();
        int width = videoParams.getWidth();
        int height = videoParams.getHeight();

        mSecurePlayback = videoParams.isScrambled();
        if (!mSecurePlayback) {
            MediaFormat mediaFormat = videoParams.getMediaFormat();
            if (mediaFormat != null) {
                mSecurePlayback = mediaFormat.containsFeature(FEATURE_SecurePlayback) &&
                        mediaFormat.getFeatureEnabled(FEATURE_SecurePlayback);
            }
        }

        if (TextUtils.isEmpty(mimeType)) {
            ASPlayerLog.i("%s configure failed, mimeType is null", getTag());
            return false;
        }

        // if no video and no mimeType, we need set valid mimeType here to create MediaCodec
        // set mimeType to video/mpeg2 here
        if (!videoParams.getHasVideo() && mimeType.equalsIgnoreCase("video/unknown")) {
            mimeType = MediaFormat.MIMETYPE_VIDEO_MPEG2;
            if (width < 0) {
                width = 1280;
            }
            if (height < 0) {
                height = 720;
            }
        }

        ASPlayerLog.i("%s mimeType: %s, mSecurePlayback: %b", getTag(), mimeType, mSecurePlayback);

        mFirstFrameDisplayed = false;

        MediaCodec mediaCodec = null;
        MediaFormat format = MediaFormat.createVideoFormat(mimeType, width, height);
        boolean configured = false;

        try {
            if (mMediaCodec != null) {
                mediaCodec = mMediaCodec;
            } else {
                if (DolbyVisionUtils.isDolbyVisionMimeType(mimeType)) {
                    int codecType = DolbyVisionUtils.getCodecType(mimeType);
                    ASPlayerLog.i("%s DolbyVision codecType: %d", getTag(), codecType);
                    mediaCodec = MediaCodecUtils.findDolbyVisionMediaCodec(codecType,
                            mTunneledPlayback, mSecurePlayback);

                    if (mediaCodec != null) {
                        ASPlayerLog.i("%s find MediaCodec for DolbyVision, codec: %s", getTag(), mediaCodec);
                    } else {
                        mediaCodec = MediaCodecUtils.findMediaCodec(
                                MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION, mTunneledPlayback, mSecurePlayback);
                        ASPlayerLog.i("%s find MediaCodec for mimeType DolbyVision, codec: %s",
                                getTag(), mediaCodec);
                    }
                    DolbyVisionUtils.setDolbyVisionMimeType(codecType, format);
                } else {
                    mediaCodec = MediaCodecUtils.findMediaCodec(mimeType, mTunneledPlayback, mSecurePlayback);
                }
            }

            if (mediaCodec == null) {
                ASPlayerLog.i("%s can not create mediacodec", getTag());
                return false;
            }

            if (mTunneledPlayback) {
                ASPlayerLog.i("%s mTunneledPlayback", getTag());
                onSetVideoFormat(format);

                // set transition parameters
                setTransitionParametersForConfigure(format);

                if (mAudioOnly) {
                    // need configure and start mediacodec for set surface color
                    // set fake filter id
                    format.setInteger(KEY_VIDEO_FILTER_ID, -1);
                    format.setInteger(KEY_AV_SYNC_HW_ID, -1);
                    ASPlayerLog.i("%s configure video filter id: -1, avSync id: -1", getTag());
                } else {
                    ASPlayerLog.i("%s configure video filter id: 0x%016x, avSyncHwId: 0x%x",
                            getTag(), mTrackFilterId, mAvSyncHwId);
                }

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
                    getTag(), mediaCodec.getName(), mimeType, format);

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

            if (mSolidScreenColor != null) {
                handleSetScreenColorOnce(mSolidScreenColor.intValue());
                mSolidScreenColor = null;
            }

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
        stopCheckDataLoss();

        super.reset();

        mLastRenderedTimeUs = 0;

        mSolidScreenColor = null;
    }

    @Override
    public void release() {
        super.release();

        mTrackFilterId = INVALID_FILTER_ID;
        mAvSyncHwId = INVALID_AV_SYNC_ID;

        mAudioOnly = false;

        mSolidScreenColor = null;
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

    private void setTransitionParametersForConfigure(MediaFormat mediaFormat) {
        if (mediaFormat == null) {
            return;
        }

        if (mTransitionModeAfterSet) {
            mediaFormat.setInteger(KEY_TRANSITION_AFTER, mTransitionModeAfter);
        }

        if (mTransitionPreRollRate >= 0) {
            mediaFormat.setInteger(KEY_TRANSITION_PREROLL_RATE, getPrerollRateForMediaFormat(mTransitionPreRollRate));
        }
        if (mTransitionPreRollAVTolerance >= 0) {
            mediaFormat.setInteger(KEY_TRANSITION_PREROLL_AV_TOLERANCE, mTransitionPreRollAVTolerance);
        }
    }

    @Override
    void setTransitionModeBefore(int transitionModeBefore) {
        ASPlayerLog.i("%s setTransitionModeBefore mode: %d", getTag(), transitionModeBefore);
        super.setTransitionModeBefore(transitionModeBefore);

        PARAMS_TRANSITION_MODE_BEFORE.putInt(KEY_TRANSITION_BEFORE, transitionModeBefore);

        if (mMediaCodec != null) {
            handleSetTransitionModeBefore();
        }
    }

    @Override
    protected void handleSetTransitionModeBefore() {
        super.handleSetTransitionModeBefore();

        if (mMediaCodec != null) {
            ASPlayerLog.i("%s handleSetTransitionModeBefore mode: %d", getTag(), mTransitionModeBefore);
            mMediaCodec.setParameters(PARAMS_TRANSITION_MODE_BEFORE);
        }
    }

    @Override
    void setTransitionModeAfter(int transitionModeAfter) {
        ASPlayerLog.i("%s setTransitionModeAfter mode: %d", getTag(), transitionModeAfter);
        super.setTransitionModeAfter(transitionModeAfter);

        PARAMS_TRANSITION_MODE_AFTER.putInt(KEY_TRANSITION_AFTER, transitionModeAfter);

//        if (mMediaCodec != null) {
//            handleSetTransitionModeAfter();
//        }
    }

    @Override
    protected void handleSetTransitionModeAfter() {
        super.handleSetTransitionModeAfter();

        if (mMediaCodec != null) {
            ASPlayerLog.i("%s handleSetTransitionModeAfter mode: %d", getTag(), mTransitionModeAfter);
            mMediaCodec.setParameters(PARAMS_TRANSITION_MODE_AFTER);
        }
    }

    @Override
    void setTransitionScreenColor(int screenColor) {
        ASPlayerLog.i("%s setTransitionScreenColor color: %d", getTag(), screenColor);
        super.setTransitionScreenColor(screenColor);

        if (mMediaCodec != null && mTransitionModeBefore != TransitionModeBefore.LAST_IMAGE) {
            handleSetTransitionScreenColor(screenColor);
            mTransitionScreenColor = null;
        }
    }

    @Override
    protected void handleSetTransitionScreenColor(int screenColor) {
        if (mMediaCodec != null) {
            ASPlayerLog.i("%s handleSetTransitionScreenColor color: %d", getTag(), mTransitionScreenColor);
            Bundle params = new Bundle();
            params.putInt(KEY_SCREEN_COLOR_MODE, SCREEN_COLOR_MODE_ONCE_TRANSITION);
            params.putInt(KEY_SCREEN_COLOR, screenColor);
            setMediaCodecParameters(mMediaCodec, params);
        }
    }

    private static int getPrerollRateForMediaFormat(float prerollRate) {
        return (int)(prerollRate * PRE_ROLL_RATE_SCALE);
    }

    @Override
    void setTransitionPreRollRate(float rate) {
        ASPlayerLog.i("%s setTransitionPreRollRate rate: %.3f", getTag(), rate);
        super.setTransitionPreRollRate(rate);

        PARAMS_TRANSITION_PREROLL_RATE.putInt(KEY_TRANSITION_PREROLL_RATE, getPrerollRateForMediaFormat(rate));

        if (mMediaCodec != null) {
            handleSetTransitionPreRollRate();
        }
    }

    @Override
    protected void handleSetTransitionPreRollRate() {
        super.handleSetTransitionPreRollRate();

        if (mMediaCodec != null) {
            ASPlayerLog.i("%s handleSetTransitionPreRollRate rate: %.3f", getTag(), mTransitionPreRollRate);
            mMediaCodec.setParameters(PARAMS_TRANSITION_PREROLL_RATE);
        }
    }

    @Override
    void setTransitionPreRollAVTolerance(int milliSecond) {
        ASPlayerLog.i("%s setTransitionPreRollAVTolerance milliSecond: %d", getTag(), milliSecond);
        super.setTransitionPreRollAVTolerance(milliSecond);

        PARAMS_TRANSITION_PREROLL_AV_TOLERANCE.putInt(KEY_TRANSITION_PREROLL_AV_TOLERANCE, milliSecond);

        if (mMediaCodec != null) {
            handleSetTransitionPreRollAVTolerance();
        }
    }

    @Override
    protected void handleSetTransitionPreRollAVTolerance() {
        super.handleSetTransitionPreRollAVTolerance();

        if (mMediaCodec != null) {
            ASPlayerLog.i("%s handleSetTransitionPreRollAVTolerance time: %d", getTag(), mTransitionPreRollAVTolerance);
            mMediaCodec.setParameters(PARAMS_TRANSITION_PREROLL_AV_TOLERANCE);
        }
    }

    @Override
    void setVideoMute(int mute) {
        ASPlayerLog.i("%s setVideoMute mute: %d", getTag(), mute);
        super.setVideoMute(mute);

        PARAMS_VIDEO_MUTE.putInt(KEY_VIDEO_MUTE, mute);

        if (mMediaCodec != null) {
            handleSetVideoMute();
            mRequestVideoMute = false;
        }
    }

    @Override
    protected void handleSetVideoMute() {
        super.handleSetVideoMute();

        if (mMediaCodec != null) {
            int mute = PARAMS_VIDEO_MUTE.getInt(KEY_VIDEO_MUTE);
            ASPlayerLog.i("%s handleSetVideoMute mute: %d", getTag(), mute);
            setMediaCodecParameters(mMediaCodec, PARAMS_VIDEO_MUTE);
        }
    }

    @Override
    protected void setScreenColorOnce(int screenColor) {
        super.setScreenColorOnce(screenColor);
        ASPlayerLog.i("%s setScreenColorOnce screenColor: %d", getTag(), screenColor);
        if (mMediaCodec != null) {
            mSolidScreenColor = Integer.valueOf(screenColor);
            handleSetScreenColorOnce(mSolidScreenColor.intValue());
        } else {
            ASPlayerLog.w("%s setScreenColorOnce screenColor: %d, mediacodec is null",
                    getTag(), screenColor);
        }
    }

    private boolean handleSetScreenColorOnce(int screenColor) {
        if (mMediaCodec != null) {
            ASPlayerLog.i("%s handleSetScreenColorOnce screenColor: %d", getTag(), screenColor);
            boolean success = handleSetScreenColor(SCREEN_COLOR_MODE_ONCE_SOLID, screenColor);
            if (success) {
                mSolidScreenColor = null;
            }

            return success;
        } else {
            ASPlayerLog.w("%s handleSetScreenColorOnce screenColor: %d failed, mediacodec is null",
                    getTag(), screenColor);
            return false;
        }
    }

    protected boolean handleSetScreenColor(int mode, int screenColor) {
        if (mMediaCodec != null) {
            ASPlayerLog.i("%s handleSetScreenColor mode: %d, screenColor: %d", getTag(),
                    mode, screenColor);
            Bundle params = new Bundle();
            params.putInt(KEY_SCREEN_COLOR_MODE, mode);
            params.putInt(KEY_SCREEN_COLOR, screenColor);
            setMediaCodecParameters(mMediaCodec, params);
            return true;
        }
        return false;
    }

    private void setMediaCodecParameters(MediaCodec mediaCodec, Bundle params) {
        if (mediaCodec == null || params == null) {
            ASPlayerLog.i("%s setMediaCodecParameters failed, invalid param", getTag());
            return;
        }

        mediaCodec.setParameters(params);
    }

    @Override
    protected void setPlaybackStatus(int playbackStatus) {
        ASPlayerLog.i("%s playbackStatus mode: %d", getTag(), playbackStatus);
        super.setPlaybackStatus(playbackStatus);

        if (mMediaCodec != null) {
            handleSetPlaybackStatus(playbackStatus);
            mVideoTargetPlaybackStatus = -1;
        }
    }

    @Override
    protected void handleSetPlaybackStatus(int playbackStatus) {
        super.handleSetPlaybackStatus(playbackStatus);

        if (playbackStatus == -1) {
            return;
        }

        if (mMediaCodec != null) {
            ASPlayerLog.i("%s handleSetPlaybackStatus status: %d", getTag(), playbackStatus);
            Bundle parameter = new Bundle();
            parameter.putInt(KEY_VIDEO_PLAYBACK_MODE, playbackStatus);
            mMediaCodec.setParameters(parameter);
        }
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

    @Override
    protected void onMediaCodecStarted() {
        super.onMediaCodecStarted();

        startCheckDataLoss();
    }

    private void startCheckDataLoss() {
        if (mHandler == null || mAudioOnly) {
            return;
        }

        if (mDataLossChecker != null) {
            stopCheckDataLoss();
        }

        mDataLossListener = new VideoDataLossListener();
        mDataLossChecker = new DataLossChecker(mHandler);
        mDataLossChecker.start(mDataLossListener, getTag(),
                CHECK_DATA_LOSS_PERIOD, DATA_LOSS_DURATION_MILLISECOND);
    }

    private void stopCheckDataLoss() {
        if (mDataLossChecker != null) {
            mDataLossChecker.stop();
            mDataLossChecker.release();
            mDataLossChecker = null;
        }
        mDataLossListener = null;
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

    private class VideoDataLossListener implements DataLossChecker.DataLossListener {

        @Override
        public void onDataLossFound() {
            notifyDecoderDataLoss();
        }

        @Override
        public void onDataResumeFound() {
            notifyDecoderDataResume();
        }
    }
}