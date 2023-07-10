package com.amlogic.asplayer.core;

import android.media.AudioFormat;
import android.media.MediaDescrambler;
import android.media.MediaFormat;

import com.amlogic.asplayer.api.AudioParams;
import com.amlogic.asplayer.api.WorkMode;

import java.nio.ByteBuffer;
import java.util.Objects;

class AudioOutputPath extends MediaOutputPath {

    interface AudioFormatListener {
        void onAudioFormat(AudioFormat audioFormat);
    }

    //
    protected AudioCodecRenderer mAudioCodecRenderer;

    //
    protected int mAudioSessionId = Constant.INVALID_AUDIO_SESSION_ID;
    protected boolean mTunneledPlayback;

    //
    protected boolean mSecurePlayback;

    //
    protected AudioCaps mAudioCaps;

    protected float mGain;
    protected boolean mMute = false;

    // input buffer shared with extractor
    protected final InputBuffer mInputBuffer;

    protected boolean mNeedToConfigureSubTrack = false;

    protected boolean mHasAudioFormatChanged = false;

    protected boolean mChangedWorkMode = false;
    protected boolean mChangePIPMode = false;

    protected boolean mHasAudio = false;

    protected Boolean mEnableADMix = null;

    protected AudioParams mAudioParams;

    protected AudioFormatListener mAudioFormatListener;

    AudioOutputPath(int id) {
        super(id, String.format("a%d", id));
        mGain = 1.f;
        mInputBuffer = new InputBuffer();
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

    void setMuted(boolean muted) {
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

    @Override
    public boolean configure() {
        return false;
    }

    @Override
    protected void pushInputBuffer() {
        if (!isConfigured() && !configure())
            return;

        pushInputBufferInitStartTime();

        boolean bufferPushed = false;
        while (mAudioCodecRenderer.hasInputBuffer() &&
                mInputBufferQueue != null &&
                !mInputBufferQueue.isEmpty() &&
                !pushInputBufferIsTimeout()) {

            // check if configuration has changed
            MediaFormat newFormat = getMediaFormat();
            if (newFormat != null && mHasAudioFormatChanged) {
                MediaDescrambler descrambler = getMediaDescrambler();
                ASPlayerLog.i("AudioOutputPath-%d input format has changed (%s)", mId, mMediaFormat);
                mAudioCodecRenderer.configure(mMediaFormat, descrambler);
                String errorMessage = mAudioCodecRenderer.getErrorMessage();
                handleConfigurationError(errorMessage);
            }

            int index = mAudioCodecRenderer.getNextInputBufferIndex();
            ByteBuffer inputBuffer = mAudioCodecRenderer.getInputBuffer(index);
            if (inputBuffer == null)
                break;
            mInputBuffer.buffer = inputBuffer;
            // If getting the next input buffer fails we will try again until it succeeds or
            // until there is no more buffer
            // Anyway we need to push back the buffer into the renderer. So even if it fails
            // we send an empty buffer hoping that the renderer will be able to handle it
            // without crash
            while (!mInputBufferQueue.pop(mInputBuffer)) {
                if (mInputBufferQueue.isEmpty()) {
                    ASPlayerLog.w("AudioOutputPath-%d all input buffers are incorrect, we push an empty one to mediacodec", mId);
                    break;
                }
            }
            if (mSecurePlayback) {
                mAudioCodecRenderer.queueSecureInputBuffer(index, 0, mInputBuffer.cryptoInfo,
                        mInputBuffer.timestampUs, 0);
            } else {
                mAudioCodecRenderer.queueInputBuffer(index, 0, mInputBuffer.buffer.limit(),
                        mInputBuffer.timestampUs, 0);
            }
            bufferPushed = true;
            mTimestampKeeper.pushTimestamp(mInputBuffer.timestampUs);
            if (mTimestampKeeper.hasDiscontinuity())
                break;
        }

        timestampInputBufferQueueFullIfNeeded();

        if (bufferPushed)
            notifyBufferPushed();

        if (mTimestampKeeper.hasDiscontinuity())
            reset();
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
        if (mAudioCodecRenderer != null) {
            mAudioCodecRenderer.pause();
        }
    }

    public void resume() {
        if (mAudioCodecRenderer != null) {
            mAudioCodecRenderer.resume();
        }
    }

    public void flush() {

    }

    @Override
    public void reset() {
//        TvLog.i("AudioOutputPath-%d reset", mId);
        super.reset();
        // maybe one day we will reuse mAudioCodecRenderer, but today it is safer to forget it
        if (mAudioCodecRenderer != null) mAudioCodecRenderer.release();
        mAudioCodecRenderer = null;

        mAudioParams = null;
        mHasAudioFormatChanged = false;
    }

    @Override
    public void release() {
        ASPlayerLog.i("AudioOutputPath-%d release", mId);
        super.release();
        if (mAudioCodecRenderer != null) mAudioCodecRenderer.release();
        mAudioCodecRenderer = null;

        mMute = false;
        mHasAudio = false;

        mAudioParams = null;
        mHasAudioFormatChanged = false;
    }

    @Override
    long render() {
        if (mAudioCodecRenderer == null || !mClock.isStarted())
            return 10000;

        if (mClock.getSpeed() == 0.0)
            return 10000;

        switch (getRenderingMode()) {
            case RENDER_FREE_RUN:
                return mAudioCodecRenderer.renderFreeRun();
            case RENDER_SYNCHRONIZED:
                return mAudioCodecRenderer.renderSynchro(mInputBufferQueue.getSizeInUs());
            case RENDER_NONE:
            default:
                return 10000;
        }
    }

    @Override
    void setSpeed(double speed) {
        super.setSpeed(speed);
        if (mAudioCodecRenderer != null)
            mAudioCodecRenderer.setSpeed(speed);
    }

    @Override
    void checkErrors() {
        if (mAudioCodecRenderer != null)
            mAudioCodecRenderer.checkErrors();
    }

    @Override
    public String getName() {
        return "AudioOutputPath";
    }

    void setCaps(AudioCaps caps) {
        mAudioCaps = caps;
    }

    @Override
    public void setWorkMode(int workMode) {
        if (workMode == mLastWorkMode) {
            return;
        }

        ASPlayerLog.i("AudioOutputPath-%d setWorkMode: %d, last mode:%d", mId, workMode, mLastWorkMode);
        if (workMode == WorkMode.CACHING_ONLY) {
            handleConfigurationError(null);
        }

        super.setWorkMode(workMode);
        mChangedWorkMode = true;
        setConfigured(false);
    }

    @Override
    public void setPIPMode(int pipMode) {
        if (pipMode == mLastPIPMode) {
            return;
        }

        ASPlayerLog.i("AudioOutputPath-%d setPIPMode: %d, last mode: %d", mId, pipMode, mLastPIPMode);
        super.setPIPMode(pipMode);
        mChangePIPMode = true;
        setConfigured(false);
    }

    protected void releaseAudioRenderer() {
        if (mAudioCodecRenderer != null) {
            mAudioCodecRenderer.release();
            mAudioCodecRenderer = null;
        }

        mLastWorkMode = -1;
        mLastPIPMode = -1;
    }
}
