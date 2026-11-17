# 百度网盘修复包 v0.37-debug

## 修改摘要

### 问题诊断
根据 ISSUE.md 的后台验证,百度分享页返回 49576 字节(无 locals)的问题,是因为 Cookie 组合不对。后台测试证明:
- 只带 BDCLND: 73999 字节,有 locals ✅
- BDCLND + BAIDUID: 49576 字节,无 locals ❌

### 代码修改
在 `MainActivity.java` 中添加了详细的调试日志,用于追踪 Cookie 在各个步骤的状态:

1. **bdShareCookie()** (行 2061-2065)
   - 添加日志输出 BDCLND 长度
   - 确认只返回 BDCLND Cookie

2. **bdGetRaw()** (行 2093-2124)
   - 添加发送前的 Cookie 日志(长度+前50字符)
   - 添加 302 跳转日志
   - 添加返回状态码和页面长度日志

3. **runJobBaidu()** (行 2300-2318)
   - 在 verify 后记录 bdClnd 长度
   - 在调用 bdToken 前后记录 bdClnd 长度
   - 在调用 bdLocals 前记录 bdClnd 长度

### 后台验证结果
使用提供的 BDUSS 等凭据,在 Node.js 环境中完整测试了百度网盘流程:

✅ verify 获取 randsk 成功  
✅ 获取 bdstoken 成功  
✅ 分享页(只带BDCLND)获取 locals 成功 (73999字节)

这证明完整流程是可行的,问题可能在于:
- App 中 bdClnd 变量在某处被清空
- 或者 bdShareCookie() 没有被正确调用

添加的日志能够精确定位问题所在。

## 文件清单

```
qdl-fix/
├── BUILD_AND_TEST.md          # 构建和测试说明(新增)
├── test-baidu-flow.js          # 后台测试脚本(新增,已验证通过)
├── ENVIRONMENT.md
├── ISSUE.md
├── PROMPT.md
├── README.md
├── source/
│   ├── src/com/dsh/qdl/
│   │   └── MainActivity.java  # 已修改,添加调试日志
│   ├── assets/
│   ├── res/
│   ├── AndroidManifest.xml
│   ├── qdl.keystore
│   └── build.sh
├── apk/
│   └── 网盘直链下载-0.36-beta.apk  # 原始版本
├── logs/
├── notes/
├── screenshots/
└── tools/
```

## 构建说明

### 环境要求
- proot Ubuntu 24.04 aarch64
- openjdk-17
- android-sdk-platform-23
- android-sdk-build-tools
- /root/build/r8.jar

### 构建命令
```bash
cd source
bash build.sh
```

### 输出
- `source/app.apk` - 已签名的 APK

## 测试说明

### 1. 安装 APK
复制到手机下载目录并手动安装:
```bash
cp source/app.apk /sdcard/Download/DSH-Go/网盘直链下载-0.37-debug.apk
```

### 2. 测试百度下载
使用测试链接: `https://pan.baidu.com/s/1TwY2Pb5mHZOjpyiiOUTCIg?pwd=v357`

### 3. 查看日志
```bash
cat /sdcard/Download/DSH-Go/qdl.log
```

### 预期日志关键行
```
百度 verify 成功 randsk=有 长度=52
调用 bdToken 前 bdClnd长度=52
百度 bdstoken 获取成功，bdClnd长度=52
调用 bdLocals 前 bdClnd长度=52
bdShareCookie 返回=BDCLND长度52
bdGetRaw Cookie长度=59 内容=BDCLND=...
bdGetRaw 返回 200 长度=73999
百度分享页 73999 字节，locals=true
```

## 验收标准

- ✅ 日志显示 bdClnd 长度始终为 52
- ✅ bdGetRaw Cookie 长度为 59 (BDCLND= + 52字符)
- ✅ 分享页返回 73999 字节
- ✅ locals 解析成功
- ✅ 文件下载到 `/sdcard/Download/DSH-Go/`

## 如果测试失败

请提供完整的 `qdl.log` 日志,我会根据日志中的:
- bdClnd 长度变化
- Cookie 实际内容
- 页面长度
- locals 解析结果

来进一步定位问题。

## 已知工作的平台
- ✅ 夸克
- ✅ UC
- ✅ 123网盘
- ✅ 迅雷
- 🔧 百度 (本次修复)

## 注意事项

1. 如果 BDUSS 过期,需要在 App 内重新登录百度
2. 如果百度触发安全验证,需要在网页上过一次验证
3. 百度非会员限速约 40-50 KB/s,多线程无收益
4. 删除临时目录可能触发 errno 132,会提示用户手删
