
ClipStack 项目上下文文档

本文档为 AI 辅助编程提供项目“地图”，包含技术栈、目录结构、核心模块与开发命令。
遵循“文档化替代探索式读取”原则，AI 应先阅读本文档再分析代码。

项目概述

· 名称：ClipStack（剪纸堆）
· 类型：Android 剪贴板历史记录管理应用
· 状态：已弃用（Deprecated），因个人原因停止更新
· 包名：com.catchingnow.clip
· 许可证：Java 代码采用 MIT 许可证；图标、图片、UI 设计等保留所有权利

技术栈

类别 技术
语言 Java
构建工具 Gradle（AGP 8.0.0）
目标 SDK compileSdk 34（Android 14）
最低支持 Android 4.0（API 14），与 Android 5.0 Lollipop 搭配最佳
迁移状态 已迁移至 AndroidX
测试 androidTest/java/ 目录包含测试代码

项目目录结构

```
ClipStack/
├── app/                                    # 主应用模块
│   ├── bpa.keystore                        # 应用签名密钥[reference:11]
│   ├── build.gradle                        # 模块级 Gradle 配置[reference:12]
│   ├── proguard-rules.pro                  # ProGuard 混淆规则[reference:13]
│   └── src/
│       ├── androidTest/                    # Android 测试代码[reference:14]
│       │   └── java/com/catchingnow/clip/
│       └── main/                           # 主源码目录[reference:15]
│           ├── AndroidManifest.xml         # 应用清单文件[reference:16]
│           ├── java/com/catchingnow/clip/  # Java 源码目录[reference:17]
│           └── res/                        # 资源文件（布局、图片、值等）[reference:18]
├── gradle/                                 # Gradle Wrapper 配置[reference:19]
│   └── wrapper/
├── .gitignore                              # Git 忽略规则[reference:20]
├── build.gradle                            # 项目级 Gradle 构建脚本[reference:21]
├── gradle.properties                       # Gradle 属性配置[reference:22]
├── gradlew / gradlew.bat                   # Gradle Wrapper 脚本[reference:23][reference:24]
├── local.properties                        # 本地 SDK 路径配置[reference:25]
├── README.md                               # 项目说明文档[reference:26]
└── settings.gradle                         # Gradle 设置文件[reference:27]
```

核心 Java 源码模块（app/src/main/java/com/catchingnow/clip/）

文件 职责
ActivityMain.java 主界面 Activity
ActivityMainDialog.java 主界面对话框
ActivityEditor.java 编辑剪贴板条目
ActivitySetting.java 设置界面
ActivityBackup.java / ActivityBackupNew.java 备份与恢复功能
CBWatcherService.java 剪贴板监听后台服务
FloatingWindowService.java 悬浮窗服务
ExperienceEnhanceService.java 体验增强服务
SyncJobService.java 同步任务服务（JobScheduler）
LaunchServiceAtStartup.java 开机自启服务
AppWidget.java / AppWidgetService.java 应用小部件
ClipObject.java 剪贴板数据模型
ClipObjectActionBridge.java 剪贴板操作桥接
BackupExport.java / BackupObject.java 备份导出与对象
Storage.java 存储相关操作
MyUtil.java 工具类
MyActionBarActivity.java 基类 Activity
MyPreferenceActivity.java 偏好设置基类
GoogleBackupAgent.java Google 备份代理
CrashHandler.java 全局崩溃捕获
SwipeableRecyclerViewTouchListener.java 滑动删除触摸监听

权限说明

· RECEIVE_BOOT_COMPLETED：开机自启后台服务，监听系统剪贴板
· WRITE_EXTERNAL_STORAGE / READ_EXTERNAL_STORAGE：导出剪贴板历史记录时使用

主要功能

· 无限保存剪贴板历史：自动保留复制过的文字，重启后自动恢复
· 易于管理：搜索、编辑、滑动删除、导出为纯文本文件
· 通知栏扩展：复制新文本时，通知栏显示最近6条记录，方便切换粘贴
· 自由分享：每条记录可分享至其他应用
· 自动清理：充电时自动清理缓存和内存（JobScheduler API）
· Material Design：遵循 Material Design 设计规范

开发常用命令

任务 命令
构建 Debug APK ./gradlew assembleDebug
构建 Release APK ./gradlew assembleRelease
安装 Debug 版 ./gradlew installDebug
运行测试 ./gradlew test
清理构建 ./gradlew clean
同步项目 ./gradlew build

外部依赖（已知）

· nispok/Snackbar
· brnunes/SwipeableRecyclerView
· EatHeat/FloatingExample

AI 辅助建议

1. 优先阅读本文档：任何代码分析或修改前，先加载本文件了解全局
2. 忽略以下目录（参考 .gitignore 规则）：build/、*.iml、local.properties
3. 注意项目状态：项目已弃用，分析或修改时请留意这一背景
