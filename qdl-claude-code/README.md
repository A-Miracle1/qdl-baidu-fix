# 网盘直链下载 APK · Claude Code 修复包

## 一句话
Android 下载器：网盘分享链接 → 转存 → 直链 → 分段下载。夸克 / UC / 123 / 迅雷已可用，**百度网盘没修好**，需要 Claude Code 接手修到能跑通再回包。

## 先看
1. `ISSUE.md` —— 问题、日志、已做的后台验证、建议修法
2. `ENVIRONMENT.md` —— 这个 App 是什么、我用的构建/修改环境、测试边界
3. `PROMPT.md` —— 可直接粘给 Claude Code 的提示词
2. `screenshots/` —— 用户报错截图（按编号）
3. `apk/网盘直链下载-0.36-beta.apk` —— 当前成品（可直接装）
4. `source/` —— 全部源码（Java + assets + res + keystore）
5. `logs/qdl.log` —— 设备运行日志
6. `notes/` —— 交接摘要 + 迅雷/百度接口逆向笔记
7. `tools/baidu-cookie-test.js` —— 无凭据即可复现「分享页 Cookie 差异」的后台脚本

## 环境
- 构建：proot Ubuntu aarch64，`android-23` 平台，`aapt` / `d8(r8.jar)` / `apksigner`，见 `source/build.sh`
- 安装包签名 keystore：`source/qdl.keystore`，密码 `qdlqdl123`
- 目标 Android 13 真机；包名 `com.dsh.qdl`，versionName 0.36-beta
- **不包含任何账号凭据**：百度需要用户自己登录（App 内 WebView，抓 BDUSS Cookie），或由用户提供临时 BDUSS 供测试

## 验收标准
- 在真机 App 里用百度分享链接 `https://pan.baidu.com/s/1TwY2Pb5mHZOjpyiiOUTCIg?pwd=v357` 能完整下载到手机 `下载/DSH-Go/`
- 失败时 `qdl.log` 能明确定位到哪一步（解析 / bdstoken / 转存 / 列目录 / PCS 下载）
- 不破坏夸克 / UC / 123 / 迅雷现有功能

## 沟通背景
用户很在意「先在后台跑通再给成品」，不要连续给未验证的包；改完请在后台用真实账号/链接跑通或给出可复现的后台脚本结果。
