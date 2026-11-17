package com.dsh.qdl;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    static final String UA_QUARK = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "quark-cloud-drive/3.20.0 Chrome/112.0.5615.165 Electron/24.1.3.8 Safari/537.36 Channel/pckk_other_ch";
    static final String UA_UC = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "uc-cloud-drive/2.5.20 Chrome/100.0.4896.160 Electron/18.3.5.4-b478491100 Safari/537.36 Channel/pckk_other_ch";
    static final String UA_BAIDU = "pan.baidu.com";
    static final String OUT_ROOT = "DSH-Go";
    static final String PREF_JOB = "job";

    WebView web;
    WebView rw;
    boolean loginMode = false;
    String loginPlatform = "quark";
    String cookieQ = "";
    String cookieU = "";
    String cookie123 = "";
    String cookieBaidu = "";
    String bdClnd = "";
    boolean loginWatch = false;
    String token123 = "";
    String base123 = "https://www.123pan.com";
    String apiHost123 = "https://api.123278.com";
    String xlToken = "";
    String xlType = "Bearer";
    String xlCaptcha = "";
    String xlDevice = "";
    String xlClient = "";
    long xlCaptchaAt = 0;
    boolean xlRefreshPending = false;
    boolean xlForceLogout = false;
    int xlPollTries = 0;
    int xlRefreshRun = 0;
    boolean xlRefreshCancelled = false;
    volatile boolean xlRefreshOk = false;
    java.util.concurrent.CountDownLatch xlLatch = null;
    static final StringBuilder LOG_BUF = new StringBuilder();
    static Uri logUri = null;
    String lastLink = "";
    String pendingStartLink = null;
    String startAfterIndex = null;
    SharedPreferences sp;
    volatile boolean busy = false;
    volatile boolean cancel = false;
    volatile boolean discard = false;
    volatile boolean debugMode = false;
    final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    final Runnable loginWatchTick = new Runnable() {
        public void run() { checkLoginAuto(); }
    };
    int forcedParts = 0;
    double emaSpeed = 0;
    String statPhase = "待机", statName = "", statHost = "";
    long statTotal = 0, statDone = 0, statStart = 0, statDlStart = 0;
    volatile double statPeak = 0;
    int statParts = 0, statActive = 0, statFails = 0;
    String sharedText = null;
    String plat = "quark";
    String defaultUA = "";
    static final String UA_DESKTOP = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    String pwdId = "";
    String stoken = "";

    String ck() { return "uc".equals(plat) ? cookieU : cookieQ; }
    String ua() { return ("123".equals(plat) || "xunlei".equals(plat)) ? UA_DESKTOP
            : ("baidu".equals(plat) ? UA_BAIDU : ("uc".equals(plat) ? UA_UC : UA_QUARK)); }
    String base() { return "uc".equals(plat) ? "https://pc-api.uc.cn" : "https://drive-pc.quark.cn"; }
    String pr() { return "uc".equals(plat) ? "UCBrowser" : "ucpro"; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = getSharedPreferences("qdl", Context.MODE_PRIVATE);
        cookieQ = sp.getString("cookieQ", "");
        cookieU = sp.getString("cookieU", "");
        cookie123 = sp.getString("cookie123", "");
        cookieBaidu = sp.getString("cookieBaidu", "");
        token123 = sp.getString("token123", "");
        xlToken = sp.getString("xlToken", "");
        xlType = sp.getString("xlType", "Bearer");
        xlCaptcha = sp.getString("xlCaptcha", "");
        xlDevice = sp.getString("xlDevice", "");
        xlClient = sp.getString("xlClient", "");
        xlCaptchaAt = sp.getLong("xlCaptchaAt", 0);
        lastLink = sp.getString("lastLink", "");

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        try {
            s.setSupportZoom(true);
            s.setBuiltInZoomControls(true);
            s.setDisplayZoomControls(false);
            s.setUseWideViewPort(true);
            s.setLoadWithOverviewMode(true);
        } catch (Throwable ignored) { }
        try {
            s.setAllowUniversalAccessFromFileURLs(true);
            s.setAllowFileAccessFromFileURLs(true);
        } catch (Throwable ignored) {
        }
        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= 21) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        }
        defaultUA = web.getSettings().getUserAgentString();
        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                if (loginMode && "xunlei".equals(loginPlatform) && xlForceLogout
                        && url != null && url.contains("pan.xunlei.com")) {
                    xlForceLogout = false;
                    js(XL_CLEAR_JS);
                    clearXunleiCookies();
                    try {
                        CookieManager.getInstance().removeAllCookies(new android.webkit.ValueCallback<Boolean>() {
                            public void onReceiveValue(Boolean value) {
                                handler.postDelayed(new Runnable() {
                                    public void run() {
                                        web.loadUrl("https://pan.xunlei.com/");
                                        toast("旧登录已退出，请登录迅雷账号");
                                    }
                                }, 600);
                            }
                        });
                    } catch (Throwable e) {
                        handler.postDelayed(new Runnable() {
                            public void run() {
                                web.loadUrl("https://pan.xunlei.com/");
                                toast("旧登录已退出，请登录迅雷账号");
                            }
                        }, 900);
                    }
                    return;
                }
                if (url != null && url.startsWith("file:///android_asset/index.html") && startAfterIndex != null) {
                    final String l = startAfterIndex;
                    startAfterIndex = null;
                    js("(function(){var i=document.getElementById('link');if(i)i.value=" + q(l) + ";})()");
                    js("window.ui && window.ui.busyStart()");
                    beginJob(l);
                    return;
                }
                if (sharedText != null) {
                    js("(function(){var i=document.getElementById('link');if(i){i.value=" + q(sharedText) + ";}})()");
                    sharedText = null;
                }
            }
        });
        rw = new WebView(this);
        WebSettings rs = rw.getSettings();
        rs.setJavaScriptEnabled(true);
        rs.setDomStorageEnabled(true);
        rs.setAllowFileAccess(true);
        rs.setAllowContentAccess(true);
        try {
            rs.setAllowUniversalAccessFromFileURLs(true);
            rs.setAllowFileAccessFromFileURLs(true);
        } catch (Throwable ignored) { }
        if (Build.VERSION.SDK_INT >= 21) CookieManager.getInstance().setAcceptThirdPartyCookies(rw, true);
        rw.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                if (xlRefreshPending && url != null && url.contains("pan.xunlei.com")) {
                    xlRefreshPending = false;
                    xlPollTries = 0;
                    pollXunleiToken();
                }
            }
        });

        FrameLayout root = new FrameLayout(this);
        root.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(rw, new FrameLayout.LayoutParams(1, 1));
        setContentView(root);

        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, 7);
        }

        Intent it = getIntent();
        if (it != null && Intent.ACTION_SEND.equals(it.getAction())) {
            String t = it.getStringExtra(Intent.EXTRA_TEXT);
            if (t != null) {
                Matcher m = Pattern.compile("https?://(?:pan\\.quark\\.cn|drive\\.uc\\.cn|fast\\.uc\\.cn|pan\\.xunlei\\.com)/s/[0-9A-Za-z?=&._-]*").matcher(t);
                sharedText = m.find() ? m.group() : t;
            }
        }
        web.loadUrl("file:///android_asset/index.html");
        fileLog("===== app start（0.25）");
    }

    @Override
    public void onBackPressed() {
        if (loginMode) {
            if ("xunlei".equals(loginPlatform)) {
                loginMode = false;
                extractXunleiToken();
                return;
            }
            if ("123".equals(loginPlatform)) {
                loginMode = false;
                extract123Token();
                return;
            }
            if ("baidu".equals(loginPlatform)) {
                String c = CookieManager.getInstance().getCookie("https://pan.baidu.com");
                if (c == null || !(c.contains("BDUSS=") || c.contains("BDUSS_BFESS="))) {
                    String c2 = CookieManager.getInstance().getCookie("https://baidu.com");
                    if (c2 != null && (c2.contains("BDUSS=") || c2.contains("BDUSS_BFESS="))) c = c2;
                }
                boolean ok = c != null && (c.contains("BDUSS=") || c.contains("BDUSS_BFESS="));
                if (ok) {
                    cookieBaidu = c;
                    sp.edit().putString("cookieBaidu", c).apply();
                }
                loginMode = false;
                loginWatch = false;
                web.getSettings().setUserAgentString(defaultUA);
                web.loadUrl("file:///android_asset/index.html");
                toast(ok ? "已保存百度网盘登录" : "还没检测到百度登录，再试一次");
                return;
            }
            String host = "123".equals(loginPlatform) ? "https://www.123pan.com"
                    : ("uc".equals(loginPlatform) ? "https://drive.uc.cn" : "https://pan.quark.cn");
            String c = CookieManager.getInstance().getCookie(host);
            if (c != null && (c.contains("__puus") || c.contains("__pus"))) {
                if ("123".equals(loginPlatform)) {
                    cookie123 = c;
                    sp.edit().putString("cookie123", c).apply();
                } else if ("uc".equals(loginPlatform)) {
                    cookieU = c;
                    sp.edit().putString("cookieU", c).apply();
                } else {
                    cookieQ = c;
                    sp.edit().putString("cookieQ", c).apply();
                }
                toast("已保存 " + ("uc".equals(loginPlatform) ? "UC" : "夸克") + " 登录");
            } else {
                toast("还没检测到登录，再试一次");
            }
            loginMode = false;
            web.getSettings().setUserAgentString(defaultUA);
            web.loadUrl("file:///android_asset/index.html");
            return;
        }
        if (xlRefreshPending || pendingStartLink != null) {
            cancelXunleiRefresh();
            toast("已取消刷新");
            return;
        }
        if (busy) {
            cancel = true;
            fileLog("cancel 由返回键触发");
            toast("正在停止（下次可续传）");
            return;
        }
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    boolean xlOk() {
        return xlToken != null && !xlToken.isEmpty() && xlDevice != null && !xlDevice.isEmpty();
    }

    static String deviceOf(String raw) {
        if (raw == null) return "";
        Matcher m = Pattern.compile("([0-9a-fA-F]{32})").matcher(raw);
        if (m.find()) return m.group(1).toLowerCase();
        return "";
    }

    /** 网页登录/刷新后，从 pan.xunlei.com 的 localStorage 里把令牌捞回来。 */
    static final String XL_EXTRACT_JS = "(function(){var cred='',cap='',dev='',cid='',ce=0;try{"
            + "var sub=localStorage.getItem('current_sub')||'';"
            + "for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);"
            + "if(/^credentials_/.test(k)){cid=k.substring(12).split('@')[0];}}"
            + "if(cid){cred=localStorage.getItem('credentials_'+cid+(sub?'@'+sub:''))||localStorage.getItem('credentials_'+cid)||'';"
            + "cap=localStorage.getItem('captcha_'+cid)||'';}"
            + "dev=localStorage.getItem('deviceid')||'';"
            + "try{var co=JSON.parse(cap||'{}');ce=Date.parse(co.expires_at||0)||0;}catch(e){}"
            + "}catch(e){}";

    void extractXunleiToken() {
        js(XL_EXTRACT_JS + "AndroidBridge.saveXunleiToken(cred,cap,dev,cid);})()");
    }

    /** 清掉迅雷网页的登录态（localStorage + 域名 Cookie），保证能进登录页。 */
    static final String XL_CLEAR_JS = "(function(){try{var del=[];"
            + "for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);"
            + "if(/^credentials_/.test(k)||/^captcha_/.test(k)||k==='current_sub'||k==='xlpan_pass_token'||k==='xlpan_pass_code')del.push(k);}"
            + "for(var j=0;j<del.length;j++)localStorage.removeItem(del[j]);return 'ok';}catch(e){return 'err';}})()";

    void clearXunleiCookies() {
        try {
            CookieManager cm = CookieManager.getInstance();
            String[] hosts = { "https://pan.xunlei.com", "https://i.xunlei.com",
                    "https://xluser-ssl.xunlei.com", "https://user.xunlei.com" };
            for (String host : hosts) {
                String ck = cm.getCookie(host);
                if (ck == null || ck.isEmpty()) continue;
                for (String part : ck.split(";")) {
                    int eq = part.indexOf('=');
                    if (eq <= 0) continue;
                    String name = part.substring(0, eq).trim();
                    if (name.isEmpty()) continue;
                    cm.setCookie(host, name + "=; Max-Age=0; path=/");
                    cm.setCookie(host, name + "=; Max-Age=0; path=/; domain=.xunlei.com");
                    cm.setCookie(host, name + "=; Max-Age=0; path=/; domain=.pan.xunlei.com");
                }
            }
            cm.flush();
        } catch (Throwable ignored) { }
    }

    /** 在隐藏 WebView 里轮询 localStorage，直到拿到可用的 captcha。 */
    void pollXunleiToken() {
        final int run = xlRefreshRun;
        if (xlRefreshCancelled || run != xlRefreshRun) return;
        try {
            rw.evaluateJavascript(XL_EXTRACT_JS + "JSON.stringify({cred:cred,cap:cap,dev:dev,cid:cid,ce:ce});",
                    new android.webkit.ValueCallback<String>() {
                        public void onReceiveValue(String v) {
                            if (run != xlRefreshRun || xlRefreshCancelled) return;
                            try {
                                if (v == null || "null".equals(v)) { scheduleXunleiPoll(); return; }
                                String json = new JSONObject("{\"v\":" + v + "}").getString("v");
                                JSONObject o = new JSONObject(json);
                                handleXunleiPoll(o.optString("cred"), o.optString("cap"),
                                        o.optString("dev"), o.optString("cid"), o.optLong("ce"));
                            } catch (Throwable e) {
                                scheduleXunleiPoll();
                            }
                        }
                    });
        } catch (Throwable e) {
            scheduleXunleiPoll();
        }
    }

    void scheduleXunleiPoll() {
        if (xlRefreshCancelled) return;
        xlPollTries++;
        if (xlPollTries >= 18) { finishXunleiRefresh(false); return; }
        handler.postDelayed(new Runnable() {
            public void run() { pollXunleiToken(); }
        }, 1200);
    }

    void handleXunleiPoll(String cred, String cap, String dev, String cid, long exp) {
        if (xlRefreshCancelled) return;
        String token = "", type = "Bearer", captcha = "", device = "";
        try {
            if (cred != null && !cred.isEmpty()) {
                JSONObject o = new JSONObject(cred);
                token = o.optString("access_token", "");
                String t2 = o.optString("token_type", "");
                if (!t2.isEmpty()) type = t2;
            }
        } catch (Throwable e) { }
        try {
            if (cap != null && !cap.isEmpty()) {
                JSONObject o = new JSONObject(cap);
                captcha = o.optString("token", "");
            }
        } catch (Throwable e) { }
        device = deviceOf(dev);
        if (cid != null && !cid.isEmpty()) xlClient = cid;
        final boolean valid = !token.isEmpty() && !device.isEmpty();
        final boolean changed = !captcha.isEmpty() && !captcha.equals(xlCaptcha);
        // 只要还能用 20 秒以上就收下，不再要求 90 秒（避免明明有效还判失败）
        final boolean fresh = valid && !captcha.isEmpty()
                && (exp > System.currentTimeMillis() + 20000L || (exp <= 0 && changed));
        if (valid) {
            xlToken = token; xlType = type; xlDevice = device;
            if (!captcha.isEmpty()) { xlCaptcha = captcha; xlCaptchaAt = System.currentTimeMillis(); }
            sp.edit().putString("xlToken", xlToken).putString("xlType", xlType)
                    .putString("xlCaptcha", xlCaptcha).putString("xlDevice", xlDevice)
                    .putString("xlClient", xlClient).putLong("xlCaptchaAt", xlCaptchaAt).apply();
        }
        fileLog("迅雷令牌轮询 #" + xlPollTries + " fresh=" + fresh + " captcha=" + !captcha.isEmpty()
                + (exp > 0 ? " 剩余" + ((exp - System.currentTimeMillis()) / 1000) + "s" : ""));
        if (fresh) { finishXunleiRefresh(true); return; }
        scheduleXunleiPoll();
    }

    /** 123 云盘登录后从 yun.123pan.cn 的 localStorage 读 authorToken。 */
    void extract123Token() {
        js("(function(){var t='',c='';try{"
                + "t=localStorage.getItem('authorToken')||'';"
                + "if(!t){try{var s=JSON.parse(localStorage.getItem('tokenSet')||'{}');t=s&&s.token?s.token:'';}catch(e){}}"
                + "c=document.cookie||'';"
                + "}catch(e){}AndroidBridge.save123Token(t,c);})()");
    }

    /** 用隐藏 WebView 打开分享页，并逼前端发一次带 captcha 的请求来生成新令牌。 */
    void refreshXunleiInternal(final String url) {
        xlRefreshCancelled = false;
        xlRefreshPending = true;
        xlPollTries = 0;
        final int run = ++xlRefreshRun;
        handler.postDelayed(new Runnable() {
            public void run() {
                if (run == xlRefreshRun && !xlRefreshCancelled) {
                    xlRefreshPending = false;
                    fileLog("迅雷刷新超时");
                    finishXunleiRefresh(false);
                }
            }
        }, 24000);
        runOnUiThread(new Runnable() {
            public void run() {
                rw.getSettings().setUserAgentString(UA_DESKTOP);
                String u = (url == null || url.isEmpty()) ? "https://pan.xunlei.com/" : url;
                rw.loadUrl(u);
                handler.postDelayed(new Runnable() {
                    public void run() { triggerXunleiCaptcha(url); }
                }, 1600);
            }
        });
        status("刷新迅雷令牌…");
    }

    /** 借用分享页的 Vuex action，带 withCaptcha 请求一次；前端会顺便刷新 captcha_。 */
    void triggerXunleiCaptcha(String url) {
        if (xlRefreshCancelled) return;
        String shareId = "", pwd = "";
        Matcher m = Pattern.compile("pan\\.xunlei\\.com/s/([0-9A-Za-z_-]+)").matcher(url == null ? "" : url);
        if (m.find()) shareId = m.group(1);
        Matcher p = Pattern.compile("[?&#](?:pwd|password)=([0-9A-Za-z]{4})").matcher(url == null ? "" : url);
        if (p.find()) pwd = p.group(1);
        if (shareId.isEmpty()) return;
        try {
            rw.evaluateJavascript("(function(){try{var s=window.$nuxt&&window.$nuxt.$store;if(!s)return 'nostore';"
                    + "s.dispatch('share/getShare',{params:{share_id:" + q(shareId) + ",pass_code:" + q(pwd)
                    + ",limit:1,page_token:'',scene:'NORMAL'},withCaptcha:true,withCredentials:true,show:false});"
                    + "return 'ok';}catch(e){return 'err';}})()", null);
            fileLog("已触发分享页 captcha 请求");
        } catch (Throwable ignored) { }
    }

    /** 任务线程里同步等刷新结果。 */
    boolean refreshXunleiAndWait(String url) {
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        xlLatch = latch;
        xlRefreshOk = false;
        refreshXunleiInternal(url);
        try {
            boolean done = latch.await(26, java.util.concurrent.TimeUnit.SECONDS);
            if (!done) finishXunleiRefresh(false);
        } catch (Throwable ignored) { }
        xlLatch = null;
        return xlRefreshOk;
    }

    void finishXunleiRefresh(final boolean ok) {
        xlRefreshRun++;
        xlRefreshPending = false;
        xlPollTries = 0;
        final boolean wasCancelled = xlRefreshCancelled;
        final java.util.concurrent.CountDownLatch latch = xlLatch;
        xlRefreshOk = ok;
        xlRefreshCancelled = false;
        if (latch != null) latch.countDown();
        if (wasCancelled) {
            js("window.ui && window.ui.finish()");
            return;
        }
        if (latch != null) {
            fileLog("迅雷刷新" + (ok ? "成功" : "失败") + "（任务内）");
            return;
        }
        runOnUiThread(new Runnable() {
            public void run() {
                if (ok) {
                    toast("迅雷令牌已刷新");
                    status("迅雷令牌已刷新");
                } else {
                    status("迅雷令牌刷新失败，需要重新登录");
                    toast("刷新失败：没拿到新令牌，请点「登录」重新登录");
                }
                js("window.ui && window.ui.finish()");
            }
        });
    }

    void cancelXunleiRefresh() {
        if (!xlRefreshPending && xlPollTries == 0) return;
        xlRefreshCancelled = true;
        xlRefreshRun++;
        xlRefreshPending = false;
        xlPollTries = 0;
        final java.util.concurrent.CountDownLatch latch = xlLatch;
        if (latch != null) latch.countDown();
        runOnUiThread(new Runnable() {
            public void run() {
                try { rw.stopLoading(); } catch (Throwable ignored) { }
            }
        });
        status("已取消");
        js("window.ui && window.ui.finish()");
        fileLog("取消迅雷刷新");
    }

    /** 登录页轮询：抓到 Cookie / token 就自动回 App，不用手动按返回。 */
    void startLoginWatch() {
        loginWatch = true;
        handler.removeCallbacks(loginWatchTick);
        handler.postDelayed(loginWatchTick, 2000);
    }

    void checkLoginAuto() {
        if (!loginWatch || !loginMode) return;
        try {
            if ("baidu".equals(loginPlatform)) {
                String c = CookieManager.getInstance().getCookie("https://pan.baidu.com");
                if (c == null || !(c.contains("BDUSS=") || c.contains("BDUSS_BFESS="))) {
                    String c2 = CookieManager.getInstance().getCookie("https://baidu.com");
                    if (c2 != null && (c2.contains("BDUSS=") || c2.contains("BDUSS_BFESS="))) c = c2;
                }
                if (c != null && (c.contains("BDUSS=") || c.contains("BDUSS_BFESS="))) {
                    finishBaiduLogin(c);
                    return;
                }
            } else if ("xunlei".equals(loginPlatform)) {
                if (!xlForceLogout) {
                    web.evaluateJavascript(XL_EXTRACT_JS + "(cred&&cred.indexOf('access_token')>=0)?'1':'0';})()",
                            new android.webkit.ValueCallback<String>() {
                                public void onReceiveValue(String v) {
                                    if (v != null && v.indexOf('1') >= 0 && loginMode && loginWatch) {
                                        loginMode = false; loginWatch = false;
                                        extractXunleiToken();
                                    }
                                }
                            });
                }
            } else if ("123".equals(loginPlatform)) {
                web.evaluateJavascript("(function(){try{return localStorage.getItem('authorToken')||'';}catch(e){return '';}})()",
                        new android.webkit.ValueCallback<String>() {
                            public void onReceiveValue(String v) {
                                if (v != null && v.length() > 4 && !"null".equals(v) && !"\"\"".equals(v)
                                        && loginMode && loginWatch) {
                                    loginMode = false; loginWatch = false;
                                    extract123Token();
                                }
                            }
                        });
            } else {
                String host = "uc".equals(loginPlatform) ? "https://drive.uc.cn" : "https://pan.quark.cn";
                String c = CookieManager.getInstance().getCookie(host);
                if (c != null && (c.contains("__puus") || c.contains("__pus"))) {
                    finishCookieLogin(c);
                    return;
                }
            }
        } catch (Throwable ignored) { }
        handler.postDelayed(loginWatchTick, 1200);
    }

    void finishBaiduLogin(String c) {
        loginWatch = false;
        loginMode = false;
        cookieBaidu = c;
        sp.edit().putString("cookieBaidu", c).apply();
        web.getSettings().setUserAgentString(defaultUA);
        web.loadUrl("file:///android_asset/index.html");
        toast("已保存百度网盘登录");
    }

    void finishCookieLogin(String c) {
        loginWatch = false;
        loginMode = false;
        if ("uc".equals(loginPlatform)) { cookieU = c; sp.edit().putString("cookieU", c).apply(); }
        else { cookieQ = c; sp.edit().putString("cookieQ", c).apply(); }
        web.getSettings().setUserAgentString(defaultUA);
        web.loadUrl("file:///android_asset/index.html");
        toast("已保存 " + ("uc".equals(loginPlatform) ? "UC" : "夸克") + " 登录");
    }

    void pushStats() {
        try {
            JSONObject o = new JSONObject();
            o.put("phase", statPhase);
            o.put("name", statName);
            o.put("host", statHost);
            o.put("total", statTotal);
            o.put("totalText", statTotal > 0 ? mb(statTotal) : "—");
            o.put("doneText", statDone > 0 ? mb(statDone) : "—");
            o.put("pct", statTotal > 0 ? (int) (statDone * 100 / statTotal) : 0);
            o.put("speed", fmtSpeed(emaSpeed));
            o.put("peak", statPeak > 0 ? fmtSpeed(statPeak) : "—");
            o.put("parts", statParts);
            o.put("active", statActive);
            o.put("fails", statFails);
            long el = statStart > 0 ? (System.currentTimeMillis() - statStart) / 1000 : 0;
            o.put("elapsed", el + "s");
            String eta = "—";
            if (emaSpeed > 1024 && statTotal > statDone) eta = Math.max(1, (long) ((statTotal - statDone) / emaSpeed)) + "s";
            o.put("eta", eta);
            js("window.ui && window.ui.stats(" + q(o.toString()) + ")");
        } catch (Throwable ignored) { }
    }

    void toast(final String m) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(MainActivity.this, m, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** 全部干完震动提醒：成功 2 下长的，失败 3 下短的。 */
    void vibrate(final boolean ok) {
        try {
            long[] pat = ok ? new long[] { 0, 320, 140, 320 } : new long[] { 0, 110, 90, 110, 90, 110 };
            android.os.Vibrator v = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(pat, -1);
        } catch (Throwable ignored) { }
    }

    void js(String code) {
        final String c = code;
        runOnUiThread(new Runnable() {
            public void run() {
                try { web.evaluateJavascript(c, null); } catch (Throwable ignored) { }
            }
        });
    }

    static String q(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ") + "\"";
    }

    void log(String m) { js("window.ui && window.ui.log(" + q(m) + ")"); fileLog(m); }

    void status(String m) { js("window.ui && window.ui.status(" + q(m) + ")"); }

    void progress(int pct, String speed, String name) {
        js("window.ui && window.ui.progress(" + pct + "," + q(speed) + "," + q(name) + ")");
    }

    void fileLog(String m) {
        final String line = new java.text.SimpleDateFormat("MM-dd HH:mm:ss").format(new java.util.Date()) + " " + m + "\n";
        synchronized (LOG_BUF) {
            if (LOG_BUF.length() > 200000) LOG_BUF.delete(0, LOG_BUF.length() - 150000);
            LOG_BUF.append(line);
        }
        try {
            File f = new File(Environment.getExternalStorageDirectory(), "Download/" + OUT_ROOT + "/qdl.log");
            if (f.getParentFile() != null && !f.getParentFile().exists()) f.getParentFile().mkdirs();
            if (f.length() > 512 * 1024) f.delete();
            FileOutputStream fo = new FileOutputStream(f, true);
            fo.write(line.getBytes("UTF-8"));
            fo.close();
            return;
        } catch (Throwable ignored) { }
        // Android 11+ 没有写「下载」目录权限时，走 MediaStore；每次整段重写，避免 append 模式不兼容
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                ContentResolver cr = getContentResolver();
                Uri col = Uri.parse("content://media/external/downloads");
                if (logUri == null) {
                    android.database.Cursor cu = cr.query(col, new String[] { "_id" },
                            "_display_name=? AND relative_path LIKE ?",
                            new String[] { "qdl.log", "Download/" + OUT_ROOT + "%" }, null);
                    if (cu != null) {
                        if (cu.moveToFirst()) logUri = Uri.withAppendedPath(col, cu.getString(0));
                        cu.close();
                    }
                }
                if (logUri == null) {
                    ContentValues cv = new ContentValues();
                    cv.put("_display_name", "qdl.log");
                    cv.put("mime_type", "text/plain");
                    cv.put("relative_path", "Download/" + OUT_ROOT);
                    cv.put("is_pending", 1);
                    logUri = cr.insert(col, cv);
                }
                if (logUri != null) {
                    OutputStream os = cr.openOutputStream(logUri, "w");
                    if (os != null) {
                        synchronized (LOG_BUF) { os.write(LOG_BUF.toString().getBytes("UTF-8")); }
                        os.close();
                    }
                    ContentValues done = new ContentValues();
                    done.put("is_pending", 0);
                    cr.update(logUri, done, null, null);
                }
            } catch (Throwable e) {
                logUri = null;
            }
        }
    }

    public class Bridge {
        @JavascriptInterface
        public boolean logged(String p) {
            if ("xunlei".equals(p)) return xlOk();
            if ("baidu".equals(p)) return cookieBaidu != null
                    && (cookieBaidu.contains("BDUSS=") || cookieBaidu.contains("BDUSS_BFESS="));
            if ("123".equals(p)) return (token123 != null && !token123.isEmpty())
                    || (cookie123 != null && !cookie123.isEmpty());
            String c = "123".equals(p) ? cookie123 : ("uc".equals(p) ? cookieU : cookieQ);
            return c != null && (c.contains("__puus") || c.contains("__pus"));
        }

        @JavascriptInterface
        public void openLogin(String p) {
            loginPlatform = "uc".equals(p) ? "uc" : ("123".equals(p) ? "123" : ("xunlei".equals(p) ? "xunlei" : ("baidu".equals(p) ? "baidu" : "quark")));
            runOnUiThread(new Runnable() {
                public void run() {
                    loginMode = true;
                    // 百度登录页在桌面 UA 下验证码弹层会错位/挡住按钮，用手机默认 UA
                    web.getSettings().setUserAgentString(UA_DESKTOP);
                    if ("xunlei".equals(loginPlatform)) {
                        xlForceLogout = true;
                        web.loadUrl("https://pan.xunlei.com/");
                        startLoginWatch();
                        toast("正在退出旧登录，随后请重新登录迅雷");
                        return;
                    }
                    if ("baidu".equals(loginPlatform)) {
                        web.loadUrl("https://pan.baidu.com/");
                        startLoginWatch();
                        toast("登录百度网盘，成功后自动返回");
                        return;
                    }
                    String lu = "123".equals(loginPlatform) ? "https://yun.123pan.cn/"
                            : ("uc".equals(loginPlatform) ? "https://drive.uc.cn/" : "https://pan.quark.cn/");
                    web.loadUrl(lu);
                    startLoginWatch();
                    toast("用网页版登录，登录成功后会自动返回");
                }
            });
        }

        @JavascriptInterface
        public void saveXunleiToken(String cred, String cap, String dev, String cid) {
            String token = "", type = "Bearer", captcha = "", device = "";
            try {
                if (cred != null && !cred.isEmpty()) {
                    JSONObject o = new JSONObject(cred);
                    token = o.optString("access_token", "");
                    String t2 = o.optString("token_type", "");
                    if (!t2.isEmpty()) type = t2;
                }
            } catch (Throwable e) { fileLog("迅雷 credentials 解析失败：" + e.getMessage()); }
            try {
                if (cap != null && !cap.isEmpty()) {
                    JSONObject o = new JSONObject(cap);
                    captcha = o.optString("token", "");
                }
            } catch (Throwable e) { fileLog("迅雷 captcha 解析失败：" + e.getMessage()); }
            device = deviceOf(dev);
            if (cid != null && !cid.isEmpty()) xlClient = cid;
            final boolean valid = !token.isEmpty() && !device.isEmpty();
            if (valid) {
                xlToken = token; xlType = type;
                if (!captcha.isEmpty()) { xlCaptcha = captcha; xlCaptchaAt = System.currentTimeMillis(); }
                xlDevice = device;
                sp.edit().putString("xlToken", xlToken).putString("xlType", xlType)
                        .putString("xlCaptcha", xlCaptcha).putString("xlDevice", xlDevice)
                        .putString("xlClient", xlClient).putLong("xlCaptchaAt", xlCaptchaAt).apply();
            }
            fileLog("迅雷令牌" + (valid ? "已保存（client=" + xlClient + "，captcha=" + !captcha.isEmpty() + "）"
                    : "不完整：token=" + !token.isEmpty() + " device=" + !device.isEmpty()));
            final boolean autoOk = valid && !captcha.isEmpty();
            runOnUiThread(new Runnable() {
                public void run() {
                    loginMode = false;
                    web.getSettings().setUserAgentString(defaultUA);
                    if (autoOk) {
                        if (pendingStartLink != null) {
                            startAfterIndex = pendingStartLink;
                            pendingStartLink = null;
                        }
                    } else {
                        if (pendingStartLink != null)
                            toast("没拿到迅雷验证令牌，请在网盘页刷新后再试");
                        pendingStartLink = null;
                    }
                    web.loadUrl("file:///android_asset/index.html");
                    toast(valid ? "已保存迅雷登录" : "没读到迅雷令牌，请登录后再按返回键");
                }
            });
            if (valid) {
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            apiXunlei("/drive/v1/about", "GET", null);
                            fileLog("迅雷令牌校验通过");
                        } catch (Exception e) {
                            fileLog("迅雷令牌校验失败：" + e.getMessage());
                        }
                    }
                }).start();
            }
        }

        @JavascriptInterface
        public void refreshXunlei() {
            if (!xlOk()) { toast("先登录迅雷"); return; }
            refreshXunleiInternal(lastLink != null && lastLink.contains("pan.xunlei.com")
                    ? lastLink : "https://pan.xunlei.com/");
        }

        @JavascriptInterface
        public void save123Token(String token, String cookie) {
            String t = token == null ? "" : token.trim();
            if (!t.isEmpty()) { token123 = t; sp.edit().putString("token123", t).apply(); }
            if (cookie != null && !cookie.isEmpty()) { cookie123 = cookie; sp.edit().putString("cookie123", cookie).apply(); }
            final boolean ok = !t.isEmpty();
            fileLog("123 登录 token " + (ok ? "已保存" : "没读到"));
            runOnUiThread(new Runnable() {
                public void run() {
                    loginMode = false;
                    web.getSettings().setUserAgentString(defaultUA);
                    web.loadUrl("file:///android_asset/index.html");
                    toast(ok ? "已保存 123 登录" : "没读到 123 登录，匿名也可以试下（看分享方流量包）");
                }
            });
        }

        @JavascriptInterface
        public void saveCookie(String p, String c) {
            if (c == null) return;
            c = c.trim();
            boolean ok = "baidu".equals(p) ? (c.contains("BDUSS=") || c.contains("BDUSS_BFESS="))
                    : (c.contains("__puus") || c.contains("__pus"));
            if (ok) {
                if ("baidu".equals(p)) { cookieBaidu = c; sp.edit().putString("cookieBaidu", c).apply(); }
                else if ("123".equals(p)) { cookie123 = c; sp.edit().putString("cookie123", c).apply(); }
                else if ("uc".equals(p)) { cookieU = c; sp.edit().putString("cookieU", c).apply(); }
                else { cookieQ = c; sp.edit().putString("cookieQ", c).apply(); }
            }
            js("window.ui && window.ui.cookieSaved(" + q(p) + "," + ok + ")");
        }

        @JavascriptInterface
        public String pendingInfo() {
            JSONObject j = loadJob();
            if (j == null) return "";
            long size = j.optLong("size", 0);
            long done = 0;
            JSONArray a = j.optJSONArray("seg");
            if (a != null) for (int i = 0; i < a.length(); i++) done += a.optLong(i, 0);
            int pct = size > 0 ? (int) (done * 100 / size) : 0;
            return j.optString("name", "") + "|" + pct;
        }

        @JavascriptInterface
        public void copy(String text) {
            try {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("qdl", text == null ? "" : text));
                toast("已复制");
            } catch (Throwable ignored) { }
        }

        @JavascriptInterface
        public void setDebug(boolean d) { debugMode = d; fileLog("调试模式 " + (d ? "开" : "关")); }

        @JavascriptInterface
        public void cancel() {
            if (xlRefreshPending || pendingStartLink != null) cancelXunleiRefresh();
            cancel = true;
            fileLog("暂停（保留续传）");
        }

        @JavascriptInterface
        public void discard() {
            if (xlRefreshPending || pendingStartLink != null) cancelXunleiRefresh();
            cancel = true;
            discard = true;
            fileLog("取消（删除半成品）");
        }

        @JavascriptInterface
        public void start(final String link) {
            if (busy) { toast("已有任务在跑"); return; }
            cancel = false;
            discard = false;
            boolean is123 = link != null && (link.contains("123pan.com") || link.contains("123684.com")
                    || link.contains("123865.com") || link.contains("123pan.cn"));
            boolean isUC = link != null && (link.contains("drive.uc.cn") || link.contains("fast.uc.cn"));
            boolean isXL = link != null && link.contains("pan.xunlei.com");
            boolean isBD = link != null && (link.contains("pan.baidu.com") || link.contains("yun.baidu.com"));
            plat = isXL ? "xunlei" : (isBD ? "baidu" : (is123 ? "123" : (isUC ? "uc" : "quark")));
            if ("xunlei".equals(plat)) {
                if (!xlOk()) {
                    js("window.ui && window.ui.needLogin(" + q(plat) + ")");
                    return;
                }
                // 不再开始前强制刷新：先拿现有令牌试，接口报验证码过期时任务内再自动刷新重试
            } else if ("baidu".equals(plat)) {
                if (cookieBaidu == null || !(cookieBaidu.contains("BDUSS=") || cookieBaidu.contains("BDUSS_BFESS="))) {
                    js("window.ui && window.ui.needLogin(" + q(plat) + ")");
                    return;
                }
            } else if (!"123".equals(plat)) {
                String need = "uc".equals(plat) ? cookieU : cookieQ;
                if (need == null || !(need.contains("__puus") || need.contains("__pus"))) {
                    js("window.ui && window.ui.needLogin(" + q(plat) + ")");
                    return;
                }
            }
            beginJob(link);
        }

        @JavascriptInterface
        public void startAfterRefresh() {
            final String link = pendingStartLink;
            if (link == null) return;
            pendingStartLink = null;
            beginJob(link);
        }
    }

    void beginJob(final String link) {
        if (busy) { toast("已有任务在跑"); return; }
        if (cancel) {
            cancel = false;
            discard = false;
            status("已取消");
            js("window.ui && window.ui.finish()");
            fileLog("开始前已被取消，不启动任务");
            return;
        }
        busy = true;
        cancel = false;
        discard = false;
        forcedParts = 0;
        lastLink = link;
        sp.edit().putString("lastLink", link).apply();
        fileLog("===== start [" + plat + "] " + link);
        try { startService(new Intent(MainActivity.this, KeepAliveService.class)); } catch (Throwable ignored) { }
        final boolean[] failed = new boolean[] { false };
        new Thread(new Runnable() {
            public void run() {
                try {
                    runJob(link);
                } catch (Exception e) {
                    failed[0] = true;
                    status("失败：" + e.getMessage());
                    log("✗ " + e.getMessage());
                } finally {
                    busy = false;
                    try { stopService(new Intent(MainActivity.this, KeepAliveService.class)); } catch (Throwable ignored) { }
                    fileLog("===== end (cancel=" + cancel + ", failed=" + failed[0] + ")");
                    if (!cancel) vibrate(!failed[0]);
                    js("window.ui && window.ui.finish()");
                }
            }
        }).start();
    }

    // ---------------- HTTP ----------------

    String http(String url, String method, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setRequestProperty("User-Agent", ua());
        c.setRequestProperty("Accept", "application/json, text/plain, */*");
        c.setRequestProperty("Referer", "uc".equals(plat) ? "https://drive.uc.cn/" : "https://pan.quark.cn/");
        c.setRequestProperty("Origin", "uc".equals(plat) ? "https://drive.uc.cn" : "https://pan.quark.cn");
        String ck = ck();
        if (ck != null && !ck.isEmpty()) c.setRequestProperty("Cookie", ck);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            OutputStream os = c.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.close();
        }
        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        if (is == null) throw new Exception("网络错误 HTTP " + code);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
        is.close();
        return new String(bo.toByteArray(), "UTF-8");
    }

    JSONObject api(String path, String method, JSONObject body) throws Exception {
        JSONObject j = new JSONObject(http(base() + path, method, body == null ? null : body.toString()));
        int code = j.optInt("code", 0);
        if (j.optInt("status", 200) == 401 || code == 31001) throw new Exception("cookie 过期，需要重新登录");
        if (code != 0) throw new Exception(j.optString("message", "接口返回 " + code));
        return j;
    }

    static String safe(String s) {
        return s == null ? "download" : s.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    static String mb(long b) {
        if (b >= 1073741824L) return String.format("%.1f GB", b / 1073741824.0);
        return String.format("%.1f MB", b / 1048576.0);
    }

    static String fmtSpeed(double bps) {
        if (bps > 1048576) return String.format("%.1f MB/s", bps / 1048576);
        if (bps > 1024) return String.format("%.0f KB/s", bps / 1024);
        return String.format("%.0f B/s", bps);
    }

    static class Item {
        String fid;
        String name;
        String rel;
        long size;
        long numId;
        String etag = "";
        String s3 = "";
    }

    // ---------------- 续传状态 ----------------

    JSONObject loadJob() {
        try {
            String s = sp.getString(PREF_JOB, "");
            if (s == null || s.isEmpty()) return null;
            return new JSONObject(s);
        } catch (Exception e) {
            return null;
        }
    }

    void saveJob(JSONObject j) { sp.edit().putString(PREF_JOB, j.toString()).apply(); }

    void clearJob() { sp.edit().remove(PREF_JOB).apply(); }

    JSONArray rootList() throws Exception {
        JSONObject j = api("/1/clouddrive/file/sort?pr=" + pr() + "&fr=pc&pdir_fid=0&_page=1&_size=200&_fetch_total=1&_sort=file_type:asc,updated_at:desc", "GET", null);
        JSONArray a = j.getJSONObject("data").optJSONArray("list");
        return a == null ? new JSONArray() : a;
    }

    JSONArray childrenOfShare(String fid) throws Exception {
        JSONObject j = api("/1/clouddrive/share/sharepage/detail?pr=" + pr() + "&fr=pc&pwd_id=" + pwdId
                + "&stoken=" + URLEncoder.encode(stoken, "UTF-8")
                + "&pdir_fid=" + fid + "&force=0&_page=1&_size=200&_fetch_share=1", "GET", null);
        JSONArray a = j.getJSONObject("data").optJSONArray("list");
        return a == null ? new JSONArray() : a;
    }

    JSONArray childrenOfDrive(String fid) throws Exception {
        JSONObject j = api("/1/clouddrive/file/sort?pr=" + pr() + "&fr=pc&pdir_fid=" + fid
                + "&_page=1&_size=200&_fetch_total=1&_sort=file_type:asc,updated_at:desc", "GET", null);
        JSONArray a = j.getJSONObject("data").optJSONArray("list");
        return a == null ? new JSONArray() : a;
    }

    static String namesOf(JSONArray arr) {
        java.util.TreeSet<String> set = new java.util.TreeSet<String>();
        for (int i = 0; i < arr.length(); i++) {
            try { set.add(arr.getJSONObject(i).optString("file_name")); } catch (Exception ignored) { }
        }
        return set.toString();
    }

    void runJob(String rawLink) throws Exception {
        if ("123".equals(plat)) { runJob123(rawLink); return; }
        if ("xunlei".equals(plat)) { runJobXunlei(rawLink); return; }
        if ("baidu".equals(plat)) { runJobBaidu(rawLink); return; }
        Matcher pm = Pattern.compile("(?:pan\\.quark\\.cn|drive\\.uc\\.cn|fast\\.uc\\.cn)/s/([0-9A-Za-z]+)").matcher(rawLink == null ? "" : rawLink);
        if (!pm.find()) throw new Exception("只认夸克 / UC 的分享链接");
        pwdId = pm.group(1);
        String pass = "";
        Matcher pm2 = Pattern.compile("[?&#](?:pwd|password)=([0-9A-Za-z]{4})").matcher(rawLink);
        if (pm2.find()) pass = pm2.group(1);

        statStart = System.currentTimeMillis();
        statDone = 0; statTotal = 0; statParts = 0; statActive = 0; statFails = 0;
        statPeak = 0; emaSpeed = 0; statHost = ""; statPhase = "解析分享";
        final Thread tick = new Thread(new Runnable() {
            public void run() {
                try {
                    while (busy) { pushStats(); Thread.sleep(900); }
                    pushStats();
                } catch (Throwable ignored) { }
            }
        });
        tick.start();
        status("解析分享…");
        fileLog("解析 " + plat + " " + pwdId);
        JSONObject t = api("/1/clouddrive/share/sharepage/token?pr=" + pr() + "&fr=pc", "POST",
                new JSONObject().put("pwd_id", pwdId).put("passcode", pass));
        stoken = t.getJSONObject("data").getString("stoken");

        JSONObject d = api("/1/clouddrive/share/sharepage/detail?pr=" + pr() + "&fr=pc&pwd_id=" + pwdId
                + "&stoken=" + URLEncoder.encode(stoken, "UTF-8")
                + "&pdir_fid=0&force=0&_page=1&_size=50&_fetch_share=1", "GET", null);
        JSONArray list = d.getJSONObject("data").optJSONArray("list");
        if (list == null || list.length() == 0) throw new Exception("分享为空，或者需要提取码");
        String title = list.getJSONObject(0).optString("file_name", pwdId);
        if (list.length() > 1) title = title + " 等" + list.length() + "项";

        JSONArray top = null;
        JSONArray before = rootList();

        // 目录分享：网盘里已有同名目录且子项一致就复用，避免重复转存
        if (list.length() == 1 && list.getJSONObject(0).optBoolean("dir", false)) {
            String dn = list.getJSONObject(0).optString("file_name", "");
            try {
                String shareNames = namesOf(childrenOfShare(list.getJSONObject(0).getString("fid")));
                for (int i = 0; i < before.length(); i++) {
                    JSONObject o = before.getJSONObject(i);
                    if (o.optBoolean("dir", false) && dn.equals(o.optString("file_name"))) {
                        if (shareNames.equals(namesOf(childrenOfDrive(o.getString("fid"))))) {
                            top = new JSONArray().put(o.getString("fid"));
                            log("网盘里已有同名目录且内容一致，跳过转存");
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                fileLog("目录去重检查失败（不影响下载）：" + e.getMessage());
            }
        }

        if (list.length() == 1 && !list.getJSONObject(0).optBoolean("dir", false)) {
            String nm = list.getJSONObject(0).optString("file_name", "");
            long sz = list.getJSONObject(0).optLong("size", 0);
            for (int i = 0; i < before.length(); i++) {
                JSONObject o = before.getJSONObject(i);
                if (nm.equals(o.optString("file_name")) && o.optLong("size", 0) == sz && !o.optBoolean("dir", false)) {
                    top = new JSONArray().put(o.getString("fid"));
                    log("网盘里已有同名文件，跳过转存");
                    break;
                }
            }
        }

        if (top == null) {
            status("转存到网盘…"); statPhase = "转存";
            fileLog("转存");
            JSONArray fids = new JSONArray();
            JSONArray toks = new JSONArray();
            for (int i = 0; i < list.length(); i++) {
                JSONObject o = list.getJSONObject(i);
                fids.put(o.getString("fid"));
                toks.put(o.optString("share_fid_token", ""));
            }
            JSONObject sv = api("/1/clouddrive/share/sharepage/save?pr=" + pr() + "&fr=pc&uc_param_str=", "POST",
                    new JSONObject().put("fid_list", fids).put("fid_token_list", toks).put("to_pdir_fid", "0")
                            .put("pdir_fid", "0").put("pwd_id", pwdId).put("stoken", stoken).put("scene", "link"));
            String taskId = sv.getJSONObject("data").optString("task_id", "");
            if (taskId.length() > 0) {
                for (int i = 0; i < 60; i++) {
                    Thread.sleep(1000);
                    JSONObject ts = api("/1/clouddrive/task?pr=" + pr() + "&fr=pc&task_id=" + taskId + "&retry_index=" + i, "GET", null);
                    int st = ts.getJSONObject("data").optInt("status", 0);
                    if (st == 2) {
                        JSONObject sa = ts.getJSONObject("data").optJSONObject("save_as");
                        if (sa != null) top = sa.optJSONArray("save_as_top_fids");
                        break;
                    }
                    if (st == 3) throw new Exception("转存失败，文件可能违规或空间不足");
                }
            } else {
                try { top = sv.getJSONObject("data").getJSONObject("save_as").getJSONArray("save_as_top_fids"); } catch (Exception ignored) { }
            }
        }
        if (top == null || top.length() == 0) throw new Exception("转存后没拿到文件列表");

        status("扫描文件…"); statPhase = "扫描";
        JSONArray root = rootList();
        HashMap<String, JSONObject> byFid = new HashMap<String, JSONObject>();
        for (int i = 0; i < root.length(); i++) {
            JSONObject o = root.getJSONObject(i);
            byFid.put(o.optString("fid"), o);
        }
        List<Item> files = new ArrayList<Item>();
        for (int i = 0; i < top.length(); i++) {
            String fid = top.getString(i);
            JSONObject o = byFid.get(fid);
            if (o == null) continue;
            String nm = o.optString("file_name", "file");
            if (o.optBoolean("dir", false)) {
                walk(fid, nm, files);
            } else {
                Item it = new Item();
                it.fid = fid;
                it.name = nm;
                it.rel = nm;
                it.size = o.optLong("size", 0);
                files.add(it);
            }
        }
        if (files.isEmpty()) throw new Exception("没有可下载的文件");

        long total = 0;
        for (Item it : files) total += it.size;
        boolean single = files.size() == 1;
        String relDir = "Download/" + OUT_ROOT + (single ? "" : "/" + safe(title));
        log("共 " + files.size() + " 个文件，" + mb(total) + "，存到「下载/" + OUT_ROOT + (single ? "" : "/" + safe(title)) + "」");

        status("获取直链…"); statPhase = "取直链";
        fileLog("取直链");
        JSONArray dlFids = new JSONArray();
        for (Item it : files) dlFids.put(it.fid);
        JSONObject dls = api("/1/clouddrive/file/download?entry=ft&fr=pc&pr=" + pr(), "POST", new JSONObject().put("fids", dlFids));
        JSONArray arr = dls.getJSONArray("data");
        HashMap<String, String> urls = new HashMap<String, String>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            urls.put(o.optString("fid"), o.optString("download_url"));
        }

        if (debugMode && !files.isEmpty()) {
            Item probe = files.get(0);
            String pu = urls.get(probe.fid);
            if (pu != null && !pu.isEmpty() && probe.size > 6L * 1048576) {
                int best = speedTest(pu, probe.size);
                if (best > 0) { forcedParts = best; log("本文件采用 " + best + " 段"); }
            }
        }

        long base = 0;
        int doneFiles = 0;
        statTotal = total;
        statPhase = "下载";
        statDlStart = System.currentTimeMillis();
        for (Item it : files) {
            if (cancel) { status("已停止"); return; }
            String u = urls.get(it.fid);
            if (u == null || u.isEmpty()) { log("✗ 拿不到直链：" + it.name); continue; }
            String sub = relDir;
            String name = it.rel;
            int cut = it.rel.lastIndexOf('/');
            if (cut > 0) { sub = relDir + "/" + it.rel.substring(0, cut); name = it.rel.substring(cut + 1); }
            status("下载 " + name);
            statName = name; statPhase = "下载";
            fileLog("开始下载 " + name + " " + it.size + " bytes");
            try {
                downloadTo(u, sub, name, total, base, it);
            } catch (Exception e) {
                if (cancel) status("已停止，再点开始可续传");
                else status("失败：" + e.getMessage());
                log("✗ " + name + "：" + e.getMessage());
                return;
            }
            base += it.size;
            doneFiles++;
            progress((int) (base * 100 / Math.max(1, total)), "", name);
            log("✓ " + name);
        }
        if (cancel) { status("已停止（网盘暂存保留，可续传）"); return; }

        try {
            api("/1/clouddrive/file/delete?pr=" + pr() + "&fr=pc", "POST", new JSONObject()
                    .put("action_type", 27).put("current_dir_fid", "0")
                    .put("file_list", top).put("exclude_fids", new JSONArray()));
            log("已清理网盘暂存");
        } catch (Exception e) {
            log("网盘暂存没删掉（不影响文件）");
        }
        double dlAvg = total / Math.max(1.0, (System.currentTimeMillis() - statDlStart) / 1000.0);
        double allAvg = total / Math.max(1.0, (System.currentTimeMillis() - statStart) / 1000.0);
        log("下载均速 " + fmtSpeed(dlAvg) + "（纯下载阶段）· 全程均速 " + fmtSpeed(allAvg) + "（含测速/转存）");
        fileLog("下载均速 " + fmtSpeed(dlAvg) + " / 全程均速 " + fmtSpeed(allAvg));
        status("完成 " + doneFiles + " 个文件");
        log("全部完成，去「下载/" + OUT_ROOT + "」找文件");
    }

    void walk(String fid, String rel, List<Item> out) throws Exception {
        JSONObject j = api("/1/clouddrive/file/sort?pr=" + pr() + "&fr=pc&pdir_fid=" + fid
                + "&_page=1&_size=200&_fetch_total=1&_sort=file_type:asc,updated_at:desc", "GET", null);
        JSONArray list = j.getJSONObject("data").optJSONArray("list");
        if (list == null) return;
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.getJSONObject(i);
            String nm = o.optString("file_name", "file");
            String r = rel + "/" + nm;
            if (o.optBoolean("dir", false)) walk(o.getString("fid"), r, out);
            else {
                Item it = new Item();
                it.fid = o.getString("fid");
                it.name = nm;
                it.rel = r;
                it.size = o.optLong("size", 0);
                out.add(it);
            }
        }
    }

    // ---------------- 存储 ----------------

    static class Sink {
        java.nio.channels.FileChannel ch;
        Uri uri;
        ContentResolver cr;
        ParcelFileDescriptor pfd;
    }

    static HttpURLConnection openConn(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        return c;
    }

    Sink openSink(String relDir, String name, Uri reuse) throws Exception {
        Sink s = new Sink();
        String mime = name.toLowerCase().endsWith(".apk")
                ? "application/vnd.android.package-archive" : "application/octet-stream";
        if (Build.VERSION.SDK_INT >= 29) {
            s.cr = getContentResolver();
            if (reuse != null) {
                s.uri = reuse;
            } else {
                ContentValues cv = new ContentValues();
                cv.put("_display_name", name);
                cv.put("mime_type", mime);
                cv.put("relative_path", relDir);
                cv.put("is_pending", 1);
                s.uri = s.cr.insert(Uri.parse("content://media/external/downloads"), cv);
                if (s.uri == null) throw new Exception("系统没让写「下载」目录");
            }
            s.pfd = s.cr.openFileDescriptor(s.uri, "rw");
            if (s.pfd == null) throw new Exception("打开文件失败（" + name + "）");
            s.ch = new FileOutputStream(s.pfd.getFileDescriptor()).getChannel();
        } else {
            File dir = new File(Environment.getExternalStorageDirectory(), relDir);
            if (!dir.exists()) dir.mkdirs();
            s.ch = new FileOutputStream(new File(dir, name)).getChannel();
        }
        if (s.ch == null) throw new Exception("打开文件失败：" + name);
        return s;
    }

    void closeSink(Sink s) {
        try { s.ch.force(true); } catch (Throwable ignored) { }
        try { s.ch.close(); } catch (Throwable ignored) { }
        try { if (s.pfd != null) s.pfd.close(); } catch (Throwable ignored) { }
        if (s.uri != null && s.cr != null) {
            try {
                ContentValues cv = new ContentValues();
                cv.put("is_pending", 0);
                s.cr.update(s.uri, cv, null, null);
            } catch (Throwable ignored) { }
        }
    }

    void headers(HttpURLConnection c) {
        c.setRequestProperty("User-Agent", ua());
        c.setRequestProperty("Accept-Encoding", "identity");
        String ck;
        if ("baidu".equals(plat)) {
            c.setRequestProperty("Referer", "https://pan.baidu.com/");
            ck = bdCookie();
        } else if ("xunlei".equals(plat)) {
            c.setRequestProperty("Referer", "https://pan.xunlei.com/");
            ck = "";
        } else if ("123".equals(plat)) {
            c.setRequestProperty("Referer", base123 + "/");
            ck = cookie123;
            c.setRequestProperty("platform", "web");
            c.setRequestProperty("app-version", "3");
        } else {
            c.setRequestProperty("Referer", "uc".equals(plat) ? "https://drive.uc.cn/" : "https://pan.quark.cn/");
            ck = ck();
        }
        if (ck != null && !ck.isEmpty()) c.setRequestProperty("Cookie", ck);
    }

    boolean supportsRange(String url) {
        try {
            HttpURLConnection c = openConn(url);
            headers(c);
            c.setRequestProperty("Range", "bytes=0-0");
            int code = c.getResponseCode();
            c.disconnect();
            return code == 206;
        } catch (Exception e) {
            return false;
        }
    }

    /** 续传匹配：老平台按 fid；迅雷每次转存 file id 会变，按 名字+大小 认。 */
    boolean sameJobFile(JSONObject j, Item it, String name, long size) {
        if (j == null) return false;
        if (j.optLong("size", 0) != size) return false;
        if (it.fid != null && it.fid.equals(j.optString("fid"))) return true;
        return name != null && name.equals(j.optString("name"));
    }

    void downloadTo(String url, String relDir, String name, long total, long base, Item it) throws Exception {
        long size = it.size;
        int parts = clampParts(size, forcedParts > 0 ? forcedParts
                : (size >= 100L * 1048576 ? 64 : (size > 32L * 1048576 ? 32 : 16)));
        if ("baidu".equals(plat)) parts = 1;   // 百度非会员多线程容易被限/403，先单连接
        JSONObject savedJob = loadJob();
        if (sameJobFile(savedJob, it, name, size)) {
            int sp = savedJob.optInt("parts", 0);
            if (sp > MAX_PARTS) {
                fileLog("旧任务分段 " + sp + " 超过上限，作废旧进度重新开始");
                clearJob();
            } else if (sp > 0) {
                parts = sp;   // 续传沿用原分段，保证偏移能对上
            }
        }
        fileLog("分段数 " + parts + "（" + mb(size) + "）");
        statParts = parts;
        try { statHost = new URL(url).getHost(); } catch (Throwable ignored) { }
        long[] segDone = new long[parts];
        Uri reuse = null;
        JSONObject job = loadJob();
        if (sameJobFile(job, it, name, size) && job.optInt("parts", 0) == parts) {
            String u = job.optString("uri", "");
            if (!u.isEmpty()) {
                Uri cand = Uri.parse(u);
                try {
                    ParcelFileDescriptor p = getContentResolver().openFileDescriptor(cand, "rw");
                    if (p != null) { p.close(); reuse = cand; }
                } catch (Exception ignored) { }
            }
            JSONArray a = job.optJSONArray("seg");
            for (int i = 0; i < parts && a != null && i < a.length(); i++) segDone[i] = a.optLong(i, 0);
            long done = 0;
            for (long x : segDone) done += x;
            if (reuse != null && done > 0) log("续传：已有 " + mb(done) + "（" + (done * 100 / Math.max(1, size)) + "%）");
        }

        Sink sink = openSink(relDir, name, reuse);
        JSONObject st = new JSONObject();
        st.put("fid", it.fid);
        st.put("name", name);
        st.put("size", size);
        st.put("parts", parts);
        st.put("uri", sink.uri == null ? "" : sink.uri.toString());
        JSONArray segArr = new JSONArray();
        for (long x : segDone) segArr.put(x);
        st.put("seg", segArr);
        saveJob(st);

        boolean ok = false;
        try {
            if (size > 4L * 1048576 && supportsRange(url)) {
                long got = downloadParallel(url, sink, size, parts, segDone, total, base, name, st);
                if (got * 100 / Math.max(1, size) < 99) throw new Exception("只下到 " + (got * 100 / Math.max(1, size)) + "%");
            } else {
                HttpURLConnection c = openConn(url);
                headers(c);
                InputStream is = c.getInputStream();
                byte[] buf = new byte[262144];
                int n;
                long got = 0, last = System.currentTimeMillis(), lastBytes = 0;
                while ((n = is.read(buf)) > 0) {
                    if (cancel) throw new Exception("已停止");
                    sink.ch.write(java.nio.ByteBuffer.wrap(buf, 0, n), got);
                    got += n;
                    long now = System.currentTimeMillis();
                    if (now - last > 700) {
                        emaSpeed = emaSpeed == 0 ? (got - lastBytes) * 1000.0 / Math.max(1, now - last)
                                : emaSpeed * 0.72 + (got - lastBytes) * 1000.0 / Math.max(1, now - last) * 0.28;
                        if (emaSpeed > statPeak) statPeak = emaSpeed;
                        statDone = base + got; statActive = 1;
                        progress((int) (((base + got) * 100) / Math.max(1, total)), fmtSpeed(emaSpeed), name);
                        last = now;
                        lastBytes = got;
                    }
                }
                is.close();
                if (got * 100 / Math.max(1, size) < 99) throw new Exception("下载不完整");
            }
            ok = true;
        } finally {
            closeSink(sink);
            if (ok) {
                clearJob();
            } else if (discard) {
                if (sink.uri != null && sink.cr != null) {
                    try { sink.cr.delete(sink.uri, null, null); } catch (Throwable ignored) { }
                }
                clearJob();
            }
        }
    }

    static final int MAX_PARTS = 128;

    static int clampParts(long size, int want) {
        int bySize = (int) Math.max(1, size / (64 * 1024));
        return Math.min(Math.min(want, MAX_PARTS), bySize);
    }

    double measure(final String url, final long size, final int wantParts, final long window) throws Exception {
        final int parts = clampParts(size, wantParts);
        final java.util.concurrent.atomic.AtomicLong bytes = new java.util.concurrent.atomic.AtomicLong(0);
        final java.util.concurrent.atomic.AtomicBoolean stop = new java.util.concurrent.atomic.AtomicBoolean(false);
        final long t0 = System.currentTimeMillis();
        List<Thread> ts = new ArrayList<Thread>();
        for (int i = 0; i < parts; i++) {
            final long start = i * size / parts;
            final long end = Math.min(size - 1, start + Math.max(262144L, size / parts) - 1);
            Thread th = new Thread(new Runnable() {
                public void run() {
                    try {
                        HttpURLConnection c = openConn(url);
                        headers(c);
                        c.setReadTimeout(2500);
                        c.setRequestProperty("Range", "bytes=" + start + "-" + end);
                        if (c.getResponseCode() != 206) return;
                        InputStream is = c.getInputStream();
                        byte[] buf = new byte[32768];
                        int r;
                        while (!stop.get() && (r = is.read(buf)) > 0) bytes.addAndGet(r);
                        is.close();
                        c.disconnect();
                    } catch (Throwable ignored) { }
                }
            });
            ts.add(th);
            th.start();
            try { Thread.sleep(15); } catch (Throwable ignored) { }
        }
        List<long[]> samples = new ArrayList<long[]>();
        while (System.currentTimeMillis() - t0 < window) {
            samples.add(new long[] { System.currentTimeMillis() - t0, bytes.get() });
            Thread.sleep(250);
        }
        stop.set(true);
        for (Thread t : ts) t.join(1500);
        samples.add(new long[] { System.currentTimeMillis() - t0, bytes.get() });
        long trim = Math.min(3000, window / 3);
        long b0 = 0, tA = trim;
        for (long[] sm : samples) {
            if (sm[0] >= trim) { b0 = sm[1]; tA = sm[0]; break; }
        }
        long[] last = samples.get(samples.size() - 1);
        double sec = Math.max(0.5, (last[0] - tA) / 1000.0);
        return (last[1] - b0) / sec;
    }

    int speedTest(final String url, final long size) throws Exception {
        int[] candidates = { 32, 64, 96, 128 };
        int best = 0;
        double bestSpeed = 0;
        int zeros = 0;
        for (int n : candidates) {
            if (cancel) break;
            double bps = measure(url, size, n, 15000);
            log("测速 " + n + " 段：" + fmtSpeed(bps) + "（15s 稳态均值）");
            fileLog("测速 " + n + " 段 -> " + fmtSpeed(bps));
            if (bps < 1024) zeros++; else zeros = 0;
            if (bps > bestSpeed) { bestSpeed = bps; best = n; }
            if (zeros >= 2) { log("连续 0 速度，停止加段：CDN 已开始限流"); break; }
        }
        if (best > 0 && !cancel) {
            double again = measure(url, size, best, 15000);
            log("复测 " + best + " 段：" + fmtSpeed(again) + "（均值 " + fmtSpeed((bestSpeed + again) / 2) + "）");
            fileLog("复测 " + best + " 段 -> " + fmtSpeed(again));
        }
        return best;
    }

    long downloadParallel(final String url, final Sink sink, final long size, final int parts,
                          final long[] segDone, final long total, final long base,
                          final String name, final JSONObject jobState) throws Exception {
        final long seg = size / parts;
        final java.util.concurrent.atomic.AtomicLong got = new java.util.concurrent.atomic.AtomicLong(0);
        for (long x : segDone) got.addAndGet(x);
        final java.util.concurrent.atomic.AtomicReference<String> err = new java.util.concurrent.atomic.AtomicReference<String>(null);
        List<Thread> threads = new ArrayList<Thread>();
        for (int i = 0; i < parts; i++) {
            final int idx = i;
            final long start = i * seg + segDone[i];
            final long end = (i == parts - 1) ? size - 1 : ((i + 1) * seg - 1);
            final long s0 = start;
            Thread th = new Thread(new Runnable() {
                public void run() {
                    try {
                        if (s0 > end) return;
                        long pos0 = s0;
                        int tries = 0;
                        while (pos0 <= end && !cancel) {
                            tries++;
                            HttpURLConnection c = null;
                            InputStream is2 = null;
                            long before = pos0;
                            try {
                                c = openConn(url);
                                headers(c);
                                c.setRequestProperty("Range", "bytes=" + pos0 + "-" + end);
                                if (c.getResponseCode() != 206) throw new Exception("分段请求被拒 HTTP");
                                is2 = c.getInputStream();
                                byte[] b2 = new byte[parts > 64 ? 32768 : 65536];
                                int r2;
                                while (!cancel && (r2 = is2.read(b2)) > 0) {
                                    java.nio.ByteBuffer bb2 = java.nio.ByteBuffer.wrap(b2, 0, r2);
                                    while (bb2.hasRemaining()) pos0 += sink.ch.write(bb2, pos0);
                                    segDone[idx] += r2;
                                    got.addAndGet(r2);
                                    if (pos0 > end) break;
                                }
                                if (pos0 > before) tries = 0;
                            } catch (Exception e2) {
                                statFails++;
                                if (tries >= 4) throw e2;
                                try { Thread.sleep(600L * tries); } catch (Throwable ignored) { }
                            } finally {
                                try { if (is2 != null) is2.close(); } catch (Throwable ignored) { }
                                try { if (c != null) c.disconnect(); } catch (Throwable ignored) { }
                            }
                        }
                    } catch (Exception e) {
                        err.compareAndSet(null, e.getMessage() == null ? "分段下载失败" : e.getMessage());
                    }
                }
            });
            try {
                th.setPriority(Thread.MIN_PRIORITY);
                th.start();
                threads.add(th);
                try { Thread.sleep(12); } catch (Throwable ignored) { }
            } catch (Throwable e) {
                statFails++;
                fileLog("线程创建失败：" + e.getMessage());
            }
        }
        long lastBytesInit = got.get();
        long last = System.currentTimeMillis(), lastBytes = lastBytesInit, lastSave = 0;
        long lastSec0 = last, lastBytes0 = lastBytesInit;
        while (true) {
            boolean alive = false;
            for (Thread t : threads) if (t.isAlive()) alive = true;
            long now = System.currentTimeMillis();
            long g = got.get();
            double inst = Math.max(0, (g - lastBytes) * 1000.0 / Math.max(1, now - last));
            if (inst > 60L * 1048576) inst = 0;   // 异常值丢弃
            emaSpeed = emaSpeed == 0 ? inst : emaSpeed * 0.72 + inst * 0.28;
            if (emaSpeed > statPeak) statPeak = emaSpeed;
            int aliveCount = 0;
            for (Thread t : threads) if (t.isAlive()) aliveCount++;
            statActive = aliveCount;
            statDone = base + g;
            progress((int) (((base + g) * 100) / Math.max(1, total)), fmtSpeed(emaSpeed), name);
            last = now;
            lastBytes = g;
            if (now - lastSave > 5000) {
                lastSave = now;
                fileLog("进度 " + (int) (((base + g) * 100) / Math.max(1, total)) + "% " + fmtSpeed(emaSpeed));
                lastBytes0 = g;
                lastSec0 = now;
            }
            if (now - lastSave > 3000 || now - lastSec0 > 5000) {
                try {
                    JSONArray a = new JSONArray();
                    for (long x : segDone) a.put(x);
                    jobState.put("seg", a);
                    saveJob(jobState);
                } catch (Throwable ignored) { }
            }
            if (!alive) break;
            if (cancel) break;
            Thread.sleep(700);
        }
        for (Thread t : threads) t.join(3000);
        try {
            JSONArray a = new JSONArray();
            for (long x : segDone) a.put(x);
            jobState.put("seg", a);
            saveJob(jobState);
        } catch (Throwable ignored) { }
        if (cancel) throw new Exception("已停止");
        if (err.get() != null) throw new Exception(err.get());
        return got.get();
    }

    // ---------------- 迅雷云盘 ----------------

    static final String XL_API = "https://api-pan.xunlei.com";

    static String enc(String s) {
        try { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); } catch (Exception e) { return ""; }
    }

    static String readText(InputStream is) throws Exception {
        if (is == null) return "";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
        is.close();
        return new String(bo.toByteArray(), "UTF-8");
    }

    JSONObject apiXunlei(String path, String method, JSONObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(XL_API + path).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setRequestProperty("User-Agent", UA_DESKTOP);
        c.setRequestProperty("Accept", "application/json, text/plain, */*");
        c.setRequestProperty("Accept-Encoding", "identity");
        c.setRequestProperty("Referer", "https://pan.xunlei.com/");
        c.setRequestProperty("Origin", "https://pan.xunlei.com");
        c.setRequestProperty("Authorization", (xlType == null || xlType.isEmpty() ? "Bearer" : xlType) + " " + xlToken);
        c.setRequestProperty("X-Captcha-Token", xlCaptcha);
        c.setRequestProperty("X-Device-Id", xlDevice);
        if (xlClient != null && !xlClient.isEmpty()) c.setRequestProperty("X-Client-Id", xlClient);
        try {
            String ck = CookieManager.getInstance().getCookie("https://pan.xunlei.com");
            if (ck != null && !ck.isEmpty()) c.setRequestProperty("Cookie", ck);
        } catch (Throwable ignored) { }
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            OutputStream os = c.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.close();
        }
        int code = c.getResponseCode();
        String text = readText(code >= 400 ? c.getErrorStream() : c.getInputStream());
        c.disconnect();
        if (text.isEmpty()) {
            if (code >= 400) throw new Exception("迅雷 HTTP " + code);
            return new JSONObject();
        }
        JSONObject j = new JSONObject(text);
        int ec = j.optInt("error_code", 0);
        String err = j.optString("error", "");
        if (code >= 400 || ec != 0 || (!err.isEmpty() && !"null".equals(err))) {
            String desc = j.optString("error_description", err.isEmpty() ? ("HTTP " + code) : err);
            if (ec == 9 || "captcha_invalid".equals(err))
                throw new Exception("迅雷验证码过期");
            if (ec == 10 || ec == 16 || ec == 4121 || ec == 4122 || code == 401)
                throw new Exception("迅雷登录已失效，回首页点「登录」重新登录");
            throw new Exception("迅雷：" + desc);
        }
        return j;
    }

    JSONObject xlShare(String shareId, String pwd, String passToken, String parentId, String pageToken) throws Exception {
        boolean sub = parentId != null && !parentId.isEmpty();
        StringBuilder q = new StringBuilder(sub ? "/drive/v1/share/detail" : "/drive/v1/share");
        q.append("?share_id=").append(enc(shareId));
        if (sub) q.append("&parent_id=").append(enc(parentId));
        else q.append("&pass_code=").append(enc(pwd));
        q.append("&pass_code_token=").append(enc(passToken))
                .append("&limit=100&page_token=").append(enc(pageToken))
                .append("&thumbnail_size=SIZE_SMALL&space=&scene=NORMAL");
        return xlApi(q.toString(), "GET", null);
    }

    String xlMkdir(String name) throws Exception {
        JSONObject b = new JSONObject().put("parent_id", "").put("name", name)
                .put("kind", "drive#folder").put("space", "");
        JSONObject j = xlApi("/drive/v1/files", "POST", b);
        JSONObject f = j.optJSONObject("file");
        String id = f == null ? "" : f.optString("id", "");
        if (id.isEmpty()) id = j.optString("id", "");
        if (id.isEmpty()) throw new Exception("返回里没有目录 id");
        return id;
    }

    void walkXunleiDrive(String parentId, String rel, List<Item> out) throws Exception {
        String pageToken = "";
        do {
            String path = "/drive/v1/files?parent_id=" + enc(parentId) + "&limit=100&page_token=" + enc(pageToken)
                    + "&space=&filters=" + enc("{\"trashed\":{\"eq\":false}}");
            JSONObject j = xlApi(path, "GET", null);
            JSONArray arr = j.optJSONArray("files");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    String nm = o.optString("name", "file");
                    String r = rel.isEmpty() ? nm : rel + "/" + nm;
                    if ("drive#folder".equals(o.optString("kind", ""))) {
                        walkXunleiDrive(o.optString("id", ""), r, out);
                    } else {
                        Item it = new Item();
                        it.fid = o.optString("id", "");
                        it.name = nm;
                        it.rel = r;
                        it.size = o.optLong("size", 0);
                        out.add(it);
                    }
                }
            }
            pageToken = j.optString("next_page_token", "");
        } while (!pageToken.isEmpty());
    }

    String xlLink(String fileId) throws Exception {
        JSONObject j = xlApi("/drive/v1/files/" + enc(fileId) + "?space=&usage=CONSUME", "GET", null);
        String u = j.optString("web_content_link", "");
        if (u.isEmpty()) {
            JSONObject links = j.optJSONObject("links");
            if (links != null) {
                JSONObject oct = links.optJSONObject("application/octet-stream");
                if (oct != null) u = oct.optString("url", "");
            }
        }
        if (u.isEmpty()) {
            JSONArray medias = j.optJSONArray("medias");
            if (medias != null) for (int i = 0; i < medias.length(); i++) {
                JSONObject m = medias.getJSONObject(i);
                if ("原始画质".equals(m.optString("media_name", ""))) {
                    JSONObject lk = m.optJSONObject("link");
                    if (lk != null) u = lk.optString("url", "");
                }
            }
        }
        return u;
    }

    static boolean isXlCaptchaError(Throwable e) {
        String m = e == null ? null : e.getMessage();
        return m != null && (m.contains("验证码") || m.contains("captcha"));
    }

    /** 迅雷接口包装：验证码过期时提示重新登录（不再自动刷令牌）。 */
    JSONObject xlApi(String path, String method, JSONObject body) throws Exception {
        try {
            return apiXunlei(path, method, body);
        } catch (Exception e) {
            if (!isXlCaptchaError(e)) throw e;
            fileLog("迅雷验证码过期，提示重新登录");
            throw new Exception("迅雷验证码过期，请点「重新登录」重新登录");
        }
    }

    void runJobXunlei(String rawLink) throws Exception {
        Matcher m = Pattern.compile("pan\\.xunlei\\.com/s/([0-9A-Za-z_-]+)").matcher(rawLink == null ? "" : rawLink);
        if (!m.find()) throw new Exception("不是迅雷云盘分享链接");
        final String shareId = m.group(1);
        String pwd = "";
        Matcher p2 = Pattern.compile("[?&#](?:pwd|password)=([0-9A-Za-z]{4})").matcher(rawLink);
        if (p2.find()) pwd = p2.group(1);

        statStart = System.currentTimeMillis();
        statDone = 0; statTotal = 0; statParts = 0; statActive = 0; statFails = 0;
        statPeak = 0; emaSpeed = 0; statHost = ""; statPhase = "解析分享";
        final Thread tick = new Thread(new Runnable() {
            public void run() {
                try {
                    while (busy) { pushStats(); Thread.sleep(900); }
                    pushStats();
                } catch (Throwable ignored) { }
            }
        });
        tick.start();

        status("解析分享…");
        fileLog("迅雷分享 " + shareId + (pwd.isEmpty() ? "" : "（有提取码）"));

        String oldTmp = sp.getString("xlTmp", "");
        if (!oldTmp.isEmpty()) {
            try {
                apiXunlei("/drive/v1/files:batchDelete", "POST",
                        new JSONObject().put("ids", new JSONArray().put(oldTmp)).put("space", ""));
                fileLog("已清理上次残留的迅雷暂存目录");
            } catch (Exception ignored) { }
            sp.edit().remove("xlTmp").apply();
        }

        JSONObject sh = xlShare(shareId, pwd, "", "", "");
        String shareStatus = sh.optString("share_status", "");
        if (!"OK".equals(shareStatus)) {
            if ("SENSITIVE_RESOURCE".equals(shareStatus))
                throw new Exception("分享内容被屏蔽（敏感资源），无法下载");
            throw new Exception("分享不可用（" + shareStatus + "）");
        }
        String passToken = sh.optString("pass_code_token", "");
        String title = sh.optString("title", "");
        if (title.isEmpty()) title = shareId;
        int fileNum = sh.optInt("file_num", sh.optInt("file_count", 0));
        log("分享：" + title + (fileNum > 0 ? " · " + fileNum + " 项" : ""));

        JSONArray rootFiles = sh.optJSONArray("files");
        if (rootFiles == null || rootFiles.length() == 0) throw new Exception("分享为空，或者需要提取码");
        JSONArray topIds = new JSONArray();
        for (int i = 0; i < rootFiles.length(); i++) {
            JSONObject o = rootFiles.getJSONObject(i);
            String id = o.optString("id", "");
            if (!id.isEmpty()) topIds.put(id);
        }
        if (topIds.length() == 0) throw new Exception("分享里没有可转存的文件");

        status("转存到网盘…"); statPhase = "转存";
        String tmp = xlMkdir("qdl-" + shareId.substring(Math.max(0, shareId.length() - 6)) + "-" + (System.currentTimeMillis() / 1000));
        sp.edit().putString("xlTmp", tmp).apply();
        fileLog("迅雷暂存目录 " + tmp);

        JSONObject rs = xlApi("/drive/v1/share/restore", "POST", new JSONObject()
                .put("parent_id", tmp)
                .put("share_id", shareId)
                .put("pass_code_token", passToken)
                .put("ancestor_ids", new JSONArray())
                .put("file_ids", topIds)
                .put("specify_parent_id", true));
        String taskId = rs.optString("restore_task_id", "");
        if (!taskId.isEmpty()) {
            boolean done = false;
            for (int i = 0; i < 120 && !cancel; i++) {
                Thread.sleep(1200);
                JSONObject t = xlApi("/drive/v1/tasks/" + enc(taskId) + "?type=share", "GET", null);
                int prog = t.optInt("progress", 0);
                String phase = t.optString("phase", t.optString("status", ""));
                if (prog >= 100 || "PHASE_TYPE_COMPLETE".equals(phase)) { done = true; break; }
                if ("PHASE_TYPE_ERROR".equals(phase))
                    throw new Exception("转存失败：" + t.optString("message", "未知错误"));
            }
            if (!done && !cancel) throw new Exception("转存超时，稍后再试");
        }
        if (cancel) { status("已停止"); return; }

        status("扫描文件…"); statPhase = "扫描";
        List<Item> files = new ArrayList<Item>();
        for (int i = 0; i < 10; i++) {
            files.clear();
            walkXunleiDrive(tmp, "", files);
            if (!files.isEmpty()) break;
            Thread.sleep(1000);
        }
        if (files.isEmpty()) throw new Exception("转存后没拿到文件列表");
        long total = 0;
        for (Item it : files) total += it.size;
        boolean single = files.size() == 1;
        String relDir = "Download/" + OUT_ROOT + (single ? "" : "/" + safe(title));
        log("共 " + files.size() + " 个文件，" + mb(total) + "，存到「下载/" + OUT_ROOT + (single ? "" : "/" + safe(title)) + "」");

        statPhase = "取直链";
        statTotal = total;
        statDlStart = System.currentTimeMillis();
        if (debugMode && !files.isEmpty()) {
            Item probe = files.get(0);
            String pu = xlLink(probe.fid);
            if (pu != null && !pu.isEmpty() && probe.size > 6L * 1048576) {
                int best = speedTest(pu, probe.size);
                if (best > 0) { forcedParts = best; log("本文件采用 " + best + " 段"); }
            }
        }
        long base = 0;
        int doneFiles = 0;
        for (Item it : files) {
            if (cancel) { status("已停止"); return; }
            String u = xlLink(it.fid);
            if (u.isEmpty()) { log("✗ 拿不到直链：" + it.name); continue; }
            String sub = relDir;
            String name = it.rel;
            int cut = it.rel.lastIndexOf('/');
            if (cut > 0) { sub = relDir + "/" + it.rel.substring(0, cut); name = it.rel.substring(cut + 1); }
            status("下载 " + name);
            statName = name; statPhase = "下载";
            fileLog("开始下载 " + name + " " + it.size + " bytes");
            try {
                downloadTo(u, sub, name, total, base, it);
            } catch (Exception e) {
                if (cancel) status("已停止，再点开始可续传");
                else status("失败：" + e.getMessage());
                log("✗ " + name + "：" + e.getMessage());
                return;
            }
            base += it.size;
            doneFiles++;
            progress((int) (base * 100 / Math.max(1, total)), "", name);
            log("✓ " + name);
        }
        if (cancel) { status("已停止（网盘暂存保留，可续传）"); return; }

        try {
            apiXunlei("/drive/v1/files:batchDelete", "POST",
                    new JSONObject().put("ids", new JSONArray().put(tmp)).put("space", ""));
            sp.edit().remove("xlTmp").apply();
            log("已清理网盘暂存");
        } catch (Exception e) {
            log("网盘暂存没删掉（不影响文件）");
        }
        double dlAvg = total / Math.max(1.0, (System.currentTimeMillis() - statDlStart) / 1000.0);
        double allAvg = total / Math.max(1.0, (System.currentTimeMillis() - statStart) / 1000.0);
        log("下载均速 " + fmtSpeed(dlAvg) + "（纯下载阶段）· 全程均速 " + fmtSpeed(allAvg) + "（含转存/解析）");
        fileLog("迅雷下载均速 " + fmtSpeed(dlAvg) + " / 全程 " + fmtSpeed(allAvg));
        status("完成 " + doneFiles + " 个文件");
        log("全部完成，去「下载/" + OUT_ROOT + "」找文件");
    }

    // ---------------- 百度网盘 ----------------

    static final String BD_HOST = "https://pan.baidu.com";
    static final String BD_API_UA = "netdisk";
    static final String BD_WEB_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    /** 只发登录必需的 Cookie，避免百度 400 Cookie Too Large。 */
    String bdCookie() {
        String ck = cookieBaidu == null ? "" : cookieBaidu;
        StringBuilder sb = new StringBuilder();
        for (String part : ck.split(";")) {
            String t = part.trim();
            int eq = t.indexOf('=');
            if (eq <= 0) continue;
            String name = t.substring(0, eq);
            if (name.equals("BDUSS") || name.equals("BDUSS_BFESS") || name.equals("STOKEN")
                    || name.equals("BAIDUID") || name.equals("PANPSC")) {
                if (sb.length() > 0) sb.append("; ");
                sb.append(t);
            }
        }
        if (!bdClnd.isEmpty() && sb.indexOf("BDCLND=") < 0) {
            if (sb.length() > 0) sb.append("; ");
            sb.append("BDCLND=").append(bdClnd);
        }
        return sb.toString();
    }

    /** 分享页只能带 BDCLND：带浏览器 BAIDUID 会被百度换成 50KB 的「分享文件」页，没有 locals。 */
    String bdShareCookie() {
        String ck = bdClnd.isEmpty() ? "" : ("BDCLND=" + bdClnd);
        fileLog("bdShareCookie 返回=" + (ck.isEmpty() ? "空" : "BDCLND长度" + bdClnd.length()));
        return ck;
    }

    JSONObject apiBaidu(String path, String method, String formBody) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(BD_HOST + path).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setRequestProperty("User-Agent", BD_API_UA);
        c.setRequestProperty("Accept", "application/json, text/plain, */*");
        c.setRequestProperty("Accept-Encoding", "identity");
        c.setRequestProperty("Referer", "https://pan.baidu.com/disk/home");
        c.setRequestProperty("Origin", "https://pan.baidu.com");
        c.setRequestProperty("Cookie", bdCookie());
        if (formBody != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            OutputStream os = c.getOutputStream();
            os.write(formBody.getBytes("UTF-8"));
            os.close();
        }
        int code = c.getResponseCode();
        String text = readText(code >= 400 ? c.getErrorStream() : c.getInputStream());
        c.disconnect();
        if (text.isEmpty()) throw new Exception("百度网络错误 HTTP " + code);
        JSONObject j = new JSONObject(text);
        int errno = j.optInt("errno", 0);
        if (errno != 0) throw new Exception("百度：" + errno + " " + bdErr(errno));
        return j;
    }

    String bdGetRaw(String path) throws Exception {
        String url = BD_HOST + path;
        String shareCk = bdShareCookie();
        fileLog("bdGetRaw 开始: path=" + path);
        fileLog("bdGetRaw Cookie: " + (shareCk.isEmpty() ? "空" : "BDCLND长度=" + (bdClnd.isEmpty() ? 0 : bdClnd.length())));
        for (int i = 0; i < 6; i++) {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(20000);
            c.setReadTimeout(60000);
            c.setRequestProperty("User-Agent", BD_WEB_UA);
            c.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*");
            c.setRequestProperty("Accept-Encoding", "identity");
            c.setRequestProperty("Referer", "https://pan.baidu.com/disk/home");
            c.setRequestProperty("Cookie", shareCk);
            int code = c.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null || loc.isEmpty()) break;
                url = new URL(new URL(url), loc).toString();
                fileLog("bdGetRaw 302跳转到 " + url);
                continue;
            }
            String t = readText(code >= 400 ? c.getErrorStream() : c.getInputStream());
            c.disconnect();
            fileLog("bdGetRaw 返回 code=" + code + " 长度=" + t.length());
            return t;
        }
        return "";
    }

    static String extractLocals(String html) {
        int i = html == null ? -1 : html.indexOf("locals.mset(");
        if (i < 0) return null;
        int start = html.indexOf('{', i);
        if (start < 0) return null;
        int depth = 0;
        boolean inStr = false;
        for (int k = start; k < html.length(); k++) {
            char ch = html.charAt(k);
            if (inStr) {
                if (ch == '\\') k++;
                else if (ch == '"') inStr = false;
                continue;
            }
            if (ch == '"') inStr = true;
            else if (ch == '{') depth++;
            else if (ch == '}') {
                depth--;
                if (depth == 0) return html.substring(start, k + 1);
            }
        }
        return null;
    }

    JSONObject bdLocals(String surl, String pwd) throws Exception {
        String html = bdGetRaw("/s/1" + surl + (pwd.isEmpty() ? "" : "?pwd=" + enc(pwd)));
        String js = extractLocals(html);
        String title = "";
        try {
            Matcher tm = Pattern.compile("<title>([^<]*)</title>").matcher(html == null ? "" : html);
            if (tm.find()) title = tm.group(1);
        } catch (Throwable ignored) { }
        fileLog("百度分享页 " + (html == null ? 0 : html.length()) + " 字节，locals=" + (js != null) + " title=" + title);
        if (js == null) throw new Exception("百度：分享页解析失败，可能要在网页里过一次验证");
        return new JSONObject(js);
    }

    static String bdErr(int e) {
        switch (e) {
            case -6: return "登录已失效，请重新登录";
            case -9: return "文件或提取码不对";
            case -12: return "提取码错误";
            case -19: case -62: return "需要验证码，请在网页里过一次验证";
            case 2: return "参数错误（可能缺 bdstoken）";
            case 3: return "未登录";
            case 12: return "转存失败（空间不足或文件被和谐）";
            case 105: return "分享链接没有文件";
            case 112: return "分享页过期，请重新登录后再试";
            case 132: return "触发安全验证，需要手动处理";
            default: return "";
        }
    }

    String bdToken() throws Exception {
        JSONObject j = apiBaidu("/api/gettemplatevariable?clienttype=0&app_id=250528&fields="
                + enc("[\"bdstoken\",\"username\",\"loginstate\"]"), "GET", null);
        JSONObject r = j.optJSONObject("result");
        if (r == null) throw new Exception("百度：拿不到 bdstoken");
        if (r.optInt("loginstate", 1) != 1) throw new Exception("百度登录已失效，请重新登录");
        String tok = r.optString("bdstoken", "");
        if (tok.isEmpty()) throw new Exception("百度：拿不到 bdstoken");
        return tok;
    }

    String bdVerify(String surl, String pwd) throws Exception {
        long ts = System.currentTimeMillis();
        JSONObject v = apiBaidu("/share/verify?surl=" + enc(surl) + "&t=" + ts
                + "&channel=chunlei&web=1&app_id=250528&clienttype=0", "POST",
                "pwd=" + enc(pwd) + "&vcode=&vcode_str=");
        return v.optString("randsk", "");
    }

    JSONObject bdWxList(String surl, String pwd, String dir, int root, int page) throws Exception {
        String body = "pwd=" + enc(pwd) + "&root=" + root + "&shorturl=" + enc(surl)
                + (root == 0 ? "&dir=" + enc(dir) : "") + "&num=1000&page=" + page + "&order=time";
        JSONObject j = apiBaidu("/share/wxlist?channel=weixin&version=2.2.2&clienttype=25&web=1", "POST", body);
        JSONObject d = j.optJSONObject("data");
        if (d == null) throw new Exception("百度：分享返回格式异常");
        int e = d.optInt("errno", 0);
        if (e != 0) throw new Exception("百度：" + e + " " + bdErr(e));
        return d;
    }

    void walkBaiduShare(String surl, String pwd, String dir, String rel, List<Item> out) throws Exception {
        int page = 1;
        while (true) {
            JSONObject d = bdWxList(surl, pwd, dir, dir.isEmpty() ? 1 : 0, page);
            JSONArray arr = d.optJSONArray("list");
            if (arr == null || arr.length() == 0) break;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String p = o.optString("path", "");
                String nm = o.optString("server_filename", "");
                if (nm.isEmpty()) {
                    if (p.endsWith("/")) continue;
                    int cut = p.lastIndexOf('/');
                    nm = cut >= 0 ? p.substring(cut + 1) : p;
                }
                if (p.equals("/") || (dir.isEmpty() && p.equals("/"))) continue;
                String r = rel.isEmpty() ? nm : rel + "/" + nm;
                if (o.optInt("isdir", 0) == 1) {
                    walkBaiduShare(surl, pwd, p.isEmpty() ? dir + "/" + nm : p, r, out);
                } else {
                    Item it = new Item();
                    it.fid = o.optString("fs_id", "");
                    it.name = nm;
                    it.rel = r;
                    it.size = o.optLong("size", 0);
                    it.s3 = p;
                    out.add(it);
                }
            }
            if (!d.optBoolean("has_more", false) || arr.length() < 1000) break;
            page++;
        }
    }

    void walkBaiduDrive(String path, String rel, List<Item> out, String tok) throws Exception {
        int page = 1;
        while (true) {
            long ts = System.currentTimeMillis();
            String q = "/api/list?order=name&desc=0&showempty=0&web=1&page=" + page + "&num=100&dir=" + enc(path)
                    + "&t=" + ts + "&channel=chunlei&web=1&app_id=250528&bdstoken=" + enc(tok) + "&logid=" + ts;
            JSONObject j = apiBaidu(q, "GET", null);
            JSONArray arr = j.optJSONArray("list");
            if (arr == null || arr.length() == 0) break;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String nm = o.optString("server_filename", "file");
                String r = rel.isEmpty() ? nm : rel + "/" + nm;
                if (o.optInt("isdir", 0) == 1) {
                    walkBaiduDrive(path + "/" + nm, r, out, tok);
                } else {
                    Item it = new Item();
                    it.fid = o.optString("fs_id", "");
                    it.name = nm;
                    it.rel = r;
                    it.size = o.optLong("size", 0);
                    it.s3 = path + "/" + nm;
                    out.add(it);
                }
            }
            if (arr.length() < 100) break;
            page++;
        }
    }

    /** 百度普通 Web 会话的 dlink 会 31362，改用 PCS 老端点按路径下载。 */
    String bdPcsUrl(String remotePath) {
        return "https://d.pcs.baidu.com/rest/2.0/pcs/file?method=download&app_id=250528&path=" + enc(remotePath);
    }

    void runJobBaidu(String rawLink) throws Exception {
        Matcher m = Pattern.compile("(?:pan|yun)\\.baidu\\.com/s/([0-9A-Za-z_-]+)").matcher(rawLink == null ? "" : rawLink);
        if (!m.find()) throw new Exception("不是百度网盘分享链接");
        String key = m.group(1);
        String surl = (key.length() > 1 && key.startsWith("1")) ? key.substring(1) : key;
        String pwd = "";
        Matcher p2 = Pattern.compile("[?&#](?:pwd|password)=([0-9A-Za-z]{4})").matcher(rawLink);
        if (p2.find()) pwd = p2.group(1);

        statStart = System.currentTimeMillis();
        statDone = 0; statTotal = 0; statParts = 0; statActive = 0; statFails = 0;
        statPeak = 0; emaSpeed = 0; statHost = ""; statPhase = "解析分享";
        final Thread tick = new Thread(new Runnable() {
            public void run() {
                try {
                    while (busy) { pushStats(); Thread.sleep(900); }
                    pushStats();
                } catch (Throwable ignored) { }
            }
        });
        tick.start();

        status("解析分享…");
        fileLog("百度分享 " + surl + (pwd.isEmpty() ? "" : "（有提取码）"));
        String savedBdClnd = "";
        if (!pwd.isEmpty()) {
            try {
                bdClnd = bdVerify(surl, pwd);
                savedBdClnd = bdClnd;
                fileLog("百度 verify 成功 randsk=" + (bdClnd.isEmpty() ? "空" : "有") + " 长度=" + bdClnd.length());
            } catch (Exception e) {
                fileLog("百度 verify 失败：" + e.getMessage());
            }
        }
        fileLog("调用 bdToken 前 bdClnd长度=" + bdClnd.length());
        String tok = bdToken();
        fileLog("百度 bdstoken 获取成功，调用后 bdClnd长度=" + bdClnd.length());

        // 恢复 bdClnd，因为 bdToken() 可能改变了它
        if (!savedBdClnd.isEmpty() && !savedBdClnd.equals(bdClnd)) {
            fileLog("检测到 bdClnd 被改变，恢复为 verify 返回的值");
            bdClnd = savedBdClnd;
        }

        JSONArray rootList = null;
        String uk = "", shareid = "";
        try {
            fileLog("调用 bdLocals 前 bdClnd长度=" + bdClnd.length());
            JSONObject locals = bdLocals(surl, pwd);
            shareid = locals.optString("shareid", locals.optString("share_id", ""));
            uk = locals.optString("share_uk", locals.optString("uk", ""));
            rootList = locals.optJSONArray("file_list");
            fileLog("locals 解析成功：shareid=" + shareid + " uk=" + uk + " files=" + (rootList == null ? 0 : rootList.length()));
        } catch (Exception e) {
            fileLog("locals 失败，改用 wxlist：" + e.getMessage());
        }
        if (rootList == null || rootList.length() == 0) {
            JSONObject first = bdWxList(surl, pwd, "", 1, 1);
            uk = first.optString("uk", "");
            shareid = first.optString("shareid", first.optString("share_id", ""));
            rootList = first.optJSONArray("list");
        }
        if (rootList == null || rootList.length() == 0) throw new Exception("分享为空，或者需要提取码");
        JSONArray topIds = new JSONArray();
        int topCount = 0;
        for (int i = 0; i < rootList.length(); i++) {
            JSONObject o = rootList.getJSONObject(i);
            String p = o.optString("path", "");
            if (p.equals("/")) continue;
            String id = o.optString("fs_id", "");
            if (!id.isEmpty()) { topIds.put(Long.parseLong(id)); topCount++; }
        }
        if (topCount == 0) throw new Exception("分享里没有可转存的文件");
        log("分享解析到 " + topCount + " 个顶层条目");

        status("转存到网盘…"); statPhase = "转存";
        String tmpPath = "/qdl-" + System.currentTimeMillis();
        try {
            apiBaidu("/api/create?a=commit&channel=chunlei&web=1&clienttype=0&app_id=250528&bdstoken=" + enc(tok), "POST",
                    "path=" + enc(tmpPath) + "&isdir=1&size=0&block_list=" + enc("[]"));
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (!msg.contains("-8")) throw e;
        }
        fileLog("百度暂存目录 " + tmpPath);

        String sekey = bdClnd;
        try { sekey = URLDecoder.decode(bdClnd, "UTF-8"); } catch (Throwable ignored) { }
        JSONObject tr = apiBaidu("/share/transfer?shareid=" + enc(shareid) + "&from=" + enc(uk)
                + "&bdstoken=" + enc(tok) + "&sekey=" + enc(sekey)
                + "&ondup=newcopy&async=2&channel=chunlei&web=1&clienttype=0&app_id=250528",
                "POST", "fsidlist=" + enc(topIds.toString()) + "&path=" + enc(tmpPath));
        fileLog("转存返回 " + tr.toString().substring(0, Math.min(200, tr.toString().length())));

        status("扫描文件…"); statPhase = "扫描";
        List<Item> files = new ArrayList<Item>();
        for (int i = 0; i < 40; i++) {
            files.clear();
            walkBaiduDrive(tmpPath, "", files, tok);
            if (!files.isEmpty()) break;
            Thread.sleep(1500);
        }
        if (files.isEmpty()) throw new Exception("转存后没拿到文件列表，可能触发验证或空间不足");

        long total = 0;
        for (Item it : files) total += it.size;
        boolean single = files.size() == 1;
        String relDir = "Download/" + OUT_ROOT + (single ? "" : "/" + safe(key));
        log("共 " + files.size() + " 个文件，" + mb(total) + "，存到「下载/" + OUT_ROOT + (single ? "" : "/" + safe(key)) + "」");

        statPhase = "取直链";
        statTotal = total;
        statDlStart = System.currentTimeMillis();
        long base = 0;
        int doneFiles = 0;
        for (Item it : files) {
            if (cancel) { status("已停止"); return; }
            if (it.s3 == null || it.s3.isEmpty()) { log("✗ 拿不到路径：" + it.name); continue; }
            String u = bdPcsUrl(it.s3);
            String sub = relDir;
            String name = it.rel;
            int cut = it.rel.lastIndexOf('/');
            if (cut > 0) { sub = relDir + "/" + it.rel.substring(0, cut); name = it.rel.substring(cut + 1); }
            status("下载 " + name);
            statName = name; statPhase = "下载";
            fileLog("开始下载 " + name + " " + it.size + " bytes");
            try {
                downloadTo(u, sub, name, total, base, it);
            } catch (Exception e) {
                if (cancel) status("已停止，再点开始可续传");
                else status("失败：" + e.getMessage());
                log("✗ " + name + "：" + e.getMessage());
                return;
            }
            base += it.size;
            doneFiles++;
            progress((int) (base * 100 / Math.max(1, total)), "", name);
            log("✓ " + name);
        }
        if (cancel) { status("已停止（网盘暂存保留，可续传）"); return; }

        try {
            apiBaidu("/api/filemanager?opera=delete&bdstoken=" + enc(tok)
                    + "&channel=chunlei&web=1&clienttype=0&app_id=250528",
                    "POST", "async=1&filelist=" + enc("[\"" + tmpPath + "\"]"));
            log("已清理网盘暂存");
        } catch (Exception e) {
            log("百度暂存没删掉（可能要求安全验证），请手动删除网盘里的 " + tmpPath);
        }
        status("完成 " + doneFiles + " 个文件");
        log("全部完成，去「下载/" + OUT_ROOT + "」找文件");
    }

    // ---------------- 123 云盘 ----------------

    String http123(String path, String method, JSONObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(apiHost123 + path).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        headers(c);
        c.setRequestProperty("Accept", "application/json, text/plain, */*");
        if (token123 != null && !token123.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + token123);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            OutputStream os = c.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.close();
        }
        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        if (is == null) throw new Exception("123 网络错误 HTTP " + code);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
        is.close();
        return new String(bo.toByteArray(), "UTF-8");
    }

    JSONObject api123(String path, String method, JSONObject body) throws Exception {
        JSONObject j = new JSONObject(http123(path, method, body));
        int code = j.optInt("code", 0);
        if (code != 0) {
            String msg = j.optString("message", "返回 " + code);
            if (code == 5112) throw new Exception("123：" + msg + "（分享方下载流量包不足；可登录 123 账号后再试）");
            throw new Exception("123：" + msg);
        }
        return j;
    }

    void walk123(String key, String pwd, String parent, String rel, List<Item> out) throws Exception {
        String q = "/api/share/get?shareKey=" + key + "&ParentFileId=" + parent
                + "&limit=100&next=0&Page=1&SharePwd=" + pwd
                + "&orderBy=1&orderDirection=asc";
        JSONObject j = api123(q, "GET", null);
        JSONArray list = j.optJSONObject("data").optJSONArray("InfoList");
        if (list == null) return;
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.getJSONObject(i);
            String nm = o.optString("FileName", "file");
            String r = rel.isEmpty() ? nm : rel + "/" + nm;
            if (o.optInt("Type", 0) == 1) {
                walk123(key, pwd, String.valueOf(o.optLong("FileId")), r, out);
            } else {
                Item it = new Item();
                it.numId = o.optLong("FileId");
                it.name = nm;
                it.rel = r;
                it.size = o.optLong("Size", 0);
                it.etag = o.optString("Etag", "");
                it.s3 = o.optString("S3KeyFlag", "");
                out.add(it);
            }
        }
    }

    void runJob123(String rawLink) throws Exception {
        Matcher m = Pattern.compile("(?:www\\.)?(123pan\\.com|123684\\.com|123865\\.com|123pan\\.cn)/s/([0-9A-Za-z-]+)").matcher(rawLink == null ? "" : rawLink);
        if (!m.find()) throw new Exception("不是 123 云盘分享链接");
        base123 = "https://www." + m.group(1);
        String key = m.group(2).replaceAll("\\.html$", "");
        String pwd = "";
        Matcher p2 = Pattern.compile("[?&#](?:pwd|password)=([0-9A-Za-z]{4})").matcher(rawLink);
        if (p2.find()) pwd = p2.group(1);

        statStart = System.currentTimeMillis();
        statPhase = "解析分享";
        statDone = 0; statTotal = 0; statParts = 0; statActive = 0; statFails = 0; statPeak = 0; emaSpeed = 0;
        final Thread tick = new Thread(new Runnable() {
            public void run() {
                try {
                    while (busy) { pushStats(); Thread.sleep(900); }
                } catch (Throwable ignored) { }
            }
        });
        tick.start();

        fileLog("123 分享 " + key);
        JSONObject info = api123("/api/share/info?shareKey=" + key + "&SharePwd=" + pwd, "GET", null);
        String title = info.optJSONObject("data").optString("ShareName", key);
        log("分享：" + title);

        statPhase = "扫描";
        List<Item> files = new ArrayList<Item>();
        walk123(key, pwd, "0", "", files);
        if (files.isEmpty()) throw new Exception("分享里没有文件");
        long total = 0;
        for (Item it : files) total += it.size;
        boolean single = files.size() == 1;
        String relDir = "Download/" + OUT_ROOT + (single ? "" : "/" + safe(title));
        log("共 " + files.size() + " 个文件，" + mb(total));

        statPhase = "取直链";
        long base = 0;
        int done = 0;
        statTotal = total;
        statDlStart = System.currentTimeMillis();
        for (Item it : files) {
            if (cancel) { status("已停止"); return; }
            JSONObject body = new JSONObject()
                    .put("shareKey", key).put("fileId", it.numId).put("s3keyFlag", it.s3)
                    .put("size", it.size).put("type", 0).put("etag", it.etag).put("fileName", it.name);
            JSONObject dl;
            try {
                dl = api123("/api/share/download/info", "POST", body);
            } catch (Exception e) {
                throw new Exception(it.name + "：" + e.getMessage());
            }
            String url = dl.optJSONObject("data") == null ? "" : dl.optJSONObject("data").optString("DownloadUrl", "");
            if (url.isEmpty()) { log("✗ 拿不到直链：" + it.name); continue; }
            String sub = relDir;
            String name = it.rel;
            int cut = it.rel.lastIndexOf('/');
            if (cut > 0) { sub = relDir + "/" + it.rel.substring(0, cut); name = it.rel.substring(cut + 1); }
            status("下载 " + name);
            statName = name; statPhase = "下载";
            try {
                downloadTo(url, sub, name, total, base, it);
            } catch (Exception e) {
                if (cancel) status("已停止，再点开始可续传");
                else status("失败：" + e.getMessage());
                log("✗ " + name + "：" + e.getMessage());
                return;
            }
            base += it.size;
            done++;
            log("✓ " + name);
        }
        double dlAvg = total / Math.max(1.0, (System.currentTimeMillis() - statDlStart) / 1000.0);
        log("下载均速 " + fmtSpeed(dlAvg));
        status("完成 " + done + " 个文件");
    }
}
