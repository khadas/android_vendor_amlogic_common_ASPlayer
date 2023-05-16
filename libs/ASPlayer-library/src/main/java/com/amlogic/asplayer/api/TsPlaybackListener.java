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
        protected final long mPositionMs;

        public FirstFrameEvent(long positionMs) {
            mPositionMs = positionMs;
        }

        public long getPositionMs() {
            return mPositionMs;
        }
    }

    /**
     * The player has displayed a first video frame
     */
    public static class VideoFirstFrameEvent extends FirstFrameEvent {
        public VideoFirstFrameEvent(long positionMs) {
            super(positionMs);
        }
    }

    /**
     * The player has displayed a first audio frame
     */
    public static class AudioFirstFrameEvent extends FirstFrameEvent {
        public AudioFirstFrameEvent(long positionMs) {
            super(positionMs);
        }
    }

    /**
     * The player has decoded a first video frame
     */
    public static class DecodeFirstVideoFrameEvent extends FirstFrameEvent {
        public DecodeFirstVideoFrameEvent(long positionMs) {
            super(positionMs);
        }
    }

    /**
     * The player has decoded a first audio frame
     */
    public static class DecodeFirstAudioFrameEvent extends FirstFrameEvent {
        public DecodeFirstAudioFrameEvent(long positionMs) {
            super(positionMs);
        }
    }

    void onPlaybackEvent(PlaybackEvent event);
}
