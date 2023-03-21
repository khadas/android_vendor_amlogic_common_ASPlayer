package com.amlogic.asplayer.core;

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
