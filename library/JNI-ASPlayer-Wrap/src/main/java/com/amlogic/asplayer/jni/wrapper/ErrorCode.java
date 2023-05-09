/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.jni.wrapper;


public class ErrorCode {

    /**
     * error code according to JNIASPlayer.h
     */

    public static final int OK = 0;

    public static final int ERROR_INVALID_PARAMS = -1;

    public static final int ERROR_INVALID_OPERATION = -2;

    public static final int ERROR_INVALID_OBJECT = -3;

    public static final int ERROR_RETRY = -4;

    public static final int ERROR_BUSY = -5;

    public static final int ERROR_END_OF_STREAM = -6;

    public static final int ERROR_IO = -7;

    public static final int ERROR_WOULD_BLOCK = -8;
}
