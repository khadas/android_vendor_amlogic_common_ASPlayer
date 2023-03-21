/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.demo.utils;

import android.os.Handler;
import android.util.Log;

import java.util.concurrent.Executor;

public class HandlerExecutor implements Executor {
    private final Handler mHandler;

    public HandlerExecutor(Handler handler) {
        mHandler = handler;
    }

    @Override
    public void execute(Runnable command) {
        if (!mHandler.post(command)) {
            Log.e("HandlerExecutor", mHandler + " has been finished");
        }
    }
}
