/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.api;

public class Pts {

    /**
     * StreamType
     *
     * @see StreamType
     */
    private int mStreamType;

    /**
     * pts
     */
    private long mPts;

    /**
     * renderTime
     */
    private long mRenderTime;

    public int getStreamType() {
        return mStreamType;
    }

    public void setStreamType(int streamType) {
        this.mStreamType = streamType;
    }

    public long getPts() {
        return mPts;
    }

    public void setPts(long pts) {
        this.mPts = pts;
    }

    public long getRenderTime() {
        return mRenderTime;
    }

    public void setRenderTime(long renderTime) {
        this.mRenderTime = renderTime;
    }
}
