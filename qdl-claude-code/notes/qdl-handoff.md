# 网盘直链下载 · 交接摘要（2026-11-17 · 0.36-beta）

## 一句话
「网盘分享链接 → 直链 → 多线程下载到手机」的独立 APK，不依赖 AI。当前 0.36-beta：夸克 / UC / 123 / 迅雷 / 百度。

## 位置
- 源码：`/root/build/qdl`（`MainActivity.java`、`KeepAliveService.java`、`assets/index.html`、`AndroidManifest.xml`、`qdl.keystore`）
- 产物：`/sdcard/Download/DSH-Go/网盘直链下载-0.36-beta.apk`
- 日志：`/sdcard/Download/DSH-Go/qdl.log`；下载目录 `DSH-Go/`
- 接口笔记：`/root/workspace/xunlei-api-notes.md`、`/root/workspace/baidu-api-notes.md`
- 版本：versionCode 37，versionName 0.xx-beta，1.0 前跑一轮全平台回归

## 构建（proot Ubuntu aarch64）
```sh
cd /root/build/qdl
AJ=/usr/lib/android-sdk/platforms/android-23/android.jar
javac -nowarn -source 8 -target 8 -bootclasspath "$AJ" -d out src/com/dsh/qdl/*.java
java -cp /root/build/r8.jar com.android.tools.r8.D8 --lib "$AJ" --min-api 21 --output outdex $(find out -name '*.class')
aapt package -f -M AndroidManifest.xml -S res -I "$AJ" -A assets -F app.unsigned.apk
(cd outdex && aapt add ../app.unsigned.apk classes.dex)
zipalign -f 4 app.unsigned.apk app-aligned.apk
apksigner sign --ks qdl.keystore --ks-pass pass:qdlqdl123 --key-pass pass:qdlqdl123 --out app.apk app-aligned.apk
cp app.apk "/sdcard/Download/DSH-Go/网盘直链下载-<版本>.apk"
```

## 已实现
- 登录：夸克/UC/123 抓 Cookie；迅雷抓 localStorage token；百度抓 BDUSS Cookie；都支持 WebView 登录 + 手动粘贴 Cookie
- 分享 → 转存 → 直链 → 分段下载 → 清理暂存；断点续传（迅雷/百度按文件名+大小匹配，因 file id 会变）
- MediaStore 写「下载/DSH-Go」；前台保活；仪表盘；调试模式测速（32/64/96/128，百度除外）
- 下载结束震动（成功 2 长 / 失败 3 短）；通知可点回 App；取消可中断刷新/下载
- 登录页支持双指缩放；百度登录用手机默认 UA；登录成功后自动检测 Cookie/token 并返回 App（不用手动按返回）
- 百度分享解析：verify 拿 BDCLND → 分享页只带 `BDCLND`（带浏览器 BAIDUID 会返回 50KB「分享文件」页、没有 locals，已后台实测）→ 手动跟随 302 → 花括号扫描 locals.mset 拿 shareid/uk/file_list；wxlist 需要登录不稳，只做兜底
- 百度转存参数已补：create 带 size=0/block_list=[]/clienttype=0；transfer 带 sekey(randsk 解码)、bdstoken、async=2

## 平台要点
- 夸克：`drive-pc.quark.cn/1/clouddrive/file/download?entry=ft&fr=pc&pr=ucpro`，必须客户端 UA（`quark-cloud-drive/3.20.0 …Electron`），否则 23018
- UC：同构，`pc-api.uc.cn`，`pr=UCBrowser`，UA `uc-cloud-drive/2.5.20 …`
- 123：登录 `yun.123pan.cn`（读 localStorage `authorToken` 走 Bearer）；API `api.123278.com`；分享可匿名下（5112=分享方流量包不足）
- 迅雷：`api-pan.xunlei.com/drive/v1`；分享 `share` → `share/detail` → `share/restore` + `tasks/{id}?type=share` → `files?parent_id` → 直链 `files/{id}?usage=CONSUME`；头 Authorization + X-Captcha-Token + X-Device-Id + X-Client-Id；captcha 约 5 分钟，过期点「重新登录」（会清旧登录态再进登录页）
- 百度：只发 Cookie 白名单（BDUSS/STOKEN/BAIDUID/BDCLND）；API UA `netdisk`，下载 UA `pan.baidu.com`；`POST /share/wxlist` 拿 shareid/uk/seckey/树 → `POST /share/transfer`（bdstoken、async=2）到 `/qdl-xxx` → `/api/list` 递归 → 下载走 PCS `https://d.pcs.baidu.com/rest/2.0/pcs/file?method=download&app_id=250528&path=`（dlink 已 31362）；百度非会员账号级限速，强制单连接
- 分段上限 128；512 段触发 CDN 反滥用

## 待办
1. 百度实测（登录 + 分享下载，重点看 wxlist/转存/PCS 下载）
2. 123 实测（登录/匿名）
3. 迅雷调试模式测速定默认段数
4. 移动 139 / 天翼 189 / 阿里
5. 1.0 前全平台回归

## 隐私
- 用户要求：不要未经同意操控手机
- Cookie/token 只存 App 私有 SharedPreferences；日志不写 token
