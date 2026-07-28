# 待修复问题

本文档记录已经确认、但计划在后续版本中处理的技术债。当前版本不改变相关行为。

## 传送门附件作用于所有生物

**状态：** 待优化

`ModAttachments.onLivingTick` 当前会在每个生物的每个 tick 中调用 `getData()`。这会为从未接触过悚域传送门的生物创建、持久化并可能同步 `PortalData`，增加服务端内存、存档和网络开销。传送倒计时变化时还会逐 tick 同步附件。

后续修复应满足：

- 仅为接触悚域传送门或已经持有相关状态的实体创建和更新附件。
- 仅由服务端更新权威传送状态，客户端通过同步数据负责显示。
- 减少倒计时同步频率，同时保持传送判定和界面效果一致。
- 验证大量生物存在时的 tick 时间、附件数量和网络包数量。

相关文件：`src/main/java/com/zonlong/iceandfiredreadland/registry/ModAttachments.java`、`src/main/java/com/zonlong/iceandfiredreadland/data/component/PortalData.java`。

## 传送门 RenderType 重复创建

**状态：** 待优化

`DreadPortalBlockEntityRenderer` 每次渲染传送门方块时都会调用 `ModRenderTypes.getDreadlandsPortal()`，而该方法会重新执行 `RenderType.create()`。这会产生不必要的对象分配，并影响渲染缓冲复用。

后续修复应满足：

- 将悚域传送门的 `RenderType` 缓存为稳定的静态实例。
- 保持 Shader 在客户端注册完成后可以正常取得。
- 检查相邻传送门方块的内部面是否可以跳过，降低重复绘制和 overdraw。
- 在 X、Z 两种朝向的完整 `3×5` 传送门区域中验证纹理、Shader 和透明渲染顺序。

相关文件：`src/main/java/com/zonlong/iceandfiredreadland/registry/ModRenderTypes.java`、`src/main/java/com/zonlong/iceandfiredreadland/render/DreadPortalBlockEntityRenderer.java`。
