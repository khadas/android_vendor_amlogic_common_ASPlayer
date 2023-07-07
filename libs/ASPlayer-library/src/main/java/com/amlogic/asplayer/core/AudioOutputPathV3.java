package com.amlogic.asplayer.core;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;


import com.amlogic.asplayer.api.AudioParams;
import com.amlogic.asplayer.api.PIPMode;
import com.amlogic.asplayer.core.encapsulation.Metadata;

import static android.media.MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback;
import static com.amlogic.asplayer.core.MediaContainerExtractor.INVALID_AV_SYNC_HW_ID;
import static com.amlogic.asplayer.core.MediaContainerExtractor.INVALID_FILTER_ID;


class AudioOutputPathV3 extends AudioOutputPath {
    private static final boolean DEBUG = false;

    private float mMixLevel;

    private Metadata.TunerMetadata mTunerMetadataMain;
    private Metadata.TunerMetadata mTunerMetadataSub;
    private Metadata.PlacementMetadata mPlacementMetadata;
    protected boolean mSecurePlayback = false;

    private int mAudioSubTrackFilterId = INVALID_FILTER_ID;

    AudioOutputPathV3(int id) {
        super(id);
        mTunerMetadataMain =
                new Metadata.TunerMetadata(Metadata.TunerMetadata.TYPE_MAIN,
                        AudioFormat.ENCODING_DEFAULT, 0);
        mTunerMetadataSub =
                new Metadata.TunerMetadata(Metadata.TunerMetadata.TYPE_SUPPLEMENTARY,
                        AudioFormat.ENCODING_DEFAULT, 0);
        mPlacementMetadata =
                new Metadata.PlacementMetadata(Metadata.PlacementMetadata.PLACEMENT_NORMAL);
        mPlacementMetadata.placement = Metadata.PlacementMetadata.PLACEMENT_NORMAL;
    }

    @Override
    void setAudioParams(AudioParams audioParams) {
        super.setAudioParams(audioParams);
    }

    @Override
    void switchAudioTrack(AudioParams audioParams) {
        super.switchAudioTrack(audioParams);

        if (audioParams != null && audioParams.getTrackFilterId() != INVALID_FILTER_ID) {
            setTrackWithTunerMetaData(audioParams);
        }
    }

    public void setAudioSubTrackFilterId(int subTrackFilterId) {
        mAudioSubTrackFilterId = subTrackFilterId;
    }

    @Override
    public boolean configure() {
        if (hasConfigurationError()) {
            ASPlayerLog.w("AudioOutputPathV3-%d has configuration error", mId);
            return false;
        }
        if (isConfigured()) {
            ASPlayerLog.w("AudioOutputPathV3-%d already configured", mId);
            return false;
        }
        if (waitForConfigurationRetry()) {
            ASPlayerLog.i("AudioOutputPathV3-%d wait for configuration retry", mId);
            return false;
        }

        if (mAudioParams == null) {
            if (DEBUG) ASPlayerLog.w("AudioOutputPathV3-%d configure failed, audio params is null", mId);
            return false;
        }

        MediaFormat format = mAudioParams.getMediaFormat();
        if (format == null) {
            if (DEBUG) ASPlayerLog.w("AudioOutputPathV3-%d configure failed, audio format is null", mId);
            return false;
        }

        ASPlayerLog.i("AudioOutputPathV3-%d configure, audio renderer: %s", mId, mAudioCodecRenderer);

        String errorMessage;

        String mimeType = format.getString(MediaFormat.KEY_MIME);

        if (mAudioCodecRenderer != null) {
            boolean needChangePIPMode = mChangePIPMode;
            boolean pipModeMatch = !needChangePIPMode;

            if (needChangePIPMode) {
                ASPlayerLog.i("AudioOutputPathV3-%d configure audio render pip mode", mId);
                pipModeMatch = mAudioCodecRenderer.setPIPMode(mTargetPIPMode);
            }

            if (pipModeMatch) {
                mChangePIPMode = false;
                mLastPIPMode = mTargetPIPMode;
                return true;
            }
        }

        if (mAudioCodecRenderer != null) {
            releaseAudioRenderer();
            mAudioCodecRenderer = null;
        }

        AudioCodecRendererV3 audioCodecRenderer =
                new AudioCodecRendererV3(mId, mClock, getHandler());
        mAudioCodecRenderer = audioCodecRenderer;
        audioCodecRenderer.setPIPMode(mTargetPIPMode);

        audioCodecRenderer.setOutputBufferListener(new AudioCodecRenderer.OutputBufferListener() {
            @Override
            public void onRender(long presentationTimeUs, long renderTime) {
//                ASPlayerLog.i("AudioOutputPathV3-%d onRender", mId);
                notifyFrameDisplayed(presentationTimeUs, renderTime);
                mTimestampKeeper.removeTimestamp(presentationTimeUs);
            }

            @Override
            public void onConsume(long presentationTimeUs, long renderTime) {
                mTimestampKeeper.removeTimestamp(presentationTimeUs);
            }
        });

        ASPlayerLog.i("AudioOutputPathV3-%d source:%s, tunneled:%b", mId, mimeType, mTunneledPlayback);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mSecurePlayback = format.containsFeature(FEATURE_SecurePlayback) &&
                    format.getFeatureEnabled(FEATURE_SecurePlayback);
        } else {
            mSecurePlayback = format.getFeatureEnabled(FEATURE_SecurePlayback);
        }
        audioCodecRenderer.setAudioSessionId(mAudioSessionId);
        audioCodecRenderer.setTunneledPlayback(mTunneledPlayback);
        audioCodecRenderer.setAudioCaps(mAudioCaps);
        audioCodecRenderer.setVolume(mGain);
        int filterId = mAudioParams.getTrackFilterId();
        int avSyncHwId = mAudioParams.getAvSyncHwId();
        ASPlayerLog.i("AudioOutputPathV3-%d track filter id: 0x%016x, avSyncHwId: 0x%04x", mId, filterId, avSyncHwId);
        if (mSecurePlayback) {
            filterId |= (1 << 20);
        }
        audioCodecRenderer.setAudioFilterId(filterId);
        audioCodecRenderer.setAvSyncHwId(avSyncHwId);
        audioCodecRenderer.setSubAudioMixLevel(mMixLevel);
        audioCodecRenderer.writeMetadata(mPlacementMetadata);

        audioCodecRenderer.configure(format, null);

        mNeedToConfigureSubTrack = mAudioSubTrackFilterId != INVALID_FILTER_ID;

        errorMessage = audioCodecRenderer.getErrorMessage();

        if (errorMessage == null) {
            AudioFormat audioFormat = audioCodecRenderer.getAudioFormat();
            if (mAudioFormatListener != null) {
                mAudioFormatListener.onAudioFormat(audioFormat);
            }
        }

        if (errorMessage != null) {
            ASPlayerLog.i("AudioOutputPathV3-%d configure failed, errorMessage: %s", mId, errorMessage);
        } else {
            setConfigured(true);
        }
        handleConfigurationError(errorMessage);
        boolean success = errorMessage == null;
        ASPlayerLog.i("AudioOutputPathV3-%d configure %s", mId, success ? "success" : "failed");
        mLastPIPMode = mTargetPIPMode;
        mChangePIPMode = false;
        return success;
    }

    private void setTrackWithTunerMetaData(AudioParams audioParams) {
        if (audioParams == null || audioParams.getTrackFilterId() == INVALID_FILTER_ID) {
            return;
        }

        ASPlayerLog.i("AudioOutputPathV3-%d setTrackWithTunerMetaData filterId: %d", mId, audioParams.getTrackFilterId());
        changeMainTrack(audioParams);
    }

    @Override
    protected void pushInputBuffer() {
        if (!isConfigured() && !configure()) {
            if (DEBUG) ASPlayerLog.i("AudioOutputPathV3-%d push input buffer failed, configure failed", mId);
            return;
        }

        if (mNeedToConfigureSubTrack) {
            changeSubTrack();
        }

        pushInputBufferInitStartTime();

        notifyBufferPushed();
    }

    @Override
    public void flush() {
        super.flush();

        if (mAudioCodecRenderer != null) {
            mAudioCodecRenderer.reset();
        }
    }

    @Override
    public void release() {
        super.release();

        mAudioSubTrackFilterId = INVALID_FILTER_ID;
    }

    private boolean changeMainTrack(AudioParams audioParams) {
        ASPlayerLog.i("AudioOutputPathV3-%d changeMainTrack start", mId);
        if (mAudioCodecRenderer instanceof AudioCodecRendererV3) {
            mMediaFormat = audioParams.getMediaFormat();
            if (mMediaFormat == null) {
                ASPlayerLog.i("AudioOutputPathV3-%d audio format null..", mId);
                return false;
            }

            if (!prepareMetadata(mTunerMetadataMain, audioParams.getTrackFilterId())) {
                return false;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ASPlayerLog.i("AudioOutputPathV3-%d changeMainTrack start writeMetadata", mId);
                ((AudioCodecRendererV3) mAudioCodecRenderer).writeMetadata(mTunerMetadataMain);
            }

            mNeedToConfigureSubTrack = mAudioSubTrackFilterId != INVALID_FILTER_ID;
        }
        return true;
    }

    private void changeSubTrack() {
        if (mNeedToConfigureSubTrack && mAudioCodecRenderer instanceof AudioCodecRendererV3) {
            if (mMediaFormat == null) {
                ASPlayerLog.i("audio format null..");
                return;
            }
            if (mAudioSubTrackFilterId != INVALID_FILTER_ID && !prepareMetadata(mTunerMetadataSub, mAudioSubTrackFilterId)) {
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ((AudioCodecRendererV3) mAudioCodecRenderer).writeMetadata(mTunerMetadataSub);
            }
            mNeedToConfigureSubTrack = false;
        }
    }

    private boolean prepareMetadata(Metadata.TunerMetadata tunerMetadata, int filterId) {
        if (mMediaFormat == null) {
            ASPlayerLog.i("audio format null..");
            return false;
        }

        tunerMetadata.filterId = 0;
        if (filterId != INVALID_FILTER_ID) {
            tunerMetadata.filterId = filterId;
        }
        if (tunerMetadata.filterId < 0) {
            return false;
        }
        tunerMetadata.encodingType = AudioUtils.getEncoding(mMediaFormat);
        ASPlayerLog.i("AudioOutputPathV3-%d prepareMetadata filterId: 0x%04x, encoding: %d",
                mId, tunerMetadata.filterId, tunerMetadata.encodingType);
        return true;
    }

    @Override
    public void setPIPMode(int pipMode) {
        ASPlayerLog.d("AudioOutputPathV3-%d setPIPMode, pipMode: %d", mId, pipMode);
        int lastPipMode = mLastPIPMode;
        super.setPIPMode(pipMode);

        if (lastPipMode == PIPMode.NORMAL && pipMode == PIPMode.PIP) {
            ASPlayerLog.d("AudioOutputPathV3-%d setPIPMode release audio render", mId);
            releaseAudioRenderer();
            setConfigured(false);
        } else {
            ASPlayerLog.d("AudioOutputPathV3-%d setPIPMode not release audio render", mId);
        }
    }
}