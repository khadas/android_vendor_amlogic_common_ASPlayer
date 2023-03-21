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
 * ASPlayer input frame buffer
 */
public class InputFrameBuffer {

    /**
     * input buffer
     */
    private InputBuffer inputBuffer;

    /**
     * frame pts, using only for frame mode
     */
    private long pts;

    private boolean isVideo;

    public InputFrameBuffer(InputBuffer inputBuffer, long pts, boolean isVideo) {
        this.inputBuffer = inputBuffer;
        this.pts = pts;
        this.isVideo = isVideo;
    }

    public InputFrameBuffer(int inputBufferType, byte[] buffer, int offset, int bufferSize, long pts, boolean isVideo) {
        this.inputBuffer = new InputBuffer(inputBufferType, buffer, offset, bufferSize);
        this.pts = pts;
        this.isVideo = isVideo;
    }
}