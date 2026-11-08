# 小米 AI 通话助手（Call Assistant）HyperOS 3 CN vs HyperOS 4 CN 详细对比

整理时间：2026-11-08  
工作流逐链路解析（独立文档）：`docs/AI_CALL_WORKFLOW_V3_V4.md`  
基线修正：上一版使用 `nezha_global_images_OS3.0.337.0.XPAMIXM`（global）作为 OS3 基线，缺少 `InCallUI/Contacts/VoiceAssist/MIUIAiasstService`，导致多处只能写“缺失”。本版改用新接入的 **中国版 HyperOS 3 真机** 作为基线，补齐全部缺失字段，并重新判定“OS4 新增”与“OS3 已存在”。

## 0. 设备与基线

| 项 | HyperOS 3 基线 | HyperOS 4 对比机 |
|---|---|---|
| 设备 | Xiaomi 13 Ultra（`ishtar` / `2304FPN6DC`） | Xiaomi 17 Pro Max（`popsicle` / `2509FPN0BC`） |
| 系统 | `OS3.0.308.0.WMACNXM`（CN） | `OS4.0.6.0.XPBCNXM`（CN） |
| Android | 16（SDK 36） | 17（SDK 37） |
| ADB | USB，序列号 `cef6254e` | USB，序列号 `2936262d` |
| 角色 | 旧实现基线 | 新实现对比 |

> 注意：两台设备型号与 Android 大版本都不同。应用级代码差异可逐类对比，但部分 `InCallUI` 变化可能混入 Android 16→17 的 AOSP 重构，不能全部归因于 HyperOS 4。下文的“新增”均指 **OS4 侧存在、OS3 CN 侧不存在**，并给出类/Manifest/字符串证据。

---

## 1. LSPilot 插件提取（OS4 设备）

### 1.1 提取位置与宿主

| 项 | 值 |
|---|---|
| LSPilot | `me.yun.lspilot` 1.2.0（19） |
| 插件根目录 | `/sdcard/Android/media/<宿主包名>/LSPilot/Plugin/` |
| 引擎 | BeanShell（`language=java`） |
| 相关宿主 | `com.xiaomi.phone`、`com.android.incallui`、`com.android.contacts`、`com.xiaomi.aiasst.service`、`com.miui.voiceassist` |

### 1.2 已部署插件

| 宿主 | 插件 | 作用 | 文件数 | 运行 |
|---|---|---|---|---|
| `com.xiaomi.phone` | `AiCallLegacyHyperPhone` | 熔断新引擎配置读取 | 9 | 已加载 |
| `com.xiaomi.phone` | `AiCallScopeProbe` | 只读配置探测 | 2 | 未运行 |
| `com.android.incallui` | `AiCallLegacyInCallUI` | 入口回退 + 关闭新引擎自动接听 | 6 | 已加载 |
| `com.android.contacts` | `AiCallLegacyContacts` | 联系人入口回退 | 6 | 已加载 |
| `com.xiaomi.aiasst.service` | `AiCallLegacyAiasst` | 旧引擎绑定修复 | 6 | 已加载 |
| `com.miui.voiceassist` | `AiCallLegacyOsbot` | 熔断 Osbot 新引擎 | **未部署** | 设备目录为空 |

本仓库落地：

```
plugins/
├── com.android.contacts/AiCallLegacyContacts/
├── com.android.incallui/AiCallLegacyInCallUI/
├── com.xiaomi.aiasst.service/AiCallLegacyAiasst/
└── com.xiaomi.phone/
    ├── AiCallLegacyHyperPhone/
    └── AiCallScopeProbe/
```

四个行为插件带 `.enabled`、`info.prop`、`main.java`、`log/`；`AiCallScopeProbe` 只有 `info.prop` 与 `main.java`，无 `.enabled`。

### 1.3 校验值

```
0b9aa6855961591447784782ca396cfa557670cbc307c72ebf9512a4ad45669f  AiCallLegacyContacts/info.prop
00ba778cee52a13e00e683ec49a44cc8f061414595526f5afd6350b474e3a007  AiCallLegacyContacts/main.java
e249b5b63bc86f8d36890a650207fe3431230632eb497fee7d706e27deeef5c0  AiCallLegacyInCallUI/info.prop
37e0086f686f7b01671a9b7807c1689ca6696e62ce635e79daf0732edce3ffcb  AiCallLegacyInCallUI/main.java
6f8b7b16f9320e753c4ff869451fd3eeea1502b156a68f4c7f4dfe428c482cdb  AiCallLegacyAiasst/info.prop
caa0dc688b107df65925e8b7f1ccd5ebc4ea4c29c1642513da4b2fb0ef0a986f  AiCallLegacyAiasst/main.java
c0c37785a1e831610e13387fe501e678c938d569d79a13bc9514a2248f552fa4  AiCallLegacyHyperPhone/info.prop
a1d0cf7aaf415512fdd2a47e087ad70af4133654d2b8163da5189409f68a0af6  AiCallLegacyHyperPhone/main.java
accf315b92ccda589f6de9ce380b67bcf486514394d5fec22f0c69b769c57790  AiCallScopeProbe/info.prop
29ec0fc98abfef279c80e0d8b740774ae7ca0ff4035024c095f899d7d53a14ee  AiCallScopeProbe/main.java
```

### 1.4 运行证据

2026-11-08 四个行为插件均加载成功：

- `AiCallLegacyHyperPhone`：`hooked AiCallConfigProvider.query`
- `AiCallLegacyInCallUI`：`hooked supportXiaoAiAutoAnswer -> false`、`hooked aiCallAutoAnswerEnabled -> false`
- `AiCallLegacyContacts`：`class=k3.l`、`hooked k(Context) -> false`
- `AiCallLegacyAiasst`：`class=A1.O`、`hooked A1.O.e(Context) -> false`

`AiCallLegacyHyperPhone/error/` 有 3 条 `Script callback onHook() timed out`（Binder 回调线程），插件本身仍加载成功。

版本注记：`AiCallLegacyHyperPhone/info.prop` 为 `1.0.0`，`main.java` 头注释为 `v1.1.0`（1.1.0 改为直接返回 null，避免 BSH 构造 `String[]` 失败）；以脚本内容为准。

---

## 2. HyperOS 3 CN 的实现：单旧引擎

### 2.1 版本与规模

| 包 | 版本 | 类数 | AI 通话类 | 关键证据 |
|---|---|---|---|---|
| `com.xiaomi.phone` (HyperPhone) | 15.0.2 (38) | 5973 | **0** | 无 `aicall` 类；Manifest 0 条 AI 通话 |
| `com.android.incallui` | 31.01.02 (4842) | 5177 | 5 | 仅旧 `aiassistant` + `AiCallRecorder` |
| `com.android.contacts` | 17.20.10.50 (201050) | 22258 | 15 | 旧 `ContactDetailAICallLogActivity` + `AtyFragment` |
| `com.xiaomi.aiasst.service` | 6.1.6 (2721) | 15872 | 966 | 旧 AI 通话全功能 |
| `com.miui.voiceassist` | 7.512.1.0917 (507512001) | 88334 | 4 | 仅 `AiCall` SDK 桩，无 Osbot |
| `com.android.phone` (TeleService) | 16 (36) | 8442 | 1 | MiClaw `PhoneToolService`，无 AI 摘要 |
| `com.android.server.telecom` | 16 (36) | 2892 | 0 | 有 `TelecomToolService`，无 AI 权限 |
| `com.xiaomi.hypercomm` | 15.0.0 (38) | 498 | 0 | 无 AI 通话符号 |
| `com.xiaomi.aicr` | 3.63.5 | 29015 | 9 | 仅 SDK/AIDL 桩 |
| `com.xiaomi.aiservice` | 3.12.4 | 5331 | 5 | 仅 AIDL 桩 |
| `com.xiaomi.aiasst.vision` | 5.12.4.20 | 22073 | 9 | 仅 SDK/AIDL 桩 |

### 2.2 OS3 的 AI 通话调用链

```
InCallUI 31.01.02
  CallCardFragment.onAiCallClick()
    -> 高温检查
    -> AiAssistantHelper.enterAi()
       -> AiModeDialog（旧）
       -> AiAssistantUtils.startAiCall()
          -> broadcast com.xiaomi.aiasst.service.use_ai_call / use_ai_call_dial
             -> aiasst.UseAiCallReceiver
                -> InCallAiServiceImpl（旧 InCallService）

aiasst 6.1.6
  InCallAiServiceImpl.onBind / onCallAdded
    -> 直接绑定/注册，无任何“新 InCallUI 存在则跳过”判断
  AICallProvider
    -> GET_AICALL_AVAILABLE / GET_AICALL_STATUS /
       GET_INCALL_VOICE_SETTINGS / GET_AUTO_ANSWER_SETTINGS / GET_DOMAIN
  UseAiCallReceiver
    -> use_ai_call / use_ai_call_dial / exit_ai_call / incall_ring

Contacts 17.20.10.50
  SystemCompat.A(ctx)  -> GET_AICALL_AVAILABLE（旧引擎可用性）
  SystemCompat.i(ctx)  -> resolveActivity(legacy aicalllog_detail)
  DialerMenuDialog     -> dialer_menu_aiphone（仅按 aiasst 版本判断）
  ContactDetailAICallLogActivity / AtyFragment -> 旧 AI 通话记录详情

VoiceAssist 7.512
  -> 无 InCallService、无 ai-assist-call-config、无 Osbot Phone 包
```

### 2.3 OS3 关键事实

- `InCallAiServiceImpl.onBind` 无门禁，直接 `super.onBind()`；`onCallAdded` 无门禁，直接注册回调。
  - 证据：`decomp/os3cn_aiasst/sources/com/xiaomi/aiasst/service/aicall/incall/InCallAiServiceImpl.java:235-270`
- aiasst 6.1.6 **不存在** `A1.O` / `A1.l0` 类，也不存在字符串 `incall_ai_assist_answer`、`support_ai_call`、`xiaoai_support_autoanswer`、`InCallUi supports ai assist answer`。
  - 证据：DEX 扫描 `AiasstService.apk` 结果为空集。
- InCallUI 31.01.02 **不存在** `com.android.incallui.aiassist/` 包（0 个类），也没有 `AiCallConfigHelper`、`supportXiaoAiAutoAnswer`、`aiCallAutoAnswerEnabled`、`AiAssistCallScreeningService`。
- InCallUI 已存在旧能力：
  - `AiAssistantUtils.awakeAi` + `Settings.Global com.xiaomi.aiasst.call.autoAnswer` + `incall_ring`
  - `AiAssistantUtils.getAiStatus` → `GET_AICALL_AVAILABLE`
  - `AiStatus` 1/2/3/4
  - `exit_ai_call`、`use_ai_call(_dial)`、`AiModeDialog`、`AiAssistantHelper`
- Contacts 已存在：
  - `ContactDetailAICallLogActivity` 与 `ContactDetailAICallLogAtyFragment`
  - `SystemCompat.A(ctx)` 的 `GET_AICALL_AVAILABLE`
  - `dialer_menu_aiphone` 菜单
  - 旧详情 intent `com.xiaomi.aiasst.service.contact.aicalllog_detail`
  - **不存在** `support_ai_call` / `android.supports_xiaoai_incallui` meta
  - **不存在** `resolveActivity("miui.intent.action.AiCallSetting")` 的新设置判断
- VoiceAssist 7.512 只有 AI API 桩（`AiCall$autoReply`、`AiCall$timbre`、`IAICallback`），**没有** `com/aios/osbot/tools/phone` 包、`OsbotInCallService`、`ai-assist-call-config`、`ai_call_auto_answer_enabled`。
- TeleService/Telecom 已有 MiClaw 工具服务（`PhoneToolService`/`TelecomToolService`，`BIND_APP_TOOL`），但没有 AI 通话摘要接收器。
- `FocusIsland`/`FocusBackScreenReceiver` 类已存在，但没有 AI 相关逻辑。

---

## 3. HyperOS 4 CN 的实现：配置中心 + 双引擎

### 3.0 架构总览

```
HyperPhone 15.0.3
  ├─ AiCallConfigProvider   ai-assist-call-config / ai_call_config.db
  ├─ AiCallDetailProvider   ai-assist-call-detail / ai_call_database
  ├─ AiCallStatusProvider   com.xiaomi.phone.ai_call_status/check
  ├─ AiCallSetting / AiCallVoiceControlSetting / AiCallDetailActivity
  └─ AiConfigMigrationReceiver -> aiasst MiClaw 迁移

InCallUI 40.01.58
  ├─ supportXiaoAiAutoAnswer（新引擎入口判断）
  ├─ aiassist/ 包（56 个类：AiAssistCallFragment/Presenter/Chat/ReplyMode...）
  ├─ AiCallConfigHelper（7 个 key + observer）
  ├─ Utils.aiCallAutoAnswerEnabled / aiCallAutoTraggerAnswerEnabled
  ├─ AiAssistCallScreeningService
  └─ AI 通知/灵动岛/背屏接听

Contacts 18.30.00.73
  ├─ k3.l.k/C 判断 HyperPhone AiCallSetting
  ├─ k3.l.H GET_AICALL_AVAILABLE
  └─ 旧 ContactDetailAICallLogActivity 保留

aiasst 6.2.2（旧引擎）
  ├─ A1.O.e 三 meta gate（新）
  ├─ InCallAiServiceImpl.onBind/onCallAdded 跳过（新）
  ├─ AICallProvider（旧，5 方法不变）
  ├─ UseAiCallReceiver（旧）
  └─ MiClawSettingsMigrationReceiver / migration service（新）

VoiceAssist 8.2.65（新引擎）
  ├─ OsbotInCallService
  ├─ AiCallConfigHelper / AiCallDetailHelper
  └─ auto answer / spam / SIM2 / TTS / ASR / 摘要

TeleService 17
  └─ AiCallSummaryReceiver + AI_CALL_SUMMARY + 通知

Telecom 17
  └─ READ_AI_CALL_DETAIL
```

### 3.1 版本矩阵

| 包 | OS3 CN | OS4 CN | 结论 |
|---|---|---|---|
| HyperPhone | 15.0.2 (38)，5973 类，0 AI | 15.0.3 (39)，6968 类，34 AI | +995 类、+34 AI 类、+10 Manifest |
| InCallUI | 31.01.02 (4842)，5177 类，5 AI | 40.01.58 (6034)，6102 类，13 AiCall 类 | 新增 `aiassist/` 56 类；`aiassistant/` 13→15 |
| Contacts | 17.20.10.50，22258 类，15 AI | 18.30.00.73，13385 类，16 AI | 大重构；新增新入口 gate/meta |
| aiasst | 6.1.6 (2721)，15872 类，966 aicall | 6.2.2 (2732)，16797 类，975 aicall | +45/-36 aicall；新增 gate/migration |
| VoiceAssist | 7.512.1.0917，88334 类，4 SDK 桩 | 8.2.65.4714，Osbot 引擎 | 新引擎整体新增 |
| TeleService | 16 (36)，8442 类 | 17 (37) | 新增摘要 receiver |
| Telecom | 16 (36)，2892 类 | 17 (37) | 新增 READ_AI_CALL_DETAIL |
| HyperComm | 15.0.0 (38)，498 类，0 AI | 15.0.0 (38)，0 AI | 无变化 |
| AICR | 3.63.5，9 AI | 4.13.6（系统 4.12.26），9 AI | 仅版本，AI 类集合不变 |
| AIService | 3.12.4，5 AI | 4.2.14（系统 4.2.12），5 AI | 仅版本，AI 类集合不变 |
| AiasstVision | 5.12.4.20，9 AI | 5.14.3.40，10 AI | +1 `AICallback` SDK 类 |

### 3.2 HyperPhone 新增

OS3 HyperPhone 15.0.2：5973 类，`aicall` 类 0，Manifest AI 条目 0。  
OS4 HyperPhone 15.0.3：6968 类，`aicall` 类 34，Manifest AI 条目 10。

新增 34 类：

```
com/xiaomi/phone/aicall/AiCallConfigProvider
com/xiaomi/phone/aicall/AiCallDetailProvider
com/xiaomi/phone/custompreferences/AiCallHeaderPreference
com/xiaomi/phone/custompreferences/AiCallQuickReplyIllustrationPreference
com/xiaomi/phone/settings/AiCallSetting
com/xiaomi/phone/settings/AiCallVoiceControlSetting
com/xiaomi/phone/settings/AiCallDetailActivity
com/xiaomi/phone/settings/AiCallStatusProvider
+ 对应内部类 / 合成 lambda
```

新增 Manifest：

```xml
<permission android:name="com.xiaomi.phone.permission.READ_AI_CALL_DETAIL" android:protectionLevel="0x12"/>
<permission android:name="com.xiaomi.phone.permission.READ_AI_CALL_CONFIG" android:protectionLevel="0x12"/>

<activity android:name="com.xiaomi.phone.settings.AiCallSetting" android:exported="true">
  <action android:name="miui.intent.action.AiCallSetting"/>
</activity>
<activity android:name="com.xiaomi.phone.settings.AiCallVoiceControlSetting"/>
<activity android:name="com.xiaomi.phone.settings.AiCallDetailActivity" android:exported="true">
  <action android:name="miui.intent.action.AiCallDetail"/>
</activity>

<provider android:name="com.xiaomi.phone.settings.AiCallStatusProvider"
          android:authorities="com.xiaomi.phone.ai_call_status" android:exported="true"/>
<provider android:name="com.xiaomi.phone.aicall.AiCallDetailProvider"
          android:authorities="ai-assist-call-detail" android:exported="true"
          android:readPermission="com.xiaomi.phone.permission.READ_AI_CALL_DETAIL"/>
<provider android:name="com.xiaomi.phone.aicall.AiCallConfigProvider"
          android:authorities="ai-assist-call-config" android:exported="true"
          android:readPermission="com.xiaomi.phone.permission.READ_AI_CALL_CONFIG"/>
```

### 3.3 配置中心

| URI | 路径 | 存储 | 权限 |
|---|---|---|---|
| `ai-assist-call-config` | `settings`、`settings/*` | `ai_call_config.db` v2，`config(key,value)` | `READ_AI_CALL_CONFIG`（0x12） |
| `ai-assist-call-detail` | `records`、`records/#`、`messages`、`messages/#` | `ai_call_database`，`ai_call_records`/`ai_call_messages` | `READ_AI_CALL_DETAIL`（0x12） |
| `com.xiaomi.phone.ai_call_status` | `check` | `MatrixCursor(condition_met)` | 无 |
| `com.xiaomi.aiasst.service.aicall.provider` | `call()` | 旧引擎 | 包名+签名白名单 |
| `com.miui.voiceassist.cta` | `call("check_cta")` | 无 | 内部 |

`ai_call_config.db` 默认值：

```sql
CREATE TABLE config (key TEXT PRIMARY KEY, value TEXT NOT NULL DEFAULT '')
ai_call_auto_answer_enabled = false
ai_call_auto_answer_delay   = 20
ai_call_spam_time           = RECORD_SYNCED
ai_call_spam_types          = 64
ai_call_voice_gender        = RECORD_SYNCED
ai_call_quick_reply_switch  = false
```

v1→v2 只补 `ai_call_quick_reply_switch=false`。

能力检查 `AiCallStatusProvider`：

1. 小米账号已登录；
2. `com.miui.voiceassist` 已安装且 `xiaoai_support_autoanswer != 0`；
3. `content://com.miui.voiceassist.cta/check` 的 `call("check_cta")` 返回 `is_agreed=true`。

### 3.4 配置 key

| Key | 默认/语义 | 读者 | 写者 |
|---|---|---|---|
| `ai_call_auto_answer_enabled` | `false` | Osbot、InCallUI `Utils.aiCallAutoAnswerEnabled` | HyperPhone 设置页 |
| `ai_call_auto_answer_enabled_sim2` | 无默认 | Osbot 卡 2 | 未发现显式写点（[INFERENCE]） |
| `ai_call_auto_answer_delay` | `20` | Osbot、InCallUI | HyperPhone 设置页 |
| `ai_call_auto_answer_delay_sim2` | 无默认 | Osbot 卡 2 | 未发现显式写点（[INFERENCE]） |
| `ai_call_spam_time` | `RECORD_SYNCED` | Osbot | HyperPhone 设置页 |
| `ai_call_spam_time_sim2` | 无默认 | Osbot 卡 2 | 未发现显式写点（[INFERENCE]） |
| `ai_call_spam_types` | `64` | Osbot | HyperPhone 设置页 |
| `ai_call_spam_types_sim2` | 无默认 | Osbot 卡 2 | 未发现显式写点（[INFERENCE]） |
| `ai_call_voice_gender` | `RECORD_SYNCED` | Osbot | HyperPhone 设置页 |
| `ai_call_trigger_answer` | 手动入口写 `true`，结束写 `false` | InCallUI、Osbot observer、ScreeningService | InCallUI |
| `ai_call_manual_reply_mode` | 无默认 | Osbot | InCallUI |
| `ai_call_assist_reply` | 无默认 | Osbot / 通知快捷回复 | InCallUI |
| `ai_call_dtmf_reply` | 无默认 | Osbot | InCallUI |
| `ai_call_more_reply_clicked` | 无默认 | Osbot | InCallUI |
| `ai_call_reply` / `ai_call_reply_description` | 无默认 | 回复链路 | HyperPhone / voiceassist |
| `ai_call_quick_reply_switch` | `false` | HyperPhone 设置 | HyperPhone 设置 |
| `ai_call_weak_network` | `false` | InCallUI / Osbot | InCallUI |
| `ai_call_incoming_log_content` / `ai_call_reply_log_content` | 空 | Osbot | Osbot |
| `ai_call_cached_missed_time` / `ai_call_cached_spam_time` | 无默认 | HyperPhone 设置缓存 | HyperPhone 设置页 |
| `ai_sim2_settings` | 无默认 | Osbot | 未发现 HyperPhone 写点（[INFERENCE]） |
| `ai_incoming_call_voice_control` / `ai_voice_reminder` | 无默认 | HyperPhone 语音控制 | HyperPhone 语音控制 |
| `ai_custom_opening_acquaintance` / `ai_custom_opening_stranger` | 无默认 | 开场白 | HyperPhone/voiceassist |

### 3.5 InCallUI 新增细节

Manifest 新增：`READ_AI_CALL_CONFIG`、`BIND_SCREENING_SERVICE`、`incall_ai_assist_answer=1`、`AiAssistCallScreeningService`；OS3 原有 `USE_AI_CALL`、`incall_ai_assistant=4`、`InCallServiceImpl` 保留。

- `aiassist/` 包：0（OS3）→ 56 类（OS4），包括：
  `AiAssistCallFragment`、`AiAssistCallPresenter`、`AiAssistChatController`、`AiAssistReplyModeController`、`AiAssistBottomControlsController`、`AiAssistCallBarController`、`AiAssistNumberKeyboardController`、`AiAssistKeyboardTrackerController`、`AiAssistGradientScrimController`、`AiCallModeDialog` 等。
- `AiAssistCall` 类：0 → 22。
- `aiassistant/` 类：13 → 15（新增 `AiCallConditionCallback`、`AiAssistantUtils$3`）。
- `AiCall` 类：5 → 13；新增 `AiCallConfigHelper`、`AiCallModeDialog` 等。
- `supportXiaoAiAutoAnswer(Context)`：检查 voiceassist meta `xiaoai_support_autoanswer` + contacts meta `support_ai_call`；两个都满足才走新引擎。
  注意：InCallUI 只查 2 个 meta（voiceassist + contacts），而 aiasst 6.2.2 的 `A1.O.e` 查 3 个（额外要求 `incall_ai_assist_answer`）。这就是“新引擎入口”和“旧引擎退出”条件不对称的来源。
- `CallCardFragment.onAiCallClick`：
  - OS3：始终 `AiAssistantHelper.enterAi()`；
  - OS4：`supportXiaoAiAutoAnswer` 为 true 时 `showAiCallModeDialog()` → `startAiCall()`；否则旧路径。
- `startAiCall()`：写 `ai_call_manual_reply_mode=false`，`ai_call_trigger_answer=true`，显示 `AiAssistCallFragment`；文字回复模式 2 秒后补写 `manual_reply_mode=true`。
- `AiCallConfigHelper`：7 个 key + `ContentObserver`（`ai_call_assist_reply`、`ai_call_dtmf_reply`、`ai_call_manual_reply_mode`、`ai_call_more_reply_clicked`、`ai_call_quick_reply_switch`、`ai_call_reply_description`、`ai_call_weak_network`）。
- `Utils.aiCallAutoAnswerEnabled()` / `aiCallAutoTraggerAnswerEnabled()`：读 provider。
- `AiAssistCallScreeningService`：AI 通话中且 trigger 为 true 时拒接第二路来电。
- `AiAssistantUtils.queryAiCallCondition` + `AiCallConditionCallback`：`ai_call_status/check` 的 `condition_met`。
- `AiAssistantUtils.awakeAi` 行为变化：
  - OS3：`Settings.Global com.xiaomi.aiasst.call.autoAnswer != 0` 就发 `incall_ring`；
  - OS4：还要 `!supportXiaoAiAutoAnswer(context)` 才发；有新引擎时抑制旧 `incall_ring`。
- 通知/岛/背屏：
  - `ACTION_PRIORITY_AI_ANSWER`、`ACTION_SHOW_MORE_REPLY`、`EXTRA_AI_QUICK_REPLY_TEXT/_NUMBER_TYPE`（OS3 NotificationBroadcastReceiver 无 AI 字符串）；
  - `FocusIsland` 新增 `miui.focus.ic_ai_assist_call`、`aiCallAutoAnswerEnabled`/`aiCallAutoTraggerAnswerEnabled` 分支；
  - `FocusBackScreenReceiver` 新增 AI 接听分支。

### 3.6 Contacts 新增/变更

OS3 Contacts 17.20.10.50（22258 类，15 AI）→ OS4 18.30.00.73（13385 类，16 AI，整体重构）。

**OS3 已有**：

- `ContactDetailAICallLogActivity` + `ContactDetailAICallLogAtyFragment`
- `SystemCompat.A(ctx)` → `GET_AICALL_AVAILABLE`
- `SystemCompat.i(ctx)` → `resolveActivity(legacy aicalllog_detail)`
- `DialerMenuDialog` 的 `dialer_menu_aiphone`
- 没有 `support_ai_call` / `android.supports_xiaoai_incallui` meta
- 没有 `resolveActivity("miui.intent.action.AiCallSetting")`

**OS4 新增**：

- `k3.l.k(ctx)` → `resolveActivity(new Intent("miui.intent.action.AiCallSetting"))`
- `k3.l.C(ctx)` = `!o.V() && k(ctx) && w(ctx)`
- `k3.l.j/q/r`：`AiCallDetailActivity` / `AiCallSetting`
- `k3.l.H(ctx)`：`GET_AICALL_AVAILABLE`，status 1/3/4 可用
- meta `support_ai_call=1`、`android.supports_xiaoai_incallui=1`
- `DialerMenuDialog` 条件从“仅 aiasst 版本”改为 `k3.l.C(ctx) || aiasst版本>=1377`
- 旧 `ContactDetailAICallLogActivity` 保留，不是新增功能；OS4 另有 HyperPhone `AiCallDetailActivity`

### 3.7 aiasst 新增/变更

OS3 6.1.6（15872 类，966 aicall）→ OS4 6.2.2（16797 类，975 aicall，+45/-36）。

**OS3 已有**：

- `InCallAiServiceImpl`（无 gate）
- `AICallProvider` 5 个 call 方法
- `UseAiCallReceiver`（`use_ai_call`、`use_ai_call_dial`、`exit_ai_call`）
- `CallScreenService`、`MIUI11CallScreenEntranceService`、`RingService`
- 旧设置/详情 Activity
- `incall_ring`、`aicall*` 等 action

**OS4 新增**：

- `A1.O` / `A1.l0` 类（OS3 完全不存在）
- `A1.O.e(ctx)` = `g && c && f`：
  - `com.miui.voiceassist` meta `xiaoai_support_autoanswer == 1`
  - `com.android.contacts` meta `support_ai_call == 1`
  - `com.android.incallui` meta `incall_ai_assist_answer >= 1`
- `InCallAiServiceImpl.onBind/onCallAdded` 在 `O.e(ctx)` 为 true 时跳过：
  - `onBind` 返回 null
  - `onCallAdded` 直接 return
- `MiClawSettingsMigrationReceiver`
- `MiClawCommonSceneReplyMigrationJobService`
- `com.xiaomi.ai_config_migrate`、`com.xiaomi.aiasst.service.debug_miclaw_migrate` action
- 其余 45 个新增 `aicall` 类多为迁移/工具/重构

> 关键：`AICallProvider` 和 `UseAiCallReceiver` **不是 OS4 新增**；OS3 6.1.6 已存在且 5 个 call 方法相同。

### 3.8 VoiceAssist / Osbot 新增

OS3 7.512.1.0917：

- 88334 类；
- 4 个 AI 类（`AiCall` API + `IAICallback`）；
- **0** 个 `com/aios/osbot/tools/phone` 类；
- 无 `OsbotInCallService`、无 `ai-assist-call-config`、无 `ai_call_*`。

OS4 8.2.65.4714：

- `com.aios.osbot.tools.phone.service.OsbotInCallService`
- `com.aios.osbot.tools.phone.data.AiCallConfigHelper`
- `com.aios.osbot.tools.phone.data.AiCallDetailHelper`
- `AiCallType`、ASR/TTS/录音/回复/摘要相关类
- Manifest：
  - `BIND_INCALL_SERVICE` 的 `OsbotInCallService`
  - `xiaoai_support_autoanswer=1`
  - `READ_AI_CALL_DETAIL`、`READ_AI_CALL_CONFIG`、`RECEIVE_AI_CALL_SUMMARY`、`USE_AI_CALL`

Osbot 关键行为：

- `onCallAdded`：仅来电方向启动；已有活跃通话时跳过。
- `setupAutoAnswer(call, delaySeconds)`：`AUTO_ANSWER_DELAY_SECONDS=3`。
- 6 秒响铃提醒（TTS，静音/勿扰跳过）。
- `registerTriggerAnswerObserver`：`ai_call_trigger_answer=true` 且通话 active 时接管。
- 配置键含 `_sim2`、`ai_sim2_settings`、`ai_custom_opening_*`。
- `AiCallDetailHelper` 使用 `content://ai-assist-call-detail/records` / `messages`。

### 3.9 TeleService / Telecom 新增

OS3 TeleService 16：

- 已有 `com.android.phone.miclaw.PhoneToolService`（`BIND_APP_TOOL`）
- 无 AI 摘要权限/receiver

OS4 TeleService 17：

```xml
<permission android:name="com.android.phone.permission.RECEIVE_AI_CALL_SUMMARY" android:protectionLevel="0x12"/>
<receiver android:name="com.android.phone.settings.AiCallSummaryReceiver"
          android:permission="com.android.phone.permission.RECEIVE_AI_CALL_SUMMARY"
          android:exported="true">
  <action android:name="miui.intent.action.AI_CALL_SUMMARY"/>
</receiver>
```

`AiCallSummaryReceiver`：

- 读 `contactCallLogId`、`ai_summary`、`ai_summary_title`；
- OneTrack 事件 `ai_call_summary_result`；
- 通知 channel `ai_call_summary`；
- 点击 → `miui.intent.action.AiCallDetail`（`com.xiaomi.phone`），extra `contactCallLogId`。

OS3 Telecom 16 已有 `TelecomToolService`，无 `READ_AI_CALL_DETAIL`；OS4 Telecom 17 新增该权限。

---

## 4. 差异总表

> 类型：新增 / 变更 / 仅版本 / 无变化。  
> “新增”= OS4 存在、OS3 CN 不存在；“变更”= 两边都有但逻辑/契约变化。

| ID | 域 | OS3 CN | OS4 CN | 类型 | 证据 |
|---|---|---|---|---|---|
| D-01 | HyperPhone 版本/规模 | 15.0.2，5973 类 | 15.0.3，6968 类 | 变更 | APK/DEX |
| D-02 | HyperPhone AI 类 | 0 | 34 | 新增 | DEX 类名 |
| D-03 | HyperPhone AI Manifest | 0 | 10 | 新增 | AXML |
| D-04 | 配置 Provider | 无 | `ai-assist-call-config` | 新增 | `AiCallConfigProvider` |
| D-05 | 详情 Provider | 无 | `ai-assist-call-detail` | 新增 | `AiCallDetailProvider` |
| D-06 | 状态 Provider | 无 | `com.xiaomi.phone.ai_call_status` | 新增 | `AiCallStatusProvider` |
| D-07 | 配置 DB | 无 | `ai_call_config.db` v2 | 新增 | `z2.a` |
| D-08 | 详情 DB | 无 | `ai_call_database` | 新增 | `AiCallDetailProvider` |
| D-09 | 设置页 | 无 | `AiCallSetting` / `AiCallVoiceControlSetting` | 新增 | Manifest |
| D-10 | 详情页 | 无 | `AiCallDetailActivity` + `contactCallLogId` | 新增 | Manifest/源码 |
| D-11 | 配置迁移 | 无 | `AiConfigMigrationReceiver` → MiClaw | 新增 | 源码 |
| D-12 | InCallUI 版本/规模 | 31.01.02，5177 类 | 40.01.58，6102 类 | 变更 | APK/DEX |
| D-13 | InCallUI `aiassist/` 包 | 0 | 56 | 新增 | DEX |
| D-14 | AI 引擎入口 | 始终旧 `enterAi()` | `supportXiaoAiAutoAnswer` 分支 | 新增 | `CallCardFragment` |
| D-15 | 新引擎配置 helper | 无 | `AiCallConfigHelper` 7 key | 新增 | 源码 |
| D-16 | 自动接听开关 | 无 | `Utils.aiCallAutoAnswerEnabled` | 新增 | 源码 |
| D-17 | trigger 开关 | 无 | `Utils.aiCallAutoTraggerAnswerEnabled` | 新增 | 源码 |
| D-18 | 手动接管写 trigger | 无 | `startAiCall` 写 `true` | 新增 | `CallCardFragment` |
| D-19 | 第二路来电控制 | 无 | `AiAssistCallScreeningService` | 新增 | Manifest/源码 |
| D-20 | InCallUI meta | `incall_ai_assistant=4` | +`incall_ai_assist_answer=1` | 新增 | AXML |
| D-21 | 状态条件查询 | 无 | `queryAiCallCondition` + callback | 新增 | `AiAssistantUtils` |
| D-22 | `awakeAi` 行为 | 只看 Global 开关 | 有新引擎时抑制旧 `incall_ring` | 变更 | `AiAssistantUtils` |
| D-23 | AI 通知快捷回复 | 无 | `ACTION_PRIORITY_AI_ANSWER` 等 | 新增 | Receiver |
| D-24 | 灵动岛 AI | 无 | AI 图标/小岛/trigger 分支 | 新增 | `FocusIsland` |
| D-25 | 背屏 AI 接听 | 无 | `AiAssistCallPresenter` 分支 | 新增 | `FocusBackScreenReceiver` |
| D-26 | Contacts 版本/规模 | 17.20.10.50，22258 类 | 18.30.00.73，13385 类 | 变更 | APK/DEX |
| D-27 | Contacts meta | 无 | `support_ai_call=1`、`android.supports_xiaoai_incallui=1` | 新增 | AXML |
| D-28 | Contacts 新设置入口 | 无 | `k3.l.k/C` + `AiCallSetting` | 新增 | 源码 |
| D-29 | Contacts AI 可用性 | `SystemCompat.A` | `k3.l.H`（语义相同） | 变更/等价 | 源码 |
| D-30 | Contacts AI 详情 Activity | 已有 | 保留 | 无新增 | DEX 15→16 |
| D-31 | Dialer AI 菜单 | 已有 `dialer_menu_aiphone` | 保留，判断条件改为 `C || 版本` | 变更 | `DialerMenuDialog` |
| D-32 | aiasst 版本/规模 | 6.1.6，15872 类 | 6.2.2，16797 类 | 变更 | APK/DEX |
| D-33 | 旧引擎三 meta gate | 无 `A1.O` | `A1.O.e` | 新增 | 类/字符串扫描 |
| D-34 | 旧引擎绑定行为 | 直接绑定 | `O.e` 为 true 则跳过 | 变更 | `InCallAiServiceImpl` |
| D-35 | AICallProvider | 已有 5 方法 | 同 5 方法 | 无新增 | 源码 |
| D-36 | UseAiCallReceiver | 已有 3 action | 同 3 action | 无新增 | 源码/Manifest |
| D-37 | MiClaw 迁移 | 无 | Receiver + JobService + 2 action | 新增 | Manifest/类 |
| D-38 | VoiceAssist 版本 | 7.512.1.0917，88334 类 | 8.2.65.4714 | 变更 | APK/DEX |
| D-39 | Osbot 新引擎 | 0 个 phone 类 | `OsbotInCallService` 等 | 新增 | DEX/Manifest |
| D-40 | voiceassist 配置/详情 helper | 无 | `AiCallConfigHelper`/`AiCallDetailHelper` | 新增 | 源码 |
| D-41 | SIM2 独立配置 | 无 | `_sim2` + `ai_sim2_settings` | 新增 | `OsbotInCallService` |
| D-42 | AI 摘要 | 无 | TeleService receiver + 通知 | 新增 | Manifest/源码 |
| D-43 | Telecom AI 权限 | 无 | `READ_AI_CALL_DETAIL` | 新增 | AXML |
| D-44 | HyperComm | 15.0.0，0 AI | 15.0.0，0 AI | 无变化 | DEX |
| D-45 | AICR | 3.63.5，9 AI | 4.13.6，9 AI | 仅版本 | DEX |
| D-46 | AIService | 3.12.4，5 AI | 4.2.14，5 AI | 仅版本 | DEX |
| D-47 | AiasstVision | 5.12.4.20，9 AI | 5.14.3.40，10 AI | 仅版本（+1 SDK 类） | DEX |
| D-48 | MiClaw 工具服务 | TeleService/Telecom 已有 | 保留 | 无变化 | Manifest |

---

## 5. HyperOS 4 新增点位清单

### 5.1 HyperPhone

| 编号 | 点位 | 证据 |
|---|---|---|
| N-01 | `AiCallConfigProvider`，authority `ai-assist-call-config`，`settings`/`settings/*` | `decomp/os4_hp/sources/com/xiaomi/phone/aicall/AiCallConfigProvider.java:28-32` |
| N-02 | `ai_call_config.db` v2，`config(key,value)` | 同文件 `:87-95`；`decomp/os4_hp/sources/z2/a.java:22-33` |
| N-03 | 6 个默认值 | `z2/a.java:25-31` |
| N-04 | `AiCallDetailProvider`，`records`/`records/#`/`messages`/`messages/#`，`migrate_batch` | `AiCallDetailProvider.java:40-45,91` |
| N-05 | `AiCallStatusProvider`，`check` → `condition_met` | `settings/AiCallStatusProvider.java:34-95` |
| N-06 | 能力条件：账号 + voiceassist meta + CTA | `AiCallStatusProvider.java:55-95`；`k3/a.java:7-15` |
| N-07 | `AiCallSetting` / `AiCallVoiceControlSetting` / `AiCallDetailActivity` | Manifest |
| N-08 | `miui.intent.action.AiCallSetting` / `AiCallDetail` | Manifest；`AiCallDetailActivity.java:750,1263` |
| N-09 | `AiConfigMigrationReceiver` BOOT → `com.xiaomi.ai_config_migrate` | `com/xiaomi/phone/AiConfigMigrationReceiver.java:12-33` |
| N-10 | `ai_config_migrate_completed` System.Global 门 | 同文件 `:18` |
| N-11 | 设置页 key `button_ai_call_*` | `settings/AiCallSetting.java:218-431` |
| N-12 | 语音控制 key | `settings/AiCallVoiceControlSetting.java:41-49` |
| N-13 | HP 侧配置读写 `f3.a` | `decomp/os4_hp/sources/f3/a.java:24,77` |

### 5.2 InCallUI

| 编号 | 点位 | 证据 |
|---|---|---|
| N-14 | `aiassist/` 包 56 类（新引擎 UI） | DEX 类集合差 |
| N-15 | `AiCallModeDialog`（`aiassist`） | DEX |
| N-16 | `supportXiaoAiAutoAnswer` 两 meta 判断 | `AiAssistantUtils.java:298-334` |
| N-17 | `onAiCallClick` 新/旧分支 | `CallCardFragment.java:527-544` |
| N-18 | `startAiCall` 写 trigger/manual mode | `CallCardFragment.java:803-833` |
| N-19 | `AiCallConfigHelper` 7 key + observer | `util/AiCallConfigHelper.java:49-110` |
| N-20 | `Utils.aiCallAutoAnswerEnabled/aiCallAutoTraggerAnswerEnabled` | `util/Utils.java:121-127,852-854` |
| N-21 | `AiAssistCallScreeningService` | `AiAssistCallScreeningService.java:15-30` |
| N-22 | `incall_ai_assist_answer=1` meta | AXML |
| N-23 | `queryAiCallCondition` + `AiCallConditionCallback` | `AiAssistantUtils.java:207-253` |
| N-24 | `awakeAi` 抑制旧 `incall_ring` | `AiAssistantUtils.java:56-80` |
| N-25 | AI 通知快捷回复 action/extra | `NotificationBroadcastReceiver.java:37-48,159,311-328` |
| N-26 | 灵动岛 AI 图标/分支 | `FocusIsland.java:40,74,85-89,220,241,269-274` |
| N-27 | 背屏 AI 接听 | `FocusBackScreenReceiver.java:39-42` |
| N-28 | `AiAssistCall*` 22 类 | DEX |

### 5.3 Contacts

| 编号 | 点位 | 证据 |
|---|---|---|
| N-29 | `k3.l.k` = `resolveActivity("miui.intent.action.AiCallSetting")` | `decomp/os4_contacts/sources/k3/l.java:322-330` |
| N-30 | `k3.l.C` = `!o.V() && k && w` | `k3/l.java:59-61` |
| N-31 | `k3.l.H` = `GET_AICALL_AVAILABLE` 1/3/4 | `k3/l.java:84-130` |
| N-32 | `k3.l.j/q/r` 新 Intent | `k3/l.java:357-385` |
| N-33 | meta `support_ai_call=1`、`android.supports_xiaoai_incallui=1` | AXML |
| N-34 | Dialer 菜单条件 `C || 版本` | `dialer/DialerMenuDialog.java:280-282` |

> `ContactDetailAICallLogActivity`、`GET_AICALL_AVAILABLE`、`dialer_menu_aiphone` 本身 OS3 已存在，不列为新增。

### 5.4 aiasst

| 编号 | 点位 | 证据 |
|---|---|---|
| N-35 | `A1.O` / `A1.l0` 类（OS3 无） | DEX 类集合差 |
| N-36 | `A1.O.e` 三 meta gate | `decomp/os4_aiasst/sources/A1/O.java:25-67` |
| N-37 | `InCallAiServiceImpl.onBind/onCallAdded` 跳过 | `InCallAiServiceImpl.java:236-257` |
| N-38 | `MiClawSettingsMigrationReceiver` | `.../receiver/MiClawSettingsMigrationReceiver.java` |
| N-39 | `MiClawCommonSceneReplyMigrationJobService` | Manifest |
| N-40 | `com.xiaomi.ai_config_migrate` / `debug_miclaw_migrate` | Manifest action |

> `AICallProvider`、`UseAiCallReceiver`、旧 Activity/Service 不是新增。

### 5.5 VoiceAssist / Osbot

| 编号 | 点位 | 证据 |
|---|---|---|
| N-41 | `OsbotInCallService` + `BIND_INCALL_SERVICE` | Manifest |
| N-42 | `xiaoai_support_autoanswer=1` | Manifest |
| N-43 | `AiCallConfigHelper` authority `ai-assist-call-config` | `decomp/os4_voice_cfg/.../AiCallConfigHelper.java:35-105` |
| N-44 | 配置键集合（含 `_sim2`） | `decomp/os4_voice_osbot/.../OsbotInCallService.java:121-176` |
| N-45 | `setupAutoAnswer` / 3 秒常量 | 同文件 `:4875-4893` |
| N-46 | 6 秒响铃提醒 | `onCallAdded$1` |
| N-47 | `registerTriggerAnswerObserver` | 同文件 `:4504-4517` |
| N-48 | `AiCallDetailHelper` | `decomp/os4_voice_detail/.../AiCallDetailHelper.java:52-55` |
| N-49 | `READ_AI_CALL_DETAIL/CONFIG`、`RECEIVE_AI_CALL_SUMMARY` | Manifest |

### 5.6 TeleService / Telecom

| 编号 | 点位 | 证据 |
|---|---|---|
| N-50 | `AiCallSummaryReceiver` + `AI_CALL_SUMMARY` | Manifest；`decomp/os4_teleservice_summary/.../AiCallSummaryReceiver.java` |
| N-51 | 通知 channel `ai_call_summary` → `AiCallDetail` | 同上 |
| N-52 | Telecom `READ_AI_CALL_DETAIL` | AXML |

---

## 6. 插件覆盖关系与缺口

| HyperOS 4 点位 | 插件 | 覆盖方式 | 结论 |
|---|---|---|---|
| `AiCallConfigProvider` 3 开关 | `AiCallLegacyHyperPhone` | `query` 返回 null | 覆盖 3 个 key |
| InCallUI 新引擎入口 | `AiCallLegacyInCallUI` | `supportXiaoAiAutoAnswer=false` | 覆盖 |
| InCallUI 自动接听判断 | `AiCallLegacyInCallUI` | `aiCallAutoAnswerEnabled=false` | 覆盖 InCallUI 侧 |
| Contacts 新设置入口 | `AiCallLegacyContacts` | `k3.l.k=false` | 覆盖入口；`H()` 未覆盖 |
| 旧引擎三 meta gate | `AiCallLegacyAiasst` | `A1.O.e=false` | 覆盖 |
| Osbot 新引擎接管 | `AiCallLegacyOsbot`（未部署） | `onCallAdded`/`onStartCommand` 短路 | **未生效** |

插件与 OS3/OS4 的适配关系：

- 四个行为插件的目标类在 **OS3 CN 中均不存在**：
  - `AiCallConfigProvider`（OS3 HyperPhone 无）
  - `supportXiaoAiAutoAnswer` / `aiCallAutoAnswerEnabled`（OS3 InCallUI 无）
  - `k3.l`（OS3 Contacts 用 `SystemCompat`）
  - `A1.O.e`（OS3 aiasst 无）
- 因此这组插件是 **OS4 专用适配器**，在 OS3 上会加载失败或空跑。

缺口：

1. `AiCallLegacyContacts` 只 hook `k(Context)`；`k3.l.H(Context)` 的 `GET_AICALL_AVAILABLE` 未拦截，某些菜单仍可能认为 AI 可用。
2. `AiCallLegacyInCallUI` 不 hook `aiCallAutoTraggerAnswerEnabled`；依赖 HyperPhone 插件把 `ai_call_trigger_answer` 读成 null。
3. `AiCallLegacyHyperPhone` 只拦 3 个 key；delay/spam/voice 等仍可读。
4. `AiCallLegacyOsbot` 未部署，Osbot 仍会收到 `onCallAdded`；只是配置熔断/入口回退削弱。
5. `AiCallLegacyHyperPhone/error/` 有 3 条 BSH 回调超时。

---

## 7. 风险与限制

1. **跨机型/跨 Android 版本**：OS3 是 Xiaomi 13 Ultra / Android 16，OS4 是 Xiaomi 17 Pro Max / Android 17。InCallUI/Contacts 的类名与数量差异包含 AOSP 重构因素。
2. **应用版本同时跨越 OS 与机型**：例如 InCallUI 31→40、Contacts 17.20→18.30，不能把所有差异都归因于 HyperOS 4。
3. **OS4 双引擎并存**：新引擎 `Osbot`、旧引擎 `aiasst` 同时可被 Telecom 绑定；最终行为取决于 meta 契约与配置读取。
4. **三 meta 契约是隐式跨应用协议**：`xiaoai_support_autoanswer`、`support_ai_call`、`incall_ai_assist_answer` 任一缺失都会改变旧/新引擎选择。
5. **区域差异已排除，但机型差异仍在**：本版两边均为 CN 版，解决了上一版 global/CN 混杂问题；仍不能完全排除机型定制。
6. **`ai_sim2_settings` 写点未定位**：只在 Osbot 读到，未在 HyperPhone/InCallUI 找到显式写点，可能由 voiceassist 或云端写入。
7. **`AiCallScopeProbe` 未运行**：没有设备侧问候语/开场白键实测输出。
8. **`AiCallLegacyOsbot` 离线源码未纳入本仓库**：设备未部署，报告只引用离线路径。

---

## 8. 复现命令

```bash
# 设备
adb devices -l
adb -s cef6254e shell getprop ro.build.fingerprint
adb -s 2936262d shell getprop ro.build.fingerprint

# 版本/路径
adb -s cef6254e shell 'dumpsys package com.android.incallui | grep -E "versionName|codePath"'
adb -s 2936262d shell 'dumpsys package com.android.incallui | grep -E "versionName|codePath"'

# 拉取 CN OS3 APK（示例）
adb -s cef6254e pull /product/priv-app/InCallUI/InCallUI.apk apk/os3cn/
adb -s cef6254e pull /system_ext/priv-app/HyperPhone/HyperPhone.apk apk/os3cn/
adb -s cef6254e pull "/data/app/.../base.apk" apk/os3cn/Contacts.apk

# 类集合/差异（Python + androguard）
python - <<'PY'
from androguard.core.dex import DEX
import zipfile
def names(p):
    z=zipfile.ZipFile(p); s=set()
    for n in z.namelist():
        if n.endswith('.dex'):
            s |= {c.get_name() for c in DEX(z.read(n)).get_classes()}
    return s
A=names('apk/os3cn/InCallUI.apk'); B=names('work/aicall/incallui.apk')
print(len(A),len(B),len([x for x in B if 'aiassist/' in x]))
PY
```

---

## 9. 产物索引

| 路径 | 内容 |
|---|---|
| `plugins/` | 5 个 OS4 LSPilot AI 通话插件 |
| `apk/os3cn/` | CN OS3 全量对比 APK：HyperPhone/InCallUI/Contacts/AiasstService/VoiceAssist/AICR/AIService/AiasstVision/TeleService/Telecom/HyperComm |
| `apk/os4_*.apk` | OS4 补充 APK |
| `decomp/os3cn_incallui/` | InCallUI 31.01.02 全量反编译 |
| `decomp/os3cn_contacts/` | Contacts 17.20.10.50 全量反编译 |
| `decomp/os3cn_aiasst/` | aiasst 6.1.6 全量反编译 |
| `decomp/os4_incallui/` | InCallUI 40.01.58 全量反编译 |
| `decomp/os4_contacts/` | Contacts 18.30.00.73 全量反编译 |
| `decomp/os4_hp/` | HyperPhone 15.0.3 全量反编译 |
| `decomp/os4_aiasst/` | aiasst 6.2.2 全量反编译 |
| `decomp/os4_voice_*` | voiceassist 单类/配置/详情 helper |
| `decomp/os4_teleservice_summary/` | AiCallSummaryReceiver |
| `decomp/os4_aiasst_provider/` | AICallProvider |
| `decomp/os4_aiasst_migration/` | MiClawSettingsMigrationReceiver |

---

## 9.1 CN OS3 APK 校验值

以下为设备当前生效的 `base.apk`（数据分区更新版），不是 system 预装版。

```
432388fe73ee8cbae3402fa51ffa875a4f725f25daca2ff752c9fd7c697de1c0  AICR.apk
0c1f543fb8836453d9574ef929dce40e3f88d7ed7190e8a91f4d2917f63a50f1  AIService.apk
395ebf9b6618fb98f8718ad3add9bf809ddfa73cdb8b43bf6619c32061506a33  AiasstService.apk
4e93589a84d9d90dacf8c3671e679dcdb4ad30e0112ffd499b3a13f8c4ae3539  AiasstVision.apk
cfc19282abd5e15981a777466cb9011b4830563f98cfa18d7728ba1e2a1552ae  Contacts.apk
1483300592a29b72ab9e3b63fa802ab787fde7239c94c531c4529536c0fa82dc  HyperComm.apk
4b27d97807d12d9f1b97519878aff2ae9952a6d1ae1bf115b42cfca5a085c62d  HyperPhone.apk
10b106268f8bad38df242ca12086bb89aece6d2103ab0a42a4bc135fc3ef8d84  InCallUI.apk
14373e02ae30dfc8c1392dc46e505fe1415d721cc52dadbd2cdeac5760afaef4  TeleService.apk
15db464ac65af504596d93a4b1604c26162c252feeda721a76ceee7adf05e852  Telecom.apk
90c9e1b6d619ae95076220070333260f482154ed309e1452794f696889e0f083  VoiceAssist.apk
```

---

## 10. 结论

- **OS3 CN 是单旧引擎架构**：`InCallUI` 旧入口 → `aiasst` 旧 InCallService；没有 HyperPhone AI 配置中心，没有 Osbot，也没有三 meta 契约。旧 `AICallProvider`、AI 记录详情、Dialer AI 菜单、`GET_AICALL_AVAILABLE` 都已存在。
- **OS4 的核心变化不是“新增 AI 通话功能”**，而是引入 **双引擎 + 配置中心 + 三 meta 契约**：
  - HyperPhone 成为配置/状态/详情中心；
  - InCallUI 新增 `aiassist/` 新引擎 UI 与入口判断；
  - Contacts 新增 HyperPhone 设置/详情入口判断；
  - aiasst 新增 `A1.O.e` 门禁，导致旧引擎在新 InCallUI 存在时主动退出；
  - VoiceAssist 新增 Osbot 新引擎；
  - TeleService 新增 AI 摘要通知。
- LSPilot 的 5 个插件正是围绕这 5 个变化点做“配置熔断 / 入口回退 / 旧引擎修复”；其中 `AiCallLegacyOsbot` 未部署，是当前唯一的明显缺口。
