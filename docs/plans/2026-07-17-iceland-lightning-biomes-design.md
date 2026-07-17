# 冰域与雷域群系设计文档

**日期:** 2026-07-17
**状态:** 已批准
**方案:** A — 最小化复制，先搭建骨架，后续迭代定制

## 问题陈述

悚域维度当前只有悚域（冷）和火域（热）两组群系，温度分区覆盖不完整。需要新增冰域和雷域，形成四域对称的温度渐变体系。

## 设计

### 温度分区架构

```
温度:   -1 ───── -0.4 ───── 0.4 ───── 1
         │         │         │        │
       冰域      悚域      雷域     火域
      (冷冻)   (阴暗恐怖)  (温带)  (炎热)
```

冰域 ↔ 火域 完全对称（参数镜像），悚域 ↔ 雷域 完全对称。

### 多噪声参数布局

| 群系 | temp | humidity | continentalness | erosion | weirdness | depth |
|------|------|----------|-----------------|---------|-----------|-------|
| iceland_plains | -1 | -0.3 | 0.4 | 0.5 | 0 | 0 |
| iceland_forest | -1 | 0.3 | 0.8 | 0.2 | 0.2 | 0.5 |
| dread_plain | -0.4 | -0.5 | 0.4 | 0.5 | -0.1 | 0 |
| dread_forest | -0.4 | 0.5 | 0.8 | 0.2 | 0 | 0.6 |
| lightning_plains | 0.4 | -0.5 | 0.4 | 0.5 | -0.1 | 0 |
| lightning_forest | 0.4 | 0.5 | 0.8 | 0.2 | 0 | 0.6 |
| fireland_plains | 1 | -0.3 | 0.4 | 0.5 | 0 | 0 |
| fireland_forest | 1 | 0.3 | 0.8 | 0.2 | 0.2 | 0.5 |

### 群系文件改动摘要

**冰域** (iceland_plains / iceland_forest):
- `temperature`: -0.5
- `downfall`: 0.5 / 0.9
- 颜色值暂用原版冰雪占位
- 移除火焰特征（岩浆湖、patch_fire）
- 移除 Blaze 生成

**雷域** (lightning_plains / lightning_forest):
- `temperature`: 0.5
- `downfall`: 0.6 / 0.9
- 颜色值暂用原版灰紫占位
- 移除火焰特征（岩浆湖、patch_fire）
- Blaze 生成待定

### 需要创建的文件

```
worldgen/biome/
├── iceland_plains.json       ← 复制自 fireland_plains
├── iceland_forest.json       ← 复制自 fireland_forest
├── lightning_plains.json     ← 复制自 fireland_plains
└── lightning_forest.json     ← 复制自 fireland_forest
```

### 需要修改的文件

| 文件 | 改动 |
|------|------|
| `dimension/dreadland.json` | 更新 dread 群系 temperature → -0.4；添加 4 个新群系到 biome_source |
| `noise_settings/dreadland.json` | surface_rule 中添加冰域/雷域的地表方块规则 |

## 决策记录

- **温度梯度:** -1（冰）→ -0.4（悚）→ 0.4（雷）→ 1（火），对称分布
- **方案选择:** 先最小化复制（方案A），后续迭代定制视觉和特征
- **悚域温度调整:** 从 -1 改为 -0.4，为冰域腾出 -1 位置
- **雷域 ↔ 悚域对称:** 雷域参数直接镜像悚域

## 非目标

- 本阶段不设计冰域/雷域的视觉审美（颜色、粒子等）
- 本阶段不创建新的自定义方块或地物特征
- 不修改火域现有配置

## 下一步

调用 planning 技能创建实现计划，或直接执行实现。
