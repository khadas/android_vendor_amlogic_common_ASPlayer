/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.api;

import com.amlogic.asplayer.core.ASPlayerLog;

import java.security.InvalidParameterException;

/**
 * ASPlayer init parameters
 */
public class InitParams {

    public static final int PLAYBACK_MODE_PASSTHROUGH = 0;
    public static final int PLAYBACK_MODE_ES_SECURE = 1;

    /**
     * Playback mode
     */
    private int mPlaybackMode;

    /**
     * Input source type
     *
     * @see InputSourceType
     */
    private int mInputSourceType;

    /**
     * Mask the event type need by caller
     */
    private long mEventMask;

    private InitParams() {

    }

    public int getPlaybackMode() {
        return mPlaybackMode;
    }

    private void setPlaybackMode(int playbackMode) {
        if (playbackMode != PLAYBACK_MODE_PASSTHROUGH && playbackMode != PLAYBACK_MODE_ES_SECURE) {
            ASPlayerLog.i("setPlaybackMode failed, invalid playback mode: " + playbackMode);
            throw new InvalidParameterException(String.format("invalid playback mode, %d", playbackMode));
        }
        this.mPlaybackMode = playbackMode;
    }

    public int getInputSourceType() {
        return mInputSourceType;
    }

    private void setInputSourceType(int inputSourceType) {
        if (inputSourceType != InputSourceType.TS_DEMOD
            && inputSourceType != InputSourceType.TS_MEMORY
            && inputSourceType != InputSourceType.ES_MEMORY) {
            ASPlayerLog.i("invalid input source type: " + inputSourceType);
            throw new InvalidParameterException(String.format("invalid input source type: %d", inputSourceType));
        }
        this.mInputSourceType = inputSourceType;
    }

    public long getEventMask() {
        return mEventMask;
    }

    private void setEventMask(long eventMask) {
        this.mEventMask = eventMask;
    }

    public static class Builder {

        private int mPlaybackMode = PLAYBACK_MODE_PASSTHROUGH;

        private int mInputSourceType = InputSourceType.TS_MEMORY;

        private long mEventMask = 0;

        public Builder() {
        }

        /**
         * Playback Mode
         *
         * @param playbackMode
         * @return
         */
        public Builder setPlaybackMode(int playbackMode) {
            this.mPlaybackMode = playbackMode;
            return this;
        }

        /**
         * Input source type
         *
         * @see InputSourceType
         *
         * @param inputSourceType
         * @return
         */
        public Builder setInputSourceType(int inputSourceType) {
            this.mInputSourceType = inputSourceType;
            return this;
        }

        public Builder setEventMask(long eventMask) {
            this.mEventMask = eventMask;
            return this;
        }

        public InitParams build() {
            InitParams initParams = new InitParams();
            initParams.setPlaybackMode(mPlaybackMode);
            initParams.setInputSourceType(mInputSourceType);
            initParams.setEventMask(mEventMask);
            return initParams;
        }
    }
}