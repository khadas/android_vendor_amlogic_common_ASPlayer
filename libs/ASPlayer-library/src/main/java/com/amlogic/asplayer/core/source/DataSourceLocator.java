package com.amlogic.asplayer.core.source;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

public class DataSourceLocator {

    public static final int SOURCE_UNKNOWN = 0;
    public static final int SOURCE_FILE = 2;
    public static final int SOURCE_HLS = 3;

    public static final int CONTAINER_UNKNOWN = 0;
    public static final int CONTAINER_TS = 1;

    // original uri provided by app/tif
    public Uri rawUri;
    // extra information provided by app/tif
    public Bundle extra;

    // source that can play the media
    public int sourceType;
    // container type of the media
    public int containerType;
    // uri extracted from rawUri suitable for source
    public Uri uriForSource;

    // service id used to select the right service in a multiple program transport stream
    public int serviceId;

    //
    public Context context;

    public DataSourceLocator(Context context) {
        this.context = context;
    }
}

