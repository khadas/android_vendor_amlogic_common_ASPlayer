package com.amlogic.asplayer.core;

import android.os.Bundle;


import static com.amlogic.asplayer.core.VideoOutputPathV3.PARAMS_TRICK_NONE;
import static com.amlogic.asplayer.core.VideoOutputPathV3.KEY_TRICK_MODE;
import static com.amlogic.asplayer.core.VideoOutputPathV3.TRICK_MODE_SMOOTH;


class RendererTrickSmoothV3 extends Renderer {

    private final Bundle mParamsTrickSmooth;
    private boolean mMute = false;

    RendererTrickSmoothV3(int id, RendererScheduler rendererScheduler) {
        super(id, rendererScheduler);
        mParamsTrickSmooth = new Bundle();
        mParamsTrickSmooth.putInt(KEY_TRICK_MODE, TRICK_MODE_SMOOTH);
    }

    @Override
    protected String getName() {
        return "RendererTrickSmoothV3";
    }

    @Override
    void setSpeed(Renderer previousRenderer, double speed) {
        ASPlayerLog.i("%s speed:%f->%f", getTag(), mSpeed, speed);
        super.setSpeed(previousRenderer, speed);

        mMute = mAudioOutputPath.isMute();
        mAudioOutputPath.setMuted(true);

        // configure video for smooth trick mode
        mVideoOutputPath.setTrickModeSpeed(speed);

        mParamsTrickSmooth.putInt(VideoOutputPathV3.KEY_TRICK_SPEED, (int)(speed * 1000));
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
                mAudioOutputPath.setMuted(mMute);
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

    @Override
    void setWorkMode(int workMode) {
    }
}
