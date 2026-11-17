// 测试百度网盘完整流程
const https = require('https');
const http = require('http');

const BDUSS = 'GhUd25jLXRZOG1ybC05UUliRXJYZE0wbHpzbWprcFVaUzlqd3BifjUyZ3pXeU5yRVFBQUFBJCQAAAAAAQAAAAEAAADdDN59AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADPO-2ozzvtqM';
const STOKEN = '6f80e939e2b12725a981fd737eed6daac509c4d7d8ced7d5a482a9bf88886a12';
const BAIDUID = '5A2B31EC8D43E1CE9096E220AA56A7D3:FG=1';
const PANPSC = '3194090403990377515%3A03RYNSbDvu%2F3Q%2B2ap9xD%2F1cS2d9ns3O5C61tf8CKQkhPw6ErnZa6tvEZy5xVYxZqz81ttRoL0tCck0T9AttkQeJEKVZg82vZq3RTUzukNzQFPyC14H3Ni9nzWX47O8AW9o%2BE1b88CJDsGg%2BXSX44rXOL0XXkgcvM%2BwocAWIasjJmIVNcupn0qbtbT0VT6%2BsulR186M70GuGbhqFzqucHmrMjQeCxJD3oCSMXysUUtNyfDP5cou8%2BvVfvaZ5Ymck2RaIdKJ5e59OSsVZaqyA8TA%3D%3D';

const testUrl = 'https://pan.baidu.com/s/1TwY2Pb5mHZOjpyiiOUTCIg?pwd=v357';
const surl = 'TwY2Pb5mHZOjpyiiOUTCIg';
const pwd = 'v357';

function request(url, options = {}) {
  return new Promise((resolve, reject) => {
    const lib = url.startsWith('https:') ? https : http;
    const req = lib.request(url, options, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => resolve({ statusCode: res.statusCode, headers: res.headers, body: data }));
    });
    req.on('error', reject);
    if (options.body) req.write(options.body);
    req.end();
  });
}

async function step1_verify() {
  console.log('\n=== 步骤1: verify 获取 randsk ===');
  const ts = Date.now();
  const url = `https://pan.baidu.com/share/verify?surl=${encodeURIComponent(surl)}&t=${ts}&channel=chunlei&web=1&app_id=250528&clienttype=0`;

  const res = await request(url, {
    method: 'POST',
    headers: {
      'User-Agent': 'pan.baidu.com',
      'Cookie': `BDUSS=${BDUSS}; STOKEN=${STOKEN}; BAIDUID=${BAIDUID}; PANPSC=${PANPSC}`,
      'Content-Type': 'application/x-www-form-urlencoded',
      'Referer': 'https://pan.baidu.com/disk/home',
    },
    body: `pwd=${encodeURIComponent(pwd)}&vcode=&vcode_str=`
  });

  console.log('Status:', res.statusCode);
  const json = JSON.parse(res.body);
  console.log('Response:', json);

  // 提取 Set-Cookie 中的 BDCLND
  let bdclnd = '';
  if (res.headers['set-cookie']) {
    const setCookies = Array.isArray(res.headers['set-cookie']) ? res.headers['set-cookie'] : [res.headers['set-cookie']];
    for (const ck of setCookies) {
      const m = ck.match(/BDCLND=([^;]+)/);
      if (m) bdclnd = m[1];
    }
  }

  console.log('BDCLND from Set-Cookie:', bdclnd);
  console.log('randsk from JSON:', json.randsk);

  return json.randsk || bdclnd;
}

async function step2_getToken(bdclnd) {
  console.log('\n=== 步骤2: 获取 bdstoken ===');
  const url = `https://pan.baidu.com/api/gettemplatevariable?clienttype=0&app_id=250528&fields=${encodeURIComponent('["bdstoken","username","loginstate"]')}`;

  const res = await request(url, {
    method: 'GET',
    headers: {
      'User-Agent': 'pan.baidu.com',
      'Cookie': `BDUSS=${BDUSS}; STOKEN=${STOKEN}; BAIDUID=${BAIDUID}; PANPSC=${PANPSC}; BDCLND=${bdclnd}`,
      'Referer': 'https://pan.baidu.com/disk/home',
    }
  });

  console.log('Status:', res.statusCode);
  const json = JSON.parse(res.body);
  console.log('Response:', JSON.stringify(json, null, 2));

  return json.result?.bdstoken;
}

async function step3_getSharePage(bdclnd) {
  console.log('\n=== 步骤3: 获取分享页(只带BDCLND) ===');
  const url = `https://pan.baidu.com/s/1${surl}?pwd=${pwd}`;

  // 只带 BDCLND
  const res = await request(url, {
    method: 'GET',
    headers: {
      'User-Agent': 'pan.baidu.com',
      'Cookie': `BDCLND=${bdclnd}`,
      'Referer': 'https://pan.baidu.com/disk/home',
    },
    redirect: 'manual'
  });

  console.log('Status:', res.statusCode);
  console.log('页面长度:', res.body.length);

  // 提取 title
  const titleMatch = res.body.match(/<title>([^<]*)<\/title>/);
  if (titleMatch) console.log('标题:', titleMatch[1]);

  // 检查是否有 locals.mset
  const hasLocals = res.body.includes('locals.mset(');
  console.log('是否有 locals.mset:', hasLocals);

  if (hasLocals) {
    const localsMatch = res.body.match(/locals\.mset\((\{[\s\S]*?\})\)/);
    if (localsMatch) {
      try {
        const locals = JSON.parse(localsMatch[1]);
        console.log('locals 解析成功:', {
          shareid: locals.shareid || locals.share_id,
          uk: locals.share_uk || locals.uk,
          file_count: locals.file_list?.length
        });
        return locals;
      } catch (e) {
        console.log('locals 解析失败:', e.message);
      }
    }
  }

  return null;
}

async function main() {
  try {
    console.log('测试链接:', testUrl);

    const bdclnd = await step1_verify();
    if (!bdclnd) {
      console.log('❌ verify 失败,无法获取 randsk/BDCLND');
      return;
    }

    console.log('\n✅ BDCLND 长度:', bdclnd.length);

    const token = await step2_getToken(bdclnd);
    if (!token) {
      console.log('❌ 获取 bdstoken 失败');
      return;
    }

    console.log('✅ bdstoken:', token);

    const locals = await step3_getSharePage(bdclnd);
    if (!locals) {
      console.log('❌ 分享页没有 locals.mset');
      return;
    }

    console.log('\n✅ 流程完整,成功获取 locals');

  } catch (e) {
    console.error('错误:', e.message);
    console.error(e.stack);
  }
}

main();
