# musicNFCwriter

应用名：**musicNFCwriter**。应用包名：`org.musicnfc.writer`。

Kotlin Android NFC URI 写入器。支持从 QQ 音乐、网易云音乐、酷狗音乐、汽水音乐的分享链接提取各自的歌曲参数，生成指定 Scheme，并写入或清除 NDEF 标签内容。

## 构建

- 使用 Android Studio 打开此目录，或在 Windows 上运行 `gradlew.bat assembleDebug`。
- 安装 Android SDK Platform 35 和 JDK 17。
- 等待 Gradle 同步，然后运行 `app` 配置或生成 APK。

## 使用

1. 选择音乐平台并粘贴单曲分享链接。
2. 点击“解析链接并生成 URI”。分享链接没有直接包含平台 ID 时，应用会在后台跟随 HTTP 跳转查找 ID；不会猜测或替换成其他平台的 ID。
3. 核对提取的参数及生成 URI，点击“写入歌曲 URI”，按提示将 NFC 标签贴近手机背面。
4. 删除操作会写入 NDEF 空记录清除标签内容；不会修改 UID、Mifare Classic 扇区数据或密钥。

生成的自定义 URI 按用户提供的协议模板生成。Android 只能把 NDEF URI 交给已注册对应 Scheme 的应用；具体跳转和自动播放行为由音乐 App 版本、系统设置和账号状态决定。某些 Mifare Classic/CUID 标签即使能被识别，也可能因密钥或手机兼容性而拒绝写入。

## 平台参数

- QQ 音乐：`songmid`；JSON 查询参数进行 UTF-8 百分号编码。
- 网易云音乐：`id` / 歌曲路径 ID。
- 酷狗音乐：分享链接中的 `chain`，不使用普通歌曲 ID。
- 汽水音乐：`track_id`。
