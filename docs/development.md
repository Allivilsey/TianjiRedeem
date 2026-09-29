# 开发与验证

[返回 README](../README.md) · [使用指南](usage.md) · [配置参考](configuration.md)

## 本地构建

使用 JDK 25 和 Maven 3.9，在仓库根目录执行：

```shell
mvn clean verify
```

该命令清理旧产物、运行测试并打包；仅运行自动测试可用 `mvn test`。当前产物为 `target/TianjiRedeem-1.0.0.jar`。Lamp 依赖已打包并重定位到插件内部包，Paper API 由服务器提供。部署环境与安装步骤见 [README](../README.md)。

构建目标与依赖版本以 [pom.xml](../pom.xml) 为准：编译目标为 Java 25，Paper API 为 `26.2.build.129-stable`。修改版本时同时检查 [plugin.yml](../src/main/resources/plugin.yml) 中的插件版本和 `api-version`。

## 源码职责

主源码位于 `src/main/java/org/allivlisey/redeem/`：

| 文件 | 职责 |
| --- | --- |
| `TianjiRedeemPlugin`、`RedeemConfig`、`Messages` | 启停、配置读取与校验、文本解析、重载 |
| `RedeemCommand` | 命令注册、权限、在线玩家解析、发券和重载入口 |
| `RedeemDialogs` | 分类与商品界面、数量输入、按钮回调和兑换反馈 |
| `RedeemProduct` | 商品名称、图标纹理、物品及画作变体的构造 |
| `Vouchers`、`RedeemService`、`ItemDelivery` | 券识别与扣除、兑换比例、按堆叠上限发放与溢出掉落 |

重载先读完并校验两份配置，再替换当前设置；失败时保留原设置。成功重载会使旧界面的回调失效，玩家下次点击时关闭界面。按钮还检查插件启用状态、操作者身份及 `tianjiredeem.use` 权限；每个回调只能使用一次，有效期为 10 分钟。

## 扩展商品与图标

仅增删原版商品时，修改服务器的 `plugins/TianjiRedeem/config.yml` 并重载即可，格式见 [配置参考](configuration.md)。若要更新插件自带目录，修改 [src/main/resources/config.yml](../src/main/resources/config.yml)；已有服务器的配置不会因此被覆盖。

商品解析和校验位于 [RedeemConfig](../src/main/java/org/allivlisey/redeem/RedeemConfig.java)：材质必须能作为物品发放，指定画作必须存在于 `Registry.ART`，相同材质与画作变体组合不能重复。增加新的商品配置字段时，在这里解析，并在 [RedeemProduct](../src/main/java/org/allivlisey/redeem/RedeemProduct.java) 中实现对应物品构造。

`RedeemProduct.icon()` 负责客户端 Sprite 图标的图集、纹理别名和植物着色。新增材质若没有同名纹理，应在此补充映射；画作使用 `minecraft:paintings` 图集和画作的 `assetId()`。`createItem()` 同时用于兑换页预览与发放模板，画作通过 `PAINTING_VARIANT` 数据组件保留变体。新增映射后补充 `RedeemProductTest`，并进服检查图标与实际画作。

每券兑换 64 个商品、每次使用 1～64 张券是代码中的固定规则。若修改兑换规则，需要同步检查 `RedeemService`、`Vouchers.consume()`、`RedeemDialogs` 及相关提示文本和测试。

## 与其他插件接入

活动奖励或菜单插件可通过控制台命令接入，完整示例和权限见[使用指南](usage.md)。以下说明源码当前的券标记与结算行为，供需要生成兼容物品的开发者参考。

需要生成兼容兑换券时，遵循当前识别规则：

- 新券使用 `FIELD_MASONED_BANNER_PATTERN`，最大堆叠数为 64，名称和描述取自配置；PDC 键为 `tianjiredeem:voucher`，写入 `PersistentDataType.BYTE`、值 `1`。
- **识别只检查该 PDC 键存在且物品数量大于 0**，不比较材质、名称、描述、PDC 类型或值。仅复制外观不能生成有效券；写入此键的其他物品也会被当作券。
- 每个物品单位算一张券。修改配置只影响新发券的外观；存量券仍可支付，原有数据和堆叠上限不会自动改写。

兑换余额来自玩家当前主背包与快捷栏，没有独立余额账本。装备、副手、光标、末影箱和容器物品内部的券不参与结算。点击兑换时重新读取存量，券不足则不扣除或发放；足够时按槽位顺序扣券，部分扣除保留剩余物品的元数据。产物按正常堆叠上限入包，放不下的部分掉落在玩家脚边，发券也使用相同的发放逻辑。

插件负责发券与兑换；活动发奖规则、券的来源管理和经济流通由管理组及接入插件负责。

## 自动测试与进服验收

测试位于 [src/test/java/org/allivlisey/redeem](../src/test/java/org/allivlisey/redeem)，使用 JUnit、MockBukkit 26.2 和 Mockito，覆盖：

- 券识别、库存范围、扣券顺序、余额不足不修改库存、商品数量守恒与溢出掉落。
- 配置字段校验、重复商品、画作变体、文本占位符和纹理映射。
- 命令权限、在线目标、参数与补全，菜单导航、数量校验、反馈及重载后的旧回调失效。

MockBukkit 不提供动态 `DialogInstancesProvider`，界面测试通过模拟 Paper 构造边界检查按钮和回调，不能证明客户端实际布局。其物品克隆会丢失数据组件，画作兑换测试因此检查交给 `ItemDelivery` 的模板；画作最终入包、掉落与放置仍需实服验证。部分库存测试还占用装备槽，以避开 MockBukkit 的 `addItem` 会遍历装备槽的差异。

发布前，在匹配版本的 Paper 服务器和客户端完成以下检查：

1. 发券并打开目录，检查分类、图标、悬浮名称、返回按钮和 1～64 数量滑块。兑换 1 张与多张券，核对扣券数及每券 64 个产物，确认余额刷新、成功音效和继续兑换。
2. 使用不足的券兑换，确认库存不变、失败原因和失败音效；把券移到副手后再次检查。不同外观的旧券应可共同支付。
3. 填满主背包后发券和兑换，确认溢出物品掉在目标玩家脚边；兑换不可堆叠商品时核对总数量。
4. 选择不同画作，检查目录图案、预览及发放结果；分别放置背包取得和溢出掉落取得的画作，确认变体一致。
5. 保持兑换界面打开，修改配置并成功重载，确认旧按钮关闭界面，重新打开后使用新设置。再用错误配置尝试重载，确认报告错误且原设置仍可使用，修正后能再次重载。
