package com.amlogic.asplayer.core;

import android.media.MediaDescrambler;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.SystemClock;

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
        void onFrame(MediaOutputPath outputPath, long timestampUs);
    }

    interface DataListener {
        void onFirstData(MediaOutputPath outputPath);
    }

    // the handler in which all stuff must be done
    private Handler mHandler;

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
    // TODO : make private
    // est ce que l'on peut le retirer de la classe de base pour le mettre dans video et audio ?
    MediaClock mClock;

    // listeners
    private FrameListener mFrameListener;
    private DataListener mDataListener;

    // critical error : no decode for any reason
    // TODO : la gestion des erreurs, comment integrer
    // - configuration
    // - erreur de decodage
    private String mError;

    // to limit spent time in pushInputBuffer
    private long mInputBufferPushStartTimeMs;

    protected MediaFormat mMediaFormat;

    protected MediaDescrambler mMediaDescrambler;

    protected int mId;
    private String mIdTag;

    MediaOutputPath(int id, String idTag) {
        mId = id;
        mIdTag = idTag;
        mClock = new MediaClock();
        mTimestampKeeper = new TimestampKeeper(getName());
    }

    abstract String getName();

    abstract String getCodecName();

    void setHandler(Handler handler) {
        mHandler = handler;
    }

    Handler getHandler() {
        return mHandler;
    }

    void setError(String error) {
        mError = error;
    }

    String getError() {
        return mError;
    }

    void setMediaFormat(MediaFormat format) {
        this.mMediaFormat = format;
    }

    MediaFormat getMediaFormat() {
        return mMediaFormat;
    }

    boolean hasMediaFormat() {
        return mMediaFormat != null;
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
//        TvLog.i("MediaOutputPath-%s isConfigured: %b", mIdTag, mConfigurationState == CONFIGURATION_FROM_PES);
        return mConfigurationState == CONFIGURATION_FROM_PES;
    }

    boolean hasConfigurationError() {
        return (mConfigurationState == CONFIGURATION_ERROR);
    }

    void setConfigured(boolean configured) {
//        TvLog.i("MediaOutputPath-%s configured: %b", mIdTag, configured);
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

    // TODO
    // a envisager
    // - mettre release abstract
    // - ajouter une/des methode/s pour flusher les inputs, la clock, les erreurs
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
        ASPlayerLog.i("MediaOutputPath-%s release", mIdTag);
        reset();
        mInputBufferQueue = null;

        mMediaFormat = null;
        mMediaDescrambler = null;
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

                ASPlayerLog.i("MediaOutputPath-%s SYNCHRO %s - input:%d ms, output:%d, margin:%d ms, next:%d ms, origin:%d ms, last:%d ms",
                        mIdTag, getName(),
                        (lastOutputTimestampUs - nextOutputTimestamp) / 1000,
                        getNbOutputBuffers(),
                        marginUs / 1000,
                        getNextOutputTimestamp() / 1000,
                        originTimestampUs/1000,
                        lastOutputTimestampUs/1000);
                setSynchroOn(originTimestampUs);
                break;
            case MediaOutputPath.RENDER_FREE_RUN:
                ASPlayerLog.i("MediaOutputPath-%s FREE RUN %s", mIdTag, getName());
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

    void notifyFrameDisplayed(long timestampUs) {
        if (mFrameListener != null) {
            mFrameListener.onFrame(this, timestampUs);
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
            ASPlayerLog.w("configuration failed, retry..: " + mConfigureErrorCount);
            mConfigureErrorCount++;
            mLastConfigureErrorTime = SystemClock.elapsedRealtime();
        } else {
            ASPlayerLog.w("configuration failed: tried: " + mConfigureErrorCount);
            mConfigureErrorCount = 0;
            mLastConfigureErrorTime = 0;
            ASPlayerLog.w("error: " + errorMessage);
            setConfigurationError(errorMessage);
        }
    }
}
