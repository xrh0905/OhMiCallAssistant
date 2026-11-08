# OhMiCall

在 HyperOS 4（CN，Android 17）上把 AI 通话链路切回 HyperOS 3 的旧引擎
`com.xiaomi.aiasst.service`，并熔断 HyperOS 4 新增的配置中心 / Osbot(MiClaw) 新引擎。

实现形式：单个 LSPosed 模块 `OhMiCall`（libxposed **API 102**、**静态 scope**、**Hot Reload**）。

远程仓库：`https://github.com/xrh0905/OhMiCallAssistant`

## 状态

| 阶段 | 内容 | 状态 |
|---|---|---|
| 0 | 工作空间、计划 `PLAN.md` | 完成 |
| 1 | Gradle/LSPosed 工程骨架 | 完成，debug/release 构建通过 |
| 2 | 5 个宿主包、7 个 Hook | 完成，待真机验证 |
| 3 | 静态 scope + API 102 Hot Reload | 完成，待真机验证 |
| 4 | GitHub Actions、release 签名 | 完成，CI 通过，`v0.1.0` 已发布 |
| 5 | 真机功能验证 V1–V8 | 待设备接入 |

## 目录

| 路径 | 角色 | 是否入库 |
|---|---|---|
| `module/` | LSPosed 模块源码、`META-INF/xposed`、资源 | 入库 |
| `docs/` | 逆向结论、签名说明 | 入库 |
| `plugins/` | 旧 LSPilot(BeanShell) 插件，作为 Hook 语义参考 | 入库（log/error 忽略） |
| `apk/` | OS3/OS4 基线 APK；`apk/os4/` 为设备实拉 + Manifest，清单见 `docs/OS4_ASSETS.md` | 本地保留，忽略 |
| `decomp/` | jadx 反编译源码（证据来源） | 本地保留，忽略 |
| `signing/` | release keystore 与密码 | 本地保留，忽略 |
| `PLAN.md` | Hook 清单、构建、验证、风险 | 入库 |

## 构建

```powershell
.\gradlew.bat :module:assembleDebug
.\gradlew.bat :module:assembleRelease
```

产物：

```text
module/build/outputs/apk/debug/module-debug.apk
module/build/outputs/apk/release/module-release.apk
```

release 使用 `signing/keystore.properties`（见 `docs/SIGNING.md`）；CI 使用同名 secrets。

## 安装与验证

1. 安装 APK，在 LSPosed 中启用模块。scope 由 `META-INF/xposed/scope.list` 固定：
   `com.xiaomi.phone`、`com.android.incallui`、`com.android.contacts`、
   `com.xiaomi.aiasst.service`、`com.miui.voiceassist`。
2. **先确认没有响铃/通话**，再重启设备，或运行 `pwsh scripts/restart-hosts.ps1`
   （脚本会检测 `dumpsys telecom`，通话中拒绝执行）。不要在响铃中强停宿主。
3. LSPosed 日志过滤 `OhMiCall`，期望：
   `module_loaded` → `route` → `hook_installed` → `hook_hit`。
4. 功能验证 V1–V8 见 `PLAN.md` §9。

## Hook 一览

| 宿主 | 目标 | 结果 |
|---|---|---|
| `com.xiaomi.phone` | `AiCallConfigProvider.query` | 熔断 3 个新引擎配置 key，返回空 `MatrixCursor` |
| `com.android.incallui` | `AiAssistantUtils.supportXiaoAiAutoAnswer` | `false`，入口回退旧引擎 |
| `com.android.incallui` | `Utils.aiCallAutoAnswerEnabled` / `aiCallAutoTraggerAnswerEnabled` | `false` |
| `com.android.contacts` | `ApplicationPackageManager.resolveActivity` | 拦截 HyperPhone 新设置/详情 Intent |
| `com.xiaomi.aiasst.service` | `ApplicationPackageManager.getApplicationInfo` | 对 `com.android.incallui` 伪造 `incall_ai_assist_answer=0` |
| `com.miui.voiceassist` | `OsbotInCallService.onCallAdded` | 整段短路，新引擎不接管 |

## 使用边界

- 仅面向 HyperOS 4 CN（上述版本）设计；类/方法缺失时记录日志并跳过，不崩溃。
- scope 不含 `system` / SystemUI / framework；只注入 5 个应用进程。
  Contacts/aiasst 的框架 API Hook 也只发生在各自应用进程内。
- 不改 APK、不改数据库、不做系统属性伪装。
- 与 LSPilot 的同名插件互斥，不要同时启用。
