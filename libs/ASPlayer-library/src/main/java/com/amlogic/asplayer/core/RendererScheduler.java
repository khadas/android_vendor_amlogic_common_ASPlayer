/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.core;

import static com.amlogic.asplayer.core.Constant.SPEED_DIFF_THRESHOLD;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaCodec;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import com.amlogic.asplayer.api.IASPlayer;
import com.amlogic.asplayer.api.StreamType;
import com.amlogic.asplayer.api.VideoTrickMode;
import com.amlogic.asplayer.core.VideoPassthroughParameters.VideoOnlyPlayControl;
import com.amlogic.asplayer.core.utils.MathUtils;

class RendererScheduler implements Runnable, MediaOutputPath.DecoderListener,
        MediaOutputPath.DataListener {

    private static final boolean DEBUG = false;

    private enum AVState {
        INIT,
        START,
        PAUSE,
        STOP
    }

    private final IASPlayer mASPlayer;
    private Context mContext;
    private ASPlayerConfig mConfig;

    // for identification
    private int mId;
    private int mSyncInstanceId = Constant.INVALID_SYNC_INSTANCE_ID;

    private Handler mHandler;

    // speed and position
    private double mSpeed;
    private final PositionHandler mPositionHandler;

    private VideoOutputPath mVideoOutputPath;
    private AudioOutputPathBase mAudioOutputPath;
    private boolean mHasVideo;
    private boolean mHasAudio;

    private AVState mTargetVideoState;
    private AVState mTargetAudioState;

    private EventNotifier mEventNotifier;

    // renderer, by speed
    private Renderer mCurrentSpeedTask;
    private final Renderer mPlaybackTask;
    private final Renderer mSmoothSpeedTask;
    private final Renderer mSpeedBySeekTask;
    private final Renderer mSpeedByIOnlyTask;
    private final Renderer mNoVideoSpeedTask;

    private boolean mFirstVideoFrameDisplayed = false;
    private boolean mFirstAudioFrameDisplayed = false;

    private int mWorkMode = -1;
    private int mPIPMode = -1;

    private boolean mPausedByVideoOnly = false;

    RendererScheduler(int id,
                      Context context,
                      IASPlayer asPlayer,
                      ASPlayerConfig config,
                      VideoOutputPath videoOutputPath,
                      AudioOutputPathBase audioOutputPath,
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
        setDecoderListener(this);
        setDataListener(this);

        mPositionHandler = new PositionHandler(mId);

        int playbackMode = mConfig.getPlaybackMode();

        if (playbackMode == ASPlayerConfig.PLAYBACK_MODE_PASSTHROUGH) {
            ASPlayerLog.i("%s playback mode passthrough", getTag());
            mPlaybackTask = new RendererPlaybackV3(mId, this);
            mSmoothSpeedTask = new RendererTrickSmoothV3(mId, this);
            mSpeedBySeekTask = new RendererTrickBySeekV3(mId, this);
            mSpeedByIOnlyTask = new RendererTrickIOnlyV3(mId, this);
        } else {
            ASPlayerLog.i("%s playback mode normal mode", getTag());
            mPlaybackTask = new RendererPlayback(mId, this);
            mSmoothSpeedTask = new RendererTrickSmooth(mId, this);
            mSpeedBySeekTask = new RendererTrickBySeek(mId, this);
            mSpeedByIOnlyTask = new RendererTrickIOnly(mId, this);
        }

        mNoVideoSpeedTask = new RendererTrickNoVideo(mId, this);

        mTargetVideoState = AVState.INIT;
        mTargetAudioState = AVState.INIT;
    }

    void setSyncInstanceId(int syncInstanceId) {
        mSyncInstanceId = syncInstanceId;
        mPlaybackTask.setSyncInstanceId(syncInstanceId);
        mSmoothSpeedTask.setSyncInstanceId(syncInstanceId);
        mSpeedBySeekTask.setSyncInstanceId(syncInstanceId);
        mNoVideoSpeedTask.setSyncInstanceId(syncInstanceId);
        mPositionHandler.setSyncInstanceId(syncInstanceId);
    }

    IASPlayer getASPlayer() {
        return mASPlayer;
    }

    VideoOutputPath getVideoOutputPath() {
        return mVideoOutputPath;
    }

    AudioOutputPathBase getAudioOutputPath() {
        return mAudioOutputPath;
    }

    PositionHandler getPositionHandler() {
        return mPositionHandler;
    }

    void prepare(Handler handler) {
        mHandler = handler;
        mAudioOutputPath.setHandler(handler);
        mVideoOutputPath.setHandler(handler);

        mSpeed = 1.0;

        // for tunneled playing
        AudioManager audioManager =
                (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            int audioSessionId = audioManager.generateAudioSessionId();
            if (audioSessionId >= 0) {
                mVideoOutputPath.setAudioSessionId(audioSessionId);
                mAudioOutputPath.setAudioSessionId(audioSessionId);
            } else {
                ASPlayerLog.e("%s failed to generateAudioSessionId, error: %d", getTag(), audioSessionId);
            }
        }

        mPlaybackTask.prepare(mContext, handler);
        if (mWorkMode != -1) {
            mPlaybackTask.setWorkMode(mWorkMode);
        }
        if (mPIPMode != -1) {
            mPlaybackTask.setPIPMode(mPIPMode);
        }
    }

    void release() {
        ASPlayerLog.i("%s release", getTag());
        mTargetVideoState = AVState.INIT;
        mTargetAudioState = AVState.INIT;
        if (mPlaybackTask != null) {
            mPlaybackTask.release();
        }
        setDecoderListener(null);
        setDataListener(null);

        if (mHandler != null) {
            mHandler.removeCallbacks(this);
        }
        mHandler = null;
    }

    double getSpeed() {
        return mSpeed;
    }

    void setDecoderListener(MediaOutputPath.DecoderListener listener) {
        mVideoOutputPath.setDecoderListener(listener);
        mAudioOutputPath.setDecoderListener(listener);
    }

    void setDataListener(MediaOutputPath.DataListener listener) {
        mAudioOutputPath.setDataListener(listener);
        mVideoOutputPath.setDataListener(listener);
    }

    void setVideoFormatListener(VideoOutputPath.VideoFormatListener listener) {
        mVideoOutputPath.setVideoFormatListener(listener);
    }

    void setAudioFormatListener(AudioOutputPathBase.AudioFormatListener listener) {
        mAudioOutputPath.setAudioFormatListener(listener);
    }

    boolean hasVideo() {
        return mHasVideo;
    }

    boolean hasAudio() {
        return mHasAudio;
    }

    private void selectRendererTask() {
        ASPlayerLog.i("%s speed: %f, hasVideo: %b, hasAudio: %b", getTag(), mSpeed, mHasVideo, mHasAudio);

        Renderer selectedSpeedTask;
        if (!mHasVideo) {
            selectedSpeedTask = mNoVideoSpeedTask;
        } else {
            int trickMode = mVideoOutputPath.getTrickMode();
            ASPlayerLog.i("%s trick mode: %d", getTag(), trickMode);
            switch (trickMode) {
                case VideoTrickMode.TRICK_MODE_SMOOTH:
                    selectedSpeedTask = mSmoothSpeedTask;
                    break;
                case VideoTrickMode.TRICK_MODE_BY_SEEK:
                    selectedSpeedTask = mSpeedBySeekTask;
                    break;
                case VideoTrickMode.TRICK_MODE_IONLY:
                    selectedSpeedTask = mSpeedByIOnlyTask;
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
        ASPlayerLog.i("%s setSpeedTask: %s", getTag(), speedTask);
        Renderer previousRenderer = mCurrentSpeedTask;
        mCurrentSpeedTask = speedTask;
        if (previousRenderer != null && previousRenderer != mCurrentSpeedTask) {
            previousRenderer.reset(Renderer.RESET_REASON_RENDERER_CHANGED);
        }
        if (mCurrentSpeedTask != null) {
            setRenderTaskState(mCurrentSpeedTask);
            mCurrentSpeedTask.setSpeed(previousRenderer, mSpeed);
        }
    }

    private void setRenderTaskState(Renderer rendererTask) {
        if (rendererTask == null) {
            return;
        }

        ASPlayerLog.i("%s setRenderTaskState video state: %s, audio state: %s, task: %s",
                getTag(), mTargetVideoState, mTargetAudioState, rendererTask);

        switch (mTargetVideoState) {
            case START:
                rendererTask.startVideo();
                break;
            case PAUSE:
            case STOP:
                rendererTask.stopVideo();
                break;
            case INIT:
            default:
                break;
        }

        switch (mTargetAudioState) {
            case START:
                rendererTask.startAudio();
                break;
            case PAUSE:
            case STOP:
                rendererTask.stopAudio();
                break;
            case INIT:
            default:
                break;
        }
    }

    public void run() {
        if (DEBUG) ASPlayerLog.i("%s run current speed task: %s", getTag(), mCurrentSpeedTask);
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
            if (DEBUG) ASPlayerLog.i("%s render scheduler post run delay", getTag());
            if (mHandler != null) {
                mHandler.postDelayed(this, delayUs / 1000);
            }
        } catch (MediaCodec.CodecException codecException) {
            ASPlayerLog.w("%s emergency stop, codec exception : diagnostic:%s, code:%d, recoverable:%b, transient:%b, error:%s",
                    getTag(),
                    codecException.getDiagnosticInfo(),
                    codecException.getErrorCode(),
                    codecException.isRecoverable(),
                    codecException.isTransient(),
                    codecException.getMessage());
            stop();
        } catch (Exception exception) {
            ASPlayerLog.w("%s emergency stop, unexpected critical error: %s, %s", getTag(),
                    exception.getMessage(), Log.getStackTraceString(exception));
            stop();
        }
    }

    void prepareStart() {
        mSpeed = 1.0;
        mCurrentSpeedTask = mPlaybackTask;

        mCurrentSpeedTask.setSpeed(null, mSpeed);
    }

    void startVideoDecoding() {
        ASPlayerLog.i("%s startVideoDecoding start", getTag());
        mTargetVideoState = AVState.START;
        mFirstVideoFrameDisplayed = false;
        startVideoRenderIfNeed();
        if (mPausedByVideoOnly) {
            mVideoOutputPath.setPlaybackStatus(VideoOnlyPlayControl.PLAYBACK_STATUS_PLAY);
        }
        mPausedByVideoOnly = false;
        ASPlayerLog.i("%s startVideoDecoding end", getTag());
    }

    private void startVideoRenderIfNeed() {
        // we need start video render whether has video or not.
        if (isTargetStateStart(mTargetVideoState)) {
            stopRendererTask();
            if (mCurrentSpeedTask != null) {
                ASPlayerLog.i("%s currentSpeedTask: %s", getTag(), mCurrentSpeedTask);
                mCurrentSpeedTask.startVideo();
            }
            startRendererTaskIfNeed();
        }
    }

    private void stopRendererTask() {
        ASPlayerLog.d("%s stopRendererTask", getTag());
        mHandler.removeCallbacks(this);
    }

    private void startRendererTaskIfNeed() {
        if (canStartRendererTask()) {
            mHandler.removeCallbacks(this);
            ASPlayerLog.d("%s need startRendererTask", getTag());
            mHandler.post(this);
        }
    }

    private void stopRendererTaskIfNeed() {
        if (!canStartRendererTask()) {
            ASPlayerLog.d("%s need stopRendererTask", getTag());
            mHandler.removeCallbacks(this);
        }
    }

    private boolean canStartRendererTask() {
        boolean videoCanStart = mVideoOutputPath != null && mVideoOutputPath.hasVideoParams()
                && isTargetStateStart(mTargetVideoState);
        boolean audioCanStart = mAudioOutputPath != null && mAudioOutputPath.hasAudioFormat()
                && isTargetStateStart(mTargetAudioState);

        return videoCanStart || audioCanStart;
    }

    private boolean isTargetStateStart(AVState state) {
        return state == AVState.START;
    }

    void stopVideoDecoding() {
        ASPlayerLog.i("%s stopVideoDecoding start", getTag());
        mTargetVideoState = AVState.STOP;
        mFirstVideoFrameDisplayed = false;
        if (mCurrentSpeedTask != null) {
            mCurrentSpeedTask.stopVideo();
        }
        stopRendererSchedulerIfNeed();

        ASPlayerLog.i("%s reset VideoOutputPath", getTag());
        mVideoOutputPath.reset();
        mPausedByVideoOnly = false;

        ASPlayerLog.i("%s stopVideoDecoding end", getTag());
    }

    private void stopRendererSchedulerIfNeed() {
        if (!isTargetStateStart(mTargetVideoState) && !isTargetStateStart(mTargetAudioState)) {
            stop();
        }
    }

    void pauseVideoDecoding() {
        ASPlayerLog.i("%s pauseVideoDecoding start", getTag());
        mTargetVideoState = AVState.PAUSE;
        mFirstVideoFrameDisplayed = false;
        if (mCurrentSpeedTask != null) {
            mCurrentSpeedTask.stopVideo();
        }
        stopRendererTaskIfNeed();

        if (mConfig.getPlaybackMode() == ASPlayerConfig.PLAYBACK_MODE_PASSTHROUGH) {
            if (hasAudio()) {
                mPausedByVideoOnly = false;
                ASPlayerLog.i("%s pauseVideoDecoding has audio pause by audio", getTag());
                // we can not pause video only
                pauseAudioDecoding();
            } else {
                ASPlayerLog.i("%s pauseVideoDecoding has no audio, video only pause", getTag());
                mVideoOutputPath.setPlaybackStatus(VideoOnlyPlayControl.PLAYBACK_STATUS_PAUSE);
                mPausedByVideoOnly = true;
            }
        }
    }

    void resumeVideoDecoding() {
        ASPlayerLog.i("%s resumeVideoDecoding start", getTag());
        mTargetVideoState = AVState.START;
        mFirstVideoFrameDisplayed = false;
        startVideoRenderIfNeed();

        if (mConfig.getPlaybackMode() == ASPlayerConfig.PLAYBACK_MODE_PASSTHROUGH) {
            if (hasAudio()) {
                ASPlayerLog.i("%s resumeVideoDecoding has audio resume by audio", getTag());
                resumeAudioDecoding();
            } else {
                if (mPausedByVideoOnly) {
                    ASPlayerLog.i("%s resumeVideoDecoding has no audio, video only resume", getTag());
                    mVideoOutputPath.setPlaybackStatus(VideoOnlyPlayControl.PLAYBACK_STATUS_PLAY);
                    mPausedByVideoOnly = false;
                }
            }
        }
    }

    void startAudioDecoding() {
        ASPlayerLog.i("%s startAudioDecoding start", getTag());
        mTargetAudioState = AVState.START;
        mFirstAudioFrameDisplayed = false;
        startAudioRenderIfNeed();
        ASPlayerLog.i("%s startAudioDecoding end", getTag());
    }

    private void startAudioRenderIfNeed() {
        if (isTargetStateStart(mTargetAudioState) && hasAudio()) {
            stopRendererTask();
            if (mCurrentSpeedTask != null) {
                mCurrentSpeedTask.startAudio();
            }
            startRendererTaskIfNeed();
        }
    }

    void stopAudioDecoding() {
        ASPlayerLog.i("%s stopAudioDecoding start", getTag());
        mTargetAudioState = AVState.STOP;
        mFirstAudioFrameDisplayed = false;
        if (mCurrentSpeedTask != null) {
            mCurrentSpeedTask.stopAudio();
        }
        stopRendererSchedulerIfNeed();
        mAudioOutputPath.reset();
        ASPlayerLog.i("%s stopAudioDecoding end", getTag());
    }

    void pauseAudioDecoding() {
        ASPlayerLog.i("%s pauseAudioDecoding start", getTag());
        mTargetAudioState = AVState.PAUSE;
        mFirstAudioFrameDisplayed = false;
        if (mCurrentSpeedTask != null) {
            mCurrentSpeedTask.stopAudio();
        }
        stopRendererTaskIfNeed();
        mAudioOutputPath.pause();
    }

    void resumeAudioDecoding() {
        ASPlayerLog.i("%s resumeAudioDecoding start", getTag());
        mTargetAudioState = AVState.START;
        mFirstAudioFrameDisplayed = false;
        startAudioRenderIfNeed();
        mAudioOutputPath.resume();
    }

    void onSetVideoParams(boolean hasVideo) {
        mHasVideo = hasVideo;
        mFirstVideoFrameDisplayed = false;

        // we need start video render whether has video or not.
        startVideoRenderIfNeed();
    }

    void onSetAudioParams(boolean hasAudio) {
        mHasAudio = hasAudio;
        mFirstAudioFrameDisplayed = false;

        if (hasAudio) {
            startAudioRenderIfNeed();
        }
    }

    void stop() {
        ASPlayerLog.i("%s stop", getTag());
        if (mHandler != null) {
            mHandler.removeCallbacks(this);
        }

        mPositionHandler.reset();
        mSpeed = 1.0;
    }

    void setSpeed(double speed) {
        if (MathUtils.equals(mSpeed, speed, SPEED_DIFF_THRESHOLD))
            return;

        mSpeed = speed;

        selectRendererTask();
    }

    private void onMediaFrame(MediaOutputPath outputPath, long presentationTimeUs, long renderTime) {
        if (mPositionHandler.isUpdateNeeded()) {
            mPositionHandler.setPresentationTimestampUs(presentationTimeUs);
        }

        if (mVideoOutputPath != null && outputPath == mVideoOutputPath) {
            onVideoFrame(presentationTimeUs, renderTime);
        } else if (mAudioOutputPath != null && outputPath == mAudioOutputPath) {
            onAudioFrame(presentationTimeUs, renderTime);
        }
    }

    private void onVideoFrame(long presentationTimeUs, long renderTime) {
        if (!mFirstVideoFrameDisplayed) {
            ASPlayerLog.i("%s first video frame", getTag());
            mEventNotifier.notifyRenderFirstVideoFrame(presentationTimeUs, renderTime);
            mFirstVideoFrameDisplayed = true;
        }

        if (mConfig.isPtsEventEnabled()) {
            mEventNotifier.notifyVideoFrameRendered(presentationTimeUs, renderTime);
        }
    }

    private void onAudioFrame(long presentationTimeUs, long renderTime) {
        if (!mFirstAudioFrameDisplayed) {
            ASPlayerLog.i("%s first audio frame", getTag());
            mEventNotifier.notifyRenderFirstAudioFrame(presentationTimeUs, renderTime);
            mFirstAudioFrameDisplayed = true;
        }

        if (mConfig.isPtsEventEnabled()) {
            mEventNotifier.notifyAudioFrameRendered(presentationTimeUs, renderTime);
        }
    }

    @Override
    public void onDecoderInitCompleted(MediaOutputPath outputPath) {
        if (mVideoOutputPath != null && outputPath == mVideoOutputPath) {
            mEventNotifier.notifyVideoDecoderInitCompleted();
        } else if (mAudioOutputPath != null && outputPath == mAudioOutputPath) {
            mEventNotifier.notifyAudioDecoderInitCompleted();
        }
    }

    @Override
    public void onFirstData(MediaOutputPath outputPath) {
    }

    @Override
    public void onDecoderDataLoss(MediaOutputPath outputPath) {
        if (mVideoOutputPath != null && outputPath == mVideoOutputPath) {
            mEventNotifier.notifyDecoderDataLoss(StreamType.VIDEO);
        } else if (mAudioOutputPath != null && outputPath == mAudioOutputPath) {
            mEventNotifier.notifyDecoderDataLoss(StreamType.AUDIO);
        }
    }

    @Override
    public void onDecoderDataResume(MediaOutputPath outputPath) {
        if (mVideoOutputPath != null && outputPath == mVideoOutputPath) {
            mEventNotifier.notifyDecoderDataResume(StreamType.VIDEO);
        } else if (mAudioOutputPath != null && outputPath == mAudioOutputPath) {
            mEventNotifier.notifyDecoderDataResume(StreamType.AUDIO);
        }
    }

    void setWorkMode(int workMode) {
        if (workMode == mWorkMode) {
            return;
        }

        ASPlayerLog.i("%s setWorkMode: %d, last mode: %d", getTag(), workMode, mWorkMode);

        if (mHandler != null) {
            mHandler.removeCallbacks(this);

            ASPlayerLog.i("%s speed task: %s, playback task: %s", getTag(), mCurrentSpeedTask, mPlaybackTask);

            if (mCurrentSpeedTask != null) {
                mCurrentSpeedTask.setWorkMode(workMode);
            }
            if (mPlaybackTask != null && mPlaybackTask != mCurrentSpeedTask) {
                mPlaybackTask.setWorkMode(workMode);
            }

            mHandler.post(this);
        } else {
            // not prepared
            ASPlayerLog.i("%s setWorkMode: %d, not prepared", getTag(), workMode);
        }

        mWorkMode = workMode;
    }

    void setPIPMode(int pipMode) {
        ASPlayerLog.i("%s setPIPMode start, mode: %d", getTag(), pipMode);

        if (pipMode == mPIPMode) {
            return;
        }

        ASPlayerLog.i("%s setPIPMode: %d, last mode: %d", getTag(), pipMode, mPIPMode);

        if (mHandler != null) {
            mHandler.removeCallbacks(this);

            ASPlayerLog.i("%s speed task: %s, playback task: %s", getTag(), mCurrentSpeedTask, mPlaybackTask);

            if (mCurrentSpeedTask != null) {
                mCurrentSpeedTask.setPIPMode(pipMode);
            }
            if (mPlaybackTask != null && mPlaybackTask != mCurrentSpeedTask) {
                mPlaybackTask.setPIPMode(pipMode);
            }

            mHandler.post(this);
        } else {
            // not prepared
            ASPlayerLog.i("%s setPIPMode: %d, not prepared", getTag(), pipMode);
        }

        mPIPMode = pipMode;
    }

    void flush() {
        ASPlayerLog.i("%s flush start", getTag());

        mAudioOutputPath.flush();
        mVideoOutputPath.flush();
        mPositionHandler.unsetOrigin();

        mFirstVideoFrameDisplayed = false;
        mFirstAudioFrameDisplayed = false;
    }

    void setTrickMode(int trickMode) {
        mVideoOutputPath.setTrickMode(trickMode);
    }

    private String getTag() {
        return String.format("[No-%d]-[%d]RendererScheduler", mSyncInstanceId, mId);
    }
}