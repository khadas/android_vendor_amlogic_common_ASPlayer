package com.amlogic.asplayer.core;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Surface;


import com.amlogic.asplayer.api.PlaybackControl.TransitionModeBefore;
import com.amlogic.asplayer.api.PlaybackControl.TransitionModeAfter;
import com.amlogic.asplayer.api.PlaybackControl.ScreenColor;
import com.amlogic.asplayer.api.PlaybackControl.VideoMute;
import com.amlogic.asplayer.api.VideoParams;
import com.amlogic.asplayer.api.VideoTrickMode;
import com.amlogic.asplayer.api.WorkMode;


import static android.media.MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback;

class VideoOutputPath extends MediaOutputPath {

    private static final boolean DEBUG = false;

    interface VideoFormatListener {
        void onVideoSizeInfoChanged(int width, int height, int pixelAspectRatio);
        void onAfdInfoChanged(byte activeFormat);
        void onFrameRateChanged(int frameRate);
        void onVFType(int vfType);
    }

    private class MediaCodecStarter implements Runnable {
        @Override
        public void run() {
            if (mMediaCodec != null) {
                ASPlayerLog.i("%s start mediacodec: %s", getTag(), mMediaCodec);
                startMediaCodec();
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
    protected MediaCodec.Callback mMediaCodecCallback;
    protected boolean mSecurePlayback;

    protected int mAudioSessionId = Constant.INVALID_AUDIO_SESSION_ID;
    protected boolean mTunneledPlayback;
    protected MediaCodec.OnFrameRenderedListener mMediaCodecOnFrameCallback;

    private MediaCodecStarter mMediaCodecStarter;

    protected Surface mSurface;
    protected Surface mDummySurface;

    protected boolean mFirstFrameDisplayed;

    protected VideoFormatListener mVideoFormatListener;

    protected VideoParams mVideoParams;

    protected int mPixelAspectRatio;
    private String mMimeType;
    protected int mVideoWidth;
    protected int mVideoHeight;
    protected int mFrameRate;
    protected byte mActiveFormat;
    protected Integer mVFType;

    protected int mTrickMode = VideoTrickMode.NONE;
    protected double mTrickModeSpeed;

    protected int mTransitionModeBefore = TransitionModeBefore.BLACK;
    protected int mTransitionModeAfter = TransitionModeAfter.PREROLL_FROM_FIRST_IMAGE;
    protected boolean mTransitionModeAfterSet = false;
    protected Integer mTransitionScreenColor = null;

    protected float mTransitionPreRollRate = 0.f;
    protected int mTransitionPreRollAVTolerance = 0;

    protected int mVideoMute = VideoMute.UN_MUTE;
    protected boolean mRequestVideoMute = false;

    protected Integer mSolidScreenColor = null;

    protected int mVideoTargetPlaybackStatus = -1;

    protected boolean mChangeWorkMode = false;
    protected boolean mMediaCodecStarted = false;
    protected long mMediaCodecStartTimeMillisecond = -1;


    VideoOutputPath(int id) {
        super(id);
    }

    void setAudioSessionId(int sessionId) {
        ASPlayerLog.i("%s setAudioSessionId: %d", getTag(), sessionId);
        mAudioSessionId = sessionId;
    }

    void setTunneledPlayback(boolean tunneledPlayback) {
        mTunneledPlayback = tunneledPlayback;
    }

    void setSurface(Surface surface) {
        ASPlayerLog.i("%s setSurface: %s", getTag(), surface);
        Surface oldSurface = mSurface;
        mSurface = surface;

        if (surface == null) {
            ASPlayerLog.i("%s setSurface: surface is null, release mediacodec", getTag());
            if (mMediaCodec != null) {
                releaseMediaCodec();
            }
        }

        if (oldSurface != null && oldSurface != surface && isConfigured()) {
            setConfigured(false);
        } else if (surface == null) {
            setConfigured(false);
        }
    }

    void setVideoParams(VideoParams videoParams) {
        this.mVideoParams = videoParams;
    }

    boolean hasVideoParams() {
        return this.mVideoParams != null;
    }

    void setDummySurface(Surface surface) {
        ASPlayerLog.i("%s setDummySurface: %s", getTag(), surface);
        mDummySurface = surface;
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

    public int getVideoWidth() {
        return mVideoWidth;
    }

    public int getVideoHeight() {
        return mVideoHeight;
    }

    public int getFrameRate() {
        return mFrameRate;
    }

    public int getPixelAspectRatio() {
        return mPixelAspectRatio;
    }

    public byte getAFD() {
        return mActiveFormat;
    }

    public int getVFType() {
        return mVFType != null ? mVFType.intValue() : 0;
    }

    @Override
    public boolean configure() {
        return false;
    }

    protected void setRequestChangeWorkMode(boolean changeWorkMode) {
        mChangeWorkMode = changeWorkMode;
        ASPlayerLog.i("%s setRequestChangeWorkMode: %b", getTag(), changeWorkMode);
    }

    protected boolean switchMediaCodecWorkMode() {
        if (!mChangeWorkMode) {
            ASPlayerLog.i("%s switch mediacodec work mode, same work mode, return", getTag());
            return true;
        }

        return switchWorkModeByReConfigure();
    }

    private boolean switchWorkModeByReConfigure() {
        if (TextUtils.isEmpty(mMimeType) || mVideoWidth <= 0 || mVideoHeight <= 0) {
            ASPlayerLog.i("%s switch mediacodec work mode, video format not set mime: %s, width: %d, height: %d",
                    getTag(), mMimeType, mVideoWidth, mVideoHeight);
            return false;
        } else if (mMediaCodec == null) {
            ASPlayerLog.i("%s can not switch mediacodec work mode, mediacodec is null", getTag());
            return false;
        } else if (mTargetWorkMode == WorkMode.NORMAL && mSurface == null) {
            ASPlayerLog.i("%s can not switch mediacodec work mode, normal mode, but no surface", getTag());
            return false;
        } else if (mTargetWorkMode == WorkMode.CACHING_ONLY && mDummySurface == null) {
            ASPlayerLog.i("%s can not switch mediacodec work mode, cache mode, but no dummy surface", getTag());
            return false;
        }

        ASPlayerLog.i("%s configure switch work mode, mediacodec: %s, work mode: %d",
                getTag(), mMediaCodec, mTargetWorkMode);
        try {
            long beginTime = System.nanoTime();
            long startTime = beginTime;
            if (mMediaCodecStarted) {
                ASPlayerLog.i("%s [KPI-FCC] switchMediaCodecWorkMode stop before, workMode: %d",
                        getTag(), mTargetWorkMode);
                stopMediaCodec();
                ASPlayerLog.i("%s [KPI-FCC] switchMediaCodecWorkMode stop end, workMode: %d, cost: %d ms",
                        getTag(), mTargetWorkMode, getCostTime(startTime));
            }

            MediaFormat format = MediaFormat.createVideoFormat(mMimeType, mVideoWidth, mVideoHeight);
            onSetVideoFormat(format);

            Surface surface = mSurface;

            if (mTargetWorkMode == WorkMode.NORMAL) {
                surface = mSurface;
                format.setInteger(FccWorkMode.MEDIA_FORMAT_KEY_FCC_WORKMODE, FccWorkMode.MEDIA_FORMAT_FCC_WORKMODE_NORMAL);
            } else if (mTargetWorkMode == WorkMode.CACHING_ONLY) {
                surface = mDummySurface;
                format.setInteger(FccWorkMode.MEDIA_FORMAT_KEY_FCC_WORKMODE, FccWorkMode.MEDIA_FORMAT_FCC_WORKMODE_CACHE);
            }

            String surfaceTag = (mDummySurface != null && surface == mDummySurface) ? "dummy surface" : "normal surface";

            mMediaCodec.setOnFrameRenderedListener(mMediaCodecOnFrameCallback, mHandler);
            mMediaCodec.setCallback(mMediaCodecCallback, mHandler);
            if (mMediaDescrambler == null) {
                ASPlayerLog.i("%s configure mediacodec without descrambler, surface: %s, format: %s",
                        getTag(), surfaceTag, format);
                ASPlayerLog.i("%s [KPI-FCC] switchMediaCodecWorkMode configure before, workMode: %d",
                        getTag(), mTargetWorkMode);
                startTime = System.nanoTime();
                mMediaCodec.configure(format, surface, null, 0);
                ASPlayerLog.i("%s [KPI-FCC] switchMediaCodecWorkMode configure end, workMode: %d, configure cost: %d ms",
                        getTag(), mTargetWorkMode, getCostTime(startTime));
            } else {
                ASPlayerLog.i("%s switchMediaCodecWorkMode configure mediacodec with descrambler, surface: %s",
                        getTag(), surfaceTag);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mMediaCodec.configure(format, surface, 0, mMediaDescrambler);
                } else {
                    ASPlayerLog.i("%s configure mediacodec failed, sdk version too small", getTag());
                }
            }

            ASPlayerLog.i("%s [KPI-FCC] switchMediaCodecWorkMode start before, workMode: %d, mediacodec: %s",
                    getTag(), mTargetWorkMode, mMediaCodec);
            startTime = System.nanoTime();
            startMediaCodec();
            long endTime = System.nanoTime();
            ASPlayerLog.i("%s [KPI-FCC] switchMediaCodecWorkMode start end, workMode: %d, cost: %d ms",
                    getTag(), mTargetWorkMode, getCostTime(startTime, endTime));

            ASPlayerLog.i("%s [KPI-FCC] switchMediaCodecWorkMode workMode: %d, totalTime: %d ms, mediecodec: %s",
                    getTag(), mTargetWorkMode, getCostTime(beginTime, endTime), mMediaCodec);
            setRequestChangeWorkMode(false);
            mLastWorkMode = mTargetWorkMode;
            return true;
        } catch (Exception exception) {
            ASPlayerLog.w("%s can't switch mediacodec work mode, error:%s", getTag(), exception.getMessage());
            ASPlayerLog.i("%s [KPI-FCC] switchMediaCodecWorkMode switch WorkMode failed", getTag());
            if (mMediaCodec != null) {
                releaseMediaCodec(mMediaCodec);
                mMediaCodec = null;
            }
            setConfigurationError(exception.getMessage());
        }

        return false;
    }

    protected void onSetVideoFormat(MediaFormat format) {
        if (format == null) {
            return;
        }

        // activate tunneled playback if needed
        if (mTunneledPlayback) {
            format.setFeatureEnabled(MediaCodecInfo.CodecCapabilities.FEATURE_TunneledPlayback, true);
        }
        if (mAudioSessionId != Constant.INVALID_AUDIO_SESSION_ID) {
            format.setInteger(MediaFormat.KEY_AUDIO_SESSION_ID, mAudioSessionId);
        } else {
            ASPlayerLog.e("%s onSetVideoFormat no audioSessionId", getTag());
        }

        // activate secure playback if needed
        if (mSecurePlayback) {
            format.setFeatureEnabled(MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback, true);
        }
    }

    @Override
    protected void pushInputBuffer() {
        if (DEBUG) ASPlayerLog.i("%s push input buffer", getTag());
        if (!isConfigured() && !configure())
            return;
    }

    protected void updateVideoSizeInfo(int width, int height, int pixelAspectRatio) {
        boolean needNotify = false;
        if (width > 0 && width != mVideoWidth) {
            mVideoWidth = width;
            needNotify = true;
        }
        if (height > 0 && height != mVideoHeight) {
            mVideoHeight = height;
            needNotify = true;
        }
        if (pixelAspectRatio > 0 && pixelAspectRatio != mPixelAspectRatio) {
            mPixelAspectRatio = pixelAspectRatio;
            needNotify = true;
        }
        if (mVideoWidth <= 0 || mVideoHeight <= 0 || mPixelAspectRatio <= 0) {
            needNotify = false;
        }
        if (needNotify && mVideoFormatListener != null) {
            ASPlayerLog.i("%s video:%dx%d, aspect ratio:%d", getTag(), mVideoWidth, mVideoHeight, mPixelAspectRatio);
            mVideoFormatListener.onVideoSizeInfoChanged(mVideoWidth, mVideoHeight, mPixelAspectRatio);
        }
    }

    protected void updateVideoResolutionInfo(int width, int height) {
        updateVideoSizeInfo(width, height, 0);
    }

    protected void updateVideoAspectRatioInfo(int pixelAspectRatio) {
        updateVideoSizeInfo(mVideoWidth, mVideoHeight, pixelAspectRatio);
    }

    protected void updateVideoFrameRateInfo(int frameRate) {
        if (frameRate != mFrameRate) {
            mFrameRate = frameRate;
            ASPlayerLog.i("%s frameRate: %d", getTag(), frameRate);

            if (mVideoFormatListener != null) {
                mVideoFormatListener.onFrameRateChanged(mFrameRate);
            }
        }
    }

    protected void updateAfdInfo(byte activeFormat) {
        if (activeFormat > 0 && activeFormat != mActiveFormat) {
            mActiveFormat = activeFormat;
            ASPlayerLog.i("%s active format:%02x", getTag(), activeFormat);

            if (mVideoFormatListener != null) {
                mVideoFormatListener.onAfdInfoChanged(activeFormat);
            }
        }
    }

    protected void updateVFTypeInfo(int vfType) {
        if (mVFType == null || vfType != mVFType.intValue()) {
            mVFType = new Integer(vfType);
            ASPlayerLog.i("%s vf_type: %d", getTag(), vfType);

            if (mVideoFormatListener != null) {
                mVideoFormatListener.onVFType(vfType);
            }
        }
    }

    public void flush() {
        ASPlayerLog.i("%s flush mediacodec: %s", getTag(), mMediaCodec);
        if (mMediaCodec != null) {
            mMediaCodec.flush();
            discardOutstandingCallbacksAndStart();
        }

        mFirstFrameDisplayed = false;

        mTimestampKeeper.clear();
        if (mInputBufferQueue != null)
            mInputBufferQueue.clear();
    }

    @Override
    public void reset() {
        ASPlayerLog.i("%s reset stop mediacodec: %s", getTag(), mMediaCodec);
        if (mMediaCodec != null) {
            // if need to keep last frame, don't set screen color
            if (mTransitionModeBefore == TransitionModeBefore.LAST_IMAGE) {
                handleSetTransitionModeBefore();
            } else if (mTransitionScreenColor != null) {
                handleSetTransitionScreenColor(mTransitionScreenColor.intValue());
            }
            stopMediaCodec();
            releaseMediaCodec();
        }
        mFirstFrameDisplayed = false;

        mMediaCodecStarted = false;
        mMediaCodecStartTimeMillisecond = -1;

        if (mMediaCodecStarter != null) {
            if (mHandler != null) {
                mHandler.removeCallbacks(mMediaCodecStarter);
            }
        }
        mMediaCodecStarter = null;

        mVideoWidth = 0;
        mVideoHeight = 0;
        mPixelAspectRatio = 0;
        mFrameRate = 0;
        mActiveFormat = 0;
        mVFType = null;

        mTrickModeSpeed = 0;

        mVideoParams = null;
        mMimeType = null;

        super.reset();
    }

    protected void startMediaCodec(MediaCodec mediaCodec) {
        if (mediaCodec == null) {
            return;
        }

        mediaCodec.start();
    }

    protected void startMediaCodec() {
        if (mMediaCodec != null) {
            startMediaCodec(mMediaCodec);
            mMediaCodecStarted = true;
            mMediaCodecStartTimeMillisecond = System.nanoTime() / 1000000;
            onMediaCodecStarted();
        }
    }

    protected void onMediaCodecStarted() {

    }

    protected void stopMediaCodec(MediaCodec mediaCodec) {
        if (mediaCodec == null) {
            return;
        }

        try {
            mediaCodec.stop();
        } catch (Exception e) {
            ASPlayerLog.e("%s stop mediacodec error: %s", getTag(), e.getMessage());
            e.printStackTrace();
        }
    }

    protected void stopMediaCodec() {
        if (mMediaCodec != null) {
            stopMediaCodec(mMediaCodec);
            onMediaCodecStopped();
        }
        mMediaCodecStarted = false;
        mMediaCodecStartTimeMillisecond = -1;
    }

    protected void onMediaCodecStopped() {

    }

    protected void releaseMediaCodec(MediaCodec mediaCodec) {
        if (mediaCodec == null) {
            return;
        }

        try {
            mediaCodec.release();
        } catch (Exception e) {
            ASPlayerLog.e("%s release mediacodec error: %s", getTag(), e.getMessage());
            e.printStackTrace();
        }
    }

    protected void releaseMediaCodec() {
        if (mMediaCodec != null) {
            releaseMediaCodec(mMediaCodec);
            onMediaCodecReleased();
        }
        mMediaCodecStarted = false;
        mMediaCodec = null;
    }

    protected void onMediaCodecReleased() {

    }

    @Override
    public void release() {
        super.release();

        mVideoParams = null;
        mMimeType = null;

        mSurface = null;
        mDummySurface = null;
    }

    public void setFreeRunMode() {
        super.setFreeRunMode();
    }

    long render() {
        if (mTunneledPlayback)
            return 10000;

        if (!hasOutputBuffers())
            return 10000;

        if (!mClock.isStarted())
            return 10000;

        // TODO : need some development
        if (mClock.getSpeed() == 0.0)
            return 10000;

        return 10000;
    }

    int getNbOutputBuffers() {
        return 0;
    }

    @Override
    boolean hasOutputBuffers() {
        return false;
    }

    @Override
    long getNextOutputTimestamp() {
        return 0;
    }

    @Override
    void checkErrors() {
        if (getError() != null) {
            ASPlayerLog.w("%s error %s, reset mediacodec", getTag(), getError());
            reset();
        }
    }

    @Override
    public String getName() {
        return "VideoOutputPath";
    }

    private void discardOutstandingCallbacksAndStart() {
        ASPlayerLog.i("%s discardOutstandingCallbacksAndStart mediacodec: %s", getTag(), mMediaCodec);
        if (mHandler == null) {
            return;
        }

        if (mMediaCodecStarter != null) {
            mHandler.removeCallbacks(mMediaCodecStarter);
        } else {
            mMediaCodecStarter = new MediaCodecStarter();
        }
        mHandler.post(mMediaCodecStarter);
    }

    void setTrickMode(int trickMode) {
        mTrickMode = trickMode;
    }

    int getTrickMode() {
        return mTrickMode;
    }

    void setTrickModeSpeed(double speed) {
        mTrickModeSpeed = speed;
    }

    void setParameters(Bundle params) {
        if (mMediaCodec != null) {
            mMediaCodec.setParameters(params);
        }
    }

    void setTransitionModeBefore(int transitionModeBefore) {
        ASPlayerLog.i("%s setTransitionModeBefore, mode: %d", getTag(), transitionModeBefore);
        mTransitionModeBefore = transitionModeBefore;
    }

    protected void handleSetTransitionModeBefore() {

    }

    void setTransitionModeAfter(int transitionModeAfter) {
        ASPlayerLog.i("%s setTransitionModeAfter, mode: %d", getTag(), transitionModeAfter);
        mTransitionModeAfter = transitionModeAfter;
        mTransitionModeAfterSet = true;
    }

    protected void handleSetTransitionModeAfter() {

    }

    void setTransitionScreenColor(int screenColor) {
        mTransitionScreenColor = new Integer(screenColor);
    }

    protected void handleSetTransitionScreenColor(int screenColor) {

    }

    void setTransitionPreRollRate(float rate) {
        mTransitionPreRollRate = rate;
    }

    protected void handleSetTransitionPreRollRate() {

    }

    void setTransitionPreRollAVTolerance(int milliSecond) {
        mTransitionPreRollAVTolerance = milliSecond;
    }

    protected void handleSetTransitionPreRollAVTolerance() {

    }

    void setVideoMute(int mute) {
        mVideoMute = mute;
        mRequestVideoMute = true;
    }

    protected void handleSetVideoMute() {

    }

    void setScreenColor(int screenColorMode, int screenColor) {
        switch (screenColorMode) {
            case ScreenColor.MODE_ONCE_TRANSITION:
                setTransitionScreenColor(screenColor);
                break;
            case ScreenColor.MODE_ONCE_SOLID:
                setScreenColorOnce(screenColor);
                break;
        }
    }

    protected void setScreenColorOnce(int screenColor) {
        mSolidScreenColor = Integer.valueOf(screenColor);
    }

    protected void setPlaybackStatus(int playbackStatus) {
        mVideoTargetPlaybackStatus = playbackStatus;
    }

    protected void handleSetPlaybackStatus(int playbackStatus) {

    }

    protected static long getCostTime(long startNanoTime) {
        return getCostTime(startNanoTime, System.nanoTime());
    }

    protected static long getCostTime(long startNanoTime, long endNanoTime) {
        return (endNanoTime - startNanoTime) / 1000000;
    }

    @Override
    public void setWorkMode(int workMode) {
        if (workMode == mLastWorkMode) {
            return;
        }

        ASPlayerLog.i("%s setWorkMode: %d, last mode: %d", getTag(), workMode, mLastWorkMode);

        if (workMode == WorkMode.CACHING_ONLY) {
            mFirstFrameDisplayed = false;
        }

        super.setWorkMode(workMode);

        setRequestChangeWorkMode(true);

        ASPlayerLog.i("%s setWorkMode, configured: %b, mediacodec: %s", getTag(), isConfigured(), mMediaCodec);
        if (mMediaCodec != null) {
            if (switchMediaCodecWorkMode()) {
                setConfigured(true);
                return;
            } else {
                ASPlayerLog.i("%s switch mediacodec workMode failed", getTag());
            }
        }

        setConfigured(false);
        setRequestChangeWorkMode(true);
    }

    protected void resetWorkMode() {
        if (mMediaCodecStarted) {
            ASPlayerLog.i("%s [KPI-FCC] resetWorkMode stop start, workMode: %d", getTag(), mLastWorkMode);
            long startTime = System.nanoTime();
            stopMediaCodec();
            ASPlayerLog.i("%s [KPI-FCC] resetWorkMode stop end, workMode: %d, cost: %d ms",
                    getTag(), mLastWorkMode, getCostTime(startTime));
        }
    }
}