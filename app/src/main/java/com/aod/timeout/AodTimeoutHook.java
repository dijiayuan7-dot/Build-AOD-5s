package com.aod.timeout;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.AndroidAppHelper;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * SystemUI LSPosed hook. Notification pulse states are deliberately excluded.
 * Timer starts once on entry to the AOD state family and is not restarted by
 * transitions between AOD substates.
 */
public final class AodTimeoutHook implements IXposedHookLoadPackage {
    private static final String TAG = "[AOD-5S] ";
    private static final long TIMEOUT_MS = 5_000L;
    private static final String MACHINE = "com.android.systemui.doze.DozeMachine";
    private static final String STATE = "com.android.systemui.doze.DozeMachine$State";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<Object, Runnable> TIMERS =
            Collections.synchronizedMap(new WeakHashMap<Object, Runnable>());

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.android.systemui".equals(lpparam.packageName)) return;

        try {
            final Class<?> stateClass = XposedHelpers.findClass(STATE, lpparam.classLoader);
            XposedHelpers.findAndHookMethod(MACHINE, lpparam.classLoader,
                    "transitionTo", stateClass, int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    final Object machine = param.thisObject;
                    final Object current;
                    try {
                        current = XposedHelpers.callMethod(machine, "getState");
                    } catch (Throwable t) {
                        log("getState failed", t);
                        return;
                    }

                    final String stateName = String.valueOf(current);
                    if (isAodState(stateName)) {
                        startTimerIfNeeded(machine, stateClass);
                    } else {
                        cancelTimer(machine);
                    }
                }
            });
            log("hooked DozeMachine.transitionTo(State, int)");
        } catch (Throwable t) {
            log("hook install failed", t);
        }
    }

    private static boolean isAodState(String state) {
        // Deliberately exclude DOZE_PULSING*, DOZE_REQUEST_PULSE and pulse UI.
        return "DOZE_AOD".equals(state)
                || "DOZE_AOD_DOCKED".equals(state)
                || "DOZE_AOD_MINMODE".equals(state)
                || "DOZE_AOD_PAUSED".equals(state)
                || "DOZE_AOD_PAUSING".equals(state);
    }

    private static void startTimerIfNeeded(final Object machine, final Class<?> stateClass) {
        synchronized (TIMERS) {
            if (TIMERS.containsKey(machine)) return;

            Runnable task = new Runnable() {
                @Override public void run() {
                    synchronized (TIMERS) { TIMERS.remove(machine); }
                    try {
                        Application app = AndroidAppHelper.currentApplication();
                        if (app == null) {
                            log("no SystemUI application; skip timeout");
                            return;
                        }
                        PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
                        if (pm == null || pm.isInteractive()) {
                            log("display interactive; do not force DOZE");
                            return;
                        }

                        Object current = XposedHelpers.callMethod(machine, "getState");
                        String name = String.valueOf(current);
                        if (!isAodState(name)) {
                            log("state changed before timeout: " + name);
                            return;
                        }

                        Object doze = XposedHelpers.getStaticObjectField(stateClass, "DOZE");
                        // The inspected SystemUI DEX declares requestState(State).
                        XposedHelpers.callMethod(machine, "requestState", doze);
                        log("5 seconds elapsed; requested DOZE from " + name);
                    } catch (Throwable t) {
                        log("timeout action failed", t);
                    }
                }
            };
            TIMERS.put(machine, task);
            MAIN.postDelayed(task, TIMEOUT_MS);
            log("AOD timer started (5000ms)");
        }
    }

    private static void cancelTimer(Object machine) {
        synchronized (TIMERS) {
            Runnable old = TIMERS.remove(machine);
            if (old != null) {
                MAIN.removeCallbacks(old);
                log("AOD timer cancelled: left AOD state family");
            }
        }
    }

    private static void log(String message) {
        XposedBridge.log(TAG + message);
    }

    private static void log(String message, Throwable t) {
        XposedBridge.log(TAG + message + ": " + t);
    }
}
