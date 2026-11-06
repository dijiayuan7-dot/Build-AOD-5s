# AOD 5秒自动关闭（LSPosed）

## 功能
- 仅在 `com.android.systemui` 中运行。
- 进入 `DOZE_AOD` / `DOZE_AOD_DOCKED` / `DOZE_AOD_MINMODE` 等 AOD 状态时开始一次 5000ms 计时。
- AOD 子状态之间切换不会重新计时。
- 超时后若设备仍非交互状态且仍处于 AOD 状态，则调用 `DozeMachine.requestState(DOZE)`。
- 明确排除 `DOZE_PULSING*`、`DOZE_REQUEST_PULSE` 等通知脉冲状态。

## 针对性核对
本项目按用户提供的 SystemUI.apk 中 DEX 方法表编写，确认存在：
- `DozeMachine.transitionTo(DozeMachine.State, int)`
- `DozeMachine.getState()`
- `DozeMachine.requestState(DozeMachine.State)`
- `DozeMachine.State.DOZE`

## 编译
需要 Android SDK（compileSdk 35）及可联网的 Gradle 环境：

```bash
gradle :app:assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/`。

## 安装
1. 编译 APK 并安装。
2. 在 LSPosed 中启用模块，作用域只勾选 System UI（`com.android.systemui`）。
3. 重启 SystemUI 或重启手机。
4. 查看 LSPosed 日志中的 `[AOD-5S]`。

## 注意
这是针对当前上传的 SystemUI DEX 方法签名编写的源码工程，不代表已在目标手机运行验证。不同厂商对 DOZE 状态转换可能有额外限制；若请求 DOZE 未能熄灭屏幕，需要结合运行日志继续适配。
