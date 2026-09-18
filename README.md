# StrikeAfterSwing

基于 SighsTemple 框架（`common + targets/<loader>-<minecraft-version>`）维护的多加载器、多版本 Minecraft 模组。

模组把落在攻击挥击动画期间的生物攻击延后到挥击结束后执行：`common` 持有延迟队列与队列语义，每个 target 提供自己的加载器入口、Mixin 与 `AttackBridge` 实现。

## 攻击前摇

被延后的攻击默认等一个原版挥击时长（`LivingEntity#getCurrentSwingDuration`）。`config/strikeafterswing/windup.json` 可以按实体类型缩放这个前摇：

```json
{
  "minecraft:zombie": 1.5,
  "minecraft:husk": 0.5
}
```

- 键是实体类型 id（`minecraft:zombie`、`modid:custom_mob`），值是倍率。`1.0` 等于原版时长，小于 1 缩短前摇，大于 1 延长前摇。
- 倍率作用在原版挥击时长（`LivingEntity#getCurrentSwingDuration`）本身，原版由它同时驱动挥击动画（`updateSwingTime`）和攻击节奏，所以**动画和命中时机一起被缩放**，不会出现"手已经放下但伤害还没到"或反之。
- 实际时长 = 四舍五入(原版挥击时长 × 倍率)，下限 1 tick。玩家不受影响，挥击保持原版。
- 结算前会按该版本的近战范围再判一次（1.21+ 用 `Mob#isWithinMeleeAttackRange`，更早版本用 `MeleeAttackGoal#getAttackReachSqr` 的距离公式）：目标在挥击期间跑出攻击范围就打空，不会隔空命中。
- 挥击动画由客户端逐帧计算，所以客户端要有同样的配置才能看到缩放后的动作：单人游戏天然一致；多人游戏里若只在服务器调了倍率而客户端配置缺失，客户端会按原版时长播放动画（伤害时机仍按服务器缩放）。
- 文件不存在时首次加载会写入上面这份示例；没有列出的实体一律按 `1.0` 处理，所以空文件或缺失文件都等价于原版行为。
- 改完保存即生效，不需要重启：模组最多每 2 秒按文件的修改时间/大小检查一次。删除文件会退回原版时长且不再自动重建；重载时 JSON 坏掉则保留上一次生效的倍率并写日志警告。
- JSON 不是对象、某个值不是数字/为负/为 NaN 时，只有那条被忽略并写入日志警告，其余条目照常生效；整份文件解析失败时保留上一次生效的倍率并警告一次（首次加载就失败则等于全部 `1.0`）。

## IDEA

直接打开任意 `targets/<loader>-<version>/` 目录。IDEA 会导入当前 target 与可编辑的 `../../common` 源码模块，只下载该 target 的加载器和 Minecraft 依赖。

## Target

| Target | JDK | 构建命令 |
| --- | --- | --- |
| `forge-1.16.5` | JDK 8 | `targets\forge-1.16.5\.\gradlew.bat clean build` |
| `forge-1.18.2` | JDK 17 | `targets\forge-1.18.2\.\gradlew.bat clean build` |
| `forge-1.19.2` | JDK 17 | `targets\forge-1.19.2\.\gradlew.bat clean build` |
| `forge-1.20.1` | JDK 21 | `targets\forge-1.20.1\.\gradlew.bat clean build` |
| `fabric-1.20.1` | JDK 21 | `targets\fabric-1.20.1\.\gradlew.bat clean build` |
| `fabric-1.21.1` | JDK 21 | `targets\fabric-1.21.1\.\gradlew.bat clean build` |
| `neoforge-1.21.1` | JDK 21 | `targets\neoforge-1.21.1\.\gradlew.bat clean build` |
| `fabric-26.1.2` | JDK 25 | `targets\fabric-26.1.2\.\gradlew.bat clean build` |
| `neoforge-26.1.2` | JDK 25 | `targets\neoforge-26.1.2\.\gradlew.bat clean build` |

Fabric 26.1.2 使用 Fabric Loom 1.17 的无开发映射工作流和 Gradle 9.5.1；Forge 1.16.5 使用 ForgeGradle 4.1 和 Gradle 6.9.4。每个 target 都必须用自己 `ci.properties` 声明的 JDK 单独构建。

`forge-1.16.5` 目前 `ci.enabled=false`，不在 CI 矩阵中：ForgeGradle 4.1 需要 `net.minecraft:mappings_official:1.16.5`，该工件已从 Forge 的 maven 下架（zip 与 pom 均为 404），冷缓存构建会在 `createMcpToSrg` 失败；改用 MCP snapshot 通道虽然还能取到映射，但会撞上 FG 4.1 自身写目录失败的 bug。这属于上游问题，本机存在既有 ForgeGradle MCP 缓存时该 target 仍能正常构建、测试与发布。

根项目默认只同步 `common`。使用 JDK 21 时可选择性构建四个 JDK 21 的 target：

```powershell
.\gradlew.bat '-Ptarget=forge-1.20.1' build
.\gradlew.bat '-Ptarget=fabric-1.20.1' build
.\gradlew.bat '-Ptarget=fabric-1.21.1' build
.\gradlew.bat '-Ptarget=neoforge-1.21.1' build
.\gradlew.bat -PallTargets=true build
```

`forge-1.16.5`（JDK 8）、`forge-1.18.2` 与 `forge-1.19.2`（JDK 17）、`fabric-26.1.2` 与 `neoforge-26.1.2`（JDK 25）因 JDK 要求不同，不纳入根项目的聚合构建。

## 结构

- `common/`: 不依赖 Minecraft 或任意 loader 的共享 Java 代码。
- `targets/*`: loader 和版本专属入口、metadata、资源及 API 适配；每个 target 自己构建可发布的 jar。
- `gradle/publish.gradle`: 根项目唯一的发布配置，一次命令发布全部 target。
- `gradle/target-conventions/target.gradle`: 所有 target 共用的构建约定（`libs/` 本地依赖、共享资源、把 `common/src/main/java` 并入 target 一起编译）。
- `scripts/`: CI 与本地的 target 发现、构建脚本，以及 `scripts/behavior-test/` 运行期行为测试。
- `legacy-build/`: 迁移前的混合构建脚本归档，只供历史排查。

## 共享资源

将所有加载器和版本共用的资源放在 `common/src/main/resources/`。构建任意 target 时，该目录会与 target 自己的 `src/main/resources/` 合并并写入最终 jar。

加载器 metadata 仍必须保留在 target 中：Fabric 使用 `fabric.mod.json`，Forge 使用 `META-INF/mods.toml`，NeoForge 使用 `META-INF/neoforge.mods.toml`。

## 本地依赖

每个 target 都会自动将自身 `libs/` 目录中的 `*.jar` 作为 `implementation` 依赖。将 jar 放入对应目录后不需要在 `build.gradle` 中逐条声明；`*-sources.jar` 和 `*-javadoc.jar` 会被忽略。

```text
targets/forge-1.16.5/libs/
targets/forge-1.18.2/libs/
targets/forge-1.19.2/libs/
targets/forge-1.20.1/libs/
targets/fabric-1.20.1/libs/
targets/fabric-1.21.1/libs/
targets/neoforge-1.21.1/libs/
targets/fabric-26.1.2/libs/
targets/neoforge-26.1.2/libs/
```

本地 jar 的传递依赖无法自动推导。若某个 jar 还依赖其他库，需要将这些库也放入同一个 `libs/` 目录，或按常规方式声明依赖。

## CI

`targets/<name>/ci.properties` 是 CI 的唯一真相来源，声明该 target 是否构建、使用哪个 JDK 和网络重试次数：

```properties
ci.enabled=true
ci.java=21
ci.attempts=3
```

`.github/workflows/verify-common.yml` 不硬编码版本矩阵，而是调用 `scripts/discover-targets.ps1` 扫描全部 target。本地可用同一组脚本：

```powershell
.\scripts\discover-targets.ps1                 # 输出 CI 将要使用的矩阵
.\scripts\build-target.ps1 -Target fabric-26.1.2 # 用声明的 JDK 构建单个 target
```

## 行为测试

`scripts/behavior-test/` 在真实专用服务器上验证运行期行为，而不是只检查 mixin 是否注入成功。一次运行按顺序跑 5 个场景，每个场景自己写一份 `windup.json` 配置并搭一组竞技场（一只当攻击者的怪物 + 一名紧贴它的 `NoAI` 村民，怪物获取目标后立即攻击）；所有时间戳都来自 `time query gametime`，量的是游戏 tick，不受服务器卡顿影响。

| 场景 | 做什么 | 断言 |
| --- | --- | --- |
| `deferral` | 基准尸壳（挥击 6 tick）对比灌满级挖掘疲劳的尸壳（挥击 `6 + (1 + 255) * 2 = 518` tick） | 两场都在窗口内首次掉血；延迟 ≥ 300 tick；基准场首击 ≤ 200 tick；疲劳场窗口内受击 ≤ 2 次、基准场 ≥ 2 次；两场的饥饿都不早于各自首次掉血（疲劳场容差 120 tick） |
| `multiplier` | 就地写入 `{"minecraft:husk": 20.0}`，与没有配置条目的僵尸在同一轮对比 | 尸壳首击 ≥ 100 tick（≈ 20 × 6）；僵尸 ≤ 90 tick（原版 6 tick + 索敌相位，历史实测 22–62）；两者相差 ≥ 40 tick |
| `broken-config` | 就地写入截断的 JSON | 保留上一场景的 x20 倍率（首击仍 ≥ 100 tick），而不是退回原版 |
| `deleted-config` | 删除配置文件 | 回退到原版时长（首击 ≤ 90 tick） |
| `reach` | 两只满级疲劳尸壳，其中一只的村民在 +200 tick 被传出攻击范围 | 对照场必须命中（证明这只尸壳确实挥了）；撤离场在对照场挨打之后再观察 60 tick 仍未掉血——那时它本该结算的那次命中早已过去 |

`deferral` 用尸壳当攻击者是因为原版 `Husk#doHurtTarget` 会在命中时挂饥饿，而它拿 `super.doHurtTarget(...)` 的返回值当判据——这个场景因此顺带验证「附加效果不会早于命中」，正是取消攻击却谎报命中的老 bug 会踩到的点。疲劳场挥击 518 tick，谎报命中时 debuff 会早 500 tick 左右，正常则与掉血同 tick，两者不会混淆；基准场只有 6 tick，采样粒度不足以区分，所以对它只检查"饥饿出现过"。

`multiplier`、`broken-config`、`deleted-config` 都在**服务器运行中**改配置文件，因此同时验证配置热重载：改完不重启，最多 2 秒后生效（场景写文件后会先等 5 秒再搭场地，确保下一次挥击是在模组察觉之后才入队的）。

9 个 target 全部实测（每个都跑满 5 个场景）：`deferral` 差值 511–519（理论 `518 − 6 = 512`，多出的几 tick 是尸壳首次索敌的相位）；`multiplier` 尸壳 120–141、无条目僵尸 6–54，两者相差 66–134 tick；`broken-config` 120–142；`deleted-config` 25–48；`reach` 对照场 518–557 命中、撤离场在整个观察时段内无伤。把 mixin 列表清空使模组失效后，`deferral` 的差值掉到个位数，测试 FAIL。

驱动器负责准备 `targets/<name>/run/`（写入 `eula=true`、开启 RCON 的 `server.properties`，并把 `pause-when-empty-seconds` 设为 0 —— 服务器默认在无人时自动暂停，会让 `gametime` 冻结、生物停止 tick）、启动专用服务器、运行 `behavior_test.py` 并在结束时停服。等所有测试生物能被 tag 选择器选中后才开始计时，避免刚召唤的实体在区块状态稳定前查不到。一个 target 约 3–5 分钟（首次要生成世界；只想跑其中几个场景时给 `behavior_test.py` 传 `--scenarios deferral,reach`）。`JAVA_HOME` 必须指向该 target 在 `ci.properties` 中声明的 JDK：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.x'
.\scripts\behavior-test\behavior-test.ps1 -Target forge-1.20.1
```

多个 target 同时跑时必须错开端口，在各自的 `targets/<name>/behavior-test.json` 中固定，例如 `{ "rconPort": 25581, "serverPort": 25571 }`（具体端口以各 target 的现状为准，不要照抄示例）：

```json
{ "rconPort": 25581, "serverPort": 25571 }
```

该文件也可覆盖命令模板，键为 `rconPort`、`serverPort`、`worldSetup`、`siteSetup`、`spawnTarget`、`spawnAttacker`、`longSwing`、`slowAttacker`、`escapeTarget`、`queryEffects`、`queryHealth`、`queryTargetEffects`、`queryTick` 以及可选的 `assertions`；默认模板见 `scripts/behavior-test/commands.json`，占位符有 `{x} {y} {z} {padY} {padX1} {padX2} {padZ1} {padZ2} {chunkX} {chunkZ} {attackerX} {escapeX} {attacker} {targetTag} {attackerTag} {tag}`。注意 `forceload` 收的是**区块**坐标，所以模板里用的是 `{chunkX}`/`{chunkZ}`；`spawnAttacker` 里的 `{attacker}` 由场景指定（尸壳或僵尸）。场景定义和断言本身对所有 target 一致，版本间的命令语法差异只写在这个文件里。

失败时看命令输出和 `targets/<name>/run/behavior-test-server.log`。

`.github/workflows/verify-common.yml` 的每个 target job 在 `clean build` 之后执行同一条命令，因此改动 `scripts/behavior-test/**` 或 `targets/*/behavior-test.json` 也会触发完整矩阵。

## 发布

发布只在**仓库根项目**配置和触发（`gradle/publish.gradle`），一份配置发布全部 target，不需要逐个版本单独发布。发布清单由根项目自动读取：每个 `targets/*/` 的加载器取目录名、Minecraft 版本取该 target 自己的 `gradle.properties`，jar 取 `targets/<dir>/build/libs/`。

发布前必须先用每个 target 自己的 JDK 构建好 jar —— 发布任务不会触发构建：

```powershell
.\gradlew.bat -PallTargets=true build                                  # 四个 JDK 21 target
cd targets\forge-1.16.5; .\gradlew.bat build; cd ..\..                  # JDK 8
cd targets\forge-1.18.2; .\gradlew.bat build; cd ..\..                  # JDK 17
cd targets\forge-1.19.2; .\gradlew.bat build; cd ..\..                  # JDK 17
cd targets\fabric-26.1.2; .\gradlew.bat build; cd ..\..                 # JDK 25
cd targets\neoforge-26.1.2; .\gradlew.bat build; cd ..\..               # JDK 25
```

然后回到仓库根发布全部版本：

```powershell
.\gradlew.bat publishMods        # CurseForge + Modrinth
.\gradlew.bat publish            # Sighs Maven 仓库
```

两个平台的项目 ID 是所有 target 共用的非敏感信息，在根 `gradle.properties` 中取消注释并填写；token 只从环境变量读取，不要写入仓库：

```properties
publish_curseforge_project_id=你的CurseForge项目ID
publish_modrinth_project_id=你的Modrinth项目ID
```

```powershell
$env:CURSEFORGE_TOKEN = '...'
$env:MODRINTH_TOKEN = '...'
$env:PUBLISH_CHANGELOG = '本次版本的更新说明' # 可选

.\gradlew.bat publishMods
```

只想发布部分版本时用 `-PpublishTargets=`：

```powershell
.\gradlew.bat -PpublishTargets=forge-1.20.1,fabric-26.1.2 publishMods
```

单个平台/单个版本的任务名形如 `publishCurseforgeForge1201`、`publishModrinthFabric2612`。`checkPublishJars` 会先校验所有待发布 jar 都已构建，缺失时直接列出路径和构建命令。细节见 [docs/PUBLISHING.md](docs/PUBLISHING.md)。

## 版本参考

- [多版本日常维护工作流](docs/MAINTENANCE_WORKFLOW.md)
- [Target CI 自动发现](docs/CI_TARGET_DISCOVERY.md)
- [发布约定](docs/PUBLISHING.md)
- [Minecraft 版本迁移差异参考](docs/version-differences/README.md)
