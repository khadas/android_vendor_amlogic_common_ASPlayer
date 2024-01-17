/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.core;

import static com.amlogic.asplayer.core.Constant.UNKNOWN_AUDIO_PRESENTATION_ID;
import static android.media.MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback;

import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaFormat;

import com.amlogic.asplayer.api.AudioParams;
import com.amlogic.asplayer.api.ErrorCode;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

abstract class AudioOutputPathBase extends MediaOutputPath {

    private static final int DEFAULT_AD_MIX_LEVEL = 50; // mix level: 50, ad volume no scale.

    interface AudioFormatListener {
        void onAudioFormat(AudioFormat audioFormat);
    }

    protected AudioCodecRenderer mAudioCodecRenderer;

    protected int mAudioSessionId = Constant.INVALID_AUDIO_SESSION_ID;
    protected boolean mTunneledPlayback;

    protected AudioCaps mAudioCaps;

    protected float mGain;
    protected float mADVolumeDb = 0.f;
    protected Integer mADMixLevel = null;
    protected boolean mMute = false;

    protected boolean mNeedToConfigureSubTrack = false;

    protected boolean mChangedWorkMode = false;
    protected boolean mChangePIPMode = false;

    protected boolean mHasAudio = false;

    protected Boolean mEnableADMix = null;
    protected Integer mDualMonoMode = null;

    protected AudioParams mAudioParams;

    protected AudioFormatListener mAudioFormatListener;

    AudioOutputPathBase(int id) {
        super(id);
        mGain = 1.f;
    }

    @Override
    String getName() {
        return "AudioOutputPathBase";
    }

    void setAudioFormatListener(AudioFormatListener listener) {
        mAudioFormatListener = listener;
    }

    void setAudioSessionId(int sessionId) {
        mAudioSessionId = sessionId;
    }

    void setTunneledPlayback(boolean tunneledPlayback) {
        mTunneledPlayback = tunneledPlayback;
    }

    @Override
    void setSyncInstanceId(int syncInstanceId) {
        super.setSyncInstanceId(syncInstanceId);
        if (mAudioCodecRenderer != null) {
            mAudioCodecRenderer.setSyncInstanceId(syncInstanceId);
        }
    }

    void setMuted(boolean muted) {
        ASPlayerLog.i("%s setMuted: %b", getTag(), muted);
        if (muted) {
            mGain = 0.f;
            mMute = true;
        } else {
            mGain = 1.f;
            mMute = false;
        }

        setAudioVolume(mGain);
    }

    void setVolume(float volume) {
        if (volume >= 0 && volume <= 1.0) {
            mGain = volume;

            setAudioVolume(mGain);
        }
    }

    float getVolume() {
        return mGain;
    }

    private void setAudioVolume(float volume) {
        if (mAudioCodecRenderer != null) {
            mAudioCodecRenderer.setVolume(volume);
        }
    }

    void setADVolumeDb(float adVolumeDb) {
        mADVolumeDb = adVolumeDb;
        if (mAudioCodecRenderer != null) {
            mAudioCodecRenderer.setSubAudioVolumeDb(mADVolumeDb);
        }
    }

    float getADVolumeDb() {
        return mADVolumeDb;
    }

    void setADMixLevel(int mixLevel) {
        mADMixLevel = Integer.valueOf(mixLevel);
        float db = AudioUtils.convertADMixLevelToDB(mixLevel);
        ASPlayerLog.i("%s setADMixLevel, mixLevel: %d, db: %.2f", getTag(), mixLevel, db);
        if (mAudioCodecRenderer != null) {
            mAudioCodecRenderer.setSubAudioVolumeDb(db);
        }
    }

    int getADMixLevel() {
        if (mADMixLevel != null) {
            return mADMixLevel.intValue();
        }
        return DEFAULT_AD_MIX_LEVEL;
    }

    void setAudioParams(AudioParams audioParams) {
        mAudioParams = audioParams;
        mHasAudio = mAudioParams != null;
    }

    boolean hasAudioFormat() {
        return mAudioParams != null;
    }

    void enableADMix() {
        mEnableADMix = Boolean.TRUE;
    }

    void disableADMix() {
        mEnableADMix = Boolean.FALSE;
    }

    void setCaps(AudioCaps caps) {
        mAudioCaps = caps;
    }

    void switchAudioTrack(AudioParams audioParams) {
    }

    boolean setDualMonoMode(int dualMonoMode) {
        mDualMonoMode = Integer.valueOf(dualMonoMode);
        if (mAudioCodecRenderer != null) {
            return mAudioCodecRenderer.setDualMonoMode(dualMonoMode);
        }

        return false;
    }

    int getDualMonoMode() {
        if (mAudioCodecRenderer != null) {
            return mAudioCodecRenderer.getDualMonoMode();
        }

        return AudioTrack.DUAL_MONO_MODE_OFF;
    }

    protected static boolean checkSecurePlayback(AudioParams audioParams) {
        if (audioParams == null) {
            return false;
        }

        boolean secure = audioParams.isScrambled();
        if (!secure && audioParams.getMediaFormat() != null) {
            MediaFormat mediaFormat = audioParams.getMediaFormat();
            secure = mediaFormat.containsFeature(FEATURE_SecurePlayback) &&
                    mediaFormat.getFeatureEnabled(FEATURE_SecurePlayback);
        }
        return secure;
    }

    boolean isPlaying() {
        return mAudioCodecRenderer != null && mAudioCodecRenderer.isPlaying();
    }

    /**
     * Set Audio Presentation Id for AC-4
     *
     * @param presentationId
     * @param programId
     * @return
     */
    int setAudioPresentationId(int presentationId, int programId) {
        if (mAudioCodecRenderer == null) {
            ASPlayerLog.e("%s setAudioPresentationId failed, audioRender is null," +
                            " presentationId: %d, programId: %d", getTag(), presentationId, programId);
            return ErrorCode.ERROR_INVALID_OBJECT;
        }

        return mAudioCodecRenderer.setAudioPresentationId(presentationId, programId);
    }

    int getAudioPresentationId() {
        if (mAudioCodecRenderer == null) {
            ASPlayerLog.e("%s getAudioPresentationId failed, audioRender is null", getTag());
            return UNKNOWN_AUDIO_PRESENTATION_ID;
        }

        return mAudioCodecRenderer.getAudioPresentationId();
    }

    /**
     * Set Audio Language for AC-4
     *
     * @param firstLanguage
     * @param secondLanguage
     * @return
     */
    int setAudioLanguage(int firstLanguage, int secondLanguage) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(AudioUtils.CMD_SET_AUDIO_FIRST_LANG, Integer.valueOf(firstLanguage));
        parameters.put(AudioUtils.CMD_SET_AUDIO_SECOND_LANG, Integer.valueOf(secondLanguage));

        int result = AudioUtils.setParametersToAudioManager(parameters, getTag());
        if (result == ErrorCode.SUCCESS) {
            ASPlayerLog.i("%s setAudioLanguage success, firstLang: %d, 0x%x, secondLang: %d, 0x%x",
                    getTag(), firstLanguage, firstLanguage, secondLanguage, secondLanguage);
            return ErrorCode.SUCCESS;
        } else {
            ASPlayerLog.i("%s setAudioLanguage failed result: %d," +
                            " firstLang: %d, 0x%x, secondLang: %d, 0x%x",
                    getTag(), result, firstLanguage, firstLanguage, secondLanguage, secondLanguage);
            return result;
        }
    }

    int setSpdifProtectionMode(int mode) {
        return AudioUtils.setParameterToAudioManager(
                AudioUtils.CMD_SET_SPDIF_PROTECTION_MODE, mode, getTag());
    }

    @Override
    boolean hasOutputBuffers() {
        return (mAudioCodecRenderer != null) && mAudioCodecRenderer.hasOutputBuffer();
    }

    @Override
    int getNbOutputBuffers() {
        if (mAudioCodecRenderer == null)
            return 0;
        else
            return mAudioCodecRenderer.getNbOutputBuffers();
    }

    @Override
    long getNextOutputTimestamp() {
        if (mAudioCodecRenderer == null)
            return 0;
        else
            return mAudioCodecRenderer.getNextOutputTimestampUs();
    }

    @Override
    long getMarginUs() {
        return mAudioCodecRenderer.getMarginUs();
    }

    @Override
    String getCodecName() {
        if (mAudioCodecRenderer != null)
            return mAudioCodecRenderer.toString();
        else
            return null;
    }

    @Override
    void setSynchroOn(long timestampUs) {
        super.setSynchroOn(timestampUs);
        if (mAudioCodecRenderer != null)
            mAudioCodecRenderer.setClockOrigin(timestampUs);
    }

    @Override
    void setFreeRunMode() {
        super.setFreeRunMode();
        if (mAudioCodecRenderer != null)
            mAudioCodecRenderer.setClockOrigin(0);
    }

    @Override
    boolean isDisplayPositionValid() {
        return (mAudioCodecRenderer != null) &&
                mAudioCodecRenderer.isDisplayPositionValid();
    }

    @Override
    long getDisplayPositionUs() {
        if (mAudioCodecRenderer != null)
            return mAudioCodecRenderer.getDisplayPositionUs();
        else
            return 0;
    }

    protected boolean hasAudioFormatChanged(AudioParams params1, AudioParams params2) {
        if (params1 == null && params2 == null) {
            return false;
        } else if (params1 == null || params2 == null) {
            return true;
        }

        if (params1 == params2) {
            return false;
        }

        if (params1.getTrackFilterId() != params2.getTrackFilterId()) {
            return true;
        }

        if (params1.getAvSyncHwId() != params2.getAvSyncHwId()) {
            return true;
        }

        if (params1.isScrambled() != params2.isScrambled()) {
            return true;
        }

        if (params1.getPid() != params2.getPid()) {
            return true;
        }

        return hasAudioMediaFormatChanged(params1.getMediaFormat(), params2.getMediaFormat());
    }

    protected boolean hasAudioMediaFormatChanged(MediaFormat format1, MediaFormat format2) {
        if (format1 == null && format2 == null) {
            return false;
        } else if (format1 != null || format2 != null) {
            return true;
        }

        // mime type
        if (!Objects.equals(format1.getString(MediaFormat.KEY_MIME),
                format2.getString(MediaFormat.KEY_MIME)))
            return true;
        // in tunneled mode (at least for Broadcom), sample rate and channel count is fixed
        // by the hdmi audio device
        if (mTunneledPlayback)
            return false;
        // sample rate
        if (format1.getInteger(MediaFormat.KEY_SAMPLE_RATE) !=
                format2.getInteger(MediaFormat.KEY_SAMPLE_RATE))
            return true;
        // for dolby, channel count change is handled by decoder
        // ref: AMLOGIC, xiushan.lu: forget that audio input channel is changed
        String mimeType = format1.getString(MediaFormat.KEY_MIME);
        if (mimeType.equals(MediaFormat.MIMETYPE_AUDIO_EAC3) ||
                mimeType.equals(MediaFormat.MIMETYPE_AUDIO_AC3))
            return false;
        // for other, we take channel count into account
        if (format1.getInteger(MediaFormat.KEY_CHANNEL_COUNT) !=
                format2.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
            return true;
        return false;
    }

    public void pause() {
        ASPlayerLog.i("%s pause start", getTag());
        if (mAudioCodecRenderer != null) {
            mAudioCodecRenderer.pause();
        }
    }

    public void resume() {
        ASPlayerLog.i("%s resume start, render: %s", getTag(), mAudioCodecRenderer);
        if (mAudioCodecRenderer != null) {
            mAudioCodecRenderer.resume();
        }
    }

    public void flush() {
        ASPlayerLog.i("%s flush", getTag());
    }

    @Override
    public void reset() {
        ASPlayerLog.i("%s reset", getTag());
        super.reset();

        mAudioParams = null;
        mDualMonoMode = null;

        mNeedToConfigureSubTrack = false;
        mChangedWorkMode = false;
        mChangePIPMode = false;
    }

    void resetForSeek() {
        ASPlayerLog.i("%s resetForSeek", getTag());
        super.reset();
    }

    @Override
    void setSpeed(double speed) {
        super.setSpeed(speed);
        if (mAudioCodecRenderer != null) {
            mAudioCodecRenderer.setSpeed(speed);
        }
    }

    @Override
    void checkErrors() {
        if (mAudioCodecRenderer != null) {
            mAudioCodecRenderer.checkErrors();
        }
    }

    protected void releaseAudioRenderer() {
        ASPlayerLog.i("%s releaseAudioRenderer", getTag());
        if (mAudioCodecRenderer != null) {
            mAudioCodecRenderer.release();
            mAudioCodecRenderer = null;
        }

        setConfigured(false);

        mLastWorkMode = -1;
        mLastPIPMode = -1;
    }

    @Override
    public void release() {
        ASPlayerLog.i("%s release", getTag());
        super.release();

        releaseAudioRenderer();

        mMute = false;
        mHasAudio = false;

        mAudioParams = null;
        mDualMonoMode = null;
    }
}
