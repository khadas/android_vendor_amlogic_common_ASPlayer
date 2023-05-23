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

if (NOT USE_SYSTEM_SO)
        add_library(libjniasplayer SHARED IMPORTED)
        set_target_properties(libjniasplayer
                PROPERTIES
                IMPORTED_LOCATION
                ${JNI_ASPLAYER_SO_DIR}/${buildTypeLower}/${ANDROID_ABI}/libjniasplayer-jni.so)
        include_directories(${JNI_ASPLAYER_INCLUDE_DIR})

        message(STATUS ANDROID_ABI=${ANDROID_ABI})
        message(STATUS "Add libjniasplayer.so")
else()
        message(STATUS "Don't Add libjniasplayer.so")
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
