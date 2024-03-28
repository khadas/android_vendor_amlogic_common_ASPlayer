/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.api;

/**
 * ASPlayer version info
 */
public class Version {

    private int mMajorVersion;

    private int mMinorVersion;

    public Version(int majorVersion, int minorVersion) {
        mMajorVersion = majorVersion;
        mMinorVersion = minorVersion;
    }

    public int getMajorVersion() {
        return mMajorVersion;
    }

    public int getMinorVersion() {
        return mMinorVersion;
    }
}
