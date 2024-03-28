package com.amlogic.asplayer.core;

import android.media.MediaDescrambler;
import android.os.Handler;
import android.os.SystemClock;

import com.amlogic.asplayer.api.PIPMode;
import com.amlogic.asplayer.api.WorkMode;

abstract class MediaOutputPath {
    private final int MAX_COUNT_CONFIGURE_RETRY = 20;
    private final long INTERVAL_CONFIGURE_RETRY = 100;

    private static final int MAX_TIME_IN_PUSH_BUFFER_IN_MS = 10;

    // no rendering yet
    static final int RENDER_NONE = 0;
    // rendering depends on clock and pts
    static final int RENDER_SYNCHRONIZED = 1;
    // rendering depends on clock but not pts
    // set when discontinuity is detected
    static final int RENDER_FREE_RUN = 2;

    // no configuration
    private static final int CONFIGURATION_NONE = 0;
    // configured from pes
    private static final int CONFIGURATION_FROM_PES = 2;
    // last configuration try has failed
    private static final int CONFIGURATION_ERROR = 3;

    interface FrameListener {
        void onFrame(MediaOutputPath outputPath, long presentationTimeUs, long renderTime);
    }

    interface DataListener {
        void onFirstData(MediaOutputPath outputPath);
        void onDecoderDataLoss(MediaOutputPath outputPath);
        void onDecoderDataResume(MediaOutputPath outputPath);
    }

    interface DecoderListener {
        void onDecoderInitCompleted(MediaOutputPath outputPath);
    }

    // the handler in which all stuff must be done
    protected Handler mHandler;

    // Timekeeper
    protected TimestampKeeper mTimestampKeeper;

    // Input queue
    protected InputBufferQueue mInputBufferQueue;

    // input queue contains pes that are waiting to be pushed into codec and infos on pes pushed
    // into codec but not yet handled
    private boolean mAtLeastOneBufferPushed;
    private long mPesQueueFullWhenMs;

    // codec, format
    private int mConfigurationState;
    private int mConfigureErrorCount;
    private long mLastConfigureErrorTime;

    // rendering mode: either not defined, synchronized, or free run
    private int mRenderingMode;

    // clock
    MediaClock mClock;

    // listeners
    private FrameListener mFrameListener;
    private DataListener mDataListener;
    private DecoderListener mDecoderListener;

    // critical error : no decode for any reason
    private String mError;

    // to limit spent time in pushInputBuffer
    private long mInputBufferPushStartTimeMs;

    protected MediaDescrambler mMediaDescrambler;

    protected int mTargetWorkMode = WorkMode.NORMAL;
    protected int mLastWorkMode = -1;

    protected int mTargetPIPMode = PIPMode.NORMAL;
    protected int mLastPIPMode = -1;

    protected final int mId;
    protected int mSyncInstanceId = Constant.INVALID_SYNC_INSTANCE_ID;

    MediaOutputPath(int id) {
        mId = id;
        mClock = new MediaClock();
        mTimestampKeeper = new TimestampKeeper(getName());
    }

    abstract String getName();

    abstract String getCodecName();

    void setSyncInstanceId(int syncInstanceId) {
        mSyncInstanceId = syncInstanceId;
    }

    void setHandler(Handler handler) {
        mHandler = handler;
    }

    void setError(String error) {
        mError = error;
    }

    String getError() {
        return mError;
    }

    void setMediaDescrambler(MediaDescrambler mediaDescrambler) {
        this.mMediaDescrambler = mediaDescrambler;
    }

    MediaDescrambler getMediaDescrambler() {
        return this.mMediaDescrambler;
    }

    void setInputBufferQueue(InputBufferQueue inputBufferQueue) {
        mInputBufferQueue = inputBufferQueue;
    }

    boolean isConfigured() {
        return mConfigurationState == CONFIGURATION_FROM_PES;
    }

    boolean hasConfigurationError() {
        return (mConfigurationState == CONFIGURATION_ERROR);
    }

    void setConfigured(boolean configured) {
        if (configured)
            mConfigurationState = CONFIGURATION_FROM_PES;
        else
            mConfigurationState = CONFIGURATION_NONE;
    }

    void setConfigurationError(String error) {
        mConfigurationState = CONFIGURATION_ERROR;
        mError = error;
    }

    abstract boolean configure();

    abstract int getNbOutputBuffers();

    abstract boolean hasOutputBuffers();

    abstract long getNextOutputTimestamp();

    boolean isDisplayPositionValid() {
        return mClock.isStarted();
    }

    long getDisplayPositionUs() {
        if (mClock.isStarted())
            return mClock.timeUs();
        else
            return 0;
    }

    long getMarginUs() {
        return 0;
    }

    boolean isInputEmpty() {
        return mTimestampKeeper.isEmpty();
    }

    long getInputSizeInUs() {
        if (mInputBufferQueue == null) return 0;
        return mInputBufferQueue.getSizeInUs() + mTimestampKeeper.getSizeInUs();
    }

    int getInputSize() {
        if (mInputBufferQueue == null)
            return 0;

        return mInputBufferQueue.getSize() + mTimestampKeeper.getSize();
    }

    boolean hasInputDiscontinuity() {
        return mTimestampKeeper.hasDiscontinuity();
    }

    protected void pushInputBufferInitStartTime() {
        mInputBufferPushStartTimeMs = SystemClock.elapsedRealtime();
    }

    protected boolean pushInputBufferIsTimeout() {
        return ((SystemClock.elapsedRealtime() - mInputBufferPushStartTimeMs) > MAX_TIME_IN_PUSH_BUFFER_IN_MS);
    }

    protected abstract void pushInputBuffer();

    public void reset() {
        mConfigurationState = CONFIGURATION_NONE;
        mClock.reset();

        mTimestampKeeper.clear();

        mRenderingMode = RENDER_NONE;
        mError = null;

        mAtLeastOneBufferPushed = false;
        mPesQueueFullWhenMs = 0;
    }

    public void release() {
        ASPlayerLog.i("%s release", getTag());
        reset();
        mInputBufferQueue = null;

        mMediaDescrambler = null;

        mHandler = null;
    }

    abstract void checkErrors();

    abstract long render();

    void resetSynchro() {
        mTimestampKeeper.clear();
        mClock.reset();
        mRenderingMode = RENDER_NONE;
    }

    int getRenderingMode() {
        return mRenderingMode;
    }

    void setSynchroOn(long clockOrigin) {
        mClock.setOriginTimestampUs(clockOrigin);
        mRenderingMode = RENDER_SYNCHRONIZED;
    }

    void setFreeRunMode() {
        mClock.setOriginTimestampUs(0);
        mRenderingMode = RENDER_FREE_RUN;
    }

    void setSpeed(double speed) {
        mClock.setSpeed(speed);
        mPesQueueFullWhenMs = 0;
    }

    long getLastOutputTimestamp() {
        if (mInputBufferQueue == null)
            return -1;
        return mTimestampKeeper.getLastOutputTimestamp();
    }

    void checkStandaloneSynchro() {
        // find expected synchro mode
        int renderingMode = MediaOutputPath.RENDER_NONE;
        if (hasOutputBuffers()) {
            if (mTimestampKeeper.hasDiscontinuity())
                renderingMode = MediaOutputPath.RENDER_FREE_RUN;
            else
                renderingMode = MediaOutputPath.RENDER_SYNCHRONIZED;
        }
        if (renderingMode == MediaOutputPath.RENDER_NONE ||
                renderingMode == mRenderingMode)
            return;

        // apply synchro
        switch (renderingMode) {
            case MediaOutputPath.RENDER_SYNCHRONIZED:
                long marginUs = getMarginUs();
                long nextOutputTimestamp = getNextOutputTimestamp();
                long lastOutputTimestampUs = getLastOutputTimestamp();
                long minimumTimestampOriginUs = Math.max(lastOutputTimestampUs - marginUs, 0);
                long originTimestampUs;
                if (minimumTimestampOriginUs < nextOutputTimestamp) {
                    originTimestampUs = minimumTimestampOriginUs;
                } else {
                    originTimestampUs = nextOutputTimestamp;
                }

                ASPlayerLog.i("%s SYNCHRO - input:%d ms, output:%d, margin:%d ms, next:%d ms, origin:%d ms, last:%d ms",
                        getTag(),
                        (lastOutputTimestampUs - nextOutputTimestamp) / 1000,
                        getNbOutputBuffers(),
                        marginUs / 1000,
                        getNextOutputTimestamp() / 1000,
                        originTimestampUs/1000,
                        lastOutputTimestampUs/1000);
                setSynchroOn(originTimestampUs);
                break;
            case MediaOutputPath.RENDER_FREE_RUN:
                ASPlayerLog.i("%s FREE RUN", getTag());
                setFreeRunMode();
                break;
            default:
                break;
        }
        mRenderingMode = renderingMode;
    }

    void setFrameListener(FrameListener listener) {
        mFrameListener = listener;
    }

    void setDataListener(DataListener listener) {
        mDataListener = listener;
    }

    void setDecoderListener(DecoderListener listener) {
        mDecoderListener = listener;
    }

    void notifyDecoderInitCompleted() {
        if (mDecoderListener != null) {
            mDecoderListener.onDecoderInitCompleted(this);
        }
    }

    void notifyFrameDisplayed(long presentationTimeUs, long renderTime) {
        if (mFrameListener != null) {
            mFrameListener.onFrame(this, presentationTimeUs, renderTime);
        }
    }

    void notifyBufferPushed() {
        if (mAtLeastOneBufferPushed)
            return;
        mAtLeastOneBufferPushed = true;
        if (mDataListener != null)
            mDataListener.onFirstData(this);
    }

    boolean atLeastOneBufferPushed() {
        return mAtLeastOneBufferPushed;
    }

    void notifyDecoderDataLoss() {
        if (mDataListener != null) {
            mDataListener.onDecoderDataLoss(this);
        }
    }

    void notifyDecoderDataResume() {
        if (mDataListener != null) {
            mDataListener.onDecoderDataResume(this);
        }
    }

    void timestampInputBufferQueueFullIfNeeded() {
        if (mInputBufferQueue != null && mInputBufferQueue.isFull()) {
            if (mPesQueueFullWhenMs == 0)
                mPesQueueFullWhenMs = SystemClock.elapsedRealtime();
        } else {
            mPesQueueFullWhenMs = 0;
        }
    }

    long elapsedSinceInputBufferQueueFull() {
        if (mPesQueueFullWhenMs == 0)
            return 0;
        else
            return (SystemClock.elapsedRealtime() - mPesQueueFullWhenMs);
    }

    protected boolean waitForConfigurationRetry() {
        return mLastConfigureErrorTime > 0 &&
                SystemClock.elapsedRealtime() - mLastConfigureErrorTime < INTERVAL_CONFIGURE_RETRY;
    }

    void handleConfigurationError(String errorMessage) {
        if (errorMessage == null) {
            mConfigureErrorCount = 0;
            mLastConfigureErrorTime = 0;
        } else if (mConfigureErrorCount < MAX_COUNT_CONFIGURE_RETRY) {
            ASPlayerLog.w("%s configuration failed, retry..: %d", getTag(), mConfigureErrorCount);
            mConfigureErrorCount++;
            mLastConfigureErrorTime = SystemClock.elapsedRealtime();
        } else {
            ASPlayerLog.w("%s configuration failed: tried: %d", getTag(), mConfigureErrorCount);
            mConfigureErrorCount = 0;
            mLastConfigureErrorTime = 0;
            ASPlayerLog.w("%s error: %s", getTag(), errorMessage);
            setConfigurationError(errorMessage);
        }
    }

    public void setWorkMode(int workMode) {
        if (workMode == mLastWorkMode) {
            return;
        }

        ASPlayerLog.i("%s-Media set work mode: %d, last mode: %d", getTag(), workMode, mLastWorkMode);

        if (workMode == WorkMode.CACHING_ONLY) {
            mTimestampKeeper.clear();
            mError = null;
            mAtLeastOneBufferPushed = false;
            mPesQueueFullWhenMs = 0;
        }

        mTargetWorkMode = workMode;
    }

    public void setPIPMode(int pipMode) {
        if (pipMode == mLastPIPMode) {
            return;
        }

        ASPlayerLog.i("%s-Media set pip mode: %d, last mode: %d", getTag(), pipMode, mLastPIPMode);

        mTargetPIPMode = pipMode;
    }

    protected String getTag() {
        return String.format("[No-%d]-[%d]%s", mSyncInstanceId, mId, getName());
    }
}