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
 * Call back event mask
 */
public class EventMask {

    public static final int EVENT_TYPE_USERDATA_AFD_MASK = 1 << 0;

    public static final int EVENT_TYPE_USERDATA_CC_MASK = 1 << 1;

    public static final int EVENT_TYPE_DATA_LOSS_MASK = 1 << 2;

    public static final int EVENT_TYPE_DATA_RESUME_MASK = 1 << 3;
}
