# 签名与 CI

## 本地签名材料（已生成，未入库）

```text
signing/
├── ohmicall-release.jks      # RSA 4096 / 10000 天 / alias=ohmicall
├── keystore.properties       # KEYSTORE_PATH / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD
└── keystore.base64           # jks 的 base64，用于 GitHub Secret
```

`signing/` 已在 `.gitignore` 中。不要提交、不要在日志中输出密码。

本地 `:module:assembleRelease` 会自动读取 `signing/keystore.properties` 并签名；
没有该文件时 release 不签名。

## GitHub Actions Secrets

在仓库 Settings → Secrets and variables → Actions 新建：

| Secret | 值 |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `signing/keystore.base64` 的内容 |
| `ANDROID_KEYSTORE_PASSWORD` | `keystore.properties` 的 `KEYSTORE_PASSWORD` |
| `ANDROID_KEY_ALIAS` | `ohmicall` |
| `ANDROID_KEY_PASSWORD` | `keystore.properties` 的 `KEY_PASSWORD` |

生成输出（本地运行）：

```powershell
pwsh scripts/export-gh-secrets.ps1
```

## 触发规则

- `push` / `pull_request` / 手动：构建 debug APK，校验 `META-INF/xposed/*`，上传 artifact。
- `v*` tag：用 secrets 构建并签名 release APK，`apksigner verify` 后创建 GitHub Release。

`versionCode` 取 `GITHUB_RUN_NUMBER`，`versionName` 取 tag 去掉 `v` 前缀；本地默认 `0.1.0`。

## 轮换

丢失 keystore 或密码后无法用同一签名更新模块；重新生成会要求先卸载旧模块。
