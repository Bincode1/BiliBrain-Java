# BiliBrain Java

<div align="center">

![Java](https://img.shields.io/badge/Java-21-1677ff?style=for-the-badge)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.13-6db33f?style=for-the-badge)
![Spring AI Alibaba](https://img.shields.io/badge/Spring%20AI%20Alibaba-1.1.2-orange?style=for-the-badge)
![MySQL](https://img.shields.io/badge/MySQL-8+-4479A1?style=for-the-badge)
![Milvus](https://img.shields.io/badge/Milvus-Vector%20Search-00bcd4?style=for-the-badge)

一个面向 Bilibili 收藏夹内容整理的后端服务。  
它把「扫码登录 -> 收藏夹同步 -> 视频处理 -> 转写切块 -> 向量检索 -> 摘要生成 -> Agent 对话 / 技能 / 工具执行」串成了一条完整链路。

[项目后端仓库](https://github.com/Bincode1/BiliBrain-Java) | [项目配套前端仓库](https://github.com/Bincode1/BiliBrain-Java-frontend)

</div>

## 项目简介

BiliBrain Java 是一个基于 `Spring Boot 3 + Spring AI Alibaba + MyBatis-Plus + MySQL + Milvus` 的后端项目，目标不是做一个简单聊天壳，而是把 B 站收藏夹里的视频内容真正沉淀为可检索、可总结、可继续加工的知识资产。

当前项目已经具备这些核心能力：

- Bilibili 扫码登录与会话查询
- 收藏夹列表读取、单收藏夹视频列表读取、B 站站内补内容搜索
- 视频处理主线：音频下载、转写、分块、向量入库、摘要生成
- 基于 Milvus 的视频 chunk 检索与摘要检索
- 聊天会话 CRUD、历史消息读取、长期记忆压缩
- Skill Registry 本地技能管理
- Tool Workspace、工具目录、工具调用审计
- 独立 Skill Agent SSE 流式执行与审批中断恢复
- 写文件类工具审批、MySQL checkpoint 持久化恢复

## 功能总览

### 1. 认证与收藏夹

- 支持 Bilibili 二维码登录
- 支持读取当前登录会话
- 支持同步收藏夹目录和收藏夹内视频元数据
- 支持按收藏夹上下文去 B 站继续搜索补充视频

### 2. 视频处理链路

- 下载 B 站视频音频
- 调用 DashScope ASR 做转写
- 对 transcript 分块
- 写入 Milvus 向量库
- 生成视频摘要
- 支持单视频重置、重新切块入库、全量重置

### 3. 检索与问答

- 支持基于 chunk 的向量检索
- 支持摘要检索与路由
- 支持聊天历史持久化
- 支持“压缩记忆 + 最近消息”上下文组装

### 4. Agent / Skills / Tools

- 基于官方 `SkillRegistry` 做本地技能目录管理
- 提供技能列表、详情、创建、激活、停用接口
- 提供工具目录、工作区管理、工具调用接口
- 提供独立 `skill-agent` 流式接口
- 支持审批中断恢复
- 支持 `write_file` 这类需要人工确认的工具调用

## 技术栈

| 类别 | 方案 |
|---|---|
| 语言 / 运行时 | Java 21 |
| Web 框架 | Spring Boot 3.5.13 |
| 流式能力 | Spring WebFlux SSE |
| ORM | MyBatis-Plus 3.5.12 |
| 数据库 | MySQL |
| 向量库 | Milvus |
| AI / Agent | Spring AI 1.1.2、Spring AI Alibaba 1.1.2.x |
| 模型能力 | DashScope Chat / Embedding / ASR |
| 测试 | Spring Boot Test、Testcontainers |

## 项目结构

```text
src/main/java/com/bin/bilibrain
├─ controller   对外 REST / SSE 接口
├─ service      业务编排
├─ graph        ingestion / summary / agent 图节点
├─ bilibili     B 站登录、收藏夹、搜索、音频下载接入
├─ tools        工具定义与调用
├─ stream       SSE 事件构建与流式输出
├─ mapper       MyBatis-Plus 数据访问
├─ model        DTO / VO / Entity
├─ manager      凭证、状态等管理器
└─ config       配置与 Bean 装配
```

## 接口概览

项目里普通接口默认返回统一 `BaseResponse<T>`，流式接口使用 `text/event-stream`。

### 认证

- `GET /api/auth/session`
- `POST /api/auth/qr/start`
- `GET /api/auth/qr/poll`

### 收藏夹与视频

- `GET /api/folders`
- `GET /api/folders/{folderId}/videos`
- `GET /api/folders/{folderId}/bili-search`
- `POST /api/folders/sync`
- `POST /api/sync`

### 视频处理与内容

- `GET /api/videos/{bvid}/process/status`
- `POST /api/videos/{bvid}/process`
- `POST /api/videos/{bvid}/reindex`
- `POST /api/videos/{bvid}/reset`
- `POST /api/videos/reset-all`
- `GET /api/videos/{bvid}/transcript`
- `GET /api/videos/{bvid}/summary`
- `POST /api/videos/{bvid}/summary`
- `POST /api/videos/{bvid}/tags`

### 聊天

- `GET /api/chat/conversations`
- `POST /api/chat/conversations`
- `PATCH /api/chat/conversations/{conversationId}`
- `DELETE /api/chat/conversations/{conversationId}`
- `GET /api/chat/history`
- `GET /api/chat/messages/{messageId}`

### Skills / Tools / Agent

- `GET /api/skills`
- `GET /api/skills/{name}`
- `POST /api/skills/create`
- `POST /api/skills/activate`
- `POST /api/skills/deactivate`
- `GET /api/tools`
- `GET /api/tools/workspaces`
- `POST /api/tools/workspaces`
- `POST /api/tools/call`
- `POST /api/skill-agent/stream`
- `POST /api/skill-agent/resume/stream`

### 系统接口

- `GET /api/health`
- `GET /api/system/readiness`
- `GET /api/settings`
- `POST /api/settings`

## 运行前准备

### 基础依赖

- JDK 21
- Maven Wrapper
- MySQL 8+
- ffmpeg / ffprobe
- DashScope API Key

### 可选依赖

- Milvus
- MinIO
- Etcd
- Attu

如果你要启用向量检索，直接用项目自带的 `docker-compose.yml` 拉起 Milvus 相关依赖即可。

```bash
docker compose up -d
```

## 配置说明

项目主配置在 [`application.yaml`](/D:/learnProjects/bilibrain/src/main/resources/application.yaml)。

常用环境变量：

| 变量 | 说明 |
|---|---|
| `SERVER_PORT` | 服务端口，默认 `8000` |
| `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DATABASE` | MySQL 连接配置 |
| `MYSQL_USERNAME` / `MYSQL_PASSWORD` | MySQL 账号密码 |
| `DASHSCOPE_API_KEY` | DashScope Key |
| `DASHSCOPE_CHAT_ENABLED` | 是否启用 Chat 模型 |
| `VECTOR_STORE_ENABLED` | 是否启用向量检索 |
| `MILVUS_HOST` / `MILVUS_PORT` | Milvus 连接配置 |
| `DATA_DIR` | 数据目录 |
| `UPLOAD_DIR` | 上传目录 |
| `TOOLS_WORKSPACE_ROOT` | 工具工作区根目录 |
| `SKILLS_ROOT` | 技能目录根路径 |
| `VAULT_ROOT` | Obsidian / 笔记发布根目录 |
| `FFMPEG_COMMAND` / `FFPROBE_COMMAND` | ffmpeg 命令路径 |

## 本地启动

### 1. 启动基础依赖

```bash
docker compose up -d
```

### 2. 启动后端

```bash
./mvnw spring-boot:run
```

Windows:

```bash
mvnw.cmd spring-boot:run
```

默认启动端口：

```text
http://localhost:8000
```

### 3. 检查系统状态

```bash
curl http://localhost:8000/api/health
curl http://localhost:8000/api/system/readiness
```

## 配套前端

这个仓库是后端仓库，对应前端仓库是：

- [BiliBrain-Java-frontend](https://github.com/Bincode1/BiliBrain-Java-frontend)

前后端本地联调时，前端默认运行在：

- `http://localhost:5173`
- `http://127.0.0.1:5173`

这些地址已经在后端 CORS 白名单里。

## 当前实现状态

已经完成：

- B 站登录、会话管理、收藏夹同步
- 视频处理主线与状态轮询
- transcript / summary 落库与读取
- Milvus 检索与 readiness 检查
- 聊天会话和消息持久化
- 技能管理、工具目录、工作区能力
- Skill Agent 流式执行、审批流、checkpoint 恢复

还没有完全做成通用图级断点续跑的部分：

- ingestion / summary 目前仍主要依赖状态落库、缓存复用和重新入队

## 测试

运行测试：

```bash
./mvnw test
```

Windows:

```bash
mvnw.cmd test
```

## 仓库地址

- 后端仓库: [https://github.com/Bincode1/BiliBrain-Java](https://github.com/Bincode1/BiliBrain-Java)
- 前端仓库: [https://github.com/Bincode1/BiliBrain-Java-frontend](https://github.com/Bincode1/BiliBrain-Java-frontend)

## License

当前仓库未声明正式开源协议；如果你准备公开发布，建议补一个明确的 LICENSE 文件。
