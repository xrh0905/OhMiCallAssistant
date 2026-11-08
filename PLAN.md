# OhMiCall 实施计划 v3（已定稿）

目标：在 HyperOS 4 CN 上，用单个 LSPosed 模块把 AI 通话链路切回 HyperOS 3 的旧引擎
`com.xiaomi.aiasst.service`，并熔断 HyperOS 4 的 HyperPhone 配置中心与 Osbot/MiClaw 新引擎。

约束：libxposed **API 102**、**静态 scope**、**Hot Reload**、Java、最小 Hook、可诊断、可回退。

v3 相对 v2：

1. 明确“不 Hook framework”的边界：**不注入 system_server / SystemUI / framework scope**；
   应用进程内的 `PackageManager` 等框架 API Hook 可接受，且优先于 DexKit。
2. 设备框架已确认 API 102；`minApiVersion=102` 与 Hot Reload 按原方案启用。
3. 已生成 release keystore；CI 使用 secrets 签名；tag 规则 `v*` + `GITHUB_RUN_NUMBER` 确认。
4. 工程、7 个 Hook、Hot Reload、CI 已落地并通过 debug/release 构建；仅剩真机验证。

---

## 0. 实现状态

| Phase | 内容 | 状态 |
|---|---|---|
| 0 | 工作空间、计划 | 完成 |
| 1 | Gradle/LSPosed 骨架、元数据、入口路由 | 完成 |
| 2 | 5 个宿主、7 个 Hook | 完成（代码 + 构建） |
| 3 | 静态 scope、API 102 Hot Reload | 完成（代码 + 构建） |
| 4 | GitHub Actions、release 签名 | 完成；CI 绿，`v0.1.0` 已发布 |
| 5 | 真机 V1–V8 | 设备已接入；LSPosed 启用由用户处理 |

构建验证：

```text
:module:assembleDebug   BUILD SUCCESSFUL
:module:assembleRelease BUILD SUCCESSFUL，apksigner verify 通过
```

APK 内容已确认：`META-INF/xposed/module.prop`、`scope.list`、`java_init.list`；
R8 后 `java_init.list` 自动改写为混淆后的入口类名。

---

## 1. 现状与证据

| 项 | 结论 | 来源 |
|---|---|---|
| 远程仓库 | `https://github.com/xrh0905/OhMiCallAssistant`（已创建并推送 `main`） | `gh repo view` |
| 本地目录 | 已 `git init`，首次提交 `4224bee`；`apk/ decomp/` 本地保留、不入库 | `git log` |
| OS4 基线 | HyperPhone 15.0.3 / InCallUI 40.01.58 / Contacts 18.30.00.73 / aiasst 6.2.2 / voiceassist 8.2.65 | `docs/` |
| OS4 APK（已拉取） | `apk/os4/`：HyperPhone、InCallUI、Contacts、MIUIAiasstService、VoiceAssist、TeleService、Telecom + 各 `*.manifest.xml` | 设备 `pm path` / `apkanalyzer` |
| 进程事实 | InCallUI `InCallServiceImpl` 显式 `android:process="com.android.incallui"`；aiasst `InCallAiServiceImpl`、Osbot `OsbotInCallService` 默认主进程；Contacts 存在 `:business` | `apk/os4/*.manifest.xml` |
| 构建环境 | JDK 21、SDK platform 36/37、build-tools 36.0.0、Gradle 8.14.3、AGP 8.12.0、`libxposed:api:102.0.0` 均已缓存 | 本地检查 |
| 设备 | `2936262d`（popsicle / OS4.0.6.0.XPBCNXM / Android 17），LSPosed v2.2.1 (7912)，API 102 | `adb` / `lspctl status` |

---

## 2. 抗混淆策略（v2 核心）

### 2.1 为什么旧方案会失效

v1（沿用 LSPilot）直接 Hook：

- `com.android.contacts` → `k3.l.k(Context)`
- `com.xiaomi.aiasst.service` → `A1.O.e(Context)`

这两个类名/方法名是 R8 产物，随应用版本重新混淆即失效。字符串证据（`findNewAiCallSetting`、
`InCallUi supports ai assist answer`）也可能被重命名或移除。

### 2.2 替代原则：Hook「输入」而不是 Hook「混淆实现」

| 原混淆目标 | 它真正做的事 | 稳定替代点 | 版本稳定性 |
|---|---|---|---|
| Contacts `k3.l.k` | `PackageManager.resolveActivity("miui.intent.action.AiCallSetting")` 是否非空 | 应用进程内 `ApplicationPackageManager.resolveActivity(Intent, int)` / `(Intent, ResolveInfoFlags)` | Android API，跨 HyperOS 版本稳定 |
| aiasst `A1.O.e` | 读三个包的 APK meta：`xiaoai_support_autoanswer`、`support_ai_call`、`incall_ai_assist_answer` | 应用进程内 `ApplicationPackageManager.getApplicationInfo(String, int)`，只把 `com.android.incallui` 的 `incall_ai_assist_answer` 改 0 | Android API，跨 HyperOS 版本稳定 |

这样：

- 目标应用更新、类名重排、方法重命名都不影响 Hook 命中；
- 不需要 DexKit、不需要运行时扫 DEX、不需要适配混淆映射；
- Hook 语义仍是最小范围：只改这两个 `PackageManager` 调用的结果。

**边界**：以上 `PackageManager` Hook 只安装在 `com.android.contacts`、
`com.xiaomi.aiasst.service` 自己的进程内；scope 不含 `system`、SystemUI、framework。
不修改系统服务、不修改全局包管理状态。

### 2.2.1 为什么不用 DexKit

DexKit 可用，但它解决的是“定位混淆类/方法”。这里已经改用更稳定的输入层
（`PackageManager` 调用），不需要定位 `k3.l` / `A1.O`。引入 DexKit 只会增加：

- 依赖与 APK 体积；
- 首次 DEX 扫描耗时；
- 对混淆字符串的二次依赖。

因此 v3 不引入 DexKit；若未来出现无法用稳定 API 覆盖的混淆点，再作为第三层方案。

### 2.3 分层选择

| 层 | 用于 | 例子 |
|---|---|---|
| A. 应用进程内 Android API（首选，抗混淆） | 混淆 helper 的输入 | `resolveActivity`、`getApplicationInfo` |
| B. 稳定命名应用类 | 未混淆、语义明确的决策点 | `AiAssistantUtils`、`Utils`、`AiCallConfigProvider`、`OsbotInCallService` |
| C. 混淆名（不使用） | 仅当 A/B 都不可能时 | `k3.l`、`A1.O` |

层 B 的类在当前 APK 中均为可读名称，且是应用公开语义点；仍记录版本矩阵，
缺失时 `class_not_found` 后 no-op，不崩溃。

### 2.4 不修改系统框架

- scope 固定为 5 个应用包，不包含 `system`、`com.android.systemui`。
- 不使用 `deoptimize` 全局方法、不修改 `PackageManagerService`、不写系统设置。
- 所有框架 API Hook 都只作用于被注入应用进程内的对象。

---

## 3. 模块工程

### 3.1 `META-INF/xposed/module.prop`

```properties
minApiVersion=102
targetApiVersion=102
staticScope=true
exceptionMode=protective
autoHotReload=true
```

### 3.2 `META-INF/xposed/scope.list`

```text
com.xiaomi.phone
com.android.incallui
com.android.contacts
com.xiaomi.aiasst.service
com.miui.voiceassist
```

- 保留 `com.xiaomi.phone`：配置熔断 Hook 落在这里（见 H1）。
- 不含 `system` / SystemUI / framework。

### 3.3 `META-INF/xposed/java_init.list`

```text
io.github.xrh0905.ohmicall.ModuleMain
```

恰好一个 Java 入口（Hot Reload 硬性要求）。

### 3.4 AndroidManifest

- 包名 `io.github.xrh0905.ohmicall`，label `OhMiCall`，无 Activity/Service/Receiver/权限。
- `android:description` 说明“HyperOS 4 AI 通话回退到旧引擎”。
- 不使用旧 `xposedmodule` metadata。

### 3.5 源码结构

```text
module/src/main/java/io/github/xrh0905/ohmicall/
├── ModuleMain.java            # 生命周期、包/进程路由、Hot Reload
├── HookTable.java             # id -> Hooker 注册表
├── hook/PhoneHooks.java
├── hook/InCallUiHooks.java
├── hook/ContactsHooks.java
├── hook/AiasstHooks.java
└── hook/OsbotHooks.java
```

---

## 4. Hook 清单 v2

| ID | 宿主 | 目标（v2 稳定点） | 修改 | 对应旧方案 |
|---|---|---|---|---|
| `phone.config_query` | `com.xiaomi.phone` | `com.xiaomi.phone.aicall.AiCallConfigProvider#query(Uri, String[], String, String[], String)` | `ai-assist-call-config/settings/{key}` 且 key ∈ 熔断集合 → 空 `MatrixCursor` | 同 v1 |
| `incallui.support_new_engine` | `com.android.incallui` | `com.android.incallui.aiassistant.AiAssistantUtils#supportXiaoAiAutoAnswer(Context)` | → `false` | 同 v1 |
| `incallui.auto_answer_enabled` | `com.android.incallui` | `com.android.incallui.util.Utils#aiCallAutoAnswerEnabled()` | → `false` | 同 v1 |
| `incallui.trigger_answer_enabled` | `com.android.incallui` | `com.android.incallui.util.Utils#aiCallAutoTraggerAnswerEnabled()`（原拼写） | → `false` | 同 v1 |
| `contacts.resolve_activity` | `com.android.contacts` | `android.app.ApplicationPackageManager#resolveActivity(Intent, int)`、`#resolveActivity(Intent, ResolveInfoFlags)` | 命中 `miui.intent.action.AiCallSetting` 或组件 `com.xiaomi.phone/.settings.AiCallDetailActivity` → `null` | **替换** `k3.l.k` |
| `aiasst.app_info_meta` | `com.xiaomi.aiasst.service` | `android.app.ApplicationPackageManager#getApplicationInfo(String, int)` | 包名 `com.android.incallui` → 返回克隆 `ApplicationInfo`，`metaData.incall_ai_assist_answer = 0` | **替换** `A1.O.e` |
| `osbot.skip_call_added` | `com.miui.voiceassist` | `com.aios.osbot.tools.phone.service.OsbotInCallService#onCallAdded(Call)` | 不 `proceed`，直接返回 | 新增（LSPilot 缺口） |

### 4.1 `contacts.resolve_activity` 细节

- 证据：
  - `decomp/os4_contacts/sources/k3/l.java:322-330`：`k()` → `resolveActivity(r(), 65536)`；
  - `k3/l.java:384`：`r()` = `new Intent("miui.intent.action.AiCallSetting")`；
  - `k3/l.java:347-353`：`j()` → `resolveActivity(q(), 65536)`；
  - `k3/l.java:377-380`：`q()` = 显式组件 `com.xiaomi.phone/com.xiaomi.phone.settings.AiCallDetailActivity`；
  - `DialerMenuDialog.java:280/412/422` 与 `DialerOverflowMenuHelper.java:125/193` 由 `k3.l.C`/`j` 决定入口。
- 过滤两种 Intent：action、显式组件；其余 `proceed()` 原样。
- 不拦截 `n()`（旧 `aicalllog_detail`），旧记录入口保持可用。
- `k3.l.H`（旧引擎 `GET_AICALL_AVAILABLE`）不改：回退旧引擎后 AI 可用性应为 true。

### 4.2 `aiasst.app_info_meta` 细节

- 证据：
  - `decomp/os4_aiasst/sources/A1/O.java:44-57`：`e = g && c && f`；
  - `A1/O.java:60-67`：`f` 读 `com.android.incallui` 的 `incall_ai_assist_answer >= 1`；
  - `InCallAiServiceImpl.java:236-257`：`onBind` / `onCallAdded` 在 `O.e(this)` 为 true 时直接返回。
- 只把 `incall_ai_assist_answer` 置 0（最小改动），`g`/`c` 不动。
- 返回克隆对象，避免修改 `PackageManager` 内部共享缓存：
  `new ApplicationInfo(orig)` + `new Bundle(orig.metaData)`。
- 仅在包名 `com.android.incallui` 时生效；其余包名直接 `proceed()`。

### 4.3 `osbot.skip_call_added` 细节

- 证据：`OsbotInCallService.java:7133-7160`；自动接听/**trigger observer**/协程均从 `onCallAdded` 进入。
- 整段短路：不注册 callback、不注册 observer、不启动 `C01941` 协程、不执行 `setupAutoAnswer`。
- 不 Hook Kotlin suspend `setupAutoAnswer`；不 Hook `onStartCommand`（仅 super）。
- 副作用：新引擎的 TTS/摘要/骚扰拦截在该服务内一并停用；这是“回退旧引擎”的预期代价。

### 4.4 配置熔断集合与返回

```text
ai_call_auto_answer_enabled
ai_call_auto_answer_enabled_sim2
ai_call_trigger_answer
```

- 返回空 `MatrixCursor`：`projection != null ? projection : new String[]{"value"}`，0 行。
- 不阻断 `ai_call_auto_answer_delay(_sim2)`（已确认）。
- 不写数据库、不改设置页展示。

---

## 5. 生命周期与路由

| 回调 | 行为 |
|---|---|
| `onModuleLoaded` | 记录进程名、`getApiVersion()`、框架名/版本；不做目标类查找 |
| `onPackageReady` | 用 `param.getClassLoader()`；按 `packageName + processName` 路由；每宿主只装一次 |
| `onHotReloading` | `setSavedInstanceState(processName)`；返回 `true`；无线程/native |
| `onHotReloaded` | 旧 `HookHandle` 按 `getId()` 查 `HookTable`，`replaceHook()`；未知 id `unhook()` |

- 包名/进程名双重过滤：`processName == packageName` 或 `processName` 以 `packageName + ":"` 开头
  （覆盖 Telecom 绑定服务可能所在的 `:xxx` 子进程）。
- 单 Hook 失败只记录，不影响其它宿主。
- 所有日志前缀 `OhMiCall`，事件码：`module_loaded`、`route`、`hook_installed`、`hook_hit`、
  `class_not_found`、`method_not_found`、`hot_reloaded`。

---

## 6. Hot Reload 的 API / 框架 / SDK 要求

### 6.1 结论

| 项 | 要求 |
|---|---|
| libxposed API | **102**（`onHotReloading`/`onHotReloaded`/`replaceHook` 均 `@SinceApi(API_102)`） |
| `module.prop` | `minApiVersion=102`、`targetApiVersion=102`、`autoHotReload=true` |
| Java 入口 | **恰好 1 个**；0 个或多个入口不支持热重载 |
| 框架 | 必须实现 libxposed API 102；API 101-only 的框架无法加载 `minApiVersion=102` 的模块 |
| Android SDK | libxposed 整体 `minSdk 26`；热重载本身不额外要求 Android API 级别；`onPackageLoaded` 才要求 API 29+（本模块不用） |
| 触发 | 模块 APK 覆盖安装且目标进程仍存活；或模块 App 通过 `libxposed/service` 手动触发（本模块无 UI，不引入） |
| 限制 | 不重放 `onModuleLoaded`/`onPackageReady`；只支持已安装 Hook 的原子替换；框架可返回 `UNSUPPORTED` |

### 6.2 框架版本

- 目标设备框架已确认 **API 102**，因此 `minApiVersion=102` 与 `autoHotReload=true` 可用。
- 参考基线：LSPosed 2.2.x 级别构建（社区参考 2.2.0-it，API 102，version code 7866）；
  Vector 2.2+ 同样支持 API 102 热重载。
- 本模块在 `onModuleLoaded` 打印 `api=... framework=... version=...`；
  若 `getApiVersion() < 102`，记录 `unsupported_api`，Hook 仍安装，但不要期望热重载。
- 官方 LSPosed GitHub Releases 停留在 1.9.2，2.x 通过其发行渠道分发。

### 6.3 热重载实现约束

- `onHotReloaded` 只使用 `HookHandle` + 新代 `HookTable`，不跨代保存目标 `ClassLoader`、目标对象、
  旧代静态集合。
- Hook 全部带稳定 ID，可被 `replaceHook` 原子替换。
- 热重载前未安装 Hook 的宿主，新代不会补装；日志提示“强停该宿主”。

---

## 7. 构建与签名

| 项 | 值 |
|---|---|
| Gradle | 8.14.3（wrapper） |
| AGP | 8.12.0 |
| compileSdk / targetSdk | 36 / 36 |
| minSdk | 26 |
| Java | 17 |
| 依赖 | `compileOnly("io.github.libxposed:api:102.0.0")` |
| 打包 | `packaging.resources.merges += "META-INF/xposed/*"` |

R8：

```proguard
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
```

签名（已生成）：

```text
signing/ohmicall-release.jks      RSA 4096 / 10000 天 / alias=ohmicall
signing/keystore.properties       本地构建使用
signing/keystore.base64           用于 GitHub Secret
```

- debug：默认 debug keystore，用于本地/PR 验证。
- release：本地读取 `signing/keystore.properties`；CI 读取环境变量 secrets。
  `signing/` 已忽略，不入库。详见 `docs/SIGNING.md`。
- `versionCode` 默认 1；CI 取 `GITHUB_RUN_NUMBER`；`versionName` 来自 tag 或 `0.1.0`。

---

## 8. GitHub Actions（已实现）

`.github/workflows/build.yml`：

| Job | 触发 | 内容 |
|---|---|---|
| `debug` | push / PR / 手动 | JDK 21 + Android SDK + Gradle cache；`assembleDebug`；校验 `META-INF/xposed/*`；上传 `OhMiCall-debug` artifact |
| `release` | `v*` tag | base64 解码 keystore；`assembleRelease`；`apksigner verify --print-certs`；创建 GitHub Release |

- secrets：`ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、
  `ANDROID_KEY_PASSWORD`；值由 `scripts/export-gh-secrets.ps1` 输出。
- `versionCode=GITHUB_RUN_NUMBER`，`versionName=tag 去掉 v`。
- CI 只做构建与 APK 校验；V1–V8 行为验证需要真机。

---

## 9. 验证矩阵

### 9.1 本地/CI

```text
gradlew.bat :module:assembleDebug
unzip -l module-debug.apk | findstr META-INF/xposed
```

必须包含 `module.prop`、`java_init.list`、`scope.list`；`aapt2 dump badging` 检查 label/minSdk。

### 9.2 真机

| # | 场景 | 期望 |
|---|---|---|
| V1 | 来电点 AI | 旧 `AiModeDialog`/旧 CallScreen；无 `AiAssistCallFragment` |
| V2 | `Settings.Global com.xiaomi.aiasst.call.autoAnswer=1` + 来电 | 旧链路 `incall_ring` → 旧自动接听 |
| V3 | Osbot | 无新引擎接管/TTS/自动接听 |
| V4 | 拨号/联系人 AI 入口 | 回退旧记录/设置；`resolve_activity` 日志命中 |
| V5 | AI 通话中第二路来电 | 不被 `AiAssistCallScreeningService` 拒绝 |
| V6 | HyperPhone 设置页 | 可打开；开关不驱动新引擎 |
| V7 | 模块覆盖安装 | `hot_reloaded replaced=...`；Hook 继续命中 |
| V8 | 日志 | 每条 Hook 至少一次 `hook_hit` |

回退：停用模块 + 强停 5 个宿主；不改 APK/DB/设置。

---

## 10. 风险

| 风险 | 缓解 |
|---|---|
| `ApplicationPackageManager` 为隐藏类，OEM 若替换实现 | 运行时 `Class.forName` + 方法查找；缺失日志；Hook 失败 no-op |
| 框架 Hook 面较宽 | 严格过滤包名/Intent；其余 `proceed()`；不修改共享 `ApplicationInfo` |
| InCallUI `Utils`/`AiAssistantUtils` 未来改名 | 属稳定命名点；缺失日志；可选退回框架 `getApplicationInfo` 方案 |
| `OsbotInCallService` 改名 | 已是最小侵入点；缺失时新引擎仍被 InCallUI 入口与配置熔断削弱 |
| 配置 `query` 空游标 | 标准空结果；观察 HyperPhone 设置页与日志 |
| ART inline | 先看 `hook_hit`；必要时 `deoptimize` 或改决策点 |
| 热重载框架不支持 | 日志 `unsupported_api`；Hook 仍可用，需重启进程更新代码 |
| 与 LSPilot 旧插件共存 | README 标注互斥 |

---

## 11. 阶段

| Phase | 内容 | 验收 |
|---|---|---|
| 0 | 工作空间、计划 | 完成 |
| 1 | Gradle 骨架、元数据、入口路由 | 完成；`assembleDebug` 成功 |
| 2 | 7 个 Hook（含 2 个应用进程内框架 seam） | 完成；构建通过，待真机 V1–V6 |
| 3 | Hot Reload | 完成；待真机 V7 |
| 4 | GitHub Actions + release 签名 | 完成；本地/CI release 均验签通过 |
| 5 | 真机 V1–V8 | 设备已接入；LSPosed 启用由用户处理 |

---

## 12. 决策记录

| 问题 | 结论 |
|---|---|
| 框架 API 版本 | 设备已确认 API 102；`minApiVersion=102`、Hot Reload 启用 |
| 混淆点兜底 | 不使用 `k3.l.k` / `A1.O.e` 硬编码；应用进程内 `PackageManager` seam；不做 DexKit |
| 配置返回值 | 空 `MatrixCursor` |
| Osbot | `onCallAdded` 整段短路 |
| delay 键 | 不阻断 |
| HyperOS 3 | 纯 no-op |
| 应用名 | `OhMiCall` |
| 设置页 | 不做 |
| 签名 | 已生成 keystore；GitHub Actions secrets 签名 |
| tag | `v*` + `GITHUB_RUN_NUMBER` 作为 versionCode |
| 仓库 | `xrh0905/OhMiCallAssistant`，已推送 `main` |

剩余唯一未完成项：真机功能验证。设备 `2936262d` 已接入并确认 LSPosed v2.2.1 (7912)，
OS4 APK 与 Manifest 已拉取；模块启用/scope 由用户处理。
