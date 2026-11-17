// 无需登录：验证百度分享页对 Cookie 的差异
// 用法：node baidu-cookie-test.js
const https = require("https");
const SURl = "TwY2Pb5mHZOjpyiiOUTCIg"; // 来自 https://pan.baidu.com/s/1TwY2Pb5mHZOjpyiiOUTCIg?pwd=v357
const PWD = "v357";

function req(path, method, body, cookie) {
  return new Promise((resolve) => {
    const q = https.request({ host: "pan.baidu.com", path, method, headers: {
      "User-Agent": "netdisk", "Accept": "*/*", "Accept-Encoding": "identity",
      "Content-Type": "application/x-www-form-urlencoded",
      "Referer": "https://pan.baidu.com/disk/home", "Cookie": cookie || ""
    }}, (res) => {
      const chunks = [];
      res.on("data", (d) => chunks.push(d));
      res.on("end", () => resolve({
        code: res.statusCode, loc: res.headers.location,
        set: res.headers["set-cookie"], body: Buffer.concat(chunks).toString("utf8")
      }));
    });
    q.on("error", (e) => resolve({ err: e.message }));
    if (body) q.write(body);
    q.end();
  });
}

async function getFollowing(path, cookie) {
  let p = path;
  for (let i = 0; i < 6; i++) {
    const r = await req(p, "GET", null, cookie);
    if ([301, 302, 303, 307, 308].includes(r.code) && r.loc) { p = r.loc; continue; }
    return r;
  }
  return { body: "" };
}

function hasLocals(html) { return html.includes("locals.mset("); }

(async () => {
  const v = await req(`/share/verify?surl=${SURl}&t=${Date.now()}&channel=chunlei&web=1&app_id=250528&clienttype=0`,
    "POST", `pwd=${PWD}&vcode=&vcode_str=`);
  const js = JSON.parse(v.body);
  const randsk = js.randsk;
  let baiduid = "";
  for (const c of (v.set || [])) if (c.startsWith("BAIDUID=")) baiduid = c.split(";")[0];
  console.log("verify errno:", js.errno, "randsk:", randsk ? "有" : "无", "BAIDUID:", baiduid ? "有" : "无");

  for (const [name, ck] of [
    ["BDCLND only", `BDCLND=${randsk}`],
    ["BDCLND + BAIDUID", `BDCLND=${randsk}; ${baiduid}`],
  ]) {
    const r = await getFollowing(`/s/1${SURl}?pwd=${PWD}`, ck);
    const title = (r.body.match(/<title>([^<]*)<\/title>/) || [])[1] || "";
    console.log(`${name}: ${r.body.length} 字节, locals=${hasLocals(r.body)}, title=${title}`);
  }

  // 用 BDCLND 正确解析出的 shareid/uk 列目录
  const page = await getFollowing(`/s/1${SURl}?pwd=${PWD}`, `BDCLND=${randsk}`);
  const m = page.body.match(/locals\.mset\((\{[\s\S]*?\})\)/);
  if (!m) { console.log("locals 解析失败"); return; }
  const locals = JSON.parse(m[1]);
  console.log("shareid:", locals.shareid, "uk:", locals.share_uk, "顶层:", locals.file_list.map(x => x.server_filename));
  const dir = locals.file_list[0].path;
  const list = await req(`/share/list?shareid=${locals.shareid}&uk=${locals.share_uk}&dir=${encodeURIComponent(dir)}&page=1&num=100&order=time&desc=1&showempty=0&web=1&channel=chunlei&app_id=250528&clienttype=0`, "GET", null, `BDCLND=${randsk}`);
  const lj = JSON.parse(list.body);
  console.log("share/list errno:", lj.errno, "条目:", (lj.list || []).map(x => [x.server_filename, x.isdir]));
})();
