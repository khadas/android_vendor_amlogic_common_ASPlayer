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
import static com.amlogic.asplayer.core.VideoOutputPathV3.PARAMS_TRICK_BY_SEEK;
import static com.amlogic.asplayer.core.VideoOutputPathV3.PARAMS_TRICK_NONE;

import com.amlogic.asplayer.core.utils.MathUtils;


class RendererTrickBySeekV3 extends Renderer {
    private static final String TAG = "RendererTrickBySeekV3";

    RendererTrickBySeekV3(int id, RendererScheduler rendererScheduler) {
        super(id, rendererScheduler);
    }

    @Override
    protected String getName() {
        return "RendererTrickBySeekV3";
    }

    @Override
    void setSpeed(Renderer previousRenderer, double speed) {
        ASPlayerLog.i("%s setSpeed: %f", getTag(), speed);
        super.setSpeed(previousRenderer, speed);

        mRequestedPositionSet = true;

        // reset audio
        if (mAudioOutputPath != null) {
            mAudioOutputPath.resetForSeek();
        }

        // configure video for trick mode
        mVideoOutputPath.setTrickModeSpeed(speed);
        mVideoOutputPath.setParameters(PARAMS_TRICK_BY_SEEK);
    }

    @Override
    protected void reset(int reason) {
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
    void setWorkMode(int workMode) {
    }
}
