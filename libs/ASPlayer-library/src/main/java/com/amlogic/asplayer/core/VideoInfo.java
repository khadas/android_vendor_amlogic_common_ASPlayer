/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.core;

class VideoInfo {
    private int mWidth;
    private int mHeight;
    private int mAspectRatio;
    private int mFrameRate;
    private int mVfType;

    VideoInfo() {
    }

    void setWidth(int width) {
        mWidth = width;
    }

    int getWidth() {
        return mWidth;
    }

    void setHeight(int height) {
        mHeight = height;
    }

    int getHeight() {
        return mHeight;
    }

    void setAspectRatio(int aspectRatio) {
        mAspectRatio = aspectRatio;
    }

    int getAspectRatio() {
        return mAspectRatio;
    }

    void setFrameRate(int frameRate) {
        mFrameRate = frameRate;
    }

    int getFrameRate() {
        return mFrameRate;
    }

    void setVFType(int vfType) {
        mVfType = vfType;
    }

    int getVFType() {
        return mVfType;
    }
}
