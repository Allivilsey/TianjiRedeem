# TianjiRedeem

Paper 26.2 / Java 25 建材兑换插件。每张兑换券兑换 64 个所选方块，每次使用 1～64 张券。

## 构建与安装

使用 JDK 25 和 Maven 3.9：

```shell
mvn clean verify
```

将 `target/TianjiRedeem-1.0.0.jar` 放入 Paper 26.2 的 `plugins` 目录，启动服务器。
命令使用 Lamp 4.0.0-rc.18；依赖已打包并重定位，无需单独安装。
首次启动会生成 `plugins/TianjiRedeem/config.yml` 和 `messages.yml`。
修改配置后重启服务器；配置不合法时插件停止启用，并在日志中指出文件及字段位置。

## 命令

| 命令 | 权限 | 默认 |
| --- | --- | --- |
| `/tianjiredeem` | `tianjiredeem.use` | 所有玩家 |
| `/tianjiredeem give <amount> [player_name]` | `tianjiredeem.admin.give` | OP |

唯一入口为 `/tianjiredeem`，没有别名。发券数量须为正整数，不受单次兑换 64 张的限制。
玩家省略目标时发给自己；控制台必须指定在线玩家。发券或兑换时背包放不下的物品掉落在目标玩家脚边。
Tab 补全采用 Lamp 原生规则：子命令前缀区分大小写（输入 `g` 补全 `give`），命令执行和玩家名称补全不区分大小写。

## 配置

兑换券固定使用砖纹旗帜图案，最多堆叠 64 张。`config.yml` 中维护券的名称、描述及商品列表：

```yaml
voucher:
  name: '&e[建材兑换券]'
  lore:
    - '&7输入命令/tianjiredeem打开兑换界面'
products:
  - minecraft:stone
  - minecraft:oak_planks
```

`products` 只填写原版物品 ID，`minecraft:` 前缀可省略，例如 `stone`；也接受 `STONE`。
ID 不得重复（不同写法的同一材料也算重复），必须对应可发放的方块。`products: []` 表示空目录。
目录、兑换页和结果页使用该物品的原版名称，随玩家客户端语言显示；预览和产物均由同一个 ID 决定，无需填写 `id`、`name` 或 `material` 字段。
单件不可堆叠的方块也按每券 64 个兑换。商品没有独立价格或产物数量。

`messages.yml` 包含按钮、界面及命令提示。兑换券外观统一由 `config.yml` 的 `voucher` 节点配置。
支持 `&` 颜色代码；保留消息原有的 `{amount}` 等占位符。`{product}` 显示原版物品名称。
名称和描述只影响新发放的券，修改外观不会使已有券失效。旧券仍可兑换，但不会自动修改其堆叠上限；新券最多堆叠 64 张，相同数据的券才能合并。

若已有旧版配置，请将 `messages.yml` 中的 `voucher.name` 和 `voucher.lore` 移入 `config.yml` 的 `voucher` 节点，然后重启服务器。旧的 `voucher.material` 已不再读取，可删除。
旧商品配置需要将每项的 `material` 值改写为上述 ID 列表；自定义商品 ID 和名称不再使用。
升级到 Lamp 命令版本时，在已有 `messages.yml` 的 `command` 下补充 `failed: '&c命令执行失败，请联系管理员查看服务器日志。'`。

## 使用与识别规则

玩家打开原生 Dialog 材料目录，每页 12 项；选择材料后查看实时券数和物品预览，滑块默认 1，点击“兑换”直接结算。结果页可继续兑换或返回目录。

新券写入 PDC `tianjiredeem:voucher`，类型 BYTE、值 1。识别只检查此键是否存在，不比较类型、值或外观。每个有效物品单位算一张券，不同外观可共同支付。

只使用主背包和快捷栏的券；忽略装备、副手、光标、末影箱及容器物品内部。结算重新读取存量，券不足时不修改物品；足够时按槽位顺序扣除，保留剩余券数据。产物为无自定义数据的原版物品，按正常堆叠规则放入背包，溢出掉落。

本插件只负责兑换和管理员手动发券，活动发奖及经济流通由管理组和其他插件负责。

## 验证

自动测试使用 JUnit、MockBukkit 26.2 和 Mockito，覆盖券识别、库存范围、精确扣券、产物守恒、配置校验、命令权限及 Dialog 回调。
MockBukkit 不实现原生 Dialog 构造器，相关测试在 Paper API 边界捕获界面内容及回调；游戏客户端的实际布局仍需进服验收。

进服检查：发券后打开目录，确认预览和 1～64 滑块；添加超过 12 项商品确认翻页；用不同外观的旧券共同支付；满背包兑换确认脚边掉落；结果页继续兑换或返回目录。
