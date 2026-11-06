package com.aod.timeout;

import android.os.Handler;
import android.os.Looper;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class AodTimeoutHook implements IXposedHookLoadPackage {

    private static final String TAG = "AOD5S";
    private static final long TIMEOUT = 5000L;

    private static final Handler handler =
            new Handler(Looper.getMainLooper());

    private static Runnable timeoutRunnable;

    @Override
    public void handleLoadPackage(
            XC_LoadPackage.LoadPackageParam lpparam) {

        if (!"com.android.systemui".equals(lpparam.packageName)) {
            return;
        }

        try {
            Class<?> dozeClass = XposedHelpers.findClassIfExists(
                    "com.android.systemui.doze.DozeMachine",
                    lpparam.classLoader
            );

            if (dozeClass == null) {
                XposedBridge.log(TAG + ": DozeMachine not found");
                return;
            }

            XposedBridge.hookAllMethods(
                    dozeClass,
                    "transitionTo",
                    new XC_MethodHook() {

                        @Override
                        protected void afterHookedMethod(
                                MethodHookParam param) {

                            if (param.args == null ||
                                    param.args.length < 1 ||
                                    param.args[0] == null) {
                                return;
                            }

                            String state = param.args[0].toString();

                            handler.removeCallbacksAndMessages(null);

                            if (!"DOZE_AOD".equals(state)) {
                                return;
                            }

                            timeoutRunnable = () -> {
                                try {
                                    Object currentState =
                                            XposedHelpers.callMethod(
                                                    param.thisObject,
                                                    "getState"
                                            );

                                    if (!"DOZE_AOD".equals(
                                            String.valueOf(currentState))) {
                                        return;
                                    }

                                    Object dozeState = null;

                                    for (Object item :
                                            currentState.getClass()
                                                    .getEnumConstants()) {
                                        if ("DOZE".equals(item.toString())) {
                                            dozeState = item;
                                            break;
                                        }
                                    }

                                    if (dozeState != null) {
                                        XposedHelpers.callMethod(
                                                param.thisObject,
                                                "requestState",
                                                dozeState
                                        );

                                        XposedBridge.log(
                                                TAG + ": AOD timeout, request DOZE"
                                        );
                                    }

                                } catch (Throwable e) {
                                    XposedBridge.log(
                                            TAG + ": timeout error " +
                                                    e.getMessage()
                                    );
                                }
                            };

                            handler.postDelayed(
                                    timeoutRunnable,
                                    TIMEOUT
                            );

                            XposedBridge.log(
                                    TAG + ": AOD detected, timer started"
                            );
                        }
                    }
            );

            XposedBridge.log(TAG + ": SystemUI hook installed");

        } catch (Throwable e) {
            XposedBridge.log(TAG + ": hook failed " + e);
        }
    }
}
