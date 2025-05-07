@echo off
set JAVA_HOME=C:\Program Files\Java\jdk1.8.0_281
set PATH=%JAVA_HOME%\bin;%PATH%

:: 獲取 .bat 檔案所在的目錄（即 target 目錄）
set "CURRENT_DIR=%~dp0"

:: 假設 Spine 資料夾在 target 的上層目錄（D:\SpineHelperMaven\Spine）
set "SPINE_DIR=%CURRENT_DIR%\Spine"

:: 假設 natives 資料夾在 target 的上層目錄（D:\SpineHelperMaven\natives）
set "NATIVES_DIR=%CURRENT_DIR%..\natives"

:: 運行 JAR 檔案
java -Djava.library.path="%NATIVES_DIR%" -jar "%CURRENT_DIR%\target\spine-animation-extractor-1.0-SNAPSHOT.jar" "%SPINE_DIR%"
pause