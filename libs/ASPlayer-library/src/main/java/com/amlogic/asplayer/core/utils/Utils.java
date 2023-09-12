/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.core.utils;

import com.amlogic.asplayer.core.Constant;

public class Utils {

    public static int getSyncInstanceIdByAvSyncId(int avSyncHwId) {
        if (avSyncHwId < 0) {
            return Constant.INVALID_SYNC_INSTANCE_ID;
        }

        return avSyncHwId & 0x00FF;
    }
}
