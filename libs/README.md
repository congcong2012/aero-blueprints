# libs/

本地依赖，**不会**被打包进模组 jar。

| 文件 | 坐标 |
| --- | --- |
| `create-1.21.1-6.0.10-280.jar` | `com.simibubi.create:create-1.21.1:6.0.10-280` |
| `sable-neoforge-1.21.1-2.0.6.jar` | `dev.ryanhcode.sable:sable-neoforge-1.21.1:2.0.6` |
| `sable-companion-common-1.21.1-1.6.0.jar` | `dev.ryanhcode.sable-companion:sable-companion-common-1.21.1:1.6.0` |
| `jij/` | Create 自带的 jar-in-jar：Ponder + Catnip、Flywheel、Registrate |

`build.gradle` 优先使用这些本地 jar（`files(...)`），文件齐全时编译和 `runServer` / `runClient`
都不需要联网；删掉它们会退回从 `maven.createmod.net` / `maven.ryanhcode.dev` 拉取（`compileOnly`，
只够编译，开发环境起不来）。

运行游戏时，这些模组由玩家 mods 文件夹里的正式版本提供。
