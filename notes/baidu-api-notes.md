# 百度网盘 Web API 逆向笔记（BDUSS Cookie 登录 · 第三方 Android 下载器用）

> 目标：不申请 OAuth access_token，只用 WebView 登录后拿到的 Cookie（BDUSS / STOKEN / BAIDUID / BDCLND），
> 在 Java + `HttpURLConnection` 里完成「分享链接 → 转存 → 取直链 → 下载 → 清理」。
>
> 所有结论来自公开源码 / 公开文档 / 公开实测文章，**没有登录任何账号、没有抓包**。
> 每条都给了出处；凡是「社区实测」而非源码可证的，都单独标注。
>
> 采集时间：2026-11。百度接口随时会变，实现时务必把 errno 打日志。

---

## 0. 结论速览（先看这段）

| 环节 | 可用端点（BDUSS 即可） | 备注 |
|---|---|---|
| 分享链接初始化 | `POST https://pan.baidu.com/share/verify?surl=<surl>` + `pwd` | 返回 `randsk`，并 `Set-Cookie: BDCLND=<randsk>` |
| 分享文件列表 | `GET/POST /share/list?shareid=&uk=&dir=`（旧）<br>`POST /share/wxlist?...`（新，免 verify）<br>`GET /s/1<surl>` 页面里的 `locals.mset({...})` | `wxlist` 是 2023 后 alist 用的路子；页面法能一次拿全 `file_list` |
| 转存 | `POST https://pan.baidu.com/share/transfer?shareid=&from=&bdstoken=&ondup=&async=`<br>form: `fsidlist=[..]&path=/目标目录` | 要 `bdstoken`，**不要** access_token |
| 取自己网盘的直链 | `GET https://pan.baidu.com/api/filemetas?target=["/绝对路径"]&dlink=1&web=5&origin=dlna` | 需要 `info[0].dlink`；**2026-09 社区实测该 dlink 下载会 31362 sign error，见 §5.3** |
| 取分享内文件直链（不转存） | `POST https://pan.baidu.com/api/sharedownload?...&sign=&timestamp=` | `extra={"sekey":"<randsk>"}`；缺 sekey → `118 / -20` 验证码 |
| 下载（最可靠） | `GET https://d.pcs.baidu.com/rest/2.0/pcs/file?method=download&app_id=250528&path=<urlencode绝对路径>` | UA `pan.baidu.com` + Cookie `BDUSS`；原生支持 Range，**推荐主用** |
| 删除转存文件 | `POST https://pan.baidu.com/api/filemanager?opera=delete&bdstoken=`<br>form: `async=1&filelist=["/路径"]` | 有风控：可能 `errno 132 authwidget`，要降级为「提示用户手动删」 |
| bdstoken | `GET https://pan.baidu.com/api/gettemplatevariable?clienttype=0&app_id=250528&fields=["bdstoken","username","loginstate"]` | `result.bdstoken`；`loginstate!=1` 说明 Cookie 失效 |

**给 Android 实现的一句话建议：**
Cookie 只带 `BDUSS`（+`STOKEN`、`BAIDUID`、需要时 `BDCLND`）四个左右；UA 用 `netdisk` 或 `pan.baidu.com`；
分享列表走 `share/wxlist` 或分享页 `locals.mset`；转存走 `share/transfer`；**直链不要迷信 `api/filemetas`，
直接走 `d.pcs.baidu.com/rest/2.0/pcs/file?method=download&path=...` + Range 分块**，这条 2013 年的老路在 2026 年仍是最稳的。

---

## 1. 登录：哪些 Cookie 是必须的

### 1.1 需要的 Cookie

| Cookie | 域 | 作用 | 是否必须 |
|---|---|---|---|
| `BDUSS` | `.baidu.com` | 百度 passport 登录态，所有 pan API 的身份凭证 | **必须** |
| `STOKEN` | `pan.baidu.com`（也有 `passport.baidu.com` 一份） | 网盘会话 token；分享页 / 部分分享接口认它 | 分享相关**必须**，纯 `/api/*` 文件操作可缺 |
| `BAIDUID` / `BAIDUID_BFESS` | `.baidu.com` | 设备标识，风控相关 | 强烈建议带上 |
| `BDUSS_BFESS` | `.baidu.com` | 新版 `BDUSS` 的 HttpOnly/更严格副本，网页端同时下发 | 建议原样回传（有就带） |
| `BDCLND` | `.baidu.com` | **不是登录 Cookie**，是「分享提取码通过」的凭证（= `randsk`） | 分享流程中必须 |
| `ndut_fmt` / `PANWEB` / `PTOKEN` / `BIDUPSID` / `H_PS_PSSID` / `PSINO` | — | 辅助 | 可选 |

关键源码：`skyzhao1223/baidu-pan-skill` 的 `scripts/bdpan_common.py` 把「必须」和「可发」写死了：

```python
REQUIRED_COOKIES = ["BDUSS", "STOKEN", "BAIDUID"]
SEND_COOKIES = [
    "BDUSS", "BDUSS_BFESS", "STOKEN", "PTOKEN", "BAIDUID", "BAIDUID_BFESS",
    "PSTM", "BIDUPSID", "H_PS_PSSID", "PSINO", "ndut_fmt", "PANWEB", "PANPSC",
]
```
<https://github.com/skyzhao1223/baidu-pan-skill/blob/main/scripts/bdpan_common.py>

> **坑（实测）**：把浏览器里 100+ 个 Cookie 全塞进请求头，`pan.baidu.com` 的 nginx 直接
> `400 Request Header Or Cookie Too Large`。只发白名单里的十来个。
> <https://blog.csdn.net/boyzhaotian/article/details/166349848>

### 1.2 BDUSS 单独够不够？

- **够**：BaiduPCS-Go 的构造函数就是 `NewPCS(appID, bduss)`，只塞一个 `BDUSS` Cookie 就能跑
  `/api/list`、`/api/filemetas`、`/api/filemanager`、上传下载等（分享页除外）。
  <https://github.com/qjfoidnh/BaiduPCS-Go/blob/main/baidupcs/baidupcs.go>
- **不够**：分享页 `/s/1xxxx` 与 `share/*` 系列要 `STOKEN`（BaiduPCS-Go 的 `AccessSharePage`
  在页面里找不到 `loginstate` 时会直接报「请确认登录参数中已经包含了网盘 STOKEN」）。
  <https://github.com/qjfoidnh/BaiduPCS-Go/blob/main/baidupcs/transfer.go>
- 如果你后面又想用 `access_token` 系接口（`/rest/2.0/xpan/*`），那是另一套凭证，BDUSS 不被接受（见 §5.2）。

### 1.3 WebView 登录后 Cookie 在哪

1. WebView 打开 `https://pan.baidu.com`，用户完成账号密码 / 扫码登录；
2. 登录成功由 `passport.baidu.com` 下发 `BDUSS`、`BAIDUID`（域 `.baidu.com`）；
3. `pan.baidu.com` 再下发 `STOKEN`（域 `pan.baidu.com`）、`PANWEB` 等；
4. Android 侧用 `CookieManager.getInstance().getCookie("https://pan.baidu.com")` 拿到 `name=value; ...` 串，
   只解析并保存白名单里的键值。**注意 `BDUSS_BFESS` 是 HttpOnly，`getCookie()` 拿不到时它本来也非必须**，
   能拿到就带上。

> 注意 `BDUSS` 有两份（`.baidu.com` 一份、`passport.baidu.com` 一份），实现时按域优先级取
> `pan.baidu.com` 那一份（社区实测文章「坑二」）。

### 1.4 登录态自检

```http
GET https://pan.baidu.com/api/gettemplatevariable?clienttype=0&app_id=250528&fields=["bdstoken","username","loginstate"]
Cookie: BDUSS=...
User-Agent: netdisk
```
```json
{"errno":0,"result":{"bdstoken":"a1b2c3...","username":"xxx","loginstate":1},"request_id":123}
```
`loginstate != 1` 或 `errno = -6` → Cookie 失效，让用户重新登录。
<https://github.com/skyzhao1223/baidu-pan-skill/blob/main/scripts/bdpan_common.py>

---

## 2. 分享链接 `https://pan.baidu.com/s/1xxxxx?pwd=abcd`

### 2.1 解析 surl

- 页面 URL 里的串 **带** 前导 `1`：`/s/1AbCdEf...`
- `share/verify`、`share/wxlist` 要的 `surl` 是 **去掉前导 1** 的：`AbCdEf...`
- `?pwd=abcd` 中的 4 位提取码另取。

```python
m = re.search(r"pan\.baidu\.com/s/1([\w-]+)", url); surl = m.group(1)
```
<https://github.com/skyzhao1223/baidu-pan-skill/blob/main/scripts/bdpan_common.py>

### 2.2 `share/verify`（有提取码时）

```http
POST https://pan.baidu.com/share/verify?surl=<surl>&t=<毫秒时间戳>&channel=chunlei&web=1&app_id=250528&clienttype=0
Content-Type: application/x-www-form-urlencoded
User-Agent: netdisk            （或浏览器 UA）
Referer: https://pan.baidu.com/share/init?surl=<surl>
Cookie: BDUSS=...; STOKEN=...; BAIDUID=...

pwd=abcd&vcode=&vcode_str=
```
响应（成功）：
```json
{"errno":0,"randsk":"%2Bxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx%3D","shareid":1234567890,"uk":123456789}
```
同时响应头下发 `Set-Cookie: BDCLND=<randsk>; ...`（`randsk` 是 URL 编码的，放进 Cookie 时**原样**放）。

- 失败：`errno = -9`（提取码错误）、`errno = -12`（访问密码错误）、`errno = -19/-62`（需要验证码），
  HTTP 302 到 `wappass.baidu.com`（见 §8）。
- 出处：`baidu-pan-skill` 的 `verify_share()`；PHP 复刻 `baiduyun-pure/functions.php` 的 `verifyPwd()`
  注释 `// -12 提取码错误`；还有老教程里 `surl` 直接拼在 query 上的写法（不带 `t` 也能过，但建议带）。
  <https://github.com/skyzhao1223/baidu-pan-skill/blob/main/scripts/bdpan_common.py>
  <https://github.com/zenskcode/baiduyun-pure/blob/main/functions.php>

### 2.3 拿 `shareid / uk(share_uk) / file_list`：两条路

#### 路线 A（推荐）：抓分享页 HTML 里的 `locals.mset({...})`

```http
GET https://pan.baidu.com/s/1<surl>?pwd=abcd
Cookie: BDUSS=...; STOKEN=...; BDCLND=<randsk>
User-Agent: 浏览器 UA 或 netdisk
Referer: https://pan.baidu.com/disk/home
```
页面里有一行：

```js
locals.mset({"bdstoken":"...","uk":123456789,"share_uk":123456789,"shareid":1234567890,
  "linkusername":"分享者昵称","expiredType":0,
  "file_list":[{"fs_id":123456789012345,"server_filename":"a.mkv","path":"/a.mkv",
                "size":740378697,"isdir":0,"md5":"458a09d08pdf9379b39f57bdc94d4dd2","duration":3605}]});
```
正则 `locals\.mset\((\{.*?\})\);`（新版页面是 `try{{...}}`，去掉 `);` 终结符更稳）。

> BaiduPCS-Go 用同一条路，并且用 `netdisk` 客户端 UA + `Referer: https://pan.baidu.com/disk/home`
> 才不会被反爬踢到「无 loginstate 的登录页」（issue #534 的修复注释）。
> <https://github.com/qjfoidnh/BaiduPCS-Go/blob/main/baidupcs/transfer.go>

#### 路线 B：`share/wxlist`（微信小程序通道，**不需要 verify**，一箭双雕）

```http
POST https://pan.baidu.com/share/wxlist?channel=weixin&version=2.2.2&clienttype=25&web=1
Content-Type: application/x-www-form-urlencoded
User-Agent: netdisk
Cookie: BDUSS=...

pwd=abcd&root=1&shorturl=<surl>
```
```json
{"errno":0,"data":{"list":[{"path":"/","fs_id":1}],"uk":123456789,"shareid":1234567890,
 "seckey":"xxxxxxxx","has_more":false}}
```
- `data.seckey` **就是后续 `sharedownload` 里的 `sekey`**（注意拼写 `seckey` vs `sekey`）；
- `data.shareid`、`data.uk` 直出；
- 列表请求把 `root=1` 换成 `root=0` 并加 `dir=/子目录`，`num=1000`、`page=n`、`order=time`。

```http
POST https://pan.baidu.com/share/wxlist?channel=weixin&version=2.2.2&clienttype=25&web=1
pwd=abcd&dir=%2F子目录&root=0&shorturl=<surl>&num=1000&page=1&order=time
```
响应 `data.list[]` 字段：`fs_id`、`server_filename`、`isdir`、`size`、`path`、`server_mtime`、`md5`，
`data.has_more` 控制翻页。

出典：alist `drivers/baidu_share/driver.go`（Init + List 全在里面）
<https://github.com/AlistGo/alist/blob/main/drivers/baidu_share/driver.go>

### 2.4 `share/list`（老牌接口，仍在用）

```http
GET https://pan.baidu.com/share/list?shareid=<shareid>&uk=<uk>&dir=%2F
Cookie: BDUSS=...; STOKEN=...; BDCLND=<randsk>
User-Agent: 浏览器 UA
Referer: https://pan.baidu.com/disk/home
```
```json
{"errno":0,"list":[
  {"category":6,"dlink":"","file_name":"a.mkv","fs_id":123456789012345,"isdir":0,
   "md5":"...","path":"/a.mkv","server_ctime":1700000000,"server_mtime":1700000000,
   "server_filename":"a.mkv","size":740378697,"thumbs":{}}],"request_id":123}
```
- 分页：`&page=1&num=100&order=time&desc=1&showempty=0&web=1&channel=chunlei&app_id=250528&bdstoken=<bdstoken>`
- BaiduPCS-Go 里被注释掉的 `SuperTransfer` 保留了这套参数（`page/num/shorturl/root/app_id/channel/clienttype/web`），
  可当作参数清单参考：<https://github.com/qjfoidnh/BaiduPCS-Go/blob/main/baidupcs/transfer.go>
- PHP 实现 `GetDirRemote()`：<https://github.com/zenskcode/baiduyun-pure/blob/main/functions.php>

> **递归列目录**：这些接口都是单层。递归 = 对每个 `isdir==1` 的项用 `dir=<它的 path>` 再请求一次，
> 深度优先，注意 `path` 是网盘绝对路径（`/foo/bar`），不是相对路径。

### 2.5 `xpan/share?list`（OAuth 版，BDUSS 用不了）

`https://pan.baidu.com/rest/2.0/xpan/share?method=list&share_id=..&uk=..&dir=..&access_token=..`
是开放平台接口，要 `access_token`，**BDUSS Cookie 不行**。别混用。

---

## 3. 转存到自己网盘 `/share/transfer`

### 3.1 前置：bdstoken + 目标目录

```http
GET https://pan.baidu.com/api/gettemplatevariable?clienttype=0&app_id=250528&fields=["bdstoken","username","loginstate"]
```
建目录（不存在时）：
```http
POST https://pan.baidu.com/api/create?a=commit&bdstoken=<bdstoken>&channel=chunlei&web=1&clienttype=0
Content-Type: application/x-www-form-urlencoded

path=/中转目录&isdir=1&size=0&block_list=[]
```
（已存在会返回非 0 errno，忽略即可）

### 3.2 转存请求

```http
POST https://pan.baidu.com/share/transfer?shareid=<shareid>&from=<uk>&bdstoken=<bdstoken>&ondup=newcopy&async=2&channel=chunlei&web=1&clienttype=0
Content-Type: application/x-www-form-urlencoded
User-Agent: netdisk
Referer: https://pan.baidu.com/disk/home
Cookie: BDUSS=...; STOKEN=...; BDCLND=<randsk>

fsidlist=[123456789012345,123456789012346]&path=/中转目录
```

参数说明：

| 参数 | 位置 | 值 | 说明 |
|---|---|---|---|
| `shareid` | query | 数字 | 来自 `locals.mset` / `wxlist` / `share/list` |
| `from` | query | 数字 | 分享者 uk（= `share_uk`） |
| `bdstoken` | query | 字符串 | 必须 |
| `sekey` | query | 字符串 | **老版本参数**，= `randsk`/`seckey`；新版可不传（`bdpan_share.py` 就没传） |
| `ondup` | query | `newcopy` / `fail` | 重名策略；`newcopy` = 自动改名，`fail` = 报错 |
| `async` | query | `1` / `2` | `2` 时立即返回 `extra.list[]` 映射；`1`/缺省可能要轮询目录 |
| `channel=chunlei&web=1&clienttype=0&app_id=250528` | query | 固定 | 少一个就可能 `errno 2` |
| `fsidlist` | **form** | `[1,2]` JSON 数组字符串 | 要转存的 fs_id 列表，单次上限 999 |
| `path` | **form** | `/中转目录` | 目标目录，**必须已存在** |

成功响应（`async=2`）：
```json
{"errno":0,"request_id":123,
 "extra":{"list":[
   {"from_fs_id":123456789012345,"to_fs_id":987654321098765,"to":"/中转目录/a.mkv"},
   {"from_fs_id":123456789012346,"to_fs_id":987654321098766,"to":"/中转目录/b/"}]}}
```
`extra.list[].to` 就是新文件的网盘绝对路径 —— **后面的 `dlink` / `pcs/file?path=` 直接用这个**。
`from_fs_id` ↔ 分享里的 `fs_id` 一一对应，用于把「用户勾选的文件」和「转存结果」对上。

失败响应（`async=1` 或 `errno=12`）：
```json
{"errno":12,"request_id":123,
 "info":[{"errno":-30,"path":"/中转目录/a.mkv"}],
 "target_file_nums":3,"target_file_nums_limit":999}
```
- `errno=12` 表示「部分/全部转存失败」，具体看 `info[].errno`：
  - `-30` → 目标目录已有同名文件/文件夹（用 `ondup=newcopy` 可避免）
  - `-33` → 一次超过 999 个
  - 其它 → 原样透出
- `target_file_nums > target_file_nums_limit` → 转存数量超当前用户上限
- `errno=4` → 文件重复（老版本巴度返回）
- `errno=2` → 参数错误（十有八九是 `bdstoken` 没带或 `path` 不存在）
- 出典：`BaiduPCS-Go/baidupcs/transfer.go` 的 `GenerateRequestQuery()` 逐个 errno 的判断
  <https://github.com/qjfoidnh/BaiduPCS-Go/blob/main/baidupcs/transfer.go>
  以及 `baidu-pan-skill` 的 `cmd_save()`
  <https://github.com/skyzhao1223/baidu-pan-skill/blob/main/scripts/bdpan_share.py>

> `async=2` 不是万能的：如果 `extra` 为空，退化为「轮询 `GET /api/list?dir=/中转目录`，直到出现期望文件名」，
> 每 2s 一次、最多 ~60s。

### 3.3 是否需要 bdstoken？

**需要**。`share/transfer` 不带 `bdstoken` 基本必定 `errno 2`。搜索型用户脚本（`百度网盘文件转存助手`）
也是先 `getBdstoken()` 再 `getRandsk()`：
<https://greasyfork.org/en/scripts/513120/code>

---

## 4. 取自己网盘文件的 dlink

### 4.1 路线一：`/api/filemetas`（BDUSS 可用，按**路径**）

```http
GET https://pan.baidu.com/api/filemetas?target=%5B%22%2F%E4%B8%AD%E8%BD%AC%E7%9B%AE%E5%BD%95%2Fa.mkv%22%5D&dlink=1&web=5&origin=dlna&app_id=250528&channel=chunlei&clienttype=0
Cookie: BDUSS=...
User-Agent: netdisk
```
即：
```
target=["/中转目录/a.mkv"]&dlink=1&web=5&origin=dlna
```
响应：
```json
{"errno":0,"request_id":123,"info":[
  {"errno":0,"fs_id":987654321098765,"path":"/中转目录/a.mkv","server_filename":"a.mkv",
   "size":740378697,"isdir":0,"category":1,"md5":"...",
   "dlink":"https://d.pcs.baidu.com/file/458a09d0...?fid=...&sign=...&expires=8h&chkbd=0"}]}
```
- alist 的「crack」下载方式就是这四件套（`target` + `dlink=1` + `web=5` + `origin=dlna`），
  UA 用 `netdisk`：<https://github.com/AlistGo/alist/blob/main/drivers/baidu_netdisk/util.go>
- **注意**：alist 的 `request()` 同时会带 `access_token`（它走 OAuth）。但不带 access_token 时该 web 接口
  也能用 BDUSS Cookie 调用（PanDownload 时代起就是这么用的）。
- `target` 里是 **网盘绝对路径**，不是 fsid。`fsid` 版本请用 `xpan/multimedia`（见 4.2）。

### 4.2 路线二：`/rest/2.0/xpan/multimedia?method=filemetas`（**必须 access_token**）

```http
GET https://pan.baidu.com/rest/2.0/xpan/multimedia?method=filemetas&access_token=xxx&fsids=[123456789012345]&dlink=1&thumb=1&extra=1
User-Agent: pan.baidu.com
```
```json
{"errno":0,"list":[{"fs_id":123456789012345,"path":"/x.mkv","server_filename":"x.mkv",
  "size":740378697,"isdir":0,"dlink":"https://d.pcs.baidu.com/file/...?fid=..&sign=..&expires=8h"}]}
```
- 官方文档明确：`dlink` 需拼 `access_token` 且请求头 `User-Agent: pan.baidu.com`，**8 小时有效**。
  <https://pan.baidu.com/union/doc/基础网盘服务/获取文件信息/查询文件信息>
- 用 BDUSS 调 xpan 系列会得到 `errno = -6 / 2 / 111`（未登录/参数错误/token 失效）。alist 在
  `errno ∈ {111,-6,20016}` 时刷新 access_token 的处理可见
  <https://github.com/AlistGo/alist/blob/main/drivers/baidu_netdisk/util.go>
- 结论：**fsid 批量取 dlink 只有 OAuth 路**；想纯 Cookie，就先 `api/list` 把 fsid → path 换出来，再走 4.1。

### 4.3 路线三：`/api/download?sign=&timestamp=&fidlist=`（BaiduPCS-Go 的「获取下载链接2」）

```http
POST https://pan.baidu.com/api/download?sign=<sign>&timestamp=<ts>&type=dlink&channel=chunlei&web=1&app_id=250528&clienttype=0
Content-Type: application/x-www-form-urlencoded

fidlist=[123456789012345]&type=dlink
```
```json
{"errno":0,"dlink":[{"dlink":"https://d.pcs.baidu.com/file/...","fs_id":"123456789012345"}]}
```
- `sign` / `timestamp` 来自 `https://pan.baidu.com/share/tplconfig?fields=sign,timestamp&channel=chunlei&web=1&app_id=250528&clienttype=0`
  （alist 用它给 `sharedownload` 签名）
- `errno 112` = 页面已过期、`113` = 签名错误 → 重新取 sign/timestamp
- 出典：`BaiduPCS-Go/baidupcs/download.go` 的 `LocatePanAPIDownload()` + `pcserror` 注释
  <https://github.com/qjfoidnh/BaiduPCS-Go/blob/main/baidupcs/download.go>

### 4.4 分享内文件拿直链（不转存）：`/api/sharedownload`

```http
POST https://pan.baidu.com/api/sharedownload?app_id=250528&channel=chunlei&clienttype=12&sign=<sign>&timestamp=<ts>&web=1
Cookie: BDUSS=...; STOKEN=...; BDCLND=<randsk>
Referer: https://pan.baidu.com/disk/home
Content-Type: application/x-www-form-urlencoded

encrypt=0&extra=%7B%22sekey%22%3A%22<randsk去URL编码>%22%7D&fid_list=[123456789012345]&primaryid=<shareid>&uk=<uk>&product=share&type=nolimit
```
- 源码注释直接给了错误码语义：**没有 `Referer` → `112`；没有 `sekey` → `118` / `-20` 出验证码**
  <https://github.com/zenskcode/baiduyun-pure/blob/main/functions.php>
- `<50MB` 时可把 `clienttype=12` 换成 `clienttype=0` 并加 `bdstoken`（注释「获取直链 50MB以内」）。

---

## 5. 下载 dlink

### 5.1 最可靠：PCS 老端点（推荐）

```http
GET https://d.pcs.baidu.com/rest/2.0/pcs/file?method=download&app_id=250528&path=%2F%E4%B8%AD%E8%BD%AC%2Fa.mkv
User-Agent: pan.baidu.com
Cookie: BDUSS=...
Range: bytes=0-4194303
Accept-Encoding: identity
```
- `path` = 网盘绝对路径（URL 编码），`app_id=250528` 固定；
- 返回 `200` 或 `206 Partial Content`，**原生支持 Range**，可以 4MB 分块断点续传；
- UA 必须像客户端：`pan.baidu.com`（`baidu-pan-skill` 的 `DOWNLOAD_UA`）或 `netdisk`（alist 默认 `CustomCrackUA`）；
- 需要一个有效的 `BDUSS` Cookie（这条是「你账号里的文件」，不是签名直链）。

```python
PCS_URL = "https://d.pcs.baidu.com/rest/2.0/pcs/file"
url = PCS_URL + "?method=download&app_id=250528&path=" + urllib.parse.quote(remote)
headers = {"User-Agent": "pan.baidu.com", "Cookie": cookie_header,
           "Range": f"bytes={start}-{end}", "Accept-Encoding": "identity"}
```
<https://github.com/skyzhao1223/baidu-pan-skill/blob/main/scripts/bdpan_download.py>

### 5.2 dlink 下载（`d.pcs.baidu.com/file/...`）

- 302 → `d.pcs.baidu.com/file/...?fid=&sign=&expires=8h&chkbd=0`；
- 有效期内**不用 Cookie**，但要 UA 与签名匹配；
- 官方 OAuth 文档要求 `User-Agent: pan.baidu.com` 且 URL 拼 `access_token`；
- **2026-09 社区实测**：用 BDUSS 从 `api/filemetas` 拿到的 dlink，无论 UA 用 `pan.baidu.com` 还是模拟客户端，
  一律 `{"error_code":31362,"error_msg":"sign error"}`；「先请求一次拿 `PANPSC` Cookie 再重试」的偏方也无效。
  该作者的结论是：**普通 Web 会话下 dlink 通道已死，只有 PCS 老端点还能下**。
  <https://blog.csdn.net/boyzhaotian/article/details/166349848>
- 反向证据：alist `linkOfficial` 走的就是 dlink + `access_token` + `UA: pan.baidu.com`，OAuth 账号仍可用
  <https://github.com/AlistGo/alist/blob/main/drivers/baidu_netdisk/util.go>。
- 所以：**有 access_token 走 dlink；只有 BDUSS 就走 §5.1**。两者都实现、失败自动降级最稳。

### 5.3 限速与并发（重要，别浪费工期）

社区实测（2026-09，非会员账号）：

| 并发 Range 连接数 | 单连接速度 | 聚合速度 |
|---|---|---|
| 1 | ~41 KB/s | 41 KB/s |
| 4 | 12–38 KB/s | 46 KB/s |
| 8 | 0–38 KB/s | 53 KB/s |
| 16 | 0–38 KB/s | 53 KB/s |

结论：**限速是账号级聚合的**，多线程不会变快，只会互相抢带宽 + 更容易触发风控。
1.4GB ≈ 8 小时。想要快只有 SVIP。
<https://blog.csdn.net/boyzhaotian/article/details/166349848>
（`baidu-pan-skill` 的 `LOW_SPEED_BPS = 100*1024` 也是同一判断。）

### 5.4 403 的常见原因

| 现象 | 原因 | 处置 |
|---|---|---|
| `403` + 正文含 `31045`/`31064`/`31066`/`user not exists`/`auth fail` | Cookie 失效 | 重新登录，刷新 BDUSS |
| `403` + `error_code 31362 sign error` | dlink 签名与会话/UA 不匹配 | 改走 PCS 端点 |
| `403` + `expires` 过期 | dlink 8 小时到期 | 重新取 dlink |
| `403` 偶发（Cookie 明明有效） | 百度鉴权后端抖动 | 退避重试 2–3 次（`probe_size` 就是这么做的） |
| `400 Request Header Or Cookie Too Large` | Cookie 太多 | 只发白名单 |

出处：`baidu-pan-skill` 的 `is_auth_error()` / `probe_size()`；CSDN 实测文。

---

## 6. 删除转存的文件

### 6.1 Web 接口

```http
POST https://pan.baidu.com/api/filemanager?opera=delete&bdstoken=<bdstoken>&channel=chunlei&web=1&clienttype=0&app_id=250528
Content-Type: application/x-www-form-urlencoded
Cookie: BDUSS=...; STOKEN=...

async=1&filelist=["/中转目录/a.mkv","/中转目录/b"]
```
- `filelist` 是 **网盘绝对路径的 JSON 数组字符串**；
- `bdstoken` 必须，获取方式同 §3.1；
- `opera` 还可以是 `copy` / `move` / `rename` / `mkdir`。

### 6.2 风控（必须做降级）

2026-09 实测：`api/filemanager?opera=delete` 返回 `errno 132` + `authwidget`，
即「您的帐号可能存在安全风险……请先进行安全验证」——**自动删除此路不通，只能让用户手动删**。
<https://blog.csdn.net/boyzhaotian/article/details/166349848>
（`errno 132` 的语义在 BaiduPCS-Go 的错误表里也是这个：
<https://github.com/qjfoidnh/BaiduPCS-Go/blob/main/baidupcs/pcserror/panerrorinfo.go>）

### 6.3 备用：PCS `file/delete`（multipart）

```
POST https://d.pcs.baidu.com/rest/2.0/pcs/file?method=delete&app_id=250528
form field "param" = {"list":[{"path":"/中转目录/a.mkv"}]}
```
出典：`BaiduPCS-Go/baidupcs/prepare.go` 的 `PrepareRemove()`（`PathsListJSON`）。
同样可能被 132 拦。

### 6.4 备用：xpan `filemanager`（要 access_token）

```
POST https://pan.baidu.com/rest/2.0/xpan/file?method=filemanager&opera=delete&access_token=...
async=0&filelist=["/path"]&ondup=fail
```
出典：alist `manage()` <https://github.com/AlistGo/alist/blob/main/drivers/baidu_netdisk/util.go>

---

## 7. 错误码表

### 7.1 pan web API（`errno`）

来自 BaiduPCS-Go 的 `FindPanErr()`（业界最全的一份 web errno 表）：
<https://github.com/qjfoidnh/BaiduPCS-Go/blob/main/baidupcs/pcserror/panerrorinfo.go>

| errno | 含义 | 场景 |
|---|---|---|
| `0` | 成功 | |
| `-1` | 分享内容违规，分享功能被禁用 | 分享 |
| `-2` | 用户不存在 | |
| `-3` | 文件不存在 | |
| `-4` | 登录信息有误 | |
| `-6` | **请重新登录** | Cookie 失效 / 未登录 |
| `-7` | 分享已删除或已取消 | |
| `-8` | 已存在同名文件 | |
| `-9` | **文件不存在**（分享验证场景常被解释为「提取码错误」） | verify / list |
| `-10` | 分享外链达上限 100000 | |
| `-11` | 验证 Cookie 无效 | |
| `-12` | **访问密码错误** | verify |
| `-19` | **需要输入验证码** | 风控 |
| `-30` | 文件已存在 | transfer 的 `info[].errno` |
| `-31` | 文件保存失败 | transfer |
| `-33` | 一次最多 999 个 | transfer |
| `-62` | 可能需要输入验证码 | 风控 |
| `2` | **参数错误 / 请更换保存路径** | 缺 bdstoken、path 不存在 |
| `3` | 未登录或帐号无效 | |
| `4` | 存储异常 / 文件重复 | |
| `12` | **转存失败**（细节看 `info[].errno`） | transfer |
| `105` | 链接错误没找到文件 | 分享链接 |
| `108` | 文件名有敏感词 | |
| `111` | **access_token 未授权/失效** | xpan 系（alist 会刷新 token） |
| `112` | **页面已过期**（常见于缺 Referer） | sharedownload / download |
| `113` | **签名错误** | sign/timestamp 过期 |
| `114` | 任务不存在 | |
| `115` | 该文件禁止分享 | |
| `132` | **帐号存在安全风险，需安全验证（authwidget）** | 删除等敏感操作 |
| `9019` | access_token 未设置或过期 | 开放平台 |
| `8001` | 已触发验证，请稍后再试 | 分享页（BaiduPCS-Go `AccessSharePage`） |

### 7.2 PCS / 下载侧

| 码 | 含义 | 出处 |
|---|---|---|
| `31066` | 文件不存在（官方错误码文档里 `13075` 明确写「PCS 返回 31066，文件不存在」） | 官方错误码页 |
| `31066`（下载失败提示） | 文件名含非法字符 / 文件已被删 | 知乎问答 |
| `31362` | dlink `sign error` | CSDN 实测 |
| `31023` | 参数/接口不支持 | alist `31023 == errno` 特判 |
| `31045` `31064` | 身份被拒（`auth fail`） | `baidu-pan-skill` |

官方错误码页（分享服务，含公共码 -1/-6/2）：
<https://pan.baidu.com/union/doc/基础网盘服务/文件分享服务新/错误码/>

> 提醒：`-9` 在不同接口语义不同 —— 分享验证里是「提取码错」，`share/transfer` 里就是「文件不存在」。
> 别只按码翻译，要结合接口 + `show_msg` / `info[].errno`。

---

## 8. 验证码 / 风控 / 302 / 过期

1. **`share/verify` 被风控**：HTTP 302 到 `https://wappass.baidu.com/static/captcha/...`，或返回 HTML 含
   `验证码`。`baidu-pan-skill` 的处理：提示用户「在浏览器里打开一次链接，再回来重试」。
2. **`sharedownload` 缺 `sekey`** → `errno 118` / `-20`，页面弹验证码；缺 `Referer` → `112`。
3. **`-19` / `-62` / `8001`**：需要验证码 / 已触发验证。不要硬刚，暂停任务 + 提示用户。
4. **删除 `errno 132 authwidget`**：安全验证，自动删不了。
5. **dlink 302**：`https://d.pcs.baidu.com/file/...` 会上跳 `d.pcs.baidu.com` / CDN 节点，Java 里要么
   `setInstanceFollowRedirects(true)`（同协议 GET 会自动跟），要么手动读 `Location` 再发一次。
   alist 的做法：`HEAD` 不跟随，取 `Location` 作为最终 URL 并沿用 `UA: pan.baidu.com`。
6. **URL 有效期**：dlink 约 **8 小时**（官方文档）；PCS `method=download` 的 URL 本身不带签名、长期可用，
   但依赖 Cookie，Cookie 失效即 403。长任务要在 403 时刷新 Cookie + 重取 URL。
7. **UA 反爬**：Chrome 76 UA 现已被踢到「无 loginstate 的登录页」，BaiduPCS-Go 改用 netdisk 客户端 UA
   （`NetdiskUA = "netdisk;P2SP;3.0.0.8;netdisk;11.12.3;ANG-AN00;android-android;10.0;JSbridge4.4.0;jointBridge;1.1.0;"`）。
   <https://github.com/qjfoidnh/BaiduPCS-Go/blob/main/baidupcs/baidupcs.go>

---

## 9. 端到端时序（贴分享 → 转存 → 下载 → 清理）

```
① 解析
   url = "https://pan.baidu.com/s/1AbCd?pwd=efgh"
   surl = "AbCd"（去掉前导 1）; pwd = "efgh"

② 校验提取码
   POST /share/verify?surl=AbCd&t=<ms>&channel=chunlei&web=1&app_id=250528&clienttype=0
   body: pwd=efgh&vcode=&vcode_str=
   → 200 {"errno":0,"randsk":"%2B..."} + Set-Cookie: BDCLND=%2B...
   （errno -9/-12 → 密码错；HTTP 302 wappass → 风控，转人工）

③ 拿 shareid / uk / 文件清单
   方案A: GET /s/1AbCd?pwd=efgh（带 BDCLND）→ 正则 locals.mset({...})
   方案B: POST /share/wxlist?...&shorturl=AbCd  body: pwd=efgh&root=1
   递归: 对 isdir 的项用 dir=<path> 再 list；收集 (fs_id, path, size, isdir)

④ 准备
   GET /api/gettemplatevariable?fields=["bdstoken","loginstate"] → bdstoken
   loginstate≠1 → 让用户重新登录
   POST /api/create?a=commit&bdstoken=..  body: path=/中转目录&isdir=1&size=0&block_list=[]

⑤ 转存（只转用户勾选的，或全部）
   POST /share/transfer?shareid=..&from=..&bdstoken=..&ondup=newcopy&async=2&channel=chunlei&web=1&clienttype=0
   body: fsidlist=[...]&path=/中转目录
   → extra.list[]: {from_fs_id, to_fs_id, to:"/中转目录/xxx"}

⑥ 展开目录
   GET /api/list?dir=/中转目录/xxx&order=name&start=0&num=100&bdstoken=..
   递归子目录；得到每个文件的 path + size

⑦ 下载
   GET https://d.pcs.baidu.com/rest/2.0/pcs/file?method=download&app_id=250528&path=<urlenc(path)>
   Header: UA: pan.baidu.com, Cookie: BDUSS=..., Range: bytes=a-b
   4MB 分块 + Content-Range 校验 + *.dlstate.json 续传账本
   （想用 dlink 就先 api/filemetas 取，失败/31362 再降级到这条）

⑧ 清理
   POST /api/filemanager?opera=delete&bdstoken=..  body: async=1&filelist=["/中转目录"]
   errno 132 → 提示用户手动删；只清理「本次新建」的目录，别碰用户已有文件
```

### 9.1 Java / HttpURLConnection 骨架

```java
// ---------- 常量 ----------
static final String UA_PAN = "pan.baidu.com";
static final String UA_NETDISK = "netdisk;P2SP;3.0.0.8;netdisk;11.12.3;ANG-AN00;android-android;10.0;JSbridge4.4.0;jointBridge;1.1.0;";
static final String APP_ID = "250528";

// ---------- Cookie ----------
// 只发白名单，避免 400 Cookie Too Large
static String cookieHeader(Map<String,String> c) {
    StringBuilder sb = new StringBuilder();
    for (String k : new String[]{"BDUSS","BDUSS_BFESS","STOKEN","BAIDUID","BDCLND","PANWEB","ndut_fmt"})
        if (c.containsKey(k)) sb.append(k).append('=').append(c.get(k)).append("; ");
    return sb.toString();
}

// ---------- GET + 自动不跟随 302 ----------
static String get(String url, String ua, String cookie, String referer) throws IOException {
    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
    c.setRequestMethod("GET");
    c.setConnectTimeout(15000);
    c.setReadTimeout(30000);
    c.setInstanceFollowRedirects(false);        // dlink 302 要自己看 Location
    c.setRequestProperty("User-Agent", ua);
    c.setRequestProperty("Cookie", cookie);
    c.setRequestProperty("Accept-Encoding", "identity");
    if (referer != null) c.setRequestProperty("Referer", referer);
    int code = c.getResponseCode();
    InputStream in = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
    return readAll(in);                          // 302 时自己取 c.getHeaderField("Location")
}

// ---------- POST form ----------
static String postForm(String url, String body, String ua, String cookie) throws IOException {
    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
    c.setRequestMethod("POST");
    c.setDoOutput(true);
    c.setConnectTimeout(15000);
    c.setReadTimeout(30000);
    c.setRequestProperty("User-Agent", ua);
    c.setRequestProperty("Cookie", cookie);
    c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
    c.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    return readAll(c.getInputStream());
}

// ---------- 分享验证 ----------
String ts = String.valueOf(System.currentTimeMillis());
String verifyUrl = "https://pan.baidu.com/share/verify?surl=" + surl
        + "&t=" + ts + "&channel=chunlei&web=1&app_id=" + APP_ID + "&clienttype=0";
String resp = postForm(verifyUrl,
        "pwd=" + URLEncoder.encode(pwd, "UTF-8") + "&vcode=&vcode_str=",
        UA_NETDISK, cookieHeader(cookies));
// 解析 errno；同时从 c.getHeaderFields().get("Set-Cookie") 里抓 BDCLND

// ---------- 转存 ----------
String transferUrl = "https://pan.baidu.com/share/transfer?shareid=" + shareid
        + "&from=" + uk + "&bdstoken=" + bdstoken
        + "&ondup=newcopy&async=2&channel=chunlei&web=1&clienttype=0&app_id=" + APP_ID;
postForm(transferUrl,
        "fsidlist=" + URLEncoder.encode(fsidListJson, "UTF-8")
                + "&path=" + URLEncoder.encode(destDir, "UTF-8"),
        UA_NETDISK, cookieHeader(cookies));

// ---------- 分块下载 ----------
// 注意：HttpURLConnection 对 Range + 206 支持正常；读完必须 close/disconnect
// 每块校验 Content-Range: bytes start-end/total，别信 md5 字段（它是对象 key，不是内容哈希）
```

**实现注意**
- `HttpURLConnection` 默认会跟随同协议 302，但**会丢掉部分自定义头**；下载 dlink 时建议
  `setInstanceFollowRedirects(false)` 后手动取 `Location` 再请求，和 alist 的做法一致。
- 请求体 `Content-Type` 必须是 `application/x-www-form-urlencoded`；`fsidlist` / `filelist` /
  `target` 这种 JSON 字符串要 `URLEncoder.encode`（空格→`+` 还是 `%20` 一般都能过，尽量 `%20`）。
- Cookie 手动拼，不要依赖 `CookieManager`（它按域区分，`.baidu.com` 与 `pan.baidu.com` 容易混）。
- `Accept-Encoding: identity`：避免 gzip 让 Range 偏移错乱。
- 所有 errno 都要落日志 + 映射成用户能懂的中文。

---

## 10. 参考源码 / 文档索引

**源码（推荐按这个顺序读）**

1. `AlistGo/alist` — `drivers/baidu_share/driver.go`（wxlist 免 verify 的分享列表 + 签名）
   <https://github.com/AlistGo/alist/blob/main/drivers/baidu_share/driver.go>
2. `AlistGo/alist` — `drivers/baidu_netdisk/util.go`（`api/filemetas` crack 参数、`pcs` 上传、dlink 跟随 302）
   <https://github.com/AlistGo/alist/blob/main/drivers/baidu_netdisk/util.go>
3. `qjfoidnh/BaiduPCS-Go` — `baidupcs/transfer.go`（分享页解析 + `share/transfer` + errno 细分）
   <https://github.com/qjfoidnh/BaiduPCS-Go/blob/main/baidupcs/transfer.go>
4. `qjfoidnh/BaiduPCS-Go` — `baidupcs/baidupcs.go`（`NetdiskUA`、base URL、`gettemplatevariable`）
   <https://github.com/qjfoidnh/BaiduPCS-Go/blob/main/baidupcs/baidupcs.go>
5. `qjfoidnh/BaiduPCS-Go` — `baidupcs/prepare.go`（`/api/list`、PCS `file/delete`、`precreate`）
   <https://github.com/qjfoidnh/BaiduPCS-Go/blob/main/baidupcs/prepare.go>
6. `qjfoidnh/BaiduPCS-Go` — `baidupcs/pcserror/panerrorinfo.go`（**完整 errno 中文表**）
   <https://github.com/qjfoidnh/BaiduPCS-Go/blob/main/baidupcs/pcserror/panerrorinfo.go>
7. `skyzhao1223/baidu-pan-skill` — `scripts/bdpan_common.py` / `bdpan_share.py` / `bdpan_download.py`
   （Cookie 白名单、verify + BDCLND、transfer、PCS 分块续传，最贴近你的需求）
   <https://github.com/skyzhao1223/baidu-pan-skill>
8. `zenskcode/baiduyun-pure` — `functions.php`（`share/list`、`sharedownload`、sign/timestamp、错误码注释）
   <https://github.com/zenskcode/baiduyun-pure/blob/main/functions.php>
9. 百度网盘文件转存助手（userscript）— verify + bdstoken 的浏览器端写法
   <https://greasyfork.org/en/scripts/513120/code>

**文档**

- 官方：查询文件信息（`method=meta` / `filemetas`，dlink 8 小时、`UA: pan.baidu.com`）
  <https://pan.baidu.com/union/doc/基础网盘服务/获取文件信息/查询文件信息>
- 官方：分享服务错误码
  <https://pan.baidu.com/union/doc/基础网盘服务/文件分享服务新/错误码/>

**实测文章（2026-09，非会员账号，结论很硬）**

- 《百度网盘 dlink 总报 31362 sign error？逆向实录》
  <https://blog.csdn.net/boyzhaotian/article/details/166349848>
  —— 31362 不可绕、PCS 老端点仍可用、账号级聚合限速、`errno 132` 删除风控、Cookie 白名单。

---

## 11. 不确定 / 需自行验证的点

1. `api/filemetas` 的 `target` 只确认了 **路径**形式；社区有 `target=["fsid"]` 的传闻，但没找到可靠源码。
   `fsid` 批量请用 `xpan/multimedia`（要 access_token）。
2. `share/transfer` 的 `sekey` 参数：老代码带（= `randsk`），新实现（`bdpan_share.py`）不带也能成功。
   建议实现成「带了不出错就带着」，或按失败重试。
3. `share/verify` 的 `randsk` 是否需要 `urldecode` 再放进 `BDCLND`：`bdpan_common.py` 原样放 Cookie，
   `baiduyun-pure` 在 `extra.sekey` 里 `urldecode`。两种写法都见过 —— 建议**Cookie 原样、`extra.sekey` 解码**。
4. 非会员账号用 `PCS method=download` 仍会被限速（~41–53KB/s 聚合），这不是 bug。
5. 大文件（>4GB）PCS 端点是否稳定、`size` 是否需要 64 位、`Content-Range` 溢出等，需要真机验证。
6. 转存目录是否必须预先存在：`baidu-pan-skill` 先 `api/create`；有资料说 `path` 不存在会 `errno 2`。
   实现时先建目录最稳。
