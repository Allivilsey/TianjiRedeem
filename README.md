# TianjiRedeem

Paper 26.2 / Java 25 建材兑换插件。每张兑换券兑换 64 个所选方块，每次使用 1～64 张券。

## 构建与安装

使用 JDK 25 和 Maven 3.9：

```shell
mvn clean verify
```

将 `target/TianjiRedeem-1.0.0.jar` 放入 Paper 26.2 的 `plugins` 目录，启动服务器。
首次启动会生成 `plugins/TianjiRedeem/config.yml` 和 `messages.yml`。
修改配置后重启服务器；配置不合法时插件停止启用，并在日志中指出文件及字段位置。

## 命令

| 命令 | 权限 | 默认 |
| --- | --- | --- |
| `/tianjiredeem` | `tianjiredeem.use` | 所有玩家 |
| `/tianjiredeem give <amount> [player_name]` | `tianjiredeem.admin.give` | OP |

唯一入口为 `/tianjiredeem`，没有别名。发券数量须为正整数，不受单次兑换 64 张的限制。
玩家省略目标时发给自己；控制台必须指定在线玩家。发券或兑换时背包放不下的物品掉落在目标玩家脚边。

## 配置

`config.yml` 中维护券材质及商品列表：

```yaml
voucher:
  material: FIELD_MASONED_BANNER_PATTERN
products:
  - id: stone
    name: 石头
    material: STONE
  - id: oak_planks
    name: 橡木木板
    material: OAK_PLANKS
```

商品 ID 不得重复，材质必须同时是方块和可发放物品。`products: []` 表示空目录。
单件不可堆叠的方块也按每券 64 个兑换。商品没有独立价格或产物数量。

`messages.yml` 包含按钮、界面、命令提示，以及 `voucher.name`、`voucher.lore`。
支持 `&` 颜色代码；保留消息原有的 `{amount}` 等占位符。商品展示名称来自商品配置。
券的材质、名称、描述只影响新发放的券，修改外观不会使已有券失效。

## 使用与识别规则

玩家打开原生 Dialog 材料目录，每页 12 项；选择材料后查看实时券数和物品预览，滑块默认 1，点击“兑换”直接结算。结果页可继续兑换或返回目录。

新券写入 PDC `tianjiredeem:voucher`，类型 BYTE、值 1。识别只检查此键是否存在，不比较类型、值或外观。每个有效物品单位算一张券，不同外观可共同支付。

只使用主背包和快捷栏的券；忽略装备、副手、光标、末影箱及容器物品内部。结算重新读取存量，券不足时不修改物品；足够时按槽位顺序扣除，保留剩余券数据。产物为无自定义数据的原版物品，按正常堆叠规则放入背包，溢出掉落。

本插件只负责兑换和管理员手动发券，活动发奖及经济流通由管理组和其他插件负责。

## 验证

自动测试使用 JUnit、MockBukkit 26.2 和 Mockito，覆盖券识别、库存范围、精确扣券、产物守恒、配置校验、命令权限及 Dialog 回调。
MockBukkit 不实现原生 Dialog 构造器，相关测试在 Paper API 边界捕获界面内容及回调；游戏客户端的实际布局仍需进服验收。

进服检查：发券后打开目录，确认预览和 1～64 滑块；添加超过 12 项商品确认翻页；用不同外观的旧券共同支付；满背包兑换确认脚边掉落；结果页继续兑换或返回目录。
