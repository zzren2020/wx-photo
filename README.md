# 微信公众号图片下载器（Android 版）

把 Windows 版「微信公众号图片下载器 V1」的核心能力移植到 Android：

- 粘贴/微信分享进入文章链接 → 读取正文图片与封面 → 网格多选 → 下载到相册；
- 下载走**前台服务**，离开界面也能继续，通知栏可取消；
- 内置**运行日志**页：单条勾选 / 全选 / 复制，另有 Logcat 日志，方便排查；
- 解析失败/遇到验证页时，可用**内置浏览器兜底提取**（用户完成验证后注入 JS 取图，并复用 Cookie）。

> 本目录不含任何预编译产物，也不要求本机安装 Android SDK / Android Studio。
> 代码上传到 GitHub 后，由 **GitHub Actions** 自动构建出 APK。

---

## 一、上传到 GitHub（重要）

1. 在 GitHub 新建一个空仓库（不要勾选自动生成 README）。
2. 把**本文件夹里面的内容**上传到**仓库根目录**，即仓库根目录应该直接看到：
   ```
   .github/
   app/
   tools/
   build.gradle.kts
   settings.gradle.kts
   gradle.properties
   .gitignore
   README.md
   ```
   ⚠️ 不要上传成 `android-app/app/...` 这种多套一层的结构，否则 Actions 找不到工程。
3. 也可以在本目录执行：
   ```bash
   git init
   git add .
   git commit -m "feat: android app"
   git branch -M main
   git remote add origin https://github.com/<你>/<仓库>.git
   git push -u origin main
   ```

## 二、用 GitHub 生成 APK（无需本机安卓环境）

1. 打开仓库的 **Actions** 页；
2. 左侧选择 **Build APK**；
3. 点 **Run workflow** → 选择 `main` → **Run workflow**；
4. 等 5–10 分钟，任务变绿后，在本次运行页面底部 **Artifacts** 下载：
   `wechat-image-downloader-debug.zip`；
5. 解压得到 **`app-debug.apk`**，传到手机安装（需允许“安装未知来源应用”）。

> push 到 `main`/`master` 且改动到 `app/**` 或 Gradle 文件时，也会自动触发构建。
> 该 APK 是 **debug 签名**，适合自用/测试，不能直接上架应用商店。

### 构建流程做了什么

- JDK 17 + Android SDK（platform 35 / build-tools 35.0.0）；
- 下载 Gradle 8.9（仓库里没有 wrapper，避免二进制文件）；
- 先跑 `:app:testDebugUnitTest`（12+ 条解析规则单测，失败则不出包）；
- 再跑 `:app:assembleDebug`；
- 上传 APK 产物。

## 三、功能与使用

1. **读取**：粘贴链接，点「读取图片」；只想拿封面点「仅获取封面」。
2. **勾选**：点缩略图切换勾选；「全选」「反选」批量操作；长按缩略图看大图（支持双指缩放、双击放大、左右滑）。
3. **下载**：底部「下载选中（N 张）」，前台服务执行，通知栏显示进度并可取消。
4. **保存位置**：
   - 默认：系统相册 `Pictures/公众号图片/<时间戳_标题_随机4位>/001.jpg`；
   - 设置里可打开「保存到自定义目录」，用系统目录选择器（SAF）指定位置（可 SD 卡）。
5. **日志**：右上角列表图标进入日志页；勾选单条或「全选」后「复制选中」；长按单条也能复制；「清空」重置。同时输出到 Logcat：
   ```bash
   adb logcat -s App 网络 解析 下载 读取 兜底 服务 设置 界面 入口
   ```
6. **兜底**：读取失败时点「浏览器提取」，在页面内完成验证/登录后点「重新提取」，App 会抓取 `#js_content` 内图片并同步 Cookie。

## 四、与 Windows 版行为对照

| Windows 版 | Android 版 |
|---|---|
| 链接正则 + host/端口/路径校验、强制 https、去 fragment | `ArticleUrl.extract`，规则一致 |
| 正则去脚本/样式 + `div` 配对取 `#js_content` | Jsoup `div#js_content`（兜底任意 `id=js_content`） |
| `og:title` → `<title>` | 一致（并额外兼容 `name=og:title`） |
| `data-src > data-original > src`、跳过 `data:`、`//` 补 https、去重 | 一致 |
| qpic `/640|1080|720|300|1000` → `/0` | `ArticleUrl.original`，规则一致 |
| 封面 `og:image` → `msg_cdn_url/cdn_url` + JS 反转义 | 一致 |
| 魔数识别 png/jpg/gif/webp/avif/bmp | `ImageSniffer`，规则一致 |
| 12MB HTML / 80MB 图片 / 45s 请求 / 60s 读流 | 12MB / 80MB 保留；超时改为 15s 连接 + 30s 读 + 180s 总 |
| 整图读入内存 | **改为流式落临时文件**，降低 OOM |
| 串行下载、无重试 | 串行 + 单张失败继续；原图失败回退缩略图 |
| `%TEMP%` 缓存 + GDI+ 缩略图 | Coil 磁盘/内存缓存 + 系统解码 |
| `FolderBrowserDialog` + `explorer.exe` | SAF 目录选择 + MediaStore 写入 |
| `--self-test` | JVM 单元测试 `CoreTest`（构建时自动跑） |

## 四之二、应用名称与图标

- 应用名：**微信公众号图片下载器**（`res/values/strings.xml` 的 `app_name`，桌面标题与通知标题同源）。
- 图标：绿色渐变底 + 白色图片卡片 + 下载箭头，与「下载公众号图片」的用途一致。
  - 传统图标 `mipmap-*/ic_launcher.png`、圆形 `ic_launcher_round.png`（48/72/96/144/192 px）；
  - 自适应图标 `mipmap-anydpi-v26/ic_launcher*.xml`，前景 `ic_launcher_foreground.png`
    （108/162/216/324/432 px，内容位于安全区内，可被系统裁成圆形/方圆/水滴）；
  - 通知栏小图标用单色矢量 `drawable/ic_stat_download.xml`（系统会按主题着色，不用彩色图）。
- 想换成自己的图：替换上述 PNG 即可；尺寸与文件名保持不变最省事。
  也可以改 `tools/generate_icons.py` 里的颜色/几何参数后重新执行：
  ```bash
  python tools/generate_icons.py     # 需要 Pillow
  ```
  该脚本完全离线运行，不下载任何素材；当前图标即由它生成（几何自绘，无第三方素材版权问题）。

## 五、已知限制

- **minSdk 29（Android 10）**：为了统一走 MediaStore 分区存储，暂不支持 Android 9 及以下；如要兼容可再补旧存储分支。
- 公众号页面结构/风控会变化；解析失败时请用「浏览器提取」兜底。
- 图片预览依赖系统解码：GIF 可动；**AVIF 在 Android 12 以下可能无法预览**（仍可下载）。
- 抓取内容的版权归原作者/公众号所有，请仅用于个人存档，遵守微信平台规则；不要用于批量转载或商业分发。
- 当前只有 debug 签名。若要长期使用/上架，请自行生成 release keystore，并改用 release 构建（不要把 keystore 提交到仓库）。

## 六、目录结构

```
app/src/main/java/com/articleimages/downloader/
├── App.kt                     # OkHttp / Coil（微信图带 Referer）
├── MainActivity.kt            # 入口、分享/链接接收、通知权限
├── core/                      # 纯 JVM，可单测
│   ├── Models.kt
│   ├── ArticleUrl.kt          # 链接提取/校验、原图改写、URL 规范化
│   ├── ArticleParser.kt       # Jsoup 解析正文/标题/图片/封面
│   ├── ImageSniffer.kt        # 字节魔数识别
│   └── SafeName.kt            # 文件名安全化
├── data/
│   ├── AppLog.kt              # 日志环形缓冲（日志页数据源）
│   ├── ArticleRepository.kt   # 抓文章、流式下载图片、限额、Cookie
│   ├── DownloadController.kt  # 下载任务单例状态
│   ├── ImageSaver.kt          # MediaStore / SAF 落盘
│   ├── Settings.kt            # 原图开关、自定义目录
│   └── CookieStore.kt
├── service/DownloadService.kt # 前台服务 + 通知 + 取消
├── ui/                        # Compose 界面
│   ├── AppRoot.kt
│   ├── HomeScreen.kt
│   ├── PreviewDialog.kt
│   ├── LogScreen.kt
│   ├── SettingsScreen.kt
│   └── WebExtractScreen.kt
└── vm/HomeViewModel.kt

app/src/main/res/
├── mipmap-*/ic_launcher.png            # 传统图标（48~192px）
├── mipmap-*/ic_launcher_round.png      # 圆形图标
├── mipmap-*/ic_launcher_foreground.png # 自适应图标前景
├── mipmap-anydpi-v26/ic_launcher*.xml  # 自适应图标声明
├── drawable/ic_stat_download.xml       # 通知栏单色小图标
└── values/{strings,colors,themes}.xml

app/src/test/java/com/articleimages/downloader/CoreTest.kt
tools/generate_icons.py                  # 离线生成图标
.github/workflows/build-apk.yml
```

## 七、版本

- `1.0.1`：应用名改为「微信公众号图片下载器」，替换自绘图标（传统/圆形/自适应/通知）。
- `1.0.0`：首个可构建版本（解析、封面、网格多选、前台下载、相册保存、SAF、日志、WebView 兜底）。
  - 应用名：微信公众号图片下载器
  - 图标：自绘绿色渐变 + 图片卡片 + 下载箭头，含自适应/圆形/通知小图标
## 八、常见问题（GitHub 构建）

**1）Actions 里没有 “Build APK”**
说明 `.github/workflows/build-apk.yml` 没上传成功（Windows 资源管理器默认隐藏以点开头的文件夹）。
处理：用 `git push` 上传，或在 GitHub 网页用 “Add file → Create new file”，文件名输入
`.github/workflows/build-apk.yml`，把工作流内容粘贴进去。

**2）构建报 SDK / license 相关错误**
工作流里已执行 `sdkmanager --licenses` 并安装 `platforms;android-35`、`build-tools;35.0.0`；
若偶发网络失败，直接重跑一次（Actions 右上角 “Re-run all jobs”）。

**3）构建报 `gradle: command not found`**
说明 “Install Gradle 8.9” 步骤失败；看该步骤日志里的 `curl` 是否超时，重跑即可。
也可以把 Gradle 安装地址换成你网络可达的镜像。

**4）APK 安装被拦截**
`app-debug.apk` 是 debug 签名，需要在手机上允许“安装未知来源应用”。
若企业设备限制安装，请自行生成 release 签名后再构建。

**5）想要 release 签名**
在本地或 CI 生成 keystore，配置 `signingConfigs`，并把 keystore 与密码放到 GitHub Secrets；
不要把 keystore 提交进仓库。

**6）下载失败 / 提示“没有读到文章正文”**
先确认链接能在手机浏览器打开；若出现验证页，用 App 里的“浏览器提取”，
完成验证后点“重新提取”。

**7）日志怎么给开发者**
日志页勾选相关行（或全选）→“复制选中”，粘贴到 Issues/聊天即可；
也可以 `adb logcat` 抓取。
