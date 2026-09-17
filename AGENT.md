# StrikeAfterSwing 开发与迁移规范

本仓库使用 SighsTemple 的 `common + targets/<loader>-<minecraft-version>` 结构维护多个 Minecraft 加载器和版本。任何开发者或自动化代理在修改前都必须遵守本文件；更完整的协作、评审和 CI 规则见 [docs/MAINTENANCE_WORKFLOW.md](docs/MAINTENANCE_WORKFLOW.md)。

## 架构边界

```text
common/                         纯 Java 的共享逻辑与共享资源
targets/<loader>-<mc-version>/  一个独立的加载器 + Minecraft 版本工程
gradle/target-conventions/      所有 target 共用的构建约定
gradle/publish.gradle           根项目唯一的发布配置（覆盖全部 target）
```

- `common` 只放 Java 8 兼容、无 Minecraft/loader 依赖的业务逻辑、DTO、算法和测试。
- `targets/*` 只放该 target 的入口、注册、事件、Minecraft API、Mixin、网络、渲染和 metadata。
- 不要在 `common` 引用 `net.minecraft.*`、Forge、NeoForge、Fabric、Mixin 或渲染/网络 API。
- 不要用运行时版本判断、反射或同名 class 覆盖来兼容不同 target；不同 API 应由各 target 的适配器实现。
- 一个发布 jar 只对应一个 loader 与一个 Minecraft 版本，禁止 universal jar。

## 文件与配置约定

| 内容 | 位置 | 规则 |
| --- | --- | --- |
| 共享 Java 代码 | `common/src/main/java/` | 必须保持 Java 8 与无平台依赖；以源码并入每个 target 编译，见下文。 |
| 共享资源 | `common/src/main/resources/` | 会自动合并到所有 target 的最终 jar。 |
| 目标专属资源 | `targets/<name>/src/main/resources/` | 仅放该版本/loader 专属资源。 |
| Loader metadata | target 的 `src/main/resources/` | 保留在 target；Fabric、Forge、NeoForge 格式不可共用。 |
| 版本/loader 参数 | `targets/<name>/gradle.properties` | 不放在仓库根 `gradle.properties`。 |
| 共享模组信息 | 根 `gradle.properties` | 仅 `mod_*` 和 Gradle 运行参数。 |
| 本地 jar 依赖 | `targets/<name>/libs/` | 自动作为 `implementation` 依赖读取；不需要逐条声明。 |
| Target 构建约定 | `gradle/target-conventions/target.gradle` | 所有 target 共用；只放构建约定，target 不复制。 |
| 发布配置 | `gradle/publish.gradle`（仅根项目应用） | 唯一的发布入口，一次发布全部 target。 |
| CI 描述符 | `targets/<name>/ci.properties` | 每个 target 必需；决定 CI 是否构建与使用哪个 JDK。 |

`libs/` 中的普通 jar 不会自动带来传递依赖；依赖的其他 jar 也必须放入同一个 `libs/`，或改用正常的 Maven 依赖声明。不要把 `*-sources.jar`、`*-javadoc.jar` 或构建产物误放入此目录。

共享资源与 target 资源若有同路径文件，必须明确选择唯一归属；不要依赖覆盖顺序。加载器 metadata、Mixin 配置、access widener/access transformer 和版本专属语言文件一律归 target。

`common` 的 Java 源码通过 `gradle/target-conventions/target.gradle` 里的 `sourceSets.main.java.srcDir project(':common').file('src/main/java')` 直接并入每个 target 一起编译，因此产物和 dev 运行期看到的是同一份 class。不要改回 `sourceSets.main.output.dir(...)`、`jar { from project(':common').sourceSets.main.output }` 或 `implementation project(':common')`：这几种写法产出的 jar 看似正常，但 ForgeGradle 6 的 dev 运行期（`runServer` / `runClient`）看不到其中的 class，服务器第一个 tick 就 `NoClassDefFoundError: cc/sighs/strikeafterswing/common/AttackBridge`。dev 启动若报找不到 `common` 的类，先检查这里有没有被改回去。

## Target 实现规范

9 个 target 的实现逐行等价，只有类型名和平台调用签名不同。新增或修改 target 时必须保持一致：

- `cc.sighs.strikeafterswing.<Loader>AttackHandler`：静态 `PendingAttackManager<Mob, Entity>` + 匿名 `AttackBridge`，只暴露 `delayAttack` 与 `tick`；不要版本后缀，不要 `Legacy` 前缀。
- `mixin/MobAttackMixin`：`@Mixin(Mob.class)` + `@Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)`，方法体只做「读挥击时长 → `delayAttack` → 命中则 `cir.setReturnValue(true)`」。
- `mixin/MinecraftServerTickMixin`：只注入一个点，`@Inject(method = "tickServer", at = @At("TAIL"))`。
- `mixin/LivingEntityAccessor`：用 `@Invoker("getCurrentSwingDuration")` 访问器，不要反射。
- 禁止 `remap = false`、`@Pseudo`、`@Coerce`、`require = 0`、SRG/混淆名（`func_*`、`field_*`、`method_*`）、反射和重复的 tick 注入点。注入点写运行时真实名称，跨命名空间映射交给 refmap。
- Mixin 配置：`required: true`、`injectors.defaultRequire: 1`、`compatibilityLevel` 与该 target 的 JDK 一致；写 `"refmap": "<mod_id>.<loader><version>.refmap.json"` 时必须与 `build.gradle` 中的 refmap 声明一致。**需要 refmap 的平台**：Forge 全部（1.16.5/1.18.2/1.19.2/1.20.1，运行期是 SRG）以及 Fabric 1.20.x/1.21.x（运行期是 intermediary）。**不需要 refmap**：NeoForge 1.20.5 及以后（`neoforge-1.21.1`、`neoforge-26.1.2`）与 Fabric 26.x（`fabric-26.1.2`，运行期即官方名）。
- 只有平台 API 真的不同（如 26.1.2 的 `doHurtTarget(ServerLevel, Entity)`、1.16.5 的 `removed` 字段）才允许出现差异，且差异只写在对应 target 里，不得引入运行时版本判断。

## 日常开发

1. 先判断改动是 `common`、单个 target、多个 target，还是构建/发布配置。
2. 单 target 改动只修改对应 `targets/<name>/`；不因方便而改动其他版本。
3. 修改 `common` 前先定义不含 Minecraft 类型的语义与接口，再为所有受影响 target 实现桥接。
4. 改动资源、metadata、Mixin、注册、事件、网络或渲染时，除构建外必须做相应的运行验证。
5. 不提交 token、账号、密码、私有仓库凭据、IDE 运行缓存或 `build/` 输出。

### 构建命令

每个 target 是独立 Gradle 根工程，应在它自己的目录中构建：

```powershell
cd targets\forge-1.20.1
.\gradlew.bat clean build
```

| Target | Gradle JVM |
| --- | --- |
| `forge-1.16.5` | JDK 8 |
| `forge-1.18.2` | JDK 17 |
| `forge-1.19.2` | JDK 17 |
| `forge-1.20.1` | JDK 21 |
| `fabric-1.20.1` | JDK 21 |
| `fabric-1.21.1` | JDK 21 |
| `neoforge-1.21.1` | JDK 21 |
| `fabric-26.1.2` | JDK 25 |
| `neoforge-26.1.2` | JDK 25 |

根项目的 `-PallTargets=true build` 只覆盖四个 JDK 21 target（`forge-1.20.1`、`fabric-1.20.1`、`fabric-1.21.1`、`neoforge-1.21.1`），不能替代 `forge-1.16.5`（JDK 8）、`forge-1.18.2` 与 `forge-1.19.2`（JDK 17）、`fabric-26.1.2` 与 `neoforge-26.1.2`（JDK 25）的独立构建。

`forge-1.16.5` 的 `ci.properties` 是 `ci.enabled=false`：ForgeGradle 4.1 依赖 `net.minecraft:mappings_official:1.16.5`，该工件已从 Forge 的 maven 下架，冷缓存（CI）构建必然在 `createMcpToSrg` 失败；MCP snapshot 通道的映射虽仍可下载，但会触发 FG 4.1 自身 `MinecraftUserRepo.findSrgToMcp` 写目录失败。这属于上游问题，**不要**为了绕过而改回反射、SRG 名或 `remap = false`。本机存在既有 ForgeGradle MCP 缓存时该 target 仍能正常构建、测试与发布；等上游修复后再把 `ci.enabled` 打开。

### 发布

发布只在仓库根项目配置和触发：`gradle/publish.gradle` 从每个 target 自己的 `gradle.properties` 读取加载器与 Minecraft 版本，一次性发布全部 target 的 jar。不要在 target 里添加发布逻辑，也不要逐个版本单独发布。

```powershell
# 1. 先用各 target 自己的 JDK 构建（发布不会触发构建）
.\gradlew.bat -PallTargets=true build
cd targets\forge-1.16.5; .\gradlew.bat build; cd ..\..
cd targets\forge-1.18.2; .\gradlew.bat build; cd ..\..
cd targets\forge-1.19.2; .\gradlew.bat build; cd ..\..
cd targets\fabric-26.1.2; .\gradlew.bat build; cd ..\..
cd targets\neoforge-26.1.2; .\gradlew.bat build; cd ..\..

# 2. 回到仓库根发布全部版本
.\gradlew.bat publishMods      # CurseForge + Modrinth
.\gradlew.bat publish          # Sighs Maven 仓库
```

只想发布部分版本时加 `-PpublishTargets=forge-1.20.1,fabric-26.1.2`。根 `gradle.properties` 中配置非敏感项目 ID；token 只通过环境变量提供：

```powershell
$env:CURSEFORGE_TOKEN = '...'
$env:MODRINTH_TOKEN = '...'
$env:PUBLISH_CHANGELOG = '...'   # 可选
```

发布前必须确认要发布的 jar 是用正确 JDK、通过 `clean build` 产出的，并检查 jar 内的 metadata、共享 class、共享资源和版本范围；`checkPublishJars` 会校验 jar 是否齐全。`targets/forge-1.16.5` 由 ForgeGradle 固定在 Gradle 6.9.4 + JDK 8，这只影响它的**构建**，发布由根项目完成，因此它与其他 target 一样可以发布到 CurseForge 与 Modrinth。细节见 [docs/PUBLISHING.md](docs/PUBLISHING.md)。

## 将既有项目迁入本框架

迁移应以“先可构建、再抽取共享代码、最后验证行为”为顺序，禁止先删除旧工程再尝试恢复。

1. **盘点原项目**：记录 Minecraft 版本、loader、JDK、Gradle、mappings、入口、Mixin、资源、数据生成、依赖与运行配置。
2. **建立 target**：为每个 `(loader, Minecraft 版本)` 建立 `targets/<loader>-<mc-version>/` 独立工程，包含 wrapper、`settings.gradle`、本地 `gradle.properties`、`ci.properties`、`libs/` 与 `../../common` 映射。
3. **复制专属层**：将入口、注册、事件、Mixin、渲染、网络、metadata 和版本专属资源放入对应 target；不要在一个 target 放多版本分支。
4. **抽取 common**：仅将不使用平台类型的状态、规则、计算、DTO 和接口迁到 `common`。把 Minecraft 对象转换为 primitive、字符串、UUID 或自定义 DTO 后再跨边界传递。
5. **迁移资源**：所有 target 共用的 assets/data/lang 放到 `common/src/main/resources/`；将 Fabric/Forge/NeoForge metadata 与版本专属 Mixin 配置保留在 target。
6. **迁移依赖**：可从公开仓库解析的依赖写入对应 target 的 `build.gradle`；仅本地提供的 jar 放入该 target 的 `libs/`。不要把 loader 依赖放入 `common`。
7. **迁移配置**：模组名称、ID、许可证、作者、描述等共享值放根 `gradle.properties`；Minecraft、loader、mappings、版本范围和 JDK 相关值放 target 本地属性。
8. **逐 target 验证**：使用要求的 JDK 运行 `clean build`，检查 jar 内容，并做最小 client 与 dedicated server 启动验证；涉及数据或资源时额外运行 data generation/reload 验证。
9. **记录差异**：不能立即统一的 API 或行为差异写入 `docs/version-differences/`，由 target 适配实现，不能以 common 中的版本判断掩盖。
10. **清理旧结构**：只有所有迁入 target 均可构建且已验证后，才删除旧代码、旧资源与旧构建入口。

`legacy-build/` 保留为本仓库迁移前的构建脚本归档；它只供历史排查，不得作为日常构建入口。

## 提交前检查

- `common` 不含平台 import，且所有受影响 target 均已适配。
- 每个新增/修改的 target 使用正确 JDK 独立构建。
- 受影响 target 跑过行为测试（`.\scripts\behavior-test\behavior-test.ps1 -Target <name>`）：改动 `common` 的攻击语义、Mixin、加载器入口或 server tick 链路时必须 PASS；只有构建通过、行为测试未跑过时要在 PR 里说明原因。
- 新增 target 已包含 `ci.properties`、`gradlew.bat` 与 `libs/`，并在 `.\scripts\discover-targets.ps1` 输出中出现。
- 最终 jar 含正确 metadata、目标专属资源与共享 class/resources。
- 本地 `libs/` 内容明确且没有误提交的旧 jar。
- 发布改动只落在根 `gradle/publish.gradle` 与根 `build.gradle`；没有在 target 里新增发布逻辑。
- 发布相关改动不包含 token；项目 ID、版本类型、依赖关系和 changelog 已确认。
- 发布前所有待发布 target 已用各自 JDK 完成 `clean build`，且 `checkPublishJars` 通过。
- README、版本差异文档和支持矩阵与实际 target 一致。
