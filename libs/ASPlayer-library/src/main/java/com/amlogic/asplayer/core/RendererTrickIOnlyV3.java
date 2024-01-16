/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.core;

import static com.amlogic.asplayer.core.Constant.SPEED_DIFF_THRESHOLD;
import static com.amlogic.asplayer.core.VideoOutputPathV3.KEY_TRICK_MODE;
import static com.amlogic.asplayer.core.VideoOutputPathV3.KEY_TRICK_SPEED;
import static com.amlogic.asplayer.core.VideoOutputPathV3.PARAMS_TRICK_NONE;
import static com.amlogic.asplayer.core.VideoOutputPathV3.TRICK_MODE_IONLY;

import android.os.Bundle;

import com.amlogic.asplayer.core.utils.MathUtils;

public class RendererTrickIOnlyV3 extends Renderer {

    private final Bundle mParamsTrickIOnly;

    RendererTrickIOnlyV3(int id, RendererScheduler rendererScheduler) {
        super(id, rendererScheduler);

        mParamsTrickIOnly = new Bundle();
        mParamsTrickIOnly.putInt(KEY_TRICK_MODE, TRICK_MODE_IONLY);
        mParamsTrickIOnly.putInt(KEY_TRICK_SPEED, 0);
    }


    @Override
    protected String getName() {
        return "RendererTrickIOnlyV3";
    }

    @Override
    void setSpeed(Renderer previousRenderer, double speed) {
        ASPlayerLog.i("%s setSpeed: %f", getTag(), speed);
        super.setSpeed(previousRenderer, speed);

        // reset audio
        if (mAudioOutputPath != null) {
            mAudioOutputPath.resetForSeek();
        }

        // configure video for trick mode
        mVideoOutputPath.setTrickModeSpeed(speed);

        mParamsTrickIOnly.putInt(VideoOutputPathV3.KEY_TRICK_SPEED, (int)(speed * 1000));
        mVideoOutputPath.setParameters(mParamsTrickIOnly);
    }

    @Override
    long doSomeWork() {
        long delayUs = 10000;

        mVideoOutputPath.pushInputBuffer();

        // check decoders
        mVideoOutputPath.checkErrors();

        if (!MathUtils.equals(mSpeed, 0, SPEED_DIFF_THRESHOLD)
                && mVideoOutputPath.isFirstFrameDisplayed()) {
            delayUs = 0;
        }

        return delayUs;
    }

    @Override
    void reset(int reason) {
        switch (reason) {
            case RESET_REASON_NEW_POSITION:
                mVideoOutputPath.flush();
                break;
            case RESET_REASON_RENDERER_CHANGED:
                mVideoOutputPath.setParameters(PARAMS_TRICK_NONE);
                break;
        }
    }

    @Override
    protected void pumpFeederData() {
        mVideoOutputPath.pushInputBuffer();
    }

    @Override
    void setWorkMode(int workMode) {

    }
}
