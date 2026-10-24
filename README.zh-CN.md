# 5G Switch

[English](README.md) · 简体中文

通过 Shizuku / Sui 切换默认数据卡的 5G，支持控制中心磁贴、名称修改、深浅主题和隐藏启动器图标。

[下载 APK](https://github.com/XiaoLeXLDW/5g-switch-os4-shizuku/releases/latest) · [从源码构建](docs/BUILDING.md) · [MIT 许可](LICENSE)

## 使用

1. 在 Android 11 或更新版本上安装 APK，启动 Shizuku / Sui，并允许应用授权。
2. 打开应用，或将 **5G·** 磁贴添加到控制中心，即可切换 5G。应用只修改默认数据卡的 5G 开关，状态以系统读回为准。
3. 开启“隐藏启动器图标”前，先添加磁贴。隐藏后按住磁贴即可进入应用，关闭“隐藏启动器图标”可恢复图标。

## 语言

当前源码支持**英文、简体中文和繁体中文**。界面跟随系统语言，其他语言默认显示英文；Android 13 及更新版本也可通过系统设置单独选择应用语言。切换语言会保留自定义磁贴名称。

## 兼容性

**v1.1.1** 已在小米 17 Ultra 徕卡版（HyperOS 4，OS 4.0.4.0 / Android 17）上安装使用并确认可用，使用 Stellar 激活。v1.2.0 的实体手机验收待进行。

最低 SDK 版本为 Android 11；其他设备的实际兼容性取决于 ROM 和电话服务接口。开启 5G 表示允许使用该网络模式，实际连接仍取决于 SIM 卡、运营商和覆盖情况。

## 界面

<img src="docs/screenshots/native-light-enabled.png" width="260" alt="中文浅色界面，5G 已开启"> <img src="docs/screenshots/native-dark-disabled.png" width="260" alt="中文深色界面，5G 已关闭">

以上为中文界面的本机测试渲染图。界面和控制中心的 5G 图标使用系统无衬线字体，运行时跟随系统字形绘制，不内置字体文件。

见[第三方许可](THIRD_PARTY_NOTICES.md)。
