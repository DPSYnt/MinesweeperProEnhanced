# MinesweeperEX 运行与安装

当前 Android 版本为 1.1，安装包位于项目根目录 `dist/`。电脑版使用项目脚本运行最新源码；已有 Windows EXE 保留历史 1.0.4，本次未重新生成。

当前源码与电脑脚本版已取消安全格排雷惩罚及所有保证有解算法，并优化电脑鼠标操作；Android 源码已同步规则。已交付的 1.1 APK 文件保持不变，仍包含旧版的安全格误排惩罚和初始数字推理保障，等待新的打包指令。

电脑脚本版与 Android 1.1 共同保留的基础规则包括：普通、困难、地狱、噩梦四档难度；仅剩国王雷时获胜，提前排除或引爆国王立即失败；内鬼核心初始生成和再生避开内鬼区最外层；HP 上限 100，共 16 项会话成就。旧 1.0.4 EXE / APK 保留原有规则。

| 平台 | 文件 | 系统要求 | 使用方法 |
| --- | --- | --- | --- |
| 历史 Windows 安装包 | `MinesweeperEX-Windows-x64-1.0.4.exe` | Windows 10 / 11，64 位 | 保留旧版；使用新规则请运行电脑脚本 |
| 电脑脚本 | 项目根目录 `run.cmd` | Windows、JDK 21 或以上 | 双击脚本，自动编译并启动游戏 |
| Android | `MinesweeperEX-Android-1.1.apk` | Android 8.0（API 26）及以上 | 传到手机后打开安装；首次安装时按系统提示允许当前来源安装应用 |

EXE 默认安装至当前用户目录，并创建桌面和开始菜单快捷方式。安装包包含游戏和精简 Java 运行环境，可在未配置 Java 的电脑运行。

也可免安装运行项目源码。双击 `run.cmd`，脚本依次查找 `JAVA_HOME`、项目已下载的 JDK 和 `PATH` 中的 JDK。当前项目已有 Microsoft OpenJDK 21.0.13.0.2，可直接运行。也可以用 `.\scripts\build.ps1 -Run -JdkHome 'JDK目录'` 指定位置。`build.cmd` 只构建 JAR；`target/MinesweeperEX.jar` 可通过 `java -jar` 启动。失败时脚本会保留错误提示窗口。

APK 是原生 Android 触控版本，直接编译复用 `config`、`model`、`service` 中的 Java 游戏规则，无需在手机安装 Java。包名为 `com.minesweeper.ex`，版本名称为 1.1、内部版本号为 6，使用项目原有 RSA 3072 位证书签名；不申请联网或存储权限。它不包含原桌面 Swing 窗口，界面按手机重新实现。原签名和递增版本号支持覆盖升级旧版，本次不执行安装或覆盖升级验证。

## Android 1.1 手机操作与规则

- 单击未知格探索；长按未知格直接排雷，也可以切换到“排雷”模式后单击。
- 双击已揭开格执行辅助展开，条件与电脑版一致：真实数字归零，且该区没有内鬼干扰。
- 拖动棋盘查看、双指缩放，或使用 `− / 适应 / +`。
- 菜单中选择难度、开始新游戏、继续当前对局、查看规则和记录或退出。
- 操作面板随时可重新开始，进行中的对局会先确认；胜利和失败后均可直接重开或重新选择难度。
- 棋盘上方列号和左侧行号从 1 开始，缩放拖动时保持对齐，与国王情报相同。

普通、困难、地狱、噩梦棋盘为 18×18 / 25×25 / 30×30 / 35×35，初始总雷数为 70 / 160 / 270 / 400，内鬼区为 4×4 / 6×6 / 8×8 / 10×10。每区唯一内鬼雷即核心，普通雷按普通规则处理；正确排除核心不扩张控制区并解除干扰，直接引爆后在内部再生，干扰保留。内鬼雷不受其他爆炸波及。干扰数字为真实邻雷数随机 ±1 后限制在 0～8。清除其他全部地雷、仅剩国王雷时获胜；提前排除、直接引爆或爆炸波及国王立即失败。完整规则见 [游戏规则](rules.md)。

手机生命值显示为 `HP：5/100`，以进度条和百分比显示控制区，操作面板大号显示本局用时，已移除实时行动状态文字。胜负后的弹窗均可重开或重新选择难度，操作面板保留独立重开按钮；手机对局记录仅显示关键统计和已解锁成就。

手机旋转屏幕会保留当前对局；返回菜单也可继续。成就与累计清雷仅在当前程序进程内保留，不提供退出后存档。

## 生成 Windows EXE

运行 `.\scripts\package-windows.ps1`，先严格编译并执行无窗口回归测试，再使用 JDK 的 `jlink`、`jpackage` 和 NSIS 生成 EXE 安装包。默认复用 `target/packaging-tools/` 中的 JDK 21 和 NSIS 3.11，也可用 `-JdkHome`、`-NsisHome` 指定目录。脚本从 `pom.xml` 读取版本，生成目录按版本隔离，安装包、启动器及卸载信息使用相同版本号。生成安装包不执行安装验证。

## 生成 APK

Android 打包需要 JDK 21、Android SDK Platform 35 / Build Tools 35.0.0、Gradle 8.11.1，首次需要访问 Google Maven 下载依赖：

```powershell
.\scripts\package-android.ps1 -JdkHome 'JDK目录' -AndroidSdk 'Android SDK目录' -Gradle 'gradle.bat路径'
```

脚本使用 Android Gradle Plugin 8.9.2，输出 Release APK，执行 Android Lint 和 APK 签名校验。安装包文件名从 `android/app/build.gradle` 的 `versionName` 读取，当前为 `MinesweeperEX-Android-1.1.apk`。`-WithTests` 额外生成测试版及独立测试 APK，二者仅在 `android/app/build` 中，不作为安装包交付。测试版包名带 `.debug`，可与 Release 共存。

Android 签名密钥和配置保存在被 Git 忽略的 `android/signing/`，不放在 `dist`。请自行备份此目录，后续覆盖安装更新必须使用同一把密钥；不要将密钥和密码提交到公开仓库。打包脚本会复用已有密钥，不会自动替换它。

可选的开发用模拟器检查（本次按要求不执行安装验证）：

```powershell
.\scripts\verify-android.ps1 -Serial emulator-5554
```

测试只允许模拟器序列号；当前测试源码已按普通、困难、地狱、噩梦四档同步棋盘、雷数和内鬼区尺寸检查，并覆盖实际触控探索与菜单继续，导出 `target/android-previews` 图片。本次未构建或执行 Android 测试。最低版本由 API 兼容性检查验证；模拟器测试不替代所有实体手机验证。
