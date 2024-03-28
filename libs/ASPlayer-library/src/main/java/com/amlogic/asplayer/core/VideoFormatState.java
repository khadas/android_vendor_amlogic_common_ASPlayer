/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.core;

import android.media.MediaFormat;

import com.amlogic.asplayer.api.VideoFormat;

class VideoFormatState implements VideoOutputPath.VideoFormatListener {
    private VideoInfo mVideoInfo;

    private EventNotifier mEventNotifier;

    VideoFormatState(EventNotifier eventNotifier) {
        mEventNotifier = eventNotifier;
        mVideoInfo = new VideoInfo();
    }

    void reset() {
        mVideoInfo = new VideoInfo();
    }

    void release() {
        mVideoInfo = null;
        mEventNotifier = null;
    }

    @Override
    public void onVideoSizeInfoChanged(int width, int height, int pixelAspectRatio) {
        if (width <= 0 || height <= 0 || pixelAspectRatio < 0) {
            return;
        }

        boolean notifyEvent = false;
        if (width != mVideoInfo.getWidth()) {
            mVideoInfo.setWidth(width);
            notifyEvent = true;
        }
        if (height != mVideoInfo.getHeight()) {
            mVideoInfo.setHeight(height);
            notifyEvent = true;
        }
        if (pixelAspectRatio != mVideoInfo.getAspectRatio()) {
            mVideoInfo.setAspectRatio(pixelAspectRatio);
            notifyEvent = true;
        }

        if (notifyEvent) {
            notifyVideoFormatChanged();
        }
    }

    @Override
    public void onAfdInfoChanged(byte activeFormat) {

    }

    @Override
    public void onFrameRateChanged(int frameRate) {
        if (frameRate <= 0) {
            return;
        }

        if (frameRate != mVideoInfo.getFrameRate()) {
            mVideoInfo.setFrameRate(frameRate);
            notifyVideoFormatChanged();
        }
    }

    @Override
    public void onVFType(int vfType) {
        if (vfType != mVideoInfo.getVFType()) {
            mVideoInfo.setVFType(vfType);
            notifyVideoFormatChanged();
        }
    }

    private void notifyVideoFormatChanged() {
        if (mVideoInfo == null || mEventNotifier == null) {
            return;
        }

        MediaFormat mediaFormat = new MediaFormat();
        mediaFormat.setInteger(VideoFormat.KEY_WIDTH, mVideoInfo.getWidth());
        mediaFormat.setInteger(VideoFormat.KEY_HEIGHT, mVideoInfo.getHeight());
        mediaFormat.setInteger(VideoFormat.KEY_FRAME_RATE, mVideoInfo.getFrameRate());
        mediaFormat.setInteger(VideoFormat.KEY_ASPECT_RATIO, mVideoInfo.getAspectRatio());
        mediaFormat.setInteger(VideoFormat.KEY_VF_TYPE, mVideoInfo.getVFType());

        mEventNotifier.notifyVideoFormatChange(mediaFormat);
    }
}
