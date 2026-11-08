// AiCallScopeProbe — 在宿主 com.xiaomi.phone 进程内，只读探测 AI通话配置实况。
// 目的: 判断"当前版本已无任何问候语代码"的前提下，历史数据是否仍留在本 App 数据里。
plog(m) { try { log("[AiCallProbe] " + m); } catch (Throwable t) { } }

if (!"com.xiaomi.phone".equals(hostContext.getPackageName())) {
    log("[AiCallProbe] 宿主是 " + hostContext.getPackageName() + ", 退出");
    return;
}
dataDir = hostContext.getApplicationInfo().dataDir;
plog("START ver=" + hostVerName + " dataDir=" + dataDir);

// ── 1. databases 目录 ──
try {
    d = new java.io.File(dataDir + "/databases");
    if (d.exists()) {
        fs = d.listFiles();
        for (f : fs) { plog("DBFILE " + f.getName() + " size=" + f.length()); }
    } else { plog("databases 目录不存在"); }
} catch (Throwable t) { plog("DB list 异常: " + t); }

// ── 2. 直读 ai_call_config.db 全表 ──
try {
    db = android.database.sqlite.SQLiteDatabase.openDatabase(
            dataDir + "/databases/ai_call_config.db", null,
            android.database.sqlite.SQLiteDatabase.OPEN_READONLY);
    cur = db.rawQuery("select key, value from config", null);
    n = 0;
    while (cur.moveToNext()) {
        plog("CFG[" + (n++) + "] " + cur.getString(0) + " = " + cur.getString(1));
    }
    cur.close();
    db.close();
    plog("ai_call_config.db 共 " + n + " 行");
} catch (Throwable t) { plog("cfg db 读取异常: " + t); }

// ── 3. 走自家 ContentProvider 再读一遍 (验证外部可见性口径) ──
try {
    cur = hostContext.getContentResolver().query(
            android.net.Uri.parse("content://ai-assist-call-config/settings"), null, null, null, null);
    if (cur == null) { plog("PROV cursor="); }
    else {
        ki = cur.getColumnIndex("key"); vi = cur.getColumnIndex("value");
        while (cur.moveToNext()) { plog("PROV " + cur.getString(ki) + "=" + cur.getString(vi)); }
        cur.close();
    }
} catch (Throwable t) { plog("provider 异常: " + t); }

// ── 4. shared_prefs 全量扫描, 只输出命中"问候/greeting/welcome"或含 ai_call 的文件 ──
try {
    sd = new java.io.File(dataDir + "/shared_prefs");
    fs = sd.listFiles();
    plog("shared_prefs 文件数=" + (fs == null ? -1 : fs.length));
    if (fs != null) {
        for (f : fs) {
            txt = "";
            try { txt = new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8"); }
            catch (Throwable t2) { continue; }
            low = txt.toLowerCase();
            hit = low.contains("greet") || low.contains("welcome") || low.contains("ai_call")
                  || txt.contains("\u95ee\u5019") || txt.contains("\u5f00\u573a");
            if (hit) {
                plog("PREFS " + f.getName() + " len=" + txt.length());
                plog("PREFS_BODY_BEGIN");
                plog(txt);
                plog("PREFS_BODY_END");
            }
        }
    }
} catch (Throwable t) { plog("shared_prefs 异常: " + t); }

// ── 5. 小爱(voiceassist) 安装/版本元数据 (仅 PackageManager 查询) ──
try {
    pi = hostContext.getPackageManager().getPackageInfo("com.miui.voiceassist", 0);
    plog("voiceassist verName=" + pi.versionName + " verCode=" + pi.versionCode);
    ai = hostContext.getPackageManager().getApplicationInfo("com.miui.voiceassist", 128);
    b = ai.metaData;
    plog("voiceassist metaData=" + (b == null ? "" : b.toString()));
    if (b != null) { plog("xiaoai_support_autoanswer=" + b.getInt("xiaoai_support_autoanswer", -999)); }
} catch (Throwable t) { plog("voiceassist 查询异常: " + t); }

plog("DONE");
