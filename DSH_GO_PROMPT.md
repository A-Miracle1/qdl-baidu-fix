# DSH Go 构建和测试任务提示词

你好,我需要你在 Android 手机的 proot Ubuntu 环境中完成以下任务:

## 📋 任务概述

1. 解压项目文件
2. 使用增强版构建脚本编译 Android APK
3. 安装到手机
4. 测试百度网盘下载功能
5. 收集完整日志并报告结果

## 🎯 验收标准

### 功能验收
- [ ] 百度网盘测试链接能成功下载 (速度 40-50 KB/s 正常)
- [ ] 日志显示 `bdClnd长度=52`
- [ ] 日志显示 Cookie 长度 59
- [ ] 日志显示分享页 73999 字节
- [ ] 日志显示 `locals=true`
- [ ] 文件下载到 `/sdcard/Download/DSH-Go/`

### UI 验收
- [ ] 深色主题 (#0a0e14 背景)
- [ ] 标题显示 "0.37 beta"
- [ ] 按钮有悬停动画效果
- [ ] 进度条显示渐变色
- [ ] 续传提示有蓝色边框高亮

## 📝 详细步骤

### 步骤 1: 准备工作目录

```bash
# 进入你的工作目录
cd ~
mkdir -p qdl-build
cd qdl-build

# 复制压缩包到这里 (如果你通过其他方式传输了文件)
# 或者直接在这个目录解压
```

### 步骤 2: 解压项目文件

```bash
# 解压 (假设压缩包在当前目录)
tar -xzf qdl-fix-v037-final.tar.gz
cd qdl-fix

# 验证文件结构
ls -la
ls -la source/
```

**预期输出:**
```
source/
├── src/
├── res/
├── assets/
├── AndroidManifest.xml
├── build-enhanced.sh
└── qdl.keystore
```

### 步骤 3: 检查构建环境

```bash
cd source

# 检查所需工具
echo "=== 检查构建工具 ==="
command -v javac && echo "✓ javac" || echo "✗ javac 缺失"
command -v java && echo "✓ java" || echo "✗ java 缺失"
command -v aapt && echo "✓ aapt" || echo "✗ aapt 缺失"
command -v zipalign && echo "✓ zipalign" || echo "✗ zipalign 缺失"
command -v apksigner && echo "✓ apksigner" || echo "✗ apksigner 缺失"

# 检查 Android SDK
ls -la /usr/lib/android-sdk/platforms/android-23/android.jar && echo "✓ android.jar" || echo "✗ android.jar 缺失"

# 检查 R8
ls -la /root/build/r8.jar && echo "✓ r8.jar" || echo "✗ r8.jar 缺失"
```

**如果有工具缺失:**
```bash
# 安装 Android SDK build tools (如果需要)
apt-get update
apt-get install -y android-sdk
```

### 步骤 4: 构建 APK

```bash
# 给构建脚本执行权限
chmod +x build-enhanced.sh

# 开始构建
bash build-enhanced.sh
```

**预期输出:**
```
🔨 Building Android APK...
Version: 0.37-beta (38)

[INFO] Checking prerequisites...
[INFO] ✓ All prerequisites satisfied
[INFO] Cleaning previous build...
[INFO] Compiling Java sources...
[INFO] Found 2 Java files
[INFO] ✓ Java compilation successful
[INFO] Converting bytecode to DEX...
[INFO] ✓ DEX conversion successful
[INFO] Packaging APK resources...
[INFO] Adding DEX to APK...
[INFO] ✓ APK packaging successful
[INFO] Aligning APK...
[INFO] ✓ APK alignment successful
[INFO] Signing APK...
[INFO] ✓ APK signing successful
[INFO] Verifying APK signature...
[INFO] ✓ APK signature valid

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
[INFO] Build completed successfully!
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  📦 APK Path:    /path/to/source/app.apk
  📏 Size:        X.XX MB
  🏷️  Version:     0.37-beta (38)
  📱 Min SDK:     21 (Android 5.0)
  🎯 Target SDK:  28 (Android 9.0)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

[INFO] Build time: Xs
```

**如果构建失败:**
- 检查错误信息中提到的缺失工具
- 确认 `qdl.keystore` 文件存在
- 查看是否有 Java 编译错误

### 步骤 5: 安装 APK 到手机

```bash
# 将 APK 复制到下载目录
cp app.apk /sdcard/Download/DSH-Go/网盘直链下载-0.37-beta.apk

# 如果目录不存在,先创建
mkdir -p /sdcard/Download/DSH-Go/

# 验证复制成功
ls -lh /sdcard/Download/DSH-Go/网盘直链下载-0.37-beta.apk
```

**手动安装:**
1. 在文件管理器中找到 `/sdcard/Download/DSH-Go/网盘直链下载-0.37-beta.apk`
2. 点击安装
3. 如果提示"未知来源",去设置中允许安装此应用

### 步骤 6: 测试百度网盘功能

**测试链接:**
```
https://pan.baidu.com/s/1TwY2Pb5mHZOjpyiiOUTCIg?pwd=v357
```

**操作步骤:**

1. **打开 APP**
   - 启动"网盘直链下载"
   - 确认 UI 是深色主题
   - 确认标题显示 "0.37 beta"

2. **登录百度网盘**
   - 点击"百度网盘"标签
   - 点击"登录"按钮
   - 使用你的百度账号登录
   - 登录成功后状态应显示绿色 ✓

3. **输入测试链接**
   - 在"网盘链接"输入框粘贴测试链接
   - 在"提取码"输入框输入: `v357`
   - 点击"开始下载"按钮

4. **观察下载过程**
   - 进度条应该开始移动
   - 速度大约 40-50 KB/s (百度非会员限速)
   - 观察是否有错误提示

5. **验证下载结果**
   ```bash
   # 检查下载目录
   ls -lh /sdcard/Download/DSH-Go/
   
   # 应该能看到下载的文件
   ```

### 步骤 7: 收集日志

```bash
# 读取完整日志
cat /sdcard/Download/DSH-Go/qdl.log

# 或者只看最后 200 行
tail -n 200 /sdcard/Download/DSH-Go/qdl.log

# 保存日志副本
cp /sdcard/Download/DSH-Go/qdl.log ~/qdl-test-log-$(date +%Y%m%d-%H%M%S).txt
```

**关键日志检查点:**

应该看到以下关键日志行:

```
百度 verify 成功 randsk=有 长度=52
调用 bdToken 前 bdClnd长度=52
调用 bdLocals 前 bdClnd长度=52
bdShareCookie 返回=BDCLND长度52
bdGetRaw Cookie长度=59 内容=BDCLND=PHX8YNFZC9...
bdGetRaw 返回 200 长度=73999
百度分享页 73999 字节，locals=true title=小王不AI干正事...
locals 解析：shareid=54517301957 uk=1101398921738 files=1
```

### 步骤 8: 测试其他网盘 (可选)

为了确保改动没有影响其他网盘,可以测试:

- **夸克网盘**: 随便找一个夸克分享链接
- **UC 网盘**: 随便找一个 UC 分享链接
- **123 网盘**: 随便找一个 123 分享链接
- **迅雷**: 随便找一个迅雷分享链接

每个测试步骤同上,只是换不同的网盘标签。

## 📊 结果报告模板

请按以下格式整理测试结果:

```markdown
# 网盘直链下载 v0.37-beta 测试报告

## 构建结果
- [ ] ✅ 构建成功 / ❌ 构建失败
- APK 大小: X.XX MB
- 构建时间: X 秒
- 构建错误 (如有): 

## 安装结果
- [ ] ✅ 安装成功 / ❌ 安装失败
- 安装错误 (如有):

## UI 验收
- [ ] ✅ 深色主题正确
- [ ] ✅ 标题显示 "0.37 beta"
- [ ] ✅ 按钮悬停动画
- [ ] ✅ 渐变进度条
- [ ] ✅ 续传提示样式

## 百度网盘测试
- [ ] ✅ 测试通过 / ❌ 测试失败
- 下载速度: XX KB/s
- 下载文件大小: X.XX MB
- 关键日志:
  - bdClnd长度: XX (应为 52)
  - Cookie长度: XX (应为 59)
  - 分享页字节: XXXXX (应为 73999)
  - locals 状态: true/false

## 其他网盘测试 (可选)
- [ ] 夸克: ✅/❌
- [ ] UC: ✅/❌
- [ ] 123: ✅/❌
- [ ] 迅雷: ✅/❌

## 完整日志
(粘贴 qdl.log 的关键部分,至少包含上面提到的关键日志行)

## 截图 (如果可以)
- APP 主界面截图
- 下载进行中截图
- 下载完成截图

## 问题和建议
(如果有任何问题或改进建议)
```

## 🔧 故障排查

### 问题 1: 构建失败 - javac not found
```bash
apt-get install -y openjdk-8-jdk
```

### 问题 2: 构建失败 - aapt not found
```bash
apt-get install -y android-sdk-build-tools
```

### 问题 3: 签名失败
```bash
# 检查 keystore 文件
ls -la qdl.keystore

# 如果文件损坏,重新生成
keytool -genkey -v -keystore qdl.keystore \
  -alias qdl -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass qdlqdl123 -keypass qdlqdl123
```

### 问题 4: 安装失败 - 解析包错误
- APK 可能损坏,重新构建
- 手机 Android 版本太低 (最低需要 5.0)

### 问题 5: 下载失败 - 日志显示 bdClnd长度=0
- 说明 verify 失败
- 检查网络连接
- 尝试重新登录百度网盘

### 问题 6: 下载失败 - 分享页 49576 字节
- 说明 Cookie 策略有问题
- 这是我们要修复的主要 bug
- 提供完整日志以便进一步分析

### 问题 7: APP 闪退
```bash
# 查看系统日志
logcat | grep "com.dsh.qdl"

# 或者
logcat -s "qdl"
```

## 📁 文件说明

### 源码文件
- `MainActivity.java` - 主逻辑,已添加百度网盘调试日志
- `KeepAliveService.java` - 后台保活服务
- `index.html` - WebView 前端界面 (已现代化 UI)

### 配置文件
- `AndroidManifest.xml` - 应用清单 (版本 38 / 0.37-beta)
- `AndroidManifest-enhanced.xml` - 增强版清单 (可选,支持更多特性)
- `proguard-rules.pro` - 代码混淆规则
- `network_security_config.xml` - 网络安全配置

### 构建脚本
- `build.sh` - 原始构建脚本
- `build-enhanced.sh` - 增强版构建脚本 (推荐使用)
- `build-windows.ps1` - Windows 构建脚本 (你用不到)

### 文档
- `BUILD_FINAL.md` - 最终构建指南
- `BUILD_AND_TEST.md` - 测试文档
- `BUILD_BEST_PRACTICES.md` - 构建最佳实践
- `README_DELIVERY.md` - 项目交付总结

## 💡 提示

1. **构建时间**: 正常情况下 10-30 秒
2. **APK 大小**: 预计 1-2 MB
3. **下载速度**: 百度非会员 40-50 KB/s 是正常的
4. **日志位置**: `/sdcard/Download/DSH-Go/qdl.log`
5. **测试文件**: 小王不AI干正事分享的测试文件

## ⚠️ 注意事项

1. **不要修改代码** - 直接使用提供的版本
2. **完整日志** - 测试后务必提供完整 qdl.log
3. **测试顺序** - 先测百度网盘,确认修复有效
4. **权限问题** - 确保 APP 有存储权限
5. **网络问题** - 确保手机能访问百度网盘

## 📞 遇到问题?

如果遇到任何问题:
1. 保存完整的构建日志
2. 保存完整的 qdl.log
3. 截图错误提示
4. 描述具体的失败现象
5. 反馈给开发者

---

**版本**: 0.37-beta  
**构建日期**: 2026-11-17  
**测试链接**: https://pan.baidu.com/s/1TwY2Pb5mHZOjpyiiOUTCIg?pwd=v357  
**预期 bdClnd 长度**: 52  
**预期分享页大小**: 73999 字节

祝测试顺利! 🚀
