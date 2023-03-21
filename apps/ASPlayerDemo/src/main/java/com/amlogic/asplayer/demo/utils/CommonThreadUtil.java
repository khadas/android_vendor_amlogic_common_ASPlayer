/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.demo.utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CommonThreadUtil {

    private static ExecutorService pool;

    private synchronized static void initThreadPool() {
        if (pool == null) {
            pool = Executors.newFixedThreadPool(1);
        }
    }

    public static void post(Runnable runnable) {
        initThreadPool();
        pool.execute(runnable);
    }
}
