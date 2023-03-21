/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.api;

public class Parameters {

    public static final int KEY_AUDIO_PRESENTATION_ID = 0;

    public static final int KEY_VIDEO_SECLEVEL = KEY_AUDIO_PRESENTATION_ID + 1;

    public static final int KEY_SET_AUDIO_PATCH_MANAGE_MODE = KEY_VIDEO_SECLEVEL + 1;

    public static final int KEY_AUDIO_SECLEVEL = KEY_SET_AUDIO_PATCH_MANAGE_MODE + 1;

    public static final int KEY_SET_SPDIF_STATUS = KEY_AUDIO_SECLEVEL + 1;

    public static final int KEY_SET_VIDEO_RECOVERY_MODE = KEY_SET_SPDIF_STATUS + 1;

    public static final int KEY_SET_OSD = KEY_SET_VIDEO_RECOVERY_MODE + 1;

    public static final int KEY_SET_LOGGER_LEVEL = KEY_SET_OSD + 1;

    public static final int KEY_SET_WMA_DESCR = KEY_SET_LOGGER_LEVEL + 1;

    public static final int KEY_SET_ES_AUDIO_EXTRA_PARAM = KEY_SET_WMA_DESCR + 1;

    public static final int KEY_SET_STREAM_EOF = KEY_SET_ES_AUDIO_EXTRA_PARAM + 1;

    public static final int KEY_BOOTPLAY_MODE = KEY_SET_STREAM_EOF + 1;

    public static final int KEY_ENABLE_VFRAME_COUNTER = KEY_BOOTPLAY_MODE + 1;

    public static final int KEY_SET_AUDIO_LANG = KEY_ENABLE_VFRAME_COUNTER + 1;
}