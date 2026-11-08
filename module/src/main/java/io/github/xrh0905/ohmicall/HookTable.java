package io.github.xrh0905.ohmicall;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedInterface;

/**
 * Stable hook ids and stateless hookers.
 *
 * Every hooker only depends on chain arguments/results, so a new generation can replace
 * old handles by id after hot reload without carrying target classes across generations.
 */
final class HookTable {

    static final String PHONE_CONFIG_QUERY = "phone.config_query";
    static final String INCALLUI_SUPPORT_NEW_ENGINE = "incallui.support_new_engine";
    static final String INCALLUI_AUTO_ANSWER = "incallui.auto_answer_enabled";
    static final String INCALLUI_TRIGGER_ANSWER = "incallui.trigger_answer_enabled";
    static final String CONTACTS_RESOLVE_ACTIVITY = "contacts.resolve_activity";
    static final String AIASST_APP_INFO_META = "aiasst.app_info_meta";
    static final String OSBOT_SKIP_CALL_ADDED = "osbot.skip_call_added";

    private static final String CONFIG_AUTHORITY = "ai-assist-call-config";
    private static final String CONFIG_SETTINGS = "settings";
    private static final String AI_CALL_SETTING_ACTION = "miui.intent.action.AiCallSetting";
    private static final String HYPER_PHONE = "com.xiaomi.phone";
    private static final String AI_CALL_DETAIL =
            "com.xiaomi.phone.settings.AiCallDetailActivity";
    private static final String INCALLUI_PACKAGE = "com.android.incallui";
    private static final String META_AI_ASSIST_ANSWER = "incall_ai_assist_answer";

    private static final Set<String> BLOCKED_CONFIG_KEYS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "ai_call_auto_answer_enabled",
                    "ai_call_auto_answer_enabled_sim2",
                    "ai_call_trigger_answer")));

    private static final Map<String, Boolean> HIT_LOGGED = new ConcurrentHashMap<>();
    private static final Map<String, XposedInterface.Hooker> HOOKERS;

    static {
        Map<String, XposedInterface.Hooker> hooks = new HashMap<>();

        hooks.put(PHONE_CONFIG_QUERY, chain -> {
            Object uriArg = chain.getArg(0);
            if (uriArg instanceof Uri) {
                Uri uri = (Uri) uriArg;
                if (CONFIG_AUTHORITY.equals(uri.getAuthority())) {
                    List<String> segments = uri.getPathSegments();
                    if (segments != null
                            && segments.size() == 2
                            && CONFIG_SETTINGS.equals(segments.get(0))
                            && BLOCKED_CONFIG_KEYS.contains(segments.get(1))) {
                        logHit(PHONE_CONFIG_QUERY);
                        Object projection = chain.getArg(1);
                        String[] columns = projection instanceof String[]
                                ? (String[]) projection
                                : new String[]{"value"};
                        return new MatrixCursor(columns, 0);
                    }
                }
            }
            return chain.proceed();
        });

        hooks.put(INCALLUI_SUPPORT_NEW_ENGINE, chain -> {
            logHit(INCALLUI_SUPPORT_NEW_ENGINE);
            return Boolean.FALSE;
        });

        hooks.put(INCALLUI_AUTO_ANSWER, chain -> {
            logHit(INCALLUI_AUTO_ANSWER);
            return Boolean.FALSE;
        });

        hooks.put(INCALLUI_TRIGGER_ANSWER, chain -> {
            logHit(INCALLUI_TRIGGER_ANSWER);
            return Boolean.FALSE;
        });

        hooks.put(CONTACTS_RESOLVE_ACTIVITY, chain -> {
            Object intentArg = chain.getArg(0);
            if (intentArg instanceof Intent && isHyperPhoneAiIntent((Intent) intentArg)) {
                logHit(CONTACTS_RESOLVE_ACTIVITY);
                return null;
            }
            return chain.proceed();
        });

        hooks.put(AIASST_APP_INFO_META, chain -> {
            Object pkgArg = chain.getArg(0);
            if (INCALLUI_PACKAGE.equals(pkgArg)) {
                Object result = chain.proceed();
                if (result instanceof ApplicationInfo) {
                    ApplicationInfo original = (ApplicationInfo) result;
                    ApplicationInfo copy = new ApplicationInfo(original);
                    Bundle meta = original.metaData == null
                            ? new Bundle()
                            : new Bundle(original.metaData);
                    meta.putInt(META_AI_ASSIST_ANSWER, 0);
                    copy.metaData = meta;
                    logHit(AIASST_APP_INFO_META);
                    return copy;
                }
                return result;
            }
            return chain.proceed();
        });

        hooks.put(OSBOT_SKIP_CALL_ADDED, chain -> {
            logHit(OSBOT_SKIP_CALL_ADDED);
            return null;
        });

        HOOKERS = Collections.unmodifiableMap(hooks);
    }

    private HookTable() {
    }

    static XposedInterface.Hooker get(String id) {
        return id == null ? null : HOOKERS.get(id);
    }

    private static boolean isHyperPhoneAiIntent(Intent intent) {
        if (AI_CALL_SETTING_ACTION.equals(intent.getAction())) {
            return true;
        }
        ComponentName component = intent.getComponent();
        return component != null
                && HYPER_PHONE.equals(component.getPackageName())
                && AI_CALL_DETAIL.equals(component.getClassName());
    }

    private static void logHit(String id) {
        if (HIT_LOGGED.putIfAbsent(id, Boolean.TRUE) == null) {
            Log.i(ModuleMain.TAG, "event=hook_hit id=" + id);
        }
    }
}
