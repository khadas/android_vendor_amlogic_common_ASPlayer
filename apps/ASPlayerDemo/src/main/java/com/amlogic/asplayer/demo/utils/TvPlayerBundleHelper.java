/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.demo.utils;

import android.os.Bundle;

import com.amlogic.asplayer.demo.Constant;
import com.amlogic.asplayer.demo.widget.ProgramInputSettingView;

public class TvPlayerBundleHelper {

    public static Bundle getTvPlayerStartBundle(ProgramInputSettingView.ProgramInfo programInfo) {
        Bundle bundle = new Bundle();

        bundle.putInt(Constant.EXTRA_TUNER_HAL_VERSION, programInfo.mTunerHalVersion);

        bundle.putInt(Constant.EXTRA_VIDEO_PID, programInfo.mVideoPid);
        bundle.putInt(Constant.EXTRA_AUDIO_PID, programInfo.mAudioPid);

        if (programInfo.mTunerHalVersion == Constant.TUNER_HAL_VERSION_1_0) {
            bundle.putString(Constant.EXTRA_VIDEO_MIME_TYPE, programInfo.mVideoMimeType);
            bundle.putString(Constant.EXTRA_AUDIO_MIME_TYPE, programInfo.mAudioMimeType);
        }

        if (programInfo.mTunerHalVersion == Constant.TUNER_HAL_VERSION_1_1) {
            bundle.putString(Constant.EXTRA_VIDEO_STREAM_TYPE, programInfo.mVideoStreamType);
            bundle.putString(Constant.EXTRA_AUDIO_STREAM_TYPE, programInfo.mAudioStreamType);

            String videoMimeType = TunerHelper.getMimeTypeFromVideoStreamType(programInfo.mVideoStreamType);
            bundle.putString(Constant.EXTRA_VIDEO_MIME_TYPE, videoMimeType);

            String audioMimeType = TunerHelper.getMimeTypeFromAudioStreamType(programInfo.mAudioStreamType);
            bundle.putString(Constant.EXTRA_AUDIO_MIME_TYPE, audioMimeType);
        }

        return bundle;
    }
}
