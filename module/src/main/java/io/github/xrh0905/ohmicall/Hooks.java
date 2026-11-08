package io.github.xrh0905.ohmicall;

import android.content.Intent;
import android.net.Uri;
import android.telecom.Call;
import android.util.Log;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/** Resolves hook targets and installs them with stable ids. */
final class Hooks {

    static final String PHONE = "com.xiaomi.phone";
    static final String INCALLUI = "com.android.incallui";
    static final String CONTACTS = "com.android.contacts";
    static final String AIASST = "com.xiaomi.aiasst.service";
    static final String VOICEASSIST = "com.miui.voiceassist";

    private static final String APP_PM = "android.app.ApplicationPackageManager";
    private static final String RESOLVE_FLAGS =
            "android.content.pm.PackageManager$ResolveInfoFlags";

    private Hooks() {
    }

    static boolean isTargetPackage(String pkg) {
        return PHONE.equals(pkg)
                || INCALLUI.equals(pkg)
                || CONTACTS.equals(pkg)
                || AIASST.equals(pkg)
                || VOICEASSIST.equals(pkg);
    }

    static void install(ModuleMain module, String pkg, ClassLoader cl) {
        switch (pkg) {
            case PHONE:
                installPhone(module, cl);
                break;
            case INCALLUI:
                installInCallUi(module, cl);
                break;
            case CONTACTS:
                installContacts(module, cl);
                break;
            case AIASST:
                installAiasst(module, cl);
                break;
            case VOICEASSIST:
                installVoiceAssist(module, cl);
                break;
            default:
                break;
        }
    }

    private static void installPhone(ModuleMain module, ClassLoader cl) {
        hook(module, cl,
                "com.xiaomi.phone.aicall.AiCallConfigProvider",
                "query",
                HookTable.PHONE_CONFIG_QUERY,
                Uri.class, String[].class, String.class, String[].class, String.class);
    }

    private static void installInCallUi(ModuleMain module, ClassLoader cl) {
        hook(module, cl,
                "com.android.incallui.aiassistant.AiAssistantUtils",
                "supportXiaoAiAutoAnswer",
                HookTable.INCALLUI_SUPPORT_NEW_ENGINE,
                android.content.Context.class);
        hook(module, cl,
                "com.android.incallui.util.Utils",
                "aiCallAutoAnswerEnabled",
                HookTable.INCALLUI_AUTO_ANSWER);
        hook(module, cl,
                "com.android.incallui.util.Utils",
                "aiCallAutoTraggerAnswerEnabled",
                HookTable.INCALLUI_TRIGGER_ANSWER);
    }

    private static void installContacts(ModuleMain module, ClassLoader cl) {
        hook(module, cl,
                APP_PM,
                "resolveActivity",
                HookTable.CONTACTS_RESOLVE_ACTIVITY,
                Intent.class, int.class);

        // API 33+ overload; resolved by name so API 26-32 devices do not verify the class.
        try {
            Class<?> flags = Class.forName(RESOLVE_FLAGS, false, cl);
            hook(module, cl,
                    APP_PM,
                    "resolveActivity",
                    HookTable.CONTACTS_RESOLVE_ACTIVITY,
                    Intent.class, flags);
        } catch (Throwable t) {
            module.log(Log.INFO, ModuleMain.TAG,
                    "event=overload_absent method=resolveActivity(Intent,ResolveInfoFlags)");
        }
    }

    private static void installAiasst(ModuleMain module, ClassLoader cl) {
        hook(module, cl,
                APP_PM,
                "getApplicationInfo",
                HookTable.AIASST_APP_INFO_META,
                String.class, int.class);
    }

    private static void installVoiceAssist(ModuleMain module, ClassLoader cl) {
        hook(module, cl,
                "com.aios.osbot.tools.phone.service.OsbotInCallService",
                "onCallAdded",
                HookTable.OSBOT_SKIP_CALL_ADDED,
                Call.class);
    }

    private static void hook(ModuleMain module, ClassLoader cl,
                             String className, String methodName, String id,
                             Class<?>... params) {
        Class<?> target;
        try {
            target = Class.forName(className, false, cl);
        } catch (Throwable t) {
            module.log(Log.WARN, ModuleMain.TAG,
                    "event=class_not_found id=" + id + " class=" + className);
            return;
        }

        Method method;
        try {
            method = target.getDeclaredMethod(methodName, params);
            method.setAccessible(true);
        } catch (Throwable t) {
            module.log(Log.WARN, ModuleMain.TAG,
                    "event=method_not_found id=" + id + " class=" + className
                            + " method=" + methodName);
            return;
        }

        XposedInterface.Hooker hooker = HookTable.get(id);
        if (hooker == null) {
            module.log(Log.ERROR, ModuleMain.TAG, "event=hooker_missing id=" + id);
            return;
        }

        try {
            module.hook(method)
                    .setId(id)
                    .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                    .intercept(hooker);
            module.log(Log.INFO, ModuleMain.TAG,
                    "event=hook_installed id=" + id
                            + " target=" + className + "#" + methodName);
        } catch (Throwable t) {
            module.log(Log.ERROR, ModuleMain.TAG, "event=install_failed id=" + id, t);
        }
    }
}
