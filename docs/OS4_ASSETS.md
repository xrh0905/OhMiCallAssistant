# HyperOS 4 设备资产（已拉取）

设备：`2936262d` / `popsicle` / `OS4.0.6.0.XPBCNXM` / Android 17 (SDK 37)
框架：LSPosed **v2.2.1 (7912)**，API **102**

拉取时间：2026-11-08。APK 位于 `apk/os4/`（`.gitignore` 已忽略，不入库）。

## APK

| 文件 | 字节 | SHA-256 |
|---|---:|---|
| `Contacts.apk` | 20948675 | `dd8d88b18df206ab4cfd53443055f1e3c4934da53a87ef898a867376b4d16e0c` |
| `HyperPhone.apk` | 40840493 | `665fdbdeae64ae88f701f9211040188431430ab7dbc3196d9bf6f412c99c30b3` |
| `InCallUI.apk` | 9420880 | `06224317a7d0c8f2599fbdde288f4e9226327794fd4ee2eb71d1e8b660417075` |
| `MIUIAiasstService.apk` | 24235034 | `7eb8c4e50c109c43452048fdb5134bf6f15678ec3550be10bcb1328dc8101bdd` |
| `Telecom.apk` | 4346526 | `15df0e46ed9460088a559c42ea5797e53ba13980fc184b8322736c17c74d301c` |
| `TeleService.apk` | 12130331 | `d13fa7b275cae8f90550e34f36fa62b89638b2426221ea0bc0824a8ea2c68935` |
| `VoiceAssist.apk` | 253934030 | `caafb6930e7a9b86c46ea9fc3ab00fbd76c826026fa0f23260f39737b42c8ffa` |

版本：

| 包 | versionName | versionCode |
|---|---|---|
| `com.xiaomi.phone` | 15.0.3 | 39 |
| `com.android.incallui` | 40.01.58 | 6034 |
| `com.android.contacts` | 18.30.00.73 | 300073 |
| `com.xiaomi.aiasst.service` | 6.2.2 | 2732 |
| `com.miui.voiceassist` | 8.2.65.4714 | 508002065 |
| `com.android.phone` | 17 | 37 |
| `com.android.server.telecom` | 17 | 37 |

## Manifest 摘录

`apk/os4/*.manifest.xml` 由 `apkanalyzer manifest print` 生成。

| 包 | 组件 | `android:process` |
|---|---|---|
| `com.android.incallui` | `InCallServiceImpl` | `com.android.incallui`（主进程） |
| `com.xiaomi.aiasst.service` | `InCallAiServiceImpl` / `UseAiCallReceiver` | 未声明 → 主进程 |
| `com.miui.voiceassist` | `OsbotInCallService` | 未声明 → 主进程 |
| `com.xiaomi.phone` | `AiCallConfigProvider` / `AiCallDetailProvider` / `AiCallStatusProvider` | 未声明 → 主进程 |
| `com.android.contacts` | 部分组件 | `:business`；AI 入口判断在主进程 |

## 设备拉取命令

```bash
adb shell pm path com.xiaomi.phone
adb pull /system_ext/priv-app/HyperPhone/HyperPhone.apk apk/os4/HyperPhone.apk

# /data/app 需 root
adb exec-out su -c cat /data/app/.../base.apk > apk/os4/Contacts.apk

# manifest
apkanalyzer manifest print apk/os4/InCallUI.apk > apk/os4/InCallUI.manifest.xml
```
