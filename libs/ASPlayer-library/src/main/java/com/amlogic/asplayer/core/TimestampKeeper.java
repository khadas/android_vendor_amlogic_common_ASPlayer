package com.amlogic.asplayer.core;

import java.util.LinkedList;

class TimestampKeeper {

    private static final int DEFAULT_DISCONTINUITY_THRESHOLD_US = 500000; // 500 ms
    private static final int DEFAULT_INPUT_BUFFER_DELTA_US = 40000; // 40 ms

    class Timestamp {
        long generation;
        long timestampUs;
    }

    private String mTrackType;

    // max delta for pts before discontinuity
    private long mDiscontinuityThresholdUs;
    // average expected timestamp delta between two consecutive input buffers (in ms)
    private long mInputBufferExpectedDeltaUs;

    // input buffer info
    private int mGeneration;
    private final LinkedList<Timestamp> mTimestamps;

    // discontinuity indicator
    private boolean mHasDiscontinuity;

    TimestampKeeper(String trackType) {
        mTimestamps = new LinkedList<>();
        mDiscontinuityThresholdUs = DEFAULT_DISCONTINUITY_THRESHOLD_US;
        mInputBufferExpectedDeltaUs = DEFAULT_INPUT_BUFFER_DELTA_US;
        mTrackType = trackType;
    }

    void setDiscontinuityThresholdUs(long discontinuityThresholdUs) {
        mDiscontinuityThresholdUs = discontinuityThresholdUs;
    }

    void setInputBufferExpectedDeltaUs(long deltaUs) {
        mInputBufferExpectedDeltaUs = deltaUs;
    }

    void clear() {
        mTimestamps.clear();
        mGeneration = 0;
        mHasDiscontinuity = false;
    }

    boolean isEmpty() {
        return mTimestamps.isEmpty();
    }

    void pushTimestamp(long timestampUs) {
        if (!mTimestamps.isEmpty() && !mHasDiscontinuity) {
            long lastTimestampUs = mTimestamps.getLast().timestampUs;
            long deltaUs = timestampUs - lastTimestampUs;
            mHasDiscontinuity = (Math.abs(deltaUs) > mDiscontinuityThresholdUs);
            if (mHasDiscontinuity) {
                ASPlayerLog.w("%s DISCONTINUITY DETECTED last:%d, timestamp:%d, delta:%d vs %d",
                        mTrackType,
                        lastTimestampUs / 1000,
                        timestampUs / 1000,
                        deltaUs / 1000,
                        mDiscontinuityThresholdUs / 1000);
            }
        }
        Timestamp timestamp = new Timestamp();
        timestamp.timestampUs = timestampUs;
        timestamp.generation = mGeneration++;
        mTimestamps.addLast(timestamp);
    }

    private void checkIntegrity() {
        for (int i = 0; i < mTimestamps.size(); ++i) {
            if ((mGeneration - mTimestamps.get(i).generation) > 100) {
                ASPlayerLog.w("Timestamps size:%d", mTimestamps.size());
                long lastTimestamp = mTimestamps.get(0).timestampUs;
                for (int j = 0; j < mTimestamps.size(); ++j) {
                    Timestamp timestamp = mTimestamps.get(j);
                    long deltaUs = timestamp.timestampUs - lastTimestamp;
                    ASPlayerLog.w(" %d -> gen:%d, delta:%d", j, timestamp.generation, deltaUs / 1000);
                }

                throw new IllegalStateException("bad timestamp list");
                // break;
            }
        }
    }

    void removeTimestamp(long timestampUs) {
        if (!mHasDiscontinuity) {
            if (isTimestampOutOfRange(timestampUs) && !mTimestamps.isEmpty()) {
                ASPlayerLog.w("%s timestamp %d is unexpected (not in timerange): size:%d [%d,%d]",
                        mTrackType,
                        timestampUs,
                        mTimestamps.size(),
                        mTimestamps.isEmpty() ? -1 : mTimestamps.peekFirst().timestampUs,
                        mTimestamps.isEmpty() ? -1 : mTimestamps.peekLast().timestampUs);
                return;
            }
        }

        int lastIndexToRemove = -1;
        for (int i = 0; i < mTimestamps.size() && i < 20; ++i) {
            Timestamp timestamp = mTimestamps.get(i);
            long deltaUs = timestamp.timestampUs - timestampUs;
            if (deltaUs <= 0 && Math.abs(deltaUs) < 1000000) {
                lastIndexToRemove = i;
            }
        }

        for (; lastIndexToRemove >= 0; lastIndexToRemove--) {
            mTimestamps.remove(0);
        }

        if (mTimestamps.size() <= 1)
            mHasDiscontinuity = false;

        if (mHasDiscontinuity) {
            long previousTimestamp = mTimestamps.peekFirst().timestampUs;
            boolean discontinuity = false;
            for (Timestamp timestamp : mTimestamps) {
                long deltaUs = timestamp.timestampUs - previousTimestamp;
                if (Math.abs(deltaUs) >= mDiscontinuityThresholdUs) {
                    discontinuity = true;
                    break;
                }
                previousTimestamp = timestamp.timestampUs;
            }
            if (!discontinuity)
                ASPlayerLog.i("no more discontinuity in %s", mTrackType);
            mHasDiscontinuity = discontinuity;
        }

        // checkIntegrity();
    }

    long getSizeInUs() {
        if (mHasDiscontinuity) {
            return mTimestamps.size() * mInputBufferExpectedDeltaUs;
        } else if (mTimestamps.size() <= 1) {
            return 0;
        } else {
            long firstTimestampUs = mTimestamps.peekFirst().timestampUs;
            long lastTimestampUs = mTimestamps.peekLast().timestampUs;
            return (lastTimestampUs - firstTimestampUs);
        }
    }

    int getSize() {
        return mTimestamps.size();
    }

    boolean hasDiscontinuity() {
        return mHasDiscontinuity;
    }

    // should be called only when there is no discontinuity
    boolean isTimestampOutOfRange(long timestampUs) {
        if (mTimestamps.isEmpty())
            return true;
        long deltaPtsFirst = (timestampUs - mTimestamps.peekFirst().timestampUs);
        if (deltaPtsFirst < -mDiscontinuityThresholdUs)
            return true;
        long deltaPtsLast = (timestampUs - mTimestamps.peekLast().timestampUs);
        if (deltaPtsLast > mDiscontinuityThresholdUs)
            return true;

        return false;
    }

    long getLastOutputTimestamp() {
        if (mTimestamps.isEmpty())
            return -1;
        return mTimestamps.peekLast().timestampUs;
    }
}
