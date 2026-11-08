package io.github.xrh0905.ohmicall;

import android.util.Log;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam;
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;

/**
 * OhMiCall entry point.
 *
 * Routes package/process callbacks to the matching hook installer and keeps every hook
 * replaceable by a stable id for API 102 hot reload.
 */
public final class ModuleMain extends XposedModule {

    public static final String TAG = "OhMiCall";

    private String processName = "";
    private boolean installed;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        processName = param.getProcessName();
        log(Log.INFO, TAG, "event=module_loaded process=" + processName
                + " api=" + getApiVersion()
                + " framework=" + getFrameworkName()
                + " version=" + getFrameworkVersion());
        if (getApiVersion() < XposedInterface.API_102) {
            log(Log.WARN, TAG, "event=unsupported_api api=" + getApiVersion());
        }
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        String pkg = param.getPackageName();
        if (!Hooks.isTargetPackage(pkg)) {
            return;
        }
        if (!pkg.equals(processName)) {
            log(Log.INFO, TAG, "event=route_skip package=" + pkg + " process=" + processName);
            return;
        }
        if (installed) {
            log(Log.INFO, TAG, "event=install_skipped package=" + pkg);
            return;
        }
        installed = true;
        log(Log.INFO, TAG, "event=route package=" + pkg
                + " process=" + processName
                + " targetSdk=" + param.getApplicationInfo().targetSdkVersion);
        Hooks.install(this, pkg, param.getClassLoader());
    }

    @Override
    public boolean onHotReloading(HotReloadingParam param) {
        log(Log.INFO, TAG, "event=hot_reloading process=" + processName);
        param.setSavedInstanceState(processName);
        return true;
    }

    @Override
    public void onHotReloaded(HotReloadedParam param) {
        int replaced = 0;
        int unhooked = 0;
        for (XposedInterface.HookHandle handle : param.getOldHookHandles()) {
            String id = handle.getId();
            XposedInterface.Hooker hooker = HookTable.get(id);
            try {
                if (hooker != null) {
                    handle.replaceHook(hooker);
                    replaced++;
                } else {
                    handle.unhook();
                    unhooked++;
                }
            } catch (Throwable t) {
                log(Log.WARN, TAG, "event=hot_reload_replace_failed id=" + id, t);
            }
        }
        log(Log.INFO, TAG, "event=hot_reloaded replaced=" + replaced + " unhooked=" + unhooked);
    }
}
