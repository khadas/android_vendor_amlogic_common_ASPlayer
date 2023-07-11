package com.amlogic.asplayer.core;

import static android.media.MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback;
import static com.amlogic.asplayer.core.MediaContainerExtractor.INVALID_AV_SYNC_HW_ID;
import static com.amlogic.asplayer.core.MediaContainerExtractor.INVALID_FILTER_ID;

import android.media.AudioFormat;
import android.media.MediaFormat;
import android.os.Build;

import com.amlogic.asplayer.api.AudioParams;
import com.amlogic.asplayer.api.PIPMode;
import com.amlogic.asplayer.api.WorkMode;
import com.amlogic.asplayer.core.encapsulation.Metadata;


class AudioOutputPathV3 extends AudioOutputPath {
    private static final boolean DEBUG = false;

    private float mMixLevel;

    private Metadata.TunerMetadata mTunerMetadataMain;
    private Metadata.TunerMetadata mTunerMetadataSub;
    private Metadata.PlacementMetadata mPlacementMetadata;
    protected boolean mSecurePlayback = false;

    private AudioParams mSubTrackAudioParams;

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
    public String getName() {
        return "AudioOutputPathV3";
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

    void setSubTrackAudioParams(AudioParams audioParams) {
        mSubTrackAudioParams = audioParams;
        mNeedToConfigureSubTrack = true;
    }

    @Override
    public boolean configure() {
        if (hasConfigurationError()) {
            ASPlayerLog.w("%s has configuration error", getTag());
            return false;
        }
        if (isConfigured()) {
            ASPlayerLog.w("%s already configured", getTag());
            return false;
        }
        if (waitForConfigurationRetry()) {
            ASPlayerLog.i("%s wait for configuration retry", getTag());
            return false;
        }

        if (mAudioParams == null) {
            if (DEBUG) ASPlayerLog.w("%s configure failed, audio params is null", getTag());
            return false;
        }

        MediaFormat format = mAudioParams.getMediaFormat();
        if (format == null) {
            if (DEBUG) ASPlayerLog.w("%s configure failed, audio format is null", getTag());
            return false;
        }

        ASPlayerLog.i("%s configure, audio renderer: %s, mChangedWorkMode: %b, mChangePIPMode: %b",
                getTag(), mAudioCodecRenderer, mChangedWorkMode, mChangePIPMode);

        String errorMessage;

        String mimeType = format.getString(MediaFormat.KEY_MIME);

        if (mAudioCodecRenderer != null) {
            boolean needChangeWorkMode = mChangedWorkMode;
            boolean workModeMatch = !needChangeWorkMode;
            boolean needChangePIPMode = mChangePIPMode;
            boolean pipModeMatch = !needChangePIPMode;

            if (needChangeWorkMode) {
                ASPlayerLog.i("%s configure audio render work mode", getTag());
                workModeMatch = mAudioCodecRenderer.setWorkMode(mTargetWorkMode);
            }
            if (needChangePIPMode) {
                ASPlayerLog.i("%s configure audio render pip mode", getTag());
                pipModeMatch = mAudioCodecRenderer.setPIPMode(mTargetPIPMode);
            }

            if (workModeMatch && pipModeMatch) {
                mChangedWorkMode = false;
                mLastWorkMode = mTargetWorkMode;
                mChangePIPMode = false;
                mLastPIPMode = mTargetPIPMode;
                setConfigured(true);
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
        mAudioCodecRenderer.setInstanceId(mInstanceId);
        audioCodecRenderer.setWorkMode(mTargetWorkMode);
        audioCodecRenderer.setPIPMode(mTargetPIPMode);

        audioCodecRenderer.setOutputBufferListener(new AudioCodecRenderer.OutputBufferListener() {
            @Override
            public void onRender(long presentationTimeUs, long renderTime) {
//                ASPlayerLog.i("%s onRender", getTag());
                notifyFrameDisplayed(presentationTimeUs, renderTime);
                mTimestampKeeper.removeTimestamp(presentationTimeUs);
            }

            @Override
            public void onConsume(long presentationTimeUs, long renderTime) {
                mTimestampKeeper.removeTimestamp(presentationTimeUs);
            }
        });

        ASPlayerLog.i("%s source:%s, tunneled:%b", getTag(), mimeType, mTunneledPlayback);
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
        ASPlayerLog.i("%s track filter id: 0x%016x, avSyncHwId: 0x%04x", getTag(), filterId, avSyncHwId);
        if (mSecurePlayback) {
            filterId |= (1 << 20);
        }
        audioCodecRenderer.setAudioFilterId(filterId);
        audioCodecRenderer.setAvSyncHwId(avSyncHwId);
        audioCodecRenderer.setSubAudioMixLevel(mMixLevel);
        audioCodecRenderer.writeMetadata(mPlacementMetadata);

        audioCodecRenderer.configure(format, null);

        mNeedToConfigureSubTrack = mSubTrackAudioParams != null;

        errorMessage = audioCodecRenderer.getErrorMessage();

        if (errorMessage == null) {
            AudioFormat audioFormat = audioCodecRenderer.getAudioFormat();
            if (mAudioFormatListener != null) {
                mAudioFormatListener.onAudioFormat(audioFormat);
            }
        }

        if (errorMessage != null) {
            ASPlayerLog.i("%s configure failed, errorMessage: %s", getTag(), errorMessage);
        } else {
            setConfigured(true);
        }
        handleConfigurationError(errorMessage);
        boolean success = errorMessage == null;
        ASPlayerLog.i("%s configure %s", getTag(), success ? "success" : "failed");
        mLastWorkMode = mTargetWorkMode;
        mLastPIPMode = mTargetPIPMode;
        mChangedWorkMode = false;
        mChangePIPMode = false;
        return success;
    }

    private void setTrackWithTunerMetaData(AudioParams audioParams) {
        if (audioParams == null || audioParams.getTrackFilterId() == INVALID_FILTER_ID) {
            return;
        }

        ASPlayerLog.i("%s setTrackWithTunerMetaData filterId: %d", getTag(), audioParams.getTrackFilterId());
        changeMainTrack(audioParams);
    }

    @Override
    protected void pushInputBuffer() {
        if (!isConfigured() && !configure()) {
            if (DEBUG) ASPlayerLog.i("%s push input buffer failed, configure failed", getTag());
            return;
        }

        if (mEnableADMix != null && mNeedToConfigureSubTrack) {
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

        mSubTrackAudioParams = null;
    }

    private boolean changeMainTrack(AudioParams audioParams) {
        ASPlayerLog.i("%s changeMainTrack start", getTag());
        if (mAudioCodecRenderer instanceof AudioCodecRendererV3) {
            mMediaFormat = audioParams.getMediaFormat();
            if (mMediaFormat == null) {
                ASPlayerLog.i("%s audio format null..", getTag());
                return false;
            }

            if (!prepareMetadata(mTunerMetadataMain, audioParams.getTrackFilterId())) {
                return false;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ASPlayerLog.i("%s changeMainTrack start writeMetadata", getTag());
                ((AudioCodecRendererV3) mAudioCodecRenderer).writeMetadata(mTunerMetadataMain);
            }

            mNeedToConfigureSubTrack = mSubTrackAudioParams != null;
        }
        return true;
    }

    private void changeSubTrack() {
        if (mEnableADMix == null || !mEnableADMix.booleanValue()) {
            return;
        }

        if (mNeedToConfigureSubTrack && mAudioCodecRenderer instanceof AudioCodecRendererV3) {
            if (mMediaFormat == null) {
                ASPlayerLog.i("%s audio format null..", getTag());
                return;
            }
            ASPlayerLog.i("%s changeSubTrack, filterId: %d", getTag(), mSubTrackAudioParams.getTrackFilterId());
            if (!prepareMetadata(mTunerMetadataSub, mSubTrackAudioParams.getTrackFilterId())) {
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
            ASPlayerLog.i("%s audio format null..", getTag());
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
        ASPlayerLog.i("%s prepareMetadata filterId: 0x%04x, encoding: %d",
                getTag(), tunerMetadata.filterId, tunerMetadata.encodingType);
        return true;
    }

    @Override
    public void setWorkMode(int workMode) {
        ASPlayerLog.d("%s setWorkMode, workMode: %d", getTag(), workMode);
        int lastWorkMode = mLastWorkMode;
        super.setWorkMode(workMode);

        if (lastWorkMode == WorkMode.NORMAL && workMode == WorkMode.CACHING_ONLY) {
            ASPlayerLog.d("%s setWorkMode release audio render", getTag());
            releaseAudioRenderer();

            setConfigured(false);
        } else {
            ASPlayerLog.d("%s setWorkMode not release audio render", getTag());
        }
    }

    @Override
    public void setPIPMode(int pipMode) {
        ASPlayerLog.d("%s setPIPMode, pipMode: %d", getTag(), pipMode);
        int lastPipMode = mLastPIPMode;
        super.setPIPMode(pipMode);

        if (lastPipMode == PIPMode.NORMAL && pipMode == PIPMode.PIP) {
            ASPlayerLog.d("%s setPIPMode release audio render", getTag());
            releaseAudioRenderer();
            setConfigured(false);
        } else {
            ASPlayerLog.d("%s setPIPMode not release audio render", getTag());
        }
    }
}