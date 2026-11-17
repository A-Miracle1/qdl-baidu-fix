# 迅雷云盘（pan.xunlei.com）HTTP API 逆向笔记

> 目标：Android（Java + HttpURLConnection）实现「浏览器登录 → 读分享 → 转存 → 取直链」。
> 依据：公开源码 / userscript / 开源驱动（非官方文档），并标注互相矛盾处。
> 未做任何登录或携带真实凭据的请求；以下 JSON 除特别说明外均为**按源码字段名构造的示例**。

## 0. 结论速览

| 项 | 值 |
|---|---|
| 云盘 API 主机 | `https://api-pan.xunlei.com/drive/v1`（另有 App 专用 `https://x-api-pan.xunlei.com/drive/v1`） |
| 账号/验证码主机 | `https://xluser-ssl.xunlei.com/v1` |
| 认证 | `Authorization: <token_type> <access_token>`（通常 `Bearer`）+ `X-Captcha-Token` + `X-Device-Id` |
| 分享列表 | `GET /drive/v1/share`（根）、`GET /drive/v1/share/detail`（子目录） |
| 转存 | `POST /drive/v1/share/restore` → `restore_task_id` → `GET /drive/v1/tasks/{id}?type=share` |
| 直链 | `GET /drive/v1/files/{id}?space=&usage=CONSUME` → `web_content_link` |
| 下载 | CDN 链接**必须带合适的 User-Agent**，否则 403/无法下载；一般不需要 Referer/Cookie |
| Token 寿命 | `expires_in` 常见 7200s；JWT 有 `exp`；可 `POST /v1/auth/token` 用 refresh_token 续 |
| Captcha token | 300s（网页侧），过期报 `error_code: 9` / `error: captcha_invalid` |

---

## 1. 认证

### 1.1 浏览器登录后凭据在哪（localStorage）

网页版把登录态写进 localStorage，键名带 client_id：

```js
// localStorage（pan.xunlei.com）
'credentials_<clientId>'            // 主账号
'credentials_<clientId>@<sub>'      // 子账号，配合 localStorage 'current_sub'
'captcha_<clientId>'                // {"token":"...","expires_at":"2026-..T..Z"}
'deviceid'                          // "div101.<32hex deviceId><32hex 后缀>"（可能逗号分隔多条）
// 兼容旧键：'xunlei_guests' / 'xunlei_authorization'
```

`credentials_*` 的值（源码只用到了三个字段）：

```json
{
  "token_type": "Bearer",
  "access_token": "eyJhbGciOi...（JWT，payload 含 sub/exp）",
  "refresh_token": "....",
  "sub": "123456789",
  "expires_in": 7200
}
```

实测代码（这是最直接的证据，逐字符可对照）：

```js
// oyksoft/xunleiyunpan_batch_download  xunlei_batch_download.user.js
if (key.startsWith('credentials_')) { creds = JSON.parse(val); clientId = key.split('_')[1]; }
const authToken = `${creds.token_type || 'Bearer'} ${creds.access_token}`;
const deviceidRaw = localStorage.getItem('deviceid') || '';
const m = /([a-fA-F0-9]{32})/.exec(deviceidRaw.split(',')[0]);   // 取第一个 32 位 hex
headers['X-Captcha-Token'] = cap.token;
headers['X-Device-Id']    = deviceId;
```

另一个脚本用「`.` 之后 32 字符」取 device id（说明 `deviceid` 实际是 `div101.` 前缀的设备签名串）：

```js
// Bleu404/PotplayerPanVideoSV
if (key === 'deviceid')
  Option.header['x-device-id'] = temp.substring(temp.indexOf('.') + 1, 32 + temp.indexOf('.') + 1)
```

YunX（Kotlin，Android 同类实现）的读取脚本更完整：优先 `credentials_<clientId>@<sub>`，再从 cookie `deviceid` 兜底：

```js
var sub = localStorage.getItem('current_sub');
var raw = sub ? localStorage.getItem('credentials_$CLIENT_ID@'+sub)
              : localStorage.getItem('credentials_$CLIENT_ID');
var c = JSON.parse(localStorage.getItem('captcha_$CLIENT_ID')||'{}');
if (c && c.token && Date.parse(c.expires_at) > Date.now()) data.captcha_token = c.token;
// cookie deviceid: "div101.xxxx" -> 取 '.' 后 32 位
```

### 1.2 网页通道 client_id / 版本 / 设备号

| 字段 | 值 | 说明 |
|---|---|---|
| web client_id | `Xqp0kJBXWhwaTpB6` | 2025-2026 年网页版；**不同项目抓到过不同值**（`Xp6vsxz_7IYVw2bAXkh1wA`、`Xp6pAdwyJv9sQuoN`），见 §1.6 |
| web client_version | `1.92.89`（2026-10 时） / `1.93.10` | 与 captcha 签名绑定，会随网页发版变化 |
| package_name（签名用） | `pan.xunlei.com` | 网页签名 raw 里放的是 host |
| device_id | 32 位小写 hex | 由网页生成；`div101.<device_id><md5>` 是 devicesign |

> 实践建议：device_id 直接从 `localStorage.deviceid` 剥出前 32 hex 并**原样复用**；不要自己随机生成后配网页 token（captcha token 与设备号绑定，容易 `client_id not match`）。

### 1.3 云盘接口必需 / 建议的请求头（网页凭据通道）

```
GET /drive/v1/files/{id}?space=&usage=CONSUME HTTP/1.1
Host: api-pan.xunlei.com
Authorization: Bearer <access_token>
X-Captcha-Token: <captcha token>              ← 必需（分享类接口必需；文件类也建议带）
X-Device-Id: <32hex>
X-Client-Id: Xqp0kJBXWhwaTpB6                 ← gopeed 扩展发送；部分脚本不发也能通
X-Client-Version: 1.92.89                     ← 与签名时用的版本一致
Referer: https://pan.xunlei.com/              ← gopeed 发送
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 ... Chrome/131.0.0.0 Safari/537.36
Accept: application/json, text/plain, */*
Cookie: <可选，网页 Cookie>
```

完整头集合的权威样例（gopeed 扩展）：

```js
var headers = {
  Accept: 'application/json, text/plain, */*',
  'User-Agent': userAgent,
  'X-Client-Id': CLIENT_ID,
  'X-Client-Version': CLIENT_VERSION,
  'X-Device-Id': deviceId,
  'X-Captcha-Token': token,
  Referer: 'https://pan.xunlei.com/'
};
if (auth.authorization) headers.Authorization = auth.authorization;
if (auth.cookie)        headers.Cookie = auth.cookie;
```

**关于 `x-device-sign` / `X-Space-Authorization`**：
- 云盘文件/分享接口**不需要** `x-device-sign`。所有网页端实现（LinkSwift、oyksoft、Potplayer、gopeed）都只发 Authorization + captcha + device-id。
- `devicesign` 只出现在**登录**流程：`POST /xluser.core.login/v3/login` 的 body 字段（`div101.<deviceId><sha1→md5>`），见 Alist `generateDeviceSign()`。
- `X-Space-Authorization` 只用于「超级保险箱」space（Alist `thunder_browser`），普通云盘不用。

### 1.4 captcha token 怎么来（网页通道）

```
POST https://xluser-ssl.xunlei.com/v1/shield/captcha/init
Content-Type: application/json
X-Client-Id: Xqp0kJBXWhwaTpB6
X-Device-Id: <32hex>
X-Client-Version: 1.92.89
```

```json
{
  "client_id": "Xqp0kJBXWhwaTpB6",
  "action": "get:/drive/v1/share",
  "device_id": "0123456789abcdef0123456789abcdef",
  "meta": {
    "username": "", "phone_number": "", "email": "",
    "package_name": "pan.xunlei.com",
    "client_version": "1.92.89",
    "captcha_sign": "1.xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
    "timestamp": "1760000000000",
    "user_id": "0"
  }
}
```

响应（顶层字段，无 data 包裹）：

```json
{ "captcha_token": "xxxxxxxx", "expires_in": 300, "url": "" }
```

签名算法（网页通道，2026-10 的 gopeed 扩展，14 条盐）：

```js
raw = client_id + client_version + "pan.xunlei.com" + device_id + timestamp_ms
for (salt of salts) raw = md5(raw + salt)
captcha_sign = "1." + raw
```

**盐和版本约 20 天轮换一次**（YunX 注释），2025 年还是 9 条盐（见 §1.6 分歧）。对 Android App 的启示：
- 若用户「浏览器登录」，最省事是**直接复用页面已经算好的** `captcha_<clientId>.token`；
- 过期（300s）后要么用 WebView 重新打开 `https://pan.xunlei.com/` 让页面自己刷新，要么维护一份可热更新的盐表；
- 不要每次请求都 captcha/init（易触发风控，且旧 token 会被作废）。

### 1.5 Token 过期与刷新

- access_token 是 JWT，payload 含 `sub`(user id)、`exp`；OAuth 响应 `expires_in` 常见 7200 秒。
- 刷新（网页 token 专用，**不带 client_secret**）：

```
POST https://xluser-ssl.xunlei.com/v1/auth/token
Content-Type: application/json
X-Device-Id: <32hex>
X-Client-Id: Xqp0kJBXWhwaTpB6
Origin: https://pan.xunlei.com
Referer: https://pan.xunlei.com/
User-Agent: <桌面 Chrome UA>
Sec-Ch-Ua / Sec-Ch-Ua-Mobile / Sec-Ch-Ua-Platform  ← 必须与 UA 的 Chrome 大版本一致

{"grant_type":"refresh_token","client_id":"Xqp0kJBXWhwaTpB6","refresh_token":"..."}
```

响应：`{ "token_type":"Bearer", "access_token":"...", "refresh_token":"...", "expires_in":7200, "sub":"..." }`
（`refresh_token` 可能不轮换，返回空就沿用旧值 —— Alist 的判据是 access_token 非空即成功）

- 刷新失败/被风控的典型信号：`401`，或 body `{"error_code":16,"error":"unauthenticated"}` / `4121` / `4122` / `10`。
- App 通道刷新是另一套：`client_id=Xp6vsxz_7IYVw2BB` + `client_secret=Xp6vsy4tN9toTVdMSpomVdXpRmES`，`application/x-www-form-urlencoded`。**网页 token 不能拿 App 凭据刷新**，反之亦然（YunX 专门写了这个坑）。

### 1.6 各来源分歧（重要）

| 项 | 来源 A | 来源 B | 判断 |
|---|---|---|---|
| web client_id | `Xqp0kJBXWhwaTpB6`（wgx0307、xinyue-search、YunX、gopeed 2026） | `Xp6vsxz_7IYVw2bAXkh1wA`（LinkSwift 早期注释）、`Xp6pAdwyJv9sQuoN`（YunX 常量「文档推荐」） | 以实际 localStorage 键 `credentials_<id>` 里的 id 为准，运行时读取，别硬编码 |
| captcha 盐数 | 14 条（gopeed 2026-10，适配网页 1.92.89） | 9 条（YunX `WEB_CAPTCHA_SALTS`，网页 1.93.10） | 会轮换；硬编码必然过期，方案见 §1.4 |
| App 通道盐 | 10 条（Alist、pan-relay、YunX 一致） | — | App 通道多年未变，最稳 |
| `usage=CONSUME` | gopeed 明确要求 | Alist 不传也能拿到 web_content_link | 建议固定带 `usage=CONSUME`，空则回退不带 |

App 通道（不依赖浏览器，适合「账号密码/短信登录」路线）：

```
client_id      Xp6vsxz_7IYVw2BB
client_secret  Xp6vsy4tN9toTVdMSpomVdXpRmES
client_version 8.31.0.9726   package com.xunlei.downloadprovider
UA: ANDROID-com.xunlei.downloadprovider/8.31.0.9726 netWorkType/5G appid/40 ... sdkVersion/512000 Oauth2Client/0.9 ...
签名 raw = client_id + client_version + package_name + device_id + ts（10 条盐）
登录: POST /xluser.core.login/v3/login → sessionID
      再 POST /v1/auth/signin/token {provider:"access_end_point_token", signin_token: sessionID}
```

---

## 2. 分享列表

分享链接：`https://pan.xunlei.com/s/{share_id}?pwd={pass_code}`（兼容 `&path=/目录`）。

### 2.1 根目录

```
GET https://api-pan.xunlei.com/drive/v1/share
    ?share_id={share_id}
    &pass_code={pwd}
    &pass_code_token=
    &page_token=
    &limit=100
    &thumbnail_size=SIZE_SMALL          // 也见 SIZE_MEDIUM
    &space=
    &scene=NORMAL
```

`share_id` 是 `/s/` 后那一段（形如 `VMUfpkyNepj25NtsaLSRMFS4A1`）。无密码时 `pass_code` 传空串即可（传 `""`，不要省略键在某些实现里更稳）。

### 2.2 子目录

```
GET https://api-pan.xunlei.com/drive/v1/share/detail
    ?share_id={share_id}
    &parent_id={folder_file_id}
    &pass_code_token={上一次响应里的 pass_code_token}
    &page_token=
    &limit=100
    &thumbnail_size=SIZE_SMALL
    &space=
    &scene=NORMAL
```

注意：进子目录**不再传 pass_code**，改传 `pass_code_token`（首层响应里给的一次性令牌）。

### 2.3 响应结构（示例按字段名构造）

```json
{
  "share_id": "VMUfpkyNepj25NtsaLSRMFS4A1",
  "share_status": "OK",                       // 非 OK：SENSITIVE_RESOURCE / 失效
  "share_status_text": "",
  "title": "某个资源",
  "pass_code_token": "eyJ...",                // 转存/子目录必需
  "next_page_token": "",
  "file_count": 2,
  "files": [
    {
      "kind": "drive#folder",
      "id": "VNYGksGk6ZYRbYP_pGVKJk0BA1",
      "parent_id": "",
      "name": "第一季",
      "size": "0",
      "phase": "PHASE_TYPE_COMPLETE",
      "trashed": false,
      "created_time": "2025-01-01T00:00:00Z",
      "modified_time": "2025-01-01T00:00:00Z"
    },
    {
      "kind": "drive#file",                   // drive#folder = 目录，drive#file = 文件
      "id": "VNTdM9nYLuKmgv7UvJLXZOtIA1",
      "parent_id": "",
      "name": "01.mp4",
      "size": "1073741824",                   // 注意是字符串
      "hash": "GCID...",
      "web_content_link": "",                 // 分享对象通常为空，见 §4.1
      "medias": [ { "media_name": "原始画质", "link": { "url": "https://..." } } ],
      "trashed": false
    }
  ]
}
```

关键判断：
- `kind` 判目录/文件；
- `size` 为字符串，解析成 long；
- 翻页看 `next_page_token`，空串结束；
- `share_status != "OK"` 直接报错（`SENSITIVE_RESOURCE` = 侵权/敏感被封；其余 = 失效）。

---

## 3. 转存到自己网盘

```
POST https://api-pan.xunlei.com/drive/v1/share/restore
Authorization: Bearer <token>
X-Captcha-Token: <token>
X-Device-Id: <32hex>
Content-Type: application/json
```

```json
{
  "parent_id": "",
  "share_id": "VMUfpkyNepj25NtsaLSRMFS4A1",
  "pass_code_token": "eyJ...",
  "ancestor_ids": [],
  "specify_parent_id": true,
  "file_ids": ["VNTdM9nYLuKmgv7UvJLXZOtIA1", "VNYGksGk6ZYRbYP_pGVKJk0BA1"]
}
```

要点：
- **`parent_id` 根目录用空串 `""`，不要用 `"0"`** —— gopeed 明确注释：`"0"` 会被当成真实 id 返回 `file_not_found`（网页 UI 把根显示成 0，容易踩）。
- `file_ids` 来自分享列表的 `id`；文件夹也可转存（整棵子树一起）。
- 不需要 `pass_code`，只要 `pass_code_token`。
- `ancestor_ids` 一般为 `[]`；`specify_parent_id` 为 `true`。

响应（同步只返回任务 id）：

```json
{ "restore_task_id": "b2c3..." }
```

然后轮询任务：

```
GET https://api-pan.xunlei.com/drive/v1/tasks/{restore_task_id}?type=share
```

```json
{
  "id": "b2c3...",
  "progress": 100,
  "status": "PHASE_TYPE_COMPLETE",
  "phase": "PHASE_TYPE_COMPLETE",
  "message": "",
  "params": {
    "trace_file_ids": "{\"VNTdM9nYLuKmgv7UvJLXZOtIA1\":\"VNewFileIdAAA1\"}"
  }
}
```

- `progress == 100` 即完成；
- **`params.trace_file_ids` 是「JSON 字符串」**，值是 `{原文件id: 新文件id}`，新 id 就是你网盘里的 file id（取 `Object.values`）。
- 建议 1~2s 轮询，最多 20~120 次；失败看 `message`。
- 转存有配额/次数限制（超限报错文案不固定），未测到准确错误码。

---

## 4. 取真实下载直链

### 4.1 分享对象自带的直链（优先，能省一次转存）

有些分享的文件对象本身就带 `web_content_link`（gopeed README：*"Prefers the direct file URLs returned directly by the share API"*）。为空时还可以查 share 的文件详情：

```
GET https://api-pan.xunlei.com/drive/v1/share/file_info
    ?share_id={share_id}
    &file_id={file_id}
    &pass_code={pwd}
    &pass_code_token={pass_code_token}
    &folder_id={父目录id或空}
    &space=
    &scene=NORMAL
```

响应里 `file_info.web_content_link`（或 `info`/顶层）可能给直链。**但不是所有分享都给**，普通文件通常必须转存。

### 4.2 自己网盘里的文件（最可靠）

```
GET https://api-pan.xunlei.com/drive/v1/files/{file_id}?space=&usage=CONSUME
Authorization: Bearer <token>
X-Captcha-Token: <token>
X-Device-Id: <32hex>
```

- `usage=CONSUME` 让服务端返回「可消费的内容链接」；`DISPLAY` 只给元数据，`web_content_link` 会是空（gopeed 注释）。
- `space` 普通云盘传空串；浏览器专家/保险箱是 `SPACE_BROWSER` / `SPACE_SAFE`。

响应（字段来自 Alist `Files` 结构体的 json tag，值构造）：

```json
{
  "kind": "drive#file",
  "id": "VNewFileIdAAA1",
  "parent_id": "",
  "name": "01.mp4",
  "size": "1073741824",
  "hash": "....",
  "web_content_link": "https://vod-xxx.xunlei.com/...?....&Expires=1760000000&Signature=...",
  "links": { "application/octet-stream": { "url": "..." } },
  "medias": [
    { "media_name": "原始画质", "link": { "url": "https://..." } },
    { "media_name": "720P",    "link": { "url": "https://..." } }
  ],
  "thumbnail_link": "https://...",
  "created_time": "2025-01-01T00:00:00Z",
  "modified_time": "2025-01-01T00:00:00Z",
  "trashed": false
}
```

- **`web_content_link`** = 普通下载直链；
- `links["application/octet-stream"]` = VIP/不限速下载地址（52pojie 帖）；
- `medias[].link.url` = 视频转码流地址，**视频播放不参与非会员限速**；`media_name == "原始画质"` 时通常等于 `web_content_link`（greasyfork 431256 脚本）；
- 直链有时效（URL 带签名/过期参数），过期后重新 GET 一次即可换新；gopeed README 也说「直链过期就重建任务拿新链接」。

### 4.3 CDN 链接需要什么头

- **User-Agent 基本是必须的**。OpenList 文档原文：*"迅雷如果需要下载必须指定 UserAgent（同下 DownUserAgent），如果设置错误会无法下载"*；Alist 取到链接后固定注入 `User-Agent: <DownloadUserAgent>`（Android DownloadManager 或官方 App UA）。
- **Referer / Cookie 一般不需要**：所有 userscript 都是把 `web_content_link` 直接丢给 IDM/aria2/curl（不带 Referer）就能下。
- 建议客户端：
  - 用与「生成链接时相同的 UA」下直链最稳（网页 token → 桌面 Chrome UA；App token → 官方 App UA）；
  - 若 403，先换 UA 再试，别急着加 Referer；
  - 支持 `Range` 断点续传（CDN 支持）。

### 4.4 是否必须转存到自己网盘？

- `/drive/v1/files/{id}` 只接受**你有权限的 file_id**：自己网盘、或「已通过分享获得权限」的对象。
- 实践结论（多个实现一致）：**普通文件必须先转存**；只有部分分享 API 直接返回了 `web_content_link`/`file_info` 时可跳过。
- LinkSwift 对迅雷分享页只给「保存网盘」按钮、不直接给下载，也是这个原因。

---

## 5. 限流 / 防滥用 / 坑

1. **captcha token 300s 过期**：过期后接口返回
   `{"error":"captcha_invalid","error_code":9,"error_description":"..."}`
   → 重新 captcha/init（Alist 的 `case 9` 分支就是干这个）。
2. **token 类错误**：HTTP 401，或 body `error_code` 属于 `{10, 16, 4121, 4122}` → 用 refresh_token 换新 token 后重试一次（Alist 白名单）。
3. **`client_id not match` / `signature invalid`**：captcha 的 client_id、版本、device_id 三者必须与请求头一致；网页 token 不能用 App client 刷新。
4. **频控/封号**：OpenList 文档明确警告「官方对频繁调用接口会封号」；脚本里通用做法是并发 ≤3、请求间隔 200~500ms、失败指数退避（500ms × 2^n，最多 3 次）。
5. **`parent_id: "0"` 陷阱**：转存根目录必须 `""`。
6. **`size` 是字符串**，直接 `Long.parseLong`，别强转。
7. **分享状态**：`SENSITIVE_RESOURCE` 是内容被封，不是你的 bug；重试无用。
8. **`space` 参数**：普通云盘 `""`；浏览器/保险箱空间不同，混用会 404/空列表。
9. **网页版会改版**：localStorage 键名、盐、client_version 都可能变（本项目里已见 client_id 三种写法）。做降级：解析失败 → 提示用户重新在 WebView 登录。
10. **隐私/合规**：只读自己账号、不绕过会员与敏感资源限制；转存后自行删除临时文件。

---

## 6. 端到端时序（浏览器登录后）

```
[一次性/首次]
1. WebView 打开 https://pan.xunlei.com/ ，用户自己登录（扫码/短信）
2. 注入 JS 读 localStorage：
   - credentials_<clientId> → access_token / refresh_token / token_type / sub
   - captcha_<clientId>     → token（300s，可复用）
   - deviceid              → 前 32 位 hex 作为 X-Device-Id
   - 用当前 clientId 作为 X-Client-Id，记录页面版本作为 X-Client-Version
3. 校验 token：GET /drive/v1/about（200 且 kind=drive#about 即有效）

[每次解析分享]
4. 解析 URL：share_id = path 里 /s/ 后一段；pass_code = ?pwd=
5. GET /drive/v1/share?share_id&pass_code&limit=100&thumbnail_size=SIZE_SMALL
   → 校验 share_status=="OK"，拿 pass_code_token、files[]、next_page_token
6. 递归：GET /drive/v1/share/detail?share_id&parent_id&pass_code_token&page_token
7. 若某文件对象已有 web_content_link → 直接用，结束
8. POST /drive/v1/share/restore {parent_id:"", share_id, pass_code_token,
                                ancestor_ids:[], specify_parent_id:true, file_ids:[...]}
   → restore_task_id
9. GET /drive/v1/tasks/{restore_task_id}?type=share ，1~2s 轮询到 progress==100
   → params.trace_file_ids（JSON 字符串）→ 新 file_id
10. GET /drive/v1/files/{new_file_id}?space=&usage=CONSUME → web_content_link
11. 下载：HttpURLConnection GET 直链，带 UA（同通道 UA），Range 续传
12. 可选：POST /drive/v1/files:batchDelete {ids:[...], space:""} 清理临时转存
```

Java 侧要点（HttpURLConnection）：
- 手写 URLEncoder 拼 query；`filters`/`trace_file_ids` 是 JSON 字符串，注意二次编码；
- 默认自动跟随 302，但 CDN 下载建议 `setInstanceFollowRedirects(false)` 自己处理，避免 POST 变 GET；
- 服务端返回 gzip 时 HttpURLConnection 不会自动解压（未设 `Accept-Encoding` 时一般给明文，别主动加）；
- 所有时间/签名用**毫秒**时间戳。

---

## 7. 最小错误处理表

| HTTP | body 关键字段 | 含义 | 动作 |
|---|---|---|---|
| 200 | `error_code: 0` / 无 error | OK | — |
| 200 | `error_code: 9`, `error: "captcha_invalid"` | captcha 过期 | captcha/init 后重试 1 次 |
| 200 | `error_code: 10/16/4121/4122` | access_token 失效 | refresh_token 续期后重试 |
| 401 | `error: "unauthenticated"` | 未登录/过期 | 同上 |
| 200 | `error_code` 其他 | 业务错 | 展示 `error_description` |
| 403 | HTML/空 | CDN 拒绝 | 换 UA，重取直链 |
| 200 | `share_status: "SENSITIVE_RESOURCE"` | 分享被封 | 直接提示，别重试 |
| 200 | `share_status != OK` | 失效/删除 | 提示 |

---

## 8. 来源（均为公开源码/文档）

1. Alist `drivers/thunder`（请求头、`/files`、`/files/{id}`、`link`、错误码 9/10/16/4121/4122、10 盐签名、devicesign）
   - https://github.com/AlistGo/alist/blob/main/drivers/thunder/driver.go
   - https://github.com/AlistGo/alist/blob/main/drivers/thunder/util.go
   - https://github.com/AlistGo/alist/blob/main/drivers/thunder/types.go
2. Alist `drivers/thunder_browser`（浏览器专家通道、`x-api-pan.xunlei.com`、UA/space、`X-Space-Authorization`）
   - https://github.com/AlistGo/alist/blob/main/drivers/thunder_browser/driver.go
3. OpenList 文档（「下载必须指定 UserAgent」、签名公式、封号警告、视频 URL 不限速）
   - https://doc.oplist.org/guide/drivers/thunder
4. gopeed 迅雷扩展 `index.js`（网页 token 通道最完整：头集合、captcha 14 盐、`/share`、`/share/detail`、`/share/file_info`、`/files/{id}?usage=CONSUME`、`"0"` 陷阱）
   - https://github.com/cybdarlnig-png/gopeed-extension-xunlei
   - https://gopeed.com/store/cybdarlnig-png%40xunlei-pan
5. YunX（Android/Kotlin 同类实现：localStorage 结构、`credentials_<id>@sub`、`current_sub`、cookie deviceid、网页 refresh 必须 web client_id、9 盐、captcha 300s）
   - https://github.com/CYQawa/YunX/blob/master/app/src/main/kotlin/com/yunx/app/data/network/XunleiWebCredential.kt
   - https://github.com/CYQawa/YunX/blob/master/app/src/main/kotlin/com/yunx/app/data/network/XunleiConstants.kt
   - https://github.com/CYQawa/YunX/blob/master/app/src/main/kotlin/com/yunx/app/data/network/XunleiApi.kt
6. oyksoft 油猴脚本（localStorage → Authorization/X-Captcha-Token/X-Device-Id，`files/{id}`）
   - https://github.com/oyksoft/xunleiyunpan_batch_download
   - https://greasyfork.org/zh-CN/scripts/590040
7. Bleu404 油猴脚本（deviceid = `.` 后 32 位；`web_content_link`、`medias`、文件详情）
   - https://github.com/Bleu404/PotplayerPanVideoSV/blob/main/src/PotplayerPanVideoSV.js
   - https://greasyfork.org/zh-CN/scripts/431256/code
8. pan-relay Python（share → restore → tasks → 新 file_id 全链路、App 通道 10 盐）
   - https://github.com/ucmao/pan-relay/blob/main/src/clients/xunlei_client.py
9. wgx0307/netdisk（share/restore/tasks/创建分享 的 body 字段、web client_id/device_id 写法）
   - https://github.com/wgx0307/netdisk/blob/main/xunlei.go
10. xinyue-search PHP（同上的 PHP 版、`share_status` 处理）
    - https://github.com/675061370/xinyue-search/blob/main/extend/netdisk/pan/XunleiPan.php
11. 52pojie「迅雷云盘 API 助手」（`web_content_link` = 限速直链，`links.application/octet-stream` = VIP 直链）
    - https://www.52pojie.cn/thread-1736985-1-1.html

---

## 9. 明确未确定项

- 直链 URL 的精确 TTL（签名参数里能看到过期时间，但未见公开测量值；实践是「几分钟到几小时」，过期重取即可）。
- 转存/分享的每日次数上限与对应错误码（未见公开文档）。
- 网页通道 9 盐 vs 14 盐的切换时间点，以及 `X-Client-Version` 是否必须严格等于签名时的版本（部分实现只发 Sign 不发 Version 也能过）。
- 用「网页 access_token + App 通道 captcha token/头」是否被服务端接受（可能因 audience 校验失败；建议同一通道内自洽，不要混搭）。
- `web_content_link` 是否对同一 file_id 每次返回同一 URL（观察到重新 GET 会刷新签名，但未做稳定性测量）。
