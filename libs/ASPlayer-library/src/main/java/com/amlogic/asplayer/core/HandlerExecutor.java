package com.amlogic.asplayer.core;

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
