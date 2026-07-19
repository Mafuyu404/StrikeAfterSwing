# StrikeAfterSwing

项目按 `common + targets/<loader>-<minecraft-version>` 组织。

## 日常维护

每个 `targets/*` 都是独立 Gradle 项目。用 IDEA 直接打开相应目录即可；该工程会同时导入仓库根目录中可编辑的 `common` 源码模块，只解析本 target 的加载器、映射和 Minecraft 依赖。

`common` 是 Java 8 字节码兼容的纯 Java 核心，不依赖 Forge、NeoForge、Fabric 或 Minecraft API。对共享逻辑的修改会参与当前打开 target 的下一次构建。

## Target 构建

使用 target 自己的 Gradle wrapper，并选择对应 JDK：

| Target | JDK | 命令 |
| --- | --- | --- |
| Forge 1.16.5 | JDK 8 | `cd targets\forge-1.16.5; .\gradlew.bat clean build` |
| Forge 1.20.1 | JDK 21 | `cd targets\forge-1.20.1; .\gradlew.bat clean build` |
| Fabric 1.20.1 | JDK 21 | `cd targets\fabric-1.20.1; .\gradlew.bat clean build` |
| NeoForge 1.21.1 | JDK 21 | `cd targets\neoforge-1.21.1; .\gradlew.bat clean build` |
| Fabric 26.1.2 | JDK 25 | `cd targets\fabric-26.1.2; .\gradlew.bat clean build` |

Fabric 26.1.2 使用 Fabric Loom 1.17 的无开发映射工作流和 Gradle 9.5.1；它必须单独使用 JDK 25 构建。

## 根项目聚合

根项目默认只导入 `common`，不会在 IDEA 同步时下载所有 target 的依赖。它只代理能够用同一 JDK 21 构建的三个现代 target：

```powershell
$env:JAVA_HOME = 'D:\program\jdk-21'
.\gradlew.bat '-Ptarget=forge-1.20.1' build
.\gradlew.bat '-Ptarget=fabric-1.20.1' build
.\gradlew.bat '-Ptarget=neoforge-1.21.1' build
.\gradlew.bat -PallTargets=true clean build
```

PowerShell 中带小数点的 `-Ptarget=...` 参数需使用单引号。Forge 1.16.5 和 Fabric 26.1.2 由于 JDK 要求不同，不纳入根项目的聚合构建。

## 迁移状态

五个 target 均已独立 `clean build` 验证，最终 jar 均包含各自的 metadata、Mixin 配置和 `common` class。根目录旧的混合 `src/`、`build-fabric.gradle` 和 `build-neoforge.gradle` 已移除；迁移前构建脚本保留在 `legacy-build/` 作为归档参考。
