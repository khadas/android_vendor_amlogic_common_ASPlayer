/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.api;

public final class PlaybackControl {

    /**
     * Video mute mode
     */
    public static final class VideoMute {
        public static final int UN_MUTE = 0;        // un mute video (default)
        public static final int MUTE = 1;           // mute video
    }

    /**
     * Screen color
     */
    public static final class ScreenColor {

        public static final int MODE_ONCE_TRANSITION = 0;   // color for transition
        public static final int MODE_ONCE_SOLID = 1;        // solid color once
        public static final int MODE_ALWAYS = 2;            // solid color always
        public static final int MODE_ALWAYS_CANCEL = 3;     // cancel solid color always

        public static final int BLACK = 0;          // black screen (default)
        public static final int BLUE = 1;           // blue screen
        public static final int GREEN = 2;          // green screen
    }

    /**
     * Transition mode before transition to next program
     */
    public static final class TransitionModeBefore {
        public static final int BLACK = 0;          // black screen (default)
        public static final int LAST_IMAGE = 1;     // keep last frame
    }

    /**
     * Transition mode after transition to next program
     */
    public static final class TransitionModeAfter {
        public static final int PREROLL_FROM_FIRST_IMAGE = 0; // show first image before sync (default)
        public static final int WAIT_UNTIL_SYNC = 1;          // wait until sync
    }
}
