package com.amlogic.asplayer.core;

import android.media.AudioTimestamp;
import android.media.AudioTrack;
import android.media.MediaFormat;
import android.os.SystemClock;

/**
 * Class to compute playback position
 * Here are the main methods that could give such information
 * - getTimestamp()
 * - getPlaybackHeadPosition()/getLatency
 * - setPositionNotificationPeriod() and setPlaybackPositionUpdateListener()
 * getTimestamp() must be the preferred solution as it is the only completely public and
 * taking into account the latency (getLatency is not public)
 * But getTimestamp() may be not available for some route.
 * A fallback using getPlaybackHeadPosition()/getLatency is necessary
 * <p>
 * The time returned by getTimestamp() is valid only when playing data: if AudioTrack is paused
 * time still runs (seen on TIMBOX/AndroidO).
 * <p>
 * None of these methods are 100% accurate.
 * Even though there are not accurate, we must be able to check if there is a drift.
 * <p>
 */

class AudioPositionTracker {

    private static final int DEFAULT_SAMPLE_RATE = 48000;
    private static final int DEFAULT_CHANNEL_COUNT = 2;

    private static final long DRIFT_MAX_US = 100000;
    private static final long DRIFT_TOLERANCE_US = 40000;
    private static final long DRIFT_ADJUSTMENT_US = 10000;

    // audio track info
    private long mSampleRate;
    private long mChannelCount;
    private AudioTrack mAudioTrack;
    private MediaFormat mAudioMediaFormat;

    // reference position in input stream
    // private long mReferenceInBytes;
    private long mReferenceInFrames;
    private long mReferenceInUs;
    private boolean mReferenceInUsSet;

    // input position to check drift
    // private long mCheckReferenceInBytes;
    private long mCheckReferenceInFrames;
    private long mCheckReferenceInUs;
    private long mCheckReferenceWhenMs;
    private boolean mCheckReferenceInUsSet;

    // current position tracker
    private Tracker mTracker;

    interface Tracker {
        void reset();

        boolean isBroken();

        void updatePosition();

        boolean isPositionValid();

        long getPositionUs();

        long getPositionAccuracyUs();

        void suspend();

        void resume();
    }

    class TrackerByTimestamp implements Tracker {

        private static final int STATE_NONE = 0;
        private static final int STATE_WAITING_FOR_REFERENCE = 1;
        private static final int STATE_WAITING_FOR_PLAY = 2;
        private static final int STATE_MONITORING = 3;

        // timings observed so far:
        // - in tunneled mode in bcm platforms, it takes about 500ms to get the first timestamp
        // - otherwise, it takes about 200ms
        private static final int TIMESTAMP_NO_REFERENCE_MAX_DURATION_MS = 1000;

        private static final int UPDATE_RATE_STATE_NONE_MS = 10;
        private static final int UPDATE_RATE_STATE_WAITING_FOR_REFERENCE_MS = 10;
        private static final int UPDATE_RATE_STATE_WAITING_FOR_PLAY_MS = 10;
        private static final int UPDATE_RATE_STATE_MONITORING_MS = 10000;
        private static final int UPDATE_RATE_STATE_MONITORING_ADJUSTING_DRIFT_MS = 100;

        private static final int ACCURACY_DURING_WAITING_FOR_PLAY_US = 100000;
        private static final int ACCURACY_DURING_MONITORING_US = 20000;

        private long mFirstPosition;
        private long mFirstPositionNanoTimeNs;

        private long mLastPositionRealTimeMs;

        private long mDisplayOffsetUs;
        private long mDriftAudioTrackUs;
        private long mDriftPositionUs;

        private int mState;
        private long mStateStartTimeMs;
        private long mLastUpdatePositionMs;
        private long mUpdatePositionRateMs;

        private long mTimestampPositionFrames;
        private long mTimestampPositionTimeNs;

        // maybe it is better to keep only a local variable
        private AudioTimestamp mAudioTimestamp;
        private long mTimestampRawPositionFrames;
        private long mTimestampWrapCount;

        private boolean mSuspended;

        TrackerByTimestamp() {
            mAudioTimestamp = new AudioTimestamp();
        }

        @Override
        public void resume() {
            reset();
            mSuspended = false;
        }

        @Override
        public void suspend() {
            mSuspended = true;
        }

        @Override
        public void reset() {
            mState = STATE_NONE;
            mStateStartTimeMs = 0;
            mLastUpdatePositionMs = 0;
            mUpdatePositionRateMs = UPDATE_RATE_STATE_WAITING_FOR_REFERENCE_MS;
            mFirstPosition = 0;
            mFirstPositionNanoTimeNs = 0;
            mLastPositionRealTimeMs = 0;
            mDisplayOffsetUs = 0;
            mDriftAudioTrackUs = 0;
            mDriftPositionUs = 0;
            mReferenceInUsSet = false;
            mCheckReferenceInUsSet = false;
            mSuspended = false;
            mTimestampPositionFrames = 0;
            mTimestampPositionTimeNs = 0;
            mTimestampRawPositionFrames = 0;
            mTimestampWrapCount = 0;
        }

        @Override
        public boolean isBroken() {
            boolean referenceState = (mState == STATE_WAITING_FOR_REFERENCE);
            long elapsedMs = (SystemClock.elapsedRealtime() - mStateStartTimeMs);

            if (referenceState && (elapsedMs > TIMESTAMP_NO_REFERENCE_MAX_DURATION_MS)) {
                ASPlayerLog.w("no more timestamp available, use playback head position algorithm (elapsed:%d)",
                        elapsedMs);
                return true;
            }
            return false;
        }

        @Override
        public boolean isPositionValid() {
            switch (mState) {
                case STATE_NONE:
                case STATE_WAITING_FOR_REFERENCE:
                    return false;
                default:
                    return true;

            }
        }

        @Override
        public long getPositionUs() {
            return mDisplayOffsetUs + mDriftAudioTrackUs + mDriftPositionUs +
                    (mTimestampPositionTimeNs - mFirstPositionNanoTimeNs) / 1000 +
                    (SystemClock.elapsedRealtime() - mLastPositionRealTimeMs) * 1000;
        }

        @Override
        public long getPositionAccuracyUs() {
            switch (mState) {
                case STATE_WAITING_FOR_PLAY:
                    return ACCURACY_DURING_WAITING_FOR_PLAY_US;
                case STATE_MONITORING:
                    return ACCURACY_DURING_MONITORING_US;
                case STATE_WAITING_FOR_REFERENCE:
                default:
                    return 0;
            }
        }

        @Override
        public void updatePosition() {
            if (!mReferenceInUsSet)
                return;

            if (!needUpdate())
                return;

            // check if we have no timestamp since a while
            if (isBroken())
                return;

            // no timestamp, go back to first state
            if (!updateTimestamp()) {
                updateState(STATE_WAITING_FOR_REFERENCE);
                return;
            }

            // handle state
            switch (mState) {
                case STATE_NONE:
                    updateState(STATE_WAITING_FOR_REFERENCE);
                    break;
                case STATE_WAITING_FOR_REFERENCE:
                    if (mTimestampPositionFrames > mReferenceInFrames) {
                        updateFirstPosition();
                        updateState(STATE_WAITING_FOR_PLAY);
                    }
                    break;
                case STATE_WAITING_FOR_PLAY:
                    if (mTimestampPositionFrames > mFirstPosition) {
                        updateFirstPosition();
                        updateState(STATE_MONITORING);
                    }
                    break;
                case STATE_MONITORING:
                    checkDrifts();
                    break;
            }
        }

        private void updateFirstPosition() {
            // update first valid position
            mFirstPosition = mTimestampPositionFrames;
            mFirstPositionNanoTimeNs = mTimestampPositionTimeNs;
            // compute offset position in time
            long deltaFirstPositionReferenceInFrames =
                    mTimestampPositionFrames - mReferenceInFrames;
            mDisplayOffsetUs =
                    mReferenceInUs + framesToUs(deltaFirstPositionReferenceInFrames);
        }

        private void checkDrifts() {
            //
            // check drift in audiotrack
            //
            long deltaPosInFrames = mTimestampPositionFrames - mFirstPosition;
            long deltaPosInUs = framesToUs(deltaPosInFrames);
            long deltaTime = (mTimestampPositionTimeNs - mFirstPositionNanoTimeNs) / 1000;
            long driftAudioTrackUs = (deltaTime - deltaPosInUs) + mDriftAudioTrackUs;
            if (Math.abs(driftAudioTrackUs) > DRIFT_MAX_US) {
                ASPlayerLog.i("drift in audiotrack:%d us, reset reference and tracker", driftAudioTrackUs);
                reset();
                return;
            } else if (Math.abs(driftAudioTrackUs) > DRIFT_TOLERANCE_US) {
                ASPlayerLog.i("drift in audiotrack:%d us, adjust offset (was:%d)",
                        driftAudioTrackUs, mDriftAudioTrackUs);
                mUpdatePositionRateMs = UPDATE_RATE_STATE_MONITORING_ADJUSTING_DRIFT_MS;
                if (driftAudioTrackUs > 0)
                    mDriftAudioTrackUs -= DRIFT_ADJUSTMENT_US;
                else
                    mDriftAudioTrackUs += DRIFT_ADJUSTMENT_US;
                return;
            }

            //
            // check drift of position
            //
            if (!mCheckReferenceInUsSet)
                return;
            if (mTimestampPositionFrames < mCheckReferenceInFrames)
                return;
            // compute position with same formula as for display position, without the elapsed
            // time adjustment
            long positionFromTimestampUs =
                    getPositionUs() -
                            (SystemClock.elapsedRealtime() - mLastPositionRealTimeMs) * 1000;
            // compute position with check reference time
            long deltaInFrames =
                    mTimestampPositionFrames - mCheckReferenceInFrames;
            long deltaInUs =
                    framesToUs(deltaInFrames);
            long positionWithCheckReferenceUs =
                    mCheckReferenceInUs + deltaInUs;
            long driftPositionUs = (positionFromTimestampUs - positionWithCheckReferenceUs) + mDriftPositionUs;
            if (Math.abs(driftPositionUs) > DRIFT_MAX_US) {
                ASPlayerLog.i("drift in position:%d us, reset reference and tracker", driftPositionUs);
                reset();
                return;
            } else if (Math.abs(driftPositionUs) > DRIFT_TOLERANCE_US) {
                ASPlayerLog.i("drift in position:%d us, adjust offset (was:%d)", driftPositionUs, mDriftPositionUs);
                mUpdatePositionRateMs = UPDATE_RATE_STATE_MONITORING_ADJUSTING_DRIFT_MS;
                if (driftPositionUs > 0)
                    mDriftPositionUs -= DRIFT_ADJUSTMENT_US;
                else
                    mDriftPositionUs += DRIFT_ADJUSTMENT_US;
                return;
            }

            mCheckReferenceInUsSet = false;
            mUpdatePositionRateMs = UPDATE_RATE_STATE_MONITORING_MS;
        }

        private boolean needUpdate() {
            if (mSuspended)
                return false;
            long nowMs = SystemClock.elapsedRealtime();
            if ((nowMs - mLastUpdatePositionMs) < mUpdatePositionRateMs)
                return false;
            mLastUpdatePositionMs = nowMs;
            return true;
        }

        private boolean updateTimestamp() {
            if (mAudioTrack.getTimestamp(mAudioTimestamp)) {
                if (mTimestampRawPositionFrames > mAudioTimestamp.framePosition) {
                    mTimestampWrapCount++;
                }
                mTimestampRawPositionFrames = mAudioTimestamp.framePosition;
                mTimestampPositionFrames =
                        (mTimestampWrapCount << 32) | mAudioTimestamp.framePosition;
                mTimestampPositionTimeNs =
                        mAudioTimestamp.nanoTime;

                mLastPositionRealTimeMs = SystemClock.elapsedRealtime();
                return true;
            } else {
                return false;
            }
        }

        private void updateState(int state) {
            if (state == mState)
                return;

            switch (state) {
                case STATE_NONE:
                    mUpdatePositionRateMs = UPDATE_RATE_STATE_NONE_MS;
                    break;
                case STATE_WAITING_FOR_REFERENCE:
                    mUpdatePositionRateMs = UPDATE_RATE_STATE_WAITING_FOR_REFERENCE_MS;
                    break;
                case STATE_WAITING_FOR_PLAY:
                    mUpdatePositionRateMs = UPDATE_RATE_STATE_WAITING_FOR_PLAY_MS;
                    break;
                case STATE_MONITORING:
                    mUpdatePositionRateMs = UPDATE_RATE_STATE_MONITORING_MS;
                    break;
            }
            mState = state;
            mStateStartTimeMs = SystemClock.elapsedRealtime();
            mLastUpdatePositionMs = SystemClock.elapsedRealtime();
        }
    }

    class TrackerByHeadPosition implements Tracker {

        private static final int ACCURACY_HEAD_POSITION_US = 20000;

        private long mLastHeadPosition;
        private long mHeadPositionWrapCount;
        private long mLatencyUs;

        private long mPlaybackPositionUs;
        private long mPlaybackPositionWhenMs;

        private long mDriftPositionUs;

        TrackerByHeadPosition() {
        }

        @Override
        public void suspend() {
        }

        @Override
        public void resume() {
            reset();
        }

        @Override
        public void reset() {
            mPlaybackPositionUs = 0;
            mPlaybackPositionWhenMs = 0;
            mDriftPositionUs = 0;
        }

        @Override
        public boolean isBroken() {
            return false;
        }

        @Override
        public void updatePosition() {
            if (!mReferenceInUsSet)
                return;

            long positionInFrames = getAudioTrackPositionInFrames();

            long deltaPositionInFrames = (positionInFrames - mReferenceInFrames);
            long deltaPositionInUs = deltaPositionInFrames * 1000000L / mSampleRate;
            long playbackPositionUs = deltaPositionInUs + mReferenceInUs;

            mPlaybackPositionUs = Math.max(playbackPositionUs - mLatencyUs, 0);
            mPlaybackPositionWhenMs = SystemClock.elapsedRealtime();

            //
            // check drift of position
            //
            if (!mCheckReferenceInUsSet)
                return;
            if (positionInFrames < mCheckReferenceInFrames)
                return;
            // compute position with check reference
            long positionWithCheckReferenceUs =
                    mCheckReferenceInUs + framesToUs(positionInFrames - mCheckReferenceInFrames);
            long driftPositionUs = (playbackPositionUs - positionWithCheckReferenceUs);
            if (Math.abs(driftPositionUs) > DRIFT_MAX_US) {
                ASPlayerLog.i("drift in position:%d us, reset reference and tracker", driftPositionUs);
                reset();
            } else if (Math.abs(driftPositionUs) > DRIFT_TOLERANCE_US) {
                ASPlayerLog.i("drift in position:%d us, adjust offset (was:%d)", driftPositionUs, mDriftPositionUs);
                if (driftPositionUs > 0)
                    mDriftPositionUs -= DRIFT_ADJUSTMENT_US;
                else
                    mDriftPositionUs += DRIFT_ADJUSTMENT_US;
            }
            mCheckReferenceInUsSet = false;
        }

        @Override
        public boolean isPositionValid() {
            return mPlaybackPositionWhenMs > 0;
        }

        @Override
        public long getPositionUs() {
            return mPlaybackPositionUs + mDriftPositionUs +
                    (SystemClock.elapsedRealtime() - mPlaybackPositionWhenMs) * 1000;
        }

        @Override
        public long getPositionAccuracyUs() {
            return ACCURACY_HEAD_POSITION_US;
        }

        void updateLatency(AudioTrack audioTrack, MediaFormat format) {
            mLatencyUs = AudioUtils.getEstimatedLatencyUs(audioTrack, format);
        }

        private long getAudioTrackPositionInFrames() {
            long positionInFrames = 0xFFFFFFFFL & mAudioTrack.getPlaybackHeadPosition();
            if (positionInFrames < mLastHeadPosition) {
                ASPlayerLog.i("wrap around, last:%d next:%d, wrap_count:%d",
                        mLastHeadPosition, positionInFrames, mHeadPositionWrapCount);
                mHeadPositionWrapCount++;
            }
            positionInFrames = (mHeadPositionWrapCount << 32) | positionInFrames;
            mLastHeadPosition = positionInFrames;

            return positionInFrames;
        }
    }

    AudioPositionTracker() {
    }

    void setAudioTrack(AudioTrack audioTrack, MediaFormat format) {
        mReferenceInUsSet = false;

        mSampleRate = DEFAULT_SAMPLE_RATE;
        mChannelCount = DEFAULT_CHANNEL_COUNT;

        if (audioTrack != null) {
            // mSampleRate = audioTrack.getSampleRate();
            // mChannelCount = audioTrack.getChannelCount();
            mSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            mChannelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        }
        mAudioTrack = audioTrack;
        mAudioMediaFormat = format;

        mTracker = new TrackerByTimestamp();
    }

    private long framesToUs(long frames) {
        return frames * 1000000L / mSampleRate;
    }

    private long bytesToFrames(long positionInBytes) {
        return positionInBytes / mChannelCount / 2;
    }

    void updateReferenceInFrames(long positionInFrames, long timestampUs) {
        if (!mReferenceInUsSet) {
            mReferenceInUsSet = true;
            mReferenceInFrames = positionInFrames;
            mReferenceInUs = timestampUs;
        } else if (!mCheckReferenceInUsSet) {
            if (positionInFrames == mReferenceInFrames ||
                    timestampUs == mReferenceInUs)
                return;

            // check if there is a drift in the source
            long deltaPosInFrames =
                    positionInFrames - mReferenceInFrames;
            long deltaPosInUs =
                    framesToUs(deltaPosInFrames);
            long deltaTime = timestampUs - mReferenceInUs;

            if (Math.abs(deltaTime - deltaPosInUs) > DRIFT_MAX_US) {
                ASPlayerLog.i("drift in source (%d us), reset reference and tracker",
                        deltaTime - deltaPosInUs);
                // if there is a drift, need to resynchronize
                mTracker.reset();
                mReferenceInUsSet = true;
                mReferenceInFrames = positionInFrames;
                mReferenceInUs = timestampUs;
            } else {
                mCheckReferenceInUsSet = true;
                mCheckReferenceInFrames = positionInFrames;
                mCheckReferenceInUs = timestampUs;
                mCheckReferenceWhenMs = SystemClock.elapsedRealtime();
            }
        }
    }

    void updateReferenceInBytes(long positionInBytes, long timestampUs) {
        updateReferenceInFrames(bytesToFrames(positionInBytes), timestampUs);
    }

    void updatePosition() {
        if (mTracker.isBroken()) {
            TrackerByHeadPosition tracker = new TrackerByHeadPosition();
            tracker.updateLatency(mAudioTrack, mAudioMediaFormat);
            mTracker = tracker;
        }

        mTracker.updatePosition();
    }

    boolean isPositionValid() {
        return (mTracker != null && mTracker.isPositionValid());
    }

    long getPositionAccuracyUs() {
        return mTracker.getPositionAccuracyUs();
    }

    long getPositionUs() {
        return mTracker.getPositionUs();
    }

    void suspend() {
        mTracker.suspend();
    }

    void resume() {
        mTracker.resume();
    }

    void stop() {
        if (!(mTracker instanceof TrackerByTimestamp))
            mTracker = new TrackerByTimestamp();
        else
            mTracker.reset();
    }
}
