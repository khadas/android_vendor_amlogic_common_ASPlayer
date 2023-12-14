package com.amlogic.asplayer.core;

import static android.media.MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback;

import static com.amlogic.asplayer.core.Constant.INVALID_FILTER_ID;

import android.media.AudioFormat;
import android.media.MediaFormat;
import android.os.Build;
import android.text.TextUtils;

import com.amlogic.asplayer.api.AudioParams;
import com.amlogic.asplayer.api.PIPMode;
import com.amlogic.asplayer.api.WorkMode;
import com.amlogic.asplayer.core.encapsulation.Metadata;


class AudioOutputPathV3 extends AudioOutputPathBase {
    private static final boolean DEBUG = true;

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
    boolean hasAudioFormat() {
        return mAudioParams != null && mAudioParams.getTrackFilterId() > 0;
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

        ASPlayerLog.i("%s configure, audio renderer: %s, mChangedWorkMode: %b, mChangePIPMode: %b",
                getTag(), mAudioCodecRenderer, mChangedWorkMode, mChangePIPMode);

        String errorMessage;

        String mimeType = mAudioParams.getMimeType();
        MediaFormat format = mAudioParams.getMediaFormat();
        if (TextUtils.isEmpty(mimeType) && format != null) {
            mimeType = format.getString(MediaFormat.KEY_MIME);
        }

        if (TextUtils.isEmpty(mimeType)) {
            if (DEBUG) ASPlayerLog.w("%s configure failed, mimeType not set", getTag());
            return false;
        }

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

        AudioCodecRendererV3 audioCodecRenderer = new AudioCodecRendererV3(mId, mClock, mHandler);
        mAudioCodecRenderer = audioCodecRenderer;
        mAudioCodecRenderer.setSyncInstanceId(mSyncInstanceId);
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

        // check secure playback or not
        mSecurePlayback = mAudioParams.isScrambled();
        if (!mSecurePlayback && format != null) {
            mSecurePlayback = format.containsFeature(FEATURE_SecurePlayback) &&
                    format.getFeatureEnabled(FEATURE_SecurePlayback);
        }

        audioCodecRenderer.setAudioSessionId(mAudioSessionId);
        audioCodecRenderer.setTunneledPlayback(mTunneledPlayback);
        audioCodecRenderer.setAudioCaps(mAudioCaps);
        audioCodecRenderer.setVolume(mGain);
        int filterId = mAudioParams.getTrackFilterId();
        int avSyncHwId = mAudioParams.getAvSyncHwId();
        ASPlayerLog.i("%s track filter id: 0x%016x, avSyncHwId: 0x%04x, scrambled: %b",
                getTag(), filterId, avSyncHwId, mSecurePlayback);
        if (mSecurePlayback) {
            filterId |= (1 << 20);
        }
        audioCodecRenderer.setAudioFilterId(filterId);
        audioCodecRenderer.setAvSyncHwId(avSyncHwId);
        audioCodecRenderer.setSubAudioVolumeDb(mADVolumeDb);
        audioCodecRenderer.writeMetadata(mPlacementMetadata);

        audioCodecRenderer.configure(mAudioParams, null);
        if (mDualMonoMode != null) {
            audioCodecRenderer.setDualMonoMode(mDualMonoMode.intValue());
        }

        mNeedToConfigureSubTrack = mSubTrackAudioParams != null;

        errorMessage = audioCodecRenderer.getErrorMessage();
        if (errorMessage != null) {
            ASPlayerLog.i("%s configure audio render failed, errorMessage: %s", getTag(), errorMessage);
            handleConfigurationError(errorMessage);
            return false;
        }

        AudioFormat audioFormat = audioCodecRenderer.getAudioFormat();
        if (mAudioFormatListener != null) {
            mAudioFormatListener.onAudioFormat(audioFormat);
        }

        ASPlayerLog.i("%s start audio render", getTag());
        audioCodecRenderer.start();

        errorMessage = audioCodecRenderer.getErrorMessage();

        if (errorMessage != null) {
            ASPlayerLog.i("%s configure audio render failed, errorMessage: %s", getTag(), errorMessage);
            handleConfigurationError(errorMessage);
        } else {
            handleConfigurationError(null);
        }

        boolean success = errorMessage == null;
        ASPlayerLog.i("%s configure %s", getTag(), success ? "success" : "failed");

        if (success) {
            setConfigured(true);

            mLastWorkMode = mTargetWorkMode;
            mLastPIPMode = mTargetPIPMode;
            mChangedWorkMode = false;
            mChangePIPMode = false;
        }

        return success;
    }

    private void setTrackWithTunerMetaData(AudioParams audioParams) {
        if (audioParams == null || audioParams.getTrackFilterId() == INVALID_FILTER_ID) {
            return;
        }

        ASPlayerLog.i("%s setTrackWithTunerMetaData filterId: %d", getTag(), audioParams.getTrackFilterId());
        boolean success = changeMainTrack(audioParams);
        if (success) {
            mAudioParams = audioParams;
        }
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
    public void pause() {
        super.pause();
    }

    @Override
    public void resume() {
        super.resume();
    }

    @Override
    public void flush() {
        super.flush();

        if (mAudioCodecRenderer != null) {
            mAudioCodecRenderer.reset();
        }
    }

    @Override
    public void reset() {
        super.reset();

        releaseAudioRenderer();
    }

    @Override
    void resetForSeek() {
        ASPlayerLog.i("%s resetForSeek", getTag());
        super.resetForSeek();

        releaseAudioRenderer();
    }

    @Override
    public void release() {
        super.release();

        mSubTrackAudioParams = null;
    }

    @Override
    long render() {
        return 10000;
    }

    private boolean changeMainTrack(AudioParams audioParams) {
        ASPlayerLog.i("%s changeMainTrack start", getTag());
        if (audioParams == null) {
            ASPlayerLog.i("%s changeMainTrack failed, audioParam is null");
            return false;
        }

        if (mAudioCodecRenderer instanceof AudioCodecRendererV3) {
            final int filterId = audioParams.getTrackFilterId();
            final int encodingType = AudioUtils.getEncoding(audioParams);
            if (!prepareMetadata(mTunerMetadataMain, filterId, encodingType)) {
                return false;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ASPlayerLog.i("%s changeMainTrack start writeMetadata", getTag());
                Metadata.TunerMetadata metadata = mTunerMetadataMain.clone();
                ((AudioCodecRendererV3) mAudioCodecRenderer).writeMetadata(metadata);
            }

            mNeedToConfigureSubTrack = mSubTrackAudioParams != null;
        }
        return true;
    }

    @Override
    void enableADMix() {
        ASPlayerLog.i("%s enableADMix start", getTag());
        super.enableADMix();
        mNeedToConfigureSubTrack = true;
        ASPlayerLog.i("%s enableADMix enable reconfiguring sub track", getTag());
    }

    @Override
    void disableADMix() {
        ASPlayerLog.i("%s disableADMix start", getTag());
        super.disableADMix();
        mNeedToConfigureSubTrack = true;
        ASPlayerLog.i("%s disableADMix enable reconfiguring sub track", getTag());
    }

    private void changeSubTrack() {
        if (mEnableADMix == null || !mNeedToConfigureSubTrack) {
            // nothing to do in these cases:
            // 1. AD enable/disable not set (AD not supported?)
            // 2. no need to reconfiguring AD track(AD not changed)
            return;
        }

        if (mEnableADMix.booleanValue()) {
            startADByMetadata();
        } else {
            stopADByMetadata();
        }
    }

    private void startADByMetadata() {
        if (mSubTrackAudioParams == null) {
            ASPlayerLog.i("%s sub track audio param is null", getTag());
            return;
        }

        if (mAudioCodecRenderer == null) {
            return;
        }

        if (mAudioCodecRenderer instanceof AudioCodecRendererV3) {
            final int filterId = mSubTrackAudioParams.getTrackFilterId();
            final int encodingType = AudioUtils.getEncoding(mSubTrackAudioParams);
            ASPlayerLog.i("%s startADMixByMetadata, filterId: %d", getTag(), filterId);
            if (!prepareMetadata(mTunerMetadataSub, filterId, encodingType)) {
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Metadata.TunerMetadata metadata = mTunerMetadataSub.clone();
                ((AudioCodecRendererV3) mAudioCodecRenderer).writeMetadata(metadata);
            }

            mNeedToConfigureSubTrack = false;
        }
    }

    private void stopADByMetadata() {
        if (mAudioCodecRenderer == null) {
            return;
        }

        if (mAudioCodecRenderer instanceof AudioCodecRendererV3) {
            final int lastADFilterId = mSubTrackAudioParams != null
                    ? mSubTrackAudioParams.getTrackFilterId() : 0;
            ASPlayerLog.i("%s stopADMixByMetadata, last AD filterId: %d", getTag(), lastADFilterId);

            final int fakeFilterId = 0; // set to invalid filter id. (not -1, 0 instead)
            if (!prepareMetadata(mTunerMetadataSub, fakeFilterId, AudioFormat.ENCODING_DEFAULT)) {
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Metadata.TunerMetadata metadata = mTunerMetadataSub.clone();
                ((AudioCodecRendererV3) mAudioCodecRenderer).writeMetadata(metadata);
            }

            mNeedToConfigureSubTrack = false;
        }
    }

    private boolean prepareMetadata(Metadata.TunerMetadata tunerMetadata, int filterId, int encodingType) {
        tunerMetadata.filterId = 0;

        if (filterId != INVALID_FILTER_ID) {
            tunerMetadata.filterId = filterId;
        }
        if (tunerMetadata.filterId < 0) {
            return false;
        }
        tunerMetadata.encodingType = encodingType;
        ASPlayerLog.i("%s prepareMetadata filterId: 0x%04x, encoding: %d",
                getTag(), tunerMetadata.filterId, tunerMetadata.encodingType);
        return true;
    }

    @Override
    public void setWorkMode(int workMode) {
        ASPlayerLog.d("%s setWorkMode, workMode: %d", getTag(), workMode);
        if (workMode == mLastWorkMode) {
            return;
        }

        int lastWorkMode = mLastWorkMode;

        super.setWorkMode(workMode);

        if (workMode == WorkMode.CACHING_ONLY) {
            handleConfigurationError(null);
        }

        mChangedWorkMode = true;
        setConfigured(false);

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
        if (pipMode == mLastPIPMode) {
            return;
        }

        int lastPipMode = mLastPIPMode;

        super.setPIPMode(pipMode);

        mChangePIPMode = true;
        setConfigured(false);

        if (lastPipMode == PIPMode.NORMAL && pipMode == PIPMode.PIP) {
            ASPlayerLog.d("%s setPIPMode release audio render", getTag());
            releaseAudioRenderer();
            setConfigured(false);
        } else {
            ASPlayerLog.d("%s setPIPMode not release audio render", getTag());
        }
    }
}