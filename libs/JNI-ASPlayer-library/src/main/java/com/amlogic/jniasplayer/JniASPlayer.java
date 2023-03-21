/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.jniasplayer;

import android.media.tv.tuner.Tuner;
import android.os.Looper;

import com.amlogic.asplayer.api.InitParams;
import com.amlogic.asplayer.api.ASPlayer;

public class JniASPlayer extends ASPlayer {

    private long mNativeContext;

    public JniASPlayer(InitParams initParams, Tuner tuner, Looper looper) {
        super(initParams, tuner, looper);
    }
}
