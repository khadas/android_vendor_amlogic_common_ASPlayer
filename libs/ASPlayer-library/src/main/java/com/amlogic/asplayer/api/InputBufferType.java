/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.api;

/**
 * Input buffer type
 */
public class InputBufferType {

    /**
     * Input buffer is normal buffer
     */
    public static final int NORMAL = 0;

    /**
     * Input buffer is secure buffer
     */
    public static final int SECURE = 1;

    /**
     * Input buffer is normal but tvp enable
     */
    public static final int TVP = 2;
}