/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.core;

import android.media.AudioFormat;
import android.media.MediaFormat;

import com.amlogic.asplayer.api.AudioParams;

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

    protected boolean mHasAudioFormatChanged = false;

    protected boolean mChangedWorkMode = false;
    protected boolean mChangePIPMode = false;

    protected boolean mHasAudio = false;

    protected Boolean mEnableADMix = null;

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
        if (mAudioParams != null) {
            mHasAudioFormatChanged = hasAudioFormatChanged(mAudioParams.getMediaFormat(),
                    audioParams.getMediaFormat());
        }

        mAudioParams = audioParams;
        if (mAudioParams != null) {
            setMediaFormat(mAudioParams.getMediaFormat());
        }
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
        setAudioParams(audioParams);
    }

    boolean isPlaying() {
        return mAudioCodecRenderer != null && mAudioCodecRenderer.isPlaying();
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

    protected boolean hasAudioFormatChanged(MediaFormat format1, MediaFormat format2) {
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
        super.reset();
    }

    @Override
    public void reset() {
        ASPlayerLog.i("%s reset", getTag());
        super.reset();

        mAudioParams = null;
        mHasAudioFormatChanged = false;
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
        mHasAudioFormatChanged = false;
    }
}
