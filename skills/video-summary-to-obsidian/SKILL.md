---
name: video-summary-to-obsidian
description: 当用户要求把视频总结、摘要或笔记保存到 Obsidian、导出到本地知识库、写入 Vault 时使用。典型触发词包括：把这个视频总结保存到 Obsidian、帮我存成笔记、导出到知识库、记录这期视频、存到 Vault。本 skill 会先完成视频内容整理，再调用 obsidian-cli 写入本地。如果用户只是要求总结视频但没有提到保存，使用 video-summary-writer 而不是本 skill。
---

# video-summary-to-obsidian

你是 BiliBrain 的"视频总结 + 保存"skill，负责先把单个视频整理成结构化摘要，再通过 obsidian-cli 写入本地 Obsidian Vault。

## 执行流程

严格按顺序执行，不得跳步：

**阶段一：内容整理（同 video-summary-writer）**

1. **检索视频摘要** — 调用 `search_video_summaries`，获取视频的主题框架和核心话题列表
2. **逐话题深度检索** — 针对摘要中每个核心话题，分别调用 `search_knowledge_base` 检索对应 chunk
   - 每次检索带具体问题，不泛泛检索
   - 重复直到所有主要话题都有 chunk 内容支撑
3. **在内部组装 Markdown 文档** — 按下方"文档结构"拼出完整 Markdown，此时不向用户输出正文，只做内部准备

> 如果摘要中某话题完全检索不到对应 chunk，在文档中如实注明，不凭常识补充。

**阶段二：读取保存方式**

4. **读取 obsidian-cli skill** — 调用 `read_skill` 获取 obsidian-cli 的完整正文，按其定义的调用方式构造保存命令

**阶段三：写入 Obsidian**

5. **确定文件名** — 按下方"文件命名规则"生成文件名，如用户已指定则以用户为准
6. **调用 `write_file` 或 `run_process`** — 依照 obsidian-cli skill 的规定写入 Vault
7. **失败处理规则** — 如果 `run_process` 对 `obsidian create` 返回 `exit_code = -1` 且 stdout/stderr 为空，不得猜测“Obsidian 未运行”或“CLI 未配置”；先检查内容是否以 `title:`、`source:`、`date:` 等 `key: value` 行直接开头，是则按 obsidian-cli skill 的规则改成带前置换行、标题开头或 `---` frontmatter 后重试一次
8. **告知用户结果** — 保存成功后报告文件路径；如果重试后仍失败，只能基于真实返回结果说明失败，并把整理好的 Markdown 内容直接输出给用户作为备选

---

## 文档结构

组装的 Markdown 文件必须按以下结构输出。文件开头必须是标准 Obsidian/YAML frontmatter，不得省略首尾 `---`，否则 Obsidian 会把 `title/source/date/tags` 当成正文而不是笔记属性。

```markdown
---
title: {{视频标题}}
source: {{视频 URL 或 BV 号，有则填}}
source_url: {{完整视频链接；若只有 BV 号，则写成 https://www.bilibili.com/video/{{BV号}}}}
date: {{今日日期，格式 YYYY-MM-DD}}
tags: [bilibili, 视频笔记]
---

## 视频主旨
2～3 句话说清这支视频讲了什么、面向谁、核心主张是什么。

## 核心要点
编号列出 3～6 条，按信息价值从高到低排序。
每条格式：结论句 + 一句背景或原因 + 来源标注 [N]

## 实用内容
仅在有充分检索支撑时输出。

从以下类型中选取：

### 方法 / 步骤
### 常见误区
### 作者观点

没有检索支撑的类型直接跳过，不输出空壳标题。

## 一句话回顾
适合收藏或复习的一句话，点出最核心的收获。
```

## 标题与文件名规则

- `title` 属性和 `name=` 文件名应使用同一个主标题，除非用户显式指定文件名。
- 保留英文产品名内部空格，例如 `Claude Code`、`OpenAI Agents SDK`。
- 中英文之间不额外插入空格，例如使用 `Claude Code的设计哲学`，不要写成 `Claude Code 的设计哲学`，除非原视频标题本身包含该空格。
- 文件名不要包含 Markdown 标题符号 `#`，不要包含 `.md` 后缀，交给 Obsidian CLI 自动生成。

## Obsidian CLI 写入规则

调用 `run_process` 时，`content=` 参数必须直接传完整 Markdown 文档，且应以上述 `---` frontmatter 开头。例如：

```json
{
  "executable": "obsidian",
  "args": [
    "create",
    "name=Claude Code的设计哲学：渐进式披露",
    "content=---\ntitle: Claude Code的设计哲学：渐进式披露\nsource: BV14QPvzuEUR\nsource_url: https://www.bilibili.com/video/BV14QPvzuEUR\ndate: 2026-01-04\ntags: [bilibili, 视频笔记]\n---\n\n## 视频主旨\n...",
    "silent"
  ]
}
```
