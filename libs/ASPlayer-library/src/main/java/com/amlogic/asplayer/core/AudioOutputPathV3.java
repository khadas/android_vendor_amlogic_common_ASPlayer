package com.amlogic.asplayer.core;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;


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

    private int mAudioFilterId = INVALID_FILTER_ID;
    private int mAudioSubTrackFilterId = INVALID_FILTER_ID;
    private int mAvSyncHwId = INVALID_AV_SYNC_HW_ID;

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

    public void setAudioFilterId(int audioFilterId) {
        this.mAudioFilterId = audioFilterId;
    }

    public void setAudioSubTrackFilterId(int subTrackFilterId) {
        this.mAudioSubTrackFilterId = subTrackFilterId;
    }

    public void setAvSyncHwId(int avSyncHwId) {
        this.mAvSyncHwId = avSyncHwId;
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

        MediaFormat format = mMediaFormat;
        if (format == null) {
            if (DEBUG) ASPlayerLog.w("AudioOutputPathV3-%d configure failed, audio format is null", mId);
            return false;
        }

        ASPlayerLog.i("AudioOutputPathV3-%d configure, audio renderer: %s", mId, mAudioCodecRenderer);

        String errorMessage;

        String mimeType = format.getString(MediaFormat.KEY_MIME);

        if (mAudioCodecRenderer != null) {
            mAudioCodecRenderer.release();
            mAudioCodecRenderer = null;
        }

        AudioCodecRendererV3 audioCodecRenderer =
                new AudioCodecRendererV3(mId, mClock, getHandler());
        mAudioCodecRenderer = audioCodecRenderer;

        audioCodecRenderer.setOutputBufferListener(new AudioCodecRenderer.OutputBufferListener() {
            @Override
            public void onRender(long presentationTimeUs) {
//                TvLog.i("AudioOutputPathV3-%d onRender", mId);
                notifyFrameDisplayed(presentationTimeUs);
                mTimestampKeeper.removeTimestamp(presentationTimeUs);
            }

            @Override
            public void onConsume(long presentationTimeUs) {
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
        int filterId = mAudioFilterId;
        ASPlayerLog.i("AudioOutputPathV3-%d track filter id: %d", mId, filterId);
        if (mSecurePlayback) {
            filterId |= (1 << 20);
        }
        audioCodecRenderer.setAudioFilterId(filterId);
        audioCodecRenderer.setAvSyncHwId(mAvSyncHwId);
        audioCodecRenderer.setSubAudioMixLevel(mMixLevel);
        audioCodecRenderer.writeMetadata(mPlacementMetadata);

        audioCodecRenderer.configure(format, null);
        mNeedToConfigureSubTrack = mAudioSubTrackFilterId != INVALID_FILTER_ID;

        errorMessage = audioCodecRenderer.getErrorMessage();
        if (errorMessage == null) {
            setConfigured(true);
        }

        if (errorMessage != null) {
            ASPlayerLog.i("AudioOutputPathV3-%d configure failed, errorMessage: %s", mId, errorMessage);
        }
        handleConfigurationError(errorMessage);
        boolean success = errorMessage == null;
        ASPlayerLog.i("AudioOutputPathV3-%d configure %s", mId, success ? "success" : "failed");
        return success;
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
    public void reset() {
        super.reset();
    }

    @Override
    public void release() {
        super.release();

        mAudioFilterId = INVALID_FILTER_ID;
        mAudioSubTrackFilterId = INVALID_FILTER_ID;
        mAvSyncHwId = INVALID_AV_SYNC_HW_ID;
    }

    private boolean changeMainTrack() {
        if (mAudioCodecRenderer instanceof AudioCodecRendererV3) {
            if (mMediaFormat == null) {
                ASPlayerLog.i("audio format null..");
                return false;
            }
            if (!prepareMetadata(mTunerMetadataMain, mAudioFilterId)) {
                return false;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
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

        tunerMetadata.filterId = filterId;
        if (tunerMetadata.filterId < 0) {
            // retry to get the filter id
            return false;
        }
        tunerMetadata.encodingType = AudioUtils.getEncoding(mMediaFormat);
        return true;
    }

    private void releaseLinearBlock(MediaCodec.LinearBlock linearBlock) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                linearBlock.recycle();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

