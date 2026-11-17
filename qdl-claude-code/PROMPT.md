你在接手一个已经存在的 Android 项目「网盘直链下载」，不是从零开始。请先读本包的 `README.md`、`ENVIRONMENT.md`、`ISSUE.md`，再动手。

【它是什么】
独立 Android APK（包名 com.dsh.qdl，Java + WebView，无 Gradle/无第三方依赖）：粘贴网盘分享链接 → 解析 → 转存到用户网盘 → 取直链 → 原生 Java 分段（Range）下载到手机「下载/DSH-Go/」。账号在 App 内 WebView 登录后抓 Cookie/localStorage。目前夸克/UC/123/迅雷可用，**百度网盘没修好**，这是唯一阻塞。

【环境】
代码在 proot Ubuntu 24.04 aarch64（Android 13 手机上，无 root、无 adb、无模拟器）里用命令行构建：javac(android-23) → d8(/root/build/r8.jar) → aapt → zipalign → apksigner（keystore：source/qdl.keystore，密码 qdlqdl123）。源码 `source/`，构建脚本 `source/build.sh`。装包/操作只能由用户在真机完成；日志在 `/sdcard/Download/DSH-Go/qdl.log`。

【当前症状】
App 内粘百度分享链接，1 秒失败：`百度：2 参数错误（可能缺 bdstoken）`，阶段「解析分享」。日志（logs/qdl.log）显示：verify 成功、bdstoken 成功，但分享页只有 49576 字节、标题「百度网盘-分享文件」、没有 locals。

【已做的后台验证（无登录）】
用测试链接 `https://pan.baidu.com/s/1TwY2Pb5mHZOjpyiiOUTCIg?pwd=v357`：
- verify 拿 randsk 正常
- 分享页 Cookie **只带 BDCLND** → 73999 字节、标题「小王不AI干正事_免费高速下载…」、有 locals
- 分享页 Cookie **BDCLND + BAIDUID** → 49576 字节、标题「百度网盘-分享文件」、没有 locals（和 App 日志一致）
- `share/list` 用 BDCLND 可递归列文件
- `wxlist` 匿名 9013 / 登录 2，不可靠
- 转存/下载需要 BDUSS，包内没有账号凭据
复现脚本：`tools/baidu-cookie-test.js`。

【你的任务】
1. 先跑 `tools/baidu-cookie-test.js` 复现 73999 vs 49576
2. 修百度：分享页只带 BDCLND、手动跟随 302、花括号扫描 locals.mset；确认 App 实际发出的 Cookie（日志里 BDCLND 长度即可）
3. 打通后续：bdstoken → /api/create 建中转目录 → /share/transfer（async=2、fsidlist、path、可选 sekey）→ /api/list 递归 → PCS `https://d.pcs.baidu.com/rest/2.0/pcs/file?method=download&app_id=250528&path=` 下载（UA pan.baidu.com + BDUSS + Range，单连接）→ 清理（132 就提示手删）
4. 百度需要在真机登录一次验证；如果无法登录，向用户申请**临时 BDUSS**，不要用未授权账号
5. 不要破坏夸克/UC/123/迅雷现有功能

【验收】
真机上用测试链接能完整下载到「下载/DSH-Go/」；失败时 `qdl.log` 能定位到具体步骤；其余四平台回归不降低。

【注意】
- 用户要求：先在后台/真机跑通再交成品，不要连续给未验证的包
- 参考：notes/baidu-api-notes.md（Alist baidu_share / BaiduPCS-Go 接口与错误码）、notes/qdl-handoff.md
- 百度 dlink 31362 不可用；非会员限速约 40–50KB/s，多线程无收益
