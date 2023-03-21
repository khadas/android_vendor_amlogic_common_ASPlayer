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
 * ASPlayer audio init parameters
 */
public class AudioParams {

    /**
     * Audio mime type
     */
    private String mMimeType;

    /**
     * Audio sample rate
     */
    private int mSampleRate;

    /**
     * Audio channel count
     */
    private int mChannelCount;

    /**
     * Audio pid in ts
     */
    private int mPid;

    /**
     * Audio filter id
     */
    private int mTrackFilterId;

    /**
     * AvSyncHwId
     */
    private int mAvSyncHwId;

    /**
     * Audio security seclevel
     */
    private int mSecLevel;

    /**
     * Audio media format
     */
    private MediaFormat mMediaFormat;

    // for JNI use
    private AudioParams() {

    }

    private AudioParams(String mimeType, int sampleRate, int channelCount) {
        this.mMimeType = mimeType;
        this.mSampleRate = sampleRate;
        this.mChannelCount = channelCount;
    }

    private AudioParams(MediaFormat mediaFormat) {
        this.mMediaFormat = mediaFormat;
    }

    public String getMimeType() {
        return this.mMimeType;
    }

    private void setMimeType(String mimeType) {
        this.mMimeType = mimeType;
    }

    public int getSampleRate() {
        return this.mSampleRate;
    }

    private void setSampleRate(int sampleRate) {
        this.mSampleRate = sampleRate;
    }

    public int getChannelCount() {
        return this.mChannelCount;
    }

    private void setChannelCount(int channelCount) {
        this.mChannelCount = channelCount;
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

    public int getSecLevel() {
        return mSecLevel;
    }

    private void setSecLevel(int secLevel) {
        this.mSecLevel = secLevel;
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
        sb.append("AudioParams{");
        sb.append(", mMimeType=").append(mMimeType);
        sb.append(", mSampleRate=").append(mSampleRate);
        sb.append(", mChannelCount=").append(mChannelCount);
        sb.append(", mPid=").append(mPid);
        sb.append(", mTrackFilterId=").append(mTrackFilterId);
        sb.append(", mAvSyncHwId=").append(mAvSyncHwId);
        sb.append(", mSecLevel=").append(mSecLevel);
        sb.append(", mMediaFormat=").append(mMediaFormat);
        sb.append("}");
        return sb.toString();
    }

    public static class Builder {

        private String mMimeType;

        private int mSampleRate;

        private int mChannelCount;

        private int mPid;

        private int mTrackFilterId;

        private int mAvSyncHwId;

        private int mSecLevel;

        private MediaFormat mMediaFormat;

        public Builder(String mimeType, int sampleRate, int channelCount) {
            this.mMimeType = mimeType;
            this.mSampleRate = sampleRate;
            this.mChannelCount = channelCount;
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

        public Builder setSecLevel(int secLevel) {
            this.mSecLevel = secLevel;
            return this;
        }

        public AudioParams build() {
            AudioParams audioParams = new AudioParams(mMimeType, mSampleRate, mChannelCount);
            audioParams.setPid(mPid);
            audioParams.setTrackFilterId(mTrackFilterId);
            audioParams.setAvSyncHwId(mAvSyncHwId);
            audioParams.setSecLevel(mSecLevel);
            audioParams.setMediaFormat(mMediaFormat);
            return audioParams;
        }
    }
}
