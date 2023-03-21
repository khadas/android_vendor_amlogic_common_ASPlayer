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
 * ASPlayer input buffer
 */
public class InputBuffer {

    /**
     * Input buffer type (secure/no secure)
     *
     * @see InputBufferType
     */
    public int mInputBufferType;

    /**
     * Input buffer data
     */
    public byte[] mBuffer;

    /**
     * Input buffer offset
     */
    public int mOffset;

    /**
     * Input buffer size
     */
    public int mBufferSize;

    // For JNI use
    public InputBuffer() {
    }

    public InputBuffer(int inputBufferType, byte[] buffer, int offset, int bufferSize) {
        this.mInputBufferType = inputBufferType;
        this.mBuffer = buffer;
        this.mOffset = offset;
        this.mBufferSize = bufferSize;
    }
}