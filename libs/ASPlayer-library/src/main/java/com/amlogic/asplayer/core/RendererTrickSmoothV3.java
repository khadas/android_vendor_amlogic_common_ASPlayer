package com.amlogic.asplayer.core;

import android.os.Bundle;


import static com.amlogic.asplayer.core.VideoOutputPathV3.PARAMS_TRICK_NONE;
import static com.amlogic.asplayer.core.VideoOutputPathV3.PARAM_TRICK_MODE;
import static com.amlogic.asplayer.core.VideoOutputPathV3.TRICK_MODE_SMOOTH;


class RendererTrickSmoothV3 extends Renderer {

    private final Bundle mParamsTrickSmooth;
    private int mId;

    RendererTrickSmoothV3(int id, RendererScheduler rendererScheduler) {
        super(rendererScheduler);
        mId = id;
        mParamsTrickSmooth = new Bundle();
        mParamsTrickSmooth.putInt(PARAM_TRICK_MODE, TRICK_MODE_SMOOTH);
    }

    @Override
    void setSpeed(Renderer previousRenderer, double speed) {
        ASPlayerLog.i("RendererTrickSmoothV3-%d speed:%f->%f", mId, mSpeed, speed);
        super.setSpeed(previousRenderer, speed);

        mAudioOutputPath.setMuted(true);

        // configure video for smooth trick mode
        mVideoOutputPath.setTrickModeSpeed(speed);

        mParamsTrickSmooth.putInt(VideoOutputPathV3.PARAM_TRICK_SPEED, (int)(speed * 1000));
        mVideoOutputPath.setParameters(mParamsTrickSmooth);
        mAudioOutputPath.setSpeed(1.0f);
    }

    @Override
    protected void pumpFeederData() {
        mVideoOutputPath.pushInputBuffer();
    }

    @Override
    protected void reset(int reason) {
        switch (reason) {
            case RESET_REASON_NEW_POSITION:
                mVideoOutputPath.flush();
                break;
            case RESET_REASON_RENDERER_CHANGED:
                mAudioOutputPath.setMuted(false);
                mVideoOutputPath.setParameters(PARAMS_TRICK_NONE);
                break;
        }
    }

    @Override
    long doSomeWork() {
        long delayUs = 10000;

        // inject
        handleFeeding();
        mVideoOutputPath.pushInputBuffer();
        mAudioOutputPath.pushInputBuffer();

        // check decoders
        mVideoOutputPath.checkErrors();
        return delayUs;
    }
}
