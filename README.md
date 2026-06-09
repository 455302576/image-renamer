# 批量图片改名 Android App

这是一个最小 Android 原生项目，用来批量直接修改图片文件名。

文件名格式：

```text
20260609_160930.jpg
```

时间来源是 Android 文件提供器返回的最后修改时间。如果系统没有返回最后修改时间，会回退到当前时间。

## 云端构建 APK

1. 在 GitHub 新建一个仓库。
2. 上传本目录里的所有文件。
3. 打开仓库的 `Actions` 页面。
4. 运行 `Build APK` 工作流，或推送到 `main/master` 后等待自动运行。
5. 在工作流结果的 `Artifacts` 里下载 `batch-image-renamer-debug-apk`。
6. 解压后把 `app-debug.apk` 发到手机安装。

## 使用方式

1. 打开 App。
2. 点击 `选择图片并改名`。
3. 在系统文件选择器里选择一张或多张图片。
4. 确认预览后执行改名。

这个 App 会尝试调用 Android 系统的 `DocumentsContract.renameDocument(...)` 直接重命名原文件。是否成功取决于你选择图片时对应文件提供器是否授予写入/改名权限。
