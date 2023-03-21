package com.amlogic.asplayer.core.source;

import android.os.ParcelFileDescriptor;

import java.io.IOException;

public interface DataSource {

    void setLocator(DataSourceLocator locator);
    DataSourceLocator getLocator();

    boolean isCacheable();
    void setUseCache(boolean use);

    void updateTimeline();

    boolean isOpen();

    void open() throws IOException;
    void close();

    int read(byte[] tsPackets) throws IOException;
    int read(byte[] tsPackets, int offset, int length) throws IOException;

    long setPositionInBytes(long position);
    long getPositionInBytes();

    long setPositionInUs(long positionUs);
    long getPositionInUs();

    long getEndPositionInBytes();
    long getEndPositionInUs();

    long getStartPositionInBytes();
    long getStartPositionInUs();

    long getPositionInBytes(long timestampUs);
    long getPositionInUs(long position);

    ParcelFileDescriptor getParcelFD();

    boolean isBuffered();
}
