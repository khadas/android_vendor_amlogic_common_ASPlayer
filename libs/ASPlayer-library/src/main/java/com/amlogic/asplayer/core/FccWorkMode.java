package com.amlogic.asplayer.core;

/**
 * 工作模式
 * Normal mode: 正常播放
 * Cache mode: Cache模式，不输出音视频
 */
class FccWorkMode {
    public static final String MEDIA_FORMAT_KEY_FCC_WORKMODE = "vendor.tunerhal.fcc.work-mode";
    public static final int MEDIA_FORMAT_FCC_WORKMODE_NORMAL = 0;
    public static final int MEDIA_FORMAT_FCC_WORKMODE_CACHE = 1;
}
