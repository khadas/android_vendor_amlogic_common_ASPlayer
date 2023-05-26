package com.amlogic.asplayer.core;


import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
import android.view.Surface;


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
    public static final String PARAM_FRAME_ADVANCE = "vendor.tunerhal.passthrough.frame-advance";

    public static final Bundle PARAMS_TRICK_NONE;
    public static final Bundle PARAMS_TRICK_BY_SEEK;
    public static final Bundle PARAMS_REQUEST_FRAME;

    static {
        PARAMS_TRICK_NONE = new Bundle();
        PARAMS_TRICK_NONE.putInt(PARAM_TRICK_MODE, TRICK_MODE_NONE);
        PARAMS_TRICK_NONE.putInt(PARAM_TRICK_SPEED, 1000);

        PARAMS_TRICK_BY_SEEK = new Bundle();
        PARAMS_TRICK_BY_SEEK.putInt(PARAM_TRICK_MODE, TRICK_MODE_BY_SEEK);
        PARAMS_TRICK_BY_SEEK.putInt(PARAM_TRICK_SPEED, 0);

        PARAMS_REQUEST_FRAME = new Bundle();
        PARAMS_REQUEST_FRAME.putInt(PARAM_FRAME_ADVANCE, 1);
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
//                TvLog.i("VideoOutputPathV3-%d media format event: %d", mId, presentationTimeUs);
                handleMediaFormatEvent(presentationTimeUs);
                return;
            }

//            if (DEBUG) TvLog.i("VideoOutputPathV3-%d pts: %d", mId, presentationTimeUs);
            if (mPlaybackMode != ASPlayerConfig.PLAYBACK_MODE_PASSTHROUGH) {
                if (mTimestampKeeper.isEmpty()) {
                    return;
                }
                mTimestampKeeper.removeTimestamp(presentationTimeUs);
            }
//            ASPlayerLog.i("VideoOutputPathV3-%d onFrameRendered pts: %d, nanoTime: %d",
//                    mId, presentationTimeUs, nanoTime);
            notifyFrameDisplayed(nanoTime / 1000);
            mLastRenderedTimeUs = presentationTimeUs;
            mFirstFrameDisplayed = true;
        }

        private void handleMediaFormatEvent(long eventData) {
            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
            buffer.putLong(eventData);
            if (DEBUG) {
                for (int i = 0; i < Long.BYTES; i++) {
                    ASPlayerLog.i("VideoOutputPathV3-%d [%d] %02x", mId, i, buffer.array()[i]);
                }
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
        if (DEBUG) ASPlayerLog.i("VideoOutputPathV3-%d configure", mId);
        if (isConfigured()) {
            ASPlayerLog.w("VideoOutputPathV3-%d mediacodec is not null as it should be", mId);
            return false;
        }
        if (waitForConfigurationRetry()) {
            return false;
        }
        if (mSurface == null) {
            ASPlayerLog.i("VideoOutputPathV3-%d surface is null", mId);
            return false;
        }

        MediaFormat format = mMediaFormat;
        if (format == null) {
            if (DEBUG) ASPlayerLog.i("VideoOutputPathV3-%d configure failed, video format is null", mId);
            return false;
        }

        mMimeType = format.getString(MediaFormat.KEY_MIME);
        mSecurePlayback = format.containsFeature(FEATURE_SecurePlayback) &&
                format.getFeatureEnabled(FEATURE_SecurePlayback);

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
                ASPlayerLog.i("VideoOutputPathV3-%d mTunneledPlayback", mId);
                // get video size from input
                mVideoWidth = format.getInteger(MediaFormat.KEY_WIDTH);
                mVideoHeight = format.getInteger(MediaFormat.KEY_HEIGHT);

                // make a copy
                format = MediaFormat.createVideoFormat(mMimeType, mVideoWidth, mVideoHeight);

                onSetVideoFormat(format);

                ASPlayerLog.i("VideoOutputPathV3-%d video filter id: %d, avSyncHwId: %d", mId, mTrackFilterId, mAvSyncHwId);

                mediaCodec.setOnFrameRenderedListener(mMediaCodecOnFrameCallback, getHandler());
            }
            mediaCodec.setCallback(mMediaCodecCallback, getHandler());

            Surface surface = mSurface;
            format.setInteger(FccWorkMode.MEDIA_FORMAT_KEY_FCC_WORKMODE, FccWorkMode.MEDIA_FORMAT_FCC_WORKMODE_NORMAL);

            ASPlayerLog.i("VideoOutputPathV3-%d mime_type:%s, codec:%s, format:%s", mId, mMimeType, mediaCodec.getName(), format);

            if (mPlaybackMode == ASPlayerConfig.PLAYBACK_MODE_ES_SECURE) {
                ASPlayerLog.i("VideoOutputPathV3-%d media codec configure PLAYBACK_MODE_ES_SECURE, surface: %s", mId, surface);
                mediaCodec.configure(format, surface, null,
                        MediaCodec.CONFIGURE_FLAG_USE_BLOCK_MODEL);
            } else {
                ASPlayerLog.i("VideoOutputPathV3-%d media codec configure normal, surface: %s", mId, surface);
                mediaCodec.configure(format, surface, null, 0);
            }

            ASPlayerLog.i("VideoOutputPathV3-%d media codec start, mediacodec: %s", mId, mediaCodec);
            mediaCodec.start();
            mMediaCodec = mediaCodec;
            ASPlayerLog.i("VideoOutputPathV3-%d codec configured: %s", mId, mMediaCodec);

            mFirstFrameDisplayed = false;
            setConfigured(true);
            configured = true;
        } catch (Exception exception) {
            ASPlayerLog.w("VideoOutputPathV3-%d can't create mediacodec error:%s", mId, exception.getMessage());
            if (mediaCodec != null) {
                mediaCodec.release();
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
//        if (DEBUG) ASPlayerLog.i("VideoOutputPathV3-%d push input buffer", mId);
        if (!isConfigured() && !configure()) {
            if (DEBUG) ASPlayerLog.i("VideoOutputPathV3-%d not configured", mId);
            return;
        }

        notifyBufferPushed();
    }

    @Override
    long getDisplayPositionUs() {
        return mLastRenderedTimeUs;
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

        ASPlayerLog.i("VideoOutputPathV3-%d track filter id: %d, avSyncHwId: %d", mId, mTrackFilterId, mAvSyncHwId);
        if (mTrackFilterId >= 0) {
            format.setInteger(KEY_VIDEO_FILTER_ID, mTrackFilterId);
        }

        if (mAvSyncHwId >= 0) {
            format.setInteger(KEY_AV_SYNC_HW_ID, mAvSyncHwId);
        }
    }
}
