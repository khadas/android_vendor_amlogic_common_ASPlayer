/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.core;


import static com.amlogic.asplayer.core.VideoOutputPathV3.PARAMS_TRICK_BY_SEEK;
import static com.amlogic.asplayer.core.VideoOutputPathV3.PARAMS_TRICK_NONE;


class RendererTrickBySeekV3 extends Renderer {
    private static final String TAG = "RendererTrickBySeekV3";

    private final int mId;

    RendererTrickBySeekV3(int id, RendererScheduler rendererScheduler) {
        super(rendererScheduler);
        mId = id;
    }

    @Override
    void setSpeed(Renderer previousRenderer, double speed) {
        ASPlayerLog.i("RendererTrickBySeekV3-%d setSpeed: %f", mId, speed);
        super.setSpeed(previousRenderer, speed);

        mRequestedPositionSet = true;

        // stop audio
        if (mAudioOutputPath != null) {
            // release or flush ?
            mAudioOutputPath.reset();
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

        if (mSpeed != 0 && mVideoOutputPath.isFirstFrameDisplayed()) {
            delayUs = 0;
        }

        return delayUs;
    }
}
