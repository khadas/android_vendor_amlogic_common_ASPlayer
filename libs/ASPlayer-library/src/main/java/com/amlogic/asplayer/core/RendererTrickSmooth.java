package com.amlogic.asplayer.core;


class RendererTrickSmooth extends Renderer {

    private static final int END_OF_STREAM_MARGIN_US = 1000000;

    RendererTrickSmooth(int id, RendererScheduler rendererScheduler) {
        super(id, rendererScheduler);
    }

    @Override
    protected String getName() {
        return "RendererTrickSmooth";
    }

    @Override
    void setSpeed(Renderer previousRenderer, double speed) {
        ASPlayerLog.i("%s speed:%f->%f", getTag(), mSpeed, speed);
        super.setSpeed(previousRenderer, speed);

        // stop audio
        if (mAudioOutputPath.hasAudioFormat()) {
            mAudioOutputPath.flush();
        }

        // configure video for smooth trick mode
        mVideoOutputPath.setTrickModeSpeed(speed);

        // deactivate tunneling mode
        mVideoOutputPath.setTunneledPlayback(false);
        mAudioOutputPath.setTunneledPlayback(false);

        // need to reconfigure clock with next valid timestamp
        if (mVideoOutputPath.hasOutputBuffers()) {
            long videoPts = mVideoOutputPath.getNextOutputTimestamp();
            mVideoOutputPath.setSynchroOn(videoPts);
            mVideoOutputPath.setSpeed(mSpeed);
        } else {
            mVideoOutputPath.mClock.reset();
        }
    }

    @Override
    protected void pumpFeederData() {
        mVideoOutputPath.pushInputBuffer();
    }

    @Override
    protected void reset(int reason) {
        if (reason == RESET_REASON_NEW_POSITION)
            mVideoOutputPath.flush();
    }

    @Override
    long doSomeWork() {
        long delayUs = 10000;

        // inject
        handleFeeding();
        mVideoOutputPath.pushInputBuffer();

        // check decoders
        mVideoOutputPath.checkErrors();

        // check speed
        boolean needSetSpeed = true;
        needSetSpeed &= mVideoOutputPath.hasOutputBuffers();
        needSetSpeed &= !mVideoOutputPath.mClock.isStarted();
        if (needSetSpeed) {
            long videoPts = mVideoOutputPath.getNextOutputTimestamp();
            mVideoOutputPath.setSynchroOn(videoPts);
            mVideoOutputPath.setSpeed(mSpeed);
        }

        // render
        mVideoOutputPath.render();

        return delayUs;
    }

    @Override
    void setWorkMode(int workMode) {
    }
}
