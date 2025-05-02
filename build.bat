@echo off
echo Spine Animation Extractor - Java Build Script (No JAR)
echo ====================================================

REM Check if Java is installed
where java >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo Error: Java not found. Please install Java and ensure it is in the PATH environment variable.
    exit /b 1
)

echo Note: This version does not require the Spine runtime library

REM Create directories if they don't exist
if not exist build mkdir build
if not exist build\classes mkdir build\classes
if not exist src\main\java\com\spinehelper mkdir src\main\java\com\spinehelper

echo Compiling Java sources...
javac -d build\classes src\main\java\com\spinehelper\*.java

if %ERRORLEVEL% neq 0 (
    echo Error: Compilation failed.
    exit /b 1
)

echo Compilation successful! No JAR file created (javac only).
echo You can run the program with:
echo java -cp build\classes com.spinehelper.Main your_spine_folder_path
echo Add --debug parameter for more processing information.
echo Add --force-predefined parameter to use predefined animation lists.
echo Note: If normal parsing fails, predefined animation lists will be used automatically.
echo.

REM Ask if user wants to run an example
set /p run_example=Do you want to run an example? (y/n): 
if /i "%run_example%"=="y" (
    set /p spine_folder=Please enter the path to the Spine folder: 
    if exist "%spine_folder%" (
        REM Debug mode
        set /p debug_mode=Enable debug mode? (y/n): 
        set DEBUG_PARAM=
        if /i "%debug_mode%"=="y" (
            set DEBUG_PARAM=--debug
        )
        
        REM Predefined mode
        set /p predefined_mode=Force use of predefined animation lists for all files? (y/n): 
        set PREDEFINED_PARAM=
        if /i "%predefined_mode%"=="y" (
            set PREDEFINED_PARAM=--force-predefined
            echo Note: Using predefined animation lists based on filenames for all files.
        ) else (
            echo Note: Predefined animation lists will be used automatically if parsing fails.
        )
        
        echo Running example...
        java -cp build\classes com.spinehelper.Main "%spine_folder%" %DEBUG_PARAM% %PREDEFINED_PARAM%
    ) else (
        echo Folder does not exist: %spine_folder%
        echo Please make sure the path is correct and the folder contains .skel files.
    )
)

pause 