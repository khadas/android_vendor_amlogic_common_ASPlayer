package com.amlogic.asplayer.core;

import android.os.SystemClock;


class RendererTrickNoVideo extends Renderer {

    private static final int BOUNDS_MARGIN_US = 2000000;

    private long mOriginPositionUs;
    private long mOriginPositionWhenMs;

    private long mRequestedPositionWhenMs;

    private final int mId;

    RendererTrickNoVideo(int id, RendererScheduler rendererScheduler) {
        super(rendererScheduler);
        mId = id;
    }

    void setPositionUs(long positionUs) {
        super.setPositionUs(positionUs);

        // set current origin
        mOriginPositionUs = positionUs;
        mOriginPositionWhenMs = SystemClock.elapsedRealtime();

        // set next position
        mRequestedPositionWhenMs = mOriginPositionWhenMs;
    }

    @Override
    void setSpeed(Renderer previousRenderer, double speed) {
        ASPlayerLog.i("speed:%f", speed);
        super.setSpeed(previousRenderer, speed);

        // set current origin
        mOriginPositionUs = mPositionHandler.getPositionUs();
        mOriginPositionWhenMs = SystemClock.elapsedRealtime();

        // set next position
        mRequestedPositionWhenMs = mOriginPositionWhenMs;
        mRequestedPositionUs = mOriginPositionUs;
        mRequestedPositionUs = Math.min(mPositionHandler.getEndPositionUs() - 1000000, mRequestedPositionUs);
        mRequestedPositionUs = Math.max(mPositionHandler.getStartPositionUs(), mRequestedPositionUs);
        mRequestedPositionSet = true;

        // stop audio
        if (mAudioOutputPath.hasMediaFormat()) {
            mAudioOutputPath.reset();
        }

        // deactivate tunneling mode
        mVideoOutputPath.setTunneledPlayback(false);
        mAudioOutputPath.setTunneledPlayback(false);
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

        // inject
        handleFeeding();

        // check if we must jump
        if ((SystemClock.elapsedRealtime() - mRequestedPositionWhenMs) < 200) {
            // update position
            long positionUs = mOriginPositionUs +
                    (long) ((SystemClock.elapsedRealtime() - mOriginPositionWhenMs) * 1000. * mSpeed) - 1000000;
            mPositionHandler.setPositionUs(positionUs);
        } else {
            // compute next position
            mRequestedPositionWhenMs = SystemClock.elapsedRealtime();
            mRequestedPositionUs = mOriginPositionUs +
                    (long) ((SystemClock.elapsedRealtime() - mOriginPositionWhenMs) * 1000. * mSpeed) -
                    1000000;
            mRequestedPositionSet = true;

            // freeze if bounds are reached
            long startPositionUs = mPositionHandler.getStartPositionUs();
            long endPositionUs = mPositionHandler.getEndPositionUs();
            if (mSpeed > 0 && mRequestedPositionUs > endPositionUs - BOUNDS_MARGIN_US) {
                freezeToPosition(endPositionUs - BOUNDS_MARGIN_US);
            } else if (mSpeed < 0 && mRequestedPositionUs < startPositionUs + BOUNDS_MARGIN_US) {
                freezeToPosition(startPositionUs);
            }
        }

        return delayUs;
    }

    private void freezeToPosition(long position) {
        ASPlayerLog.i("position:%d", position);

        // set current origin
        mOriginPositionUs = position;
        mOriginPositionWhenMs = SystemClock.elapsedRealtime();

        // set next position
        mRequestedPositionWhenMs = mOriginPositionWhenMs;
        mRequestedPositionUs = position;
        mRequestedPositionSet = true;
    }
}
