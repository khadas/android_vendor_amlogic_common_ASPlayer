package com.amlogic.asplayer.core.ts;

import android.os.ParcelFileDescriptor;


import com.amlogic.asplayer.core.ASPlayerLog;
import com.amlogic.asplayer.core.source.DataSource;
import com.amlogic.asplayer.core.source.DataSourceLocator;

import java.io.IOException;
import java.io.RandomAccessFile;


public class DataSourceFile implements DataSource {

    private RandomAccessFile mFile;
    private ParcelFileDescriptor mFileFd;

    private String mMediaLocation;
    private DataSourceLocator mLocator;

    private TsFileAnalyzer mTsAnalyzer;
    private boolean mAnalyzed;

    public DataSourceFile() {
    }

    @Override
    public boolean isCacheable() {
        return (mTsAnalyzer != null);
    }

    @Override
    public void setUseCache(boolean use) {
    }

    @Override
    public void setLocator(DataSourceLocator locator) {
        mMediaLocation = locator.uriForSource.getPath();
        mLocator = locator;
    }

    @Override
    public DataSourceLocator getLocator() {
        return mLocator;
    }


    @Override
    public void updateTimeline() {
        if (mAnalyzed)
            return;

        mAnalyzed = true;

        // with pcr
        mTsAnalyzer = new TsFileAnalyzerByPcr();
        mTsAnalyzer.reset(mFile);
        if (mTsAnalyzer.updateTimeline())
            return;

        mTsAnalyzer = null;
    }

    @Override
    public boolean isOpen() {
        return (mFile != null);
    }

    @Override
    public void open() throws IOException {
        if (mFile != null)
            return;

        mFile = new RandomAccessFile(mMediaLocation, "r");
        mFileFd = null;
    }

    @Override
    public void close() {
        try {
            if (mFile != null)
                mFile.close();
        } catch (IOException exception) {
            ASPlayerLog.w("fails with:%s, error:%s", mMediaLocation, exception.getMessage());
        }
        mFile = null;

        try {
            if (mFileFd != null) {
                mFileFd.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        mFileFd = null;

        mAnalyzed = false;
    }

    @Override
    public int read(byte[] tsPackets) throws IOException {
        return read(tsPackets, 0, tsPackets.length);
    }

    @Override
    public int read(byte[] tsPackets, int offset, int length) throws IOException {
        mFile.readFully(tsPackets, offset, length);
        return length;
    }

    @Override
    public long setPositionInBytes(long position) {
        try {
            mFile.seek(position);
            position = mFile.getFilePointer();
        } catch (IOException exception) {
            ASPlayerLog.w("fails, pos:%d error:%s", position, exception.getMessage());
            position = -1;
        }
        return position;
    }

    @Override
    public long getPositionInBytes() {
        try {
            if (mFile != null)
                return mFile.getFilePointer();
        } catch (IOException exception) {
            ASPlayerLog.w("fails, error:%s", exception.getMessage());
        }
        return -1;
    }

    @Override
    public long setPositionInUs(long positionUs) {
        long position = -1;

        try {
            position = getPositionInBytes(positionUs);
            if (position >= 0)
                mFile.seek(position);
            else
                ASPlayerLog.w("can't get position in bytes: position:%d, positionUs:%d", position, positionUs);
        } catch (IOException exception) {
            ASPlayerLog.w("fails:%d, exception:%s", positionUs, exception.getMessage());
        }

        return position;
    }

    @Override
    public long getPositionInUs() {
        return getPositionInUs(getPositionInBytes());
    }

    @Override
    public long getEndPositionInBytes() {
        if (mTsAnalyzer != null) {
            return mTsAnalyzer.getEndPositionInBytes();
        } else {
            try {
                if (mFile != null)
                    return mFile.length();
            } catch (IOException exception) {
                ASPlayerLog.w("fails, error:%s", exception.getMessage());
            }
            return 0;
        }
    }

    @Override
    public long getEndPositionInUs() {
        if (mTsAnalyzer != null)
            return mTsAnalyzer.getEndPositionInUs();
        else
            return 0;
    }

    @Override
    public long getStartPositionInBytes() {
        if (mTsAnalyzer != null)
            return mTsAnalyzer.getStartPositionInBytes();
        else
            return 0;
    }

    @Override
    public long getStartPositionInUs() {
        if (mTsAnalyzer != null)
            return mTsAnalyzer.getStartPositionInUs();
        else
            return 0;
    }

    @Override
    public long getPositionInBytes(long timestampUs) {
        if (mTsAnalyzer != null)
            return mTsAnalyzer.getPositionInBytes(timestampUs);
        else
            return -1;
    }

    @Override
    public long getPositionInUs(long position) {
        if (mTsAnalyzer != null)
            return mTsAnalyzer.getPositionInUs(position);
        else
            return -1;
    }

    @Override
    public ParcelFileDescriptor getParcelFD() {
        if (mFile != null && mFileFd == null) {
            try {
                mFileFd = ParcelFileDescriptor.dup(mFile.getFD());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return mFileFd;
    }

    @Override
    public String toString() {
        return mMediaLocation;
    }

    @Override
    public boolean isBuffered() {
        return true;
    }
}
