# 真机验证步骤（HyperOS 4）

设备基线：`2936262d` / popsicle / OS4.0.6.0.XPBCNXM / Android 17
LSPosed：v2.2.1 (7912)，API 102

## 正确顺序

1. 安装/更新 `module/build/outputs/apk/release/module-release.apk`。
2. LSPosed 启用 `OhMiCall`（静态 scope：5 个宿主）。
3. **确认当前没有响铃/通话**，然后重启设备，或运行：
   ```powershell
   pwsh scripts/restart-hosts.ps1
   ```
   脚本会先检查 `dumpsys telecom`，有 `RINGING/ACTIVE/...` 时拒绝执行。
4. 确认日志：
   ```powershell
   adb logcat -d -s OhMiCall
   ```
   期望每个宿主出现 `module_loaded`、`route`、`hook_installed`，触发后出现 `hook_hit`。
5. 设置/确认旧引擎自动接听：
   ```powershell
   adb shell settings get global com.xiaomi.aiasst.call.autoAnswer
   ```
   测试时可设为 `1`：
   ```powershell
   adb shell settings put global com.xiaomi.aiasst.call.autoAnswer 1
   ```
6. 用另一台手机拨入，等待旧引擎自动接听。

## 禁止事项

**不要在响铃/通话中 `force-stop` 以下任一包：**

```text
com.android.incallui
com.xiaomi.aiasst.service
com.miui.voiceassist
com.xiaomi.phone
com.android.contacts
```

`onCallAdded` 只发生一次。响铃中杀掉 InCallUI/aiasst/Osbot 后，Telecom 会重新绑定，
但本次通话的 `awakeAi` / `incall_ring` / 自动接听触发已丢失，表现为：

- 电话一直响，不自动接听；
- 最终对端挂断，Telecom 记录 `MISSED` / `INCOMING_MISSED`；
- 日志中没有 `hook_hit`（因为触发点已经错过）。

## 2026-11-08 实测对照

| 通话 | 操作 | 结果 |
|---|---|---|
| TC@1 20:09:43 | 响铃中（20:09:57）执行了 5 包 force-stop | InCallUI/aiasst/Osbot 被杀；20:10:04 对端挂断，`MISSED` / `INCOMING_MISSED` / `CODE_USER_TERMINATED_BY_REMOTE` |
| TC@2 20:11:06 | 无 force-stop，模块已就绪 | `aiasst.app_info_meta` 命中；旧 `InCallAiServiceImpl.onBind/onCallAdded` 执行；`Awake ai assistant.`；`incall_ring`；20:11:26 ACTIVE，随后正常挂断 |

InCallUI 没有 `am_crash`：TC@1 结束后于 20:10:13 被系统以 `umms_pressure` 回收，
Telecom 侧 `isCrashed: false`。

## 热重载

模块覆盖安装时 `autoHotReload=true`，日志应出现：

```text
event=hot_reloading
event=hot_reloaded replaced=N
```

热重载不会重放 `onPackageReady`；若某宿主没有已安装 Hook，需要在该宿主空闲时重启。
仍不要在通话中重启宿主。
