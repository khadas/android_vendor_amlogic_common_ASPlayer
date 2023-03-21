@echo off
setlocal enableDelayedExpansion

set version=1.0

set cur_dir=%~dp0
rem SDK output dir
set output_dir=%cur_dir%asplayer_sdk

rem Jni_ASPlayer SDK android project source dir
set sdk_source_dir=%cur_dir%projects\modules

set dateStr=%date:~0,4%.%date:~5,2%.%date:~8,2%
set output_dir=%output_dir%\JniASPlayerSDK_!version!_%dateStr%

call :BUILD_SDK %version% %output_dir%
if not %errorlevel% == 0 (
	echo BUILD Jni_ASPlayer SDK failed
	EXIT /B 1
)

echo ASPlayerSDK path: %dest_sdk_output_path%
echo [DONE] BUILD ASPlayerSDK SUCCESS

explorer %output_dir%

EXIT /B %errorlevel%



:BUILD_SDK
setlocal
set version=%~1
set output_dir=%~2

echo BUILD SDK...
cd %sdk_source_dir%
call build_sdk.bat %version% %output_dir% 1

if not %errorlevel% == 0 (
	echo BUILD SDK failed
	cd %cur_dir%
	EXIT /B 1
)

cd %cur_dir%

endlocal
EXIT /B 0
