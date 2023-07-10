/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.core;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaCodec;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import com.amlogic.asplayer.api.IASPlayer;
import com.amlogic.asplayer.api.VideoTrickMode;

class RendererScheduler implements Runnable {

    private static final boolean DEBUG = false;

    private static final String TAG = RendererScheduler.class.getSimpleName();

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
    private boolean mHasVideo;
    private boolean mHasAudio;

    private boolean mVideoStarted;
    private boolean mAudioStarted;

    private EventNotifier mEventNotifier;

    // renderer, by speed
    private Renderer mCurrentSpeedTask;
    private final Renderer mPlaybackTask;
    private final Renderer mSmoothSpeedTask;
    private final Renderer mSpeedBySeekTask;
    private final Renderer mNoVideoSpeedTask;

    private boolean mFirstVideoFrameDisplayed = false;
    private boolean mFirstAudioFrameDisplayed = false;

    private int mWorkMode = -1;
    private int mPIPMode = -1;

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

        mPositionHandler = new PositionHandler(mId);

        int playbackMode = mConfig.getPlaybackMode();

        if (playbackMode == ASPlayerConfig.PLAYBACK_MODE_PASSTHROUGH) {
            ASPlayerLog.i("playback mode passthrough");
            mPlaybackTask = new RendererPlaybackV3(this);
            mSmoothSpeedTask = new RendererTrickSmoothV3(mId, this);
            mSpeedBySeekTask = new RendererTrickBySeekV3(mId, this);
        } else {
            ASPlayerLog.i("playback mode normal mode");
            mPlaybackTask = new RendererPlayback(this);
            mSmoothSpeedTask = new RendererTrickSmooth(mId, this);
            mSpeedBySeekTask = new RendererTrickBySeek(mId, this);
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
        if (mWorkMode != -1) {
            mPlaybackTask.setWorkMode(mWorkMode);
        }
        if (mPIPMode != -1) {
            mPlaybackTask.setPIPMode(mPIPMode);
        }
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

    void setVideoFormatListener(VideoOutputPath.VideoFormatListener listener) {
        mVideoOutputPath.setVideoFormatListener(listener);
    }

    void setAudioFormatListener(AudioOutputPath.AudioFormatListener listener) {
        mAudioOutputPath.setAudioFormatListener(listener);
    }

    private void selectRendererTask() {
        ASPlayerLog.i("RendererScheduler-%d speed: %f", mId, mSpeed);

        boolean isNormalPlaySpeed = Math.abs(mSpeed - 1) < SPEED_DIFF_THRESHOLD;
        boolean isPauseSpeed = Math.abs(mSpeed) < SPEED_DIFF_THRESHOLD;

        Renderer selectedSpeedTask;
        if (isPauseSpeed) {
            selectedSpeedTask = mPlaybackTask;
        } else if (isNormalPlaySpeed) {
            selectedSpeedTask = mPlaybackTask;
        } else if (!mHasVideo) {
            selectedSpeedTask = mNoVideoSpeedTask;
        } else {
            int trickMode = mVideoOutputPath.getTrickMode();
            ASPlayerLog.i("RendererScheduler-%d trick mode: %d", mId, trickMode);
            switch (trickMode) {
                case VideoTrickMode.TRICK_MODE_SMOOTH:
                    selectedSpeedTask = mSmoothSpeedTask;
                    break;
                case VideoTrickMode.TRICK_MODE_BY_SEEK:
                case VideoTrickMode.TRICK_MODE_IONLY:
                    selectedSpeedTask = mSpeedBySeekTask;
                    break;
                case VideoTrickMode.NONE:
                default:
                    selectedSpeedTask = mPlaybackTask;
                    break;
            }
        }

        setSpeedTask(selectedSpeedTask);
    }

    private void setSpeedTask(Renderer speedTask) {
        ASPlayerLog.i("RendererScheduler-%d setSpeedTask: %s", mId, speedTask);
        Renderer previousRenderer = mCurrentSpeedTask;
        mCurrentSpeedTask = speedTask;
        if (previousRenderer != null && previousRenderer != mCurrentSpeedTask) {
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
            if (mCurrentSpeedTask == null)
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
        mCurrentSpeedTask = mPlaybackTask;

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
        if (mCurrentSpeedTask != null) {
            mCurrentSpeedTask.stopVideo();
        }
        stopRendererSchedulerIfNeed();

        ASPlayerLog.i("RendererScheduler-%d reset VideoOutputPath", mId);
        mVideoOutputPath.reset();

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
        if (mCurrentSpeedTask != null) {
            mCurrentSpeedTask.stopVideo();
        }
        stopRendererTaskIfNeed();

        if (mConfig.getPlaybackMode() == ASPlayerConfig.PLAYBACK_MODE_PASSTHROUGH) {
            // we can not pause video only
            pauseAudioDecoding();
        }
    }

    void resumeVideoDecoding() {
        mVideoStarted = true;
        mFirstVideoFrameDisplayed = false;
        stopRendererTask();
        if (mCurrentSpeedTask != null) {
            mCurrentSpeedTask.startVideo();
        }
        startRendererTaskIfNeed();

        if (mConfig.getPlaybackMode() == ASPlayerConfig.PLAYBACK_MODE_PASSTHROUGH) {
            resumeAudioDecoding();
        }
    }

    void startAudioDecoding() {
        ASPlayerLog.i("RendererScheduler-%d startAudioDecoding start", mId);
        mAudioStarted = true;
        mFirstAudioFrameDisplayed = false;
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
        mFirstAudioFrameDisplayed = false;
        if (mCurrentSpeedTask != null) {
            mCurrentSpeedTask.stopAudio();
        }
        stopRendererSchedulerIfNeed();
        mAudioOutputPath.reset();
        ASPlayerLog.i("RendererScheduler-%d stopAudioDecoding end", mId);
    }

    void pauseAudioDecoding() {
        mAudioStarted = false;
        mFirstAudioFrameDisplayed = false;
        if (mCurrentSpeedTask != null) {
            mCurrentSpeedTask.stopAudio();
        }
        stopRendererTaskIfNeed();
        mAudioOutputPath.pause();
    }

    void resumeAudioDecoding() {
        mAudioStarted = true;
        mFirstAudioFrameDisplayed = false;
        stopRendererTask();
        if (mCurrentSpeedTask != null) {
            mCurrentSpeedTask.startAudio();
        }
        startRendererTaskIfNeed();
        mAudioOutputPath.resume();
    }

    void onSetVideoParams(boolean hasVideo) {
        mHasVideo = hasVideo;
    }

    void onSetAudioParams(boolean hasAudio) {
        mHasAudio = hasAudio;
    }

    void stop() {
        ASPlayerLog.i("RendererScheduler-%d stop", mId);
        if (mHandler != null) {
            mHandler.removeCallbacks(this);
        }

        mPositionHandler.reset();
        mSpeed = 1.0;
    }

    void setSpeed(double speed) {
        if (Math.abs(mSpeed - speed) < SPEED_DIFF_THRESHOLD)
            return;

        mSpeed = speed;

        selectRendererTask();
    }

    private void onMediaFrame(MediaOutputPath outputPath, long presentationTimeUs, long renderTime) {
//        ASPlayerLog.i("RendererScheduler-%d onMediaFrame", mId);
        if (mPositionHandler.isUpdateNeeded()) {
            mPositionHandler.setPresentationTimestampUs(presentationTimeUs);
        }

        if (mHasVideo && outputPath == mVideoOutputPath) {
            onVideoFrame(presentationTimeUs, renderTime);
        } else if (mHasAudio && outputPath == mAudioOutputPath) {
            onAudioFrame(presentationTimeUs, renderTime);
        }
    }

    private void onVideoFrame(long presentationTimeUs, long renderTime) {
        if (!mFirstVideoFrameDisplayed) {
            ASPlayerLog.i("RendererScheduler-%d first video frame", mId);
            mEventNotifier.notifyRenderFirstVideoFrame(renderTime);
            mFirstVideoFrameDisplayed = true;
        }

        if (mConfig.isPtsEventEnabled()) {
            mEventNotifier.notifyVideoFrameRendered(presentationTimeUs, renderTime);
        }
    }

    private void onAudioFrame(long presentationTimeUs, long renderTime) {
        if (!mFirstAudioFrameDisplayed) {
            ASPlayerLog.i("RendererScheduler-%d first audio frame", mId);
            mEventNotifier.notifyRenderFirstAudioFrame(renderTime);
            mFirstAudioFrameDisplayed = true;
        }

        if (mConfig.isPtsEventEnabled()) {
            mEventNotifier.notifyAudioFrameRendered(presentationTimeUs, renderTime);
        }
    }

    void setWorkMode(int workMode) {
        if (workMode == mWorkMode) {
            return;
        }

        ASPlayerLog.i("RendererScheduler-%d setWorkMode: %d, last mode: %d", mId, workMode, mWorkMode);

        if (mHandler != null) {
            mHandler.removeCallbacks(this);

            ASPlayerLog.i("RendererScheduler-%d speed task: %s, playback task: %s", mId, mCurrentSpeedTask, mPlaybackTask);

            if (mCurrentSpeedTask != null) {
                mCurrentSpeedTask.setWorkMode(workMode);
            }
            if (mPlaybackTask != null && mPlaybackTask != mCurrentSpeedTask) {
                mPlaybackTask.setWorkMode(workMode);
            }

            mHandler.post(this);
        } else {
            // not prepared
            ASPlayerLog.i("RendererScheduler-%d setWorkMode: %d, not prepared", mId, workMode);
        }

        mWorkMode = workMode;
    }

    void setPIPMode(int pipMode) {
        ASPlayerLog.i("RendererScheduler-%d setPIPMode start, mode: %d", mId, pipMode);

        if (pipMode == mPIPMode) {
            return;
        }

        ASPlayerLog.i("RendererScheduler-%d setPIPMode: %d, last mode: %d", mId, pipMode, mPIPMode);

        if (mHandler != null) {
            mHandler.removeCallbacks(this);

            ASPlayerLog.i("RendererScheduler-%d speed task: %s, playback task: %s", mId, mCurrentSpeedTask, mPlaybackTask);

            if (mCurrentSpeedTask != null) {
                mCurrentSpeedTask.setPIPMode(pipMode);
            }
            if (mPlaybackTask != null && mPlaybackTask != mCurrentSpeedTask) {
                mPlaybackTask.setPIPMode(pipMode);
            }

            mHandler.post(this);
        } else {
            // not prepared
            ASPlayerLog.i("RendererScheduler-%d setPIPMode: %d, not prepared", mId, pipMode);
        }

        mPIPMode = pipMode;
    }

    void flush() {
        ASPlayerLog.i("RendererScheduler-%d flush start", mId);

        mAudioOutputPath.reset();
        mVideoOutputPath.flush();
        mPositionHandler.unsetOrigin();

        mFirstVideoFrameDisplayed = false;
        mFirstAudioFrameDisplayed = false;
    }

    void setTrickMode(int trickMode) {
        mVideoOutputPath.setTrickMode(trickMode);
    }
}