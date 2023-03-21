/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.demo.utils;

import android.content.Context;
import android.widget.Toast;

public class ToastUtils {

    private static Toast sToast;

    public static void showToast(Context context, String msg) {
        if (sToast == null) {
            sToast = Toast.makeText(context, msg, Toast.LENGTH_SHORT);
        } else {
            sToast.cancel();
            sToast.setText(msg);
            sToast.setDuration(Toast.LENGTH_SHORT);
        }
        sToast.show();
    }
}
