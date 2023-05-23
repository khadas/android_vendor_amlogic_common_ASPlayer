# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)

project("jniasplayer")

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

set(JNI_DIR ${CMAKE_SOURCE_DIR}/src/main/jni)

add_library( # Sets the name of the library.
        jniasplayer-jni

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        ${JNI_DIR}/JNIASPlayer.cpp
        ${JNI_DIR}/JniASPlayerJNI.cpp
        ${JNI_DIR}/NativeHelper.cpp
        ${JNI_DIR}/JniPlaybackListener.cpp
        ${JNI_DIR}/JniMediaFormat.cpp)

target_include_directories(
        jniasplayer-jni
        PUBLIC
        ${JNI_DIR}/include
        ${JNI_DIR}/common
)

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
        jniasplayer-jni
        ${log-lib} )
