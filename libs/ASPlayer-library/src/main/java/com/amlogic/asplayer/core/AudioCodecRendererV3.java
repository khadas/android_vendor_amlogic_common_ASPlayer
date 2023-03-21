package com.amlogic.asplayer.core;

import android.annotation.SuppressLint;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTimestamp;
import android.media.AudioTrack;
import android.media.MediaDescrambler;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;


import com.amlogic.asplayer.api.WorkMode;
import com.amlogic.asplayer.core.encapsulation.EncapsulationEncoder;
import com.amlogic.asplayer.core.encapsulation.Metadata;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;


public class AudioCodecRendererV3 implements AudioCodecRenderer {
    private static final int[] SAMPLE_RATES = { 48000, 44100, 32000 };

    private int mId;
    private final Handler mHandler;
    private AudioCaps mAudioCaps;
    private float mGain;
    private float mMixLevel;
    private final MediaClock mClock;
    private int mAudioFilterId;
    private int mAvSyncHwId;
    private AudioTrack mAudioTrack;
    private int mInputEncoding;
    DecoderThread mDecoderThread;
    private static final int AUDIO_BUFFER_SIZE = 256;
    private OutputBufferListener mOutputBufferListener;
    private AudioTimestamp timestamp = new AudioTimestamp();
    private long mLastRenderedTimeUs;
    private final ByteBuffer mEmptyPacket = ByteBuffer.allocate(AUDIO_BUFFER_SIZE);
    private final ByteBuffer mMetadataPacket = ByteBuffer.allocate(AUDIO_BUFFER_SIZE);
    private final List<Metadata> mMetadata = new ArrayList<>();
    private String mErrorMessage;

    AudioCodecRendererV3(int id, MediaClock clock, Handler handler) {
        mId = id;
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

        ASPlayerLog.i("AudioCodecRendererV3-%d source format:%s", mId, format);
        try {
            mInputEncoding = AudioUtils.getEncoding(format);

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build();

            AudioFormat audioFormat = getAudioFormat(audioAttributes, mInputEncoding);
            ASPlayerLog.i("AudioCodecRendererV3-%d audio format: %s", mId, audioFormat);
            @SuppressLint("WrongConstant")
            AudioTrack.Builder builder = new AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(AUDIO_BUFFER_SIZE * 10);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    builder.setOffloadedPlayback(true);
            }

            ASPlayerLog.i("AudioCodecRendererV3-%d audio filter idt:%d, av sync id: %d", mId, mAudioFilterId, mAvSyncHwId);
            TunerHelper.AudioTrack.setTunerConfiguration(builder, mAudioFilterId, mAvSyncHwId);

            mAudioTrack = builder.build();
            ASPlayerLog.i("AudioCodecRendererV3-%d create AudioTrack success: %s", mId, mAudioTrack);

            ASPlayerLog.i("AudioCodecRendererV3-%d set volume %f", mId, mGain);
            mAudioTrack.setVolume(mGain);
            if (mClock.getSpeed() == 0) {
                mAudioTrack.pause();
            } else {
                ASPlayerLog.i("AudioCodecRendererV3-%d AudioTrack play", mId);
                mAudioTrack.play();
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                mAudioTrack.setAudioDescriptionMixLeveldB(mMixLevel);
            }

            startDecoderThread();
        } catch (Exception exception) {
            ASPlayerLog.w("AudioCodecRendererV3-%d Failed to configure AudioTrack: %s", mId, exception.getMessage());
            mErrorMessage = exception.toString();
            releaseDecoderThread();
            AudioUtils.releaseAudioTrack(mAudioTrack);
            mAudioTrack = null;
        }
    }

    class DecoderThread extends Thread {
        @Override
        public void run() {
            super.run();
            ASPlayerLog.i("AudioCodecRendererV3-%d DecoderThread started.", mId);

            long mLastRenderNotificationTime = 0;
            List<Metadata> metadataList = new ArrayList<>();
            while (!isInterrupted()) {
                int written;
                int expectedToWrite;

                if (mEmptyPacket.remaining() <= 0) {
                    mEmptyPacket.rewind();
                }
                if (mEmptyPacket.remaining() >= AUDIO_BUFFER_SIZE) {
                    checkMetadata(metadataList);
                }

                if (mMetadataPacket.remaining() > 0) {
                    expectedToWrite = mMetadataPacket.remaining();
                    written = mAudioTrack.write(mMetadataPacket, expectedToWrite,
                            AudioTrack.WRITE_NON_BLOCKING);
                    ASPlayerLog.i("AudioCodecRendererV3-%d meta expected: %d, meta written: %d",
                            mId, expectedToWrite, written);
                } else {
                    expectedToWrite = mEmptyPacket.remaining();

//                    TvLog.i("AudioCodecRendererV3-%d write %d bytes", mId, expectedToWrite);
                    written = mAudioTrack.write(mEmptyPacket, expectedToWrite,
                            AudioTrack.WRITE_NON_BLOCKING);
                    if (expectedToWrite != AUDIO_BUFFER_SIZE ||
                            expectedToWrite != written && written > 0) {
                        ASPlayerLog.i("AudioCodecRendererV3-%d expected: %d, written: %d",
                                mId, expectedToWrite, written);
                    }
                }

                if ((SystemClock.elapsedRealtime() - mLastRenderNotificationTime > 500)
                        && mAudioTrack.getTimestamp(timestamp)) {
                    if (mOutputBufferListener != null) {
                        mHandler.post(()-> {
//                            TvLog.i("AudioCodecRendererV3-%d decoder thread", mId);
                            mOutputBufferListener.onRender(timestamp.nanoTime / 1000);
                        });
                        mLastRenderedTimeUs = timestamp.nanoTime / 1000;
                    }
                    mLastRenderNotificationTime = SystemClock.elapsedRealtime();
                }

                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            ASPlayerLog.i("AudioCodecRendererV3-%d DecoderThread exiting..", mId);
        }

        private void checkMetadata(List<Metadata> metadataList) {
            metadataList.clear();
            synchronized (mMetadata) {
                metadataList.addAll(mMetadata);
                mMetadata.clear();
            }
            EncapsulationEncoder.encodePacket(mMetadataPacket, metadataList, AUDIO_BUFFER_SIZE);
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
    public void release() {
//        TvLog.i("AudioCodecRendererV3-%d release", mId);
        releaseDecoderThread();

        AudioUtils.releaseAudioTrack(mAudioTrack);
        mAudioTrack = null;
        mInputEncoding = AudioFormat.ENCODING_INVALID;

        mErrorMessage = null;
    }

    @Override
    public void reset() {
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
}
