package com.amlogic.asplayer.core.sipsi.mpeg;

public class Mpeg {

    public static final int PID_MAX = 8191;

    public static final long PTS_MAX_VALUE = 0x1FFFFFFFFL;
    public static final long PTS_MAX_VALUE_IN_US = Mpeg.ptsToUs(PTS_MAX_VALUE);

    public static final int TS_PACKET_SIZE = 188;
    public static final int TS_PACKET_HEADER_SIZE = 4;
    public static final int TS_PACKET_HEADER_SIZE_AF = 6;
    public static final int TS_PACKET_HEADER_SIZE_PCR = 12;

    public static final long PCR_MAX_DELTA_US = 200000;

    public final static int CRC_SIZE = 4;
    public static final int PSI_HEADER_SIZE = 3;
    public static final int PSI_PRIVATE_MAX_SIZE = 4093;

    public static final int PES_MIN_HEADER_SIZE = 6;
    public static final int PES_MIN_HEADER_WITH_EXTENSION_SIZE = 9;

    public static final int PES_STREAM_ID_PROGRAM_STREAM_MAP = 0xBC;
    public static final int PES_STREAM_ID_PRIVATE_STREAM_1 = 0xBD;
    public static final int PES_STREAM_ID_PADING_STREAM = 0xBE;
    public static final int PES_STREAM_ID_PRIVATE_STREAM_2 = 0xBF;
    public static final int PES_STREAM_ID_ECM_STREAM = 0xF0;
    public static final int PES_STREAM_ID_EMM_STREAM = 0xF1;
    public static final int PES_STREAM_ID_DSMCC = 0xF2;
    public static final int PES_STREAM_ID_13522 = 0xF3;
    public static final int PES_STREAM_ID_H222_1_TYPE_A = 0xF4;
    public static final int PES_STREAM_ID_H222_1_TYPE_B = 0xF5;
    public static final int PES_STREAM_ID_H222_1_TYPE_C = 0xF6;
    public static final int PES_STREAM_ID_H222_1_TYPE_D = 0xF7;
    public static final int PES_STREAM_ID_H222_1_TYPE_E = 0xF8;
    public static final int PES_STREAM_ID_ANCILLARY_STREAM = 0xF9;
    public static final int PES_STREAM_ID_SL_PACKETIZED_STREAM = 0xFA;
    public static final int PES_STREAM_ID_FLEXMUX_STREAM = 0xF1;
    public static final int PES_STREAM_ID_PROGRAM_STREAM_DIRECTORY = 0xFF;

    public static final long MICROS_PER_SECOND = 1000000L;

    public static long ptsToUs(long pts) {
        return (pts * MICROS_PER_SECOND) / 90000;
    }

    public static long usToPts(long us) {
        return (us * 90000) / MICROS_PER_SECOND;
    }

    public static long deltaPts(long pts1, long pts2) {
        long delta1 = pts1 - pts2;
        long delta2 = pts1 + 0x1FFFFFFFFL - pts2;
        if (Math.abs(delta1) < Math.abs(delta2))
            return delta1;
        else
            return delta2;
    }

    public static long pcrToUs(long pcr) {
        return (pcr * MICROS_PER_SECOND) / (90000 * 300);
    }
}
