package com.amlogic.asplayer.core.sipsi.mpeg;

/**
 * This class gets information from a TS packet as described in
 *  13818-1, Section 2.4.3.2 (Transport Stream packet layer), Table 2-2
 */
public class TsPacket {

    static int nextPacketId = 0;

    /*
    int tei;
    int payloadUnitStart;
    int transportPriotiry;
    int pid;
    int scramblingControl;
    int adaptationFieldExist;
    int payloadDataExist;
    int continuityCounter;

    int afLength;
    int afDiscontinuity;
    int afRandomAccess;
    int afElementaryStreamPriority;
    int afPCRflag;
    int afOPCRflag;
    int afSplicingPointFlag;
    int afPrivateData;
    int afExtension;
    long pcr90Mhz;
    int pcr27Mhz;
    long opcr90Mhz;
    int opcr27Mhz;
    int afSpliceCountDown;

    byte[] payloadData;
    int payloadOffset;
    */

    byte[] mBytes;
    int mOffset;

    public TsPacket() {
    }

    public void wrap(byte[] bytes, int offset) {
        mBytes = bytes;
        mOffset = offset;
    }

    public byte[] getBytes() {
        return mBytes;
    }

    public int getOffset() {
        return mOffset;
    }

    public boolean isValid() {
        return (mBytes[mOffset] == 0x47) && ((mBytes[mOffset + 1] & 0x80) == 0);
    }

    public int getPid() {
        return ((mBytes[1 + mOffset] & 0x1f) << 8) | (mBytes[2 + mOffset] & 0xff);
    }

    public int getScramblingControl() {
        return ((mBytes[3 + mOffset] & 0xC0) >>> 6);
    }

    public boolean hasPcr() {
        if (!hasAdaptation())
            return false;
        if (getAdaptationLength() <= 0)
            return false;

        return ((mBytes[5 + mOffset] & 0x10) != 0);
    }

    public long getPcr() {
        if (!hasPcr())
            return 0;
        return (((long)mBytes[6 + mOffset] & 0xff) << 25) |
                (((long)mBytes[7 + mOffset] & 0xff) << 17) |
                (((long)mBytes[8 + mOffset] & 0xff) << 9) |
                (((long)mBytes[9 + mOffset] & 0xff) << 1) |
                (((long)mBytes[10 + mOffset] & 0xff) >> 7);
    }

    public long getPcrExt() {
        if (!hasPcr())
            return 0;
        return (((long) mBytes[10 + mOffset] & 0x1) << 8) | ((long) mBytes[11 + mOffset] & 0xff);
    }

    public int getContinuityCounter() {
        return mBytes[3 + mOffset] & 0xf;
    }

    public int getAdaptationLength() {
        return mBytes[4 + mOffset] & 0xff;
    }

    public boolean hasAdaptation() {
        return ((mBytes[3 + mOffset] & 0x20) != 0);
    }

    public boolean hasRandomAccessIndicator() {
        if (!hasAdaptation())
            return false;
        return ((mBytes[5 + mOffset] & 0x40) != 0);
    }

    public boolean hasPayload() {
        return ((mBytes[3 + mOffset] & 0x10) != 0);
    }

    public boolean isUnitStart() {
        return ((mBytes[1 + mOffset] & 0x40) != 0);
    }

    public int getPesPayloadPos() {
        // see ISO13818-1, section 2.4.3.3, payload_unit_start_indicator
        if (!hasPayload())
            return -1;
        if (!hasAdaptation())
            return mOffset + Mpeg.TS_PACKET_HEADER_SIZE;
        return mOffset + Mpeg.TS_PACKET_HEADER_SIZE + 1 + getAdaptationLength();
    }

    public int getPsiStartPayloadPos() {
        // see ISO13818-1, section 2.4.3.3, payload_unit_start_indicator
        if (!hasPayload() || !isUnitStart())
            return -1;
        int pos = mOffset + Mpeg.TS_PACKET_HEADER_SIZE;
        if (hasAdaptation())
            pos += 1 + getAdaptationLength();

        // pointerfield + 1
        return (pos + (mBytes[pos] & 0xff) + 1);
    }

    public int getPsiPayloadPos() {
        if (!hasPayload())
            return -1;
        int pos = mOffset + Mpeg.TS_PACKET_HEADER_SIZE;
        if (hasAdaptation())
            pos += 1 + getAdaptationLength();

        return pos;
    }

    public int fillWithPayload(byte[] data, int offset, int payloadPos) {
        if (payloadPos == -1)
            return 0;
        int payloadMaxSize =
                Math.min(mOffset + Mpeg.TS_PACKET_SIZE - payloadPos, data.length - offset);
        System.arraycopy(mBytes, payloadPos, data, offset, payloadMaxSize);
        return payloadMaxSize;
    }
}
