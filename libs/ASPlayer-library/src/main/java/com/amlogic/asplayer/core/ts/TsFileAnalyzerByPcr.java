package com.amlogic.asplayer.core.ts;

import android.os.SystemClock;


import com.amlogic.asplayer.core.ASPlayerLog;
import com.amlogic.asplayer.core.sipsi.mpeg.Mpeg;
import com.amlogic.asplayer.core.sipsi.mpeg.TsPacket;

import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Locale;

class TsFileAnalyzerByPcr extends TsFileAnalyzer {

    static class TsAnalyzerException extends Exception {
        TsAnalyzerException(String reason, String where) {
            super(String.format("%s: %s", where, reason));
        }
    }

    private static final int OPERATION_SEARCH_POSITION_TIMEOUT_MS = 500;
    private static final int OPERATION_GENERAL_INFO_TIMEOUT_MS = 500;
    private static final int OPERATION_SEARCH_DISCONTINUITIES_TIMEOUT_MS = 2000;
    private static final int OPERATION_SEARCH_PCR_TIMEOUT_MS = 200;

    // This is the number of ts packets to read to be quite sure to get at least one pcr
    // (a pcr should be provided at least every 100ms)
    // For a 25Mb/s streams => we should read ~1663 packets
    // For a 8Mb/s streams => we should read ~532 packets
    // For a 1Mb/s streams => we should read ~67 packets
    // Maybe we should adapt the number of packets to read with the bitrate
    // If we read too much, we spend too much time to read useless data
    // If we don't read enough, we call read too many times
    private static final int NB_PACKETS_BY_READ = 1000;

    // general info
    private int mEstimatedByteRate;
    private int mPcrPid;
    private TimelinePcr mPcrStart;
    private TimelinePcr mPcrEnd;
    private ArrayList<TimelineChunk> mChunks;

    // search working attributes
    private long mOperationStartTimeMs;
    private long mOperationTimeoutMs;
    private TsPacket mPacket;
    private byte[] mReadBytes;

    TsFileAnalyzerByPcr() {
        mPcrStart = new TimelinePcr();
        mPcrEnd = new TimelinePcr();
        mPacket = new TsPacket();
        mReadBytes = new byte[NB_PACKETS_BY_READ * Mpeg.TS_PACKET_SIZE];
        mChunks = new ArrayList<>();
    }

    @Override
    void reset(RandomAccessFile file) {
        super.reset(file);
        mChunks.clear();
        mPcrStart.reset();
        mPcrEnd.reset();
        mPcrPid = 0;
        mEstimatedByteRate = 0;
    }

    @Override
    long getPositionInBytes(long timestampUs) {
        TimelineChunk chunk = findChunkWithTimestamp(timestampUs);
        if (chunk == null)
            return -1;
        long position = -1;
        try {
            position = getPositionInBytesAt(chunk.startPcr, chunk.endPcr,
                    Math.max(timestampUs - chunk.startTimeUs, 0));
        } catch (TsAnalyzerException exception) {
            ASPlayerLog.w("can't get position  %s, %s", this, exception.getMessage());
        }

        return position;
    }

    @Override
    long getPositionInUs(long position) {
        TimelineChunk chunk = findChunkWithPosition(position);
        try {
            return getPositionUsAt(chunk, position);
        } catch (TsAnalyzerException exception) {
            ASPlayerLog.w("can't get position %s, %s", this, exception.getMessage());
        }

        return -1;
    }

    @Override
    long getStartPositionInUs() {
        return 0;
    }

    @Override
    long getStartPositionInBytes() {
        return 0;
    }

    @Override
    long getEndPositionInUs() {
        if (mChunks == null || mChunks.isEmpty()) {
            return 0;
        }
        TimelineChunk lastChunk = mChunks.get(mChunks.size() - 1);
        return lastChunk.durationUs + lastChunk.startTimeUs;
    }

    @Override
    long getEndPositionInBytes() {
        if (mChunks == null || mChunks.isEmpty())
            return 0;
        TimelineChunk lastChunk = mChunks.get(mChunks.size() - 1);
        return lastChunk.endPcr.position;
    }

    /**
     * collect general info:
     * - estimated byte rate
     * - pcr pid
     * - first pcr, last pcr
     * - chunks (list of discontinuities)
     */
    @Override
    boolean updateTimeline() {

        boolean timelineUpdated;
        try {
            initTimeout(OPERATION_GENERAL_INFO_TIMEOUT_MS);

            // first pcr
            findNextPcr(0, mPcrStart);

            // estimated byte rate
            long firstPcrTimeStampUs = mPcrStart.timeUs;
            long firstPcrPosition = mPcrStart.position;
            long currentPcrTimeStampUs = firstPcrTimeStampUs;
            long currentPcrPosition = firstPcrPosition;
            long deltaPcrUs;
            TimelinePcr pcr = new TimelinePcr();
            do {
                checkTimeout("updateTimeline, estimate byte rate");

                // get pcr
                findNextPcr(currentPcrPosition + Mpeg.TS_PACKET_SIZE, pcr);

                // compute delta between two last pcrs
                deltaPcrUs = PtsAdjuster.deltaTimestamp(pcr.timeUs, currentPcrTimeStampUs);
                if (deltaPcrUs <= 0 || deltaPcrUs >= Mpeg.PCR_MAX_DELTA_US) {
                    ASPlayerLog.i("pcr jump (%d us) at position %d, reset search",
                            deltaPcrUs, pcr.position);
                    firstPcrTimeStampUs = pcr.timeUs;
                    firstPcrPosition = pcr.position;
                }
                currentPcrTimeStampUs = pcr.timeUs;
                currentPcrPosition = pcr.position;
                deltaPcrUs = PtsAdjuster.deltaTimestamp(currentPcrTimeStampUs, firstPcrTimeStampUs);
            }
            while (deltaPcrUs < 1000000);

            mEstimatedByteRate =
                    (int) ((currentPcrPosition - firstPcrPosition) * Mpeg.MICROS_PER_SECOND / deltaPcrUs);

            // look for last pcr
            long lastPosition = getLengthInBytes();
            findPreviousPcr(lastPosition, mPcrEnd);

            searchDiscontinuities(mChunks);

            timelineUpdated = true;

            dumpChunks();
        } catch (TsAnalyzerException exception) {
            ASPlayerLog.i("can't analyze source %s, %s", this, exception.getMessage());
            timelineUpdated = false;
        }

        return timelineUpdated;
    }

    private void searchDiscontinuities(ArrayList<TimelineChunk> chunks) throws TsAnalyzerException {
        ASPlayerLog.i("looking for pcr discontinuities");

        initTimeout(OPERATION_SEARCH_DISCONTINUITIES_TIMEOUT_MS);

        // store getPositionInBytes of source
        long oldPosition = getPositionInBytes();

        //
        TimelinePcr lastPcrBeforeDiscontinuity = new TimelinePcr();
        TimelinePcr firstPcrAfterDiscontinuity = new TimelinePcr();
        TimelinePcr chunkStart = new TimelinePcr();
        TimelinePcr segmentStart = new TimelinePcr();
        TimelinePcr segmentEnd = new TimelinePcr();
        chunkStart.set(mPcrStart);
        segmentStart.set(mPcrStart);
        long currentChunkSizeSec = 30;
        long currentChunkStartTimeUs = 0;
        do {
            checkTimeout("searchDiscontinuities");

            computeEndPcr(segmentStart, currentChunkSizeSec, segmentEnd);
            boolean discontinuityDetected =
                    detectDiscontinuity(segmentStart, segmentEnd, true);
            if (discontinuityDetected) {
                lastPcrBeforeDiscontinuity.set(segmentStart);
                firstPcrAfterDiscontinuity.set(segmentEnd);
                boolean discontinuityFound =
                        searchDiscontinuity(lastPcrBeforeDiscontinuity, firstPcrAfterDiscontinuity,
                                currentChunkSizeSec);
                if (!discontinuityFound) {
                    segmentStart.set(segmentEnd);
                } else {
                    // and chunk
                    TimelineChunk chunk = new TimelineChunk();
                    chunk.startPcr.set(chunkStart);
                    chunk.endPcr.set(lastPcrBeforeDiscontinuity);
                    chunk.durationUs = PtsAdjuster.deltaTimestamp(lastPcrBeforeDiscontinuity.timeUs, chunkStart.timeUs);
                    chunk.startTimeUs = currentChunkStartTimeUs;
                    chunks.add(chunk);

                    // updateTimeline next
                    segmentStart.set(firstPcrAfterDiscontinuity);
                    chunkStart.set(firstPcrAfterDiscontinuity);
                    currentChunkStartTimeUs += chunk.durationUs;
                }
            } else {
                segmentStart.set(segmentEnd);
            }
        }
        while (segmentStart.position < mPcrEnd.position);

        // add last
        TimelineChunk chunk = new TimelineChunk();
        chunk.startPcr.set(chunkStart);
        chunk.endPcr.set(mPcrEnd);
        chunk.durationUs = PtsAdjuster.deltaTimestamp(mPcrEnd.timeUs, chunkStart.timeUs);
        chunk.startTimeUs = currentChunkStartTimeUs;
        chunks.add(chunk);

        setPositionInBytes(oldPosition);
    }

    private long getPositionInBytesAt(TimelinePcr startPcr, TimelinePcr endPcr, long deltaInChunkUs) throws TsAnalyzerException {

        initTimeout(OPERATION_SEARCH_POSITION_TIMEOUT_MS);

        long positionInBytes;
        TimelinePcr firstPcr = new TimelinePcr();
        firstPcr.set(startPcr);
        TimelinePcr lastPcr = new TimelinePcr();
        lastPcr.set(endPcr);
        long deltaPosUs;
        TimelinePcr pcr = new TimelinePcr();
        long positionUsInChunck = PtsAdjuster.plus(firstPcr.timeUs, deltaInChunkUs);
        try {
            do {
                double ratioTime =
                        (double) PtsAdjuster.deltaTimestamp(positionUsInChunck, firstPcr.timeUs) /
                                (double) PtsAdjuster.deltaTimestamp(lastPcr.timeUs, firstPcr.timeUs);
                positionInBytes =
                        (long) ((lastPcr.position - firstPcr.position) * ratioTime) + firstPcr.position;
                findNextPcr(positionInBytes, pcr);

                deltaPosUs = PtsAdjuster.deltaTimestamp(pcr.timeUs, positionUsInChunck);

                if (deltaPosUs < 0)
                    firstPcr.set(pcr);
                else
                    lastPcr.set(pcr);
            }
            while (Math.abs(deltaPosUs) > 500000);
        } catch (TsAnalyzerException exception) {
            ASPlayerLog.w("exception, chunk[%d,%d %d,%d], local_pos:%dms, search[pos:%d,%d  %d,%d - %d,%d]",
                    startPcr.position, startPcr.timeUs / 1000,
                    endPcr.position, endPcr.timeUs / 1000,
                    deltaInChunkUs / 1000,
                    pcr.position, pcr.timeUs / 1000,
                    firstPcr.position, firstPcr.timeUs / 1000,
                    lastPcr.position, lastPcr.timeUs / 1000);
            throw exception;
        }

        return positionInBytes;
    }

    private long getPositionUsAt(TimelineChunk chunk, long position) throws TsAnalyzerException {
        if (chunk == null)
            return -1;
        initTimeout(OPERATION_SEARCH_PCR_TIMEOUT_MS);
        TimelinePcr pcr = new TimelinePcr();
        if (position >= chunk.endPcr.position)
            pcr.set(chunk.endPcr);
        else if (position <= chunk.startPcr.position)
            pcr.set(chunk.startPcr);
        else
            findNextPcr(position, pcr);

        return PtsAdjuster.deltaTimestamp(pcr.timeUs, chunk.startPcr.timeUs) + chunk.startTimeUs;
    }

    private void findNextPcr(long offset, TimelinePcr pcr) throws TsAnalyzerException {
        findPcr(offset, pcr, 1);
    }

    private void findPreviousPcr(long offset, TimelinePcr pcr) throws TsAnalyzerException {
        findPcr(offset, pcr, -1);
    }

    private void initTimeout(long timeout) {
        mOperationStartTimeMs = SystemClock.elapsedRealtime();
        mOperationTimeoutMs = timeout;
    }

    private boolean searchDiscontinuity(TimelinePcr lastPcrBeforeDiscontinuity,
                                        TimelinePcr firstPcrAfterDiscontinuity,
                                        long deltaSec) throws TsAnalyzerException {
        boolean discontinuityDetected;

        TimelinePcr pcr = new TimelinePcr();
        while (deltaSec > 1) {
            checkTimeout("search discontinuity");

            // compute intermediate pcr
            deltaSec = (deltaSec + 1) / 2;

            pcr.position =
                    (lastPcrBeforeDiscontinuity.position + firstPcrAfterDiscontinuity.position) / 2;
            findNextPcr(pcr.position, pcr);

            // discontinuity in which segment ?
            discontinuityDetected = detectDiscontinuity(lastPcrBeforeDiscontinuity, pcr, false);
            if (discontinuityDetected) {
                // first
                firstPcrAfterDiscontinuity.set(pcr);
            } else {
                // second
                lastPcrBeforeDiscontinuity.set(pcr);
                // double check
                discontinuityDetected = detectDiscontinuity(lastPcrBeforeDiscontinuity, pcr, false);
                if (!discontinuityDetected)
                    throw new TsAnalyzerException("no more discontinuity detected", "searchDiscontinuity");
            }
        }

        return true;
    }

    private void computeEndPcr(TimelinePcr pcrOrigin, long deltaSec, TimelinePcr pcr) throws TsAnalyzerException {
        long estimatedLastPcrPosition =
                Math.min(deltaSec * mEstimatedByteRate + pcrOrigin.position, mPcrEnd.position + Mpeg.TS_PACKET_SIZE);
        findPreviousPcr(estimatedLastPcrPosition, pcr);
    }

    private boolean detectDiscontinuity(TimelinePcr firstPcr, TimelinePcr lastPcr, boolean dump) {
        long deltaTimeLastFirstUs =
                PtsAdjuster.deltaTimestamp(lastPcr.timeUs, firstPcr.timeUs);
        long deltaPosLastFirst = lastPcr.position - firstPcr.position;
        long byteRate = deltaTimeLastFirstUs != 0 ? deltaPosLastFirst * 1000000 / Math.abs(deltaTimeLastFirstUs) : 0;
        double ratioByteRates = (double) byteRate / (double) mEstimatedByteRate;
        if (deltaTimeLastFirstUs <= 0 || ratioByteRates > 10. || ratioByteRates < 0.1) {
            if (dump) {
                ASPlayerLog.i("pos[%d, %d] deltaUs:%d, byteRate:%d (vs %d)",
                        firstPcr.position, lastPcr.position,
                        deltaTimeLastFirstUs, byteRate, mEstimatedByteRate);
            }
            return true;
        } else {
            return false;
        }
    }

    private void findPcr(long offset, TimelinePcr pcr, int direction) throws TsAnalyzerException {

        long to = SystemClock.elapsedRealtime();
        long oldPosition = getPositionInBytes();

        offset = align(offset);
        pcr.reset();

        long position = offset;
        long length = getLengthInBytes();
        do {
            if (timeoutReached()) {
                String reason = String.format(Locale.US,
                        "timeout reached(%d vs %d), in method:%d: offset:%d, position:%d length:%d, direction:%d",
                        SystemClock.elapsedRealtime() - mOperationStartTimeMs,
                        mOperationTimeoutMs,
                        (SystemClock.elapsedRealtime() - to),
                        offset, position, length, direction);
                setPositionInBytes(oldPosition);
                throw new TsAnalyzerException(reason, "findPcr");
            }

            if (direction < 0)
                position -= mReadBytes.length;
            if (position < 0)
                position = 0;
            setPositionInBytes(position);
            int nbRead = read(mReadBytes);
            if (nbRead <= 0)
                break;

            int nbPackets = mReadBytes.length / Mpeg.TS_PACKET_SIZE;
            for (int i = 0; i < nbPackets; i++) {
                int packetPosition;
                if (direction > 0)
                    packetPosition = i;
                else
                    packetPosition = nbPackets - 1 - i;

                mPacket.wrap(mReadBytes, packetPosition * Mpeg.TS_PACKET_SIZE);
                if (!mPacket.isValid() || !mPacket.hasPcr())
                    continue;
                if (mPcrPid != 0 && mPacket.getPid() != mPcrPid)
                    continue;
                pcr.position = position + packetPosition * Mpeg.TS_PACKET_SIZE;
                mPcrPid = mPacket.getPid();
                pcr.timeUs = Mpeg.ptsToUs(mPacket.getPcr());
                break;
            }
            if (direction > 0)
                position += mReadBytes.length;
        }
        while (position > 0 && position < length && pcr.timeUs < 0);

        setPositionInBytes(oldPosition);
    }

    private boolean timeoutReached() {
        return (SystemClock.elapsedRealtime() - mOperationStartTimeMs) > mOperationTimeoutMs;
    }

    private void checkTimeout(String where) throws TsAnalyzerException {
        if (timeoutReached())
            throw new TsAnalyzerException("timeout", where);
    }

    private TimelineChunk findChunkWithTimestamp(long timestampUs) {
        for (TimelineChunk candidateChunk : mChunks) {
            if (timestampUs < candidateChunk.startTimeUs + candidateChunk.durationUs) {
                return candidateChunk;
            }
        }
        return null;
    }

    private TimelineChunk findChunkWithPosition(long position) {
        if (mChunks.isEmpty())
            return null;

        for (TimelineChunk candidateChunk : mChunks) {
            if (position <= candidateChunk.endPcr.position)
                return candidateChunk;
        }

        return mChunks.get(mChunks.size() - 1);
    }

    private void dumpChunks() {
        ASPlayerLog.i("nb_chunks:%d", mChunks.size());
        for (TimelineChunk chunk : mChunks) {
            ASPlayerLog.i("    duration:%d start[%d, %dms] end[%d, %dms]",
                    PtsAdjuster.deltaTimestamp(chunk.endPcr.timeUs, chunk.startPcr.timeUs) / 1000,
                    chunk.startPcr.position, chunk.startPcr.timeUs / 1000,
                    chunk.endPcr.position, chunk.endPcr.timeUs / 1000);
        }
    }

}
