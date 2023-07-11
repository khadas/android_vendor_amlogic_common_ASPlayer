package com.amlogic.asplayer.core;

import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

class RendererPlayback extends Renderer {
    static final int SYNCHRO_MODE_NONE = 0;
    static final int SYNCHRO_MODE_SYNCHRONIZABLE = 1;
    static final int SYNCHRO_MODE_ONE_TRACK = 2;
    static final int SYNCHRO_MODE_FREE_RUN = 3;

    // 3s is not enough as a maximum. That's why it has been increased to 5s.
    // - in input streams, delta between audio and video input buffers: about 1.8s
    // - to get the first image: up to 1.3s
    // If you change it, check also VideoOutputPath.SYNC_MAX_DELTA_IN_FUTURE_US
    static final int SYNCHRO_MAX_AV_DELTA_US = 5000000;

    // should be set, according that
    // - video position is continuous
    // - audio position is not continuous, delta is about ~20ms
    static final long MAX_AV_DELTA_US = 30000;
    //
    static final long MAX_AUDIO_SUBTITLE_DELTA_US = 100000;

    static final int SYNCHRO_AUDIO_RENDER_WINDOW_US = 20000;

    boolean mHasAudio;
    boolean mHasVideo;

    abstract class PlaybackTask {
        String what;
        long minimumExecutionTimeMs;
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
            long delayUs;
            if (minimumExecutionTimeMs == -1 ||
                    remainingTime() >= minimumExecutionTimeMs) {
                nbSkipped = 0;
                delayUs = run();
            } else {
                nbSkipped++;
                delayUs = 0;
            }
            return delayUs;
        }

        abstract long run();
    }

    class VideoRendererTask extends PlaybackTask {
        VideoRendererTask() {
            super("VideoRendererTask");
        }

        long run() {
            long delayUs = 10000;
            if (mVideoOutputPath.isConfigured())
                delayUs = mVideoOutputPath.render();

            return delayUs;
        }
    }

    class AudioRendererTask extends PlaybackTask {
        AudioRendererTask() {
            super("AudioRendererTask");
        }

        long run() {
            long delayUs = 10000;
            if (mAudioOutputPath.isConfigured())
                delayUs = mAudioOutputPath.render();

            return delayUs;
        }
    }

    class CheckSynchroModeTask extends PlaybackTask {
        CheckSynchroModeTask() {
            super("CheckSynchroModeTask");
        }

        long run() {
            checkSynchroMode();
            return 10000;
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
            return 10000;
        }
    }

    class VideoInputBuffersTask extends PlaybackTask {
        VideoInputBuffersTask() {
            super("VideoInputBuffersTask");
        }

        long run() {
            mVideoOutputPath.checkErrors();
            mVideoOutputPath.pushInputBuffer();
            return 10000;
        }
    }

    class SubtitleTask extends PlaybackTask {
        SubtitleTask() {
            super("SubtitleTask");
        }

        long run() {
            return 10000;
        }
    }

    class EitTask extends PlaybackTask {
        EitTask() {
            super("EitTask");
        }

        long run() {
            return 10000;
        }
    }

    // audio caps, to reconfigure pipeline if needed
    private AudioCaps mAudioCaps;

    // synchro
    private int mSynchroMode;

    // tunneled mode
    private boolean mTunneledPlayback;
    private long mLastAudioTimestampUpdateMs;
    private long mLastAudioTimestamp;

    // tasks
    private ArrayList<PlaybackTask> mTasks;
    private Comparator<PlaybackTask> mTaskComparator;
    private PlaybackTask mVideoRendererTask;
    private PlaybackTask mAudioInputBuffersTask;
    private PlaybackTask mVideoInputBuffersTask;
    private PlaybackTask mFeedingTask;

    // timings
    private long mDoSomeWorkStartTimeMs;
    private long mNextDelayUs;

    // work
    private long mLastMessageTimestampMs;
    private boolean mBlockingDecodersNotified;

    RendererPlayback(int id, RendererScheduler rendererScheduler) {
        super(id, rendererScheduler);

        mVideoRendererTask = new VideoRendererTask();
        PlaybackTask audioRendererTask = new AudioRendererTask();
        PlaybackTask checkSynchroModeTask = new CheckSynchroModeTask();
        mFeedingTask = new FeedingTask();
        mAudioInputBuffersTask = new AudioInputBuffersTask();
        mVideoInputBuffersTask = new VideoInputBuffersTask();
        PlaybackTask subtitleTask = new SubtitleTask();
        PlaybackTask eitTask = new EitTask();

        mTasks = new ArrayList<>();
        mTasks.add(mVideoRendererTask);
        mTasks.add(audioRendererTask);
        mTasks.add(checkSynchroModeTask);
        mTasks.add(mFeedingTask);
        mTasks.add(mAudioInputBuffersTask);
        mTasks.add(mVideoInputBuffersTask);
        mTasks.add(subtitleTask);
        mTasks.add(eitTask);

        mVideoRendererTask.setMinimumExecutionTimeMs(10);
        audioRendererTask.setMinimumExecutionTimeMs(10);
        checkSynchroModeTask.setMinimumExecutionTimeMs(5);
        mFeedingTask.setMinimumExecutionTimeMs(20);
        mAudioInputBuffersTask.setMinimumExecutionTimeMs(30);
        mVideoInputBuffersTask.setMinimumExecutionTimeMs(30);
        subtitleTask.setMinimumExecutionTimeMs(5);
        eitTask.setMinimumExecutionTimeMs(5);

        checkSynchroModeTask.setPriority(0);
        mVideoRendererTask.setPriority(1);
        audioRendererTask.setPriority(1);
        mAudioInputBuffersTask.setPriority(2);
        mVideoInputBuffersTask.setPriority(2);
        mFeedingTask.setPriority(3);
        subtitleTask.setPriority(4);
        eitTask.setPriority(4);

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
    protected String getName() {
        return "RendererPlayback";
    }

    void setHasVideo(boolean hasVideo) {
        mHasVideo = hasVideo;
    }

    void setHasAudio(boolean hasAudio) {
        mHasAudio = hasAudio;
    }

    @Override
    void prepare(Context context, Handler handler) {
        super.prepare(context, handler);
        mAudioCaps = new AudioCaps(context, handler);
        mAudioCaps.setListener(new AudioCaps.Listener() {
            @Override
            public void onAudioCapabilitiesChanged(AudioCaps audioCapabilities) {
                RendererPlayback.this.onAudioCapabilitiesChanged(audioCapabilities);
            }
        });
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
        ASPlayerLog.i("%s speed:%f", getTag(), speed);
        super.setSpeed(previousRenderer, speed);

        // TODO : suspicious
        // should be handled as setPosition ?

        // reset tunneled mode
        mTunneledPlayback = canUseTunneledPlayback();

        // ensure that all renderers are ready to play
        if (speed == 1 && previousRenderer != this) {
            ASPlayerLog.i("%s set speed, reset VideoOutputPath", getTag());
            mVideoOutputPath.reset();
            mLastMessageTimestampMs = 0;

            // TODO: was mandatory in case speed xxx -> speed 1, check if it is still true
            if (mTunneledPlayback)
                setPositionUs(mPositionHandler.getPositionUs() - 1000000);
        }

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
                "RESET_REASON_DECODERS_BLOCKED"
        };
        if (reason >= 1 && reason <= reasons.length)
            ASPlayerLog.i("%s reset %s", getTag(), reasons[reason - 1]);
        else
            ASPlayerLog.i("%s unexpected reason:%d", getTag(), reason);

        switch (reason) {
            case Renderer.RESET_REASON_NEW_POSITION:
            case Renderer.RESET_REASON_NO_MEDIA:
            case Renderer.RESET_REASON_BAD_DATA:
            case Renderer.RESET_REASON_NO_DATA:
                ASPlayerLog.i("%s reset VideoOutputPath, RESET_REASON_NO_DATA", getTag());
                mVideoOutputPath.reset();
                mAudioOutputPath.reset();
                break;
            case Renderer.RESET_REASON_DISCONTINUITY:
                ASPlayerLog.i("%s reset VideoOutputPath, RESET_REASON_DISCONTINUITY", getTag());
                mVideoOutputPath.reset();
                mAudioOutputPath.reset();
                if (mTunneledPlayback)
                    mAudioOutputPath.release();
                break;
            case Renderer.RESET_REASON_DECODERS_BLOCKED:
                mVideoOutputPath.release();
                mAudioOutputPath.release();
                break;
        }
        mSynchroMode = SYNCHRO_MODE_NONE;
        mLastMessageTimestampMs = 0;
        mLastAudioTimestampUpdateMs = 0;
        mLastAudioTimestamp = 0;
        mBlockingDecodersNotified = false;
    }

    private void initLoop() {
        mDoSomeWorkStartTimeMs = SystemClock.elapsedRealtime();
        mNextDelayUs = 40000;
    }

    private void termLoop() {
    }

    private long remainingTime() {
        return (40 - (SystemClock.elapsedRealtime() - mDoSomeWorkStartTimeMs));
    }

    @Override
    long doSomeWork() {

        initLoop();

        // if there is no more output buffer, make sure that tasks pushing input buffers
        // will be executed
        boolean hasAudio = mHasAudio;
        boolean hasVideo = mHasVideo;
        if (hasAudio && mAudioOutputPath.getNbOutputBuffers() == 0)
            mAudioInputBuffersTask.setMinimumExecutionTimeMs(-1);
        else
            mAudioInputBuffersTask.setMinimumExecutionTimeMs(30);
        if (mTunneledPlayback) {
            mVideoInputBuffersTask.setMinimumExecutionTimeMs(10);
            mVideoRendererTask.setMinimumExecutionTimeMs(50);
            mVideoRendererTask.setPriority(5);
            mVideoInputBuffersTask.setMinimumExecutionTimeMs(30);
        } else {
            if (hasVideo && mVideoOutputPath.getNbOutputBuffers() == 0)
                mVideoInputBuffersTask.setMinimumExecutionTimeMs(-1);
            else
                mVideoInputBuffersTask.setMinimumExecutionTimeMs(30);
            mVideoRendererTask.setMinimumExecutionTimeMs(10);
            mVideoRendererTask.setPriority(1);
        }
        if ((hasAudio && mAudioOutputPath.isInputEmpty()) ||
                (hasVideo && mVideoOutputPath.isInputEmpty()))
            mFeedingTask.setMinimumExecutionTimeMs(-1);
        else
            mFeedingTask.setMinimumExecutionTimeMs(20);
        // sort tasks
        // - prioritize task
        // - make sure that a task that was skipped will be executed
        Collections.sort(mTasks, mTaskComparator);

        // execute task
        long delayUs;
        for (PlaybackTask task : mTasks) {
            delayUs = task.execute();
            mNextDelayUs = Math.min(delayUs, mNextDelayUs);
        }

        termLoop();

        return mNextDelayUs;
    }

    private boolean canUseTunneledPlayback() {
        return (MediaCodecUtils.isTunneledPlaybackSupported() &&
                mAudioCaps.isHdmiPlugged() &&
                !mAudioCaps.isBluetoothDeviceReady());
    }

    private void onAudioCapabilitiesChanged(AudioCaps audioCapabilities) {
        ASPlayerLog.i("%s output audio caps have changed to %s", getTag(), audioCapabilities);

        boolean wasTunneledPlayback = mTunneledPlayback;
        mTunneledPlayback = canUseTunneledPlayback();
        if (wasTunneledPlayback != mTunneledPlayback) {
            ASPlayerLog.i("%s tunneled mode must changed:%b -> %b", getTag(), wasTunneledPlayback, mTunneledPlayback);
            mVideoOutputPath.setTunneledPlayback(mTunneledPlayback);
            mAudioOutputPath.setTunneledPlayback(mTunneledPlayback);
            mVideoOutputPath.release();
            mAudioOutputPath.release();
        }
    }

    private void monitorSynchro() {
        if (!mAudioOutputPath.isDisplayPositionValid())
            return;
        if (!mVideoOutputPath.isDisplayPositionValid())
            return;

        long audioDisplayPositionUs = mAudioOutputPath.getDisplayPositionUs();
        long videoDisplayPositionUs = mVideoOutputPath.getDisplayPositionUs();

        long avDeltaUs = audioDisplayPositionUs - videoDisplayPositionUs;
        if (Math.abs(avDeltaUs) > 5000000) {
            ASPlayerLog.w("%s monitorSynchro : huge av delta %d, there is something wrong",
                    getTag(), avDeltaUs / 1000);
        } else if (Math.abs(avDeltaUs) > MAX_AV_DELTA_US) {
            // we readjust video clock offset
            long newOffset = mVideoOutputPath.mClock.getOffsetUs() + avDeltaUs;
            mVideoOutputPath.mClock.setOffsetUs(newOffset);
            ASPlayerLog.i("%s readjust video clock with delta:%d ms, new offset:%d ms",
                    getTag(), avDeltaUs / 1000, mVideoOutputPath.mClock.getOffsetUs() / 1000);
        }
        if ((SystemClock.elapsedRealtime() - mLastMessageTimestampMs) > 5000) {
            long videoClock = mVideoOutputPath.mClock.timeUs();
            long audioClock = mAudioOutputPath.mClock.timeUs();
            String deltaAVoutputs;
            if (mAudioOutputPath.hasOutputBuffers() && mVideoOutputPath.hasOutputBuffers()) {
                long minAudioTimestampUs = mAudioOutputPath.getNextOutputTimestamp();
                long minVideoTimestampUs = mVideoOutputPath.getNextOutputTimestamp();
                long deltaAv = minAudioTimestampUs - minVideoTimestampUs;
                deltaAVoutputs = String.format(Locale.US, "d_pts_av:%d", deltaAv);
            } else {
                deltaAVoutputs = String.format("d_pts_av[a:%b, v:%b]",
                        mAudioOutputPath.hasOutputBuffers(),
                        mVideoOutputPath.hasOutputBuffers());
            }
            ASPlayerLog.i("%s d_av:%d ms, video offset:%d, d_clock[av:%d] %s",
                    getTag(), avDeltaUs / 1000, mVideoOutputPath.mClock.getOffsetUs(),
                    (audioClock - videoClock), deltaAVoutputs);
            mLastMessageTimestampMs = SystemClock.elapsedRealtime();
        }
    }

    protected void checkSynchroSubtitles() {
        if (!mAudioOutputPath.isDisplayPositionValid())
            return;
    }

    private void checkSynchroInTunneledMode() {
        // check if pipeline is blocked
        if (mSpeed == 1) {
            boolean blocking = false;
            String blockingMessage = null;
            int maxDelaySinceBufferFull = SYNCHRO_MAX_AV_DELTA_US / 1000;

            // blocked by audio
            if (mAudioOutputPath.elapsedSinceInputBufferQueueFull() > maxDelaySinceBufferFull) {
                blocking = true;
                blockingMessage = "audio injection blocked";
            }
            // blocked by video
            if (mVideoOutputPath.elapsedSinceInputBufferQueueFull() > maxDelaySinceBufferFull) {
                blocking = true;
                if (blockingMessage == null)
                    blockingMessage = "video injection blocked";
                else
                    blockingMessage = "audio and video injection blocked";
            }
            // no more timestamp
            long elapsedSinceLastTimestamp = 0;
            if (mLastAudioTimestampUpdateMs == 0) {
                if (mAudioOutputPath.getNextOutputTimestamp() != 0) {
                    mLastAudioTimestampUpdateMs = SystemClock.elapsedRealtime();
                    mLastAudioTimestamp = mAudioOutputPath.getNextOutputTimestamp();
                }
            } else {
                if ((mAudioOutputPath.getNextOutputTimestamp() != 0) &&
                        mAudioOutputPath.getNextOutputTimestamp() != mLastAudioTimestamp) {
                    mLastAudioTimestampUpdateMs = SystemClock.elapsedRealtime();
                    mLastAudioTimestamp = mAudioOutputPath.getNextOutputTimestamp();
                }
                elapsedSinceLastTimestamp =
                        SystemClock.elapsedRealtime() - mLastAudioTimestampUpdateMs;
            }
            if (elapsedSinceLastTimestamp > SYNCHRO_MAX_AV_DELTA_US) {
                blocking = true;
                blockingMessage = String.format(Locale.US, "no timestamp since %d",
                        SystemClock.elapsedRealtime() - mLastAudioTimestampUpdateMs);
            }
            // if blocked, release decoders, try to restarts
            if (blocking && !mBlockingDecodersNotified) {
                ASPlayerLog.w("%s blocking detected (%s): reset decoders ", getTag(), blockingMessage);
                reset(Renderer.RESET_REASON_DECODERS_BLOCKED);
                mBlockingDecodersNotified = true;
            }
        }

        if (mAudioOutputPath.isConfigured())
            mAudioOutputPath.checkStandaloneSynchro();
    }

    private void checkSynchroInStandardMode() {
        // if only audio or only video is configured
        if (!mAudioOutputPath.isConfigured() || !mVideoOutputPath.isConfigured()) {
            if (mAudioOutputPath.isConfigured() && mVideoOutputPath.hasConfigurationError()) {
                mAudioOutputPath.checkStandaloneSynchro();
                mSynchroMode = SYNCHRO_MODE_ONE_TRACK;
            }
            if (mVideoOutputPath.isConfigured() && mAudioOutputPath.hasConfigurationError()) {
                mVideoOutputPath.checkStandaloneSynchro();
                mSynchroMode = SYNCHRO_MODE_ONE_TRACK;
            }
            return;
        }

        // check if pipeline is blocked
        if (mAudioOutputPath.elapsedSinceInputBufferQueueFull() > SYNCHRO_MAX_AV_DELTA_US / 1000 ||
                mVideoOutputPath.elapsedSinceInputBufferQueueFull() > SYNCHRO_MAX_AV_DELTA_US / 1000) {
            ASPlayerLog.w("%s injection blocked because of audio/video decoders, must restart", getTag());
            mAudioOutputPath.release();
            mVideoOutputPath.release();
        }

        // there is no info on outputs
        if (!mAudioOutputPath.hasOutputBuffers() || !mVideoOutputPath.hasOutputBuffers())
            return;

        // output but input discontinuities
        boolean audioInputDiscontinuous = mAudioOutputPath.hasInputDiscontinuity();
        boolean videoInputDiscontinuous = mVideoOutputPath.hasInputDiscontinuity();
        if (audioInputDiscontinuous || videoInputDiscontinuous) {
            mSynchroMode = SYNCHRO_MODE_FREE_RUN;
            mAudioOutputPath.checkStandaloneSynchro();
            mVideoOutputPath.checkStandaloneSynchro();
        }
        // output and good inputs
        else if (mAudioOutputPath.hasOutputBuffers() && mVideoOutputPath.hasOutputBuffers()) {
            // compute min and max pts
            long minAutioTimestampUs = mAudioOutputPath.getNextOutputTimestamp();
            long maxAudioTimestampUs = mAudioOutputPath.getLastOutputTimestamp();
            long minVideoTimestampUs = mVideoOutputPath.getNextOutputTimestamp();
            long maxVideoTimestampUs = mVideoOutputPath.getLastOutputTimestamp();

            // compute deltas between audio and video
            long distanceMinAudioMinVideo = Math.abs(minAutioTimestampUs - minVideoTimestampUs);
            long distanceMinAudioMaxVideo = Math.abs(minAutioTimestampUs - maxVideoTimestampUs);
            long distanceMaxAudioMinVideo = Math.abs(maxAudioTimestampUs - minVideoTimestampUs);
            long distanceMaxAudioMaxVideo = Math.abs(maxAudioTimestampUs - maxVideoTimestampUs);
            long minDistanceAudioVideo =
                    Math.min(
                            Math.min(distanceMinAudioMinVideo, distanceMinAudioMaxVideo),
                            Math.min(distanceMaxAudioMinVideo, distanceMaxAudioMaxVideo));
            // deltas too big
            if (minDistanceAudioVideo > SYNCHRO_MAX_AV_DELTA_US) {
                mSynchroMode = SYNCHRO_MODE_FREE_RUN;
                mAudioOutputPath.checkStandaloneSynchro();
                mVideoOutputPath.checkStandaloneSynchro();
            } else {
                boolean mustSetClockOrigin = false;
                String setClockReason = null;

                // if clocks not started, set origins
                if (!mAudioOutputPath.mClock.isStarted() || !mVideoOutputPath.mClock.isStarted()) {
                    mustSetClockOrigin = true;
                    setClockReason = "clock(s) not started";
                }
                // if clocks are started, but too far from data, reset origin
                else {
                    long currentAudioDeltaUs =
                            minAutioTimestampUs - mAudioOutputPath.mClock.timeUs();
                    long currentVideoDeltaUs =
                            minVideoTimestampUs - mVideoOutputPath.mClock.timeUs();
                    if (currentAudioDeltaUs > SYNCHRO_MAX_AV_DELTA_US) {
                        mustSetClockOrigin = true;
                        setClockReason = String.format(Locale.US, "audio delta too high (%d/%d)ms",
                                currentAudioDeltaUs / 1000, SYNCHRO_MAX_AV_DELTA_US / 1000);
                    }
                    // TODO : check if margin is necessary
                    if ((maxAudioTimestampUs - mAudioOutputPath.mClock.timeUs()) < -SYNCHRO_AUDIO_RENDER_WINDOW_US) {
                        mustSetClockOrigin = true;
                        setClockReason = String.format(Locale.US, "max audio pts too late %d ms",
                                (maxAudioTimestampUs - mAudioOutputPath.mClock.timeUs()) / 1000);
                    }
                    if (currentVideoDeltaUs > SYNCHRO_MAX_AV_DELTA_US) {
                        mustSetClockOrigin = true;
                        setClockReason = String.format(Locale.US, "video delta too high (%d/%d)ms",
                                currentAudioDeltaUs / 1000, SYNCHRO_MAX_AV_DELTA_US / 1000);
                    }
                    if ((maxVideoTimestampUs - mVideoOutputPath.mClock.timeUs()) < 0) {
                        mustSetClockOrigin = true;
                        setClockReason = String.format(Locale.US, "max video pts too late %d ms",
                                (maxVideoTimestampUs - mVideoOutputPath.mClock.timeUs()) / 1000);
                    }
                }

                if (mustSetClockOrigin) {
                    long originTimestampUs = 0;
                    long marginUs = 0;
                    String origin;
                    if ((maxAudioTimestampUs - minVideoTimestampUs) <= 0) {
                        originTimestampUs = maxAudioTimestampUs; // audio before first video pts
                        marginUs = mAudioOutputPath.getMarginUs();
                        origin = String.format(Locale.US,
                                "max audio pts, margin:%d, delta(max-a, min-v):%d",
                                marginUs / 1000,
                                (maxAudioTimestampUs - minVideoTimestampUs) / 1000);
                    } else if ((maxVideoTimestampUs - minAutioTimestampUs) <= 0) {
                        originTimestampUs = maxVideoTimestampUs; // video before first audio pts
                        marginUs = mVideoOutputPath.getMarginUs();
                        origin = String.format(Locale.US,
                                "max video pts, margin:%d, delta(max-v, min-a):%d",
                                marginUs / 1000,
                                (maxVideoTimestampUs - minAutioTimestampUs) / 1000);
                    } else if (((minAutioTimestampUs - maxVideoTimestampUs) <= 0) &&
                            ((minAutioTimestampUs - minVideoTimestampUs) >= 0)) {
                        originTimestampUs = minAutioTimestampUs; // audio in video pts range
                        marginUs = mAudioOutputPath.getMarginUs();
                        origin = String.format(Locale.US,
                                "min audio pts, margin:%d, delta(min-a, max-v):%d",
                                marginUs / 1000,
                                (minAutioTimestampUs - maxVideoTimestampUs) / 1000);
                    } else if (((minVideoTimestampUs - maxAudioTimestampUs) <= 0) &&
                            ((minVideoTimestampUs - minAutioTimestampUs) >= 0)) {
                        originTimestampUs = minVideoTimestampUs; // video in audio pts range
                        marginUs = mVideoOutputPath.getMarginUs();
                        origin = String.format(Locale.US,
                                "min video pts, margin:%d, delta(min-v, max-a):%d",
                                marginUs / 1000,
                                (minVideoTimestampUs - maxAudioTimestampUs) / 1000);
                    } else { // should not happen
                        ASPlayerLog.w("%s unexpected origin case a[%d,%d] v[%d,%d]",
                                getTag(), minAutioTimestampUs / 1000, maxAudioTimestampUs / 1000,
                                minVideoTimestampUs / 1000, maxVideoTimestampUs / 1000);
                        origin = "no origin defined";
                    }

                    ASPlayerLog.i("%s because %s, must set clock origin to %s", getTag(), setClockReason, origin);
                    originTimestampUs = Math.max(originTimestampUs - marginUs, 0);

                    mAudioOutputPath.setSynchroOn(originTimestampUs);
                    mVideoOutputPath.setSynchroOn(originTimestampUs);
                }

                if (mSynchroMode != SYNCHRO_MODE_SYNCHRONIZABLE)
                    ASPlayerLog.i("%s av sync is on", getTag());
                mSynchroMode = SYNCHRO_MODE_SYNCHRONIZABLE;
            }
        }

        if (mSynchroMode == SYNCHRO_MODE_SYNCHRONIZABLE)
            monitorSynchro();
    }

    private void checkSynchroMode() {
        // if there is no audio and no video, don't try to synchronize
        if (!mAudioOutputPath.isConfigured() && !mVideoOutputPath.isConfigured()) {
            mSynchroMode = SYNCHRO_MODE_NONE;
            return;
        }

        // if speed is not 1, should be 0, there is no synchro
        if (mSpeed != 1) {
            return;
        }

        // no av synchronization in tunneled mode, but still audio/subtitles
        if (mTunneledPlayback) {
            checkSynchroInTunneledMode();
        } else {
            checkSynchroInStandardMode();
        }

        checkSynchroSubtitles();
    }

    @Override
    void setWorkMode(int workMode) {
    }
}
