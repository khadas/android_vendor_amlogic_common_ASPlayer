@echo off
setlocal enableDelayedExpansion

set default_version=1.0
set cur_dir=%~dp0

set version=%1
set output_dir=%2
set not_show_explorer=%3

set asplayer_src_dir=%cur_dir%..\..\..\ASPlayer\libs\ASPlayer-library
set jni_asplayer_src_dir=%cur_dir%..\..\..\ASPlayer\libs\JNI-ASPlayer-library


if "%version%" == "" (
    set version=%default_version%
)

set dateStr=%date:~0,4%.%date:~5,2%.%date:~8,2%
if "%output_dir%" == "" (
    set output_dir=%cur_dir%..\..\..\ASPlayer\asplayer_sdk\JniASPlayerSDK_!version!_!dateStr!
)

set dateStr=%date:~0,4%.%date:~5,2%.%date:~8,2%


rem clean ASPlayer-library module
call gradlew :ASPlayer-library:clean
if not %errorlevel% == 0 (
    echo "CLEAN ASPlayer-library FAILED"
    exit /B 1
)

rem clean JNI-ASPlayer-library module
call gradlew :JNI-ASPlayer-library:clean
if not %errorlevel% == 0 (
    echo "CLEAN JNI-ASPlayer-library FAILED"
    exit /B 1
)

rem clean JNI-ASPlayer-Wrap module
call gradlew :JNI-ASPlayer-Wrap:clean
if not %errorlevel% == 0 (
    echo "CLEAN JNI-ASPlayer-Wrap FAILED"
    exit /B 1
)

rem clean ASPlayerDemo App
call gradlew :ASPlayerDemo:clean
if not %errorlevel% == 0 (
    echo "CLEAN ASPlayerDemo FAILED"
    exit /B 1
)

rem build ASPlayer-library
call gradlew :ASPlayer-library:makeJar
if not %errorlevel% == 0 (
    echo "BUILD ASPlayer-library jar FAILED"
    exit /B 1
)

rem build JNI-ASPlayer-library
call gradlew :JNI-ASPlayer-library:releaseJniASPlayer
if not %errorlevel% == 0 (
    echo "BUILD JNI-ASPlayer-library FAILED"
    exit /B 1
)

rem build ASPlayerDemo App
call gradlew :ASPlayerDemo:build
if not %errorlevel% == 0 (
    echo "BUILD ASPlayerDemo FAILED"
    exit /B 1
)

rem copy ASPlayer-library and JNI-ASPlayer-library to output_dir
if exist !output_dir! (
    RD /S /Q !output_dir!
)
MD !output_dir!\libs\
xcopy /Y /Q /E %jni_asplayer_src_dir%\build\sdk\* !output_dir!\

if not %errorlevel% == 0 (
    echo BUILD SDK FAILED
    exit /B 1
) else (
    echo BUILD SDK SUCCESS
    echo output dir: !output_dir!
    if "%not_show_explorer%" == "" (
        explorer !output_dir!
    )
    exit /B 0
)
