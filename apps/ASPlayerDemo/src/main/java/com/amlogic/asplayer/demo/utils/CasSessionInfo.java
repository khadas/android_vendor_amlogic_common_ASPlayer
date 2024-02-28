/*
 * Copyright (c) 2024 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */

package com.amlogic.asplayer.demo.utils;

public class CasSessionInfo {

    public static final byte[] GOOGLE_TEST_PRIVATE_DATA = {
        (byte) 0x0a, (byte) 0x08, (byte) 0x77, (byte) 0x69, (byte) 0x64, (byte) 0x65,
        (byte) 0x76, (byte) 0x69, (byte) 0x6e, (byte) 0x65, (byte) 0x12, (byte) 0x08,
        (byte) 0x32, (byte) 0x31, (byte) 0x31, (byte) 0x34, (byte) 0x30, (byte) 0x38,
        (byte) 0x34, (byte) 0x34
    };

    private int mEcmPid;

    private int mSessionUsage;

    private int mScramblingMode;

    private boolean mIsProgramLevel;

    private byte[] mPrivateData;

    private int[] mScrambledEsPids;

    private CasSessionInfo() {}

    public int getEcmPid() {
        return mEcmPid;
    }

    private void setEcmPid(int pid) {
        mEcmPid = pid;
    }

    public int getSessionUsage() {
        return mSessionUsage;
    }

    private void setSessionUsage(int usage) {
        mSessionUsage = usage;
    }

    public int getScramblingMode() {
        return mSessionUsage;
    }

    private void setScramblingMode(int mode) {
        mScramblingMode = mode;
    }

    public boolean getIsProgramLevel() {
        return mIsProgramLevel;
    }

    private void setIsProgramLevel(boolean isProgramLevel) {
        mIsProgramLevel = isProgramLevel;
    }

    public byte[] getPrivateData() {
        return mPrivateData;
    }

    private void setPrivateData(byte[] data) {
        mPrivateData = data;
    }

    public int[] getScrambledEsPids() {
        return mScrambledEsPids;
    }

    private void setScrambledEsPids(int[] Pids) {
        mScrambledEsPids = Pids;
    }

    public static class Builder {

        private int mEcmPid = 0x1FFF;

        private int mSessionUsage = 0;//LIVE

        private int mScramblingMode = 0;//RESERVED

        private boolean mIsProgramLevel = false;

        private byte mPrivateData[] = CasSessionInfo.GOOGLE_TEST_PRIVATE_DATA;

        private int mScrambledEsPids[] = {0x1FFF, 0x1FFF};

        public Builder() {
        }

        public Builder setEcmPid(int pid) {
            this.mEcmPid = pid;
            return this;
        }

        public Builder setSessionUsage(int usage) {
            this.mSessionUsage = usage;
            return this;
        }

        public Builder setScramblingMode(int mode) {
            this.mScramblingMode = mode;
            return this;
        }

        public Builder setIsProgramLevel(boolean isProgramLevel) {
            this.mIsProgramLevel = isProgramLevel;
            return this;
        }

        public Builder setPrivateData(byte[] data) {
            this.mPrivateData = data;
            return this;
        }

        public Builder setScrambledEsPids(int[] pids) {
            this.mScrambledEsPids = pids;
            return this;
        }

        public CasSessionInfo build() {
            CasSessionInfo info = new CasSessionInfo();
            info.setEcmPid(mEcmPid);
            info.setSessionUsage(mSessionUsage);
            info.setScramblingMode(mScramblingMode);
            info.setIsProgramLevel(mIsProgramLevel);
            info.setPrivateData(mPrivateData);
            info.setScrambledEsPids(mScrambledEsPids);

            return info;
        }
    }
}
