# 这是什么 + 我是怎么改的（给接手者）

## 1. 成品是什么
一个**独立 Android APK**「网盘直链下载」（包名 `com.dsh.qdl`），不依赖服务器/AI：
- 用户粘贴网盘分享链接 → 解析分享 → 转存到自己的网盘 → 取直链 → 用原生 Java 分段（HTTP Range）下载到手机 `下载/DSH-Go/`
- 账号：App 内 WebView 打开各网盘网页登录，登录后抓 Cookie / localStorage token，保存在 App 私有 SharedPreferences
- 支持：夸克、UC、123、迅雷、百度；**前四个可用，百度未修好**（本次要修的就是百度）
- 结构：单 Activity（`MainActivity.java`）+ WebView 前端（`assets/index.html`）+ 前台保活服务（`KeepAliveService.java`）；下载引擎、各平台适配都在 MainActivity 里；文件用 MediaStore 写入「下载/DSH-Go」；日志写 `qdl.log`；无 Gradle、无第三方依赖（只有 Android SDK + org.json）

## 2. 我的开发/修改环境（重要）
- 运行在**一台 Android 13 手机上的 proot Ubuntu 24.04 aarch64**（无 root；没有 bubblewrap/Landlock，shell 以 danger-full-access 运行）
- 工作区 `/root/workspace`；手机存储挂载在 `/sdcard`；APK/日志都在 `/sdcard/Download/DSH-Go/`
- 构建工具：`openjdk-17`、`android-sdk-platform-23` 的 `android.jar`、`android-sdk-build-tools`（aapt/zipalign/apksigner）、`/root/build/r8.jar`（D8）
- **没有 Android Studio / Gradle / adb / 模拟器**，全部命令行编译：
  `javac -source 8 -target 8 -bootclasspath android.jar` → `d8` → `aapt package` → `aapt add classes.dex` → `zipalign` → `apksigner`（keystore `qdl.keystore`，密码 `qdlqdl123`）
- 修改方式：用编辑器/Python 直接改 `MainActivity.java`、`assets/index.html`、`AndroidManifest.xml`，然后 `source/build.sh` 构建，把 `app.apk` 复制到手机下载目录；**安装由用户手动完成**（没有 adb）
- 测试能力有限：
  - 不能在后台跑 Android App，也没有模拟器
  - 能用 Node/Python 在后台复现各网盘 HTTP 接口（`tools/baidu-cookie-test.js` 就是）
  - 登录后的转存/下载只能靠用户真机操作 + 读 `/sdcard/Download/DSH-Go/qdl.log`
  - **没有百度账号凭据**，所以百度登录后的步骤未在后台验证；需要用户设备或临时 BDUSS
- 当前版本：versionCode 37 / versionName `0.36-beta`；minSdk 21 / targetSdk 28；图标与名称在 `res/`

## 3. 本轮我做了什么
- 迅雷：WebView 登录读 `credentials_/captcha_/deviceid`；分享 `share` → `share/detail` → `share/restore`+`tasks` → `files` 直链；验证码过期改为「重新登录」（清登录态后进登录页）；下载实测 12–16 MB/s
- 百度：登录抓 `BDUSS/STOKEN/BAIDUID`；分享 `share/verify` 拿 `BDCLND` → 解析分享页 `locals.mset` → `share/transfer`（bdstoken/async=2）→ `/api/list` → PCS 老端点下载。**仍卡在分享页解析**，详见 `ISSUE.md`
- 其它修复：123 登录页/API 域名、迅雷刷新按钮/取消/下载结束震动、通知可点回 App、`qdl.log` 落盘、登录成功后自动检测 Cookie 返回
- 版本从 0.21 迭代到 0.36；历史迭代细节不用看，以本包为准

## 4. 已知边界
- 百度：分享页 Cookie 组合敏感（`BDCLND` only = 74KB 有 locals；`+BAIDUID` = 50KB 无 locals，已后台实测）；App 日志仍是 50KB，优先查 Cookie 是否真的只发了 `BDCLND`
- 百度非会员账号级限速（约 40–50 KB/s，多线程无收益），所以百度强制单连接
- 百度普通 Web 会话的 dlink 会 31362，不能走 dlink，走 PCS `d.pcs.baidu.com/rest/2.0/pcs/file?method=download&path=`
- 删除百度中转目录可能触发安全验证 errno 132，删不掉就提示用户手删
- 各平台分享/转存接口随时可能变；不要用未登录的匿名路径做生产假设（wxlist 已实测 9019/2）
