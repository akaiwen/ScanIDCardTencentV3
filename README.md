# ScanIDCardTencentV3（身份证识别实验：腾讯云 OCR + V3(TC3)签名）

本项目严格按你提供的 `ScanIDCard.pptx` 的流程实现：  
**拍照 → 保存图片 → Base64 → V3(TC3-HMAC-SHA256)签名 → POST 请求 → Handler 解析 JSON**。

## 1. 环境要求
- Android Studio（建议 Jellyfish / Koala 及以上）
- JDK 17（Android Studio 自带即可）
- 真机或模拟器（需要相机；模拟器请使用 Virtual Scene / 上传图片功能）

## 2. 配置腾讯云密钥（必须）
本项目不会内置你的 SecretId/SecretKey（安全原因）。你需要自己填入。

### 方式 A（推荐）：local.properties
1) 把根目录的 `local.properties.example` 复制一份为 `local.properties`  
2) 修改以下两行：

```properties
TENCENT_SECRET_ID=你的SecretId
TENCENT_SECRET_KEY=你的SecretKey
```

> 注意：`local.properties` 不要提交到 Git。

### 方式 B：gradle.properties
在根目录 `gradle.properties` 里填入同名字段也可以（不推荐，容易泄露）。

## 3. 运行步骤（从 0 到 1）
0) 打开 Android Studio → **Open** 选择本项目根目录  
1) 等待 Gradle Sync 完成（会自动下载 Gradle 8.2）  
2) 连接真机/启动模拟器 → Run  
3) 点击【拍摄正面/拍摄反面】拍照  
4) 点击【开始识别】→ 下方显示结果

## 4. 你在 PPT 里要求的关键点对应到代码的位置
- **点击事件**：`MainActivity.setupClicks()`
- **保存拍摄图片**：`createImageFile()` + `TakePicture()`（FileProvider）
- **图片 Base64**：`imageFileToBase64()`
- **V3 签名封装**：`TencentV3Signer.buildAuthorization()`
- **POST 请求**：`TencentOcrClient.idCardOcr()`
- **Handler 解析 JSON**：`uiHandler.handleMessage()` + `prettyResultFromResponse()`

## 5. 常见错误排查
- `HTTP 401` / `AuthFailure.SignatureFailure`：密钥错误、时间戳错误、签名串不一致
- `RequestLimitExceeded`：触发频控，稍等重试
- `ResourceNotFound`：Action/Version 写错（本项目使用 `IDCardOCR` + `2018-11-19`）

祝你实验顺利！
