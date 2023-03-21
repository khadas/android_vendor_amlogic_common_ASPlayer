/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.core;

public class TsPlaybackConfig {

    // number of ts packets read at once
    public static final int TS_PACKET_SIZE = 188;
    public static final int BUFFERED_PACKETS_BYTES = TS_PACKET_SIZE * 100;
    // When switching between two tracks, there are data in decoder/renderer that will be flushed
    // To start at the same point, we need then to have a margin when we store data of non-selected
    // tracks
    public static final int TRACK_MARGIN_US = 200000;
    public static final int INVALID_VALUE = -1;
    public static final long MAX_PLAYBACK_BUFFER_TIME_MS = 1000;
    public static final long PLAYBACK_BUFFER_SIZE = TS_PACKET_SIZE * 32768;
    public static final long THRESHOLD_POSITION_DIFF = 1000;
}
