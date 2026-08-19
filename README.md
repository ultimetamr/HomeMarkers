# 家居点位标记器（Home Markers）

<p align="center">
  <img src="app/src/main/res/mipmap-anydpi/ic_spatial_launcher.png" width="160" alt="家居点位标记器应用图标">
</p>

家居点位标记器是一款基于 PICO Spatial SDK 的混合现实找物与收纳辅助应用。用户可以在真实家居物品或墙面、桌面等位置创建空间标签，记录物品名称、存放位置、使用备注和有效期；需要找物时，可通过查询面板快速定位对应标签。

## 主要功能

- **空间点位创建**：点击“创建”后隐藏主界面，通过手柄射线、手势捏合或指尖接触选择墙面或桌面。
- **完整标签信息**：记录物品名称、存放位置、使用备注和保质期/有效期。
- **持久化空间锚点**：使用 Persistent Spatial Anchor 保存标签位置，应用重新启动后恢复。
- **查询与定位**：支持名称、位置、备注和颜色的模糊查询，并提供相对方向与距离提示。
- **静态高亮**：目标标签保持 100% 不透明度，其他标签降至 18%，便于远距离识别且不产生视觉干扰。
- **颜色管理**：默认淡蓝色，并支持淡黄、淡粉、淡绿、淡紫和浅灰。
- **有效期提醒**：临期标签显示橙色边框，过期标签显示红色边框。
- **标签维护**：支持编辑、重新锚定和删除；删除时同步清理本地数据与空间锚点。
- **空间列表联动**：查询列表可以定位到空间标签，并从列表进入编辑、重锚和删除流程。

## 使用流程

### 创建标签

1. 启动应用，主界面只显示“创建”和“查询”两个入口。
2. 点击“创建”，主界面隐藏，应用进入表面选择状态。
3. 使用手柄扳机、手势捏合或指尖接触选中墙面/桌面。
4. 命中表面后填写物品信息并选择颜色。
5. 点击保存后创建持久化空间锚点和空间标签。

如果没有命中有效表面，应用会显示约 1.5 秒的提示，并保持等待放置状态。

### 查询物品

1. 点击“查询”。
2. 输入物品名称、位置、备注或颜色关键词。
3. 选择结果后，目标空间标签保持完全可见，其他标签自动降低透明度。
4. 根据查询面板中的方向和距离提示寻找物品。

## 技术方案

| 模块 | 实现 |
| --- | --- |
| 空间平台 | PICO Spatial SDK 0.13.3 |
| 开发语言 | Kotlin |
| 空间容器 | Full Space Mixed `DefaultStage` |
| 管理界面 | SpatialUI、Jetpack Compose、`PicoTheme` |
| 界面跟随 | `AnchorComponent(AnchorTarget.createCameraTarget())`，相机前方约 0.9 米 |
| 空间标签 | ECS Entity + AttachmentPanel + Billboard/LookAt |
| 空间持久化 | Persistent Spatial Anchor |
| 本地数据 | Room Database |
| 搜索 | 名称、位置、备注、颜色的子序列模糊匹配 |
| 交互输入 | 手柄射线、手势捏合、指尖表面接触 |

> Persistent Spatial Anchor 依赖 Full Space Stage，因此本项目不使用 Shared Space `WindowContainer` 承载持久化锚点。

## 项目结构

```text
app/src/main/java/com/spatialapps/homemarkers/
├── content/        # 空间场景、标签实体、SpatialUI 面板和交互流程
├── data/           # Room 数据仓库与 Persistent Spatial Anchor 封装
├── domain/         # 标签模型、搜索、高亮、方向提示和放置状态策略
├── platform/       # SpatialApplication 与启动 Activity
└── Main.kt         # Spatial App 入口
```

## 开发环境

- Android Studio 2025.1 或兼容版本
- Java 21（推荐使用 Android Studio 内置 JBR）
- Android SDK Platform 35
- PICO Spatial SDK 0.13.3
- PICO OS 空间设备或 PICO Emulator
- `pico-cli`（用于安装、启动和设备调试）

项目配置：

- 包名：`com.spatialapps.homemarkers`
- `minSdk`：35
- `targetSdk`：35
- 支持 ABI：`arm64-v8a`、`x86_64`

## 构建

Windows PowerShell：

```powershell
$env:JAVA_HOME = "<Android Studio 安装目录>\jbr"
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

生成的 Debug APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 安装与运行

查看已连接设备：

```powershell
pico-cli device list --format json
```

安装 APK：

```powershell
pico-cli app install app/build/outputs/apk/debug/app-debug.apk --device <设备序列号> --replace
```

启动应用：

```powershell
pico-cli app launch com.spatialapps.homemarkers --device <设备序列号>
```

PICO 同一时间只允许一个 Full Space 应用占用前台。如果启动被系统拒绝，请先停止当前正在运行的 Full Space 应用。

## 测试

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
```

单元测试覆盖：

- 创建模式的表面输入门控与 1.5 秒未命中提示策略
- 手势捏合和指尖接触的边沿触发
- 模糊搜索和 20 个以上标签查询
- 旧分类值到颜色的兼容映射
- 临期/过期状态计算
- 查询方向与距离计算
- 目标 100%、非目标 18% 的静态透明度策略

## 已验证设备

- PICO B3110
- Android 16 / API 36
- `arm64-v8a`

## 说明

本项目为私有项目，未附带开源许可证。未经授权请勿复制、分发或用于商业用途。
