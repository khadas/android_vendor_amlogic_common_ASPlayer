/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.core;

import android.util.Pair;

import com.amlogic.asplayer.api.EventMask;
import com.amlogic.asplayer.api.InitParams;
import com.amlogic.asplayer.api.InputBufferType;
import com.amlogic.asplayer.api.InputSourceType;

import java.util.ArrayList;
import java.util.List;

public class ASPlayerConfig {

    public static final int PLAYBACK_MODE_PASSTHROUGH = InitParams.PLAYBACK_MODE_PASSTHROUGH;
    public static final int PLAYBACK_MODE_ES_SECURE = InitParams.PLAYBACK_MODE_ES_SECURE;

    private static final int DEFAULT_INPUT_SOURCE_TYPE = InputSourceType.TS_MEMORY;

    private int mPlaybackMode;
    private int mInputSourceType = DEFAULT_INPUT_SOURCE_TYPE;
    private long mEventMask = 0;
    private long mReadTimeoutMs;
    private List<Pair<Float, Float>> mSupportedSmoothTrickSpeed;

    private ASPlayerConfig() {

    }

    private void setPlaybackMode(int playbackMode) {
        mPlaybackMode = playbackMode;
    }

    public int getPlaybackMode() {
        return mPlaybackMode;
    }

    private void setInputSourceType(int inputSourceType) {
        this.mInputSourceType = inputSourceType;
    }

    public int getInputSourceType() {
        return mInputSourceType;
    }

    private void setReadTimeoutMs(long timeoutMs) {
        mReadTimeoutMs = timeoutMs;
    }

    public long getReadTimeoutMs() {
        return mReadTimeoutMs;
    }

    private void setEventMask(long eventMask) {
        this.mEventMask = eventMask;
    }

    public void setSupportedSmoothTrickSpeed(List<Pair<Float, Float>> supportedSpeed) {
        mSupportedSmoothTrickSpeed = supportedSpeed;
    }

    public boolean canSupportSmoothTrick(double speed) {
        if (mSupportedSmoothTrickSpeed != null) {
            for (Pair<Float, Float> supportedSpeedRange: mSupportedSmoothTrickSpeed) {
                if (supportedSpeedRange.first <= speed && speed <= supportedSpeedRange.second) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isPtsEventEnabled() {
        return (mEventMask & EventMask.EVENT_TYPE_PTS_MASK) != 0;
    }

    public static class Builder {
        private int mPlaybackMode;
        private int mInputSourceType;
        private long mEventMask;
        private long mReadTimeoutMs;
        private List<Pair<Float, Float>> mSupportedSmoothTrickSpeed = new ArrayList<>();

        private static List<Pair<Float, Float>> DEFAULT_SMOOTH_TRICK_SPEED = new ArrayList<>();

        static {
            DEFAULT_SMOOTH_TRICK_SPEED.add(Pair.create(0f, 2f));
        }

        public Builder() {
            mPlaybackMode = PLAYBACK_MODE_PASSTHROUGH;
            mInputSourceType = DEFAULT_INPUT_SOURCE_TYPE;
            mEventMask = 0;
            mReadTimeoutMs = 1000;
            mSupportedSmoothTrickSpeed.addAll(DEFAULT_SMOOTH_TRICK_SPEED);
        }

        public Builder setPlaybackMode(int playbackMode) {
            mPlaybackMode = playbackMode;
            return this;
        }

        public Builder setInputSourceType(int inputSourceType) {
            mInputSourceType = inputSourceType;
            return this;
        }

        public Builder setEventMask(long eventMask) {
            mEventMask = eventMask;
            return this;
        }

        public Builder setReadTimeout(long timeoutMs) {
            mReadTimeoutMs = timeoutMs;
            return this;
        }

        public Builder addSupportedSmoothTrickSpeed(Pair<Float, Float> supportedSpeedRange) {
            mSupportedSmoothTrickSpeed.add(supportedSpeedRange);
            return this;
        }

        public ASPlayerConfig build() {
            ASPlayerConfig config = new ASPlayerConfig();
            config.setPlaybackMode(mPlaybackMode);
            config.setInputSourceType(mInputSourceType);
            config.setEventMask(mEventMask);
            config.setReadTimeoutMs(mReadTimeoutMs);
            config.setSupportedSmoothTrickSpeed(mSupportedSmoothTrickSpeed);
            return config;
        }
    }
}