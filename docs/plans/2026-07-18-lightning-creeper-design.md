# 雷域苦力怕闪电机制设计文档

**日期:** 2026-07-18
**状态:** 已批准
**方案:** A — `@EventBusSubscriber` 独立事件处理器

## 问题陈述

在悚域维度的雷域群系（`is_lightning` 标签）中，苦力怕生成时应自动被闪电击中，变为闪电苦力怕。触发时机包括：自然生成、chunk 加载生成、刷怪蛋召唤。

## 设计

### 架构

```
EntityJoinLevelEvent
  → 实体是 Creeper？
    → 在服务端？
      → 在悚域维度？
        → 群系有 is_lightning 标签？
          → 生成原版 LightningBolt
```

### 组件

| 文件 | 作用 |
|------|------|
| `registry/tag/ModBiomeTags.java` | 群系标签常量 `IS_LIGHTNING`，映射到 `iceandfire_dreadland:is_lightning` |
| `event/handler/DreadlandServerEvents.java` | 事件处理器，监听 `EntityJoinLevelEvent`，执行闪电生成逻辑 |

### 数据流

1. `EntityJoinLevelEvent` 在实体加入世界时被 NeoForge 触发
2. `DreadlandServerEvents.onEntityJoinLevel` 检查实体类型是否为 `Creeper`
3. 检查 `level.isClientSide()` 确保只在服务端执行
4. 检查 `level.dimension()` 是否为 `ModDimensions.DREADLAND`
5. 通过 `level.getBiome(entity.blockPosition()).is(ModBiomeTags.IS_LIGHTNING)` 检查群系
6. 使用 `EntityType.LIGHTNING_BOLT.create(level)` 创建原版闪电
7. `level.addFreshEntity(bolt)` 将闪电加入世界
8. 原版机制自动处理：苦力怕 → 闪电苦力怕、物品破坏、火焰

### 没有错误处理——所有异常情况都是静默跳过

- 不是苦力怕 → return
- 在客户端 → return
- 不在悚域 → return
- 不在雷域群系 → return

## 决策记录

- **使用 `EntityJoinLevelEvent` 而非 `FinalizeSpawnEvent`:** `EntityJoinLevelEvent` 比 `FinalizeSpawnEvent` 更早触发，且覆盖所有实体加入世界的方式（包括刷怪蛋、chunk 加载）
- **使用原版 LightningBolt 而非自定义实体:** 无需额外渲染或网络同步，完全复用原版机制
- **TagKey 而非字符串内联:** 提取到 `ModBiomeTags` 常量类，匹配 IceAndFire-CE 模式，便于其他模块复用

## 非目标

- 不在雷域群系添加持续闪电天气效果
- 不修改苦力怕的 AI 或属性
- 不阻止闪电破坏物品（使用原版行为）

## 下一步

Invoke planning skill or directly implement——两个简单文件（约 50 行总代码）。
