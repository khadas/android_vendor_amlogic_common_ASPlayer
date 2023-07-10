package com.amlogic.asplayer.core;

/**
 * FCC Mode
 * Normal mode: playback mode, decoding/rendering
 * Cache mode: Cache mode, no decoding/no rendering
 */
class FccWorkMode {
    public static final String MEDIA_FORMAT_KEY_FCC_WORKMODE = "vendor.tunerhal.passthrough.work-mode";
    public static final int MEDIA_FORMAT_FCC_WORKMODE_NORMAL = 0;
    public static final int MEDIA_FORMAT_FCC_WORKMODE_CACHE = 1;
}
