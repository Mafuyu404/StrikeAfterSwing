# StrikeAfterSwing 多版本、多加载器重构路线图

## 完成状态

重构已完成。项目现在使用“可编辑的共享核心源码 + 独立加载器/版本工程”结构，根目录不再保留会混合加载器和 Minecraft 版本的可构建源码。

```text
StrikeAfterSwing/
  common/                               无加载器依赖的共享 Java 核心
  targets/
    forge-1.16.5/                       独立 Forge 1.16.5 Gradle 工程
    forge-1.20.1/                       独立 Forge 1.20.1 Gradle 工程
    fabric-1.20.1/                      独立 Fabric 1.20.1 Gradle 工程
    neoforge-1.21.1/                    独立 NeoForge 1.21.1 Gradle 工程
    fabric-26.1.2/                      独立 Fabric 26.1.2 Gradle 工程
  legacy-build/                         迁移前构建脚本归档
  build.gradle                          现代 JDK 21 target 的可选聚合入口
  settings.gradle                       根项目默认仅包含 common
```

## 设计约束

1. 每个 `targets/<loader>-<mc-version>/` 都是可直接由 IDEA 打开的 Gradle 根工程。
2. target 仅同步自己的加载器、映射和 Minecraft 依赖；不会隐式下载其他 target 的依赖。
3. `common` 通过外部源码子项目方式导入，因此可以在 IDEA 中直接编辑、重构和调试。
4. `common` 保持 Java 8 字节码兼容，不能依赖 Forge、NeoForge、Fabric、Minecraft 或 Mixin API。
5. target 自己维护入口、Mixin、accessor、metadata、资源和 Minecraft API 适配。
6. 每个发布 jar 对应一个加载器和一个 Minecraft 版本，并包含所需的 `common` class。

target 的 `settings.gradle` 使用如下映射：

```groovy
include 'common'
project(':common').projectDir = file('../../common')
```

因此，直接打开 `targets/fabric-1.20.1/` 时会出现可编辑的 `common` 模块，但不会同步 Forge、NeoForge、Fabric 26.1.2 或其他 Minecraft 版本。

## 模块边界

### common

共享核心只包含稳定的业务逻辑：

- `PendingAttackManager`：延迟攻击队列、tick、去重和防递归执行。
- `AttackBridge`：将实体可用性和实际攻击调用交给 target 实现。

禁止放入 `common` 的内容：

- Minecraft 实体、世界、服务器或映射方法名。
- Forge、NeoForge、Fabric API、Mixin 注解及 accessor。
- 特定 loader、特定大版本或运行时版本分支。

### targets

每个 target 负责：

- 加载器、Minecraft、映射、Gradle 和 JDK 版本。
- 该版本的 `AttackBridge` 实现和 Minecraft API 调用。
- 加载器入口、Mixin、资源和 metadata。
- 将共享核心 class 纳入 remap/reobf 后的最终 jar。

不同版本由不同维护者维护时，应只打开并修改自己的 target。涉及共享行为时修改 `common`，再由受影响 target 以各自 JDK 独立验证。

## 已验证构建矩阵

| Target | 独立构建命令 | 运行 JDK | 验证结果 |
| --- | --- | --- | --- |
| `forge-1.16.5` | `targets\forge-1.16.5\.\gradlew.bat clean build` | JDK 8 | 通过，jar 含 Forge metadata、Mixin 和 common class |
| `forge-1.20.1` | `targets\forge-1.20.1\.\gradlew.bat clean build` | JDK 21 | 通过，jar 含 Forge metadata、Mixin 和 common class |
| `fabric-1.20.1` | `targets\fabric-1.20.1\.\gradlew.bat clean build` | JDK 21 | 通过，jar 含 Fabric metadata、Mixin 和 common class |
| `neoforge-1.21.1` | `targets\neoforge-1.21.1\.\gradlew.bat clean build` | JDK 21 | 通过，jar 含 NeoForge metadata、Mixin 和 common class |
| `fabric-26.1.2` | `targets\fabric-26.1.2\.\gradlew.bat clean build` | JDK 25 | 通过，jar 含 Fabric metadata、Mixin 和 common class |

Fabric 26.1.2 使用 Fabric Loom `1.17-SNAPSHOT` 的无开发映射流程和 Gradle 9.5.1。其 `Mob#doHurtTarget` 适配了 26.1.2 的 `ServerLevel, Entity` 方法签名。

## 根项目聚合

根项目默认只包含 `common`。使用 JDK 21 时，可以显式代理三个 JDK 21 的现代 target：

```powershell
.\gradlew.bat '-Ptarget=forge-1.20.1' build
.\gradlew.bat '-Ptarget=fabric-1.20.1' build
.\gradlew.bat '-Ptarget=neoforge-1.21.1' build
.\gradlew.bat -PallTargets=true clean build
```

`-PallTargets=true` 已验证通过。Forge 1.16.5 和 Fabric 26.1.2 分别需要 JDK 8 与 JDK 25，必须通过自己的 wrapper 单独构建，不能放入根项目的同一次 Gradle invocation。

## 已完成清理

- [x] 旧的根 `src/` 混合源码树已删除。
- [x] 旧的根 `build-fabric.gradle` 和 `build-neoforge.gradle` 已删除。
- [x] 迁移前构建脚本保留在 `legacy-build/` 作为归档参考。
- [x] README 已更新为当前 target 目录、JDK 要求和实际构建命令。

## 后续维护规则

1. 新增 Minecraft 版本时新建 `targets/<loader>-<mc-version>/`，不要向现有 target 加运行时版本判断或互斥 Mixin。
2. 新 target 在合入前必须独立 `clean build`，并检查最终 jar 的 metadata、Mixin JSON 和 `common` class。
3. 只有 JDK 与 Gradle 要求能够统一的 target 才可加入根 `-PallTargets=true`；其余 target 在 CI 中作为独立 job 构建。
4. `legacy-build/` 只供历史排查，不得作为日常构建入口。

## Goal Objective

```text
维护 StrikeAfterSwing 的 common + targets/<loader>-<minecraft-version> 结构。
common 是可编辑、Java 8 兼容、无 Minecraft 和加载器依赖的源码模块；每个 target 是独立 Gradle 工程，只同步自己的加载器、映射和游戏依赖。
所有登记 target 必须以各自 JDK 独立构建；根项目仅显式聚合可由 JDK 21 共同构建的现代 target。
```
