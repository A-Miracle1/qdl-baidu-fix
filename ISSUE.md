# 百度网盘接入问题（当前唯一阻塞）

## 症状
在 App 里粘贴百度分享链接，点开始下载，1 秒内失败：

```
失败：百度：2 参数错误（可能缺 bdstoken）
阶段：解析分享
```

截图：`screenshots/05`、`06`、`07`、`08`、`09`。

## 最新日志（完整见 logs/qdl.log）
```
11-17 12:23:12 ===== start [baidu] https://pan.baidu.com/s/1TwY2Pb5mHZOjpyiiOUTCIg?pwd=v357
11-17 12:23:12 百度分享 TwY2Pb5mHZOjpyiiOUTCIg（有提取码）
11-17 12:23:12 百度 verify 成功 randsk=有
11-17 12:23:12 百度 bdstoken 获取成功
11-17 12:23:13 百度分享页 49576 字节，locals=false title=百度网盘-分享文件
11-17 12:23:13 locals 失败，改用 wxlist：百度：分享页解析失败，可能要在网页里过一次验证
11-17 12:23:13 ✗ 百度：2 参数错误（可能缺 bdstoken）
```
注：`app start（0.25）` 是历史硬编码字符串，不代表安装版本；12:23/12:29 两次都失败，页面都是 49576 字节、没有 locals。

## 已做的后台验证（无登录凭据，用用户给的公开分享链接）
URL：`https://pan.baidu.com/s/1TwY2Pb5mHZOjpyiiOUTCIg?pwd=v357`
- `POST /share/verify?surl=TwY2...&t=...&channel=chunlei&web=1&app_id=250528&clienttype=0`，body `pwd=v357&vcode=&vcode_str=` → `errno 0, randsk=...`，并 Set-Cookie `BDCLND`
- `GET /s/1TwY2...?pwd=v357`（手动跟随 302，Referer `https://pan.baidu.com/disk/home`）：
  - Cookie **只带 `BDCLND=<randsk>`** → **73999 字节**，title `小王不AI干正事_免费高速下载|百度网盘-分享无限制`，**有 `locals.mset({...})`**
  - Cookie **`BDCLND + 浏览器 BAIDUID`** → **49576 字节**，title `百度网盘-分享文件`，**没有 locals**（正是 App 里拿到的页面）
  - 只带 `BDCLND + 伪造 BAIDUID` 又回到 73999（说明是 BAIDUID 与 verify 会话不匹配导致）
- `share/list`（Cookie 只带 BDCLND）能递归列出文件
- `wxlist`：匿名 `errno 9013 need verify`；登录态 `errno 2` → 不可靠，只做兜底
- `share/tplconfig?fields=sign,timestamp&...&shareid=&uk=`：`BDCLND+verify 返回的 BAIDUID` 时能给 sign；`sharedownload` 匿名仍 `9019 need verify`
- 转存/下载必须 BDUSS 登录态，**此包不含账号，无法在这里验证**

## 代码位置（source/src/com/dsh/qdl/MainActivity.java）
- `bdCookie()`：百度请求 Cookie 白名单（BDUSS/STOKEN/BAIDUID/BDCLND/PANPSC）
- `bdShareCookie()`：**分享页应只返回 `BDCLND=<bdClnd>`**（当前实现如此，但日志仍是 49576，需重点排查是否真的走到了、bdClnd 是否为空、是否被别处覆盖）
- `bdGetRaw()`：手动跟随 302（每跳重新带 Cookie），Referer `https://pan.baidu.com/disk/home`
- `extractLocals()` / `bdLocals()`：花括号扫描 `locals.mset(...)`；已在真实页面验证可解析
- `bdVerify()`：`/share/verify` 拿 randsk（**不要 URLDecode**，放 Cookie 原样）
- `bdWxList()`：兜底，容易 9019/2
- `bdToken()`：`/api/gettemplatevariable?fields=["bdstoken","username","loginstate"]`
- `runJobBaidu()`：verify → bdToken → locals（失败再 wxlist）→ 取顶层 fs_id → `/api/create` 建 `/qdl-<ts>` → `/share/transfer`（bdstoken、async=2、fsidlist、path、sekey）→ `/api/list` 递归 → PCS `https://d.pcs.baidu.com/rest/2.0/pcs/file?method=download&app_id=250528&path=` 下载 → `/api/filemanager` 删临时目录
- `bdPcsUrl()`：百度普通 Web 会话 dlink 会 31362，改走 PCS 老端点；UA `pan.baidu.com`，Cookie BDUSS

## 重点怀疑 / 建议修法
1. **为什么 0.36 日志仍是 49576**：
   - 确认安装的是 0.36；确认 `bdShareCookie()` 真的只输出 BDCLND；
   - 在 `bdGetRaw` 打印实际 Cookie（BDCLND 长度即可）与最终 URL/标题；
   - 有可能 `bdClnd` 在 `bdToken()` 或别处被清空，或分享页请求走了带 BAIDUID 的 `bdCookie()`。
2. 直接用 **分享页 locals** 作为主路径（we verified），wxlist 只兜底；解析用花括号扫描，不要正则。
3. 转存参数按 BaiduPCS-Go/Alist：`bdstoken` 必须、`async=2`、form `fsidlist=[..]&path=/qdl-xxx`、`sekey` 可选（randsk 解码）；`/api/create` 带 `size=0&block_list=[]&clienttype=0`。
4. 建目录/转存后轮询 `/api/list`，再走 PCS 端点（不要 dlink，31362）；UA `pan.baidu.com` + BDUSS + Range；非会员单连接即可。
5. 删除临时目录可能 `errno 132` 安全验证，删不掉就提示用户手删，不要阻塞下载。
6. 测试凭据：包内没有。请用户在自己的 App 里登录百度后复现，或让用户提供**临时 BDUSS** 在后台跑；不要用别人的账号。

## 参考
- `notes/baidu-api-notes.md`（733 行，含 Alist `baidu_share` / BaiduPCS-Go 的接口与错误码、源码链接）
- Alist: drivers/baidu_share/driver.go、drivers/baidu_netdisk/util.go
- BaiduPCS-Go: baidupcs/transfer.go、download.go、pcserror/panerrorinfo.go
- 复现脚本：`tools/baidu-cookie-test.js`（无需登录，打印 73999/49576 两种页面）

## 构建 / 安装
```sh
cd source && sh build.sh
# 产物 source/app.apk；或直接装 apk/网盘直链下载-0.36-beta.apk（同签名，可覆盖）
```
真机：Android 13，包名 com.dsh.qdl；下载目录 `/sdcard/Download/DSH-Go/`，日志 `qdl.log`。
