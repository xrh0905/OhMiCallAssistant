// ============================================================================
//  AiCallLegacyAiasst  —  LSPilot BSH 插件      宿主: com.xiaomi.aiasst.service
//  v1.0.0
//
//  作用: 让旧引擎在「新 InCallUI 存在」时仍然绑定自己的 InCallService。
//
//  证据 (aiasst classes2.dex):
//    InCallAiServiceImpl.onBind/onCallAdded:
//      if (A1.O.e(ctx)) { log "InCallUi supports ai assist answer"; return; }
//    A1.O.e(ctx) = g(ctx) && c(ctx) && f(ctx)
//      g: com.miui.voiceassist meta xiaoai_support_autoanswer == 1
//      c: com.android.contacts meta support_ai_call == 1
//      f: com.android.incallui meta incall_ai_assist_answer >= 1   (=1)
//    => 三个 meta 都在时, 旧引擎故意不绑定 InCallService:
//       Telecom onBind 返回 null -> isCrashed -> 20s 自动接听无法应答
//       onCallAdded 跳过 -> 旧 CallScreen 无 presenter -> ErrorHandler code 11
//       ("当前通话你的网络状况不佳"), 打字发送被禁用。
//
//  做法: hook A1.O.e(Context) -> false, 其余逻辑不变。
// ============================================================================

HOST = hostContext.getPackageName();
log("[AiCallAiasst] host=" + HOST);

if (!"com.xiaomi.aiasst.service".equals(HOST)) {
    log("[AiCallAiasst] 宿主不是 com.xiaomi.aiasst.service, 跳过");
    return;
}

resolveClass(name) {
    c = null;
    try { c = Class.forName(name); } catch (Throwable t1) { }
    if (c == null) { try { c = hostLoader.loadClass(name); } catch (Throwable t2) { } }
    if (c == null) { try { c = findClass().name(name).single(); } catch (Throwable t3) { } }
    return c;
}

findMethod(clazz, name, argc) {
    if (clazz == null) { return null; }
    for (m : clazz.getDeclaredMethods()) {
        if (m.getName().equals(name) && m.getParameterTypes().length == argc) {
            m.setAccessible(true);
            return m;
        }
    }
    return null;
}

clazz = resolveClass("A1.O");
if (clazz == null) {
    try { clazz = findClass().usingStrings("InCallUi supports ai assist answer").single(); } catch (Throwable t) { }
    log("[AiCallAiasst] 通过 DexKit 回退查找 A1.O");
}

if (clazz == null) {
    log("[AiCallAiasst] 找不到 A1.O");
} else {
    log("[AiCallAiasst] class=" + clazz.getName());
    m = findMethod(clazz, "e", 1);
    if (m == null) {
        log("[AiCallAiasst] 找不到 e(Context)");
    } else {
        hookBefore(m, "aicall_old_bind", param -> {
            try { param.skipWith(Boolean.FALSE); } catch (Throwable t) { }
        });
        log("[AiCallAiasst] hooked A1.O.e(Context) -> false");
    }
}

log("[AiCallAiasst] 加载完成");
