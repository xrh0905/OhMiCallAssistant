# OhMiCallAssistant

在 HyperOS 4（CN，Android 17）上把 AI 通话链路切回 HyperOS 3 的旧引擎
`com.xiaomi.aiasst.service`，并熔断 HyperOS 4 新增的配置中心 / Osbot(MiClaw) 新引擎。

实现形式：单个 LSPosed 模块（libxposed **API 102**、**静态 scope**、**Hot Reload**）。

远程仓库 `https://github.com/xrh0905/OhMiCallAssistant` 当前返回 HTTP 404（私有或未创建），
本目录是本地工作空间。

## 状态

| 阶段 | 内容 | 状态 |
|---|---|---|
| 0 | 工作空间创建（git 基线、目录与忽略规则、README） | 完成 |
| 0 | 详细实施计划 `PLAN.md` | 完成，待审阅 |
| 1 | Gradle/LSPosed 工程骨架 | 待批准 |
| 2 | 5 个宿主包的 Hook 实现 | 待批准 |
| 3 | 静态 scope + Hot Reload 接线 | 待批准 |
| 4 | 构建、APK 校验、真机验证 | 待批准 |

## 目录

| 路径 | 角色 | 是否入库 |
|---|---|---|
| `docs/` | 逆向结论：HyperOS 3 vs 4 差异、通话工作流 | 入库 |
| `plugins/` | 旧 LSPilot(BeanShell) 插件，作为 Hook 语义参考 | 入库（log/error 忽略） |
| `apk/` | OS3/OS4 基线 APK | 本地保留，忽略 |
| `decomp/` | jadx 反编译源码（证据来源） | 本地保留，忽略 |
| `PLAN.md` | 详细计划（Hook 清单、构建、验证、风险） | 入库 |
| `module/` | LSPosed 模块工程（Phase 1 创建） | 计划入库 |

## 证据基线

| 包 | HyperOS 4 CN 版本 | 反编译目录 |
|---|---|---|
| `com.xiaomi.phone` | 15.0.3 (39) | `decomp/os4_hp`, `decomp/hp_cfg`, `decomp/hp_dbhelper` |
| `com.android.incallui` | 40.01.58 (6034) | `decomp/os4_incallui` |
| `com.android.contacts` | 18.30.00.73 | `decomp/os4_contacts` |
| `com.xiaomi.aiasst.service` | 6.2.2 (2732) | `decomp/os4_aiasst`, `decomp/os4_aiasst_provider` |
| `com.miui.voiceassist` | 8.2.65.4714 | `decomp/os4_voice_osbot`, `decomp/os4_voice_cfg`, `decomp/os4_voice_detail` |

详见 `docs/AI_CALL_ASSISTANT_HYPEROS3_VS_HYPEROS4.md` 与 `docs/AI_CALL_WORKFLOW_V3_V4.md`。

## 使用边界

- 仅面向 HyperOS 4 CN（上述版本）设计；类/方法缺失时记录日志并跳过，不崩溃。
- 不改 APK、不改数据库、不做系统属性伪装；只 Hook HyperOS 4 应用进程内的判断方法。
- 与 LSPilot 的同名插件互斥，不要同时启用。
