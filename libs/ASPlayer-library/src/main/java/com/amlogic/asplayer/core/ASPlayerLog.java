/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.core;

import android.util.Log;


/**
 * Utility to log messages, adding information about the caller
 * Note that these methods are consuming time and memory, so it must be used with care
 * That's why v() (for verbose) and d() (for debug) are not provided
 */
public class ASPlayerLog {

    private static final boolean DEBUG = true;
    private static final String TAG = Constant.LOG_TAG;

    // depth clarification
    // 0 -> native method VMStack.getThreadStackTrace()
    // 1 -> Thread.getStackTrace()
    // 2 -> TvLog.formatMessage()
    // 3 -> caller of TvLog.formatMessage()
    public static String formatMessage(int depth, String format, Object... args) {
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        String className = elements[depth].getClassName();
        String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
        String methodName = elements[depth].getMethodName();
        return String.format("%s.%s: %s",
                simpleClassName,
                methodName,
                String.format(format, args));
    }

    public static void e(String format, Object... args) {
        Log.e(TAG, formatMessage(4, format, args));
//        Log.e(TAG, String.format(format, args));
    }

    public static void w(String format, Object... args) {
        Log.w(TAG, formatMessage(4, format, args));
//        Log.w(TAG, String.format(format, args));
    }

    public static void i(String format, Object... args) {
        Log.i(TAG, formatMessage(4, format, args));
//        Log.i(TAG, String.format(format, args));
    }

    public static void d(String format, Object... args) {
        if (DEBUG) Log.d(TAG, formatMessage(4, format, args));
    }
}
