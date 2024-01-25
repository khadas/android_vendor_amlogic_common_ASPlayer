/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.core;

import android.media.MediaFormat;
import android.text.TextUtils;

public class DolbyVisionUtils {

    public static final String MIMETYPE_DOLBY_VISION_HEVC = "video/dolby-vision-hevc";
    public static final String MIMETYPE_DOLBY_VISION_AVC = "video/dolby-vision-avc";
    public static final String MIMETYPE_DOLBY_VISION_AV1 = "video/dolby-vision-av1";

    public static final int CODEC_TYPE_UNKNOWN = 0;
    public static final int CODEC_TYPE_HEVC = 1;
    public static final int CODEC_TYPE_AVC = 2;
    public static final int CODEC_TYPE_AV1 = 3;

    public static final String CODEC_NAME_UNKNOWN = "";
    public static final String CODEC_NAME_HEVC = "dvhe";
    public static final String CODEC_NAME_AVC = "dvav";
    public static final String CODEC_NAME_AV1 = "dav1";

    public static final int STREAM_TYPE_HEVC = 0x24;
    public static final int STREAM_TYPE_AVC = 0x1B;

    public static int getCodecType(String mimeType) {
        int codecType = DolbyVisionUtils.CODEC_TYPE_UNKNOWN;

        if (TextUtils.isEmpty(mimeType)) {
            return codecType;
        }

        if (mimeType.equalsIgnoreCase(MIMETYPE_DOLBY_VISION_HEVC)) {
            codecType = DolbyVisionUtils.CODEC_TYPE_HEVC;
        } else if (mimeType.equalsIgnoreCase(MIMETYPE_DOLBY_VISION_AVC)) {
            codecType = DolbyVisionUtils.CODEC_TYPE_AVC;
        } else if (mimeType.equalsIgnoreCase(MIMETYPE_DOLBY_VISION_AV1)) {
            codecType = DolbyVisionUtils.CODEC_TYPE_AV1;
        }
        return codecType;
    }

    public static boolean isDolbyVisionMimeType(String mimeType) {
        int codecType = getCodecType(mimeType);
        return codecType != CODEC_TYPE_UNKNOWN;
    }

    public static void setDolbyVisionMimeType(int codecType, MediaFormat format) {
        if (codecType != DolbyVisionUtils.CODEC_TYPE_UNKNOWN) {
            switch (codecType) {
                case DolbyVisionUtils.CODEC_TYPE_HEVC:
                    format.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_VIDEO_HEVC);
                    break;
                case DolbyVisionUtils.CODEC_TYPE_AVC:
                    format.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_VIDEO_AVC);
                    break;
                case DolbyVisionUtils.CODEC_TYPE_AV1:
                    format.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_VIDEO_AV1);
                    break;
                default:
                    break;
            }
        }
    }
}
