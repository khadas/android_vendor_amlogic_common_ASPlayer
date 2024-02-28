/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.demo.utils;

import android.text.TextUtils;
import android.widget.EditText;

public class ViewUtils {

    public static String getInputText(EditText editText) {
        String str = editText.getText().toString().trim();
        if (TextUtils.isEmpty(str)) {
            str = editText.getHint().toString().trim();
        }
        return str;
    }

    public static int getInputOrHintNumber(EditText editText, int defaultValue) {
        int number = defaultValue;
        try {
            String str = editText.getText().toString().trim();
            if (TextUtils.isEmpty(str)) {
                str = editText.getHint().toString().trim();
            }
            number = Integer.parseInt(str);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return number;
    }

    public static int getInputNumber(EditText editText, int defaultValue) {
        int number = defaultValue;
        try {
            String str = editText.getText().toString().trim();
            number = Integer.parseInt(str);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return number;
    }
}
