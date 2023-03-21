package com.amlogic.asplayer.core;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaDescrambler;
import android.media.MediaFormat;
import android.os.Handler;


import com.amlogic.asplayer.api.WorkMode;
import com.amlogic.asplayer.core.ts.TsAc3Parser;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Collections;

/**
 * AudioCodecRenderer that copies input buffer to audio track
 */
class AudioCodecRendererPassthrough implements AudioCodecRenderer {

    private class InputBuffer {
        InputBuffer(int index) {
            this.index = index;
            this.buffer = ByteBuffer.allocateDirect(MAX_INPUT_BUFFER_SIZE);
        }

        void clear() {
            offset = 0;
            size = 0;
            timestampUs = 0;
            flags = 0;
            buffer.clear();
        }

        int index;
        int offset;
        int size;
        long timestampUs;
        int flags;
        ByteBuffer buffer;
    }

    private class OutputBuffer {
        OutputBuffer(int index) {
            this.index = index;
            this.buffer = ByteBuffer.allocateDirect(MAX_OUTPUT_BUFFER_SIZE);
        }

        void clear() {
            offset = 0;
            size = 0;
            timestampUs = 0;
            flags = 0;
            buffer.clear();
        }

        int index;
        int offset;
        int size;
        long timestampUs;
        int flags;
        ByteBuffer buffer;
    }

    private static final int AUDIO_BUFFER_SIZE_FOR_TUNNELED_MODE = 32 * 1024;

    private static final int MAX_AC3_BUFFERED_SYNCFRAMES = 4;
    private static final int MAX_EAC3_BUFFERED_SYNCFRAMES = 8;

    // no yet tested (13 July 2017)
    private static final int AUDIOTRACK_AAC_BUFFERSIZE = 2048;

    private static final int MAX_INPUT_BUFFERS = 8;
    private static final int MAX_OUTPUT_BUFFERS = 8;
    private static final int MAX_INPUT_BUFFER_SIZE = 8 * 1024;
    private static final int MAX_OUTPUT_BUFFER_SIZE = 8 * 1024;

    private Handler mHandler;

    // input buffers
    private InputBuffer[] mInputBuffers;
    private ArrayDeque<InputBuffer> mAvailableInputBuffers;
    private ArrayDeque<InputBuffer> mQueuedInputBuffers;

    // output buffers
    private OutputBuffer[] mOutputBuffers;
    private ArrayDeque<OutputBuffer> mAvailableOutputBuffers;
    private ArrayDeque<OutputBuffer> mQueuedOutputBuffers;

    // decoder thread
    private Thread mDecoderThread;
    private final Object mLock;
    private Exception mException;
    private boolean mReleased;

    // clock
    private MediaClock mClock;

    // input infos
    private int mInputEncoding;
    private int mInputSampleRate;
    private int mInputChannelCount;

    // output infos
    private int mOutputEncoding;
    private int mOutputSampleRate;
    private int mOutputChannelCount;

    // output track
    private AudioTrack mAudioTrack;
    private int mAudioSessionId;
    private boolean mTunneledPlayback;
    private float mGain;
    private long mMarginUs;
    private long mNbFramesPerSample;
    private long mSyncFrameSize;

    // for display position
    private AudioPositionTracker mAudioPositionTracker;
    private long mPushedDataInFrames;

    // listener
    private BufferInfo mInfo;
    private OutputBufferListener mOutputBufferListener;

    private int mWorkMode = WorkMode.NORMAL;

    AudioCodecRendererPassthrough(MediaClock clock, Handler handler) {
        mHandler = handler;

        mInputBuffers = new InputBuffer[MAX_INPUT_BUFFERS];
        for (int i = 0; i < mInputBuffers.length; ++i)
            mInputBuffers[i] = new InputBuffer(i);
        mAvailableInputBuffers = new ArrayDeque<>();
        Collections.addAll(mAvailableInputBuffers, mInputBuffers);
        mQueuedInputBuffers = new ArrayDeque<>();

        mOutputBuffers = new OutputBuffer[MAX_OUTPUT_BUFFERS];
        for (int i = 0; i < mOutputBuffers.length; ++i)
            mOutputBuffers[i] = new OutputBuffer(i);
        mAvailableOutputBuffers = new ArrayDeque<>();
        Collections.addAll(mAvailableOutputBuffers, mOutputBuffers);
        mQueuedOutputBuffers = new ArrayDeque<>();

        mLock = new Object();

        mInfo = new BufferInfo();

        mGain = 1.f;
        mClock = clock;

        mAudioPositionTracker = new AudioPositionTracker();
    }

    @Override
    public void setAudioSessionId(int sessionId) {
        mAudioSessionId = sessionId;
    }

    @Override
    public void setTunneledPlayback(boolean activated) {
        mTunneledPlayback = activated;
    }

    @Override
    public boolean isPlaying() {
        return mAudioTrack != null && mAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING;
    }

    @Override
    public void setSpeed(double speed) {
        if (mAudioTrack == null)
            return;
        if (speed != 0 && speed != 1) {
            ASPlayerLog.w("unexpected speed %f, should be 0 or 1", speed);
            return;
        }
        mClock.setSpeed(speed);
        if (speed == 0) {
            mAudioTrack.pause();
            mAudioPositionTracker.suspend();
        } else {
            mAudioTrack.play();
            mAudioPositionTracker.resume();
        }
    }

    @Override
    public boolean hasInputBuffer() {
        synchronized (mLock) {
            return !mAvailableInputBuffers.isEmpty();
        }
    }

    @Override
    public int getNextInputBufferIndex() {
        synchronized (mLock) {
            if (mAvailableInputBuffers.isEmpty())
                return -1;
            else
                return mAvailableInputBuffers.pop().index;
        }
    }

    @Override
    public ByteBuffer getInputBuffer(int index) {
        synchronized (mLock) {
            if (index < 0 || index >= mInputBuffers.length)
                return null;
            return mInputBuffers[index].buffer;
        }
    }

    @Override
    public void queueInputBuffer(int index, int offset, int size, long timestampUs, int flags) {
        synchronized (mLock) {
            InputBuffer buffer = mInputBuffers[index];
            buffer.offset = offset;
            buffer.size = size;
            buffer.timestampUs = timestampUs;
            buffer.flags = flags;
            mQueuedInputBuffers.addLast(buffer);
            if (canDecodeBuffer())
                mLock.notify();
        }
    }

    @Override
    public boolean hasOutputBuffer() {
        synchronized (mLock) {
            return !mQueuedOutputBuffers.isEmpty();
        }
    }

    @Override
    public int getNbOutputBuffers() {
        synchronized (mLock) {
            return mQueuedOutputBuffers.size();
        }
    }

    @Override
    public long getNextOutputTimestampUs() {
        synchronized (mLock) {
            OutputBuffer buffer = peekOutputBuffer();
            if (buffer == null)
                return 0;
            else
                return buffer.timestampUs;
        }
    }

    @Override
    public boolean isDisplayPositionValid() {
        return mAudioPositionTracker.isPositionValid();
    }

    @Override
    public long getDisplayPositionUs() {
        if (!isDisplayPositionValid())
            return 0;

        return mAudioPositionTracker.getPositionUs();
    }

    @Override
    public long getMarginUs() {
        return mMarginUs;
    }

    @Override
    public void setOutputBufferListener(OutputBufferListener listener) {
        mOutputBufferListener = listener;
    }

    @Override
    public void setClockOrigin(long timestampUs) {
        mAudioPositionTracker.stop();
        if (mAudioTrack != null && mAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            mAudioTrack.pause();
            mAudioPositionTracker.suspend();
        }
    }

    @Override
    public void setAudioCaps(AudioCaps audioCaps) {
        if (audioCaps == null)
            return;
        ASPlayerLog.i(audioCaps.toString());
    }

    @Override
    public void setVolume(float gain) {
        mGain = gain;
        if (mAudioTrack != null)
            mAudioTrack.setVolume(gain);
    }

    @Override
    public long renderFreeRun() {
        if (mAudioTrack.getPlaybackRate() != AudioTrack.PLAYSTATE_PLAYING) {
            mAudioTrack.play();
            mAudioPositionTracker.resume();
        }

        mAudioPositionTracker.updatePosition();

        return render(peekOutputBuffer());
    }

    @Override
    public long renderSynchro(long inputQueueSizeUs) {
        if (mQueuedOutputBuffers.isEmpty())
            return 10000;

        mAudioPositionTracker.updatePosition();

        // get output buffer info
        OutputBuffer outputBuffer = peekOutputBuffer();
        if (outputBuffer == null)
            return 10000;

        // compute delta between timestamp and clock
        long timeUs = mClock.timeUs();
        long timestampUs = outputBuffer.timestampUs;
        long deltaUs = timestampUs - timeUs;
        boolean audiotrackStarted = (mAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING);

        // compute buffer duration
        long bufferDurationUs = (outputBuffer.size * mNbFramesPerSample * 1000000L / mSyncFrameSize)
                / mOutputSampleRate;

        // output buffer is NOT pushed into audiotrack if output pts is a bit after current time
        // it is a kind of control based on injection
        boolean mustRender = true;
        boolean mustConsume = false;
        if (!audiotrackStarted) {
            // current output buffer is before clock, we must skip it
            if (deltaUs + bufferDurationUs < 0) {
                mustRender = false;
                mustConsume = true;
            } else if (deltaUs > bufferDurationUs) {
                // we must suspend inject until we reach clock
                mustRender = false;
            }
        }
        // AudioTrack.getUnderrunCount not reliable for at least amlogic, so do nothing here
        // else {
        // }

        if (mustConsume) {
            consume(outputBuffer);
            return 1000;
        } else if (mustRender) {
            if (mAudioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                ASPlayerLog.i("first delta:%d ms size:%d",
                        deltaUs / 1000, outputBuffer.size);
                mAudioTrack.play();
                mAudioPositionTracker.resume();
            }
            return render(outputBuffer);
        } else {
            return 10000;
        }
    }

    @Override
    public void configure(MediaFormat format, MediaDescrambler descrambler) {
        if (format.getInteger(MediaFormat.KEY_SAMPLE_RATE) == mInputSampleRate) {
            ASPlayerLog.i("no new sample rate, ignore new format: =>", format);
            return;
        }

        AudioUtils.releaseAudioTrack(mAudioTrack);
        mAudioTrack = null;

        mQueuedInputBuffers.clear();
        mQueuedOutputBuffers.clear();
        mAvailableInputBuffers.clear();
        mAvailableOutputBuffers.clear();
        Collections.addAll(mAvailableInputBuffers, mInputBuffers);
        Collections.addAll(mAvailableOutputBuffers, mOutputBuffers);
        mException = null;

        ASPlayerLog.i("source format:%s", format);
        try {
            mInputSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            mInputChannelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);

            String mimeType = format.getString(MediaFormat.KEY_MIME);
            switch (mimeType) {
                case MediaFormat.MIMETYPE_AUDIO_AC3:
                    mInputEncoding = AudioFormat.ENCODING_AC3;
                    mInputChannelCount = Math.min(mInputChannelCount, 6);
                    break;
                case MediaFormat.MIMETYPE_AUDIO_EAC3:
                    mInputEncoding = AudioFormat.ENCODING_E_AC3;
                    break;
                case MediaFormat.MIMETYPE_AUDIO_AAC:
                    mInputEncoding = AudioFormat.ENCODING_AAC_LC;
                    break;
                default:
                    throw new IllegalStateException("Invalid mime type " + mimeType);
            }

            int bufferSize = getAudioTrackBufferSize(format);

            // it may be possible that output parameters differs from input
            // for instance on AMLOGIC, input (EAC3), output (AC3)
            configureOutput(mInputEncoding, mInputSampleRate, mInputChannelCount);

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setFlags(mTunneledPlayback ? AudioAttributes.FLAG_HW_AV_SYNC : 0)
                    .build();
            AudioFormat audioFormat = new AudioFormat.Builder()
                    .setEncoding(mOutputEncoding)
                    .setSampleRate(mOutputSampleRate)
                    .setChannelMask(getChannelConfig(mOutputChannelCount))
                    .build();
            AudioTrack.Builder builder = new AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(bufferSize)
                    .setSessionId(mAudioSessionId);
            mAudioTrack = builder.build();

            updateAudioTrackInfos(format);
            mAudioPositionTracker.setAudioTrack(mAudioTrack, format);
            mPushedDataInFrames = 0;

            ASPlayerLog.i("AudioTrack [sample_rate:%d, channel:%d, encoding:%d, buffer_size:%d]",
                    mOutputSampleRate, mOutputChannelCount, mOutputEncoding, bufferSize);

            // suspend (to avoid glitch).
            // track will be resume as soon as a new output buffer will be played
            mAudioTrack.pause();
            mAudioPositionTracker.suspend();
            mAudioTrack.setVolume(mGain);

            startDecoderThread();
        } catch (Exception exception) {
            ASPlayerLog.w("Failed to configure AudioTrack: %s", exception.getMessage());
            mException = exception;
            releaseDecoderThread();
            AudioUtils.releaseAudioTrack(mAudioTrack);
            mAudioTrack = null;
        }
    }

    @Override
    public void release() {
        releaseDecoderThread();
        AudioUtils.releaseAudioTrack(mAudioTrack);
        mAudioTrack = null;

        mInputEncoding = AudioFormat.ENCODING_INVALID;
        mInputChannelCount = 0;
        mInputSampleRate = 0;

        mOutputEncoding = AudioFormat.ENCODING_INVALID;
        mOutputChannelCount = 0;
        mOutputSampleRate = 0;

        mQueuedInputBuffers.clear();
        mQueuedOutputBuffers.clear();
        mAvailableInputBuffers.clear();
        mAvailableOutputBuffers.clear();
        Collections.addAll(mAvailableInputBuffers, mInputBuffers);
        Collections.addAll(mAvailableOutputBuffers, mOutputBuffers);
        mException = null;

        mAudioPositionTracker.stop();
        mHandler.removeCallbacksAndMessages(this);
    }

    private void startDecoderThread() {
        if (mDecoderThread != null) {
            releaseDecoderThread();
        }
        mReleased = false;
        mDecoderThread = new Thread(AudioCodecRendererPassthrough.this::run);
        mDecoderThread.start();
    }

    private void releaseDecoderThread() {
        synchronized (mLock) {
            mReleased = true;
            mLock.notify();
        }
        try {
            if (mDecoderThread != null) {
                mDecoderThread.join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        mDecoderThread = null;
    }

    @Override
    public void reset() {
        synchronized (mLock) {
            mQueuedInputBuffers.clear();
            mQueuedOutputBuffers.clear();
            mAvailableInputBuffers.clear();
            mAvailableOutputBuffers.clear();
            Collections.addAll(mAvailableInputBuffers, mInputBuffers);
            Collections.addAll(mAvailableOutputBuffers, mOutputBuffers);
            mAudioPositionTracker.stop();
            mHandler.removeCallbacksAndMessages(this);
        }
        mException = null;
    }

    @Override
    public void checkErrors() {
        if (mException != null) {
            ASPlayerLog.w("error %s, reset ", mException.getMessage());
            release();
        }
    }

    @Override
    public String getErrorMessage() {
        return mException != null? mException.getMessage(): null;
    }

    private int getAudioTrackBufferSize(MediaFormat format) {
        if (mTunneledPlayback) {
            return AUDIO_BUFFER_SIZE_FOR_TUNNELED_MODE;
        } else {
            String mimeType = format.getString(MediaFormat.KEY_MIME);
            switch (mimeType) {
                case MediaFormat.MIMETYPE_AUDIO_AC3:
                    return MAX_AC3_BUFFERED_SYNCFRAMES * format.getInteger(TsAc3Parser.KEY_SYNCFRAME_SIZE);
                case MediaFormat.MIMETYPE_AUDIO_EAC3:
                    return MAX_EAC3_BUFFERED_SYNCFRAMES * format.getInteger(TsAc3Parser.KEY_SYNCFRAME_SIZE);
                case MediaFormat.MIMETYPE_AUDIO_AAC:
                default:
                    return AUDIOTRACK_AAC_BUFFERSIZE;
            }
        }
    }

    private void updateAudioTrackInfos(MediaFormat format) {
        String mimeType = format.getString(MediaFormat.KEY_MIME);
        int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        switch (mimeType) {
            case MediaFormat.MIMETYPE_AUDIO_AC3:
            case MediaFormat.MIMETYPE_AUDIO_EAC3:
                mNbFramesPerSample = format.getInteger(TsAc3Parser.KEY_NB_SAMPLES_BY_SYNCFRAME);
                mSyncFrameSize = format.getInteger(TsAc3Parser.KEY_SYNCFRAME_SIZE);
                int maxNbOfBufferedSyncFrames;
                if (mimeType.equals(MediaFormat.MIMETYPE_AUDIO_AC3))
                    maxNbOfBufferedSyncFrames = MAX_AC3_BUFFERED_SYNCFRAMES;
                else
                    maxNbOfBufferedSyncFrames = MAX_EAC3_BUFFERED_SYNCFRAMES;
                mMarginUs = mTunneledPlayback ? 0 :
                        (maxNbOfBufferedSyncFrames * mNbFramesPerSample * 1000000L / sampleRate);
                break;
            case MediaFormat.MIMETYPE_AUDIO_AAC:
            default:
                ASPlayerLog.w("can't get audio track info for %s", format);
                break;
        }
    }

    private void configureOutput(int sourceEncoding, int sourceSampleRate, int sourceChannelCount) {
        mOutputEncoding = sourceEncoding;
        mOutputSampleRate = sourceSampleRate;
        mOutputChannelCount = sourceChannelCount;
    }

    private int getChannelConfig(int channelCount) {
        // At least on Broadcom platforms, some configurations are not supported and must be
        // replaced by more generic ones.
        // This workaround is applied for all, as Exoplayer does.
        switch (channelCount) {
            case 1:
                return AudioFormat.CHANNEL_OUT_MONO;
            case 2:
                return AudioFormat.CHANNEL_OUT_STEREO;
            case 3:
                // return AudioFormat.CHANNEL_OUT_STEREO | AudioFormat.CHANNEL_OUT_FRONT_CENTER;
            case 4:
                // return AudioFormat.CHANNEL_OUT_QUAD;
            case 5:
                // return AudioFormat.CHANNEL_OUT_QUAD | AudioFormat.CHANNEL_OUT_FRONT_CENTER;
            case 6:
                return AudioFormat.CHANNEL_OUT_5POINT1;
            case 7:
                // return AudioFormat.CHANNEL_OUT_5POINT1 | AudioFormat.CHANNEL_OUT_BACK_CENTER;
            case 8:
                return AudioFormat.CHANNEL_OUT_7POINT1_SURROUND;
            default:
                ASPlayerLog.w("invalid channel count " + channelCount);
                return AudioFormat.CHANNEL_OUT_STEREO;
        }
    }

    private void notifyOutputBufferConsumption(boolean rendered) {
        final BufferInfo info = new BufferInfo();
        info.presentationTimeUs = mInfo.presentationTimeUs;
        info.offset = mInfo.offset;
        info.size = mInfo.size;
        info.flags = mInfo.flags;
        if (rendered) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mOutputBufferListener != null) {
                        ASPlayerLog.i("");
                        mOutputBufferListener.onRender(info.presentationTimeUs);
                    }
                }
            }, 0);
        } else {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mOutputBufferListener != null)
                        mOutputBufferListener.onConsume(info.presentationTimeUs);
                }
            }, 0);
        }
    }

    private long render(OutputBuffer outputBuffer) {
        if (mOutputBufferListener != null) {
            mInfo.flags = outputBuffer.flags;
            mInfo.offset = outputBuffer.offset;
            mInfo.presentationTimeUs = outputBuffer.timestampUs;
            mInfo.size = outputBuffer.size;
            notifyOutputBufferConsumption(true);
        }

        int remaining = outputBuffer.buffer.remaining();
        int nbWritten;
        if (mTunneledPlayback) {
            nbWritten = mAudioTrack.write(outputBuffer.buffer, remaining, AudioTrack.WRITE_NON_BLOCKING,
                    outputBuffer.timestampUs * 1000);
        } else {
            nbWritten = mAudioTrack.write(outputBuffer.buffer, remaining, AudioTrack.WRITE_NON_BLOCKING);
        }

        // check errors
        String errorMessage = null;
        switch (nbWritten) {
            case AudioTrack.ERROR_DEAD_OBJECT:
                errorMessage = "AudioTrack is dead";
                break;
            case AudioTrack.ERROR_INVALID_OPERATION:
                errorMessage = "AudioTrack not initialized, can't write data";
                break;
            case AudioTrack.ERROR:
                errorMessage = "unexpected error";
                break;
            case AudioTrack.ERROR_BAD_VALUE:
                errorMessage = "unexpected bad value error";
                break;
        }
        if (errorMessage != null) {
            ASPlayerLog.w("error while writing data: %s", errorMessage);
            mException = new IllegalStateException(errorMessage);
            return 20000;
        }

        // tempo if all data can't be injected, otherwise push as much as possible
        if (nbWritten == remaining) {
            mAudioPositionTracker.updateReferenceInFrames(mPushedDataInFrames, outputBuffer.timestampUs);
            mPushedDataInFrames += mNbFramesPerSample;

            outputBuffer.clear();
            synchronized (mLock) {
                mQueuedOutputBuffers.remove(outputBuffer);
                mAvailableOutputBuffers.add(outputBuffer);
                mLock.notify();
            }
            return 5000;
        } else {
            return 20000;
        }
    }

    private void consume(OutputBuffer outputBuffer) {
        if (mOutputBufferListener != null) {
            mInfo.flags = outputBuffer.flags;
            mInfo.offset = outputBuffer.offset;
            mInfo.presentationTimeUs = outputBuffer.timestampUs;
            mInfo.size = outputBuffer.size;
            notifyOutputBufferConsumption(false);
        }
        outputBuffer.clear();
        synchronized (mLock) {
            mQueuedOutputBuffers.remove(outputBuffer);
            mAvailableOutputBuffers.add(outputBuffer);
            mLock.notify();
        }
    }

    private OutputBuffer peekOutputBuffer() {
        if (mQueuedOutputBuffers.isEmpty())
            return null;
        return mQueuedOutputBuffers.peekFirst();
    }

    private boolean canDecodeBuffer() {
        return !mQueuedInputBuffers.isEmpty() && !mAvailableOutputBuffers.isEmpty();
    }

    private void run() {
        try {
            ASPlayerLog.i("encoding[source:%d, device:%d]", mInputEncoding, mOutputEncoding);

            InputBuffer inputBuffer;
            OutputBuffer outputBuffer = null;
            while (!mReleased) {
                synchronized (mLock) {
                    while (true) {
                        if (mReleased)
                            return;
                        // no need to push input if there is no room for output
                        if (!mAvailableOutputBuffers.isEmpty() && !mQueuedInputBuffers.isEmpty())
                            break;
                        mLock.wait();
                    }
                    if (mReleased)
                        return;

                    inputBuffer = null;
                    if (!mQueuedInputBuffers.isEmpty())
                        inputBuffer = mQueuedInputBuffers.pop();

                    if (outputBuffer == null)
                        outputBuffer = mAvailableOutputBuffers.pop();
                }

                if (inputBuffer != null && outputBuffer != null) {
                    outputBuffer.buffer.clear();
                    outputBuffer.buffer.put(inputBuffer.buffer);
                    outputBuffer.buffer.flip();
                    outputBuffer.timestampUs = inputBuffer.timestampUs;
                    outputBuffer.flags = inputBuffer.flags;
                    outputBuffer.offset = 0;
                    outputBuffer.size = outputBuffer.buffer.limit();
                    synchronized (mLock) {
                        inputBuffer.buffer.clear();
                        mAvailableInputBuffers.add(inputBuffer);
                        mQueuedOutputBuffers.addLast(outputBuffer);
                        outputBuffer = null;
                    }
                } else {
                    ASPlayerLog.w("unexpected state (none should be null) input:%s output:%s",
                            inputBuffer, outputBuffer);
                }
            }
        } catch (InterruptedException e) {
            mException = new IllegalStateException(e);
        }
    }
}
