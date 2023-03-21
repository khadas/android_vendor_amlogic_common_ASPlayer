/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.jniasplayer;

import com.amlogic.asplayer.api.TsPlaybackListener;
import com.amlogic.asplayer.core.ASPlayerLog;

public class JniPlaybackListener implements TsPlaybackListener {

    private long mNativeContext;

    public JniPlaybackListener() {
        ASPlayerLog.d("JniPlaybackListener constructor");
    }

    @Override
    public void onPlaybackEvent(PlaybackEvent event) {
        ASPlayerLog.d("onPlaybackEvent, event: " + event);
        native_notifyPlaybackEvent(event);
    }

    private native void native_notifyPlaybackEvent(PlaybackEvent event);
}
