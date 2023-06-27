/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.demo.player;

import android.content.Context;
import android.os.HandlerThread;
import android.os.Looper;

public class SimpleTvPlayer extends TvPlayer {

    private HandlerThread mHandlerThread;

    private SimpleTvPlayer(Context context, Looper looper) {
        super(context, looper);
    }

    public static SimpleTvPlayer create(Context context) {
        HandlerThread handlerThread = new HandlerThread("simple-tvplayer");
        handlerThread.start();

        SimpleTvPlayer player = new SimpleTvPlayer(context, handlerThread.getLooper());
        player.mHandlerThread = handlerThread;
        return player;
    }

    @Override
    public void release() {
        super.release();

        mHandlerThread.quitSafely();
        mHandlerThread = null;
    }
}
