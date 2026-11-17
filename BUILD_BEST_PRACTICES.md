# Android 构建最佳实践指南

本文档基于业界高 star 项目的构建经验,为 qdl 项目提供完善的构建配置。

## 📋 已实施的改进

### 1. 增强构建脚本 (`build-enhanced.sh`)

**特点:**
- ✅ 完整的错误处理和前置检查
- ✅ 彩色输出和进度提示
- ✅ 构建时间统计
- ✅ APK 签名验证
- ✅ 详细的构建信息展示
- ✅ 清理和临时文件管理

**使用:**
```bash
cd source
bash build-enhanced.sh
```

### 2. 安全网络配置 (`network_security_config.xml`)

**功能:**
- 🔒 明确信任的域名白名单
- 🔒 支持明文 HTTP (网盘 API 需要)
- 🔒 信任系统和用户证书
- 🔒 调试时的证书配置

**受信任域名:**
- 百度网盘: `pan.baidu.com`, `pcs.baidu.com`
- 夸克网盘: `pan.quark.cn`, `drive.uc.cn`
- 123网盘: `www.123pan.com`
- 迅雷: `pan.xunlei.com`

### 3. ProGuard 混淆规则 (`proguard-rules.pro`)

**保护内容:**
- 保留 WebView JavaScript 接口
- 保留注解和泛型签名
- 保护 Activity 和 Service
- 保留行号用于调试堆栈

**未来可启用 R8 shrinking:**
```bash
java -cp /root/build/r8.jar com.android.tools.r8.R8 \
    --lib $ANDROID_JAR \
    --min-api 21 \
    --output outdex \
    --pg-conf proguard-rules.pro \
    $(find out -name '*.class')
```

### 4. 增强的 AndroidManifest

**新增特性:**
- 📱 支持 Android 13+ 新权限模型
- 📱 DeepLink 支持 (直接从浏览器打开链接)
- 📱 FileProvider 用于分享文件
- 📱 前台服务类型声明 (Android 14+)
- 📱 备份和数据迁移规则
- 📱 硬件加速和 RTL 支持

**权限优化:**
```xml
<!-- 分级权限请求 -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />  <!-- 只在旧版本请求 -->

<!-- Android 13+ 细粒度媒体权限 -->
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
```

### 5. 备份和恢复规则

**自动备份 (`backup_rules.xml`):**
- ✅ 备份用户设置
- ❌ 排除设备特定数据
- ❌ 排除缓存和数据库

**设备迁移 (`data_extraction_rules.xml`):**
- ✅ 迁移应用设置
- ❌ 不迁移下载任务

### 6. FileProvider 配置

**用途:**
- 安全地分享下载完成的文件
- 支持 Android 7.0+ 的文件权限模型
- 避免 `file://` URI 导致的 `FileUriExposedException`

## 🎨 美学和 UX 优化

### UI 改进总结

**深色主题:**
```css
--bg-primary: #0a0e14;    /* 深邃背景 */
--bg-card: #151a21;       /* 卡片背景 */
--accent: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
```

**微交互:**
- 按钮悬停放大 (scale: 1.02)
- 卡片悬停阴影增强
- 输入框聚焦高光效果
- 渐变进度条动画

**信息架构:**
- Emoji 图标快速识别
- Badge 状态颜色编码
- 16px 圆角柔和视觉
- 清晰的视觉层次

## 🔧 高级功能实现

### 1. Deep Link 处理

现在 APP 可以直接从浏览器打开网盘链接:

```xml
<intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="https" android:host="pan.baidu.com" />
</intent-filter>
```

**需要在 MainActivity 中添加:**
```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    // 处理 Deep Link
    Intent intent = getIntent();
    String action = intent.getAction();
    Uri data = intent.getData();
    
    if (Intent.ACTION_VIEW.equals(action) && data != null) {
        String url = data.toString();
        // 自动填充到输入框
        webView.evaluateJavascript(
            "document.getElementById('url').value = '" + url + "';",
            null
        );
    }
}
```

### 2. 前台服务优化 (Android 14+)

```xml
<service
    android:foregroundServiceType="dataSync"
    tools:targetApi="29" />
```

**需要在服务中:**
```java
Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
    .setContentTitle("正在下载")
    .setSmallIcon(R.mipmap.ic_launcher)
    .build();

startForeground(NOTIFICATION_ID, notification, 
    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
```

### 3. 运行时权限请求

**建议添加优雅的权限请求流程:**

```java
private void checkPermissions() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Android 13+
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) 
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                new String[]{Manifest.permission.POST_NOTIFICATIONS}, 
                REQUEST_CODE
            );
        }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        // Android 6-12
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) 
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }, 
                REQUEST_CODE
            );
        }
    }
}
```

## 📊 性能优化建议

### 1. 启用 R8 代码优化

```bash
# 替换 D8 为 R8 (支持 ProGuard 规则)
java -cp /root/build/r8.jar com.android.tools.r8.R8 \
    --lib $ANDROID_JAR \
    --min-api 21 \
    --output outdex \
    --pg-conf proguard-rules.pro \
    $(find out -name '*.class')
```

**优点:**
- 减小 APK 体积 (30-50%)
- 移除未使用代码
- 优化字节码
- 混淆类名

### 2. WebView 性能优化

**建议在 MainActivity 中添加:**
```java
webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
webView.getSettings().setDatabaseEnabled(true);
webView.getSettings().setDomStorageEnabled(true);
webView.getSettings().setAppCacheEnabled(true);
webView.getSettings().setAppCachePath(getCacheDir().getAbsolutePath());
```

### 3. 下载性能优化

**多线程下载 (非百度网盘):**
```java
// 对于支持多线程的网盘
ExecutorService downloadPool = Executors.newFixedThreadPool(3);
for (int i = 0; i < 3; i++) {
    final int part = i;
    downloadPool.submit(() -> downloadPart(url, part));
}
```

## 🔐 安全增强

### 1. 网络请求验证

```java
// 验证 URL 是否在白名单内
private boolean isAllowedDomain(String url) {
    String[] allowedDomains = {
        "pan.baidu.com",
        "pan.quark.cn",
        "www.123pan.com",
        "pan.xunlei.com"
    };
    
    for (String domain : allowedDomains) {
        if (url.contains(domain)) return true;
    }
    return false;
}
```

### 2. Cookie 安全存储

```java
// 使用 EncryptedSharedPreferences
SharedPreferences sharedPreferences = EncryptedSharedPreferences.create(
    "secret_shared_prefs",
    MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
    context,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
);
```

## 📱 兼容性测试矩阵

### 推荐测试配置

| Android 版本 | API Level | 关键特性 | 测试要点 |
|-------------|-----------|---------|---------|
| 5.0-5.1 | 21-22 | Material Design | 基础功能 |
| 6.0 | 23 | 运行时权限 | 权限请求流程 |
| 7.0-7.1 | 24-25 | FileProvider | 文件分享 |
| 8.0-8.1 | 26-27 | 通知渠道 | 下载通知 |
| 9.0 | 28 | DNS over TLS | 网络连接 |
| 10 | 29 | 分区存储 | 文件访问 |
| 11 | 30 | 强制分区存储 | 下载目录 |
| 12-12L | 31-32 | 蓝牙权限 | 基础功能 |
| 13 | 33 | 细粒度媒体权限 | 存储权限 |
| 14 | 34 | 前台服务类型 | 后台下载 |

## 🚀 CI/CD 集成

### GitHub Actions 示例

```yaml
name: Build APK

on:
  push:
    tags:
      - 'v*'

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up JDK 8
      uses: actions/setup-java@v4
      with:
        java-version: '8'
        distribution: 'temurin'
    
    - name: Install Android SDK
      run: |
        sudo apt-get update
        sudo apt-get install -y android-sdk
    
    - name: Build APK
      run: |
        cd source
        bash build-enhanced.sh
    
    - name: Upload APK
      uses: actions/upload-artifact@v4
      with:
        name: app-release
        path: source/app.apk
    
    - name: Create Release
      uses: softprops/action-gh-release@v1
      with:
        files: source/app.apk
```

## 📚 参考资源

### 高 Star 项目借鉴

本构建体系参考了以下领域的最佳实践:

1. **安全配置**: Signal Android, Telegram
2. **构建脚本**: F-Droid build recipes
3. **性能优化**: Google I/O app, Santa Tracker
4. **UI/UX**: Material Design sample apps

### 官方文档

- [Android App Bundle](https://developer.android.com/guide/app-bundle)
- [Network Security Configuration](https://developer.android.com/training/articles/security-config)
- [ProGuard/R8 Rules](https://developer.android.com/studio/build/shrink-code)
- [App Backup](https://developer.android.com/guide/topics/data/autobackup)

## 🎯 下一步优化方向

### 短期 (1-2 周)

- [ ] 实现 Deep Link 自动填充
- [ ] 添加运行时权限请求 UI
- [ ] 启用 R8 代码优化
- [ ] 添加下载完成通知

### 中期 (1 个月)

- [ ] 多线程下载 (非百度网盘)
- [ ] 下载队列管理
- [ ] 断点续传数据库持久化
- [ ] 深色/浅色主题切换

### 长期 (3 个月+)

- [ ] 迁移到 Gradle 构建系统
- [ ] 使用 Kotlin 重写
- [ ] 添加单元测试和 UI 测试
- [ ] Google Play 发布

---

**版本**: 0.37-beta  
**最后更新**: 2026-11-17  
**维护者**: Kiro AI Assistant
