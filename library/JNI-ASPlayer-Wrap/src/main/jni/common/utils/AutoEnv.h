/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */

#ifndef JNI_ASPLAYER_WRAPPER_AUTOENV_H
#define JNI_ASPLAYER_WRAPPER_AUTOENV_H

#include <jni.h>

class AutoEnv {
public:
    static JNIEnv *GetJniEnv();

    static void setJavaVM(JavaVM *jvm);

private:
    static void DeferThreadDetach(JNIEnv *env);

private:
    static JavaVM *g_JavaVM;
};


#endif //JNI_ASPLAYER_WRAPPER_AUTOENV_H
