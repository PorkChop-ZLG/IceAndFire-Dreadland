# 待修复问题

本文档记录已经确认、但计划在后续版本中处理的技术债。

## 2026-10-15 传送门修复执行结果

完整九项问题及证据见[悚域传送门审查报告](code-review-dread-portal-2026-10-15.md)，实施顺序和验收要求见[修复计划](plans/2026-10-15-dread-portal-fix-plan.md)。

T0–T8 已实施。下表区分「已改代码」与「已验证」，**未验证的项目不得当作通过**。

| 编号 | 问题 | 代码状态 | 验证状态 |
| --- | --- | --- | --- |
| DP-001 | 双向无安全落点 | 已实现 | GameTest 覆盖安全/熔岩/水/无支撑/预算/失败不带坐标 |
| DP-002 | 出口覆盖建筑 | 已实现 | GameTest 覆盖容器、实心建筑、基岩、回滚、等待点 |
| DP-003 | Iris 屏蔽自定义 Shader | **未实现** | 未验证 |
| DP-004 | 换维度失败无回滚 | 已实现 | GameTest 覆盖维度缺失；取消路径待实测 |
| DP-005 | 归零后离门仍传送 | 已实现 | GameTest 覆盖 |
| DP-006 | 全生物附件开销 | 已实现 | GameTest 覆盖附件生命周期；大量生物性能待实测 |
| DP-007 | RenderType 重复创建 | 已实现 | 面数已由 GameTest 验证；RenderType 实例身份与实机画面待实测 |
| DP-008 | HUD 计数无上限 | 已实现 | 进度数学已由 GameTest 验证；遮罩画面与断线清理待实测 |
| DP-009 | 跳过传送资格检查 | 已实现 | GameTest 覆盖乘客/载客/死亡实体 |

已执行的验证：`gradlew build --offline --no-daemon`（成功）与
`gradlew runGameTestServer --offline --no-daemon`（51 项全部通过）。
日志位于 `build/reports/dread-portal/`。

尚未执行、因此不能宣称通过的项目：

- **真实跨维度往返**。GameTestServer 用 `WorldPresets.FLAT` 构建世界，只注册
  `minecraft:overworld`、`minecraft:the_end`、`minecraft:the_nether`，不含悚域维度，
  因此自动化用例无法验证成功抵达、出口生成与返程。需在正式客户端/服务端实测。
- **客户端画面**：传送门外观、Shader、HUD 遮罩、光影开启/关闭与切换（DP-003、DP-007、DP-008）。
- **性能对照**：大量生物时的 tick 开销、附件数量与网络包数量（DP-006）。
- **旧存档加载**：带旧 `portal_data` 附件的实体实际反序列化（当前只用 Codec 直接验证）。

## 传送门附件作用于所有生物

**状态：** 已修复（DP-006）

`ModAttachments.onLivingTick` 已改为：仅服务端推进；未接触且从未持有状态时直接返回，
不再调用 `getData()`；附件在回到空闲时用 `removeData` 移除；注册时移除了
`.sync(...)` 与 `.copyOnDeath()`。旧存档字段名保持不变，旧数据一律归一化为空闲。

相关文件：`src/main/java/com/zonlong/iceandfiredreadland/registry/ModAttachments.java`、
`src/main/java/com/zonlong/iceandfiredreadland/data/component/PortalData.java`。

## 传送门 RenderType 重复创建

**状态：** 已修复（DP-007）

`ModRenderTypes` 现在持有单个静态 `RenderType` 实例，Shader 仍由 `ShaderStateShard`
的 supplier 延迟取用，因此资源重载后仍会采用新实例。渲染器按相邻门块剔除内部面：
完整 `3×5` 实心墙由 90 个提交面降至 46 个外表面，剔除 44 个内部面（已由 GameTest 验证）。

相关文件：`src/main/java/com/zonlong/iceandfiredreadland/registry/ModRenderTypes.java`、
`src/main/java/com/zonlong/iceandfiredreadland/render/DreadPortalBlockEntityRenderer.java`、
`src/main/java/com/zonlong/iceandfiredreadland/world/portal/DreadPortalGeometry.java`。

## Iris 光影下自定义 Shader 被屏蔽

**状态：** 待修复（DP-003）

在启用光影时，Iris 默认拒绝非 `ExtendedShader`/`FallbackShader` 的自定义核心 Shader，
当前门面会走被屏蔽的路径。本项尚未实现，需要客户端实测确认画面。

要求见审查报告 DP-003 与计划 T6。
