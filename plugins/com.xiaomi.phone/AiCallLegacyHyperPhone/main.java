// ============================================================================
//  AiCallLegacyHyperPhone  —  LSPilot BSH 插件      宿主: com.xiaomi.phone
//  v1.1.0
//
//  作用: 新 AI 通话引擎的「总闸」。
//
//  证据 (HyperPhone / classes.dex):
//    provider: com.xiaomi.phone/.aicall.AiCallConfigProvider
//      authority = ai-assist-call-config, 路径 settings / settings/*
//      表 config(key TEXT PRIMARY KEY, value TEXT)
//    Osbot (com.miui.voiceassist) 的 AiCallConfigHelper.getConfig(key):
//      content://ai-assist-call-config/settings/<key> -> query 读列 value
//      若 cursor 为 null 直接返回 defaultValue(默认 "")
//    OsbotInCallService.onCallAdded$1 读:
//      ai_call_auto_answer_enabled(_sim2) / ai_call_auto_answer_delay(_sim2)
//      / ai_call_spam_time(_sim2) / ai_call_spam_types(_sim2)
//    InCallUI 写 ai_call_trigger_answer=true 触发新引擎接管。
//
//  做法: hook AiCallConfigProvider.query, 对以下 key 返回 null cursor:
//    ai_call_auto_answer_enabled, ai_call_auto_answer_enabled_sim2,
//    ai_call_trigger_answer
//  调用方拿到 null 即回落默认值 "" -> 与 "true" 比较为 false。
//  不改数据库, 不动旧引擎(aijasst)的 prefs。
//
//  v1.1.0: 不再构造 MatrixCursor/数组(BSH 对 new String[]{} 会报
//          ClassNotFoundException: String), 直接返回 null, 两边调用方都判空。
// ============================================================================

import android.net.Uri;
import android.database.Cursor;

HOST = hostContext.getPackageName();
log("[AiCallPhone] host=" + HOST);

if (!"com.xiaomi.phone".equals(HOST)) {
    log("[AiCallPhone] 宿主不是 com.xiaomi.phone, 跳过");
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

clazz = resolveClass("com.xiaomi.phone.aicall.AiCallConfigProvider");
if (clazz == null) {
    log("[AiCallPhone] 找不到 AiCallConfigProvider");
} else {
    m = findMethod(clazz, "query", 5);
    if (m == null) {
        log("[AiCallPhone] 找不到 query(Uri,String[],String,String[],String)");
    } else {
        hookAfter(m, "aicall_cfg_block", param -> {
            try {
                u = (Uri) param.args[0];
                if (u != null && "ai-assist-call-config".equals(u.getAuthority())) {
                    segs = u.getPathSegments();
                    if (segs != null && segs.size() == 2 && "settings".equals(segs.get(0))) {
                        key = (String) segs.get(1);
                        blocked = "ai_call_auto_answer_enabled".equals(key) || "ai_call_auto_answer_enabled_sim2".equals(key) || "ai_call_trigger_answer".equals(key);
                        if (blocked) {
                            old = param.result;
                            if (old != null) {
                                try { ((Cursor) old).close(); } catch (Throwable t2) { }
                            }
                            param.result = null;
                        }
                    }
                }
            } catch (Throwable t) {
                try { log("[AiCallPhone] cfg hook error: " + t); } catch (Throwable ignore) { }
            }
        });
        log("[AiCallPhone] hooked AiCallConfigProvider.query");
    }
}

log("[AiCallPhone] 加载完成");
