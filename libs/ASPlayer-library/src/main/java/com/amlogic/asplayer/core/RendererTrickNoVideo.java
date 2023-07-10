package com.amlogic.asplayer.core;

import static com.amlogic.asplayer.core.VideoOutputPathV3.PARAMS_TRICK_BY_SEEK;

import android.os.SystemClock;


class RendererTrickNoVideo extends Renderer {

    private final int mId;

    RendererTrickNoVideo(int id, RendererScheduler rendererScheduler) {
        super(rendererScheduler);
        mId = id;
    }

    void setPositionUs(long positionUs) {
        super.setPositionUs(positionUs);
    }

    @Override
    void setSpeed(Renderer previousRenderer, double speed) {
        ASPlayerLog.i("speed:%f", speed);
        super.setSpeed(previousRenderer, speed);

        mRequestedPositionSet = true;

        // stop audio
        if (mAudioOutputPath.hasMediaFormat()) {
            mAudioOutputPath.reset();
        }

        // deactivate tunneling mode
        mVideoOutputPath.setTunneledPlayback(false);
        mAudioOutputPath.setTunneledPlayback(false);

        mVideoOutputPath.setTrickModeSpeed(speed);
        mVideoOutputPath.setParameters(PARAMS_TRICK_BY_SEEK);
    }

    @Override
    protected void reset(int reason) {
    }

    @Override
    protected void pumpFeederData() {
    }

    @Override
    long doSomeWork() {
        long delayUs = 10000;

        return delayUs;
    }

    @Override
    void setWorkMode(int workMode) {
    }
}
