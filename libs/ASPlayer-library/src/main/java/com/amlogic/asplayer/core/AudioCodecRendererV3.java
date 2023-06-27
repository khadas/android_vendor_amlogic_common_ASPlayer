package com.amlogic.asplayer.core;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTimestamp;
import android.media.AudioTrack;
import android.media.MediaDescrambler;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;


import com.amlogic.asplayer.api.PIPMode;
import com.amlogic.asplayer.api.WorkMode;
import com.amlogic.asplayer.core.encapsulation.EncapsulationEncoder;
import com.amlogic.asplayer.core.encapsulation.Metadata;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static com.amlogic.asplayer.core.MediaContainerExtractor.INVALID_AV_SYNC_HW_ID;
import static com.amlogic.asplayer.core.MediaContainerExtractor.INVALID_FILTER_ID;


public class AudioCodecRendererV3 implements AudioCodecRenderer {
    private static final int[] SAMPLE_RATES = { 48000, 44100, 32000 };

    private int mId;
    private final Handler mHandler;
    private AudioCaps mAudioCaps;
    private float mGain;
    private float mMixLevel;
    private final MediaClock mClock;
    private int mAudioFilterId = INVALID_FILTER_ID;
    private int mAvSyncHwId = INVALID_AV_SYNC_HW_ID;
    private AudioTrack mAudioTrack;
    private DecoderThread mDecoderThread;
    private final Object mLock;
    private static final long AUDIO_TRACK_PREPARE_TIMEOUT = 200;
    private static final int AUDIO_BUFFER_SIZE = 256;
    private OutputBufferListener mOutputBufferListener;
    private AudioTimestamp timestamp = new AudioTimestamp();
    private long mLastRenderedTimeUs;
    private final ByteBuffer mEmptyPacket = ByteBuffer.allocate(AUDIO_BUFFER_SIZE);
    private final ByteBuffer mMetadataPacket = ByteBuffer.allocate(AUDIO_BUFFER_SIZE);
    private final List<Metadata> mMetadata = new ArrayList<>();
    private String mErrorMessage;

    private int mLastPIPMode = -1;
    private int mTargetPIPMode = mLastPIPMode;

    private boolean mPaused = true;

    private AudioFormat mAudioFormat;

    AudioCodecRendererV3(int id, MediaClock clock, Handler handler) {
        mId = id;
        mLock = new Object();
        mHandler = handler;
        mGain = 1.f;
        mClock = clock;
        EncapsulationEncoder.encodeEmptyPacket(mEmptyPacket, AUDIO_BUFFER_SIZE);
    }

    @Override
    public void setOutputBufferListener(OutputBufferListener listener) {
        mOutputBufferListener = listener;
    }

    @Override
    public void setClockOrigin(long timestampUs) {

    }

    @Override
    public void setAudioSessionId(int sessionId) {

    }

    public void setAudioFilterId(int filterId) {
        ASPlayerLog.i("AudioCodecRendererV3-%d set audio filter id: %d", mId, filterId);
        mAudioFilterId = filterId;
    }

    public void writeMetadata(Metadata metadata) {
        ASPlayerLog.i("AudioCodecRendererV3-%d metadata: %s", mId, metadata);
        synchronized (mMetadata) {
            mMetadata.removeIf(item -> item.getClass().equals(metadata.getClass()));
            mMetadata.add(metadata);
        }
    }

    public void setSubAudioMixLevel(float mixLevel) {
        mMixLevel = mixLevel;
    }

    public void setAvSyncHwId(int avSyncHwId) {
        mAvSyncHwId = avSyncHwId;
    }

    @Override
    public void setTunneledPlayback(boolean activated) {

    }

    @Override
    public void setAudioCaps(AudioCaps audioCaps) {
        mAudioCaps = audioCaps;
    }

    @Override
    public void setVolume(float gain) {
        mGain = gain;
        if (mAudioTrack != null)
            mAudioTrack.setVolume(gain);
    }

    @Override
    public long renderFreeRun() {
        return 100000;
    }

    @Override
    public long renderSynchro(long inputQueueSizeUs) {
        return 100000;
    }

    private AudioFormat getAudioFormat(AudioAttributes audioAttributes, int encoding) {
        int[] channels = new int[] {
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.CHANNEL_OUT_QUAD,
                AudioFormat.CHANNEL_OUT_5POINT1,
                AudioFormat.CHANNEL_OUT_7POINT1_SURROUND,
                AudioFormat.CHANNEL_OUT_SURROUND,
                AudioFormat.CHANNEL_OUT_MONO,

        };
        for (int sampleRate : SAMPLE_RATES) {
            for (int channel : channels) {
                AudioFormat audioFormat = new AudioFormat.Builder()
                        .setEncoding(encoding)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channel)
                        .build();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (AudioTrack.isDirectPlaybackSupported(audioFormat, audioAttributes)) {
                        ASPlayerLog.d("AudioCodecRendererV3-%d audio format, encoding: %d, samplerate: %d, channelMask: %d",
                                mId, encoding, sampleRate, channel);
                        ASPlayerLog.d("AudioCodecRendererV3-%d select audio format: %s", mId, audioFormat);
                        return audioFormat;
                    }
                }
            }
        }

        return new AudioFormat.Builder()
                .setEncoding(encoding)
                .setSampleRate(48000)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build();
    }

    @Override
    public void configure(MediaFormat format, MediaDescrambler descrambler) {
        ASPlayerLog.i("AudioCodecRendererV3-%d configure", mId);
        releaseDecoderThread();
        AudioUtils.releaseAudioTrack(mAudioTrack);
        mAudioTrack = null;

        int pipMode = mTargetPIPMode;
        ASPlayerLog.i("AudioCodecRendererV3-%d source format:%s", mId, format);
        try {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build();

            AudioTrack.Builder builder = new AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setBufferSizeInBytes(AUDIO_BUFFER_SIZE * 10);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    builder.setOffloadedPlayback(true);
            }

            ASPlayerLog.i("AudioCodecRendererV3-%d audio filter idt:%d, av sync id: %d", mId, mAudioFilterId, mAvSyncHwId);
            TunerHelper.AudioTrack.setTunerConfiguration(builder, mAudioFilterId, mAvSyncHwId);

            AudioFormat audioFormat = null;
            if (TunerHelper.TunerVersionChecker
                    .isHigherOrEqualVersionTo(TunerHelper.TunerVersionChecker.TUNER_VERSION_1_1)) {
                ASPlayerLog.i("AudioCodecRendererV3-%d tuner version >= 1.1", mId);
                audioFormat = getAudioFormat(audioAttributes, AudioFormat.ENCODING_DEFAULT);
            } else {
                ASPlayerLog.i("AudioCodecRendererV3-%d get tuner version failed, or tuner version < 1.1", mId);
                audioFormat = getAudioFormat(audioAttributes, AudioUtils.getEncoding(format));
            }
            builder.setAudioFormat(audioFormat);

            mAudioTrack = builder.build();
            ASPlayerLog.i("AudioCodecRendererV3-%d create AudioTrack success: %s", mId, mAudioTrack);

            prepareAudioTrack();

            ASPlayerLog.i("AudioCodecRendererV3-%d configure AudioTrack, pipMode: %d", mId, pipMode);

            if (pipMode == PIPMode.NORMAL) {
                ASPlayerLog.i("AudioCodecRendererV3-%d set volume %f", mId, mGain);
                mAudioTrack.setVolume(mGain);
                if (mClock.getSpeed() == 0) {
                    ASPlayerLog.i("AudioCodecRendererV3-%d AudioTrack.pause", mId);
                    mAudioTrack.pause();
                } else {
                    ASPlayerLog.i("AudioCodecRendererV3-%d AudioTrack.play", mId);
                    mAudioTrack.play();
                }
            } else if (pipMode == PIPMode.PIP) {
                ASPlayerLog.i("AudioCodecRendererV3-%d set volume 0", mId);
                mAudioTrack.setVolume(0.f);
                ASPlayerLog.i("AudioCodecRendererV3-%d AudioTrack.stop", mId);
                mAudioTrack.stop();
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                mAudioTrack.setAudioDescriptionMixLeveldB(mMixLevel);
            }

            if (pipMode == PIPMode.NORMAL) {
                startDecoderThread();
            }

            mAudioFormat = audioFormat;

            mLastPIPMode = pipMode;
        } catch (Exception exception) {
            ASPlayerLog.w("AudioCodecRendererV3-%d Failed to configure AudioTrack: %s", mId, exception.getMessage());
            mErrorMessage = exception.toString();
            releaseDecoderThread();
            AudioUtils.releaseAudioTrack(mAudioTrack);
            mAudioTrack = null;
        }
    }

    private void prepareAudioTrack() {
        long timeout = SystemClock.elapsedRealtime() + AUDIO_TRACK_PREPARE_TIMEOUT;
        while (true) {
            if (SystemClock.elapsedRealtime() >= timeout || !write()) {
                break;
            }
        }
    }

    private boolean write() {
        int written;
        int expectedToWrite;

        if (mEmptyPacket.remaining() <= 0) {
            mEmptyPacket.rewind();
        }
        if (mEmptyPacket.remaining() >= AUDIO_BUFFER_SIZE) {
            checkMetadata();
        }

        if (mMetadataPacket.remaining() > 0) {
            expectedToWrite = mMetadataPacket.remaining();
            written = mAudioTrack.write(mMetadataPacket, expectedToWrite,
                    AudioTrack.WRITE_NON_BLOCKING);
            ASPlayerLog.i("AudioCodecRendererV3-%d meta expected: %d, meta written: %d",
                            mId, expectedToWrite, written);
        } else {
            expectedToWrite = mEmptyPacket.remaining();
            written = mAudioTrack.write(mEmptyPacket, expectedToWrite,
                    AudioTrack.WRITE_NON_BLOCKING);
            if (expectedToWrite != AUDIO_BUFFER_SIZE ||
                    expectedToWrite != written && written > 0) {
                ASPlayerLog.i("AudioCodecRendererV3-%d expected: %d, written: %d",
                                mId, expectedToWrite, written);
            }
        }

        return written == expectedToWrite;
    }

    private void checkMetadata() {
        synchronized (mMetadata) {
            if (mMetadata.size() > 0) {
                EncapsulationEncoder.encodePacket(mMetadataPacket, mMetadata, AUDIO_BUFFER_SIZE);
                mMetadata.clear();
            }
        }
    }

    class DecoderThread extends Thread {
        @Override
        public void run() {
            super.run();
            ASPlayerLog.i("AudioCodecRendererV3-%d DecoderThread started.", mId);

            long mLastRenderNotificationTime = 0;

            synchronized (mLock) {
                while (!isInterrupted()) {
                    write();

                    if ((SystemClock.elapsedRealtime() - mLastRenderNotificationTime > 500)
                            && mAudioTrack.getTimestamp(timestamp)) {
                        mHandler.post(() -> {
                            if (mOutputBufferListener != null) {
                                mOutputBufferListener
                                        .onRender(0, timestamp.nanoTime / 1000);
                            }
                        });
                        mLastRenderNotificationTime = SystemClock.elapsedRealtime();
                    }

                    try {
                        mLock.wait(100);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            ASPlayerLog.i("AudioCodecRendererV3-%d DecoderThread exiting..", mId);
        }
    }

    private void startDecoderThread() {
        ASPlayerLog.i("AudioCodecRendererV3-%d start decoder thread", mId);
        if (mDecoderThread != null) {
            releaseDecoderThread();
        }
        mDecoderThread = new DecoderThread();
        mDecoderThread.start();
    }

    private void releaseDecoderThread() {
        if (mDecoderThread != null) {
            ASPlayerLog.i("AudioCodecRendererV3-%d release decoder thread", mId);
            mDecoderThread.interrupt();
            try {
                mDecoderThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
            mDecoderThread = null;
        }
    }

    @Override
    public void setSpeed(double speed) {
        if (mAudioTrack == null)
            return;
        if (speed != 0 && speed != 1) {
            ASPlayerLog.w("AudioCodecRendererV3-%d unexpected speed %f, should be 0 or 1", mId, speed);
            return;
        }
        mClock.setSpeed(speed);
        if (speed == 0) {
            mAudioTrack.pause();
        } else {
            mAudioTrack.play();
        }
    }

    @Override
    public void pause() {
        if (mAudioTrack != null) {
            mAudioTrack.pause();
        }
        mPaused = true;
    }

    @Override
    public void resume() {
        if (mPaused) {
            if (mAudioTrack != null) {
                mAudioTrack.play();
            }
            mPaused = false;
        }
    }

    @Override
    public void release() {
//        TvLog.i("AudioCodecRendererV3-%d release", mId);
        releaseDecoderThread();

        AudioUtils.releaseAudioTrack(mAudioTrack);
        mAudioTrack = null;
        mOutputBufferListener = null;
        mErrorMessage = null;
    }

    @Override
    public void reset() {
        synchronized (mLock) {
            if (mAudioTrack != null) {
                boolean isPlaying = mClock.getSpeed() != 0;
                if (isPlaying) mAudioTrack.pause();
                mAudioTrack.flush();
                if (isPlaying) mAudioTrack.play();
            }
        }
    }

    @Override
    public AudioFormat getAudioFormat() {
        return mAudioFormat;
    }

    @Override
    public void checkErrors() {
        if (mErrorMessage != null) {
            ASPlayerLog.w("AudioCodecRendererV3-%d error %s, reset ", mId, mErrorMessage);
            release();
        }
    }

    @Override
    public String getErrorMessage() {
        return mErrorMessage;
    }

    @Override
    public boolean hasInputBuffer() {
        return true;
    }

    @Override
    public int getNextInputBufferIndex() {
        return 0;
    }

    @Override
    public ByteBuffer getInputBuffer(int index) {
        return null;
    }

    @Override
    public void queueInputBuffer(int index, int offset, int size, long timestampUs, int flags) {

    }

    @Override
    public boolean hasOutputBuffer() {
        return false;
    }

    @Override
    public int getNbOutputBuffers() {
        return 0;
    }

    @Override
    public long getNextOutputTimestampUs() {
        return 0;
    }

    @Override
    public long getMarginUs() {
        return 0;
    }

    @Override
    public long getDisplayPositionUs() {
        return 0;
    }

    @Override
    public boolean isDisplayPositionValid() {
        return true;
    }

    @Override
    public boolean isPlaying() {
        return mAudioTrack != null && mAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING;
    }

    private boolean switchAudioTrackMode(boolean cache) {
        ASPlayerLog.i("AudioCodecRendererV3-%d switchAudioTrackMode type: %s", mId, cache ? "cache" : "normal");
        if (cache) {
            ASPlayerLog.w("AudioCodecRendererV3-%d AudioTrack.setVolume(0)", mId);
            mAudioTrack.setVolume(0.f);
            ASPlayerLog.w("AudioCodecRendererV3-%d AudioTrack.stop", mId);
            mAudioTrack.stop();
            mErrorMessage = null;

            releaseDecoderThread();
        } else {
            ASPlayerLog.w("AudioCodecRendererV3-%d AudioTrack.setVolume: %.2f", mId, mGain);
            mAudioTrack.setVolume(mGain);
            ASPlayerLog.w("AudioCodecRendererV3-%d AudioTrack.play", mId);
            mAudioTrack.play();
            mErrorMessage = null;

            startDecoderThread();
        }
        return true;
    }

    @Override
    public boolean setPIPMode(int pipMode) {
        mTargetPIPMode = pipMode;
        ASPlayerLog.i("AudioCodecRendererV3-%d setPIPMode: %d, last pip mode: %d", mId, pipMode, mLastPIPMode);
        boolean success = switchPipMode(mTargetPIPMode);
        if (success) {
            mLastPIPMode = mTargetPIPMode;
        }
        ASPlayerLog.i("AudioCodecRendererV3-%d setPIPMode to %d result: %s", mId, pipMode, success ? "success" : "failed");
        return success;
    }

    private boolean switchPipMode(int pipMode) {
        if (pipMode == mLastPIPMode) {
            return true;
        } else if (mAudioTrack == null) {
            return false;
        }

        boolean success = false;
        if (pipMode == PIPMode.NORMAL) {
            success = switchAudioTrackMode(false);
        } else if (pipMode == PIPMode.PIP) {
            success = switchAudioTrackMode(true);
        }

        return success;
    }
}