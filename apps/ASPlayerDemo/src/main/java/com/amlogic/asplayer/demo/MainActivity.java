/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.demo;


import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import com.amlogic.asplayer.demo.live.LiveTvPlayerSettingActivity;
import com.amlogic.asplayer.demo.local.LocalTvPlayerSettingActivity;


public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        initViews();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions = new String[] {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
            requestPermissions(permissions, 1);
        }
    }

    private void initViews() {
        findViewById(R.id.btn_local_tv_player).setOnClickListener((v) -> {
            gotoActivity(LocalTvPlayerSettingActivity.class);
        });
        findViewById(R.id.btn_live_tv_player).setOnClickListener((v) -> {
            gotoActivity(LiveTvPlayerSettingActivity.class);
        });
    }

    private void gotoActivity(Class cls) {
        Intent intent = new Intent(MainActivity.this, cls);
        startActivity(intent);
    }
}