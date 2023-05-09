package com.amlogic.asplayer.core;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaCodec;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import com.amlogic.asplayer.api.IASPlayer;
import com.amlogic.asplayer.api.WorkMode;

class RendererScheduler implements Runnable {

    private static final boolean DEBUG = false;

    private static final String TAG = Constant.LOG_TAG;

    private static final double SPEED_DIFF_THRESHOLD = 0.001;

    private final IASPlayer mASPlayer;
    private Context mContext;
    private ASPlayerConfig mConfig;

    // for identification
    private int mId;

    private Handler mHandler;

    // speed and position
    private double mSpeed;
    private final PositionHandler mPositionHandler;

    private VideoOutputPath mVideoOutputPath;
    private AudioOutputPath mAudioOutputPath;

    private boolean mVideoStarted;
    private boolean mAudioStarted;

    private EventNotifier mEventNotifier;

    // renderer, by speed
    private Renderer mCurrentSpeedTask;
    private final RendererStarter mStarterTask;
    private final Renderer mPlaybackTask;
    private final Renderer mSmoothSpeedTask;
    private final Renderer mNoVideoSpeedTask;

    private boolean mFirstVideoFrameDisplayed = false;
    private boolean mFirstAudioFrameShowed = false;

    RendererScheduler(int id,
                      Context context,
                      IASPlayer asPlayer,
                      ASPlayerConfig config,
                      VideoOutputPath videoOutputPath,
                      AudioOutputPath audioOutputPath,
                      EventNotifier eventNotifier) {
        mId = id;
        mContext = context;
        mASPlayer = asPlayer;
        mConfig = config;
        mVideoOutputPath = videoOutputPath;
        mAudioOutputPath = audioOutputPath;
        mEventNotifier = eventNotifier;

        mSpeed = 1.0;
        MediaOutputPath.FrameListener frameListener = this::onMediaFrame;
        mAudioOutputPath.setFrameListener(frameListener);
        mVideoOutputPath.setFrameListener(frameListener);

        mPositionHandler = new PositionHandler();

        mStarterTask = new RendererStarter(this);

        int playbackMode = mConfig.getPlaybackMode();

        if (playbackMode == ASPlayerConfig.PLAYBACK_MODE_PASSTHROUGH) {
            ASPlayerLog.i("playback mode passthrough");
            mPlaybackTask = new RendererPlaybackV3(this);
            mSmoothSpeedTask = new RendererTrickSmoothV3(mId, this);
        } else {
            ASPlayerLog.i("playback mode normal mode");
            mPlaybackTask = new RendererPlayback(this);
            mSmoothSpeedTask = new RendererTrickSmooth(mId, this);
        }

        mNoVideoSpeedTask = new RendererTrickNoVideo(mId, this);
    }

    IASPlayer getASPlayer() {
        return mASPlayer;
    }

    VideoOutputPath getVideoOutputPath() {
        return mVideoOutputPath;
    }

    AudioOutputPath getAudioOutputPath() {
        return mAudioOutputPath;
    }

    PositionHandler getPositionHandler() {
        return mPositionHandler;
    }

    void prepare(int id, Handler handler) {
        mId = id;
        mHandler = handler;
        mAudioOutputPath.setHandler(handler);
        mVideoOutputPath.setHandler(handler);

        // for tunneled playing
        AudioManager audioManager =
                (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            int audioSessionId = audioManager.generateAudioSessionId();
            mVideoOutputPath.setAudioSessionId(audioSessionId);
            mAudioOutputPath.setAudioSessionId(audioSessionId);
        }

        mPlaybackTask.prepare(id, mContext, handler);
    }

    void release() {
        mPlaybackTask.release();
        mVideoOutputPath.release();
        mAudioOutputPath.release();
        setHandler(null);
    }

    double getSpeed() {
        return mSpeed;
    }

    void setHandler(Handler handler) {
        mHandler = handler;
        mAudioOutputPath.setHandler(handler);
        mVideoOutputPath.setHandler(handler);
    }

    void setDataListener(MediaOutputPath.DataListener listener) {
        mAudioOutputPath.setDataListener(listener);
        mVideoOutputPath.setDataListener(listener);
    }

    void setVideoListener(VideoOutputPath.VideoFormatListener listener) {
        mVideoOutputPath.setVideoFormatListener(listener);
    }

    private void selectRendererTask() {
        ASPlayerLog.i("RendererScheduler-%d speed: %f", mId, mSpeed);

        boolean isNormalPlaySpeed = Math.abs(mSpeed - 1) < SPEED_DIFF_THRESHOLD;
        boolean isPauseSpeed = Math.abs(mSpeed) < SPEED_DIFF_THRESHOLD;

        Renderer selectedSpeedTask = mCurrentSpeedTask;
        if (isPauseSpeed) {
            // don't change the renderer task, unless there is none
            if (mCurrentSpeedTask == null || mCurrentSpeedTask == mStarterTask)
                selectedSpeedTask = mPlaybackTask;
        } else if (isNormalPlaySpeed) {
            selectedSpeedTask = mPlaybackTask;
        } else if (mSpeed > 0 && mConfig.canSupportSmoothTrick(mSpeed)) {
            selectedSpeedTask = mSmoothSpeedTask;
        }

        setSpeedTask(selectedSpeedTask);
    }

    private void setSpeedTask(Renderer speedTask) {
        ASPlayerLog.i("RendererScheduler-%d setSpeedTask: %s", mId, speedTask);
        Renderer previousRenderer = mCurrentSpeedTask;
        mCurrentSpeedTask = speedTask;
        if (previousRenderer != mCurrentSpeedTask) {
            previousRenderer.reset(Renderer.RESET_REASON_RENDERER_CHANGED);
        }
        if (mCurrentSpeedTask != null) {
            mCurrentSpeedTask.setSpeed(previousRenderer, mSpeed);
        }
    }

    public void run() {
        if (DEBUG) ASPlayerLog.i("RendererScheduler-%d run current speed task: %s", mId, mCurrentSpeedTask);
        try {
            long to = SystemClock.elapsedRealtime();
            long delayUs = 10000;
            if (mCurrentSpeedTask != null)
                delayUs = mCurrentSpeedTask.doSomeWork();
            if (mCurrentSpeedTask == mStarterTask)
                selectRendererTask();
            long t1 = SystemClock.elapsedRealtime();
            long marginUs = (40 - (t1 - to)) * 1000;
            delayUs = Math.max(0, Math.min(delayUs, marginUs));
            if (DEBUG) ASPlayerLog.i("RendererScheduler-%d render scheduler post run delay", mId);
            if (mHandler != null) {
                mHandler.postDelayed(this, delayUs / 1000);
            }
        } catch (MediaCodec.CodecException codecException) {
            ASPlayerLog.w("RendererScheduler-%d emergency stop, codec exception : diagnostic:%s, code:%d, recoverable:%b, transient:%b, error:%s",
                    mId,
                    codecException.getDiagnosticInfo(),
                    codecException.getErrorCode(),
                    codecException.isRecoverable(),
                    codecException.isTransient(),
                    codecException.getMessage());
            stop();
        } catch (Exception exception) {
            ASPlayerLog.w("RendererScheduler-%d emergency stop, unexpected critical error: %s", mId, exception.getMessage(), exception);
            Log.d(TAG, String.format("RendererScheduler-%d emergency stop, unexpected critical error", mId), exception);
            stop();
        }
    }

    void prepareStart() {
        mSpeed = 1.0;
        // shortcut for default speed
        if (mSpeed == 1)
            mCurrentSpeedTask = mPlaybackTask;
        // else mStarterTask will get the list of tracks to be able to select the right renderer
        else
            mCurrentSpeedTask = mStarterTask;

        mCurrentSpeedTask.setSpeed(null, mSpeed);
    }

    void startVideoDecoding() {
        ASPlayerLog.i("RendererScheduler-%d startVideoDecoding start", mId);
        mVideoStarted = true;
        mFirstVideoFrameDisplayed = false;
        stopRendererTask();
        if (mCurrentSpeedTask != null) {
            ASPlayerLog.i("RendererScheduler-%d currentSpeedTask: %s", mId, mCurrentSpeedTask);
            mCurrentSpeedTask.startVideo();
        }
        startRendererTaskIfNeed();
        ASPlayerLog.i("RendererScheduler-%d startVideoDecoding end", mId);
    }

    private void stopRendererTask() {
        ASPlayerLog.d("RendererScheduler-%d stopRendererTask", mId);
        mHandler.removeCallbacks(this);
    }

    private void startRendererTaskIfNeed() {
        if (canStartRendererTask()) {
            mHandler.removeCallbacks(this);
            ASPlayerLog.d("RendererScheduler-%d need startRendererTask", mId);
            mHandler.post(this);
        }
    }

    private void stopRendererTaskIfNeed() {
        if (!canStartRendererTask()) {
            ASPlayerLog.d("RendererScheduler-%d need stopRendererTask", mId);
            mHandler.removeCallbacks(this);
        }
    }

    private boolean canStartRendererTask() {
        boolean videoCanStart = mVideoOutputPath != null && mVideoOutputPath.hasMediaFormat() && mVideoStarted;
        boolean audioCanStart = mAudioOutputPath != null && mAudioOutputPath.hasMediaFormat() && mAudioStarted;

        return videoCanStart || audioCanStart;
    }

    void stopVideoDecoding() {
        ASPlayerLog.i("RendererScheduler-%d stopVideoDecoding start", mId);
        mVideoStarted = false;
        mFirstVideoFrameDisplayed = false;
        stopRendererSchedulerIfNeed();
        if (mCurrentSpeedTask != null) {
            mCurrentSpeedTask.stopVideo();
        }
        mVideoOutputPath.reset();
        ASPlayerLog.i("RendererScheduler-%d release VideoOutputPath", mId);
        ASPlayerLog.i("RendererScheduler-%d stopVideoDecoding end", mId);
    }

    private void stopRendererSchedulerIfNeed() {
        if (!mVideoStarted && !mAudioStarted) {
            stop();
        }
    }

    void pauseVideoDecoding() {
        mVideoStarted = false;
        mFirstVideoFrameDisplayed = false;
        stopRendererTaskIfNeed();
        if (mCurrentSpeedTask != null) {
            mCurrentSpeedTask.stopVideo();
        }
        mVideoOutputPath.reset();
    }

    void resumeVideoDecoding() {
        mVideoStarted = true;
        mFirstVideoFrameDisplayed = false;
        stopRendererTask();
        if (mCurrentSpeedTask != null) {
            mCurrentSpeedTask.startVideo();
        }
        startRendererTaskIfNeed();
    }

    void startAudioDecoding() {
        ASPlayerLog.i("RendererScheduler-%d startAudioDecoding start", mId);
        mAudioStarted = true;
        mFirstAudioFrameShowed = false;
        stopRendererTask();
        if (mCurrentSpeedTask != null) {
            mCurrentSpeedTask.startAudio();
        }
        startRendererTaskIfNeed();
        ASPlayerLog.i("RendererScheduler-%d startAudioDecoding end", mId);
    }

    void stopAudioDecoding() {
        ASPlayerLog.i("RendererScheduler-%d stopAudioDecoding start", mId);
        mAudioStarted = false;
        mFirstAudioFrameShowed = false;
        stopRendererSchedulerIfNeed();
        if (mCurrentSpeedTask != null) {
            mCurrentSpeedTask.stopAudio();
        }
        mAudioOutputPath.reset();
        ASPlayerLog.i("RendererScheduler-%d stopAudioDecoding end", mId);
    }

    void pauseAudioDecoding() {
        mAudioStarted = false;
        mFirstAudioFrameShowed = false;
        stopRendererTaskIfNeed();
        if (mCurrentSpeedTask != null) {
            mCurrentSpeedTask.stopAudio();
        }
        mAudioOutputPath.reset();
    }

    void resumeAudioDecoding() {
        mAudioStarted = true;
        mFirstAudioFrameShowed = false;
        stopRendererTask();
        if (mCurrentSpeedTask != null) {
            mCurrentSpeedTask.startAudio();
        }
        startRendererTaskIfNeed();
    }

    void onSetVideoParams() {
        startRendererTaskIfNeed();
    }

    void onSetAudioParams() {
        startRendererTaskIfNeed();
    }

    void stop() {
        ASPlayerLog.i("RendererScheduler-%d stop", mId);
        if (mHandler != null) {
            mHandler.removeCallbacks(this);
        }

        mPositionHandler.reset();
        mCurrentSpeedTask = null;
        mSpeed = 1.0;
    }

    void setSpeed(double speed) {
        if (Math.abs(mSpeed - speed) < SPEED_DIFF_THRESHOLD)
            return;

        mSpeed = speed;

        selectRendererTask();
    }

    void setPositionUs(long positionUs) {
        ASPlayerLog.i("RendererScheduler-%d Seek at position : %d ms", mId, positionUs / 1000);
        if (mCurrentSpeedTask != null) {
            mCurrentSpeedTask.setPositionUs(positionUs);
        }
    }

    private void onMediaFrame(MediaOutputPath outputPath, long timestampUs) {
//        ASPlayerLog.i("RendererScheduler-%d onMediaFrame", mId);
        if (!mPositionHandler.isUpdateNeeded() && mFirstVideoFrameDisplayed && mFirstAudioFrameShowed) return;
//        ASPlayerLog.i("RendererScheduler-%d onMediaFrame need update, mFirstVideoFrameDisplayed: %b, mFirstAudioFrameShowed: %b", mId, mFirstVideoFrameDisplayed, mFirstAudioFrameShowed);

        long positionUs = mPositionHandler.getPositionUs();
        if (outputPath != mVideoOutputPath && outputPath != mAudioOutputPath) {
            return;
        }

        if (outputPath == mVideoOutputPath && !mFirstVideoFrameDisplayed) {
            ASPlayerLog.i("RendererScheduler-%d first video frame", mId);
            mEventNotifier.notifyDecodeFirstVideoFrame(positionUs, mSpeed);
            mFirstVideoFrameDisplayed = true;
        } else if (outputPath == mAudioOutputPath && !mFirstAudioFrameShowed) {
            ASPlayerLog.i("RendererScheduler-%d first audio frame", mId);
            mEventNotifier.notifyDecodeFirstAudioFrame(positionUs, mSpeed);
            mFirstAudioFrameShowed = true;
        }
    }

    void flush() {
        mVideoOutputPath.flush();
        mAudioOutputPath.reset();
        mPositionHandler.unsetOrigin();
    }

}
