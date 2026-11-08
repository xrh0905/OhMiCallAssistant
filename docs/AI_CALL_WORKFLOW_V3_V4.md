# AI 通话工作流详解：HyperOS 3 CN vs HyperOS 4 CN

整理时间：2026-11-08  
配套差异文档：`docs/AI_CALL_ASSISTANT_HYPEROS3_VS_HYPEROS4.md`  
本文只讲“流程”：谁发起、经过哪些类/广播/Provider、在什么条件下接管、失败后走哪条路。

## 0. 术语与基线

| 术语 | 含义 |
|---|---|
| V3 | HyperOS 3 CN，Xiaomi 13 Ultra `ishtar`，`OS3.0.308.0.WMACNXM`，Android 16 |
| V4 | HyperOS 4 CN，Xiaomi 17 Pro Max `popsicle`，`OS4.0.6.0.XPBCNXM`，Android 17 |
| 旧引擎 | `com.xiaomi.aiasst.service`（`InCallAiServiceImpl` / `CallScreen`） |
| 新引擎 | `com.miui.voiceassist` 的 `com.aios.osbot.tools.phone`（`OsbotInCallService` / MiClaw） |
| 配置中心 | V4 `com.xiaomi.phone` 的 `ai-assist-call-config` / `ai_call_config.db` |
| AI 按钮 | InCallUI 通话卡片上的“AI 通话”入口 |

> 说明：V3 的 CN 版本本身就有旧 AI 通话全套流程；V4 不是“从无到有”，而是“旧引擎 + 新引擎 + 配置中心 + 跨应用门禁”的并行架构。本文中的 V4 自动接听细节有一部分来自 `classes28.dex` 字节码（`onCallAdded$1` jadx 未能完整反编译），会标注 `[BYTECODE]`。

---

## 1. V3 工作流：单旧引擎

### 1.1 参与组件

| 层 | 组件 | 职责 |
|---|---|---|
| UI/入口 | `com.android.incallui.CallCardFragment` | AI 按钮点击、自动接听触发 |
| 入口协调 | `com.android.incallui.aiassistant.AiAssistantHelper` | `enterAi()`、`onIncoming()`、模式对话框、状态检查 |
| 入口工具 | `com.android.incallui.aiassistant.AiAssistantUtils` | 广播 `use_ai_call`、查询 AI 状态、`awakeAi` |
| 广播接收 | `com.xiaomi.aiasst.service.aicall.receiver.UseAiCallReceiver` | 处理 `use_ai_call` / `use_ai_call_dial` / `exit_ai_call` |
| 自动接听 | `com.xiaomi.aiasst.service.aicall.receiver.PhoneRingReceiver` + `AutoPickupManager` | `incall_ring` → 防骚扰/未知号码/延时 → 自动接听 |
| 会话服务 | `com.xiaomi.aiasst.service.aicall.incall.InCallAiCallService` | 启动 CallScreen、执行接听/拨号动作 |
| Telecom 服务 | `com.xiaomi.aiasst.service.aicall.incall.InCallAiServiceImpl` | 旧 `InCallService`，注册通话回调 |
| 会话 UI | `CallScreenActivity` / `CallScreenService` | AI 通话界面、ASR/TTS/回复 |
| 状态 Provider | `com.xiaomi.aiasst.service.aicall.provider.AICallProvider` | `GET_AICALL_AVAILABLE` 等 |
| 联系人 | `com.android.contacts` `SystemCompat` | 状态查询、旧记录详情入口 |
| 记录详情 | `ContactDetailAICallLogActivity` / `AtyFragment` | 旧 AI 通话记录详情 |

### 1.2 入口可见性：`isShowAiAssistant`

V3 `AiAssistantHelper.isShowAiAssistant(AiStatus, boolean)` 的条件：

1. `AiAssistantUtils.isAiAvailable(aiStatus)`；
2. 唯一通话（`uniqueCall()`）；
3. 非视频、非会议；
4. `isIncomingCall(firstCall) || supportAiDial(...)`；
5. 通话状态允许；
6. 非视频彩铃、无视频请求；
7. 非老人模式；
8. 非中继/非离线/非反诈；
9. 非卫星、非副屏、非锁屏。

V3 没有“新引擎/旧引擎”分支，也没有 `supportXiaoAiAutoAnswer` 条件。

### 1.3 手动入口（V3）

```
用户点击 AI 按钮
  -> CallCardFragment.onAiCallClick()
     -> 高温检查（isHighTemperatureProtect）
     -> AiAssistantHelper.enterAi()
        -> AiAssistantUtils.getAiStatus(...)
           -> AICallProvider.call("GET_AICALL_AVAILABLE")
              -> Bundle KEY_STATUS_CODE
        -> status.mCode != 1:
              Toast(status.mMessage)，结束
        -> status.mCode == 1:
              if AiAssistantUtils.disableAiDialog(ctx)
                 -> AiAssistantUtils.startAiCall(ctx, firstCall, 0)
              else if supportAiMode(ctx)
                 -> showAiModeDialog(firstCall)
                    -> 用户选模式 i
                    -> AiAssistantUtils.startAiCall(ctx, firstCall, i)
              else
                 -> showAiDialog(firstCall)
                    -> 用户确认
                    -> AiAssistantUtils.startAiCall(ctx, firstCall, 0)
```

`AiAssistantUtils.startAiCall`：

```java
intent.setPackage("com.xiaomi.aiasst.service");
intent.setAction(
    isIncomingCall(call)
      ? "com.xiaomi.aiasst.service.use_ai_call"
      : "com.xiaomi.aiasst.service.use_ai_call_dial"
);
intent.putExtra("incoming_number", number);
if (mode > 0) intent.putExtra("ai_call_mode", mode);
sendBroadcast(intent);
```

`disableAiDialog` = `getAiVersionCode(ctx) >= 5`；版本来自 aiasst meta `support_incallui_place`。V3 aiasst 6.1.6 通常已满足，因此常见路径是 **直接 startAiCall，不弹模式框**。

### 1.4 旧引擎接收广播（V3）

`UseAiCallReceiver.onReceive`：

| Action | 方法 | 行为 |
|---|---|---|
| `use_ai_call` | `h(context, intent)` | 来电手动接管 |
| `use_ai_call_dial` | `i(context, intent)` | 去电手动接管 |
| `exit_ai_call` | `b(intent)` | 退出 AI 通话，返回真实通话 |
| `set_incall_voice_control` | `e(context, intent)` | 更新通话中语音控制开关 |

`h()` 的关键分支：

1. 取 `incoming_number`、`ai_call_mode`；
2. 如果悬浮窗正在显示 → `C0517x` 走悬浮窗；
3. 否则 `C0517x().Q(...)` 或 `InCallAiCallService.C(...)`：
   - 有 mode：`InCallAiCallService.C(ctx, number, mode)`；
   - 无 mode：`C0517x().O(ctx, 2, number)`；
4. 打点 `manualpickup`，记录 mode。

`i()` 去电类似，最终 `InCallAiCallService.D(...)`。

### 1.5 自动接听（V3）

```
AI 来电
  -> InCallUI AiAssistantHelper.onIncoming(call)
     -> AiAssistantUtils.awakeAi(ctx, call)
        -> getAiStatus(...) // GET_AICALL_AVAILABLE
        -> isAiAvailable && !RelayUtils.getIPhoneCall(call)
        -> Settings.Global "com.xiaomi.aiasst.call.autoAnswer" != 0 ?
             sendBroadcast("com.xiaomi.aiasst.service.incall_ring", number)
           : 结束
  -> aiasst.PhoneRingReceiver
     -> new C0464b0().b(number)
        -> 2 秒延时（必要时）
        -> AutoPickupManager.onPhoneRing()
           -> 漫游检查
           -> canNotAutoPickup() 检查
           -> planPreventMissCalls()
           -> planAntiSpam()
              -> 命中防骚扰/未知号码策略后延时
              -> InCallAiCallService.i(ctx, number)
                 -> ACTION_AUTO_PICKUP_USE
  -> InCallAiCallService.onStartCommand
     -> ACTION_AUTO_PICKUP_USE:
          if TelephonyUtil.i(ctx)
             -> r(ctx, number, "MODE_AUTO_ANSWER")
  -> CallScreen
```

要点：

- V3 的自动接听开关是 **`Settings.Global com.xiaomi.aiasst.call.autoAnswer`**，不是 Provider 配置。
- `AutoPickupManager` 自己处理防骚扰标签、未知号码、延时、网络；`InCallAiCallService.i()` 还会再次检查网络（`G.o/G.q/G.w`）。
- `PhoneRingReceiver` 只处理 `incall_ring`。
- 旧引擎的自动接听配置在 aiasst 自己的 `SettingsSp`/prefs（如 `auto_pickup_toggle`），不在 HyperPhone。

### 1.6 `InCallAiCallService` 的四个入口（V3）

| Static 方法 | Action | 场景 | 后续 |
|---|---|---|---|
| `C(ctx, number, mode)` | `ACTION_USE_AI_CALL` | 来电手动 | `E()` → 隐私/CTA → CallScreen |
| `D(ctx, number, mode)` | `ACTION_USE_AI_CALL_DIAL` | 去电手动 | `F()` → CallScreen |
| `i(ctx, number)` | `ACTION_AUTO_PICKUP_USE` | 自动接听 | `r(..., MODE_AUTO_ANSWER)` |
| `h(ctx, number)` | `ACTION_AM_USE` | AM/自动机模式 | `r(..., MODE_AM)` |

`onStartCommand` 分发：

```java
ACTION_USE_AI_CALL      -> if (TelephonyUtil.i || h) E(...) else stopSelf()
ACTION_AUTO_PICKUP_USE  -> if (TelephonyUtil.i) r(..., "MODE_AUTO_ANSWER")
ACTION_USE_AI_CALL_DIAL -> m(...)
ACTION_AM_USE           -> if (TelephonyUtil.i) r(..., "MODE_AM")
```

### 1.7 旧 `InCallService` 生命周期（V3）

`InCallAiServiceImpl`：

- `onBind()`：直接 `super.onBind()`，无门禁；
- `onCallAdded(call)`：直接 `call.registerCallback(...)`，加入列表，调用内部 `m/f/p` 与 `g.a().p0()`；
- `onCallRemoved`：回调清理；
- 旧 CallScreen 通过 `InCallAiServiceImpl` 的 listener 获取通话状态。

V3 没有“新 InCallUI 存在则跳过”的逻辑。

### 1.8 通话结束与记录（V3）

- `InCallAiServiceImpl` 回调结束；`CallScreenActivity` 退出；
- 通话记录写入系统 CallLog（AI 标记由旧引擎/记录模块处理）；
- 联系人里的 `ContactDetailAICallLogActivity` / `ContactDetailAICallLogAtyFragment` 展示旧 AI 记录；
- `SystemCompat.i(ctx)` → `resolveActivity("com.xiaomi.aiasst.service.contact.aicalllog_detail")` 判断旧详情入口是否存在；
- `DialerMenuDialog` 的 `dialer_menu_aiphone` 按 aiasst 版本显示“AI 通话记录/设置”入口；
- 没有摘要通知链路。

### 1.9 V3 状态机

```
IDLE
  ├─ incoming ringing
  │    ├─ Global autoAnswer=1 -> incall_ring -> AutoPickupManager -> AI_CALLSCREEN
  │    └─ user taps AI -> enterAi -> use_ai_call -> AI_CALLSCREEN
  ├─ outgoing -> user taps AI -> enterAi -> use_ai_call_dial -> AI_CALLSCREEN
  └─ normal call -> InCallUI only

AI_CALLSCREEN
  ├─ ASR/TTS/回复
  ├─ exit_ai_call -> 回到真实通话
  └─ call removed -> END -> CallLog/旧详情
```

---

## 2. V4 工作流：配置中心 + 双引擎

### 2.1 参与组件

| 层 | 组件 | 职责 |
|---|---|---|
| 配置中心 | `com.xiaomi.phone.aicall.AiCallConfigProvider` | `ai_assist_call_config` 读写 |
| 记录中心 | `com.xiaomi.phone.aicall.AiCallDetailProvider` | AI 通话记录/消息 |
| 能力检查 | `com.xiaomi.phone.settings.AiCallStatusProvider` | 账号 + voiceassist meta + CTA |
| 设置页 | `AiCallSetting` / `AiCallVoiceControlSetting` / `AiCallDetailActivity` | 配置 UI、详情 |
| 迁移 | `AiConfigMigrationReceiver` | BOOT 后通知 aiasst 迁移 |
| 入口 UI | `InCallUI.CallCardFragment` | 新/旧分支 |
| 新引擎入口 | `InCallUI.AiAssistantUtils.supportXiaoAiAutoAnswer` | 2 meta 判断 |
| 新引擎配置 | `InCallUI.util.AiCallConfigHelper` | 7 key + observer |
| 新引擎 UI | `com.android.incallui.aiassist.*`（56 类） | `AiAssistCallFragment` 等 |
| 新引擎服务 | `com.miui.voiceassist` `OsbotInCallService` | 自动接听/接管/ASR/TTS |
| 新引擎配置 | `com.aios.osbot.tools.phone.data.AiCallConfigHelper` | `ai-assist-call-config` |
| 新引擎记录 | `AiCallDetailHelper` | `ai-assist-call-detail` |
| 旧引擎 | `com.xiaomi.aiasst.service.InCallAiServiceImpl` | 仍绑定，但受 `A1.O.e` 门禁 |
| 旧引擎门禁 | `A1.O.e` | 3 meta 判断 |
| 摘要 | `TeleService.AiCallSummaryReceiver` | `AI_CALL_SUMMARY` 通知 |
| 联系人 | `Contacts.k3.l` | 新设置/详情入口判断 |

### 2.2 启动与配置迁移

```
BOOT_COMPLETED
  -> HyperPhone.AiConfigMigrationReceiver
     -> Settings.Global "ai_config_migrate_completed" == 1 ?
          return
     -> sendBroadcast(component=aiasst/MiClawSettingsMigrationReceiver,
                      action="com.xiaomi.ai_config_migrate")
        -> MiClawSettingsMigrationReceiver
           -> 校验调用方（uid 1000/1001 或 voiceassist 且签名）
           -> 异步迁移旧设置/快捷方式
```

迁移不是每次通话都跑，只在未完成时触发一次。

### 2.3 兼容性契约：三 meta

V4 的三处 meta 是跨应用隐式协议：

| 应用 | meta | 读取方 | 作用 |
|---|---|---|---|
| `com.miui.voiceassist` | `xiaoai_support_autoanswer=1` | InCallUI、aiasst、HyperPhone status | 新引擎/能力存在 |
| `com.android.contacts` | `support_ai_call=1` | InCallUI、aiasst | 联系人支持 AI 通话 |
| `com.android.incallui` | `incall_ai_assist_answer=1` | aiasst `A1.O.e` | 新 InCallUI 支持 AI 应答 |

注意不对称：

- InCallUI `supportXiaoAiAutoAnswer` 只查 **voiceassist + contacts**；
- aiasst `A1.O.e` 查 **voiceassist + contacts + incallui**。

因此 V4 的常见状态是：InCallUI 认为“可以走新引擎”，同时 aiasst 认为“新 InCallUI 已支持，旧引擎退出”。

### 2.4 来电：三条并行路径

```
Telecom 绑定:
  ├─ com.android.incallui.InCallServiceImpl
  ├─ com.miui.voiceassist.OsbotInCallService   (新)
  └─ com.xiaomi.aiasst.service.InCallAiServiceImpl (旧，受门禁)

InCallUI:
  ├─ AiAssistantHelper.onIncoming(call)
  │    -> awakeAi()
  │       -> if Global autoAnswer != 0 && !supportXiaoAiAutoAnswer
  │            send incall_ring (旧引擎)
  │          else 不发 (新引擎存在时抑制旧 ring)
  │
  └─ AiAssistCallScreeningService.onScreenCall()
       -> 若 InCallState=INCALL 且 ai_call_trigger_answer=true
          -> 拒接第二路来电
          else -> allow

Osbot:
  onCallAdded -> 自动接听/响铃提醒/接管
aiasst:
  onBind/onCallAdded -> if A1.O.e(ctx) true -> 直接跳过
```

### 2.5 新引擎自动接听（V4）

`OsbotInCallService.onCallAdded`：

1. 只处理来电方向（`CallDirection == 0`）；
2. 如果已有活跃通话（state 4）且不是同一通 → 跳过；
3. `currentCall = call`，注册 callbacks 与 observers；
4. 启动协程 `onCallAdded$1`：
   - 归一化号码（去空格/括号/`+86`/`0086`）；
   - `PendingContextStore.getSkipList()` / `getAnsweringList()`；
   - `resolveConfig(call, "ai_call_auto_answer_enabled", "ai_call_auto_answer_enabled_sim2")`；
   - 号码在 skip list → 跳过自动接听；
   - 号码在 answering list → `setupAutoAnswer(call, 0)` 立即接听；
   - 否则读取 `ai_call_auto_answer_delay(_sim2)`；
   - 读取 `ai_call_spam_time(_sim2)`、`ai_call_spam_types(_sim2)`；
   - 防骚扰/黄页命中且非联系人 → 使用 `spam_time` 作为延时；
   - 调用 `setupAutoAnswer(call, delaySeconds)`。
5. 6 秒响铃提醒（`playRingingReminder`）：仅在 ringer normal、call 仍为 RINGING 时 TTS；静音/勿扰跳过。

`setupAutoAnswer`：

```
if (autoAnswerJob != null) cancel
autoAnswerJob = launch {
    log "倒计时: Ns"
    delay(N)
    if (call.state != RINGING) return
    acceptRingingCall(markAiManaged = true)
}
```

`acceptRingingCall(true)`：

1. `moreReplyClicked` 为 true → 不接听；
2. 无网络 → 不接听；
3. 刷新小米账号状态；未登录 → 不接听；
4. `TelecomManager.acceptRingingCall(0)`；
5. 失败回退 `call.answer(0)`；
6. 成功且 `markAiManaged`：
   - `autoAnsweredByAi = true`
   - `aiFullAssist = true`
   - `putConfig("ai_call_trigger_answer", "true")`

> `[BYTECODE]` skip/allow 列表、spam 延时分支来自 `classes28.dex` 的 `onCallAdded$1`；jadx 未完整还原该方法，但方法调用与字符串常量可确认上述分支。

### 2.6 新引擎手动接管（V4）

#### 2.6.1 响铃中点击 AI

```
CallCardFragment.onAiCallClick()
  -> supportXiaoAiAutoAnswer(ctx) == true
     -> Utils.isNetworkAvailable()? 否 -> Toast 弱网，结束
     -> showAiCallModeDialog()
        -> AiCallModeDialog（AI 自动回复 / 文字回复）
        -> startAiCall(textReplyMode)
           -> write ai_call_manual_reply_mode=false
           -> Utils.setAiCallAutoTraggerAnswerEnabled("true")
              -> write ai_call_trigger_answer=true
           -> notifyAiCallStateChanged(true)
           -> showAiAssistCallFragment(true)
           -> 文字回复模式 2s 后补写 manual_reply_mode=true
  -> supportXiaoAiAutoAnswer(ctx) == false
     -> 高温检查
     -> AiAssistantHelper.enterAi()（旧路径）
```

Osbot `registerTriggerAnswerObserver` 收到 `true`：

- `call != null && call.state == 2`（RINGING）且 `!autoAnsweredByAi`
  → `setupAutoAnswer(call, 0)` 立即接听；
- 后续 `acceptRingingCall(true)` 标记 managed，`onCallActive()` 创建引擎。

#### 2.6.2 通话已接通（state 4）时点击 AI

Osbot observer 分支：

| trigger | call state | 其他条件 | 动作 |
|---|---|---|---|
| `true` | 2 RINGING | `!autoAnsweredByAi` | 立即接听 |
| `true` | 4 ACTIVE | `!wasAiManaged` | `initAiAssistFresh()` → `takeOverCallWithAi()` |
| `true` | 4 ACTIVE | `wasAiManaged && isAiAssistPaused` | `resumeAiAssist()` |
| `false` | any | `wasAiManaged && !isCallEnding` | `pauseAiAssist()` |

`takeOverCallWithAi` 的启动顺序：

1. `shouldContinueAiAssist("start takeover")`；
2. `clearAllConfigs()`；
3. `migrateLegacyReplyRules()`；
4. 解析号码/联系人名/黄页标签；
5. 创建 `CallConversationEngine`；
6. 开始录音/ASR/TTS/会话；
7. `startCallCaptureWithAsr(...)`、`startSilenceTimer()`。

#### 2.6.3 旧引擎回退

如果 `supportXiaoAiAutoAnswer` 为 false：

```
AiAssistantHelper.enterAi()
  -> getAiStatus() // 仍走 aiasst AICallProvider
  -> status != 1 -> Toast
  -> startAiCall broadcast use_ai_call/use_ai_call_dial
     -> aiasst.UseAiCallReceiver
        -> InCallAiCallService / CallScreen
```

但在 V4 正常状态下，`A1.O.e` 为 true，`InCallAiServiceImpl.onBind/onCallAdded` 会跳过，因此旧回退会失败。这就是 `AiCallLegacyAiasst` 插件存在的原因：把 `A1.O.e` 固定为 false。

### 2.7 新引擎会话状态机

```
RINGING
  ├─ auto answer countdown -> acceptRingingCall(true) -> ACTIVE(AI)
  ├─ manual trigger (state 2) -> setupAutoAnswer(0) -> ACTIVE(AI)
  └─ user answers normally -> ACTIVE(non-AI) -> no engine

ACTIVE(AI)
  ├─ onCallActive -> create engine -> playGreeting/ASR/TTS
  ├─ manual reply mode -> isManualReplyMode=true -> silence timeout/drain skipped
  ├─ trigger=false & wasAiManaged -> pauseAiAssist
  ├─ trigger=true & wasAiManaged & paused -> resumeAiAssist
  └─ call removed -> cleanup -> record/report/summary

ACTIVE(non-AI)
  └─ user taps AI (state 4) -> trigger=true -> takeOverCallWithAi -> ACTIVE(AI)
```

会话中的配置观察：

| key | 作用 |
|---|---|
| `ai_call_assist_reply` | 推荐回复词 |
| `ai_call_dtmf_reply` | DTMF 回复 |
| `ai_call_manual_reply_mode` | 手动回复模式；true 时暂停自动回复/静音计时 |
| `ai_call_more_reply_clicked` | 用户点击“更多” |
| `ai_call_weak_network` | 弱网提示/策略 |
| `ai_call_trigger_answer` | 接管/暂停/恢复总开关 |

`onSilenceTimeout` 在 `isCallEnding`、`isAiAssistPaused`、`isManualReplyMode` 时跳过；否则按 ASR 活动/状态机继续。

### 2.7.1 时序参数

| 参数 | V3 | V4 |
|---|---|---|
| 自动接听延时 | `AutoPickupManager` 的防骚扰/未知号码延时 + `ai_call` 配置 | `ai_call_auto_answer_delay`，默认 20s；allow list 为 0；防骚扰命中用 `spam_time` |
| 响铃提醒 | 未见独立 TTS 提醒（[INFERENCE]） | `playRingingReminder` 在 6s 后（静音/勿扰跳过） |
| 手动接管 | 广播后 `InCallAiCallService` 启动 CallScreen | 写 trigger 后 observer 立即 `setupAutoAnswer(call, 0)`；已接通则 `takeOverCallWithAi` |
| 接听前检查 | 网络/防骚扰/漫游 | 更多回复按钮、网络、小米账号 |

### 2.8 通话结束、记录、摘要

`OsbotInCallService.onCallRemoved`：

- 非当前通话 → 跳过；
- `!wasAiManaged` → 普通通话，写 `trigger=false`，结束；
- `wasAiManaged`：
  1. `isCallEnding=true`；
  2. 取 `engine`、`currentRecordId`；
  3. `cleanup()`、`setAiCallMute(false)`、`abandonAudioFocus()`；
  4. 保存 `recordId`；
  5. 异步生成 post-call report；
  6. `showCallScenarioToast(report, contactCallLogId)`：
     - 构造 `Intent("miui.intent.action.AI_CALL_SUMMARY")`；
     - 目标 `com.android.phone/.settings.AiCallSummaryReceiver`；
     - extras：`contactCallLogId`、`ai_summary`、`ai_summary_title`；
     - `sendBroadcast`。

`TeleService.AiCallSummaryReceiver`：

1. 读 extras；
2. `ai_call_summary_result` 打点（success/no_info_skip/failed_retryable）；
3. 发通知 channel `ai_call_summary`；
4. 点击 → `Intent("miui.intent.action.AiCallDetail")`，package `com.xiaomi.phone`，extra `contactCallLogId`；
5. `AiCallDetailActivity` 打开 → `AiCallDetailProvider` 查询 `records/messages`。

### 2.9 详情链路

```
TeleService 通知 / Contacts 入口
  -> HyperPhone.AiCallDetailActivity
     -> Intent extra contactCallLogId
     -> loadData()
        -> content://ai-assist-call-detail/records?...
           -> AiCallDetailProvider.query()
              -> ai_call_records
        -> content://ai-assist-call-detail/messages?...
              -> ai_call_messages
        -> 音频分段播放
```

Contacts 侧：

- 旧 `ContactDetailAICallLogActivity` 仍存在；
- 新 `k3.l.j/q/r` 可打开 HyperPhone `AiCallDetailActivity` / `AiCallSetting`；
- `k3.l.C(ctx)` 决定 Dialer/Contacts 是否走新入口；
- `k3.l.H(ctx)` 用 `GET_AICALL_AVAILABLE` 判断 AI 状态。

### 2.10 第二路来电

`AiAssistCallScreeningService.onScreenCall`：

```
if (callDirection != incoming) -> allow
else if (InCallState == INCALL && Utils.aiCallAutoTraggerAnswerEnabled())
    -> disallow + reject
else -> allow
```

即：AI 通话进行中（trigger=true），第二路来电被直接拒接；普通通话中不拦截。

### 2.11 入口可见性（V4）

V4 `AiAssistantHelper.isShowAiAssistant` 在 V3 条件基础上增加：

```java
boolean support = AiAssistantUtils.supportXiaoAiAutoAnswer(ctx);
boolean notOutgoing = !support
    || (CallList.getOutgoingCall() == null
        && !Call.State.isMiuiDialing(firstCall.getState()));
```

即：新引擎存在时，**去电/拨号中隐藏 AI 按钮**；来电仍显示。V3 没有这个限制。

### 2.12 双状态源

V4 同时存在两个“AI 可用性”来源：

| 来源 | 调用方 | 检查内容 |
|---|---|---|
| aiasst `AICallProvider.GET_AICALL_AVAILABLE` | InCallUI `getAiStatus` | 旧引擎状态（`KEY_STATUS_CODE` 1/2/3/4） |
| HyperPhone `AiCallStatusProvider` | `AiCallSetting`、InCallUI `queryAiCallCondition` | 小米账号 + voiceassist meta + CTA |

这意味着：

- AI 按钮是否显示，仍由旧引擎状态控制；
- 设置页/条件查询，由 HyperPhone 配置中心控制；
- 两者可能不一致（例如旧引擎可用但新引擎配置/CTA 未就绪）。

---

## 3. V3 vs V4 流程对照

| 步骤 | V3 | V4 |
|---|---|---|
| Telecom 绑定 | InCallUI + aiasst | InCallUI + Osbot + aiasst |
| 自动接听开关 | `Settings.Global com.xiaomi.aiasst.call.autoAnswer` | `ai_call_auto_answer_enabled(_sim2)` |
| 自动接听执行 | `incall_ring` → `AutoPickupManager` → `InCallAiCallService` | Osbot `onCallAdded` → `setupAutoAnswer` → `acceptRingingCall` |
| 自动接听前置 | 网络/防骚扰/未知号码/漫游 | skip/allow list + 防骚扰 + 网络 + 小米账号 |
| 手动入口 | 始终旧 `enterAi()` | `supportXiaoAiAutoAnswer` 分支 |
| 手动模式框 | 旧 `AiModeDialog`（版本>=5 时直接跳过） | 新 `AiCallModeDialog`（AI 自动回复 / 文字回复） |
| 手动控制 | 广播 `use_ai_call(_dial)` | 写 `ai_call_trigger_answer=true` |
| 新引擎接管 | 无 | Osbot observer / `takeOverCallWithAi` |
| 旧引擎绑定 | 直接绑定 | `A1.O.e` 三 meta 为 true 时跳过 |
| 旧引擎回退 | 默认路径 | 仅在新引擎不可用时；且需 `A1.O.e=false` |
| 会话 UI | 旧 CallScreen | `AiAssistCallFragment` + `aiassist/` |
| 配置存储 | aiasst `SettingsSp` | `ai_call_config.db` + Osbot/AiCallConfigHelper |
| 记录详情 | 旧 `ContactDetailAICallLogActivity` | HyperPhone `AiCallDetailActivity` + 旧详情 |
| 摘要 | 无 | TeleService `AI_CALL_SUMMARY` 通知 |
| 第二路来电 | 无特殊处理 | `AiAssistCallScreeningService` 拒接 |
| 旧/新状态源 | 单一 aiasst | 双状态源（aiasst + HyperPhone） |

---

## 4. 失败/回退矩阵

| 条件 | V3 行为 | V4 行为 |
|---|---|---|
| 旧引擎存在，aiasst 版本 >=5 | 直接 `startAiCall` | 仍可作为 fallback，但 `A1.O.e` 可能阻止绑定 |
| 新 InCallUI meta `incall_ai_assist_answer=1` | 不存在 | aiasst `O.e` 为 true → 旧引擎 `onBind/onCallAdded` 跳过 |
| Contacts meta `support_ai_call=1` | 不存在 | InCallUI 新入口条件之一 |
| voiceassist meta `xiaoai_support_autoanswer=1` | 不存在 | InCallUI/aiasst/HyperPhone 条件之一 |
| 新引擎存在但账号未登录 | 不适用 | `acceptRingingCall` 拒绝接听 |
| 新引擎存在但无网络 | 旧引擎 `i()` 也检查网络 | `acceptRingingCall` 拒绝接听 |
| 用户点击“更多回复” | 不适用 | `moreReplyClicked=true` → 拒绝自动接听 |
| 第二路来电 | 无专门拦截 | AI 通话中拒接 |
| 旧引擎 `O.e` 为 true 且无插件 | 不适用 | 旧引擎绑定失败；新引擎接管 |
| 新引擎 Osbot 未部署/被插件熔断 | 不适用 | 新入口可能变“死键”；需 InCallUI 插件回退 |
| HyperPhone provider 被插件返回 null | 不适用 | 新引擎读默认值 → 自动接听/trigger 失效 |

---

## 5. 插件对工作流的作用点

### 5.1 `AiCallLegacyHyperPhone`

- 拦截 `ai-assist-call-config/settings/<key>` 的 3 个 key：
  - `ai_call_auto_answer_enabled`
  - `ai_call_auto_answer_enabled_sim2`
  - `ai_call_trigger_answer`
- 效果：
  - Osbot `onCallAdded` 读不到 enabled → 不自动接听；
  - Osbot trigger observer 读不到 true → 不接管；
  - InCallUI `aiCallAutoAnswerEnabled` 读不到 true → 关闭 InCallUI 侧自动接听判断。
- 不覆盖：delay/spam/voice 等 key。

### 5.2 `AiCallLegacyInCallUI`

- `supportXiaoAiAutoAnswer=false`：
  - 手动入口走旧 `enterAi()`；
  - `onAiCallClick` 不再弹新 `AiCallModeDialog`。
- `aiCallAutoAnswerEnabled=false`：
  - InCallUI 侧自动接听判断关闭；
  - 但 Osbot 仍直接读 provider，需要 HyperPhone 插件配合。
- 不覆盖：`aiCallAutoTraggerAnswerEnabled`。

### 5.3 `AiCallLegacyContacts`

- `k3.l.k=false`：
  - `k3.l.C` 为 false；
  - Dialer/Contacts 回退旧 `aicalllog_detail`/旧设置。
- 不覆盖：`k3.l.H` 的 `GET_AICALL_AVAILABLE`、`j/q/r` 直接 Intent。

### 5.4 `AiCallLegacyAiasst`

- `A1.O.e=false`：
  - 旧 `InCallAiServiceImpl.onBind/onCallAdded` 不再跳过；
  - 旧引擎可正常绑定。
- 需要与 InCallUI/Contacts 插件配合，否则入口仍可能走新引擎。

### 5.5 `AiCallLegacyOsbot`（未部署）

- 目标：`onCallAdded` 返回 null、`onStartCommand` 返回 `START_NOT_STICKY`。
- 当前设备未部署，因此新引擎仍会收到 `onCallAdded`；只是配置/入口被其他插件削弱。

---

## 6. 关键代码锚点

### V3

| 主题 | 文件/行 |
|---|---|
| AI 按钮点击 | `decomp/os3cn_incallui/sources/com/android/incallui/CallCardFragment.java:299-307` |
| 入口协调 | `.../aiassistant/AiAssistantHelper.java:139-170` |
| 自动接听唤醒 | `.../aiassistant/AiAssistantUtils.java:40-60` |
| 状态查询 | `.../aiassistant/AiAssistantUtils.java:87-160` |
| 手动广播 | `.../aiassistant/AiAssistantUtils.java:197-209` |
| 广播接收 | `decomp/os3cn_aiasst/sources/com/xiaomi/aiasst/service/aicall/receiver/UseAiCallReceiver.java:127-241` |
| 自动接听入口 | `.../receiver/PhoneRingReceiver.java` |
| 防骚扰/延时 | `.../pickup/AutoPickupManager.java:443-455` 及以上 |
| 服务分发 | `.../incall/InCallAiCallService.java:606-648` |
| 旧 InCallService | `.../incall/InCallAiServiceImpl.java:235-270` |
| 联系人状态 | `decomp/os3cn_contacts/sources/com/miui/contacts/common/SystemCompat.java:52-65,248-265` |

### V4

| 主题 | 文件/行 |
|---|---|
| 入口分支 | `decomp/os4_incallui/sources/com/android/incallui/CallCardFragment.java:527-544` |
| 手动接管 | 同文件 `:803-833` |
| 新引擎判断 | `.../aiassistant/AiAssistantUtils.java:298-334` |
| 自动接听唤醒 | `.../aiassistant/AiAssistantUtils.java:52-80` |
| 状态/条件 | `.../aiassistant/AiAssistantUtils.java:102-190,207-253` |
| 配置 helper | `.../util/AiCallConfigHelper.java:49-110` |
| 第二路来电 | `.../AiAssistCallScreeningService.java:15-30` |
| Osbot onCallAdded | `decomp/os4_voice_osbot/sources/com/aios/osbot/tools/phone/service/OsbotInCallService.java:7133-7165` |
| Osbot 配置键 | 同文件 `:121-176` |
| Osbot 自动接听 | 同文件 `:4875-4893` |
| Osbot 接听执行 | 同文件 `:2332-2410` |
| Osbot trigger observer | 同文件 `:4504-4530` |
| Osbot 会话启动 | 同文件 `:3514-3530` |
| Osbot 结束/摘要 | 同文件 `:7171-7210`；`:4900-4960` |
| 摘要通知 | `decomp/os4_teleservice_summary/sources/com/android/phone/settings/AiCallSummaryReceiver.java` |
| 旧引擎门禁 | `decomp/os4_aiasst/sources/A1/O.java:25-67` |
| 旧引擎跳过 | `.../aicall/incall/InCallAiServiceImpl.java:236-257` |
| 配置 Provider | `decomp/os4_hp/sources/com/xiaomi/phone/aicall/AiCallConfigProvider.java` |
| 状态 Provider | `.../settings/AiCallStatusProvider.java:34-95` |
| 详情页 | `.../settings/AiCallDetailActivity.java:235-420` |
| 迁移 | `.../AiConfigMigrationReceiver.java:12-33` |

---

## 7. 验证与限制

已验证：

- V3/V4 应用版本、类数、AI 类数、Manifest 条目均由 APK/DEX/AXML 实扫；
- V3 的旧入口、旧引擎、旧自动接听、旧详情均在本机 APK 中找到；
- V4 的 `aiassist/` 包、Provider、Osbot 服务、摘要 receiver 均在设备 APK 中找到；
- V4 `onCallAdded$1` 的 skip/allow/spam/延时分支来自 `classes28.dex` 字节码，已标注 `[BYTECODE]`；
- 插件运行日志来自设备 LSPilot `log/`。

限制：

1. V3/V4 机型与 Android 大版本不同，部分 InCallUI 差异可能来自 AOSP 16→17。
2. `onCallAdded$1` jadx 未能完整反编译，自动接听分支的控制流以字节码为准，未逐指令还原。
3. `ai_sim2_settings` 的写点未定位。
4. `AiCallLegacyOsbot` 未部署，新引擎未被完全熔断。
5. 旧引擎 CallScreen 内部 ASR/TTS 细节未逐方法展开；本文聚焦“入口/接管/自动接听/结束”主链路。
