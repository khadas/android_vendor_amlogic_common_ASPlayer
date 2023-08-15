/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.core.utils;

import com.amlogic.asplayer.core.ASPlayerLog;

public class StringUtils {

    public static void dumpBytes(byte[] buffer) {
        if (buffer == null) return;

        dumpBytes(buffer, 0, buffer.length);
    }

    public static void dumpBytes(byte[] buffer, int offset, int len) {
        dumpBytes(null, buffer, offset, len);
    }

    public static void dumpBytes(String msg, byte[] buffer) {
        dumpBytes(msg, buffer, 0, buffer.length);
    }

    public static void dumpBytes(String msg, byte[] buffer, int offset, int len) {
        if (buffer == null) return;

        StringBuilder sb = new StringBuilder();
        for (int i = offset; i < (len + offset); i++) {
            sb.append(String.format(" %02x", buffer[i]));
        }
        if (msg != null) {
            ASPlayerLog.i("%s dumpBytes: %s", msg, sb.toString());
        } else {
            ASPlayerLog.i("dumpBytes: %s", sb.toString());
        }
    }
}
