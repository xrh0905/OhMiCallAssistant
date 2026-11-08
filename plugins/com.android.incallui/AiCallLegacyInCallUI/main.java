// ============================================================================
//  AiCallLegacyInCallUI  —  LSPilot BSH 插件        宿主: com.android.incallui
//  v1.0.0
//
//  作用: 把 InCallUI 的「AI通话」入口从新引擎(com.miui.voiceassist /
//        com.aios.osbot.tools.phone.service.OsbotInCallService)
//        切回旧引擎(com.xiaomi.aiasst.service)。
//
//  依据 (HyperOS 4.0.6 / InCallUI 40.01.58, 反编译件见 work/aicall/incallui_dex):
//    CallCardFragment.onAiCallClick()
//      if (AiAssistantUtils.supportXiaoAiAutoAnswer(ctx))   // 新引擎
//          -> showAiCallModeDialog() -> CallCardFragment.startAiCall(Z)
//             -> AiCallConfigHelper.write("ai_call_trigger_answer","true")
//             -> voiceassist OsbotInCallService 接管
//      else                                                 // 旧引擎
//          -> AiAssistantHelper.enterAi()
//             -> AiModeDialog -> AiAssistantUtils.startAiCall(ctx, call, mode)
//             -> broadcast com.xiaomi.aiasst.service.use_ai_call(_dial)
//
//  因此只需一个 hook: supportXiaoAiAutoAnswer -> false。
//  (isShowAiAssistant 里该值 false 时条件仍然通过, AI 按钮不会消失。)
//
//  另: Utils.aiCallAutoAnswerEnabled() 读 ai_call_auto_answer_enabled,
//  是新引擎「自动接听」的门; 这里一起置 false。旧自动接听由 aiasst 的
//  auto_pickup_toggle 控制, 与本插件无关。
// ============================================================================

HOST = hostContext.getPackageName();
log("[AiCallLegacy] host=" + HOST);

if (!"com.android.incallui".equals(HOST)) {
    log("[AiCallLegacy] 宿主不是 com.android.incallui, 跳过");
    return;
}

resolveClass(name) {
    c = null;
    try { c = Class.forName(name); } catch (Throwable t1) { }
    if (c == null) { try { c = hostLoader.loadClass(name); } catch (Throwable t2) { } }
    if (c == null) { try { c = findClass().name(name).single(); } catch (Throwable t3) { } }
    return c;
}

findMethod1(clazz, name) {
    if (clazz == null) { return null; }
    for (m : clazz.getDeclaredMethods()) {
        if (m.getName().equals(name) && m.getParameterTypes().length == 1) {
            m.setAccessible(true);
            return m;
        }
    }
    return null;
}

findMethod0(clazz, name) {
    if (clazz == null) { return null; }
    for (m : clazz.getDeclaredMethods()) {
        if (m.getName().equals(name) && m.getParameterTypes().length == 0) {
            m.setAccessible(true);
            return m;
        }
    }
    return null;
}

// 1) 新/旧引擎总开关: supportXiaoAiAutoAnswer(Context) -> false
u = resolveClass("com.android.incallui.aiassistant.AiAssistantUtils");
if (u == null) {
    log("[AiCallLegacy] 找不到 AiAssistantUtils");
} else {
    m = findMethod1(u, "supportXiaoAiAutoAnswer");
    if (m == null) {
        log("[AiCallLegacy] 找不到 supportXiaoAiAutoAnswer(Context)");
    } else {
        hookBefore(m, "aicall_legacy_flag", param -> {
            try { param.skipWith(Boolean.FALSE); } catch (Throwable t) { }
        });
        log("[AiCallLegacy] hooked supportXiaoAiAutoAnswer -> false");
    }
}

// 2) 新引擎自动接听: Utils.aiCallAutoAnswerEnabled() -> false
ut = resolveClass("com.android.incallui.util.Utils");
if (ut == null) {
    log("[AiCallLegacy] 找不到 Utils");
} else {
    m2 = findMethod0(ut, "aiCallAutoAnswerEnabled");
    if (m2 == null) {
        log("[AiCallLegacy] 找不到 aiCallAutoAnswerEnabled()");
    } else {
        hookBefore(m2, "aicall_legacy_autoanswer", param -> {
            try { param.skipWith(Boolean.FALSE); } catch (Throwable t) { }
        });
        log("[AiCallLegacy] hooked aiCallAutoAnswerEnabled -> false");
    }
}

log("[AiCallLegacy] 加载完成");
