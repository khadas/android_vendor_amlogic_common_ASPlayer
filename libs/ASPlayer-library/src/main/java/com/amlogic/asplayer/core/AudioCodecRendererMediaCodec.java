package com.amlogic.asplayer.core;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaDescrambler;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;


import com.amlogic.asplayer.api.WorkMode;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Locale;

import static android.media.MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback;

/**
 * AudioCodecRenderer that uses MediaCodec to decode audio
 */
class AudioCodecRendererMediaCodec implements AudioCodecRenderer {

    private static final int MAX_BUFFER_INFOS = 128;

    private static final int DEFAULT_INJECTION_BUFFER_SIZE = 1024 * 10;
    private static final int DEFAULT_MARGIN_US = 200000;

    private Handler mHandler;
    private MediaCodec mMediaCodec;
    private String mErrorMessage;

    private ArrayDeque<Integer> mInputBufferIndexes;

    private ArrayDeque<Integer> mOutputBufferIndexes;
    private BufferInfo[] mOutputBufferInfos;

    private AudioTrack mAudioTrack;
    private long mNbBytesPushed;
    private boolean mPrimingDone;
    private long mFirstTimestampUs;
    private int mAudioTrackBufferSize;
    private long mAudioTrackBufferDurationUs;
    private int mAudioSessionId;
    private boolean mTunneledPlayback;
    private long mLastUnderrunCount;

    private float mGain;

    // intermediate output buffer used to inject data in audio track
    private ByteBuffer mInjectionBuffer;

    private MediaClock mClock;

    private OutputBufferListener mOutputBufferListener;

    private long mPushedDataInBytes;

    private AudioPositionTracker mAudioPositionTracker;

    private AudioCaps mAudioCaps;

    private class MediaCodecCallback extends MediaCodec.Callback {
        @Override
        public void onInputBufferAvailable(MediaCodec mediaCodec, int index) {
            if (mMediaCodec != null && mMediaCodec.equals(mediaCodec)) {
                mInputBufferIndexes.add(index);
            }
        }

        @Override
        public void onOutputBufferAvailable(MediaCodec mediaCodec, int index, MediaCodec.BufferInfo bufferInfo) {
            // ignore outputbuffer, until audio track is created
            if (mAudioTrack == null) {
                ASPlayerLog.w("track null, ignore index:%d", index);
                if (mMediaCodec != null)
                    mMediaCodec.releaseOutputBuffer(index, false);
                return;
            }

            BufferInfo info = null;
            if (index >= 0 && index < mOutputBufferInfos.length) {
                info = mOutputBufferInfos[index];
            }
            if (info == null) {
                ASPlayerLog.w("emergency exit: either index<%d> is incorrect, or callback called after release()", index);
                return;
            }
            info.flags = bufferInfo.flags;
            info.offset = bufferInfo.offset;
            info.size = bufferInfo.size;
            info.presentationTimeUs = bufferInfo.presentationTimeUs;
            mOutputBufferIndexes.add(index);
        }

        @Override
        public void onError(MediaCodec mediaCodec, MediaCodec.CodecException e) {
            mErrorMessage = e.getMessage();
        }

        @Override
        public void onOutputFormatChanged(MediaCodec mediaCodec, MediaFormat format) {
            int newSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int newChannelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int newEncoding;
            if (format.containsKey(MediaFormat.KEY_PCM_ENCODING))
                newEncoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING);
            else
                newEncoding = AudioFormat.ENCODING_PCM_16BIT;

            mPushedDataInBytes = 0;
            mLastUnderrunCount = 0;
            createAudioTrack(newEncoding, newSampleRate, newChannelCount);
            if (mAudioTrack != null) {
                mAudioPositionTracker.setAudioTrack(mAudioTrack, format);
                // pause (to avoid glitch).
                // track will be resume as soon as a new output buffer will be played
                mAudioTrack.pause();
                mAudioPositionTracker.suspend();
                mAudioTrack.setVolume(mGain);
            } else {
                mErrorMessage = String.format(Locale.US,
                        "can't create audio track [encoding:%d, sample-rate:%d, channel-count:%d",
                        newEncoding, newSampleRate, newChannelCount);
            }
        }
    }

    AudioCodecRendererMediaCodec(MediaClock clock, Handler handler) {
        mHandler = handler;
        mInputBufferIndexes = new ArrayDeque<>();
        mOutputBufferIndexes = new ArrayDeque<>();
        mOutputBufferInfos = new BufferInfo[MAX_BUFFER_INFOS];
        fillOutputBufferInfos();
        mGain = 1.f;
        mClock = clock;
        mInjectionBuffer = ByteBuffer.allocate(DEFAULT_INJECTION_BUFFER_SIZE);
        mAudioPositionTracker = new AudioPositionTracker();
    }

    private void fillOutputBufferInfos() {
        for (int i = 0; i < mOutputBufferInfos.length; ++i) {
            mOutputBufferInfos[i] = new BufferInfo();
        }
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
    public void setOutputBufferListener(OutputBufferListener listener) {
        mOutputBufferListener = listener;
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
    public void setClockOrigin(long timestampUs) {
        mAudioPositionTracker.stop();
        if (mAudioTrack != null && mAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            mAudioTrack.pause();
            mAudioPositionTracker.suspend();
        }
    }

    @Override
    public String getErrorMessage() {
        return mErrorMessage;
    }

    @Override
    public void checkErrors() {
        if (mErrorMessage != null) {
            ASPlayerLog.w("error %s, reset ", mErrorMessage);
            release();
        }
    }

    @Override
    public boolean hasInputBuffer() {
        return !mInputBufferIndexes.isEmpty();
    }

    @Override
    public int getNextInputBufferIndex() {
        if (mInputBufferIndexes.isEmpty())
            return -1;

        return mInputBufferIndexes.pop();
    }

    @Override
    public ByteBuffer getInputBuffer(int index) {
        if (index < 0)
            return null;
        else
            return mMediaCodec.getInputBuffer(index);
    }

    @Override
    public void queueInputBuffer(int index, int offset, int size, long timestampUs, int flags) {
        mMediaCodec.queueInputBuffer(index, offset, size, timestampUs, flags);
    }

    @Override
    public void queueSecureInputBuffer(int index, int offset, MediaCodec.CryptoInfo info, long presentationTimeUs, int flags) throws MediaCodec.CryptoException {
        mMediaCodec.queueSecureInputBuffer(index, offset, info, presentationTimeUs, flags);
    }

    @Override
    public void configure(MediaFormat format, MediaDescrambler descrambler) {
        ASPlayerLog.i("format:%s", format);
        if (mMediaCodec != null) {
            mMediaCodec.stop();
            mMediaCodec.setCallback(null);
        }
        mOutputBufferIndexes.clear();
        mInputBufferIndexes.clear();
        fillOutputBufferInfos();
        releaseAudioTrack();

        resetInjectionBuffer();

        String mimeType = format.getString(MediaFormat.KEY_MIME);

        boolean securePlayback;
        if (Build.VERSION.SDK_INT >= 30) {
            securePlayback =
                    format.containsFeature(FEATURE_SecurePlayback) && format.getFeatureEnabled(FEATURE_SecurePlayback);
        } else {
            securePlayback = descrambler != null;
        }
        try {
            MediaFormat adjustedMediaFormat = adjustMediaFormat(format);

            // find and init media codec
            mMediaCodec = MediaCodecUtils.findMediaCodec(adjustedMediaFormat, false, securePlayback);
            if (mMediaCodec != null) {
                ASPlayerLog.i("mimetype:%s, codec:%s", mimeType, mMediaCodec.getName());
                mMediaCodec.setCallback(new MediaCodecCallback(), mHandler);
                if (descrambler == null) {
                    mMediaCodec.configure(adjustedMediaFormat, null, null, 0);
                } else {
                    mMediaCodec.configure(adjustedMediaFormat, null, 0, descrambler);
                }
                mMediaCodec.start();
            } else {
                ASPlayerLog.w("codec not found for:%s", mimeType);
                mErrorMessage = String.format("no codec found for mime-type:%s", mimeType);
            }
        } catch (Exception exception) {
            ASPlayerLog.w("Failed to configure MediaCodec: %s", exception.getMessage());
            if (mMediaCodec != null)
                mMediaCodec.release();
            mErrorMessage = exception.getMessage();
        }
    }

    @Override
    public void release() {
        if (mMediaCodec != null)
            mMediaCodec.release();
        mMediaCodec = null;

        releaseAudioTrack();

        mInputBufferIndexes.clear();
        mOutputBufferIndexes.clear();
        Arrays.fill(mOutputBufferInfos, null);

        mErrorMessage = null;

        resetInjectionBuffer();

        mClock.reset();

        mAudioPositionTracker.stop();
    }

    @Override
    public void reset() {
        if (mMediaCodec != null)
            mMediaCodec.release();
        mMediaCodec = null;

        mInputBufferIndexes.clear();
        mOutputBufferIndexes.clear();
        Arrays.fill(mOutputBufferInfos, null);

        mErrorMessage = null;

        resetInjectionBuffer();

        mClock.reset();
        mAudioPositionTracker.stop();

        releaseAudioTrack();
    }

    @Override
    public int getNbOutputBuffers() {
        return mOutputBufferIndexes.size();
    }

    @Override
    public boolean hasOutputBuffer() {
        return !mOutputBufferIndexes.isEmpty();
    }

    @Override
    public long getMarginUs() {
        return DEFAULT_MARGIN_US;
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
    public long getNextOutputTimestampUs() {
        AudioCodecRenderer.BufferInfo info = peekOutputBufferInfo();
        if (info == null)
            return 0;
        else
            return info.presentationTimeUs;
    }

    @Override
    public long renderFreeRun() {
        if (mOutputBufferIndexes.isEmpty())
            return 10000;

        mAudioPositionTracker.updatePosition();

        if (mAudioTrack.getPlaybackRate() != AudioTrack.PLAYSTATE_PLAYING) {
            mAudioTrack.play();
            mAudioPositionTracker.resume();
        }

        return render();
    }

    @Override
    public long renderSynchro(long inputQueueSizeUs) {
        if (mOutputBufferIndexes.isEmpty())
            return 10000;

        mAudioPositionTracker.updatePosition();

        // get output buffer info
        BufferInfo bufferInfo = peekOutputBufferInfo();
        if (bufferInfo == null)
            return 10000;

        // compute buffer duration
        long bufferDurationUs = AudioUtils.getBufferDurationUs(bufferInfo.size,
                mAudioTrack.getSampleRate(),
                mAudioTrack.getChannelCount());

        // compute delta between timestamp and clock
        long timeUs = mClock.timeUs();
        long timestampUs = bufferInfo.presentationTimeUs;
        long deltaUs = timestampUs - timeUs;

        // To start an AudioTrack, the buffer will need to be filled up, otherwise the track will
        // be in underrun state at play()
        // see AudioTrack.play() for more details
        if (mAudioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
            // current output buffer is really far before clock, we must skip it as the next ones
            // unless we have already pushed some data (it may happen if first push takes too
            // much time).
            int nbDiscarded = 0;
            long timeDiscarded = 0;
            while ((timestampUs + mAudioTrackBufferDurationUs < timeUs) && mNbBytesPushed == 0) {
                nbDiscarded++;
                timeDiscarded += bufferDurationUs;
                consume(peekOutputBufferInfo());

                bufferInfo = peekOutputBufferInfo();
                if (bufferInfo == null)
                    break;
                timeUs = mClock.timeUs();
                timestampUs = bufferInfo.presentationTimeUs;
                bufferDurationUs = AudioUtils.getBufferDurationUs(bufferInfo.size,
                        mAudioTrack.getSampleRate(),
                        mAudioTrack.getChannelCount());
            }
            if (bufferInfo == null && nbDiscarded > 0 &&
                    (timestampUs + mAudioTrackBufferDurationUs + inputQueueSizeUs < timeUs)) {
                ASPlayerLog.w("suspicious clock, discarded buffers (nb:%d, time:%dms), clock:%d, nb_outputs:%d, inputs_ms:%d",
                        nbDiscarded, timeDiscarded,
                        timeUs / 1000,
                        getNbOutputBuffers(),
                        inputQueueSizeUs / 1000);
            }
            if (bufferInfo == null)
                return inputQueueSizeUs > 0 ? 1000 : 40000;
        } else {
            if (mAudioTrack.getUnderrunCount() > mLastUnderrunCount) {
                if (mOutputBufferIndexes.isEmpty()) {
                    ASPlayerLog.i("underrun detected, deltaUs:%d, inputsize:%d underrun:%d",
                            deltaUs,
                            inputQueueSizeUs,
                            mAudioTrack.getUnderrunCount());
                    mLastUnderrunCount = mAudioTrack.getUnderrunCount();
                    mAudioTrack.pause();
                    mAudioPositionTracker.suspend();
                    mNbBytesPushed = 0;
                    mPrimingDone = false;
                    mFirstTimestampUs = -1;
                    return 10000;
                } else {
                    mLastUnderrunCount = mAudioTrack.getUnderrunCount();
                }
            }
        }

        if (mFirstTimestampUs == -1)
            mFirstTimestampUs = timestampUs;

        if (mAudioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING &&
                mPrimingDone &&
                mFirstTimestampUs < timeUs) {
            long to = SystemClock.elapsedRealtime();
            ASPlayerLog.i("first delta:%dms (time:%dms, timestamp:%dms) pushed:%d buffer-size:%d",
                    (timeUs - mFirstTimestampUs) / 1000,
                    timeUs / 1000, mFirstTimestampUs / 1000,
                    mNbBytesPushed, mAudioTrackBufferSize);
            mAudioTrack.play();
            mAudioPositionTracker.resume();
        }

        return render();
    }

    private int findBestSampleRate(int preferredSampleRate) {
        if (!mTunneledPlayback)
            return preferredSampleRate;

        if (mAudioCaps == null)
            return preferredSampleRate;

        // sanity checks
        if (!mAudioCaps.isHdmiPlugged() || mAudioCaps.isBluetoothDeviceReady()) {
            ASPlayerLog.w("unexpected output configuration in tunneled mode:%s %s",
                    mAudioCaps.isHdmiPlugged() ? "" : "hdmi unplugged",
                    mAudioCaps.isBluetoothDeviceReady() ? "bt device connected" : "");
            return preferredSampleRate;
        }

        int defaultSampleRate = 48000;
        int[] sampleRates = mAudioCaps.getHdmiSupportedSampleRates();
        if (sampleRates == null || sampleRates.length == 0) {
            return defaultSampleRate;
        } else {
            int previousSampleRate = 0;
            for (int sampleRate : sampleRates) {
                if (sampleRate == preferredSampleRate)
                    return preferredSampleRate;
                if (sampleRate < preferredSampleRate && sampleRate > previousSampleRate)
                    previousSampleRate = sampleRate;
            }
            if (previousSampleRate == 0)
                previousSampleRate = sampleRates[0];
            return previousSampleRate;
        }
    }

    private MediaFormat adjustMediaFormat(MediaFormat format) {
        String mimeType = format.getString(MediaFormat.KEY_MIME);
        int channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);

        sampleRate = findBestSampleRate(sampleRate);
        MediaFormat adjustedMediaFormat =
                MediaFormat.createAudioFormat(mimeType, sampleRate, channelCount);

        if (mimeType.equals(MediaFormat.MIMETYPE_AUDIO_AAC)) {
            // Dolby certification: add -4dB attenuation to reach -23dB global attenuation
            adjustedMediaFormat.setInteger(android.media.MediaFormat.KEY_AAC_DRC_TARGET_REFERENCE_LEVEL, 80);
            adjustedMediaFormat.setInteger(android.media.MediaFormat.KEY_AAC_MAX_OUTPUT_CHANNEL_COUNT, 2);
            ASPlayerLog.i("set AAC target reference level to 80");
        }

        if (format.containsKey("csd-0"))
            adjustedMediaFormat.setByteBuffer("csd-0", format.getByteBuffer("csd-0"));

        return adjustedMediaFormat;
    }

    private BufferInfo peekOutputBufferInfo() {
        if (mOutputBufferIndexes.isEmpty())
            return null;
        return mOutputBufferInfos[mOutputBufferIndexes.peekFirst()];
    }

    private void resetInjectionBuffer() {
        if (mInjectionBuffer.capacity() > DEFAULT_INJECTION_BUFFER_SIZE) {
            ASPlayerLog.i("reduce inject buffer size from %d to %d",
                    mInjectionBuffer.capacity(), DEFAULT_INJECTION_BUFFER_SIZE);
            mInjectionBuffer = ByteBuffer.allocate(DEFAULT_INJECTION_BUFFER_SIZE);
        }
        mInjectionBuffer.limit(0);
    }

    private void updateInjectionBuffer() {
        if (mInjectionBuffer.remaining() != 0)
            return;
        if (mOutputBufferIndexes.isEmpty())
            return;

        int index = mOutputBufferIndexes.pop();
        BufferInfo info = mOutputBufferInfos[index];

        mAudioPositionTracker.updateReferenceInBytes(mPushedDataInBytes,
                info.presentationTimeUs);

        mPushedDataInBytes += info.size;

        ByteBuffer buffer = mMediaCodec.getOutputBuffer(index);
        if (buffer == null)
            return;

        buffer.clear();
        mInjectionBuffer.clear();
        mInjectionBuffer.order(buffer.order());
        buffer.limit(info.size);
        if (mInjectionBuffer.capacity() < info.size) {
            ASPlayerLog.i("injecting buffer not big enough (%d vs %d)",
                    mInjectionBuffer.capacity(), info.size);
            mInjectionBuffer = ByteBuffer.allocate(info.size);
        }
        mInjectionBuffer.put(buffer);
        mInjectionBuffer.flip();

        /*
        Log.w(TAG, String.format("mInjectionBuffer(init) : pos:%d, limit:%d, remaining:%d",
                    mInjectionBuffer.position(), mInjectionBuffer.limit(), mInjectionBuffer.remaining()));*/
        mMediaCodec.releaseOutputBuffer(index, false /* render */);

        if (mOutputBufferListener != null) {
            ASPlayerLog.i("");
            mOutputBufferListener.onRender(info.presentationTimeUs);
        }
    }

    private long render() {
        long to = SystemClock.elapsedRealtime();

        // active push, try to push as much as we can, to avoid underrun whereas we have data to push
        do {
            // copy output buffer in injection buffer if necessary
            BufferInfo info = peekOutputBufferInfo();
            if (info == null)
                return 10000;
            updateInjectionBuffer();

            // audioTrack.write is used in asynchronous mode to avoid blocking the rendering thread
            int remaining = mInjectionBuffer.remaining();
            if (remaining == 0)
                return 10000;

            int nbWritten;
            if (mTunneledPlayback) {
                nbWritten = mAudioTrack.write(mInjectionBuffer, remaining, AudioTrack.WRITE_NON_BLOCKING,
                        info.presentationTimeUs * 1000);
            } else {
                nbWritten = mAudioTrack.write(mInjectionBuffer, remaining, AudioTrack.WRITE_NON_BLOCKING);
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
                mErrorMessage = errorMessage;
                return 20000;
            }

            mNbBytesPushed += nbWritten;
            mPrimingDone |=
                    (nbWritten != remaining) && mNbBytesPushed >= (mAudioTrackBufferSize * 9 / 10);

            if (nbWritten != remaining) {
                return 20000;
            }
        } while ((SystemClock.elapsedRealtime() - to) < 10);

        return 5000;
    }

    private void consume(BufferInfo info) {
        if (mOutputBufferIndexes.isEmpty())
            return;

        int index = mOutputBufferIndexes.pop();
        mMediaCodec.releaseOutputBuffer(index, false);

        if (mOutputBufferListener != null)
            mOutputBufferListener.onConsume(info.presentationTimeUs);
    }

    private void createAudioTrack(int newEncoding, int newSampleRate, int newChannelCount) {
        // check if we can reuse the audio track
        if (mAudioTrack != null &&
                mAudioTrack.getAudioFormat() == newEncoding &&
                mAudioTrack.getSampleRate() == newSampleRate &&
                mAudioTrack.getChannelCount() == newChannelCount) {
            mAudioTrack.pause();
            mAudioTrack.flush();
            return;
        }
        mAudioTrack = null;

        // audio track creation parameters
        int newChannelConfig = AudioUtils.getChannelConfig(newChannelCount);
        mAudioTrackBufferSize =
                AudioTrack.getMinBufferSize(newSampleRate, newChannelConfig, newEncoding);
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setFlags(mTunneledPlayback ? AudioAttributes.FLAG_HW_AV_SYNC : 0)
                .build();
        AudioFormat audioFormat = new AudioFormat.Builder()
                .setEncoding(newEncoding)
                .setSampleRate(newSampleRate)
                .setChannelMask(newChannelConfig)
                .build();
        AudioTrack.Builder builder = new AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(mAudioTrackBufferSize)
                .setSessionId(mAudioSessionId);

        mAudioTrackBufferDurationUs =
                AudioUtils.getBufferDurationUs(mAudioTrackBufferSize, newSampleRate, newChannelCount);
        /*
        TvLog.i("audiotrack [%dHz, config:%s, encoding:%s, buffer:%d]",
                newSampleRate,
                AudioUtils.channelConfigDescription(newChannelConfig),
                AudioUtils.encodingDescription(newEncoding),
                bufferSize);*/
        ASPlayerLog.i("audiotrack [%dHz, config:%s, encoding:%s, buffer[size:%d, duration:%d], session-id:%d]",
                newSampleRate,
                AudioUtils.channelConfigDescription(newChannelConfig),
                AudioUtils.encodingDescription(newEncoding),
                mAudioTrackBufferSize,
                mAudioTrackBufferDurationUs,
                mAudioSessionId);

        // try to create the audio track
        int nbTries = 0;
        long to = SystemClock.elapsedRealtime();
        String errorMessage = null;
        // The loop is a workaround for BCM issue CS7819096 "Dolby - AudioTrack creation fails
        // with error -38".
        // The issue might happen also on other platforms, depending on how fast the previous
        // audiotrack used for tunneled or passthrough is released
        while (mAudioTrack == null && nbTries < 10) {
            try {
                mAudioTrack = builder.build();
            } catch (Exception exception) {
                if (errorMessage == null)
                    errorMessage = exception.getMessage();
                AudioUtils.releaseAudioTrack(mAudioTrack);
                mAudioTrack = null;
                try {
                    Thread.sleep(20);
                } catch (InterruptedException interruptedException) {
                    // ignore
                }
            }
            nbTries++;
        }
        long elapsed = SystemClock.elapsedRealtime() - to;

        if (errorMessage != null) {
            ASPlayerLog.w("%s audiotrack after %d tries (%s ms), first error:%s",
                    mAudioTrack == null ? "can't create" : "create",
                    nbTries, elapsed,
                    errorMessage);
        }
    }

    private void releaseAudioTrack() {
        AudioUtils.releaseAudioTrack(mAudioTrack);
        mAudioTrack = null;
        mNbBytesPushed = 0;
        mPrimingDone = false;
        mFirstTimestampUs = -1;
    }

    @Override
    public String toString() {
        if (mMediaCodec != null)
            return mMediaCodec.getName();
        else
            return "AudioCodecRendererMediaCodec:<no-codec>";
    }
}
