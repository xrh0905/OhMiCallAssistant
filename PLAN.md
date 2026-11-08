# OhMiCallAssistant 实施计划（待审阅）

目标：在 HyperOS 4 CN 上，用单个 LSPosed 模块把 AI 通话链路切回 HyperOS 3 的旧引擎
`com.xiaomi.aiasst.service`，并熔断 HyperOS 4 新增的 HyperPhone 配置中心与 Osbot/MiClaw 新引擎。

约束：libxposed **API 102**、**静态 scope**、**Hot Reload**、Java、最小 Hook、可诊断、可回退。

---

## 1. 现状与证据

| 项 | 结论 | 来源 |
|---|---|---|
| 远程仓库 | `https://github.com/xrh0905/OhMiCallAssistant` → HTTP 404 | 本次探测 |
| 本地目录 | 非 git 仓库，仅 `apk/ decomp/ docs/ plugins/` | `git status` |
| OS4 版本基线 | HyperPhone 15.0.3 / InCallUI 40.01.58 / Contacts 18.30.00.73 / aiasst 6.2.2 / voiceassist 8.2.65 | `docs/AI_CALL_ASSISTANT_HYPEROS3_VS_HYPEROS4.md` |
| 旧参考实现 | 4 个已部署 LSPilot BeanShell 插件 + 1 个未部署 Osbot 插件 | `plugins/`、docs §6 |
| 构建环境 | JDK 21、SDK `D:\AndroidSDK`（platform 36/37、build-tools 36.0.0）、Gradle 8.14.3 与 AGP 8.12.0 已缓存、`io.github.libxposed:api:102.0.0` 已缓存 | 本次检查 |
| 设备 | `adb devices` 为空，当前无法真机验证 | 本次检查 |

**核心判断**：OS4 不是新增功能，而是「配置中心 + 双引擎 + 三 meta 契约」。只要同时满足：

1. InCallUI 认为新引擎不可用 → 走旧 `AiAssistantHelper.enterAi()`；
2. aiasst 的三 meta 门禁为 false → 旧 `InCallService` 正常绑定；
3. Contacts 的新设置入口判断为 false → 回退旧记录/设置入口；
4. HyperPhone 配置查询读不到 `enabled/trigger` → 新引擎无配置驱动；
5. Osbot 的 `onCallAdded` 不接管 → 新引擎不参与通话；

AI 通话（手动入口、自动接听、第二路策略、记录详情）就回到 OS3 行为。

---

## 2. 工作空间整理

### 2.1 本次完成

```text
OhMiCallAssistant/
├── .gitignore          # 忽略 apk/、decomp/、build/、LSPilot 日志
├── README.md           # 工作空间地图、阶段状态、证据基线
├── PLAN.md             # 本文件
├── docs/               # 既有逆向结论（入库）
├── plugins/            # 既有 LSPilot 插件（入库；log/error 忽略）
├── apk/                # 基线 APK（本地保留，不入库）
└── decomp/             # jadx 源码（本地保留，不入库）
```

- `git init`，首次提交（如本机 git user 已配置）。
- **不移动** `apk/ decomp/ docs/ plugins/`：两份文档大量引用这些相对路径，移动会破坏证据链。
- 不删除任何既有文件；`plugins/` 作为 Hook 语义与哈希证据保留。

### 2.2 Phase 1 目标结构（待批准后创建）

```text
OhMiCallAssistant/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat / gradle/wrapper/
└── module/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/io/github/xrh0905/ohmicall/
        │   ├── ModuleMain.java
        │   ├── HookRegistry.java
        │   ├── hook/PhoneHooks.java
        │   ├── hook/InCallUiHooks.java
        │   ├── hook/ContactsHooks.java
        │   ├── hook/AiasstHooks.java
        │   └── hook/OsbotHooks.java
        └── resources/
            ├── META-INF/xposed/java_init.list
            ├── META-INF/xposed/module.prop
            ├── META-INF/xposed/scope.list
            └── res/values/strings.xml
```

---

## 3. 模块配置

### 3.1 `META-INF/xposed/module.prop`

```properties
minApiVersion=102
targetApiVersion=102
staticScope=true
exceptionMode=protective
autoHotReload=true
```

- `staticScope=true`：scope 由 `scope.list` 固定，用户无需/不能手动勾选（满足「Static Scope」）。
- `autoHotReload=true`：模块 App 更新时框架尝试热重载；仍由旧代 `onHotReloading()` 放行。
- `exceptionMode=protective`：与 Hook 侧 `ExceptionMode.DEFAULT` 配合，Hook 异常不炸宿主。

### 3.2 `META-INF/xposed/scope.list`

```text
com.xiaomi.phone
com.android.incallui
com.android.contacts
com.xiaomi.aiasst.service
com.miui.voiceassist
```

仅这 5 个宿主，不含 `system`、`com.android.systemui`、framework。`com.miui.voiceassist` 体积大，
但新引擎服务在该包内，无法省略。

### 3.3 `META-INF/xposed/java_init.list`

```text
io.github.xrh0905.ohmicall.ModuleMain
```

恰好一个 Java 入口类（Hot Reload 的硬性要求）。

### 3.4 AndroidManifest

- 无 Activity/Service/Receiver，无运行时权限。
- `android:label` / `android:description` 使用标准资源，不使用旧 `xposedmodule` metadata。
- 包名 `io.github.xrh0905.ohmicall`；versionName `0.1.0`。

---

## 4. 入口与路由

`ModuleMain extends io.github.libxposed.api.XposedModule`：

| 回调 | 行为 |
|---|---|
| `onModuleLoaded` | 记录进程名、API/框架版本；不查找目标类 |
| `onPackageReady` | 按 `packageName + processName` 路由；用 `param.getClassLoader()` 安装 Hook；每个宿主只装一次 |
| `onHotReloading` | `setSavedInstanceState(processName)`；无线程/无 native，返回 `true` |
| `onHotReloaded` | 用旧 `HookHandle.getId()` 查新代 Hooker，`replaceHook()`；未知 id `unhook()` |

路由规则：

```text
packageName 命中 5 个宿主之一
  → processName 记录并打日志（默认要求 == packageName；若真机日志显示目标类在 :xxx 进程，
    再按证据扩展，不做“全进程盲装”）
  → 反射解析目标类/方法
  → 命中则安装，缺失则 event=class_not_found / method_not_found 并继续
```

每个 Hook 独立 try/catch，单个失败不影响其它宿主。

---

## 5. Hook 清单

| ID | 宿主 | 目标 | 修改 | 目的 |
|---|---|---|---|---|
| `phone.config_query` | `com.xiaomi.phone` | `com.xiaomi.phone.aicall.AiCallConfigProvider#query(Uri, String[], String, String[], String)` | `ai-assist-call-config/settings/{key}` 且 key ∈ 熔断集合 → 返回空结果 | 新引擎读不到 `true` |
| `incallui.support_new_engine` | `com.android.incallui` | `com.android.incallui.aiassistant.AiAssistantUtils#supportXiaoAiAutoAnswer(Context)` | → `false` | 入口回退旧引擎 |
| `incallui.auto_answer_enabled` | `com.android.incallui` | `com.android.incallui.util.Utils#aiCallAutoAnswerEnabled()` | → `false` | 关闭新引擎 UI 自动接听分支 |
| `incallui.trigger_answer_enabled` | `com.android.incallui` | `com.android.incallui.util.Utils#aiCallAutoTraggerAnswerEnabled()` | → `false` | 关闭 trigger/第二路拒接/通知接管分支 |
| `contacts.new_setting_entry` | `com.android.contacts` | `k3.l#k(Context)`（`private static boolean`） | → `false` | 联系人/拨号回退旧入口 |
| `aiasst.new_engine_gate` | `com.xiaomi.aiasst.service` | `A1.O#e(Context)`（`public static boolean`） | → `false` | 旧引擎恢复 `onBind/onCallAdded` |
| `osbot.skip_call_added` | `com.miui.voiceassist` | `com.aios.osbot.tools.phone.service.OsbotInCallService#onCallAdded(Call)` | 不 `proceed`，直接返回 | 新引擎不接管通话、不自动接听 |

### 5.1 熔断 key 集合（`phone.config_query`）

```text
ai_call_auto_answer_enabled
ai_call_auto_answer_enabled_sim2
ai_call_trigger_answer
```

返回值策略：

- **推荐**：返回 `new MatrixCursor(projection != null ? projection : new String[]{"value"}, 0)`，
  即“key 不存在”的空游标；对 `query` 的所有标准消费者都合法，避免 null 解引用。
- 备选：返回 `null`（LSPilot 已在真机验证：voice `AiCallConfigHelper.getConfig` 与 InCallUI 均判空回落默认值）。
- 不写数据库、不改 HyperPhone 设置页展示，只影响读取口径。

### 5.2 不 Hook 的点及理由

| 点位 | 决定 | 理由 |
|---|---|---|
| `k3.l#H(Context)`（`GET_AICALL_AVAILABLE`） | 不改 | 这是旧引擎可用性；回退旧引擎时应保持可用，否则 AI 入口整体消失 |
| `com.xiaomi.phone.settings.AiCallStatusProvider#query(check)` | 不改 | 只影响新设置页能力判断，不影响行为；减少副作用 |
| `OsbotInCallService#setupAutoAnswer` | 不直接 Hook | Kotlin suspend 方法，拦截复杂度与风险高；`onCallAdded` 短路已覆盖其唯一调用路径 |
| `OsbotInCallService#onStartCommand` | 不改 | 仅 `super` 转发，无业务 |
| `InCallUI.CallCardFragment#onAiCallClick` | 不改 | 上游 flag 已决定分支，优先最小 Hook |
| `aiasst.InCallAiServiceImpl#onBind/onCallAdded` | 不改 | 门禁方法 `A1.O.e` 是唯一决策点，直接 Hook 更稳 |

### 5.3 可选加固

- `osbot.config_get`：`com.aios.osbot.tools.phone.data.AiCallConfigHelper#getConfig(String, String)`
  对上述 3 个 key 直接返回 `defaultValue`。默认**不做**：`onCallAdded` 已阻断，额外 Hook 只会扩大影响面。
- 若真机发现 `supportXiaoAiAutoAnswer` 被 ART inline 导致未命中：优先改 Hook `CallCardFragment.onAiCallClick`
  或 `CallCardPresenter` 分支；最后才考虑 `deoptimize()`。

---

## 6. Hot Reload 设计

1. 所有 Hook 使用稳定 `setId(...)`；新代 `HookRegistry` 提供 `id → Hooker` 静态映射。
2. `onHotReloaded`：
   ```text
   for handle in oldHandles:
       hooker = HookRegistry.get(handle.getId())
       if hooker != null and process is still a target: handle.replaceHook(hooker)
       else handle.unhook()
   ```
   `replaceHook` 保留 executable / priority / exception mode / id，且不影响已进入的调用。
3. 不跨代保存目标 ClassLoader、目标对象、HookHandle；`setSavedInstanceState` 只放 `String`（进程名）。
4. 已知限制：包生命周期回调不会重放。若某宿主在热重载前尚未安装 Hook，新代不会自动补装，
   需强停该宿主进程；日志会明确记录 `hot_reloaded replaced=N unhooked=M`。
5. 不使用后台线程、native hook、外部回调，`onHotReloading` 无需额外清理。

---

## 7. 构建配置

| 项 | 值 | 依据 |
|---|---|---|
| Gradle | 8.14.3（wrapper，本地已缓存） | `~/.gradle/wrapper/dists` |
| AGP | 8.12.0（本地已缓存） | `~/.gradle/caches/.../com.android.tools.build/gradle/8.12.0` |
| compileSdk / targetSdk | 36 / 36 | `D:\AndroidSDK\platforms\android-36` |
| minSdk | 26 | libxposed API 102 下限 |
| Java | 17（toolchain） | AGP 8.12 + JDK 21 运行 |
| 依赖 | `compileOnly("io.github.libxposed:api:102.0.0")` | 已缓存，官方要求 compileOnly |
| 打包 | `packaging.resources.merges += "META-INF/xposed/*"` | 官方 example |

- 不引入 AndroidX/Kotlin；模板中的 `@NonNull` 省略，避免额外依赖。
- Debug 用默认 debug keystore 产出可安装 APK；Release 签名从 `keystore.properties`/环境变量读取，
  文件不入库。R8 规则：

```proguard
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
```

- 若后续必须 `compileSdk 37`：升级 AGP 9.1.1 + Gradle 9.x（均已缓存），作为备用方案，
  本次不启用，避免 AGP 9 的 DSL 变动扩大计划面。

---

## 8. 验证矩阵

### 8.1 构建/静态校验（当前可做）

```text
gradlew.bat :module:assembleDebug
unzip -l module/build/outputs/apk/debug/module-debug.apk | findstr META-INF/xposed
```

必须存在：`module.prop`、`java_init.list`、`scope.list`；`aapt2 dump badging` 校验 label/description/minSdk。

### 8.2 真机（需要设备；当前 adb 无设备）

安装 → LSPosed 启用（scope 静态，无需手选）→ 强停 5 个宿主 → 复现。

日志期望（模块 TAG `OhMiCall`，LSPosed 日志）：

```text
module_loaded process=...
route package=... process=... classloader=...
hook_installed id=... 
hook_hit id=...        # 触发后
class_not_found ...    # 仅版本不匹配时
```

功能用例：

| # | 场景 | 期望 |
|---|---|---|
| V1 | 来电点击 AI 按钮 | 出现旧 `AiModeDialog`/旧 CallScreen，不出现 `AiAssistCallFragment` |
| V2 | `Settings.Global com.xiaomi.aiasst.call.autoAnswer=1` + 来电 | 旧链路发 `incall_ring` → 旧引擎自动接听 |
| V3 | Osbot/MiClaw | 无新引擎接管/TTS/自动接听；日志无 `onCallAdded` 业务 |
| V4 | 拨号/联系人 AI 入口 | 回退 `aicalllog_detail`/旧设置，不跳 HyperPhone `AiCallDetailActivity` |
| V5 | AI 通话中第二路来电 | 不被 `AiAssistCallScreeningService` 自动拒接 |
| V6 | HyperPhone AI 设置页 | 页面可开；开关不再驱动新引擎行为 |
| V7 | 模块热更新 | 日志 `hot_reloaded replaced=...`，Hook 仍命中；未装 Hook 的宿主强停后恢复 |

### 8.3 回退

停用模块 → 强停 5 个宿主。模块不改 APK/DB/设置，回退无残留。

---

## 9. 风险与缓解

| 风险 | 影响 | 缓解 |
|---|---|---|
| 混淆名随版本漂移（`k3.l`、`A1.O`、`Utils`） | Hook 不命中 | 按版本记录证据；`class_not_found` 日志；缺失即 no-op；不硬编码崩溃 |
| `query` 返回空/`null` 游标 | 个别调用方 NPE | 优先空 `MatrixCursor`；观察日志与崩溃；必要时退回 `null`（已验证） |
| `onCallAdded` 全阻断 | 新引擎所有功能（含摘要/骚扰拦截）失效 | 符合“回退旧引擎”目标；文档明示；可按需改为只阻断 `setupAutoAnswer` |
| ART inline | flag Hook 不触发 | 先看 `hook_hit`；改 Hook 决策点；最后 `deoptimize` |
| Process 名不在 `packageName` | 回调不触发 | 先记录所有 `route`；按真机证据调整进程匹配 |
| LSPilot 同名插件共存 | 双重 Hook、行为难判定 | README 标注互斥；验证前禁用旧插件 |
| Android 17 平台/AGP 兼容 | 构建失败 | 已选本地可用的 36 + AGP 8.12；备用 AGP 9.1.1 |
| Hot Reload 不重放生命周期 | 未安装的宿主热更后仍无 Hook | 日志提示；强停目标进程 |

---

## 10. 分阶段实施与验收

| Phase | 内容 | 验收 |
|---|---|---|
| 0 | 工作空间（git、忽略规则、README、本计划） | 目录结构正确；`git status` 干净（除本地大文件） |
| 1 | Gradle 骨架 + 元数据 + 入口日志（不装 Hook） | `assembleDebug` 成功；APK 内含 3 个 xposed 元数据文件；LSPosed 可识别、日志 `module_loaded` |
| 2 | 7 个 Hook（5 宿主） | 每个 `hook_installed`；V1–V6 全过 |
| 3 | Hot Reload | 模块覆盖安装后 `hot_reloaded`，Hook 继续命中 |
| 4 | 文档收尾 | README 更新用法/版本矩阵；移除外置调试代码 |

Phase 1 的入口只记录生命周期与路由，不安装任何 Hook；不会伪装成“已实现”。

---

## 11. 待审阅决策

1. `phone.config_query` 返回**空 MatrixCursor**（推荐）还是沿用 LSPilot 的 `null`？
2. Osbot 采用**整段 `onCallAdded` 短路**（推荐）还是只 Hook `setupAutoAnswer`（保留新引擎其它能力）？
3. 是否同时阻断 `ai_call_auto_answer_delay`（改变自动接听延时）？默认**不阻断**，沿用用户设置。
4. HyperOS 3 设备是否保持纯 no-op（推荐，类不存在自然跳过）还是加开机版本硬门禁？
5. 模块包名/应用名：`io.github.xrh0905.ohmicall` / `OhMiCallAssistant` 是否确认？
6. 是否需要最小设置页（Remote Preferences 开关各 Hook）？默认**不做**，先用静态行为。
7. Release 签名方式：本地 `keystore.properties`（不入库）还是仅提供 debug APK？
