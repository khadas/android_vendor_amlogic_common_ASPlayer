## For more information about using CMake with Android Studio, read the
## documentation: https://d.android.com/studio/projects/add-native-code.html
#
## Sets the minimum version of CMake required to build the native library.
#
cmake_minimum_required(VERSION 3.4.1)

project(jniasplayer-wrapper)
#
set(JNI_DIR ${CMAKE_SOURCE_DIR}/src/main/jni)

message(USE_SYSTEM_SO=${USE_SYSTEM_SO})

# JNI-ASPlayer Directories
message(JNI_ASPLAYER_INCLUDE_DIR=${JNI_ASPLAYER_INCLUDE_DIR})
message(JNI_ASPLAYER_SO_DIR=${JNI_ASPLAYER_SO_DIR})

string(TOLOWER ${CMAKE_BUILD_TYPE} buildTypeLower)

include_directories(${JNI_ASPLAYER_INCLUDE_DIR})

if (USE_SYSTEM_SO EQUAL 1)
        add_definitions(-DUSE_SYSTEM_SO=1)
        message("Don't Add libjniasplayer.so")
else()
        add_definitions(-DUSE_SYSTEM_SO=0)
        add_library(libjniasplayer SHARED IMPORTED)
        set_target_properties(libjniasplayer
                PROPERTIES
                IMPORTED_LOCATION
                ${JNI_ASPLAYER_SO_DIR}/${buildTypeLower}/${ANDROID_ABI}/libjniasplayer-jni.so)

        message(ANDROID_ABI=${ANDROID_ABI})
        message("Add libjniasplayer-jni.so")
endif()

add_library( # Sets the name of the library.
        jniasplayer-wrapper
        SHARED
        # Provides a relative path to your source file(s).
        ${JNI_DIR}/common/utils/AutoEnv.cpp
        ${JNI_DIR}/JniASPlayerWrapper.cpp
        ${JNI_DIR}/DynamicJniASPlayerWrapper.cpp
        ${JNI_DIR}/PlaybackListenerWrapper.cpp
        ${JNI_DIR}/ASPlayerJni.cpp
        ${JNI_DIR}/JniNativeLib.cpp
        ${JNI_DIR}/NativeHelper.cpp
        ${JNI_DIR}/native-lib.cpp)

find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)

if (USE_SYSTEM_SO)
        target_link_libraries( # Specifies the target library.
                jniasplayer-wrapper
                ${log-lib}
                android)
else()
        message(STATUS "Link local libjniasplayer-jni.so")
        target_link_libraries( # Specifies the target library.
                jniasplayer-wrapper
                libjniasplayer
                ${log-lib}
                android)
endif()
