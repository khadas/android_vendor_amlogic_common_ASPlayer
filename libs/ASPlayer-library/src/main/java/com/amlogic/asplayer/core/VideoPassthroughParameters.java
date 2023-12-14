/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.core;

class VideoPassthroughParameters {

    public static class ScreenColorMode {
        /**
         * Set screen color once for transition
         */
        public static final int SCREEN_COLOR_MODE_ONCE_TRANSITION = 0;

        /**
         * Set screen color, screen will show video frame when new video frame came
         */
        public static final int SCREEN_COLOR_MODE_ONCE_SOLID = 1;

        /**
         * Set screen color and keep the color always
         */
        public static final int SCREEN_COLOR_MODE_ALWAYS = 2;

        /**
         * Used for canceling screen color always
         */
        public static final int SCREEN_COLOR_MODE_ALWAYS_CANCEL = 3;
    }
}
