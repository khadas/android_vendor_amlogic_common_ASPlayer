/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.demo.player;

class TrackInfo {
    public int pid;
    public String mimeType;

    static class VideoTrackInfo extends TrackInfo {
        public String videoStreamType;
    }

    static class AudioTrackInfo extends TrackInfo {
        public String audioStreamType;
    }
}
