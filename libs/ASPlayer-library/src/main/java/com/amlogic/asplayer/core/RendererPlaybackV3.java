package com.amlogic.asplayer.core;


import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;


import com.amlogic.asplayer.api.WorkMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

class RendererPlaybackV3 extends Renderer {

    private static final boolean DEBUG = false;

    abstract class PlaybackTask {
        private static final long EXECUTION_TIMEOUT = 200;
        String what;
        long minimumExecutionTimeMs;
        long nextTime;
        long delayUs;

        int nbSkipped;
        int priority;

        PlaybackTask(String what) {
            this.what = what;
        }

        void setMinimumExecutionTimeMs(long minimumExecutionTimeMs) {
            this.minimumExecutionTimeMs = minimumExecutionTimeMs;
        }

        void setPriority(int priority) {
            this.priority = priority;
        }

        long execute() {
            if (nextTime > SystemClock.elapsedRealtime()) {
                return delayUs;
            }

            long timeMsStart = SystemClock.elapsedRealtime();
            if (minimumExecutionTimeMs == -1 ||
                    remainingTime() >= minimumExecutionTimeMs) {
                nbSkipped = 0;
                delayUs = run();
                nextTime = SystemClock.elapsedRealtime() + delayUs / 1000;
            } else {
                nbSkipped++;
                delayUs = 0;
            }
            long timeMsDuration = SystemClock.elapsedRealtime() - timeMsStart;
            if (timeMsDuration > EXECUTION_TIMEOUT) {
                ASPlayerLog.w("RendererPlaybackV3-%d long execution time (%d) detected for %s", mId, timeMsDuration, this);
            }
            return delayUs;
        }

        abstract long run();
    }

    class CheckSynchroModeTask extends PlaybackTask {
        CheckSynchroModeTask() {
            super("CheckSynchroModeTask");
        }

        long run() {
            checkSynchroMode();
            return 1000000;
        }
    }

    class FeedingTask extends PlaybackTask {
        FeedingTask() {
            super("FeedingTask");
        }

        long run() {
            handleFeeding();
            return 10000;
        }
    }

    class AudioInputBuffersTask extends PlaybackTask {
        AudioInputBuffersTask() {
            super("AudioInputBuffersTask");
        }

        long run() {
            mAudioOutputPath.checkErrors();
            mAudioOutputPath.pushInputBuffer();
            return 100000;
        }
    }

    class VideoInputBuffersTask extends PlaybackTask {
        VideoInputBuffersTask() {
            super("VideoInputBuffersTask");
        }

        long run() {
            mVideoOutputPath.checkErrors();
            mVideoOutputPath.pushInputBuffer();
            return 100000;
        }
    }

    private int mId;

    // audio caps, to reconfigure pipeline if needed
    private AudioCaps mAudioCaps;

    // tunneled mode
    private boolean mTunneledPlayback;

    // tasks
    private ArrayList<PlaybackTask> mTasks;
    private Comparator<PlaybackTask> mTaskComparator;
    private PlaybackTask mAudioInputBuffersTask;
    private PlaybackTask mVideoInputBuffersTask;
    private PlaybackTask mFeedingTask;

    // timings
    private long mDoSomeWorkStartTimeMs;
    private long mNextDelayUs;

    private int mWorkMode = -1;
    private int mPIPMode = -1;

    RendererPlaybackV3(RendererScheduler rendererScheduler) {
        super(rendererScheduler);

        PlaybackTask checkSynchroModeTask = new CheckSynchroModeTask();
        mFeedingTask = new FeedingTask();
        mAudioInputBuffersTask = new AudioInputBuffersTask();
        mVideoInputBuffersTask = new VideoInputBuffersTask();

        mTasks = new ArrayList<>();
        mTasks.add(checkSynchroModeTask);
        mTasks.add(mFeedingTask);

        checkSynchroModeTask.setMinimumExecutionTimeMs(100);
        mFeedingTask.setMinimumExecutionTimeMs(20);
        mAudioInputBuffersTask.setMinimumExecutionTimeMs(30);
        mVideoInputBuffersTask.setMinimumExecutionTimeMs(30);

        checkSynchroModeTask.setPriority(0);
        mAudioInputBuffersTask.setPriority(2);
        mVideoInputBuffersTask.setPriority(2);
        mFeedingTask.setPriority(3);

        mTaskComparator = new Comparator<PlaybackTask>() {
            @Override
            public int compare(PlaybackTask o1, PlaybackTask o2) {
                if (o1.priority < o2.priority)
                    return -1;
                if (o1.priority > o2.priority)
                    return 1;
                return Integer.compare(o1.nbSkipped, o2.nbSkipped);
            }
        };
    }

    @Override
    void startVideo() {
        super.startVideo();
        if (!mTasks.contains(mVideoInputBuffersTask)) {
            mTasks.add(mVideoInputBuffersTask);
        }
    }

    @Override
    void stopVideo() {
        super.stopVideo();
        if (mTasks.contains(mVideoInputBuffersTask)) {
            mTasks.remove(mVideoInputBuffersTask);
        }
    }

    @Override
    void startAudio() {
        super.startAudio();
        if (!mTasks.contains(mAudioInputBuffersTask)) {
            mTasks.add(mAudioInputBuffersTask);
        }
    }

    @Override
    void stopAudio() {
        super.stopAudio();
        if (mTasks.contains(mAudioInputBuffersTask)) {
            mTasks.remove(mAudioInputBuffersTask);
        }
    }

    @Override
    void prepare(int id, Context context, Handler handler) {
        mId = id;
        mAudioCaps = new AudioCaps(context, handler);
        mAudioCaps.setListener(RendererPlaybackV3.this::onAudioCapabilitiesChanged);
        mAudioOutputPath.setCaps(mAudioCaps);
    }

    void release() {
        mAudioOutputPath.setCaps(null);
        if (mAudioCaps != null)
            mAudioCaps.release();
        mAudioCaps = null;
    }

    @Override
    void setSpeed(Renderer previousRenderer, double speed) {
        ASPlayerLog.i("RendererPlaybackV3-%d speed:%f", mId, speed);
        super.setSpeed(previousRenderer, speed);

        // reset tunneled mode
        mTunneledPlayback = MediaCodecUtils.isTunneledPlaybackSupported();

        // set speed
        mVideoOutputPath.setSpeed(speed);
        mAudioOutputPath.setSpeed(speed);
        mVideoOutputPath.setTrickModeSpeed(speed);

        // tunneling mode
        mVideoOutputPath.setTunneledPlayback(mTunneledPlayback);
        mAudioOutputPath.setTunneledPlayback(mTunneledPlayback);
    }

    @Override
    protected void pumpFeederData() {
    }

    @Override
    protected void reset(int reason) {
        String[] reasons = {
                "RESET_REASON_NEW_POSITION",
                "RESET_REASON_NO_MEDIA",
                "RESET_REASON_BAD_DATA",
                "RESET_REASON_NO_DATA",
                "RESET_REASON_DISCONTINUITY",
                "RESET_REASON_DECODERS_BLOCKED",
                "RESET_REASON_RENDERER_CHANGED",
        };
        if (reason >= 1 && reason <= reasons.length)
            ASPlayerLog.i("RendererPlaybackV3-%d %s", mId, reasons[reason - 1]);
        else
            ASPlayerLog.i("RendererPlaybackV3-%d unexpected reason:%d", mId, reason);

        switch (reason) {
            case Renderer.RESET_REASON_NEW_POSITION:
            case Renderer.RESET_REASON_NO_MEDIA:
            case Renderer.RESET_REASON_BAD_DATA:
            case Renderer.RESET_REASON_NO_DATA:
                mVideoOutputPath.flush();
                mAudioOutputPath.reset();
                mPositionHandler.unsetOrigin();
                break;
            case Renderer.RESET_REASON_DISCONTINUITY:
                mPositionHandler.unsetOrigin();
                break;
            case Renderer.RESET_REASON_DECODERS_BLOCKED:
                ASPlayerLog.i("RendererPlaybackV3-%d release VideoOutputPath", mId);
                mVideoOutputPath.reset();
                mAudioOutputPath.reset();
                break;
        }
    }

    private void initLoop() {
        mDoSomeWorkStartTimeMs = SystemClock.elapsedRealtime();
        mNextDelayUs = 1000000;
    }

    private void termLoop() {
    }

    private long remainingTime() {
        return (1000 - (SystemClock.elapsedRealtime() - mDoSomeWorkStartTimeMs));
    }

    @Override
    long doSomeWork() {
//        if (DEBUG) ASPlayerLog.i("RendererPlaybackV3-%d ", mId);
        initLoop();
        // sort tasks
        // - prioritize task
        // - make sure that a task that was skipped will be executed
        Collections.sort(mTasks, mTaskComparator);

        // execute task
        long delayUs;
        for (PlaybackTask task : mTasks) {
            if (DEBUG) ASPlayerLog.i("RendererPlaybackV3-%d execute task: %s", mId, task);
            delayUs = task.execute();
            mNextDelayUs = Math.min(delayUs, mNextDelayUs);
        }

        termLoop();

        return mNextDelayUs;
    }

    private void onAudioCapabilitiesChanged(AudioCaps audioCapabilities) {
        ASPlayerLog.i("RendererPlaybackV3-%d output audio caps have changed to %s", mId, audioCapabilities);
    }

    private void checkSynchroSubtitles() {
//        long avSyncTime = Mpeg.ptsToUs(mExtractor.getAvSyncTime());
    }

    private void checkSynchroMode() {
        // if there is no audio and no video, don't try to synchronize
        if (!mAudioOutputPath.isConfigured() && !mVideoOutputPath.isConfigured()) {
            return;
        }

        // if speed is not 1, should be 0, there is no synchro
        if (mSpeed != 1) {
            return;
        }

        checkSynchroSubtitles();
    }

    @Override
    void setWorkMode(int workMode) {
        if (workMode == mWorkMode) {
            return;
        }

        ASPlayerLog.i("RendererPlaybackV3-%d set work mode: %d, last mode: %d", mId, workMode, mWorkMode);

        mVideoOutputPath.setWorkMode(workMode);
        mAudioOutputPath.setWorkMode(workMode);

        if (workMode == WorkMode.CACHING_ONLY) {
            mDoSomeWorkStartTimeMs = 0;
            mNextDelayUs = 0;
        }

        mWorkMode = workMode;
    }

    @Override
    void setPIPMode(int pipMode) {
        if (pipMode == mPIPMode) {
            return;
        }

        ASPlayerLog.i("RendererPlaybackV3-%d set pip mode: %d, last mode: %d", mId, pipMode, mPIPMode);
        mVideoOutputPath.setPIPMode(pipMode);
        mAudioOutputPath.setPIPMode(pipMode);

        mPIPMode = pipMode;
    }
}

