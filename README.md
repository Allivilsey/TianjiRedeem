# TianjiRedeem

面向 Paper 26.2 的建材兑换插件。玩家使用兑换券，在原生 Dialog 界面中选择建材或画作并完成兑换。

**1 张券兑换 64 个所选物品，每次可使用 1～64 张券。**

- 默认按类别提供建材和指定画作，可通过配置增删。
- 商品目录使用九列物品图标，支持悬浮名称、物品预览和返回导航，无需额外资源包。
- 商品名称与画作标题随玩家客户端语言显示，券外观、界面文字和兑换音效可配置。
- 管理员可发券、为在线玩家打开界面，并通过命令重载配置。

## 快速开始

运行环境为 **Paper 26.2、Java 25**；从源码构建还需要 **JDK 25 和 Maven 3.9**。插件已打包所需的 Lamp 命令库，无需安装前置插件。

1. 在项目根目录构建：

   ```shell
   mvn clean verify
   ```

2. 将 `target/TianjiRedeem-1.0.0.jar` 放入服务器的 `plugins/` 目录，启动服务器。
3. 首次启动会生成 `plugins/TianjiRedeem/config.yml` 和 `messages.yml`。
4. 由 OP 在游戏内发给自己 4 张券，再打开兑换界面：

   ```text
   /tianjiredeem give 4
   /tianjiredeem open
   ```

普通玩家默认可以使用 `/tianjiredeem open`。选择分类和商品后，通过滑块选择使用券数，再点击“兑换”。背包放不下的券或兑换产物会掉落在玩家脚边。

## 文档

| 文档 | 内容 |
| --- | --- |
| [使用指南](docs/usage.md) | 玩家兑换流程、命令与权限、发券接入、重载和常见问题 |
| [配置参考](docs/configuration.md) | 商品分类、画作、券外观、音效、消息占位符与旧配置迁移 |
| [开发说明](docs/development.md) | 构建与测试、源码职责、兑换券识别、图标扩展和进服验收 |

默认文件：[config.yml](src/main/resources/config.yml) · [messages.yml](src/main/resources/messages.yml)

修改这两份服务器配置后，执行 `/tianjiredeem reload`。两份文件均通过校验后生效；校验失败会保留当前配置，并提示错误位置。已有服务器配置不会被插件自动覆盖，升级时请参考[配置迁移说明](docs/configuration.md)。

插件负责兑换和发券，券的获取途径由服务器活动或其他插件安排。兑换比例、单次券数范围和券材质目前固定，不支持按商品设置价格。

## 许可证

本项目采用 [MIT License](LICENSE)。打包的 Lamp 命令库许可证见 [LICENSE-Lamp.txt](src/main/resources/META-INF/LICENSE-Lamp.txt)。
