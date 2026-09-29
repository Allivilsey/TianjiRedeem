# TianjiRedeem

Paper 26.2 / Java 25 建材兑换插件。每张兑换券兑换 64 个所选物品，每次使用 1～64 张券。

本项目采用 [MIT License](LICENSE)。

## 构建与安装

使用 JDK 25 和 Maven 3.9：

```shell
mvn clean verify
```

将 `target/TianjiRedeem-1.0.0.jar` 放入 Paper 26.2 的 `plugins` 目录，启动服务器。
命令使用 Lamp 4.0.0-rc.18；依赖已打包并重定位，无需单独安装。
首次启动会生成 `plugins/TianjiRedeem/config.yml` 和 `messages.yml`。
修改配置后执行 `/tianjiredeem reload` 生效。启动时配置不合法会停止启用，并在日志中指出文件及字段位置。

## 命令

| 命令 | 权限 | 默认 |
| --- | --- | --- |
| `/tianjiredeem open` | `tianjiredeem.use` | 所有玩家 |
| `/tianjiredeem open <player_name>` | `tianjiredeem.admin.open` | OP |
| `/tianjiredeem give <amount> [player_name]` | `tianjiredeem.admin.give` | OP |
| `/tianjiredeem reload` | `tianjiredeem.admin.reload` | OP |

唯一入口为 `/tianjiredeem`，没有别名；不带子命令时显示用法。`open` 打开自己的兑换界面；管理员或控制台可用 `open <player_name>` 为指定在线玩家打开界面，玩家 ID 为完整游戏名，不区分大小写。目标玩家仍需拥有 `tianjiredeem.use` 权限。
发券数量须为正整数，不受单次兑换 64 张的限制。`give` 中玩家省略目标时发给自己；控制台必须指定在线玩家。发券或兑换时背包放不下的物品掉落在目标玩家脚边。
Tab 补全采用 Lamp 原生规则：子命令前缀区分大小写（输入 `g` 补全 `give`），命令执行和玩家名称补全不区分大小写。

`reload` 可由管理员或控制台执行，同时重载 `config.yml` 和 `messages.yml`，更新分类、商品、兑换券外观、音效与提示。两份配置全部通过校验后生效；失败时提示文件和字段位置，保留当前配置，修正后可再次重载。成功重载后，已打开的旧兑换界面在下次点击时关闭，请重新执行 `open`。

## 配置

兑换券固定使用砖纹旗帜图案，最多堆叠 64 张。`config.yml` 中维护券的名称、描述、兑换音效及商品列表：

```yaml
voucher:
  name: '&e[建材兑换券]'
  lore:
    - '&7输入命令/tianjiredeem open打开兑换界面'
sounds:
  success: {sound: minecraft:entity.experience_orb.pickup, volume: 1.0, pitch: 1.0}
  failure: {sound: minecraft:entity.villager.no, volume: 1.0, pitch: 1.0}
categories:
  stone:
    name: 石材与矿物
    products:
      - minecraft:stone
  painting:
    name: 画作
    products:
      - {material: minecraft:painting, painting-variant: minecraft:earth}
```

`categories` 中每个分类包含 `name`（菜单名称）与 `products`（该分类的商品），商品无需重复填写分类。分类与商品均按配置顺序显示，空分类不显示。
普通商品直接填写可发放的原版物品 ID，`minecraft:` 前缀可省略，例如 `stone`；也接受 `STONE`。`categories: {}` 表示空目录，分类下 `products: []` 表示空分类。
指定画作使用 `painting-variant`，预览和发放均保留该变体；同一物品和画作变体不能重复配置到多个分类。
目录和兑换页使用原版物品名称或画作标题，随玩家客户端语言显示。单件不可堆叠的物品也按每券 64 个兑换，商品没有独立价格或产物数量。

`sounds.success`、`sounds.failure` 分别配置兑换成功和失败的音效：`sound` 为原版音效 ID，`volume` 为非负音量（0 表示静音），`pitch` 为大于 0 的音调。音效仅向操作玩家播放，使用主音量通道；客户端静音时听不到。旧配置缺少这些字段时使用上例默认值，修改后重载生效。

默认目录包含《建材分类.md》的 10 类、425 个条目（含 51 幅指定画作），另保留原有石砖和橡木木板，共 427 项，全部沿用每券 64 个的比例。
兼容旧版独立的分类名称映射与顶层 `products`，旧版纯 ID 列表仍集中到默认目录入口。迁移时，将商品移到对应分类的 `products` 下，删除商品中的 `category` 和旧的顶层 `products`；已有服务器配置不会被自动覆盖，修改后重载生效。

`messages.yml` 包含按钮、界面及命令提示。兑换券外观统一由 `config.yml` 的 `voucher` 节点配置。
分类菜单和物品目录的提示分别配置在 `dialog.category-hint`、`dialog.catalog-hint`，显示于标题与按钮之间。文本换行宽度按实际一行按钮总宽度（含间距）计算，高度由客户端根据内容自动调整。每个列表项显示为一行，长行自动换行，空字符串可留空行，例如：

```yaml
  category-hint:
    - '&7请选择建材分类'
  catalog-hint:
    - '&7悬停查看名称'
    - '&7点击选择建材'
```

已有 `messages.yml` 需在 `dialog` 下补充上述两个提示字段，重载生效；兼容之前的字符串写法。
支持 `&` 颜色代码；保留消息原有的 `{amount}` 等占位符。`{product}` 显示原版物品名称。
名称和描述只影响新发放的券，修改外观不会使已有券失效。旧券仍可兑换，但不会自动修改其堆叠上限；新券最多堆叠 64 张，相同数据的券才能合并。

若已有旧版配置，请将 `messages.yml` 中的 `voucher.name` 和 `voucher.lore` 移入 `config.yml` 的 `voucher` 节点，然后重启服务器。旧的 `voucher.material` 已不再读取，可删除。
更早的商品对象配置需要补充 `category` 并在 `categories` 中定义分类；自定义商品 ID 和名称不再使用。
升级到 Lamp 命令版本时，在已有 `messages.yml` 的 `command` 下补充 `failed: '&c命令执行失败，请联系管理员查看服务器日志。'`。
升级到 `open` 子命令版本时，已有 `messages.yml` 的 `command.usage`、`command.player-only` 以及兑换券描述中的旧命令需手动更新为新用法。
重载提示可通过 `command.reloaded`、`command.reload-failed`（支持 `{error}`）自定义；旧 `messages.yml` 缺少这两项时使用插件内置文本。已有 `command.usage` 可手动补充 `reload` 用法。

## 使用与识别规则

玩家先在原生 Dialog 中选择类型，再通过固定九列、20×20 的正方形图标按钮选择材料；行数按该类商品数量自动增加，底部按钮返回类型菜单。按钮悬浮提示显示名称及画作变体。选择材料后查看实时券数、兑换比例和物品预览，滑块默认 1，点击“兑换”直接结算。成功播放音效并刷新当前兑换页的余额，失败播放另一音效并在兑换页显示原因；刷新后滑块恢复为 1，可继续兑换或返回当前分类，不再显示独立结果页。
图标使用客户端原版 [Sprite](https://docs.papermc.io/adventure/minimessage/format/#sprite) 纹理，画作显示对应画面，无需资源包；原生 Sprite 字形为 8×8。目录全部使用 Dialog，不受容器行数限制。
默认清单的图标路径已逐项核对。新增材质若没有同名物品或方块纹理，需在 `RedeemProduct.icon()` 中补充其纹理映射。

新券写入 PDC `tianjiredeem:voucher`，类型 BYTE、值 1。识别只检查此键是否存在，不比较类型、值或外观。每个有效物品单位算一张券，不同外观可共同支付。

只使用主背包和快捷栏的券；忽略装备、副手、光标、末影箱及容器物品内部。结算重新读取存量，券不足时不修改物品；足够时按槽位顺序扣除，保留剩余券数据。产物为原版物品，指定画作附带原版画作变体组件，按正常堆叠规则放入背包，溢出掉落。

本插件只负责兑换和管理员手动发券，活动发奖及经济流通由管理组和其他插件负责。

## 验证

自动测试使用 JUnit、MockBukkit 26.2 和 Mockito，覆盖券识别、库存范围、精确扣券、产物守恒、分类配置、画作变体、命令权限及菜单交互。
MockBukkit 不实现原生 Dialog 构造器，且复制物品时会丢失数据组件；相关测试分别捕获 Dialog 与画作发放边界，游戏客户端实际布局及画作放置仍需进服验收。

进服检查：发券后选择类型，确认九列方形图标按钮、悬浮提示、返回按钮和 1～64 滑块；兑换不同画作并放置，确认变体一致；用不同外观的旧券共同支付；满背包兑换确认脚边掉落；确认成功和失败音效、余额刷新、失败原因及连续兑换。
