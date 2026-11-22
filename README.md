# Aero Blueprints 航空蓝图

一个 **机械动力：航空学（Create: Aeronautics）** 附属模组：让 **蓝图加农炮** 可以把蓝图直接打印到
**物理结构**（Sable 子关卡 / 飞艇 / 飞机 / 车辆）上。打印出来的方块会成为结构本身的一部分，
随船一起移动、旋转，并正常参与质量、浮力与碰撞计算。

## 环境

| 项目 | 版本 |
| --- | --- |
| Minecraft | 1.21.1 |
| 加载器 | NeoForge 21.1.228+ |
| 机械动力 Create | 6.0.10+ |
| Sable | 2.0.x（机械动力：航空学自带依赖） |

机械动力：航空学（Create: Aeronautics）本身不是硬依赖：任何基于 Sable 子关卡的结构都能用。

## 它解决什么问题

Sable 把物理结构的方块存在一块独立的 **plot**（保留区块网格）里，再用一个会移动、旋转的 pose
把这些坐标映射到世界。而原版蓝图加农炮是一台"世界坐标机器"：它在 `蓝图锚点 + 相对偏移` 放置方块。

于是把蓝图部署在飞艇上、再用加农炮打印时，方块会落在世界里的空气中，不会变成船的一部分。

装上本模组后：

1. 加农炮开始打印时，检测蓝图锚点是否落在某个物理结构上；
2. 如果是，就记下这个锚点在结构内部（plot 坐标）的位置；
3. 之后每一发炮弹，都会把世界坐标的目标换算成 plot 坐标再放置。

因为 plot 坐标是固定的，**即使打印途中飞艇在漂浮、旋转、被撞得晃动**，方块依然会落在船体上
同一个位置。

## 使用

1. 把飞艇 / 飞机停在合适的位置（不需要完全静止，但停稳更好瞄准）。
2. 手持蓝图右键，把全息投影摆到结构上（锚点落在船体或船体包围盒内即可），确认部署。
3. 在 256 格范围内放好蓝图加农炮，插入蓝图。
4. 提供材料、火药，开炮。

打印时：

- 方块需求清单、替换模式、跳过缺失、合并方块实体等判定，都按**船上真实的方块**计算，
  而不是按世界里的空气计算；
- 传送带会整条链一起生成在结构内部；
- 打印范围超出结构现有区块时，会自动为结构开辟新的 plot 区块；
- 蓝图里的实体（盔甲架、物品展示框等模板实体）仍按世界坐标生成，会落在结构表面。

## 判定规则

| 情况 | 行为 |
| --- | --- |
| 蓝图锚点在 plot 坐标里（站在船上部署的蓝图） | 自动换算成世界坐标加载，再映射回结构放置 |
| 蓝图锚点在世界中，且落在某个子关卡的包围盒内（允许 1.5 格外扩） | 启用坐标换算 |
| 蓝图锚点在世界中，找不到结构 | 完全走原版行为，无副作用 |
| 加农炮放在船上、蓝图部署在世界中 | 256 格射程检查会自动换算到同一坐标系 |
| 蓝图部署在船上、加农炮放在世界 | 同上 |

## 配置

`config/aeroblueprints-common.toml`

```toml
[general]
    # 是否允许蓝图加农炮打印到物理结构
    enableSubLevelPrinting = true
    # 打印会话建立时输出日志，便于排查
    debugLogging = false
```

## 构建

```bash
./build.sh build
# 产物：build/libs/aeroblueprints-1.21.1-1.0.1.jar

./build.sh runServer    # 起一个带 Create + Sable 的开发服务端
./build.sh runClient    # 起开发客户端
```

需要 JDK 21。工程使用 NeoForge ModDev，Create / Sable 仅作为运行期外部依赖，不会打包进 jar。

`libs/` 里放了本地依赖副本（`create-*`、`sable-*`、`sable-companion-*`），`libs/jij/` 是
Create 自带的三件套（Ponder/Catnip、Flywheel、Registrate）。有这些文件就能离线编译和启动开发环境；
删掉后 `build.gradle` 会退回从 maven 下载。

### 启动自检

开发或排查兼容问题时可以打开自检，它会在模组加载阶段就强制加载被 patch 的 Create 类，
并在服务端启动后跑一遍完整的打印流程（组装子关卡 → 加载蓝图 → 模拟一发炮弹）：

```bash
JAVA_TOOL_OPTIONS="-Daeroblueprints.selftest=true" ./build.sh runServer
```

日志里应当出现五行 `self-test: ... loaded (session=..., plotTarget=...)`，以及
`[diag] shot: ... landed=Block{minecraft:gold_block}`。
本模组已在 Create 6.0.10-280 + Sable 2.0.6 的开发服务端上验证通过这套自检。

## 实现概要

| 类 | 作用 |
| --- | --- |
| `SubLevelPrinting` | 找到锚点所在的 Sable 子关卡、世界坐标 ↔ plot 坐标换算、射程检查坐标对齐 |
| `SubLevelPrintSession` | 一次打印会话：世界锚点 + plot 锚点 + 子关卡 UUID，可存 NBT |
| `PlotChunkHelper` | 方块落地前确保目标 plot 区块存在（大范围打印需要） |
| `SchematicPrinterMixin` | 建立/清除会话；把 `shouldPlaceBlock` 的判定目标换成 plot 坐标 |
| `SchematicannonBlockEntityMixin` | 给每一发炮弹打上 plot 落点；修正 256 格射程检查 |
| `LaunchedItemMixin` | 让落点随炮弹保存，读档后仍然有效 |
| `LaunchedItemForBlockStateMixin` | 方块落点改为写入 plot |
| `LaunchedItemForBeltMixin` | 传送带整条链改为在 plot 内生成 |

Sable 会拦截 plot 区块的方块读写，所以写入 plot 和写入世界用的是同一套
`Level#setBlock` / `BlockHelper#placeSchematicBlock`：方块变更会自动回流到质量追踪、碰撞形状
与客户端同步，不需要额外通知物理系统。

## 排查

打印开始时日志会输出一行：

```
[Aero Blueprints] schematic loaded: anchor=... errored=... empty=... session=...
[Aero Blueprints] blueprint anchor ... is plot space; loading it at world ...
```

- `empty=true`：蓝图内容没读出来。Create 要求 `SCHEMATIC_OWNER` 非空、文件名以 `.nbt` 结尾、
  文件位于 `schematics/uploaded/<owner>/` 下；不满足时加农炮会判定「蓝图已过期」并**消耗掉蓝图**，
  表现为放进去就没了、什么都不打印。这种情况与本模组无关。
- `errored=true`：蓝图解析时抛了异常，日志上方会有 Create 的 `Failed to load Schematic for Printing` 堆栈。
- `session=null` 且锚点在世界中：说明锚点附近没有物理结构，走的是原版打印。

## 已知限制

- 蓝图锚点必须落在结构上或结构包围盒附近；对着空气部署蓝图不会触发换算。
- 蓝图实体（非方块）不会跟随结构内部坐标，按世界位置生成。
- 打印期间拆除结构，会话会自动失效并退回原版行为。
- 只处理 Sable 子关卡；原版机械动力的动态结构（轴承、活塞）不在范围内。
