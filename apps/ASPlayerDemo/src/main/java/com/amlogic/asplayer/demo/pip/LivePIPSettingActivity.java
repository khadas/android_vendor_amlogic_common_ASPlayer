/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.demo.pip;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
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

public class LivePIPSettingActivity extends Activity {

    private static final String TAG = Constant.LOG_TAG;

    private EditText mEditFrequency;
    private ProgramInputSettingView mProgramInputView;
    private ProgramInputSettingView mPipProgramInputView;

    private Button mBtnStartPlay;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_live_pip_tv_player_setting);

        initViews();

        mProgramInputView.setTunerHalVersion(ProgramInputSettingView.TunerHalVersion.VERSION_1_0);
        mPipProgramInputView.setTunerHalVersion(ProgramInputSettingView.TunerHalVersion.VERSION_1_0);
        mPipProgramInputView.showTunerHalLayout(false);
    }

    private void initViews() {
        mEditFrequency = findViewById(R.id.edit_frequency);
        mEditFrequency.setHint(String.valueOf(Constant.DEFAULT_DVBT_FREQUENCY));

        mProgramInputView = findViewById(R.id.program_setting_view);
        mPipProgramInputView = findViewById(R.id.pip_program_setting_view);

        mBtnStartPlay = findViewById(R.id.btn_fullscreen_play);
        mBtnStartPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gotoVideoPlayActivity();
            }
        });

        mBtnStartPlay.requestFocus();
    }

    private void gotoVideoPlayActivity() {
        int frequency = ViewUtils.getInputNumber(mEditFrequency, 0);

        if (frequency <= 0) {
            Toast.makeText(this, R.string.tips_no_frequency, Toast.LENGTH_SHORT).show();
            return;
        }

        ProgramInputSettingView.ProgramInfo programInfo = mProgramInputView.getProgramInfo();
        if (programInfo == null) {
            Toast.makeText(this, R.string.tips_check_input, Toast.LENGTH_SHORT).show();
            return;
        }

        ProgramInputSettingView.ProgramInfo pipProgramInfo = mPipProgramInputView.getProgramInfo();
        if (pipProgramInfo == null) {
            Toast.makeText(this, R.string.tips_check_input, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(LivePIPSettingActivity.this, LivePIPPlayerActivity.class);

        // program info bundle
        Bundle programBundle = TvPlayerBundleHelper.getTvPlayerStartBundle(programInfo);
        intent.putExtra(Constant.EXTRA_PROGRAM_BUNDLE, programBundle);

        // DVB-T info bundle
        Bundle dvbtBundle = new Bundle();
        dvbtBundle.putInt(Constant.EXTRA_FRONTEND_FREQUENCY, frequency * 1000000);
        intent.putExtra(Constant.EXTRA_FRONTEND_BUNDLE, dvbtBundle);

        // pip program info bundle
        Bundle pipProgramBundle = TvPlayerBundleHelper.getTvPlayerStartBundle(pipProgramInfo);
        intent.putExtra(Constant.EXTRA_PIP_PROGRAM_BUNDLE, pipProgramBundle);

        startActivity(intent);
    }
}
