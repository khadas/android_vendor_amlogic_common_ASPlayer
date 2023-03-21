package com.amlogic.asplayer.core;


class RendererStarter extends Renderer {

    RendererStarter(RendererScheduler rendererScheduler) {
        super(rendererScheduler);
    }

    @Override
    void setSpeed(Renderer previousRenderer, double speed)  {
        super.setSpeed(previousRenderer, speed);
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

        handleFeeding();

        return delayUs;
    }
}
