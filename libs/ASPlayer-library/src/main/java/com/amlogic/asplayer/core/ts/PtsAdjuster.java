package com.amlogic.asplayer.core.ts;


import com.amlogic.asplayer.core.ASPlayerLog;
import com.amlogic.asplayer.core.sipsi.mpeg.Mpeg;
import com.amlogic.asplayer.core.source.DataSource;

/**
 * PtsAdjuster is not thread safe.
 * - pushPcr is called in Extractor thread
 * - reset, hasReference, getReferencePosition, getReferenceTimeUs, discontinuityDetected,
 *   adjust are called in Player thread
 * But that's ok because synchronization is performed at TsMediaContainerExtractor level.
 */
class PtsAdjuster {
    private int mNumberOfPcrLoops;
    private TimelinePcr mReferencePcr;

    private long mOriginPositionInUs;
    private long mOriginTimestampInUs;

    private int mNbPcrDiscontinuities;
    private boolean mDiscontinuityDetected;

    /**
     * Computes delta = (timestamp1-timestamp2)
     * Takes into account that timestamp can wrap around when max value is reached
     *
     * @param timestamp1 first operand, must be positive and lower then Mpeg.PTS_MAX_VALUE_IN_US
     * @param timestamp2 second operand, must be positive and lower then Mpeg.PTS_MAX_VALUE_IN_US
     * @return difference between timestamp1 and timestamp2
     */
    static long deltaTimestamp(long timestamp1, long timestamp2) {
        long delta1 = timestamp1 - timestamp2;
        long delta2 = timestamp1 + Mpeg.PTS_MAX_VALUE_IN_US - timestamp2;
        if (Math.abs(delta1) < Math.abs(delta2))
            return delta1;
        else
            return delta2;
    }

    /**
     * Computes timestamp-addition
     * Takes into account that result must wrap around if addition is greater than timestamp
     *
     * @param timestamp first operand, must be positive and lower then Mpeg.PTS_MAX_VALUE_IN_US
     * @param addition  second operand, must be positive and lower then Mpeg.PTS_MAX_VALUE_IN_US
     * @return sum of timestamp and addition, always positive and lesser than
     * Mpeg.PTS_MAX_VALUE_IN_US
     */
    static long plus(long timestamp, long addition) {
        return (timestamp + addition) % Mpeg.PTS_MAX_VALUE_IN_US;
    }

    PtsAdjuster() {
        mReferencePcr = new TimelinePcr();
    }

    void reset() {
        mReferencePcr.reset();
        mOriginPositionInUs = -1;
        mOriginTimestampInUs = -1;
        mNbPcrDiscontinuities = 0;
        mDiscontinuityDetected = false;
        mNumberOfPcrLoops = 0;
    }

    boolean hasReference() {
        return mReferencePcr.isValid();
    }

    long getReferencePosition() {
        return mReferencePcr.position;
    }

    long getReferenceTimeUs() {
        return (mReferencePcr.timeUs - mOriginTimestampInUs) + mOriginPositionInUs;
    }

    boolean discontinuityDetected() {
        return mDiscontinuityDetected;
    }

    void pushPcr(DataSource source, long position, long pcr) {
        mDiscontinuityDetected = false;
        if (!mReferencePcr.isValid()) {
            mReferencePcr.position = position;
            mReferencePcr.timeUs = Mpeg.pcrToUs(pcr);
            mOriginPositionInUs = source.getPositionInUs(position);
            mOriginTimestampInUs = mReferencePcr.timeUs;
        } else {
            long pcrInUs = Mpeg.pcrToUs(pcr) + mNumberOfPcrLoops * Mpeg.PTS_MAX_VALUE_IN_US;

            boolean possibleLoop = false;
            while ((pcrInUs - mReferencePcr.timeUs) < 0) {
                pcrInUs += Mpeg.PTS_MAX_VALUE_IN_US;
                possibleLoop = true;
            }

            long deltaUs = pcrInUs - mReferencePcr.timeUs;
            if ((deltaUs > Mpeg.PCR_MAX_DELTA_US)) {
                mNbPcrDiscontinuities++;
                ASPlayerLog.i("potential pcr discontinuity detected at position %d, pcr:[adj:%d, loop:%d, %d us], last:%d us, delta:%d us, nb pcrs:%d",
                        position,
                        pcrInUs / 1000, mNumberOfPcrLoops, Mpeg.pcrToUs(pcr) / 1000,
                        mReferencePcr.timeUs / 1000, deltaUs / 1000,
                        mNbPcrDiscontinuities);
                if (mNbPcrDiscontinuities > 2) {
                    ASPlayerLog.i("discontinuity confirmed, set flag");
                    mDiscontinuityDetected = true;
                    mNbPcrDiscontinuities = 0;
                    mReferencePcr.position = position;
                    mReferencePcr.timeUs = pcrInUs;
                    if (possibleLoop)
                        mNumberOfPcrLoops++;
                }
            } else {
                mNbPcrDiscontinuities = 0;
                mReferencePcr.position = position;
                mReferencePcr.timeUs = pcrInUs;
                if (possibleLoop)
                    mNumberOfPcrLoops++;
            }
        }
    }

    long adjust(long timestampUs) {
        // if there is no pcr, we can't adjust
        if (!mReferencePcr.isValid())
            return timestampUs;

        // readjust according origin timestampUs and pcr number of loops
        long adjustedTimestampUs =
                (timestampUs + mNumberOfPcrLoops * Mpeg.PTS_MAX_VALUE_IN_US) - mOriginTimestampInUs;
        // add origin offset.
        return adjustedTimestampUs + mOriginPositionInUs;
    }
}
