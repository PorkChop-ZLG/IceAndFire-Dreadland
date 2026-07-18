# Dread Portal 迁移设计文档

**日期:** 2026-07-18
**状态:** Approved
**来源:** 照搬 IceAndFire-CE 的 dread_portal 实现到本模组

## 问题陈述
将冰火传说社区版的 dread_portal 传送门方块完整迁移到 IceAndFire-DreadLand 模组中，
实现主世界/任意维度 ↔ 悚域的双向传送。

## 设计决策

### 核心改动
1. 传送目标维度改为 `iceandfire_dreadland:dreadland`
2. 支持从任何非悚域维度进入悚域（不只是主世界）
3. 移除 DreadPortalProcessor（出口结构不做方块随机化）
4. 物品形态使用简单 item model（不用 MiscItemRenderer）
5. 注册体系从 Iaf 前缀改为 Mod 前缀
6. 主类重构为分散注册类模式

### 架构
```
Block (DreadPortalBlock)
  └── BlockEntity (DreadPortalBlockEntity) → BER → Shader + Textures
  └── animateTick() → Particle
  └── 站立检测 → PortalData (Attachment) → 100 tick 倒计时 → 维度传送
      └── 到达悚域时放置 dread_exit_portal.nbt 结构
  └── GuiMixin + PortalRenderHelper → GUI 覆盖层
```

### 删除项
- DreadPortalProcessor.java
- ModProcessors.java
- MiscItemRenderer（不使用）
- DynamicItemRenderer 注册

### 文件清单
Java: 22 个文件
资源: 14 个文件

## 下一步
使用 /execute 或手动逐步实施。
