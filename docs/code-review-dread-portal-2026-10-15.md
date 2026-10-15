# 悚域传送门代码审查

日期：2026-10-15
项目基线：`85420885ca3bf3f07435746e8e2813feebf53bc7`（0.1.2）
范围：传送门激活、框架生命周期、实体传送与附件、出口 NBT、BER/Shader、粒子、HUD，以及旧问题复核。
方式：源码审查、上游历史对照、Minecraft/NeoForge 1.21.1 源码核查、结构 NBT 解码；DP-004、005、009 另经只读独立复核。未修改功能代码，未进行游戏内复现。

## 结论与优先级

确认 9 项需要处理的问题：3 项 P1，6 项 P2。P1 应优先处理，P2 为常规修复。下文的“确认”表示代码路径和前置条件已核实，不表示已经在游戏内复现。

| 编号 | 优先级 | 问题 | 主要位置 |
| --- | --- | --- | --- |
| DP-001 | P1 | 双向传送照搬原 XYZ，没有安全落点计算 | `PortalData.java:49-58` |
| DP-002 | P1 | 出口模板直接覆盖目标区域，可能损毁存档建筑 | `PortalData.java:57-58` |
| DP-003 | P1 | 自定义核心 Shader 在 Iris 光影管线中默认被屏蔽 | `ModRenderTypes.java:11-28`、客户端入口 `62-68` |
| DP-004 | P2 | 忽略换维度失败和返回实体，失败后仍可能生成出口 | `PortalData.java:47-58` |
| DP-005 | P2 | 归零后先传送、后检查接触，离开门仍可能触发 | `PortalData.java:46-66` |
| DP-006 | P2 | 所有生物全量创建、持久化、同步附件，并在两端运算 | `ModAttachments.java:22-35` |
| DP-007 | P2 | 每个传送门方块每帧新建 RenderType，破坏合批 | `ModRenderTypes.java:17-28` |
| DP-008 | P2 | HUD 计数无限增长，离门后长时间遮挡且跨会话残留 | `PortalRenderHelper.java:16-29` |
| DP-009 | P2 | 绕过实体传送资格检查，乘客等实体可被强制拆乘传送 | `PortalData.java:46-56` |

## DP-001 [P1] 双向传送没有计算安全落点

**位置：** `src/main/java/com/zonlong/iceandfiredreadland/data/component/PortalData.java:49-58`。

两个方向的 `DimensionTransition` 都把 `living.position()` 原样作为目标位置，没有读取目标维度的地表高度、检查实体碰撞空间、液体、建筑高度或世界边界。进入悚域后，出口结构也按同一个旧高度生成。

**触发条件与影响：**

- 从较低海拔的主世界传入地表较高的悚域，出口被嵌入地下。这正是本次报告的地下落点问题的直接根因。
- 反向传送也有同样问题：悚域位置在主世界可能是实心地层、空中或液体。
- 主世界方向没有出口模板兜底。如果从悚域的高处返回主世界，可能高空跌落；从地下返程可能进入方块。
- 仅调整悚域入口的 Y 并不完整：若返程仍沿用新的 Y，主世界返程高度可能更危险。

**建议：** 独立目标位置解析逻辑，先寻找可复用的有效出口，再在邻域内寻找地面和足够空间；高度图只能作为候选来源，还需检查实体包围盒、液体、支撑和结构完整占地。确定安全落点并完成必要的出口准备后再移动实体。返程应有明确的安全目标或保存的入口关联；保留现有“悚域返回主世界”的规则时，同样必须进行安全检查。

## DP-002 [P1] 出口生成可覆盖建筑和容器

**位置：** `src/main/java/com/zonlong/iceandfiredreadland/data/component/PortalData.java:57-58`。

是否生成出口只取决于目标中心那个方块是不是悚域传送门。代码不搜索附近现有出口，不验证模板区域是否允许替换，直接用空的 `StructurePlaceSettings` 调用 `placeInWorld()`。

**结构实证：** 对 `src/main/resources/data/iceandfire_dreadland/structure/dread_exit_portal.nbt` 解码后得到：

- 大小为 `6×7×6`，共 252 个方块条目。
- 其中 128 个是明确的 `minecraft:air`，16 个是传送门。
- 相对于抵达方块，覆盖范围为 X/Z `-2…+3`，Y `-1…+5`。
- 空气方块也是模板的实际放置内容；没有忽略空气的处理器。

Minecraft 1.21.1 的 `StructureTemplate.placeInWorld()` 在没有处理器限制时直接调用 `setBlock()`，不会自动替模组检查玩家建筑或方块是否可替换。在带 NBT 的模板位置还会清理原方块实体。

**触发条件与影响：** 目标中心不是传送门、但周围有建筑或旧出口时，进入悚域可能替换实体方块并清空部分区域。落点贴近已有出口也会重新覆盖。问题不限于挖开自然地形。

**建议：** 先检查完整占地、保护容器和不可替换建筑、优先复用附近出口，并制定受限的平台/清障策略。不能仅通过忽略模板空气解决所有问题，因为实体方块仍会覆盖建筑；也不能只把 Y 抬高，因为高地同样可能有建筑。生成前需验证，生成结果及模板缺失也需处理。

## DP-003 [P1] Iris 默认屏蔽当前自定义核心 Shader

**位置：**

- `src/main/java/com/zonlong/iceandfiredreadland/registry/ModRenderTypes.java:11-28`。
- `src/main/java/com/zonlong/iceandfiredreadland/IceAndFireDreadLandClient.java:62-68`。
- `src/main/java/com/zonlong/iceandfiredreadland/render/DreadPortalBlockEntityRenderer.java:24-60`。

当前客户端注册普通 `ShaderInstance`，BER 通过自定义 `ShaderStateShard` 直接使用它；没有光影开启时的替代渲染路径。

**Iris 核查基线：** 官方仓库 `1.21.1` 分支，提交 `eb7afb99f747cc8ed5ee4072119539035d33cefd`。本地只读参考位于 `.audit-sources/Iris-1.21.1/`，该目录被 Git 忽略。未将分支源码等同于用户实际安装的 Iris 二进制版本。

**源码证据链：**

1. `common/src/main/java/net/irisshaders/iris/config/IrisConfig.java:59`：`allowUnknownShaders` 默认为 `false`。
2. `common/src/main/java/net/irisshaders/iris/mixin/MixinShaderInstance.java:92-107`：世界光影管线生效时，非 `ExtendedShader`、非 `FallbackShader` 的实例被判定为跳过。
3. 同文件 `159-177`：在 `apply()` 尾部对该情况调用 `DepthColorStorage.disableDepthColor()`。
4. `common/src/main/java/net/irisshaders/iris/gl/blending/DepthColorStorage.java:17-35`：关闭深度写入和所有颜色通道写入。
5. `neoforge/src/main/java/net/irisshaders/iris/mixin/forge/MixinShaderInstance.java` 同时处理 NeoForge 的 ResourceLocation 构造函数；不能通过更换命名空间绕开这套分类。

因此，在上述 Iris 分支的默认设置和光影生效条件下，当前门面会走被屏蔽的路径。这是有源码依据的兼容性根因；具体光影包画面仍需实机验证。

**建议：** 保留无光影时的现有效果；光影启用时，切换到 Iris 能接管的标准渲染类型和相应顶点格式，例如标准实体贴图管线配合动画纹理。通过可选兼容层调用官方 `IrisApi.isShaderPackInUse()`，区分“安装 Iris”与“光影实际生效”，并支持运行时切换。

注意当前顶点仅包含位置和颜色。改用标准实体管线时，必须同时提供其要求的 UV、法线、光照、覆盖等顶点数据，不能只替换 `RenderType`。也不应把要求用户开启 `allowUnknownShaders` 当作完整修复；未知 Shader 的输出目标、阴影等仍未完整接入光影包。

## DP-004 [P2] 换维度失败没有回滚，成功后也未使用返回实体

**位置：** `src/main/java/com/zonlong/iceandfiredreadland/data/component/PortalData.java:47-58`。

代码先设置 `teleported=true`，然后调用 `changeDimension()`，不接收其返回值。目标维度不存在时也在这个标志已经设置后直接返回。

Minecraft/NeoForge 1.21.1 的行为是：

- `Entity.changeDimension()` 和 `ServerPlayer.changeDimension()` 会先调用 NeoForge 的跨维度旅行检查；事件取消时返回 `null`。
- 普通非玩家实体跨维度通常创建新实体，玩家通常返回原对象。

**触发条件与影响：**

- 从非悚域进入悚域时，即使旅行事件取消、玩家仍留在原世界，出口生成逻辑仍会继续；若目标位置不是悚域传送门且出口模板存在，仍会尝试放置出口结构。
- `teleported=true` 留下，实体在原传送门内无法自然重试，需离开后再进入。
- 成功后的处理继续使用旧实体和提前捕获的 `world`，对普通生物不符合该 API 的实体替换语义。当前相同坐标可能掩盖问题；后续修复安全落点时，旧对象位置不能用来定位新出口或更新新实体状态。

**建议：** 明确区分目标缺失、旅行取消、实际迁移成功和出口准备失败。使用返回的目标实体处理成功后的状态；失败时不要提交已传送标志，也不要继续生成出口。为准备出口与旅行事件取消之间设计清晰顺序，避免简单交换两行后仍在取消时留下世界修改。

## DP-005 [P2] 倒计时归零后离开门仍会被传送

**位置：** `src/main/java/com/zonlong/iceandfiredreadland/data/component/PortalData.java:46-66`。

进入 `tick()` 时首先判断旧计数是否为零并执行传送，到第 61 行才检查当前是否仍站在门内。

**确定的控制流：**

1. 某次 Post tick 在门内把计数从 1 减到 0。
2. 下一次实体 tick 移出传送门。
3. 下一次 Post tick 先看到旧计数为 0，仍会发起跨维度传送；后面的离门重置不能阻止这次调用。

倒计时同时被序列化并配置 `copyOnDeath()`。如果零值状态经保存恢复或复制进入新的位置，也缺少接触条件阻止陈旧状态触发；这些扩展场景未实测。

**建议：** 服务端先检查当前接触、存活及传送资格，再更新计数并作最终触发判定；失败或离开立即复位。短时接触倒计时通常不应跨死亡保留。传送完成后应结束当前更新，避免继续用旧世界引用运行后续逻辑。

## DP-006 [P2] 全生物附件的创建、存档和网络开销仍在

**位置：** `src/main/java/com/zonlong/iceandfiredreadland/registry/ModAttachments.java:22-35`。

旧问题确认仍存在，且可以更精确地描述：

- 所有 `LivingEntity` 的 Post tick 均调用 `getData()`，包括从未接触门的生物。
- NeoForge `AttachmentHolder.getData()` 首次访问即创建并保存到附件集合，同时调用同步入口。注册了 serializer，数据会跟随实体序列化。
- `.sync(PACKET_CODEC)` 默认向收到该实体的所有客户端同步，而非仅给本地玩家。
- 门内倒计时每次变动都标记 dirty，并发送完整附件；客户端和服务端也都会执行计数运算。
- 当前 HUD 只读取 `PortalRenderHelper.TICK`，没有读取附件倒计时。代码中不存在这些逐 tick 同步的显示消费者。

**影响边界：** 不在门内的实体并非每 tick 都发包，但仍每 tick 查附件和方块，并在初次创建/追踪时增加同步、在存档时增加数据。门内倒计时变化才持续发包。NeoForge 的客户端同步调用会被服务端类型检查短路，不能把这条问题描述成客户端不断向服务端回传包。

**建议：** 接触时才创建状态，已有状态按需更新，传送权威逻辑只在服务端执行。根据 UI 的真实需要决定是否完全移除该同步，或仅对所属玩家同步进入、离开、完成等状态边界；避免全实体逐 tick 广播。若改成 `entityInside`，需确保仍覆盖旧式无框出口与 X/Z 框架门两类形态。

## DP-007 [P2] RenderType 未缓存且所有方块无条件提交六面

**位置：**

- `src/main/java/com/zonlong/iceandfiredreadland/registry/ModRenderTypes.java:17-28`。
- `src/main/java/com/zonlong/iceandfiredreadland/render/DreadPortalBlockEntityRenderer.java:24-56`。

旧问题确认仍存在。Minecraft 1.21.1 的 `RenderType.create()` 直接构造新 `CompositeRenderType`，不会按名称自动复用。`MultiBufferSource.BufferSource` 按 RenderType 获取 buffer；创建新的非固定类型时会结束上一个共享类型的批次。

因此，同一个 3×5 门面的 15 个 BER 使用 15 个不同的 RenderType，除了对象分配，还打断相邻门块合批。最终 GPU draw call 数取决于实际渲染顺序和管线，本次没有进行 GPU 采样。

此外，BER 对每个方块无条件提交全部六面，完全没有邻居或轴向检查。完整 3×5×1 的方块区域提交 90 个四边形，其中 44 个是内部相邻面；按同样立方体几何只保留外表面时应为 46 个。这是几何提交数量，不能直接等同为同等比例的帧率损失。

**建议：** 缓存稳定 RenderType，保留 Shader supplier 在资源重载后取最新实例的方式；剔除同类相邻内部面。框架门是否改为薄平面属于视觉选择，应与旧出口的 2×2×4 实体形态分别处理，不能直接把所有门块统一成薄面。

## DP-008 [P2] HUD 计数无上限且退出世界不清理

**位置：**

- `src/main/java/com/zonlong/iceandfiredreadland/render/PortalRenderHelper.java:16-29`。
- `src/main/java/com/zonlong/iceandfiredreadland/mixin/GuiMixin.java:32-33`。

门内每 tick 都执行 `TICK++`，不受服务器倒计时和 `teleported` 限制。离门后每 tick 只减一。Mixin 仅在渲染时钳制显示值，没有钳制累计状态。

**触发条件与影响：** 在抵达出口后继续站在门里较久，再离开，遮罩会先保持满值一段时间，随后才开始淡出；等待时间随停留时长增加。断开世界时 `world/player == null` 分支直接返回，也没有清空静态计数，随后进入另一个世界可能显示上次的遮罩。

**建议：** 使用有界的显示进度，离门以固定时长淡出，并在退出/更换世界时重置。若要与传送倒计时同步，应明确显示策略并考虑配置值；不宜继续借用冻结所需 tick 作为无关联的传送参数。

## DP-009 [P2] 跳过实体使用传送门的资格检查

**位置：** `src/main/java/com/zonlong/iceandfiredreadland/data/component/PortalData.java:46-56`。

当前对所有生物，只要倒计时归零就直接调用 `changeDimension()`。没有调用 `canUsePortal(false)`、`canChangeDimensions(oldLevel, newLevel)`，也没有类似原版的目标维度启用检查。

Minecraft 1.21.1 将这些检查放在 `Entity.handlePortal()` 中，而非由 `changeDimension()` 全部代劳：基础 `canUsePortal(false)` 排除乘客和非存活实体，`LivingEntity` 另排除睡眠；末影龙明确对 `canUsePortal()` 返回 false。

**触发条件与影响：** 骑乘者脚部落在门内时也会积累计数；普通非玩家乘客随后由底层 `changeDimension()` 调用 `unRide()` 强制拆乘。不允许正常通过门的生物也可能被这条路径传送。其他模组重写 `canChangeDimensions()` 的约束会被跳过。

**建议：** 在计时及最终提交前执行资格检查，明确载具/乘客是否允许一起传送，并尊重实体对跨维度的限制。使用原版 Portal/PortalProcessor 体系可以减少重复实现，但自定义实现同样可以保留，关键是完整处理其承担的约束。

## 对“迁移旧代码导致体系冲突”的判断

对照用户提供的 IceAndFire-CE 仓库时，当前 master `018d56a` 已没有悚域传送门。Git 历史显示 `40f1345` 于 2026-07-18 删除了这套内容；本次使用其父提交 `4cacb3c39b8492ddc1daf3ae00d3afdefb474f87` 核对原实现。

原实现已经使用 `DimensionTransition`、NeoForge Attachment、现代 Codec 等机制。因此，不能将当前问题简单归因于还在使用旧 Forge Teleporter API。项目也已做命名空间迁移、独立注册和客户端入口隔离。

主要遗留是：原始传送没有安全落点；通过附件轮询重做了部分原版 Portal 状态机，却没有完整处理资格、取消和实体替换；自定义 Shader 没有光影兼容路径。

可清理但不计入上述 9 项故障的代码：

- 本地 `util/DreadBlock.java` 只由门块实现，其唯一 `UNBREAKABLE` 常量没有其他引用，也没有加入门块状态定义。这是闲置标记，不会自动接入上游同名概念。
- `PortalData.get(Player)` 没有调用者，连带其只用于该方法的导入可清理。
- `DreadPortalBlock.animateTick()` 的 `k` 未使用，`i=3` 可直接表达。
- 门块声明 `randomTicks()`，但没有随机 tick 行为；客户端粒子使用的是 `animateTick()`，二者应区分。
- `ModRenderTypes` 的 `RenderSystem` import 未使用；附件抽象和单字段 `RenderVariables` 可按重构需要精简，但它们仍有调用链，不应直接认定为无效代码删除。

## 旧文档复核

| 原文档条目 | 本次结论 | 对应问题 |
| --- | --- | --- |
| `docs/known-issues.md`：传送门附件作用于所有生物 | 仍存在；明确区分初次创建/追踪同步与门内逐 tick 同步 | DP-006 |
| `docs/known-issues.md`：RenderType 重复创建 | 仍存在；进一步确认会打断共享缓冲合批 | DP-007 |
| 同条目建议检查内部面 | 已确认当前全部提交，无邻居剔除 | DP-007 |

`docs/code-review-post-0.1.0.md` 的主要发现涉及画像，本次没有扩大为画像模块重新审查。此前明确保留的 Uranus 依赖也未作为垃圾依赖处理。

## 尚需运行验证的项目

- 两种框架朝向、混合标签框架、拆框级联清理、旧式无框出口的行为。
- 高山、海面、洞穴、建筑旁、世界边界、不同维度高度范围的往返。
- 多入口靠近同一目标区域、出口模板缺失/放置失败、跨维度事件取消。
- 玩家与非玩家实体、载具/乘客、死亡/重登后的倒计时。
- 无 Iris、Iris 未启用光影、Iris 开启光影、切换光影包、资源重载。
- 大量生物时的附件数量/存档体积/包数量，以及多门场景的 CPU/GPU 开销。
- 门内长时间停留后离门、断线重连、切换世界的 HUD 清理。

框架形状检测及固定 3×5 约束已阅读，未发现足够依据新增一个确定故障；未实测不等于已经通过上述场景。

## 验证记录与限制

- 阅读当前传送链、相关渲染/资源、原迁移设计及框架设计；解码出口 NBT 并确认完整尺寸与方块数量。
- 对照 IceAndFire-CE 历史实现、项目缓存的 NeoForge `21.1.233` 源码包和 Iris 官方 `1.21.1` 分支。
- `gradlew.bat build --offline --no-daemon`：在配置阶段失败，缺少 NeoGradle `7.1.38` 等依赖缓存。
- `gradlew.bat build --no-daemon`：在配置阶段失败，连接 `maven.neoforged.net:443` 下载依赖超时，尚未进入 Java 编译。
- 未获得本轮编译、GameTest 或游戏内画面通过证据；没有把依赖下载失败认定为功能源码编译错误。
- 本轮新增本审查文档，未修复上述问题，也未修改模组功能源码。

## 建议修复顺序

1. 先统一处理安全落点、出口占地保护、失败回滚和返程规则（DP-001、002、004）。
2. 调整传送状态机与实体资格，去掉全生物无条件创建/同步（DP-005、006、009）。
3. 缓存并优化渲染，加入 Iris 光影分支，同时保留旧出口与框架门两种形态（DP-003、007）。
4. 修复 HUD 生命周期并清理明确闲置代码（DP-008）。
5. 按上述场景完成行为和画面验证，再评估是否发布。

## 修复交接状态（2026-10-15）

执行计划：[悚域传送门修复计划（DeepSeek 执行交接）](plans/2026-10-15-dread-portal-fix-plan.md)。

本报告负责记录问题与证据；具体修复由用户转交给 DeepSeek。当前助手仅编写报告和计划，没有执行功能修改，也没有将任何问题标为已修复。

| 问题 | 对应计划任务 | 当前状态 |
| --- | --- | --- |
| DP-001 | T2、T4 | 未修复 |
| DP-002 | T3、T4 | 未修复 |
| DP-003 | T5、T6 | 未修复 |
| DP-004 | T4 | 未修复 |
| DP-005 | T1、T4 | 未修复 |
| DP-006 | T1 | 未修复 |
| DP-007 | T5 | 未修复 |
| DP-008 | T7 | 未修复 |
| DP-009 | T1、T4 | 未修复 |

计划通过“外部安全等待点 + 成功回调提交出口”细化失败处理：旅行前只做只读预检与数据准备；旅行取消时不写入出口；实际迁移成功后才放置，并为放置失败保留安全位置。此处是计划说明，不是已实现结论。

执行者只有在对应代码、行为用例及必要实机画面均有证据后才能更新状态。环境阻塞、源码推断、编译通过和游戏验证通过必须分别记录。
