# Ice And Fire: Dread Land

Ice And Fire: Dread Land 是 Ice And Fire: Community Edition 的 NeoForge 附属模组，为 Minecraft 添加全新的悚域维度、四类温度主题群系、悚域传送门以及雷域生物机制。

## 模组信息

| 项目 | 内容 |
| --- | --- |
| 模组名称 | Ice And Fire: Dread Land |
| Mod ID | `iceandfire_dreadland` |
| 当前版本 | `0.0.2` |
| Minecraft | `1.21.1` |
| Mod Loader | NeoForge `21.1.233` |
| 许可证 | MIT |
| 作者 | PorkChop-ZLG |

## 主要功能

### 悚域维度

- 添加维度 ID：`iceandfire_dreadland:dreadland`。
- 使用噪声世界生成，包含八个群系：悚域、冰域、雷域、火域的平原和森林。
- 不同群系拥有独立的地表、植被、湖泊、树木和生物生成配置。
- 使用 Ice And Fire 的冻结、龟裂和焦黑方块作为部分群系的地表材料。
- 通过群系标签接入 Ice And Fire 的结构生成和生物生成，例如冰域、雷域和火域相关内容。

### 悚域传送门

- 添加方块：`iceandfire_dreadland:dread_portal`。
- 使用 `#iceandfire_dreadland:dreadland_portal_frame` 标签中的方块搭建竖直矩形框架，默认框架方块为 `iceandfire:dread_stone`。
- 框架遵循原版下界传送门尺寸规则：内部宽度为 2-21 格、内部高度为 3-21 格，四个角可以省略。
- 同一个框架可以混合使用标签中的不同方块；数据包可以向该标签追加兼容方块。
- 使用悚域之钥右键框架朝向内部空间的一面即可激活。成功激活后，生存模式消耗一把钥匙，创造模式不消耗。
- 由钥匙生成的传送门依赖框架存在；框架被破坏后，内部传送门方块会联动消失。
- 创造模式直接放置的传送门方块以及已有出口结构中的传送门不依赖框架，继续保持原有行为。
- 生物站在传送门方块内时开始倒计时，默认等待 `100` tick，即约 5 秒。
- 非悚域维度中的生物会被传送到悚域；悚域中的生物会返回主世界。
- 生物抵达悚域后，如果目标位置不是传送门，会自动放置悚域出口结构。
- 传送门包含自定义方块实体渲染、粒子效果、纹理和 Shader 效果。

当前版本没有提供钥匙和传送门的合成配方，可以通过创造模式物品栏或命令获取：

```mcfunction
/give @s iceandfire_dreadland:dreadland_key
/give @s iceandfire_dreadland:dread_portal
```

### 雷域机制

在雷域平原和雷域森林中，苦力怕加入世界时会自动受到闪电影响并转化为闪电苦力怕。

默认情况下，闪电为视觉效果，不产生火焰或方块破坏。启用破坏性雷暴后，会使用原版闪电的完整破坏效果。

### 配置界面

模组使用 Jupiter 提供配置管理和配置界面。客户端可以从模组列表进入配置页面；服务端配置由管理员控制。

## 依赖

运行模组需要安装以下前置：

| 依赖 | 版本要求 |
| --- | --- |
| Minecraft | `1.21.1` |
| NeoForge | `21.1.233` 或更高的兼容版本 |
| Ice And Fire: Community Edition | `1.21.1` 或更高的兼容版本 |
| Jupiter | `2.3.7` 或更高版本 |

## 安装

1. 安装 Minecraft `1.21.1` 和对应的 NeoForge。
2. 将 Ice And Fire: Community Edition、Jupiter 和本模组的 `.jar` 文件放入 `mods` 文件夹。
3. 使用 NeoForge 配置启动游戏。
4. 进入世界后，从创造模式物品栏获取悚域之钥和传送门，或使用上面的 `/give` 命令。

## 配置

配置文件路径：

```text
config/iceandfire_dreadland/common.json
```

| 配置键 | 默认值 | 范围 | 说明 |
| --- | ---: | --- | --- |
| `portalTeleportTick` | `100` | `0-600` | 传送门倒计时，单位为 tick。20 tick 约等于 1 秒。 |
| `lightningDestructive` | `false` | `true/false` | 是否启用会产生火焰、破坏方块和破坏物品的完整闪电效果。 |

## 开发与构建

开发环境要求 JDK 21。Windows 下可以使用：

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat runServer
.\gradlew.bat runData
```

Linux/macOS 下可以使用：

```bash
./gradlew build
./gradlew runClient
./gradlew runServer
./gradlew runData
```

构建产物位于：

```text
build/libs/iceandfire_dreadland-0.0.2.jar
```

## 项目结构

```text
src/main/java/                         Java 逻辑、注册表、事件和渲染代码
src/main/resources/assets/             纹理、模型、语言文件、粒子和 Shader
src/main/resources/data/               维度、群系、世界生成和标签数据
docs/plans/                            功能设计和实现计划
.github/workflows/build.yml            GitHub Actions 构建流程
```

## 构建状态

项目当前包含完整的 Gradle 构建流程。执行 `gradlew build` 可以完成资源处理、Java 编译和模组打包；当前仓库暂未配置自动化测试源集。
