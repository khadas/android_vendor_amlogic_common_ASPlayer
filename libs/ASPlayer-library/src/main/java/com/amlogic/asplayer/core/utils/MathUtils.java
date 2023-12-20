/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.core.utils;

public class MathUtils {

    public static boolean equals(double first, double second, double diffThreshold) {
        return Math.abs(first - second) < diffThreshold;
    }

    public static boolean equals(float first, float second, float diffThreshold) {
        return Math.abs(first - second) < diffThreshold;
    }
}
