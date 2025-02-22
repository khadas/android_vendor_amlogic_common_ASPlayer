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
import android.text.TextUtils;

import com.amlogic.asplayer.demo.Constant;
import com.amlogic.asplayer.demo.widget.ProgramInputSettingView;

public class TvPlayerBundleHelper {

    public static Bundle getTvPlayerStartBundle(ProgramInputSettingView.ProgramInfo programInfo) {
        Bundle bundle = new Bundle();

        bundle.putInt(Constant.EXTRA_TUNER_HAL_VERSION, programInfo.mTunerHalVersion);

        bundle.putInt(Constant.EXTRA_VIDEO_PID, programInfo.mVideoPid);
        bundle.putInt(Constant.EXTRA_AUDIO_PID, programInfo.mAudioPid);

        bundle.putInt(Constant.EXTRA_CAS_SYSTEM_ID, programInfo.mCasSystemId);
        bundle.putInt(Constant.EXTRA_CAS_ECM_PID, programInfo.mCasEcmPid);
        bundle.putInt(Constant.EXTRA_CAS_SCRAMBLING_MODE, programInfo.mCasScramblingMode);

        if (programInfo.mTunerHalVersion == Constant.TUNER_HAL_VERSION_1_0) {
            bundle.putString(Constant.EXTRA_VIDEO_MIME_TYPE, programInfo.mVideoMimeType);
            bundle.putString(Constant.EXTRA_AUDIO_MIME_TYPE, programInfo.mAudioMimeType);
        }

        if (programInfo.mTunerHalVersion == Constant.TUNER_HAL_VERSION_1_1) {
            bundle.putString(Constant.EXTRA_VIDEO_STREAM_TYPE, programInfo.mVideoStreamType);
            bundle.putString(Constant.EXTRA_AUDIO_STREAM_TYPE, programInfo.mAudioStreamType);

            String videoMimeType = programInfo.mVideoMimeType;
           if (TextUtils.equals("video/dolby-vision-hevc", videoMimeType)
               || TextUtils.equals("video/dolby-vision-avc", videoMimeType)) {
                bundle.putString(Constant.EXTRA_VIDEO_MIME_TYPE, videoMimeType);
            } else {
                videoMimeType = TunerHelper.getMimeTypeFromVideoStreamType(programInfo.mVideoStreamType);
                bundle.putString(Constant.EXTRA_VIDEO_MIME_TYPE, videoMimeType);
            }

            String audioMimeType = programInfo.mAudioMimeType;
            if (TextUtils.isEmpty(audioMimeType)) {
                audioMimeType = TunerHelper.getMimeTypeFromAudioStreamType(programInfo.mAudioStreamType);
            }
            bundle.putString(Constant.EXTRA_AUDIO_MIME_TYPE, audioMimeType);
        }

        return bundle;
    }
}
