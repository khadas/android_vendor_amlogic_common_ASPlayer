/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.core;

public class RendererTrickIOnly extends Renderer {

    RendererTrickIOnly(int id, RendererScheduler rendererScheduler) {
        super(id, rendererScheduler);
    }

    @Override
    protected String getName() {
        return "RendererTrickIOnly";
    }

    @Override
    long doSomeWork() {
        return 100000;
    }

    @Override
    void reset(int reason) {

    }

    @Override
    protected void pumpFeederData() {

    }

    @Override
    void setWorkMode(int workMode) {

    }
}
