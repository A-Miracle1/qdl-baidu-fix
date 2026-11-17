@echo off
echo === 自动构建百度网盘修复版 APK ===
echo.

REM 检查 Java 环境
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未找到 Java 环境
    echo 请先安装 JDK 8 或更高版本
    pause
    exit /b 1
)

REM 设置目录
set BASE_DIR=%~dp0
set SOURCE_DIR=%BASE_DIR%source
set APK_DIR=%BASE_DIR%apk
set BUILD_DIR=%BASE_DIR%build-output

echo [1/5] 准备构建环境...
if not exist "%BUILD_DIR%" mkdir "%BUILD_DIR%"

echo [2/5] 下载 apktool...
if not exist "%BASE_DIR%apktool.jar" (
    echo 请手动下载 apktool.jar 到项目根目录
    echo 下载地址: https://github.com/iBotPeaches/Apktool/releases/latest
    pause
    exit /b 1
)

echo [3/5] 反编译 APK...
java -jar apktool.jar d "%APK_DIR%\网盘直链下载-0.36-beta.apk" -o "%BUILD_DIR%\decompiled" -f

echo [4/5] 替换修改后的代码...
REM 这里需要将 smali 代码替换

echo [5/5] 重新打包...
java -jar apktool.jar b "%BUILD_DIR%\decompiled" -o "%BUILD_DIR%\网盘直链下载-0.39-beta-unsigned.apk"

echo.
echo === 构建完成 ===
echo 输出文件: %BUILD_DIR%\网盘直链下载-0.39-beta-unsigned.apk
echo.
echo 注意: APK 未签名，需要使用 apksigner 签名后才能安装
pause
