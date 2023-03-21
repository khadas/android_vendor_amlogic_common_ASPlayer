package com.amlogic.asplayer.core.ts;


import com.amlogic.asplayer.core.ASPlayerLog;
import com.amlogic.asplayer.core.sipsi.mpeg.Mpeg;

import java.io.IOException;
import java.io.RandomAccessFile;

abstract class TsFileAnalyzer {

    // source to analyze
    private RandomAccessFile mFile;

    TsFileAnalyzer() {
    }

    void reset(RandomAccessFile file) {
        mFile = file;
    }

    abstract boolean updateTimeline();

    abstract long getPositionInBytes(long timestampUs);

    abstract long getPositionInUs(long position);

    abstract long getStartPositionInBytes();

    abstract long getStartPositionInUs();

    abstract long getEndPositionInBytes();

    abstract long getEndPositionInUs();

    protected long align(long position) {
        return (position / Mpeg.TS_PACKET_SIZE) * Mpeg.TS_PACKET_SIZE;
    }

    protected long setPositionInBytes(long position) {
        try {
            mFile.seek(position);
            return mFile.getFilePointer();
        } catch (IOException exception) {
            ASPlayerLog.w("error, %s", exception.getMessage());
            return -1;
        }
    }

    protected long getPositionInBytes() {
        try {
            return mFile.getFilePointer();
        } catch (IOException exception) {
            ASPlayerLog.w("error, %s", exception.getMessage());
            return -1;
        }
    }

    protected long getLengthInBytes() {
        try {
            return mFile.length();
        } catch (IOException exception) {
            ASPlayerLog.w("error, %s", exception.getMessage());
            return -1;
        }
    }

    protected int read(byte[] bytes) {
        try {
            return mFile.read(bytes);
        } catch (IOException exception) {
            ASPlayerLog.w("error, %s", exception.getMessage());
            return -1;
        }
    }
}
