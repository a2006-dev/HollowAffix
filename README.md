# 驱动块识别 v1.0

异环·空幕 驱动块辅助工具。实时识别副属性，按规则模板自动评级。

## 特点

- **规则模板化** — 判定逻辑写在 JSON 里，不改代码就能换规则
- **深色卡片 UI** — 全新设计
- **悬浮窗显示规则名** — 一眼知道当前用的哪套规则
- **四档判定** — 满分 / 毕业 / 过渡 / 垃圾

## 技术

| 能力 | 方案 |
|---|---|
| 截屏 | AccessibilityService.takeScreenshot() |
| OCR | PP-OCRv4 + onnxruntime-android |
| 检测 | DB 算法：置信度过滤 + unclip 外扩 + 去重 |
| 字典 | 6625 字符（对齐 RapidOCR） |
| 规则 | 内置 JSON，可替换 |

不需要 Shizuku / Root。

## 权限

1. **无障碍服务** — 截屏
2. **悬浮窗** — 显示面板
3. **所有文件访问** — 保存日志

## 使用

1. 打开 App，三个权限确认 OK
2. 点「打开悬浮窗」
3. **按 Home 退到桌面**
4. 启动游戏，选中驱动块
5. 点悬浮窗「开始」

注意：必须在游戏前台时点「开始」。

## 规则格式

位置：app/src/main/assets/rules/general.json

包含字段：name / author / version / weights / doubleCritBonus /
groups / rules / fallback / colors

### 字段说明

| 字段 | 说明 |
|---|---|
| weights | 词条权重，用于算总分 |
| doubleCritBonus | 双爆时额外加分 |
| groups | 组定义，可在规则里按组引用 |
| rules | 判定规则，按顺序匹配，命中即停 |
| fallback | 所有规则都不匹配时的等级 |
| colors | 等级对应颜色 |

### 规则匹配写法

| 写法 | 含义 |
|---|---|
| require: ["双爆"] | 必须同时有暴击率和暴击伤害（特殊逻辑） |
| require: ["单爆"] | 只能有其中一个（特殊逻辑） |
| require: ["大攻击"] | 组内任一成员命中即可 |
| require: ["攻击力"] | 必须有这个具体词条 |
| requireAny: [...] | 至少满足其中一项 |
| minCount: 4 | 词条总数不少于 4 |

### 自定义规则

1. 复制 general.json，改名
2. 修改 name 和 rules
3. 放到 assets/rules/ 下
4. 改 RuleSet.loadBuiltin() 里的文件名

## 悬浮窗布局

```
┌─────────────────────┐
│ 通用规则         ─ ×│  规则名
│ 就绪                │
│                     │
│ 毕业           61分 │  等级+分数
│ 双爆+攻%            │  判定依据
│ · 攻击力 3.75%      │
│ · 暴击率 3%         │
│ · 暴击伤害 6%       │
│ · 环合强度 18       │
│                     │
│ [开始][暂停][框选]  │
└─────────────────────┘
```

- 标题栏拖动
- `─` 折叠
- 等级颜色随规则

## 输出

目录：/sdcard/HollowAffix/

| 文件 | 内容 |
|---|---|
| trace.txt | 执行过程 |
| ocr_cards.txt | 识别结果 |
| region.txt | 区域变更 |
| debug/ | 调试截图（仅 debug 版） |

## 与旧版区别

| | HollowProbe | HollowAffix |
|---|---|---|
| 包名 | com.hollow.probe | com.hollow.affix |
| 规则 | 硬编码 | JSON 模板 |
| UI | 深色简洁 | 深色卡片 + 强调色 |
| 悬浮窗 | 无规则名 | 顶部显示规则名 |
| 判定 | 三处硬编码 | 数据驱动 |

## 后续规划

- 规则在线库（Gitee/HF 拉取）
- 规则导入/导出
- 自定义规则编辑器