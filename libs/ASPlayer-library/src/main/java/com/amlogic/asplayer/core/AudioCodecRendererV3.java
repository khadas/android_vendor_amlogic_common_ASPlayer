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


import com.amlogic.asplayer.api.AudioParams;
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
    private float mSubAudioVolumeDb = 0.f;
    private final MediaClock mClock;
    private int mAudioFilterId = INVALID_FILTER_ID;
    private int mAvSyncHwId = INVALID_AV_SYNC_HW_ID;
    private AudioTrack mAudioTrack;
    private DecoderThread mDecoderThread;
    private final Object mLock;
    private static final long AUDIO_TRACK_PREPARE_TIMEOUT = 200;
    private static final int AUDIO_BUFFER_SIZE = 256;
    private OutputBufferListener mOutputBufferListener;
    private AudioFormatListener mAudioFormatListener;
    private AudioTimestamp timestamp = new AudioTimestamp();
    private final ByteBuffer mEmptyPacket = ByteBuffer.allocate(AUDIO_BUFFER_SIZE);
    private final ByteBuffer mMetadataPacket = ByteBuffer.allocate(AUDIO_BUFFER_SIZE);
    private final List<Metadata> mMetadata = new ArrayList<>();
    private String mErrorMessage;

    private int mLastWorkMode = -1;
    private int mTargetWorkMode = mLastWorkMode;
    private int mSyncInstanceId = Constant.INVALID_SYNC_INSTANCE_ID;

    private int mLastPIPMode = -1;
    private int mTargetPIPMode = mLastPIPMode;

    private boolean mPaused;

    private AudioFormat mAudioFormat;

    AudioCodecRendererV3(int id, MediaClock clock, Handler handler) {
        mId = id;
        mLock = new Object();
        mHandler = handler;
        mGain = 1.f;
        mClock = clock;
        EncapsulationEncoder.encodeEmptyPacket(mEmptyPacket, AUDIO_BUFFER_SIZE);

        mPaused = false;
    }

    @Override
    public void setSyncInstanceId(int syncInstanceId) {
        mSyncInstanceId = syncInstanceId;
    }

    @Override
    public void setOutputBufferListener(OutputBufferListener listener) {
        mOutputBufferListener = listener;
    }

    @Override
    public void setAudioFormatListener(AudioFormatListener listener) {
        mAudioFormatListener = listener;
    }

    @Override
    public void setClockOrigin(long timestampUs) {

    }

    @Override
    public void setAudioSessionId(int sessionId) {

    }

    public void setAudioFilterId(int filterId) {
        ASPlayerLog.i("%s set audio filter id: 0x%016x", getTag(), filterId);
        mAudioFilterId = filterId;
    }

    public void writeMetadata(Metadata metadata) {
        ASPlayerLog.i("%s metadata: %s", getTag(), metadata);
        synchronized (mMetadata) {
//            mMetadata.removeIf(item -> item.getClass().equals(metadata.getClass()));
            mMetadata.add(metadata);
        }
    }

    @Override
    public void setSubAudioVolumeDb(float volumeDb) {
        mSubAudioVolumeDb = volumeDb;
        setAudioDescriptionVolume(mSubAudioVolumeDb);
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
        ASPlayerLog.i("%s setVolume: %.2f", getTag(), gain);
        mGain = gain;
        setAudioTrackVolume(mGain);
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
                        ASPlayerLog.d("%s audio format, encoding: %d, samplerate: %d, channelMask: %d",
                                getTag(), encoding, sampleRate, channel);
                        ASPlayerLog.d("%s select audio format: %s", getTag(), audioFormat);
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
    public void configure(AudioParams audioParams, MediaDescrambler descrambler) {
        ASPlayerLog.i("%s configure", getTag());
        releaseDecoderThread();
        AudioUtils.releaseAudioTrack(mAudioTrack);
        mAudioTrack = null;

        int workMode = mTargetWorkMode;
        int pipMode = mTargetPIPMode;

        ASPlayerLog.i("%s source format:%s", getTag(), audioParams);
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

            ASPlayerLog.i("%s audio filter id: 0x%016x, avSyncId: 0x%x", getTag(), mAudioFilterId, mAvSyncHwId);
            TunerHelper.AudioTrack.setTunerConfiguration(builder, mAudioFilterId, mAvSyncHwId);

            AudioFormat audioFormat = null;
            if (TunerHelper.TunerVersionChecker
                    .isHigherOrEqualVersionTo(TunerHelper.TunerVersionChecker.TUNER_VERSION_1_1)) {
                ASPlayerLog.i("%s tuner version >= 1.1", getTag());
                audioFormat = getAudioFormat(audioAttributes, AudioFormat.ENCODING_DEFAULT);
            } else {
                ASPlayerLog.i("%s get tuner version failed, or tuner version < 1.1", getTag());
                int encodingType = AudioUtils.getEncoding(audioParams);
                ASPlayerLog.i("%s encodingType: %d", getTag(), encodingType);
                audioFormat = getAudioFormat(audioAttributes, encodingType);
            }
            builder.setAudioFormat(audioFormat);

            mAudioTrack = builder.build();
            ASPlayerLog.i("%s create AudioTrack success: %s", getTag(), mAudioTrack);

            prepareAudioTrack();

            ASPlayerLog.i("%s configure AudioTrack, workMode: %d, pipMode: %d", getTag(), workMode, pipMode);

            mAudioFormat = audioFormat;
        } catch (Exception exception) {
            ASPlayerLog.w("%s Failed to configure AudioTrack: %s", getTag(), exception.getMessage());
            mErrorMessage = exception.toString();
            AudioUtils.releaseAudioTrack(mAudioTrack);
            mAudioTrack = null;
        }
    }

    @Override
    public void start() {
        mPaused = false;
        int workMode = mTargetWorkMode;
        int pipMode = mTargetPIPMode;

        ASPlayerLog.i("%s start, workMode: %d, pipMode: %d", getTag(), workMode, pipMode);

        try {
            if (workMode == WorkMode.NORMAL && pipMode == PIPMode.NORMAL) {
                setAudioTrackVolume(mGain);

                setAudioDescriptionVolume(mSubAudioVolumeDb);

                if (mClock.getSpeed() == 0) {
                    pauseAudioTrack();
                } else {
                    startAudioTrack();
                }
            } else if (workMode == WorkMode.CACHING_ONLY || pipMode == PIPMode.PIP) {
                setAudioTrackVolume(0.f);
                stopAudioTrack();
            }

            if (workMode == WorkMode.NORMAL && pipMode == PIPMode.NORMAL) {
                startDecoderThread();
            }

            mLastWorkMode = workMode;
            mLastPIPMode = pipMode;
        } catch (Exception exception) {
            ASPlayerLog.w("%s Failed to start AudioTrack: %s", getTag(), exception.getMessage());
            mErrorMessage = exception.toString();
            releaseDecoderThread();
            AudioUtils.releaseAudioTrack(mAudioTrack);
            mAudioTrack = null;
        }
    }

    private void setAudioDescriptionVolume(float volumeDb) {
        if (mAudioTrack != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (volumeDb < AudioUtils.VOLUME_MIN_DB) {
                volumeDb = AudioUtils.VOLUME_MIN_DB;
            } else if (volumeDb > AudioUtils.VOLUME_MAX_DB) {
                volumeDb = AudioUtils.VOLUME_MAX_DB;
            }
            mAudioTrack.setAudioDescriptionMixLeveldB(volumeDb);
            ASPlayerLog.i("%s setAudioDescriptionMixLeveldB: %.2f", getTag(), volumeDb);
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
            ASPlayerLog.i("%s meta expected: %d, meta written: %d",
                            getTag(), expectedToWrite, written);
        } else {
            expectedToWrite = mEmptyPacket.remaining();
            written = mAudioTrack.write(mEmptyPacket, expectedToWrite,
                    AudioTrack.WRITE_NON_BLOCKING);
            if (expectedToWrite != AUDIO_BUFFER_SIZE ||
                    expectedToWrite != written && written > 0) {
                ASPlayerLog.i("%s expected: %d, written: %d",
                                getTag(), expectedToWrite, written);
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
            ASPlayerLog.i("%s DecoderThread started.", getTag());

            long mLastRenderNotificationTime = 0;

            synchronized (mLock) {
                while (!isInterrupted()) {
                    write();

                    if ((SystemClock.elapsedRealtime() - mLastRenderNotificationTime > 500)
                            && mAudioTrack.getTimestamp(timestamp)) {
                        mHandler.post(() -> {
                            if (!mPaused && mOutputBufferListener != null) {
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
            ASPlayerLog.i("%s DecoderThread exiting..", getTag());
        }
    }

    private void startDecoderThread() {
        ASPlayerLog.i("%s start decoder thread", getTag());
        if (mDecoderThread != null) {
            releaseDecoderThread();
        }
        mDecoderThread = new DecoderThread();
        mDecoderThread.start();
    }

    private void releaseDecoderThread() {
        if (mDecoderThread != null) {
            ASPlayerLog.i("%s release decoder thread", getTag());
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
        ASPlayerLog.i("%s setSpeed %f", getTag(), speed);
        if (mAudioTrack == null)
            return;
        if (speed != 0 && speed != 1) {
            ASPlayerLog.w("%s unexpected speed %f, should be 0 or 1", getTag(), speed);
            return;
        }
        mClock.setSpeed(speed);
        if (speed == 0) {
            pauseAudioTrack();
        } else {
            if (!mPaused) {
                startAudioTrack();
            }
        }
    }

    @Override
    public void pause() {
        ASPlayerLog.i("%s pause start", getTag());
        pauseAudioTrack();
        mPaused = true;
    }

    @Override
    public void resume() {
        ASPlayerLog.i("%s resume start, paused: %b", getTag(), mPaused);
        if (mPaused) {
            startAudioTrack();
            mPaused = false;
        }
    }

    @Override
    public void release() {
        ASPlayerLog.i("%s release", getTag());
        releaseDecoderThread();

        AudioUtils.releaseAudioTrack(mAudioTrack);
        mAudioTrack = null;
        mOutputBufferListener = null;
        mErrorMessage = null;
    }

    @Override
    public void reset() {
        ASPlayerLog.i("%s reset", getTag());
        synchronized (mLock) {
            if (mAudioTrack != null) {
                boolean isPlaying = mClock.getSpeed() != 0;
                if (isPlaying) {
                    pauseAudioTrack();
                }

                flushAudioTrack();

                if (isPlaying) {
                    startAudioTrack();
                }
            }
        }
    }

    private void startAudioTrack() {
        if (mAudioTrack != null) {
            ASPlayerLog.i("%s AudioTrack.play: %s", getTag(), mAudioTrack);
            mAudioTrack.play();
        }
    }

    private void pauseAudioTrack() {
        if (mAudioTrack != null) {
            ASPlayerLog.i("%s AudioTrack.pause: %s", getTag(), mAudioTrack);
            mAudioTrack.pause();
        }
    }

    private void flushAudioTrack() {
        if (mAudioTrack != null) {
            ASPlayerLog.i("%s AudioTrack.flush: %s", getTag(), mAudioTrack);
            mAudioTrack.flush();
        }
    }

    private void stopAudioTrack() {
        if (mAudioTrack != null) {
            ASPlayerLog.i("%s AudioTrack.stop: %s", getTag(), mAudioTrack);
            mAudioTrack.stop();
        }
    }

    private void setAudioTrackVolume(float volume) {
        if (mAudioTrack != null) {
            ASPlayerLog.i("%s AudioTrack.setVolume(%.2f)", getTag(), volume);
            int ret = mAudioTrack.setVolume(volume);
            if (ret != AudioTrack.SUCCESS) {
                ASPlayerLog.e("%s AudioTrack.setVolume(%.2f) failed, ret: %d", getTag(), volume, ret);
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
            ASPlayerLog.w("%s error %s, reset ", getTag(), mErrorMessage);
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

    @Override
    public boolean setWorkMode(int workMode) {
        mTargetWorkMode = workMode;

        ASPlayerLog.i("%s setWorkMode: %d, last mode: %d, audio track: %s", getTag(), workMode, mLastWorkMode, mAudioTrack);

        boolean success = switchWorkMode(mTargetWorkMode);
        if (success) {
            mLastWorkMode = mTargetWorkMode;
        }
        ASPlayerLog.i("%s setWorkMode to %d result: %s", getTag(), workMode, success ? "success" : "failed");
        return success;
    }

    private boolean switchWorkMode(int workMode) {
        ASPlayerLog.i("%s switchWorkMode: %d, last mode: %d, audio track: %s", getTag(), workMode, mLastWorkMode, mAudioTrack);

        if (mLastWorkMode == workMode) {
            return true;
        } else if (mAudioTrack == null) {
            return false;
        }

        boolean success = false;
        if (workMode == WorkMode.CACHING_ONLY) {
            success = switchAudioTrackMode(true);
        } else if (workMode == WorkMode.NORMAL) {
            success = switchAudioTrackMode(false);
        }

        return success;
    }

    private boolean switchAudioTrackMode(boolean cache) {
        ASPlayerLog.i("%s switchAudioTrackMode type: %s", getTag(), cache ? "cache" : "normal");
        if (cache) {
            setAudioTrackVolume(0.f);
            stopAudioTrack();
            mErrorMessage = null;

            releaseDecoderThread();
        } else {
            setAudioTrackVolume(mGain);
            startAudioTrack();

            mErrorMessage = null;

            startDecoderThread();
        }
        return true;
    }

    @Override
    public boolean setPIPMode(int pipMode) {
        mTargetPIPMode = pipMode;
        ASPlayerLog.i("%s setPIPMode: %d, last pip mode: %d", getTag(), pipMode, mLastPIPMode);
        boolean success = switchPipMode(mTargetPIPMode);
        if (success) {
            mLastPIPMode = mTargetPIPMode;
        }
        ASPlayerLog.i("%s setPIPMode to %d result: %s", getTag(), pipMode, success ? "success" : "failed");
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

    private String getTag() {
        return String.format("[No-%d]-[%d]AudioCodecRendererV3", mSyncInstanceId, mId);
    }
}