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

public interface TsPlaybackListener {

    static abstract class PlaybackEvent {
    }

    /**
     * Video format changed event
     */
    public static class VideoFormatChangeEvent extends PlaybackEvent {
        private final MediaFormat mVideoFormat;

        public VideoFormatChangeEvent(MediaFormat videoFormat) {
            this.mVideoFormat = videoFormat;
        }

        public MediaFormat getVideoFormat() {
            return mVideoFormat;
        }
    }

    /**
     * Audio format changed event
     */
    public static class AudioFormatChangeEvent extends PlaybackEvent {
        private final MediaFormat mAudioFormat;

        public AudioFormatChangeEvent(MediaFormat audioFormat) {
            mAudioFormat = audioFormat;
        }

        public MediaFormat getAudioFormat() {
            return mAudioFormat;
        }
    }

    /**
     * The player has displayed a first video frame (or audio for audio only media)
     * the media.
     */
    public static abstract class FirstFrameEvent extends PlaybackEvent {
        private final long mPositionMs;
        private final double mSpeed;

        public FirstFrameEvent(long positionMs, double speed) {
            mPositionMs = positionMs;
            mSpeed = speed;
        }

        public long getPositionMs() {
            return mPositionMs;
        }

        public double getSpeed() {
            return mSpeed;
        }
    }

    /**
     * The player has displayed a first video frame
     * the media.
     */
    public static class VideoFirstFrameEvent extends FirstFrameEvent {
        public VideoFirstFrameEvent(long positionMs, double speed) {
            super(positionMs, speed);
        }
    }

    /**
     * The player has displayed a first audio frame
     * the media.
     */
    public static class AudioFirstFrameEvent extends FirstFrameEvent {
        public AudioFirstFrameEvent(long positionMs, double speed) {
            super(positionMs, speed);
        }
    }

    void onPlaybackEvent(PlaybackEvent event);
}
