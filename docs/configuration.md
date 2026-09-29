# 配置参考

[返回 README](../README.md) · [使用指南](usage.md) · [开发说明](development.md)

首次启动后，配置文件位于 `plugins/TianjiRedeem/`：

| 文件 | 用途 | 完整默认文件 |
| --- | --- | --- |
| `config.yml` | 兑换券外观、兑换音效、分类和商品 | [config.yml](../src/main/resources/config.yml) |
| `messages.yml` | 命令提示、界面标题、按钮及提示文字 | [messages.yml](../src/main/resources/messages.yml) |

已有文件不会被默认配置覆盖。修改后执行 `/tianjiredeem reload`，命令及权限见[使用指南](usage.md)。

## 最小配置

下面是一份可直接使用的完整 `config.yml`，包含一个商品。`messages.yml` 保留首次启动生成的完整文件即可。

```yaml
voucher:
  name: '&e[建材兑换券]'
  lore: []
categories:
  stone:
    name: 石材
    products:
      - minecraft:stone
```

`sounds` 可以省略，插件会使用默认音效。若希望清空全部商品，将上面的 `categories` 节点替换为 `categories: {}`，并确保没有旧的顶层 `products` 列表。

## 兑换券外观

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `voucher.name` | 字符串，必填 | 新发放兑换券的名称，可为空字符串 |
| `voucher.lore` | 字符串列表，必填 | 每个列表项是一行描述；`[]` 表示没有描述 |

名称和描述支持 `&` 颜色及格式代码，例如 `&e`、`&l`。默认不使用斜体，需要时可显式写入 `&o`。含颜色代码的文本应使用 YAML 引号包裹。

兑换券材质固定为砖纹旗帜图案（`FIELD_MASONED_BANNER_PATTERN`），新券最多堆叠 64 张。`voucher.material` 已不再读取。

修改外观只影响之后发放的券。已有券继续有效，原来的名称、描述和堆叠上限不会自动改变；不同外观的券可以共同支付，数据相同的券才能合并堆叠。识别和扣券范围见[使用指南](usage.md)。

## 兑换音效

```yaml
sounds:
  success:
    sound: minecraft:entity.experience_orb.pickup
    volume: 1.0
    pitch: 1.0
  failure:
    sound: minecraft:entity.villager.no
    volume: 1.0
    pitch: 1.0
```

`success` 用于兑换成功，`failure` 用于券不足或兑换数量无效。两者独立配置：

| 字段 | 默认值 | 限制 |
| --- | --- | --- |
| `sound` | 上例对应的音效 ID | 必须是服务器音效注册表中的有效 ID |
| `volume` | `1.0` | 有限数值，必须大于等于 `0`；`0` 可静音 |
| `pitch` | `1.0` | 有限数值，必须大于 `0` |

可省略整个 `sounds`、任一音效节点或其中的字段，缺失部分使用默认值。音量和音调填写 YAML 数字，不要加引号；提供节点时必须保持上例的映射结构。

音效只向操作玩家播放，使用主音量通道；客户端关闭相应音量时听不到。

## 分类和商品

推荐将商品直接写入所属分类：

```yaml
categories:
  decor:
    name: 装饰
    products:
      - minecraft:item_frame
      - minecraft:lantern
      - material: minecraft:flower_pot
  painting:
    name: 画作
    products:
      - material: minecraft:painting
        painting-variant: minecraft:earth
      - material: minecraft:painting
        painting-variant: minecraft:wind
  reserved:
    name: 待开放
    products: []
```

此片段替换 `config.yml` 的 `categories` 节点，保留 `voucher` 等其他配置。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `categories` | 映射 | 键是分类标识，例如 `decor`；`{}` 表示空目录 |
| `categories.<分类>.name` | 非空白字符串，必填 | 分类按钮和分类页面的名称，按纯文本显示，不解析 `&` 颜色代码 |
| `categories.<分类>.products` | 列表，必填 | 该分类的商品；`[]` 表示空分类 |
| 商品条目的 `material` | 非空白字符串 | 对象写法中的物品 ID；普通商品也可直接写为字符串 |
| 商品条目的 `painting-variant` | 非空白字符串，可选 | 画作变体 ID，只能配合 `painting` 使用 |

普通物品可写为 `minecraft:stone`、`stone` 或 `STONE`。材质必须能作为物品发放；空气、液态水以及不存在的材质会导致校验失败。方块之外的原版物品也可配置。

指定画作必须使用服务器存在的画作变体，例如 `minecraft:earth`。目录预览和发放物品都保留所选变体；只写 `minecraft:painting` 则是未指定变体的普通画。

分类及其商品按配置顺序显示。空分类不显示，全部分类为空时显示 `dialog.catalog-empty`。

商品按“材质 + 画作变体”在整个配置中去重，包括不同分类和兼容格式的顶层商品列表。`stone` 与 `minecraft:stone` 算重复；两幅不同变体的画可以共存，同一变体不能配置两次。错误位置会指向第二次出现的条目。

商品名称使用原版物品名称或画作标题，随客户端语言显示。新增商品的图标若没有对应纹理，需修改代码中的纹理映射，参见[开发说明](development.md)。

## 可配置的范围

当前开放的设置是券名称和描述、音效、分类及商品、消息文本。兑换比例固定为每券 64 个物品，单次使用 1～64 张券；商品没有独立价格、数量、自定义名称或自定义商品 ID。修改 `dialog.rate` 只改变说明文字，不改变兑换比例。兑换券材质、菜单列数、按钮尺寸及数量滑块范围也不能通过 YAML 调整。

## 消息文本

以[默认 messages.yml](../src/main/resources/messages.yml) 为基准修改。除下述两项兼容默认值外，所有已定义的消息字段都必须存在。通常字段值必须是字符串，可以写 `''`；数字、布尔值等类型不能作为消息文本。支持 `&` 颜色及格式代码。

只有 `command.reloaded`、`command.reload-failed` 在缺失时会使用插件内置文本，其他字段不会自动补齐。

| 字段 | 显示时机或用途 | 可用占位符 |
| --- | --- | --- |
| `command.usage` | 命令用法、参数不匹配 | 无 |
| `command.player-only` | 非玩家执行 `open` 时未指定目标 | 无 |
| `command.no-permission` | 无权限 | 无 |
| `command.invalid-amount` | 发券数量不是有效正整数 | 无 |
| `command.player-required` | 非玩家发券时未指定目标 | 无 |
| `command.player-not-found` | 找不到指定在线玩家 | `{player}`：输入的玩家名称 |
| `command.given` | 向发券执行者确认发放 | `{player}`：接收者名称；`{amount}`：发券张数 |
| `command.received` | 接收者收到别人发放的券 | `{player}`：接收者名称；`{amount}`：发券张数 |
| `command.failed` | 未预期的命令错误 | 无 |
| `command.reloaded` | 重载成功 | 无 |
| `command.reload-failed` | 重载失败 | `{error}`：文件和错误位置或原因 |
| `dialog.catalog-title` | 分类页标题、兼容格式中的无分类目录入口及标题 | 无 |
| `dialog.catalog-empty` | 没有可兑换商品 | 无 |
| `dialog.category-hint` | 分类页提示 | 无 |
| `dialog.catalog-hint` | 商品目录提示 | 无 |
| `dialog.close` | 关闭按钮 | 无 |
| `dialog.redeem-title` | 兑换页标题 | `{product}`：原版物品名称或画作标题 |
| `dialog.rate` | 兑换比例说明 | 无 |
| `dialog.balance` | 当前可用兑换券数量 | `{amount}`：剩余券数 |
| `dialog.amount` | 使用券数滑块标签 | 无 |
| `dialog.redeem` | 兑换按钮 | 无 |
| `dialog.back` | 返回按钮 | 无 |
| `dialog.insufficient` | 券不足 | `{product}`：所选商品；`{amount}`：尝试使用的券数；`{count}`：当前可用券数 |
| `dialog.invalid-amount` | 兑换数量不是 1～64 的整数 | 无 |
| `startup.invalid-config` | 启动配置错误日志 | `{error}`：文件和错误位置或原因 |

占位符只在对应消息中替换，未提供的占位符会原样保留。例如，`{amount}` 在 `dialog.balance` 表示券余额，在 `dialog.insufficient` 表示本次想使用的券数。

### 多行提示

`dialog.category-hint` 和 `dialog.catalog-hint` 可以是字符串，也可以是字符串列表。每个列表项是一行，空字符串可留空行，`[]` 表示空提示：

```yaml
dialog:
  category-hint:
    - '&7请选择建材分类'
  catalog-hint:
    - '&7悬停查看名称'
    - ''
    - '&7点击选择要兑换的物品'
```

将这两个字段合并到原有 `dialog` 节点，保留其他必填消息。提示位于标题与按钮之间，宽度跟随一行按钮的总宽度，长行自动换行，高度由客户端调整。

## 重载和错误处理

`/tianjiredeem reload` 同时读取两份文件，全部校验成功后才更新设置。成功后，新打开的界面使用新配置；已打开的旧界面在下次点击时关闭，需要重新执行 `/tianjiredeem open`。

重载失败时，插件继续使用上一次生效的配置和消息，不会部分应用新设置。按提示修正文件后再次重载即可；插件不会替你恢复磁盘上的错误文件。

例如 `config.yml: categories.decor.products[0].painting-variant` 指向 `decor` 分类中第一个商品的画作变体。列表下标从 `0` 开始。YAML 格式错误会报告解析信息。

启动时配置不合法会停止启用插件，并在日志中报告原因；修正后重启服务器。若 `messages.yml` 本身无法读取或校验，启动错误使用插件内置文本报告。

## 从旧配置迁移

插件仍接受旧的分类名称映射和顶层 `products`。推荐把商品移入分类，下面两种目录内容等价；`voucher` 和 `sounds` 保持原样。

旧格式：

```yaml
categories:
  decor: 装饰
  painting: 画作
products:
  - material: minecraft:lantern
    category: decor
  - material: minecraft:painting
    category: painting
    painting-variant: minecraft:earth
```

推荐格式：

```yaml
categories:
  decor:
    name: 装饰
    products:
      - minecraft:lantern
  painting:
    name: 画作
    products:
      - material: minecraft:painting
        painting-variant: minecraft:earth
```

迁移后删除旧顶层 `products`，同时删除已移入分类的商品中的 `category` 字段。若同时保留两种列表，插件会先加载分类内商品，再追加顶层商品，重复条目会校验失败。

更早的顶层纯 ID 列表（例如 `products: [stone, lantern]`）仍可使用，商品归入以 `dialog.catalog-title` 命名的默认目录入口。顶层对象条目必须填写已定义的非空 `category`；嵌套商品则由所在分类决定归属。

升级时还需核对：

- 将旧 `messages.yml` 中的 `voucher.name`、`voucher.lore` 移入 `config.yml`；删除不再读取的 `voucher.material`。
- 对照默认消息文件补齐缺失字段，尤其是 `command.failed`、`dialog.category-hint` 和 `dialog.catalog-hint`。更新用法提示、玩家提示及券描述中的命令为 `/tianjiredeem open`，并在用法中列出 `reload`。
- 自定义商品 `id`、`name` 不会生效，按上面的物品 ID 或画作变体配置商品。

保存两份文件后重载；插件尚未成功启用时则重启服务器。
