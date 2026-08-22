# 0.1.0 之后代码审查结果

**日期：** 2026-08-23  
**审查范围：** `git diff 7be4b24`（0.1.0 提交）到当前工作区  
**审查方式：** 子代理代码审查 + 作者人工确认  
**状态说明：** 已编号，便于后续修复追踪。

---

## 审查发现项

### REV-001 [Major] `skipRendering` 可能剔除画的正面
- **文件：** `src/main/java/com/zonlong/iceandfiredreadland/block/DreadQueenPortraitBlock.java`（约 174-177 行）
- **问题：** `skipRendering` 对 `direction == FACING` 返回 `true`，可能把画的正面也剔除，导致从正面看不到画。
- **建议：** 只剔除同一幅画内部相邻方块之间的面；不要剔除朝向观察者的正面。
- **状态：** 已关闭（REV-013 实测正面显示正常，无法复现）。

### REV-002 [Major] 放置时对“含水非空气方块”判断过宽
- **文件：** `src/main/java/com/zonlong/iceandfiredreadland/item/DreadQueenPortraitItem.java`（约 47 行）
- **问题：** `!current.isAir() && !current.getFluidState().is(Fluids.WATER)` 会把含水台阶、告示牌、箱子等非空气方块也当作可替换，可能直接覆盖真实方块且不掉落。
- **建议：** 改用 `current.canBeReplaced()` 判断；水是可替换的，真实方块不可替换。
- **状态：** 已修复。

### REV-003 [Major] `onRemove` 缺少客户端侧保护
- **文件：** `src/main/java/com/zonlong/iceandfiredreadland/block/DreadQueenPortraitBlock.java`（约 111-117 行）
- **问题：** 掉落/抑制逻辑没有 `!level.isClientSide` 保护，客户端应用服务器方块更新时也可能生成掉落物或访问服务端静态集合。
- **建议：** 将整个掉落逻辑包在 `if (!level.isClientSide) { ... }` 中。
- **状态：** 已修复（掉落逻辑已重构为标准战利品表路径）。

### REV-004 [Minor] 静态 `Map` 持有 `Level` 引用，存在泄漏风险
- **文件：** `src/main/java/com/zonlong/iceandfiredreadland/block/DreadQueenPortraitBlock.java`（约 45、103-120 行）
- **问题：** `CREATIVE_SUPPRESSED_DROPS` 是静态 `IdentityHashMap<Level, Set<BlockPos>>`，强引用 `Level`，且集合清空后不会移除 `Level` 条目，跨世界/重进世界可能造成泄漏。
- **建议：** 当 `Set` 为空时移除对应 `Level` 条目；或改用更安全的作用域存储。
- **状态：** 已修复（已移除静态 `Map`）。

### REV-005 [Minor] 放置后只使用 `UPDATE_CLIENTS`，缺少邻居更新
- **文件：** `src/main/java/com/zonlong/iceandfiredreadland/item/DreadQueenPortraitItem.java`（约 63 行）
- **问题：** 放置 9 个方块时使用 `Block.UPDATE_CLIENTS`，避免放置过程中触发自毁，但组装完成后没有触发邻居更新/`gameEvent`。
- **建议：** 放置完成后统一触发一次邻居更新或 `gameEvent(BLOCK_PLACE)`。
- **状态：** 已修复。

### REV-006 [Minor] 掉落逻辑绕过标准战利品表机制
- **文件：** `src/main/java/com/zonlong/iceandfiredreadland/block/DreadQueenPortraitBlock.java`（约 110-125 行）
- **问题：** 手动在 `onRemove` 掉落，`getDrops` 返回空，绕过了战利品表/模组掉落修改器。
- **建议：** 后续可考虑改为标准战利品表路径，或至少集中封装掉落逻辑。
- **状态：** 已修复（已改为标准 `getDrops` + `destroyBlock` 路径）。

### REV-007 [Nit] JSON 文件存在 BOM/缩进不一致
- **文件：** `blockstates/dread_queen_portrait.json`、`models/block/dread_queen_portrait_*.json`
- **问题：** 生成的文件带 UTF-8 BOM，且缩进风格与项目其他手写 JSON 不一致。
- **建议：** 去除 BOM 并统一缩进。
- **状态：** 已修复（已去除 BOM，统一为 2 空格缩进）。

### REV-008 [Nit] 音效 step/hit/fall 复用了 `PAINTING_BREAK`
- **文件：** `src/main/java/com/zonlong/iceandfiredreadland/block/DreadQueenPortraitBlock.java`
- **问题：** 自定义 `SoundType` 的 step/hit/fall 都使用 `PAINTING_BREAK`，不是最优听感。
- **建议：** 可选优化为更合适的音量/音效。
- **状态：** 已修复（step/hit/fall 改为羊毛音效）。

---

## 已确认非问题（不修复）

### REV-009 无墙壁支撑校验
- **结论：** 这是用户明确要求的“悬空放置”功能，不是 BUG，应保留。

### REV-010 创造模式消耗物品
- **结论：** 用户实测创造模式不会消耗物品，子代理此条审查有误，不修复。

### REV-011 Uranus 依赖未在源码直接引用
- **结论：** 用户确认应保留 `implementation "curse.maven:uranus-1010827:6979353"`，不修改。

### REV-012 跨区块边界的 3×3 画可能自毁
- **结论：** 已按步骤实测，跨区块边界放置、保存重进、`F3+A` 重载、远距离往返均未出现自毁，判定为不存在。
- **状态：** 已关闭。

### REV-013 `skipRendering` 正面剔除问题
- **结论：** 已按步骤实测四种朝向，正面画面均正常显示，判定为不存在。
- **状态：** 已关闭。

---

## 待验证/不确定项

暂无。

## 整体结论

- 核心多方块数学、模型/贴图路径、语言键、含水结构整体正确。
- REV-001、REV-012、REV-013 已实测关闭。
- REV-002 ~ REV-008 已全部修复。
