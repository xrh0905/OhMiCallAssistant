// ============================================================================
//  AiCallLegacyContacts  —  LSPilot BSH 插件        宿主: com.android.contacts
//  v1.0.0
//
//  作用: 拨号/联系人里的「AI通话」入口回退旧版。
//
//  证据 (com.android.contacts 18.30.00.73, classes2.dex):
//    k3.l.k(Context)  private static boolean
//        resolveActivity(new Intent("miui.intent.action.AiCallSetting"))
//     k3.l.C(Context)
//        if (!k3.o.V()) && k3.l.k(ctx) && k3.l.w(ctx) -> true
//     调用方据此决定用 HyperPhone 的 AiCallDetailActivity / AiCallSetting,
//     还是回退到 com.xiaomi.aiasst.service.contact.aicalllog_detail。
//
//  做法: hook k(Context) -> false。
// ============================================================================

HOST = hostContext.getPackageName();
log("[AiCallContacts] host=" + HOST);

if (!"com.android.contacts".equals(HOST)) {
    log("[AiCallContacts] 宿主不是 com.android.contacts, 跳过");
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

clazz = resolveClass("k3.l");
if (clazz == null) {
    try { clazz = findClass().usingStrings("findNewAiCallSetting").single(); } catch (Throwable t) { }
    log("[AiCallContacts] 通过 DexKit 回退查找 class");
}

if (clazz == null) {
    log("[AiCallContacts] 找不到 k3.l");
} else {
    log("[AiCallContacts] class=" + clazz.getName());
    m = findMethod(clazz, "k", 1);
    if (m == null) {
        log("[AiCallContacts] 找不到 k(Context)");
    } else {
        hookBefore(m, "aicall_legacy_contacts", param -> {
            try { param.skipWith(Boolean.FALSE); } catch (Throwable t) { }
        });
        log("[AiCallContacts] hooked k(Context) -> false");
    }
}

log("[AiCallContacts] 加载完成");
