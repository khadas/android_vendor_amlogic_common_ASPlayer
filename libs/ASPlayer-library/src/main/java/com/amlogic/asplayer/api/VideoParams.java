/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.api;

import android.media.MediaFormat;

/**
 * video init parameters
 */
public class VideoParams {

    /**
     * Video mimeType
     */
    private String mMimeType;

    /**
     * Video width
     */
    private int mWidth;

    /**
     * video height
     */
    private int mHeight;

    /**
     * Video pid in ts
     */
    private int mPid;

    /**
     * video track filter id
     */
    private int mTrackFilterId;

    /**
     * video track avSyncHwId
     */
    private int mAvSyncHwId;

    /**
     * Video scrambled or not
     */
    private boolean mScrambled;

    /**
     * Video media format
     */
    private MediaFormat mMediaFormat;

    // for JNI use
    private VideoParams() {

    }

    private VideoParams(String mimeType, int width, int height) {
        this.mMimeType = mimeType;
        this.mWidth = width;
        this.mHeight = height;
    }

    private VideoParams(MediaFormat mediaFormat) {
        this.mMediaFormat = mediaFormat;
    }

    private void setMimeType(String mimeType) {
        this.mMimeType = mimeType;
    }

    public String getMimeType() {
        return this.mMimeType;
    }

    private void setWidth(int width) {
        this.mWidth = width;
    }

    public int getWidth() {
        return this.mWidth;
    }

    private void setHeight(int height) {
        this.mHeight = height;
    }

    public int getHeight() {
        return this.mHeight;
    }

    public int getPid() {
        return mPid;
    }

    private void setPid(int pid) {
        this.mPid = pid;
    }

    public int getTrackFilterId() {
        return mTrackFilterId;
    }

    private void setTrackFilterId(int trackFilterId) {
        this.mTrackFilterId = trackFilterId;
    }

    public int getAvSyncHwId() {
        return mAvSyncHwId;
    }

    private void setAvSyncHwId(int avSyncHwId) {
        this.mAvSyncHwId = avSyncHwId;
    }

    private void setScrambled(boolean scrambled) {
        mScrambled = scrambled;
    }

    public boolean isScrambled() {
        return mScrambled;
    }

    public MediaFormat getMediaFormat() {
        return mMediaFormat;
    }

    private void setMediaFormat(MediaFormat mediaFormat) {
        this.mMediaFormat = mediaFormat;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("VideoParams{");
        sb.append("mMimeType=").append(mMimeType);
        sb.append(", mWidth=").append(mWidth);
        sb.append(", mHeight=").append(mHeight);
        sb.append(", mPid=").append(mPid);
        sb.append(", mTrackFilterId=").append(mTrackFilterId);
        sb.append(", mAvSyncHwId=").append(mAvSyncHwId);
        sb.append(", mScrambled=").append(mScrambled);
        sb.append(", mMediaFormat=").append(mMediaFormat);
        sb.append("}");
        return sb.toString();
    }

    public static class Builder {

        private String mMimeType;

        private int mWidth;

        private int mHeight;

        private int mPid;

        private int mTrackFilterId;

        private int mAvSyncHwId;

        private boolean mScrambled;

        private MediaFormat mMediaFormat;

        public Builder(String mimeType, int width, int height) {
            this.mMimeType = mimeType;
            this.mWidth = width;
            this.mHeight = height;
        }

        public Builder(MediaFormat mediaFormat) {
            this.mMediaFormat = mediaFormat;
        }

        public Builder setPid(int pid) {
            this.mPid = pid;
            return this;
        }

        public Builder setTrackFilterId(int trackFilterId) {
            this.mTrackFilterId = trackFilterId;
            return this;
        }

        public Builder setAvSyncHwId(int avSyncHwId) {
            this.mAvSyncHwId = avSyncHwId;
            return this;
        }

        public Builder setScrambled(boolean scrambled) {
            this.mScrambled = scrambled;
            return this;
        }

        public VideoParams build() {
            VideoParams videoParams = new VideoParams(mMimeType, mWidth, mHeight);
            videoParams.setPid(mPid);
            videoParams.setTrackFilterId(mTrackFilterId);
            videoParams.setAvSyncHwId(mAvSyncHwId);
            videoParams.setScrambled(mScrambled);
            videoParams.setMediaFormat(mMediaFormat);
            return videoParams;
        }
    }
}