# 百度网盘修复 - 构建和测试说明

## 修改内容

### 1. 添加详细调试日志 (MainActivity.java)

#### bdShareCookie() 方法
- 添加日志输出 BDCLND 长度
- 确认返回的 Cookie 内容

#### bdGetRaw() 方法  
- 添加发送前的 Cookie 日志(长度+前50字符)
- 添加 302 跳转日志
- 添加返回状态码和内容长度日志

#### runJobBaidu() 方法
- 在 verify 后添加 bdClnd 长度日志
- 在调用 bdToken 前后添加 bdClnd 长度日志
- 在调用 bdLocals 前添加 bdClnd 长度日志

这些日志能够帮助确认:
1. bdClnd 是否正确从 verify 获取
2. bdClnd 是否在各个步骤中保持不变
3. 分享页请求是否真的只带了 BDCLND

## 后台测试结果

✅ **使用提供的 BDUSS 等凭据,完整流程已验证可行:**

1. verify 成功获取 randsk: `PHX8YNFZC9%2BsCR1%2FCXImCR2SJPEZ9QR5pfyY%2ByRXPK0%3D` (长度 52)
2. bdstoken 成功获取: `c4de52e75e72d0bb8f762f6d9ef5fc66`
3. 分享页(只带BDCLND)成功获取 73999 字节,有 locals.mset
   - shareid: 54517301957
   - uk: 1101398921738
   - file_count: 1

## 在 proot Ubuntu 上构建 APK

```bash
# 在你的 proot Ubuntu 环境中执行:
cd /path/to/qdl-fix/source
bash build.sh

# 构建成功后,APK 位于:
# source/app.apk

# 复制到手机下载目录:
cp app.apk /sdcard/Download/DSH-Go/网盘直链下载-0.37-debug.apk
```

## 测试步骤

### 1. 安装 APK
在手机上打开文件管理器,找到 `/sdcard/Download/DSH-Go/网盘直链下载-0.37-debug.apk`,点击安装

### 2. 测试百度网盘下载
1. 打开 App
2. 粘贴测试链接: `https://pan.baidu.com/s/1TwY2Pb5mHZOjpyiiOUTCIg?pwd=v357`
3. 点击开始下载
4. 观察日志输出

### 3. 查看日志
```bash
# 在 proot 环境或 adb 中查看:
cat /sdcard/Download/DSH-Go/qdl.log
```

## 预期的日志输出

如果一切正常,日志应该包含:

```
百度分享 TwY2Pb5mHZOjpyiiOUTCIg（有提取码）
百度 verify 成功 randsk=有 长度=52
调用 bdToken 前 bdClnd长度=52
百度 bdstoken 获取成功，bdClnd长度=52
调用 bdLocals 前 bdClnd长度=52
bdShareCookie 返回=BDCLND长度52
bdGetRaw Cookie长度=59 内容=BDCLND=PHX8YNFZC9%2BsCR1%2FCXImCR2SJPEZ9QR5pfyY%2ByRXPK...
bdGetRaw 返回 200 长度=73999
百度分享页 73999 字节，locals=true title=小王不AI干正事_免费高速下载|百度网盘-分享无限制
locals 解析：shareid=54517301957 uk=1101398921738 files=1
```

## 如果仍然失败

### 检查点 1: bdClnd 是否为空
如果日志显示 `bdClnd长度=0`,说明 verify 没有正确保存 randsk

### 检查点 2: Cookie 长度
如果 `bdGetRaw Cookie长度` 不是 59 (BDCLND= 加 52字符),说明 Cookie 构造有问题

### 检查点 3: 页面长度
如果返回 49576 而不是 73999,说明百度仍然返回了简化页面

### 检查点 4: 需要登录
如果所有日志都正常,但 locals 解析失败,可能是:
- BDUSS 已过期,需要在 App 内重新登录百度
- 或者百度检测到异常,需要在网页上过一次验证

## 修改的文件

- `source/src/com/dsh/qdl/MainActivity.java` (已添加调试日志)
- 版本号建议改为 0.37-debug

## 下一步

如果测试成功:
1. 可以移除部分调试日志(保留关键的)
2. 更新版本号为 0.37-beta
3. 重新构建正式版本

如果测试失败:
1. 发送完整的 qdl.log 日志
2. 说明具体的失败现象
3. 我会根据日志进一步分析
