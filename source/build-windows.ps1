$ErrorActionPreference = "Stop"

Write-Host "================================" -ForegroundColor Cyan
Write-Host "网盘直链下载 APK 构建工具" -ForegroundColor Cyan
Write-Host "版本: 0.37-beta" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

# 检查是否在 source 目录
if (-not (Test-Path "AndroidManifest.xml")) {
    Write-Host "❌ 错误: 请在 source 目录下运行此脚本" -ForegroundColor Red
    exit 1
}

# 配置路径 (请根据实际安装路径修改)
$ANDROID_SDK = "C:\android-sdk"
$ANDROID_JAR = "$ANDROID_SDK\platforms\android-23\android.jar"
$R8_JAR = "$ANDROID_SDK\r8.jar"
$BUILD_TOOLS = "$ANDROID_SDK\build-tools\34.0.0"

# 检查工具是否存在
$missing = @()
if (-not (Test-Path $ANDROID_JAR)) { $missing += "Android SDK platform-23" }
if (-not (Test-Path $R8_JAR)) { $missing += "R8/D8 (r8.jar)" }
if (-not (Test-Path "$BUILD_TOOLS\aapt.exe")) { $missing += "Build Tools 34.0.0" }
if (-not (Get-Command javac -ErrorAction SilentlyContinue)) { $missing += "JDK (javac)" }

if ($missing.Count -gt 0) {
    Write-Host "❌ 缺少必要工具:" -ForegroundColor Red
    $missing | ForEach-Object { Write-Host "   - $_" -ForegroundColor Yellow }
    Write-Host ""
    Write-Host "请参考 BUILD_FINAL.md 安装所需工具" -ForegroundColor Yellow
    exit 1
}

Write-Host "✓ 工具检查通过" -ForegroundColor Green
Write-Host ""

# 清理
Write-Host "[1/7] 清理旧文件..." -ForegroundColor Cyan
Remove-Item -Recurse -Force out, outdex -ErrorAction SilentlyContinue
Remove-Item -Force app.unsigned.apk, app-aligned.apk, app.apk -ErrorAction SilentlyContinue
New-Item -ItemType Directory out, outdex -Force | Out-Null

# 编译 Java
Write-Host "[2/7] 编译 Java 源码..." -ForegroundColor Cyan
$javaFiles = Get-ChildItem -Recurse src -Filter *.java
javac -nowarn -source 8 -target 8 -bootclasspath "$ANDROID_JAR" -d out $javaFiles.FullName
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Java 编译失败" -ForegroundColor Red
    exit 1
}
Write-Host "   ✓ 编译完成: $($javaFiles.Count) 个文件" -ForegroundColor Green

# 转换 DEX
Write-Host "[3/7] 转换 DEX (D8)..." -ForegroundColor Cyan
$classFiles = Get-ChildItem -Recurse out -Filter *.class
java -cp "$R8_JAR" com.android.tools.r8.D8 --lib "$ANDROID_JAR" --min-api 21 --output outdex $classFiles.FullName
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ DEX 转换失败" -ForegroundColor Red
    exit 1
}
Write-Host "   ✓ DEX 生成完成" -ForegroundColor Green

# 打包资源
Write-Host "[4/7] 打包资源 (aapt)..." -ForegroundColor Cyan
& "$BUILD_TOOLS\aapt.exe" package -f -M AndroidManifest.xml -S res -I "$ANDROID_JAR" -A assets -F app.unsigned.apk
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ 资源打包失败" -ForegroundColor Red
    exit 1
}
Write-Host "   ✓ 资源打包完成" -ForegroundColor Green

# 添加 DEX
Write-Host "[5/7] 添加 DEX 到 APK..." -ForegroundColor Cyan
Push-Location outdex
& "$BUILD_TOOLS\aapt.exe" add ..\app.unsigned.apk classes.dex
Pop-Location
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ DEX 添加失败" -ForegroundColor Red
    exit 1
}
Write-Host "   ✓ DEX 添加完成" -ForegroundColor Green

# 对齐
Write-Host "[6/7] 对齐 APK (zipalign)..." -ForegroundColor Cyan
& "$BUILD_TOOLS\zipalign.exe" -f 4 app.unsigned.apk app-aligned.apk
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ APK 对齐失败" -ForegroundColor Red
    exit 1
}
Write-Host "   ✓ 对齐完成" -ForegroundColor Green

# 签名
Write-Host "[7/7] 签名 APK (apksigner)..." -ForegroundColor Cyan
& "$BUILD_TOOLS\apksigner.bat" sign --ks qdl.keystore --ks-pass pass:qdlqdl123 --key-pass pass:qdlqdl123 --out app.apk app-aligned.apk
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ APK 签名失败" -ForegroundColor Red
    exit 1
}
Write-Host "   ✓ 签名完成" -ForegroundColor Green

# 清理临时文件
Remove-Item -Recurse -Force out, outdex -ErrorAction SilentlyContinue
Remove-Item -Force app.unsigned.apk, app-aligned.apk -ErrorAction SilentlyContinue

# 显示结果
Write-Host ""
Write-Host "================================" -ForegroundColor Green
Write-Host "✅ 构建成功!" -ForegroundColor Green
Write-Host "================================" -ForegroundColor Green
$apkSize = (Get-Item app.apk).Length / 1KB
Write-Host "文件: $(Get-Location)\app.apk" -ForegroundColor Cyan
Write-Host "大小: $([math]::Round($apkSize, 2)) KB" -ForegroundColor Cyan
Write-Host ""
Write-Host "下一步:" -ForegroundColor Yellow
Write-Host "1. 将 app.apk 传输到手机" -ForegroundColor White
Write-Host "2. 安装并测试" -ForegroundColor White
Write-Host "3. 查看日志: /sdcard/Download/DSH-Go/qdl.log" -ForegroundColor White
Write-Host ""
