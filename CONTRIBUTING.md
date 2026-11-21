# 开发与维护

项目名称为 MinesweeperEX，桌面使用 Java 21 和 Swing，Android 直接复用 `config`、`model`、`service` 中的游戏规则。当前行为以 [游戏规则](docs/rules.md) 和游戏内说明为准；根目录 `游戏规则.txt` 及 `docs/legacy/` 是历史资料。

## 本地验证

在安装 JDK 21 或更高版本的 Windows 环境执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\build.ps1 -Test
```

`JAVA_HOME` 应指向完整 JDK。脚本严格编译源码，运行规则回归与无窗口 Swing 交互检查，生成脚本运行所需的 `target/MinesweeperEX.jar`。需要核对界面时加 `-Preview`；真实桌面鼠标检查需手动选择 `-DesktopTest`，会短暂操作桌面。

GitHub Actions 在推送、拉取请求和手动触发时执行同一套桌面回归，不执行安装验证，不自动生成或发布 EXE / APK。

## 修改约定

- 游戏规则集中在共享引擎及难度配置中，界面负责展示与输入；调整规则时同步 `RuleGuide` 和相关文档。
- 对规则或鼠标处理的修改应包含能验证实际行为的回归检查。不要仅为实现细节增加重复断言。
- 主分支维护可运行版本；日常修改建议新建功能分支，通过拉取请求合并。
- 提交只包含与本次工作有关的源码、测试、文档及配置。构建产物、安装包、个人路径及 Android 签名文件均不进入 Git。
- EXE / APK 必须在明确指定平台和版本后单独打包。已经交付的 Android 1.1 APK 不代表当前源码规则，具体差异见 [运行与安装说明](docs/distribution.md)。

## 日常 Git 流程

先同步主分支，再创建本次工作分支：

```powershell
git switch main
git pull --ff-only
git switch -c fix/describe-the-change
```

修改并验证后，检查暂存区再提交：

```powershell
git status --short
git add src docs
git diff --cached --stat
git diff --cached
git commit -m "fix: describe the corrected behavior"
git push -u origin HEAD
```

按实际范围暂存其他文件。不要使用强制推送替代同步或冲突处理。GitHub 登录通过 Git Credential Manager 或 GitHub CLI 完成，不把令牌写入仓库 URL、脚本或文档。
