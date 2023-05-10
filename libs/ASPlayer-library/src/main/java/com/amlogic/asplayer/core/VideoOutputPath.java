package com.amlogic.asplayer.core;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;


import com.amlogic.asplayer.api.WorkMode;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Locale;

import static android.media.MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback;

class VideoOutputPath extends MediaOutputPath {

    private static final String TAG = Constant.LOG_TAG;

    interface VideoFormatListener {
        void onVideoSizeInfoChanged(int width, int height, float pixelAspectRatio);
        void onAfdInfoChanged(byte activeFormat);
        void onFrameRateChanged(float frameRate);
    }

    private class VideoMediaCodecCallback extends MediaCodec.Callback {
        @Override
        public void onInputBufferAvailable(MediaCodec codec, int index) {
            if (mMediaCodecStarter != null)
                return;
            onMediaCodecInputBufferAvailable(codec, index);
        }

        @Override
        public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {
            if (mMediaCodecStarter != null)
                return;
            onMediaCodecOutputBufferAvailable(codec, index, info);
        }

        @Override
        public void onError(MediaCodec codec, MediaCodec.CodecException e) {
            if (mMediaCodecStarter != null)
                return;
            onMediaCodecError(codec, e);
        }

        @Override
        public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
            ASPlayerLog.i("VideoOutputPath-%d onOutputFormatChanged, format: %s", mId, format);
            if (mMediaCodecStarter != null)
                return;
            onMediaCodecOutputFormatChanged(codec, format);
        }
    }

    private class VideoMediaCodecOnFrameCallback implements MediaCodec.OnFrameRenderedListener {
        @Override
        public void onFrameRendered(MediaCodec codec, long presentationTimeUs, long nanoTime) {
            if (mTimestampKeeper.isEmpty())
                return;
            mTimestampKeeper.removeTimestamp(presentationTimeUs);
            notifyFrameDisplayed(presentationTimeUs);
        }
    }

    private class MediaCodecStarter implements Runnable {
        @Override
        public void run() {
            if (mMediaCodec != null) {
                ASPlayerLog.i("VideoOutputPath-%d start mediacodec: %s", mId, mMediaCodec);
                mMediaCodec.start();
            }
            mMediaCodecStarter = null;
        }
    }

    private static final int MAX_BUFFER_INFOS = 128;

    private static final String KEY_CROP_LEFT = "crop-left";
    private static final String KEY_CROP_RIGHT = "crop-right";
    private static final String KEY_CROP_BOTTOM = "crop-bottom";
    private static final String KEY_CROP_TOP = "crop-top";

    // can't be less than RendererPlayback.SYNCHRO_MAX_AV_DELTA_US
    private static final int SYNC_MAX_DELTA_IN_FUTURE_US = 5000000;
    private static final int SYNC_RENDER_WINDOW_US = 20000;

    // margin computed for 50Hz display, 20ms between vsync, 2 vsyncs
    // see MediaCodec.releaseOutputBuffer
    private static final int MARGIN_FOR_VSYNC_US = 40000;

    // maximum (560ms) seen on BBC News stream
    private static final int MAX_THRESHOLD_BETWEEN_PTS_US = 600000;

    protected MediaCodec mMediaCodec;
    protected VideoMediaCodecCallback mMediaCodecCallback;
    protected boolean mSecurePlayback;

    protected int mAudioSessionId = Constant.INVALID_AUDIO_SESSION_ID;
    protected boolean mTunneledPlayback;
    protected MediaCodec.OnFrameRenderedListener mMediaCodecOnFrameCallback;

    private MediaCodecStarter mMediaCodecStarter;

    // input and output buffers indexes provided by MediaCodec
    protected final ArrayDeque<Integer> mInputBufferIndexes;
    protected final ArrayDeque<Integer> mOutputBufferIndexes;
    protected MediaCodec.BufferInfo[] mOutputBufferInfos;

    // input buffer shared with extractor
    protected final InputBuffer mInputBuffer;

    //
    protected Surface mSurface;
    protected Surface mDummySurface;
    private long mFreeRunNextFrameTimestampUs;
    private boolean mVisible;

    protected boolean mFirstFrameDisplayed;

    protected VideoFormatListener mVideoFormatListener;

    protected float mPixelAspectRatio;
    protected String mMimeType;
    protected int mVideoWidth;
    protected int mVideoHeight;
    protected float mFrameRate;
    protected byte mActiveFormat;

    protected long mNbDecodedFrames;
    private int mNbSuspiciousTimestamps;

    protected double mTrickModeSpeed;

    VideoOutputPath(int id) {
        super(id, String.format("v%d", id));
        mInputBufferIndexes = new ArrayDeque<>();
        mOutputBufferIndexes = new ArrayDeque<>();
        mOutputBufferInfos = new MediaCodec.BufferInfo[MAX_BUFFER_INFOS];
        mVisible = true;
        mInputBuffer = new InputBuffer();

        mMediaCodecCallback = new VideoMediaCodecCallback();
        mMediaCodecOnFrameCallback = new VideoMediaCodecOnFrameCallback();
    }

    void setAudioSessionId(int sessionId) {
        mAudioSessionId = sessionId;
    }

    void setTunneledPlayback(boolean tunneledPlayback) {
        mTunneledPlayback = tunneledPlayback;
    }

    void setSurface(Surface surface) {
        ASPlayerLog.i("VideoOutputPath-%d setSurface: %s", mId, surface);
        mSurface = surface;
        if (!isConfigured()) {
            return;
        }

        if (surface == null) {
            ASPlayerLog.i("VideoOutputPath-%d setSurface: surface is null, release mediacodec", mId);
            mInputBufferIndexes.clear();
            mOutputBufferIndexes.clear();
            mMediaCodec.reset();
            mMediaCodec.release();
            mMediaCodec = null;
        }
        setConfigured(false);
    }

    void setDummySurface(Surface surface) {
        ASPlayerLog.i("VideoOutputPath-%d setFccDummySurface: %s", mId, surface);
        mDummySurface = surface;
    }

    void setVisible(boolean visible) {
        mVisible = visible;
        if (mSurface != null) {
            if (visible) {
                resetSynchro();
            } else {
                pushBlankFrame();
                mInputBufferIndexes.clear();
                mOutputBufferIndexes.clear();
                Arrays.fill(mOutputBufferInfos, null);
                resetSynchro();
                setConfigured(false);
            }
        }
    }

    void pushBlankFrame() {
        if (mSurface == null)
            return;
        if (mMediaCodec == null)
            return;

        mMediaCodec.reset();
        MediaFormat format =
                MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1920, 1080);
        format.setInteger(MediaFormat.KEY_PUSH_BLANK_BUFFERS_ON_STOP, 1);
        mMediaCodec.configure(format, mSurface, null, 0);
        mMediaCodec.start();
        mMediaCodec.stop();
        mMediaCodec.release();
        ASPlayerLog.i("VideoOutputPath-%d pushBlankFrame, release mediacodec", mId);
        mMediaCodec = null;
    }

    String getCodecName() {
        if (mMediaCodec != null)
            return mMediaCodec.getName();
        else
            return null;
    }

    boolean isFirstFrameDisplayed() {
        return mFirstFrameDisplayed;
    }

    void setVideoFormatListener(VideoFormatListener listener) {
        mVideoFormatListener = listener;
    }

    @Override
    boolean isDisplayPositionValid() {
        return mClock.isStarted();
    }

    @Override
    long getDisplayPositionUs() {
        if (mClock.isStarted())
            return mClock.timeUs() - MARGIN_FOR_VSYNC_US;
        else
            return 0;
    }

    @Override
    long getMarginUs() {
        return MAX_THRESHOLD_BETWEEN_PTS_US;
    }

    @Override
    public boolean configure() {
        if (isConfigured()) {
            ASPlayerLog.w("VideoOutputPath-%d mediacodec is not null as it should be", mId);
            return false;
        }
        if (waitForConfigurationRetry()) {
            return false;
        }
        if (mSurface == null) {
            ASPlayerLog.w("VideoOutputPath-%d surface is null", mId);
            return false;
        }

        mNbDecodedFrames = 0;
        mFirstFrameDisplayed = false;
        mInputBufferQueue = null;

        MediaFormat format = mMediaFormat;
        if (format == null) {
            ASPlayerLog.i("VideoOutputPath-%d configure failed, format is null", mId);
            return false;
        }

        mMimeType = format.getString(MediaFormat.KEY_MIME);
        ASPlayerLog.i("VideoOutputPath-%d mimetype: %s", mId, mMimeType);
        applyAmlBestVideoQualityWorkAround(format);

        if (Build.VERSION.SDK_INT >= 30 && format != null) {
            mSecurePlayback =
                    format.containsFeature(FEATURE_SecurePlayback) &&
                            format.getFeatureEnabled(FEATURE_SecurePlayback);
        } else {
            mSecurePlayback = mMediaDescrambler != null;
        }

        MediaCodec mediaCodec = null;
        boolean configured = false;
        try {
            mediaCodec = MediaCodecUtils.findMediaCodec(format, mTunneledPlayback, mSecurePlayback);
            // get video size from input
            mVideoWidth = format.getInteger(MediaFormat.KEY_WIDTH);
            mVideoHeight = format.getInteger(MediaFormat.KEY_HEIGHT);

            if (mTunneledPlayback) {
                // make a copy
                format = MediaFormat.createVideoFormat(mMimeType, mVideoWidth, mVideoHeight);
                onSetVideoFormat(format);

                mediaCodec.setOnFrameRenderedListener(mMediaCodecOnFrameCallback, getHandler());
            }
            mediaCodec.setCallback(mMediaCodecCallback, getHandler());

            ASPlayerLog.i("VideoOutputPath-%d mime_type:%s, codec:%s, format:%s", mId, mMimeType, mediaCodec.getName(), format);
            Surface surface = mSurface;
            format.setInteger(FccWorkMode.MEDIA_FORMAT_KEY_FCC_WORKMODE, FccWorkMode.MEDIA_FORMAT_FCC_WORKMODE_NORMAL);

            if (mMediaDescrambler == null) {
                mediaCodec.configure(format, surface, null, 0);
            } else {
                mediaCodec.configure(format, surface, 0, mMediaDescrambler);
            }
            mediaCodec.start();
            ASPlayerLog.i("VideoOutputPath-%d start MediaCodec: %s", mId, mMediaCodec);
            mMediaCodec = mediaCodec;

            setConfigured(true);
            mFirstFrameDisplayed = false;
            configured = true;
        } catch (Exception exception) {
            ASPlayerLog.w("VideoOutputPath-%d can't create mediacodec error:%s", mId, exception.getMessage());
            if (mediaCodec != null)
                mediaCodec.release();
            handleConfigurationError(exception.toString());
        }

        return configured;
    }

    protected void onSetVideoFormat(MediaFormat format) {
        if (format == null) {
            return;
        }

        // activate tunneled playback if needed
        if (mTunneledPlayback) {
            format.setFeatureEnabled(MediaCodecInfo.CodecCapabilities.FEATURE_TunneledPlayback, true);
        }
        format.setInteger(MediaFormat.KEY_AUDIO_SESSION_ID, mAudioSessionId);

        // activate secure playback if needed
        if (mSecurePlayback) {
            format.setFeatureEnabled(MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback, true);
        }
    }

    @Override
    protected void pushInputBuffer() {
        ASPlayerLog.i("VideoOutputPath-%d push input buffer", mId);
        if (!isConfigured() && !configure())
            return;

        try {
            pushInputBufferInitStartTime();

            boolean bufferPushed = false;
            float pixelAspectRatio = 0;
            int width = 0;
            int height = 0;
            while (!mInputBufferIndexes.isEmpty() &&
                    !mInputBufferQueue.isEmpty() &&
                    !pushInputBufferIsTimeout()) {

                // try to fill next input buffer
                Integer index = mInputBufferIndexes.peek();
                if (index == null)
                    break;
                ByteBuffer buffer = mMediaCodec.getInputBuffer(index);
                if (buffer == null)
                    break;
                mInputBuffer.buffer = buffer;
                if (!mInputBufferQueue.pop(mInputBuffer))
                    break;

                // and queue the buffer
                if (mSecurePlayback) {
                    mMediaCodec.queueSecureInputBuffer(index, 0, mInputBuffer.cryptoInfo,
                            mInputBuffer.timestampUs, 0);
                } else {
                    mMediaCodec.queueInputBuffer(index, 0, mInputBuffer.buffer.limit(),
                            mInputBuffer.timestampUs, 0);
                }

                // filling the buffer is a success, we can remove used index
                mInputBufferIndexes.pop();

                mTimestampKeeper.pushTimestamp(mInputBuffer.timestampUs);
                if (mTimestampKeeper.hasDiscontinuity())
                    break;

                pixelAspectRatio = mInputBuffer.pixelAspectRatio;
                width = mInputBuffer.width;
                height = mInputBuffer.height;
                bufferPushed = true;
            }
            updateVideoSizeInfo(width, height, pixelAspectRatio);

            if (bufferPushed)
                notifyBufferPushed();

            timestampInputBufferQueueFullIfNeeded();

            if (mTimestampKeeper.hasDiscontinuity()) {
                ASPlayerLog.i("VideoOutputPath-%d mTimestampKeeper.hasDiscontinuity reset mediacodec", mId);
                reset();
            }
        } catch (Exception e) {
            ASPlayerLog.w("VideoOutputPath-%d error=%s", mId, e.getMessage());
            setError(e.getMessage());
        }
    }

    protected void updateVideoSizeInfo(int width, int height, float pixelAspectRatio) {
        boolean needNotify = false;
        if (width > 0 && width != mVideoWidth) {
            mVideoWidth = width;
            needNotify = true;
        }
        if (height > 0 && height != mVideoHeight) {
            mVideoHeight = height;
            needNotify = true;
        }
        if (pixelAspectRatio > 0 && Math.abs(pixelAspectRatio - mPixelAspectRatio) > 0.001) {
            mPixelAspectRatio = pixelAspectRatio;
            needNotify = true;
        }
        if (mVideoHeight <= 0 || mVideoWidth <= 0 || mPixelAspectRatio <= 0) {
            needNotify = false;
        }
        if (needNotify && mVideoFormatListener != null) {
            ASPlayerLog.i("VideoOutputPath-%d video:%dx%d, aspect ratio:%f", mId, mVideoWidth, mVideoHeight, mPixelAspectRatio);
            mVideoFormatListener.onVideoSizeInfoChanged(mVideoWidth, mVideoHeight, mPixelAspectRatio);
        }
    }

    protected void updateVideoResolutionInfo(int width, int height) {
        updateVideoSizeInfo(width, height, 0);
    }

    protected void updateVideoAspectRatioInfo(float pixelAspectRatio) {
        updateVideoSizeInfo(0, 0, pixelAspectRatio);
    }

    protected void updateVideoFrameRateInfo(float frameRate) {
        if (mVideoFormatListener != null && frameRate != mActiveFormat) {
            mFrameRate = frameRate;
            ASPlayerLog.i("VideoOutputPath-%d frameRate: %f", mId, frameRate);
            mVideoFormatListener.onFrameRateChanged(frameRate);
        }
    }

    protected void updateAfdInfo(byte activeFormat) {
        if (mVideoFormatListener != null && activeFormat > 0 && activeFormat != mActiveFormat) {
            mActiveFormat = activeFormat;
            ASPlayerLog.i("VideoOutputPath-%d active format:%02x", mId, activeFormat);
            mVideoFormatListener.onAfdInfoChanged(activeFormat);
        }
    }

    public void flush() {
        if (mMediaCodec != null) {
            mMediaCodec.flush();
            discardOutstandingCallbacksAndStart();
        }

        mFirstFrameDisplayed = false;

        mInputBufferIndexes.clear();
        mOutputBufferIndexes.clear();
        Arrays.fill(mOutputBufferInfos, null);

        mTimestampKeeper.clear();
        if (mInputBufferQueue != null)
            mInputBufferQueue.clear();
    }

    @Override
    public void reset() {
        if (mMediaCodec != null) {
            try {
                mMediaCodec.flush();
                mMediaCodec.stop();
            } catch (Exception e) {
                ASPlayerLog.e("VideoOutputPath-%d reset flush/stop mediacodec error: %s", mId, (e != null ? e.getMessage() : ""));
                e.printStackTrace();
            }
        }
        ASPlayerLog.i("VideoOutputPath-%d reset stop mediacodec: %s", mId, mMediaCodec);
        mFirstFrameDisplayed = false;

        if (mMediaCodecStarter != null)
            getHandler().removeCallbacks(mMediaCodecStarter);
        mMediaCodecStarter = null;

        mInputBufferIndexes.clear();
        mOutputBufferIndexes.clear();
        Arrays.fill(mOutputBufferInfos, null);

        mVideoWidth = 0;
        mVideoHeight = 0;
        mPixelAspectRatio = 0f;
        mFrameRate = 0f;
        mActiveFormat = 0;

        mTrickModeSpeed = 0;

        mMimeType = null;

        super.reset();
    }

    @Override
    public void release() {
        super.release();

        mSurface = null;
        mDummySurface = null;
    }

    public void setFreeRunMode() {
        super.setFreeRunMode();
        mFreeRunNextFrameTimestampUs = getMarginUs();
    }

    private void displayFrame(long presentationTimeUs, long deltaUs) {
        int index = mOutputBufferIndexes.pop();

        if (mVisible) {
            // MediaCodec.releaseOutputBuffer(index, true) uses the timestamp associated with
            // the buffer
            // The reference clock used by MediaCodec.releaseOutputBuffer is the same as
            // System.nanotime()
            // As a consequence, MediaCodec.releaseOutputBuffer(index, true) should not be used,
            // unless timestamps of input buffers are aligned with System.nanotime()
            // Source code ref: MediaCodec.cpp, Layer.cpp
            long timestamp = System.nanoTime();
            if (mClock.getSpeed() == 1 && isFirstFrameDisplayed())
                timestamp += MARGIN_FOR_VSYNC_US * 1000 + deltaUs * 1000;
            mMediaCodec.releaseOutputBuffer(index, timestamp);
        } else {
            mMediaCodec.releaseOutputBuffer(index, false);
        }
        mTimestampKeeper.removeTimestamp(presentationTimeUs);
        notifyFrameDisplayed(presentationTimeUs);
        mFirstFrameDisplayed = true;
    }

    private long renderFreeRun() {
        // compute delta between timestamp and clock
        long timeUs = mClock.timeUs();
        long timestampUs = mFreeRunNextFrameTimestampUs;
        long signedDeltaUs = timestampUs - timeUs;

        if (signedDeltaUs < SYNC_RENDER_WINDOW_US) {
            displayFrame(mFreeRunNextFrameTimestampUs, 0);
            mFreeRunNextFrameTimestampUs += 40000;
        }

        return mFreeRunNextFrameTimestampUs - timeUs;
    }

    private long renderSynchro() {
        long timeForNextFrameUs;

        // get output buffer info
        MediaCodec.BufferInfo bufferInfo = peekOutputBufferInfo();
        if (bufferInfo == null)
            return 10000;

        // compute delta between timestamp and clock
        long timeUs = mClock.timeUs();
        long timestampUs = bufferInfo.presentationTimeUs;
        long deltaUs = timestampUs - timeUs;

        // just check if we must render or skip the frame
        boolean mustConsume = false;
        boolean mustRender = false;
        // we don't skip frames if we are close to the clock
        // - skip frames is like a freeze on screen
        // - time to skip is almost as fast as to display
        if (deltaUs < -100000) {
            ASPlayerLog.i("VideoOutputPath-%d skip frame (delta %d ms, input size %d ms, timestamp %d ms",
                    mId, deltaUs / 1000,
                    (mInputBufferQueue.getSizeInUs() + mTimestampKeeper.getSizeInUs()) / 1000,
                    bufferInfo.presentationTimeUs / 1000);
            mustConsume = true;
            timeForNextFrameUs = 0;
        } else if (deltaUs < 10000) {
            mustRender = true;
            timeForNextFrameUs = Math.max(0, deltaUs + 40000);
        } else if (deltaUs > SYNC_MAX_DELTA_IN_FUTURE_US) {
            setError(String.format(Locale.US, "delta %d too big, must reset", deltaUs));
            timeForNextFrameUs = 0;
        } else {
            timeForNextFrameUs = deltaUs;
        }

        if (mustRender)
            displayFrame(bufferInfo.presentationTimeUs, deltaUs);
        else if (mustConsume)
            consume(bufferInfo);

        return timeForNextFrameUs;
    }

    void renderOneFrame() {
        if (mFirstFrameDisplayed)
            return;
        MediaCodec.BufferInfo bufferInfo = peekOutputBufferInfo();
        if (bufferInfo != null)
            displayFrame(bufferInfo.presentationTimeUs, 0);
    }

    private long tryRenderFirstFrame() {
        if (!hasOutputBuffers())
            return 5000;
        MediaCodec.BufferInfo bufferInfo = peekOutputBufferInfo();
        if (bufferInfo != null)
            displayFrame(bufferInfo.presentationTimeUs, 0);
        return 10000;
    }

    long render() {
        if (mTunneledPlayback)
            return 10000;

        if (!isFirstFrameDisplayed())
            return tryRenderFirstFrame();

        if (!hasOutputBuffers())
            return 10000;

        if (!mClock.isStarted())
            return 10000;

        // TODO : need some development
        if (mClock.getSpeed() == 0.0)
            return 10000;

        switch (getRenderingMode()) {
            case RENDER_FREE_RUN:
                return renderFreeRun();
            case RENDER_SYNCHRONIZED:
                return renderSynchro();
            case RENDER_NONE:
            default:
                return 10000;
        }
    }

    int getNbOutputBuffers() {
        return mOutputBufferIndexes.size();
    }

    private void onMediaCodecInputBufferAvailable(MediaCodec codec, int index) {
        if (codec != mMediaCodec)
            return;
        mInputBufferIndexes.add(index);
    }

    private void onMediaCodecOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {
        if (codec != mMediaCodec)
            return;

        mNbDecodedFrames++;

        // Sometimes info.presentationTimeUs is wrong, it is not set to the value provided in
        // input buffer.
        // In that case, we just ignore the buffer, otherwise, it will mess up synchronization
        // A presentationTimeUs is supposed to be wrong if it is out of the range of input buffer
        // timestamps. It can happen if
        // - MediaCodec produces a corrupted timestamp (seen on AMLOGIC only)
        // - A discontinuity has been found (a stream in loop for instance)
        if (mTimestampKeeper.isTimestampOutOfRange(info.presentationTimeUs) &&
                getRenderingMode() == RENDER_SYNCHRONIZED) {
            if (mNbSuspiciousTimestamps == 0) {
                ASPlayerLog.i("VideoOutputPath-%d suspicious output buffer %d, timestamp[%d] not found in pes info queue",
                        mId, mNbDecodedFrames, info.presentationTimeUs);
            }
            mNbSuspiciousTimestamps++;
            mMediaCodec.releaseOutputBuffer(index, false);
            return;
        }
        if (mNbSuspiciousTimestamps > 0) {
            ASPlayerLog.i("VideoOutputPath-%d back to acceptable output buffer %d, timestamp[%d], after %d incorrect buffers",
                    mId, mNbDecodedFrames, info.presentationTimeUs, mNbSuspiciousTimestamps);
            mNbSuspiciousTimestamps = 0;
        }

        mOutputBufferInfos[index] = info;
        mOutputBufferIndexes.addLast(index);
    }

    private void onMediaCodecOutputFormatChanged(MediaCodec codec, MediaFormat format) {
        if (codec != mMediaCodec)
            return;

        ASPlayerLog.i("VideoOutputPath-%d format=%s", mId, format);
        int videoWidth;
        int videoHeight;
        if (format.containsKey(KEY_CROP_RIGHT)
                && format.containsKey(KEY_CROP_LEFT) && format.containsKey(KEY_CROP_BOTTOM)
                && format.containsKey(KEY_CROP_TOP)) {
            videoWidth = format.getInteger(KEY_CROP_RIGHT) - format.getInteger(KEY_CROP_LEFT) + 1;
            videoHeight = format.getInteger(KEY_CROP_BOTTOM) - format.getInteger(KEY_CROP_TOP) + 1;
            ASPlayerLog.i("VideoOutputPath-%d video size %dx%d", mId, videoWidth, videoHeight);
        } else {
            ASPlayerLog.i("VideoOutputPath-%d %s-%b, %s-%b, %s-%b, %s-%b",
                    mId, KEY_CROP_RIGHT, format.containsKey(KEY_CROP_RIGHT),
                    KEY_CROP_LEFT, format.containsKey(KEY_CROP_LEFT),
                    KEY_CROP_BOTTOM, format.containsKey(KEY_CROP_BOTTOM),
                    KEY_CROP_TOP, format.containsKey(KEY_CROP_TOP));
            videoWidth = format.getInteger(MediaFormat.KEY_WIDTH);
            videoHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
        }
        updateVideoSizeInfo(videoWidth, videoHeight, -1);
    }

    private void onMediaCodecError(MediaCodec codec, MediaCodec.CodecException e) {
        if (codec != mMediaCodec)
            return;

        ASPlayerLog.w("VideoOutputPath-%d error=%s", mId, e.getMessage());
        setError(e.getMessage());
        setConfigurationError(e.toString());
        setConfigured(false);
    }

    private void consume(MediaCodec.BufferInfo info) {
        if (mOutputBufferIndexes.isEmpty())
            return;
        int index = mOutputBufferIndexes.pop();
        mMediaCodec.releaseOutputBuffer(index, false);
        if (info != null)
            mTimestampKeeper.removeTimestamp(info.presentationTimeUs);
    }

    private MediaCodec.BufferInfo peekOutputBufferInfo() {
        if (mOutputBufferIndexes.isEmpty())
            return null;
        int index = mOutputBufferIndexes.peekFirst();
        return mOutputBufferInfos[index];
    }

    @Override
    boolean hasOutputBuffers() {
        return (!mOutputBufferIndexes.isEmpty());
    }

    @Override
    long getNextOutputTimestamp() {
        MediaCodec.BufferInfo info = peekOutputBufferInfo();
        if (info == null)
            return 0;
        else
            return info.presentationTimeUs;
    }

    @Override
    void checkErrors() {
        if (getError() != null) {
            ASPlayerLog.w("VideoOutputPath-%d error %s, reset mediacodec", mId, getError());
            reset();
        }
    }

    @Override
    public String getName() {
        return "VideoOutputPath";
    }

    private void discardOutstandingCallbacksAndStart() {
        ASPlayerLog.i("VideoOutputPath-%d discardOutstandingCallbacksAndStart mediacodec: %s", mId, mMediaCodec);
        if (mMediaCodecStarter != null)
            getHandler().removeCallbacks(mMediaCodecStarter);
        else
            mMediaCodecStarter = new MediaCodecStarter();
        getHandler().post(mMediaCodecStarter);
    }

    private void applyAmlBestVideoQualityWorkAround(MediaFormat format) {
        if (mTrickModeSpeed != 1)
            return;

        // wk for amlogic, we can change format to force high video quality
        // high video quality means
        // - deinterlacing
        // - must push at least 3 frames to get the first frame
        // - random delay between audio/video synchronization
        boolean isBest = true;
        try {
            Class<?> klass = Class.forName("android.os.SystemProperties");
            Method get = klass.getMethod("get", String.class, String.class);
            Object stringValue = get.invoke(klass, "persist.sys.video.quality", "best");
            isBest = stringValue.equals("best");
        } catch (ClassNotFoundException
                | NoSuchMethodException
                | InvocationTargetException
                | IllegalAccessException exception) {
            ASPlayerLog.w("VideoOutputPath-%d fails to get property persist.sys.video.quality: %s",
                    mId, exception.getMessage());
        }
        if (isBest) {
            format.setInteger(MediaFormat.KEY_MAX_WIDTH, 3840);
            format.setInteger(MediaFormat.KEY_MAX_HEIGHT, 2160);
        }
        ASPlayerLog.i("VideoOutputPath-%d WK AMLOGIC, apply %s video quality",
                mId, isBest ? "best" : "normal");
    }

    private void applyAmlTrickModeWorkaround() {
        if (mMediaCodec == null)
            return;

        // On amlogic platform, if MAX_WIDTH and MAX_HEIGHT are set, a video deinterlacer is
        // activated for a best video quality.
        // The problem is that
        // - 3 frames must be pushed to have the first frame visible on screen
        // - rendering is slower, making x2 trick mode impossible
        // The idea here is to use normal mediacodec when trick mode are activated
        MediaFormat format = mMediaCodec.getInputFormat();
        String mime = format.getString(MediaFormat.KEY_MIME);
        if (!mime.equals(MediaFormat.MIMETYPE_VIDEO_AVC) &&
                !mime.equals(MediaFormat.MIMETYPE_VIDEO_MPEG2))
            return;
        if (!format.containsKey(MediaFormat.KEY_MAX_WIDTH) ||
                !format.containsKey(MediaFormat.KEY_MAX_HEIGHT))
            return;

        mMediaCodec.reset();

        mInputBufferIndexes.clear();
        mOutputBufferIndexes.clear();
        Arrays.fill(mOutputBufferInfos, null);

        MediaFormat newFormat = new MediaFormat();
        newFormat.setString(MediaFormat.KEY_MIME, format.getString(MediaFormat.KEY_MIME));
        newFormat.setInteger(MediaFormat.KEY_WIDTH, format.getInteger(MediaFormat.KEY_WIDTH));
        newFormat.setInteger(MediaFormat.KEY_HEIGHT, format.getInteger(MediaFormat.KEY_HEIGHT));
        mMediaCodec.setCallback(mMediaCodecCallback, getHandler());
        mMediaCodec.configure(newFormat, mSurface, null, 0);
        discardOutstandingCallbacksAndStart();

        ASPlayerLog.i("VideoOutputPath-%d  workaround, best quality to normal for trick mode", mId);
    }

    void setTrickModeSpeed(double speed) {
        mTrickModeSpeed = speed;
        if (speed != 0 && speed != 1) {
            applyAmlTrickModeWorkaround();
        }
    }

    void setParameters(Bundle params) {
        if (mMediaCodec != null) {
            mMediaCodec.setParameters(params);
        }
    }
}

