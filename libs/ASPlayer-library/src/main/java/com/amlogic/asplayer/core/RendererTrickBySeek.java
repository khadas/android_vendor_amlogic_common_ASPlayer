package com.amlogic.asplayer.core;

import android.os.SystemClock;


class RendererTrickBySeek extends Renderer {

    private static final int BOUNDS_MARGIN_US = 2000000;

    private static final int DELAY_FOR_FLUSH_MS = 40;

    private long mOriginPositionUs;
    private long mOriginPositionWhenMs;

    private long mRequestedPositionWhenMs;

    private long mLastDisplayedPositionUs;
    private boolean mLastDisplayedPositionSet;

    private long mPendingNextPositionTimeMs;

    private final int mId;

    RendererTrickBySeek(int id, RendererScheduler rendererScheduler) {
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

        mVideoOutputPath.flush();
    }

    @Override
    void setSpeed(Renderer previousRenderer, double speed) {
        ASPlayerLog.i("RendererTrickBySeek-%d speed:%f", mId, speed);
        super.setSpeed(previousRenderer, speed);

        // set current origin
        mOriginPositionUs = mPositionHandler.getPositionUs();
        mOriginPositionWhenMs = SystemClock.elapsedRealtime();

        // set last position
        mLastDisplayedPositionSet = false;
        mLastDisplayedPositionUs = 0;

        // set next position
        mRequestedPositionWhenMs = mOriginPositionWhenMs;
        mRequestedPositionUs = mOriginPositionUs;
        mRequestedPositionSet = true;

        //
        mPendingNextPositionTimeMs = 0;

        // stop audio
        if (mAudioOutputPath.hasMediaFormat()) {
            // release or flush ?
            mAudioOutputPath.reset();
        }

        // configure video for trick mode
        mVideoOutputPath.setTrickModeSpeed(speed);

        // deactivate tunneling mode
        mVideoOutputPath.setTunneledPlayback(false);
        mAudioOutputPath.setTunneledPlayback(false);
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

    @Override
    protected void reset(int reason) {
    }

    @Override
    protected void pumpFeederData() {
        mVideoOutputPath.pushInputBuffer();
    }

    @Override
    long doSomeWork() {
        long delayUs = 10000;

        if (hasNextPositionPending()) {
            if ((SystemClock.elapsedRealtime() - mPendingNextPositionTimeMs) < 0)
                return 10000;
            else
                applyPendingNextPosition();
        }

        // inject
        handleFeeding();
        mVideoOutputPath.pushInputBuffer();

        // check decoders
        mVideoOutputPath.checkErrors();

        // render
        boolean needRender =
                (mSpeed != 0) ||
                        (!mVideoOutputPath.isFirstFrameDisplayed());
        if (needRender && mVideoOutputPath.hasOutputBuffers()) {
            // elapsed since last request
            long elapsedSinceSetPositionMs =
                    SystemClock.elapsedRealtime() - mRequestedPositionWhenMs;
//            if (mRequestedPositionWhenMs != 0 && !mVideoOutputPath.isFirstFrameDisplayed())
//                mDoctor.notifyTrickBySeekDecodingTime(elapsedSinceSetPositionMs);

            // check if frame to display is in right sequence order
            long positionUs = mVideoOutputPath.getNextOutputTimestamp();
            boolean skipFrame = false;
            if (mSpeed < 0 && mLastDisplayedPositionSet) {
                skipFrame = (positionUs - mLastDisplayedPositionUs) > 0;
            } else if (mSpeed > 0 && mLastDisplayedPositionSet) {
                skipFrame = (positionUs - mLastDisplayedPositionUs) < 0;
            }
            mLastDisplayedPositionUs = positionUs;
            mLastDisplayedPositionSet = true;

            // render
            if (!skipFrame)
                mVideoOutputPath.renderOneFrame();

            // next position
            boolean isLimitOfStreamReached = false;
            long currentPositionUs = mPositionHandler.getPositionUs();
            if (mSpeed < 0)
                isLimitOfStreamReached = true;
            if (mSpeed > 0)
                isLimitOfStreamReached = true;
            if (mSpeed != 0 && !isLimitOfStreamReached) {
                armNextRequestedPosition();
                delayUs = 0;
            } else {
                delayUs = 40000;
            }
        } else {
            // If the jump is not big enough, we might miss a reference frame, and we need to do
            // a bigger jump.
            // May happen for a rewind at the end of stream for instance
            long elapsedSinceSetPositionMs =
                    SystemClock.elapsedRealtime() - mRequestedPositionWhenMs;

            if (mSpeed != 0 && elapsedSinceSetPositionMs > 1000) {
                armNextRequestedPosition();
                delayUs = 0;
            }
        }

        return delayUs;
    }

    private boolean hasNextPositionPending() {
        return (mPendingNextPositionTimeMs != 0);
    }

    private void applyPendingNextPosition() {
        mPendingNextPositionTimeMs = 0;
        mRequestedPositionWhenMs = SystemClock.elapsedRealtime();
        mRequestedPositionUs = mOriginPositionUs +
                (long) ((SystemClock.elapsedRealtime() - mOriginPositionWhenMs) * 1000. * mSpeed) -
                1000000;
        mRequestedPositionSet = true;

        mVideoOutputPath.flush();
    }

    private void armNextRequestedPosition() {
        mPendingNextPositionTimeMs = SystemClock.elapsedRealtime() + DELAY_FOR_FLUSH_MS;
    }
}
