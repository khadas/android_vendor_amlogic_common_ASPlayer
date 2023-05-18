/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.demo.local;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.amlogic.asplayer.demo.Constant;
import com.amlogic.asplayer.demo.R;
import com.amlogic.asplayer.demo.utils.TvPlayerBundleHelper;
import com.amlogic.asplayer.demo.utils.ViewUtils;
import com.amlogic.asplayer.demo.widget.ProgramInputSettingView;


public class LocalTvPlayerSettingActivity extends Activity {

    private static final String TAG = Constant.LOG_TAG;

    private EditText mEditTsPath;
    private ProgramInputSettingView mProgramInputView;
    private Button mBtnStartPlay;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_tv_player_setting);

        initViews();
    }

    private void initViews() {
        mEditTsPath = findViewById(R.id.edit_ts_path);
        mEditTsPath.setHint(Constant.DEFAULT_TS_PATH);

        mProgramInputView = findViewById(R.id.program_setting_view);

        mBtnStartPlay = findViewById(R.id.btn_fullscreen_play);
        mBtnStartPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gotoVideoPlayActivity();
            }
        });

        mBtnStartPlay.requestFocus();
    }

    private String getLocalTsPath() {
        return ViewUtils.getInputText(mEditTsPath);
    }

    private void gotoVideoPlayActivity() {
        String tsPath = getLocalTsPath();

        if (TextUtils.isEmpty(tsPath)) {
            Toast.makeText(this, R.string.no_ts_path_tips, Toast.LENGTH_SHORT).show();
            return;
        }

        ProgramInputSettingView.ProgramInfo programInfo = mProgramInputView.getProgramInfo();
        if (programInfo == null) {
            Toast.makeText(this, R.string.tips_check_input, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(LocalTvPlayerSettingActivity.this, LocalTvPlayerActivity.class);
        intent.putExtra(Constant.EXTRA_TS_PATH, tsPath);

        Bundle programBundle = TvPlayerBundleHelper.getTvPlayerStartBundle(programInfo);
        intent.putExtra(Constant.EXTRA_PROGRAM_BUNDLE, programBundle);

        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
