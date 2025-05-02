@echo off
echo Spine Animation Extractor - Folder Version (No Dependencies)
echo ====================================================
echo Specially optimized for Spine 3.8.99 version
echo ====================================================

REM Check if Java is installed
where java >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo Error: Java not found. Please install Java and ensure it is in the PATH environment variable.
    pause
    exit /b 1
)

REM Check if argument is provided
if "%~1"=="" (
    echo.
    echo Usage: Drag and drop the Spine folder onto this batch file
    echo or run: ExtractAnimations.bat path\to\your\spine_folder
    echo.
    echo Options:
    echo  --debug                  Enable debug mode for more information
    echo  --force-predefined       Force use of predefined animation lists based on filenames
    echo.
    echo Note: This tool is specially optimized for Spine 3.8.99 version
    echo Note: If normal parsing fails, it will automatically use predefined animations
    echo.
    pause
    exit /b 1
)

REM Parse parameters and flags
set FOLDER_PATH=%~1
set DEBUG_MODE=
set FORCE_PREDEFINED=

REM Check if parameters include --debug
echo %* | findstr /C:"--debug" >nul && set DEBUG_MODE=--debug

REM Check if parameters include --force-predefined
echo %* | findstr /C:"--force-predefined" >nul && set FORCE_PREDEFINED=--force-predefined

REM Check if path exists
if not exist "%FOLDER_PATH%" (
    echo Error: Path not found: %FOLDER_PATH%
    pause
    exit /b 1
)

REM Check if it's a directory
if not exist "%FOLDER_PATH%\" (
    echo Error: The provided path is not a directory: %FOLDER_PATH%
    echo Please drag a folder containing .skel files, not individual files.
    pause
    exit /b 1
)

echo Processing Spine folder: %FOLDER_PATH%
echo This will scan for all .skel files in the folder and its subfolders.
echo Results will be saved to output.lua in the folder.
echo.

if defined FORCE_PREDEFINED (
    echo Using predefined animation lists based on filenames.
    echo This mode ignores the actual file content and assigns animations based on filename patterns.
) else (
    echo The best parsing method will be automatically selected after detecting your .skel file version.
    echo For Spine 3.8.99 version files, a specially optimized parser will be used.
    echo If normal parsing fails, it will automatically use predefined animations based on filename.
)

if defined DEBUG_MODE (
    echo Debug mode enabled - detailed processing information will be displayed.
)
echo.

REM Check if compiled classes exist
set CLASS_PATH=build\classes

if not exist "%CLASS_PATH%\com\spinehelper\Main.class" (
    echo Error: Compiled classes not found at %CLASS_PATH%
    echo Would you like to build the project now? (y/n):
    set /p build_now=
    if /i "%build_now%"=="y" (
        call build.bat
        if %ERRORLEVEL% neq 0 (
            echo Build failed. Cannot continue.
            pause
            exit /b 1
        )
    ) else (
        echo Build required. Please build the project first.
        pause
        exit /b 1
    )
)

REM Advanced options
if defined DEBUG_MODE (
    echo Do you also want to enable Java stack trace? (y/n):
    set /p stack_trace=
    if /i "%stack_trace%"=="y" (
        REM Enable full Java exception stack trace
        set DEBUG_MODE=--debug --stacktrace
    )
)

REM Run extractor with the provided folder
echo Executing... Please wait
java -cp %CLASS_PATH% com.spinehelper.Main "%FOLDER_PATH%" %DEBUG_MODE% %FORCE_PREDEFINED%

echo.
if %ERRORLEVEL% equ 0 (
    echo Process completed successfully!
    echo output.lua file has been generated in your specified directory.
    
    if defined FORCE_PREDEFINED (
        echo Note: Predefined animation lists were used for all files. If the results are not as expected,
        echo you can edit the extractFromPredefinedList method in SpineExtractor.java to add more animations.
    ) else (
        echo Note: If certain files could not be parsed, predefined animation lists were automatically used.
        echo Check the console output to see which files used predefined lists.
    )
) else (
    echo Encountered problems during processing.
    echo If you need more information, please rerun with the --debug option.
)

pause 