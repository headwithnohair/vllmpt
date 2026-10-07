# vllmpt · 多模态 AI Agent 后端平台

> 基于 Spring Boot 4 + LangChain4j 构建的多模态 AI 对话与知识库后端，统一提供「多模态对话、RAG 检索增强、ReAct 工具调用、会话记忆、SSE 流式输出」等 AI 基础能力。

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-brightgreen)
![LangChain4j](https://img.shields.io/badge/LangChain4j-1.16.2-blue)
![MyBatis-Plus](https://img.shields.io/badge/MyBatis--Plus-3.5.15-green)
![License](https://img.shields.io/badge/License-待补充-lightgrey)
![Status](https://img.shields.io/badge/Status-开发中-yellow)

---

## 目录

- [项目简介](#项目简介)
- [功能特性](#功能特性)
- [技术栈](#技术栈)
- [系统架构](#系统架构)
- [目录结构](#目录结构)
- [快速开始](#快速开始)
- [配置项说明](#配置项说明)
- [API 接口说明](#api-接口说明)
- [核心设计说明](#核心设计说明)
- [项目状态](#项目状态)
- [常见问题](#常见问题)
- [贡献指南](#贡献指南)
- [许可证](#许可证)

---

## 项目简介

`vllmpt` 是一个面向企业知识密集型场景（智能问答、知识库助手、智能审批等）的 **AI Agent 后端平台**，为上层业务提供统一的 AI 能力底座。核心能力包括：

- **多模态对话**：支持「文本 + 图片 + 文档 + 视频」混合输入；
- **RAG 检索增强**：文本知识库上传、智能分块、向量化存储、语义召回与重排序；
- **ReAct 工具调用**：大模型自主决策调用业务工具（Function Calling）；
- **多轮对话记忆**：基于 Redis 的会话级短期记忆（滑动窗口）持久化；
- **流式输出（SSE）**：打字机式实时响应；
- **多模型动态切换**：运行时按请求指定模型、温度与最大 Token（工厂 + 实例缓存）；
- **并发治理**：单用户并发推理限制（Redis ZSet + Lua）与会话级互斥锁（Redisson RLock）。

> 说明：项目处于持续开发阶段，部分能力仍在完善中，详见 [项目状态](#项目状态)。

---

## 功能特性

| 特性 | 说明 | 状态 |
|------|------|------|
| 多模态会话 | 文本 + 图片 + 文档混合输入，模型侧多模态内容与记忆侧纯文本双通道建模 | 已实现（视频为占位） |
| 多模态记忆降 Token | 图片在记忆中以 `[图片: 名称]` 文本标记替代 URL，降低记忆 Token 占用 | 已实现 |
| RAG 知识库 | 大文件上传 → 校验 → 桶迁移 → 流式分块（chunk+overlap+元数据）→ bge-m3 向量化 → Chroma 存储 | 已实现（文本类） |
| 检索重排序 | 召回后经 bge-reranker-v2-m3 重排，Top-K 注入 System Prompt | 已实现 |
| ReAct Agent | 模型自主决策多步工具调用，含步数熔断与安全执行 | 已实现 |
| 工具插件化 | `@AiTool` + `@Tool` 注解自动收集，分组建模 | 已实现 |
| 会话记忆 | `MessageWindowChatMemory`（窗口 10 条）+ Redisson RList 持久化 | 已实现 |
| SSE 流式输出 | `text/event-stream` 逐 token 推送，`[DONE]` 结束标记 | 已实现 |
| 模型工厂 | Chat / Embedding / Rerank 模型动态创建 + 参数归一化 + 实例缓存 | 已实现 |
| 对话 Pipeline | 责任链 + Builder 编排「记忆 → 检索 → 附件 → 组装 → 模型 → 落库」 | 已实现 |
| 并发限制 | 单用户并发推理任务数限制（Redis ZSet + Lua 原子脚本） | 已实现 |
| 会话互斥 | 同 `sessionId` 串行化推理（Redisson RLock + 看门狗续期） | 已实现 |
| 文件服务 | MinIO 预签名直传、ETag 校验、桶迁移归档、并发/磁盘保护 | 已实现 |
| Token 配额 | 每日 Token 原子预扣与结算、按模型用量统计 | 部分实现（详见状态） |
| JWT 认证 | Spring Security + JWT 无状态认证 | 未接入（当前全路径放行） |

---

## 技术栈

### 语言与框架

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 21（启用 Preview 特性） | 开发语言 |
| Spring Boot | 4.0.6 | 应用框架 |
| Spring WebFlux | - | SSE 流式响应（Reactor `Flux`） |
| Spring Security | - | 认证鉴权框架（骨架就绪） |
| Spring Validation | - | 参数校验 |
| MyBatis-Plus | 3.5.15 | ORM（逻辑删除、字段自动映射） |
| Lombok / Hutool / Commons-Lang3 | - | 工具库 |

### AI 组件

| 组件 | 版本 / 型号 | 用途 |
|------|------------|------|
| LangChain4j | 1.16.2 | AI 编排框架（消息模型、记忆、RAG、工具） |
| 硅基流动 SiliconFlow | OpenAI 兼容协议 | 模型推理统一接入层（可替换 OpenAI / vLLM 等） |
| Chat Model | `Qwen/Qwen2.5-72B-Instruct`（dev 覆盖为 `Qwen/Qwen3.6-35B-A3B`） | 文本对话 |
| 多模态 Chat Model | `Qwen/Qwen2-VL-72B-Instruct`（配置声明） | 图文混合理解 |
| Embedding Model | `BAAI/bge-m3`（1024 维） | 文本向量化 |
| Rerank Model | `BAAI/bge-reranker-v2-m3`（Cohere 协议） | 召回重排序 |
| Apache PDFBox | langchain4j pdfbox parser | PDF 文本抽取 |

### 数据与中间件

| 技术 | 版本 | 用途 |
|------|------|------|
| MySQL | 8.0 | 业务关系数据 |
| Redis | 7.x（Redisson 4.4.0） | 会话记忆、限流计数、会话锁、配额计数 |
| RabbitMQ | amqp-client 5.30.0 | 异步任务（已配置，消费端待落地） |
| Elasticsearch | 9.4.1 客户端 | 日志检索（规划） |
| MinIO | 8.5.7 SDK | 对象存储（图片 / 文档） |
| Chroma | latest | 向量数据库（知识库 / 用户画像双 Collection） |

### 辅助与运维

| 技术 | 用途 |
|------|------|
| SpringDoc OpenAPI（Swagger UI） | 接口文档自动生成 |
| Spring Boot Actuator + Micrometer | 健康检查、Prometheus 指标 |
| Docker / Docker Compose | 依赖环境一键编排（8 个服务） |
| Maven Wrapper（mvnw） | 构建与依赖管理 |

---

## 系统架构

```
┌──────────────────────────────────────────────────────────────┐
│                        客户端 / 第三方系统                      │
└───────────────────────────┬──────────────────────────────────┘
                            │ HTTP / SSE
┌───────────────────────────▼──────────────────────────────────┐
│  接入层：Controller                                            │
│  MultimodalController  ChatPiplineController                  │
│  EmbeddingTextController  FileUploadController                │
│  + Spring Security 过滤链 / 全局异常处理 / 统一响应 Result       │
└───────────────────────────┬──────────────────────────────────┘
                            │
┌───────────────────────────▼──────────────────────────────────┐
│  业务层                                                        │
│  对话 Pipeline（责任链）:                                       │
│    ChatMemoryStage → ChatRagSearchStage → ChatFileStage       │
│    → ChatLoadInfoStage → ChatAgentStage → ChatMemorySaveStage │
│  MultimodalAssistantImpl（ReAct：while + ToolRegistry）        │
│  KnowledgeBaseRagService / UserProfileRagService（双域 RAG）   │
│  ConcurrentLimitService / ChatSessionLockService（并发治理）   │
└───────┬──────────────┬──────────────┬───────────────┬─────────┘
        │              │              │               │
┌───────▼─────┐ ┌──────▼──────┐ ┌─────▼──────┐ ┌──────▼────────┐
│ AI 基础设施  │ │  文件服务    │ │  记忆服务   │ │  工具体系      │
│ ChatModel   │ │ MinioService│ │ ChatMemory │ │ ToolRegistry  │
│ Factory     │ │ 预签名直传   │ │ Provider   │ │ @AiTool 收集   │
│ Embedding   │ │ 桶迁移归档   │ │ Redisson   │ │ 分组索引/执行   │
│ Factory     │ │ 并发限流     │ │ MemoryStore│ └───────────────┘
│ Rerank      │ └──────┬──────┘ └─────┬──────┘
│ Content     │        │              │
│ Resolver    │        │              │
└───────┬─────┘        │              │
        │              │              │
┌───────▼──────────────▼──────────────▼─────────────────────────┐
│  数据与中间件层                                                 │
│  MySQL │ Redis │ RabbitMQ │ MinIO │ Chroma │ Elasticsearch     │
└───────────────────────────┬──────────────────────────────────┘
                            │ OpenAI 兼容协议
┌───────────────────────────▼──────────────────────────────────┐
│  外部模型服务：硅基流动 SiliconFlow（可替换 OpenAI / vLLM / …）  │
└──────────────────────────────────────────────────────────────┘
```

---

## 目录结构

```
vllmpt/
├── doc/                              # 项目文档与 Docker 环境编排
│   ├── docker/
│   │   ├── docker-compose-env.yml    # 8 个基础服务编排
│   │   ├── init_chroma.py            # Chroma 初始化脚本
│   │   ├── README.md                 # Docker 环境使用说明
│   │   ├── mysql/  elasticsearch/  logstash/   # 各服务挂载配置
│   ├── 项目概览.md
│   ├── 配置说明.md
│   ├── project_improvement_plan.md
│   ├── 每日Token配额方案.md
│   ├── 并发限制练习指南.md
│   └── 会话互斥练习指南.md
├── src/
│   ├── main/
│   │   ├── java/org/albedo/vllmpt/
│   │   │   ├── VllmptApplication.java          # 启动类（@MapperScan）
│   │   │   ├── common/                         # 公共层
│   │   │   │   ├── enums/                      # BizScene/ModelType/InputType/… 枚举
│   │   │   │   ├── exception/                  # BusinessException + 全局异常处理
│   │   │   │   ├── redis/                      # RedisKey（key 统一管理）
│   │   │   │   ├── result/                     # Result<T> 统一响应
│   │   │   │   └── security/                   # 401/403 JSON 处理器
│   │   │   ├── config/                         # 中间件与 AI 组件装配
│   │   │   │   ├── ChatMemoryConfig            # 记忆 Provider
│   │   │   │   ├── VectorStoreConfig           # Chroma 双 Collection
│   │   │   │   ├── RerankConfig                # ScoringModel + 重排聚合器
│   │   │   │   ├── ToolAutoConfig              # @AiTool 工具自动收集
│   │   │   │   ├── RedissonConfig / MinioConfig / WebSecurityConfig
│   │   │   │   └── EmbeddingProperties
│   │   │   ├── core/order/pipeline/            # Pipeline-Stage 责任链核心
│   │   │   └── module/                         # 业务层
│   │   │       ├── ai/                         # AI 基础能力（模型工厂/文档抽取）
│   │   │       ├── chat/                       # 对话域（控制器/服务/工具/Pipeline Stage）
│   │   │       ├── file/                       # 文件域（上传/MinIO）
│   │   │       ├── order/config/               # Pipeline 组装配置
│   │   │       ├── quota/                      # Token 配额（实体/Mapper）
│   │   │       ├── stats/                      # 统计/自检
│   │   │       └── user/                       # 用户（实体/Mapper）
│   │   └── resources/
│   │       ├── application.yml                 # 公共配置
│   │       └── application-dev.yml             # 开发环境配置
│   └── test/
│       └── java/org/albedo/vllmpt/             # 单元测试
├── pom.xml
├── mvnw / mvnw.cmd
├── AI Agent后端项目文档.md
└── ReadMe.md
```

> 待补充：`application-test.yml`、`application-prod.yml`（`doc/配置说明.md` 中有描述，仓库中尚未提供）；`doc/sql/`（`doc/每日Token配额方案.md` 引用的建表脚本 `init_user_quota.sql` 未包含在仓库中）。

---

## 快速开始

### 环境要求

- JDK 21（编译启用 Preview 特性，`pom.xml` 锁定 `release 21`）
- Maven 3.9+（或使用自带 `mvnw`）
- Docker / Docker Compose（用于本地依赖服务）
- 可访问的硅基流动（SiliconFlow）API Key

### 1. 克隆项目

```bash
git clone <repository-url>   # 待补充：仓库地址
cd vllmpt
```

### 2. 启动依赖服务

项目提供了一键编排的基础服务（MySQL / Redis / RabbitMQ / Elasticsearch / Kibana / Logstash / MinIO / Chroma）：

```bash
cd doc/docker
docker compose -f docker-compose-env.yml up -d
# 查看状态
docker compose -f docker-compose-env.yml ps
```

各服务默认端口与账号（与 `application-dev.yml` 保持一致）：

| 服务 | 端口 | 账号 / 密码 |
|------|------|------------|
| MySQL | 3306 | `root` / `root123`（库：`vllmpt_dev`） |
| Redis | 6379 | 密码 `redis123` |
| RabbitMQ | 5672 / 15672 | `guest` / `guest` |
| Elasticsearch | 9200 | 安全认证关闭 |
| Kibana | 5601 | - |
| Logstash | 5044 / 9600 | - |
| MinIO | 9000 / 9001 | `minioadmin` / `minio123` |
| Chroma | 8000 | - |

### 3. 配置 API Key

在 `src/main/resources/application-dev.yml` 中将 `langchain4j.open-ai.chat-model.api-key` 配置为你的硅基流动 API Key，或通过环境变量注入：

```bash
# Linux / macOS
export SILICONFLOW_API_KEY=sk-xxxxxx

# Windows PowerShell
$env:SILICONFLOW_API_KEY="sk-xxxxxx"
```

> `application-dev.yml` 中该字段为 `api-key: ${SILICONFLOW_API_KEY:}`，未设置时为空。请勿将真实密钥提交到仓库。

### 4. 构建与运行

```bash
# 方式一：Maven Wrapper 启动（默认 profile=dev）
./mvnw spring-boot:run
# Windows
mvnw.cmd spring-boot:run

# 方式二：打包后运行
./mvnw clean package -DskipTests
java -jar target/vllmpt-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

应用默认端口 `8080`。启动后访问：

- Swagger UI：http://localhost:8080/swagger-ui.html
- Actuator（dev 全量暴露）：http://localhost:8080/actuator

### 5. 快速验证

```bash
# 同步多模态对话（Pipeline）
curl -X POST http://localhost:8080/api/pipline/fack \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"s1","text":"你好","modelName":"Qwen/Qwen3.6-35B-A3B"}'

# SSE 流式对话
curl -N -X POST http://localhost:8080/api/pipline/stream \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"s1","text":"写一段产品简介"}'
```

---

## 配置项说明

### 配置加载与优先级

- 公共配置：`application.yml`
- 环境配置：`application-dev.yml`（`spring.profiles.active: dev` 为默认）
- 优先级（高 → 低）：命令行参数 > 系统属性 > 环境变量 > `application-{profile}.yml` > `application.yml`

### 关键配置项

| 配置项 | 位置 | 说明 |
|--------|------|------|
| `spring.profiles.active` | application.yml | 激活的环境，默认 `dev` |
| `spring.datasource.*` | application-dev.yml | MySQL 连接与 HikariCP 连接池 |
| `spring.data.redis.*` | application-dev.yml | Redis 连接与 Lettuce 连接池 |
| `spring.rabbitmq.*` | application-dev.yml | RabbitMQ 连接、手动 ack、prefetch、并发与重试 |
| `spring.servlet.multipart.*` | application.yml | 文件上传限制（单文件 50MB / 请求 100MB） |
| `mybatis-plus.*` | application.yml | Mapper 位置、驼峰映射、逻辑删除字段 `deleted` |
| `springdoc.*` | application.yml / dev | Swagger / OpenAPI 开关 |
| `management.*` | application.yml / dev | Actuator 端点暴露与 Prometheus 指标 |
| `ai.models[]` | application.yml | 模型注册表（id / 类型 / 场景 / 单价 / 优先级） |
| `ai.routing.strategy` | application.yml | 路由策略（`PRIORITY` / `COST_OPTIMIZED` / `PERFORMANCE` / `LOAD_BALANCE`） |
| `ai.fallback.*` | application.yml | 降级重试配置（`max-retries` / `retry-delay`） |
| `ai.concurrent.*` | application-dev.yml | 单用户并发推理限制（`limit` / `window-seconds` / `key-ttl-seconds`） |
| `langchain4j.open-ai.chat-model.*` | application.yml / dev | 对话模型（base-url / api-key / model-name / timeout / temperature / max-tokens） |
| `langchain4j.open-ai.embedding-model.*` | application-dev.yml | 向量模型（`BAAI/bge-m3`，dimension 1024） |
| `langchain4j.open-ai.reranker-model.*` | application-dev.yml | 重排模型（`BAAI/bge-reranker-v2-m3`） |
| `app.vector-store.*` | application-dev.yml | Chroma 地址与双 Collection 名称 |
| `minio.*` | application-dev.yml | MinIO 端点、密钥、默认 Bucket、是否 HTTPS |
| `elasticsearch.*` | application-dev.yml | ES 连接与超时 |
| `logging.*` | application.yml / dev | 日志级别、滚动策略（100MB / 30 天） |

### 关键常量（代码内）

| 常量 | 位置 | 值 |
|------|------|----|
| 记忆窗口 | `ChatMemoryConfig` | `maxMessages=10` |
| ReAct 最大步数 | `MultimodalAssistantImpl` | 5；`ChatAgentStage` 为 10 |
| 分块大小 / 重叠 | `PlainTextParser` | `MAX_CHUNK_CHARS=1000` / `OVERLAP_CHARS=150` |
| 单行保护上限 | `PlainTextParser` | `MAX_LINE_CHARS=100000` |
| 召回数量 / 最低分 | `KnowledgeBaseRagServiceImpl`、`ChatRagSearchStage` | `maxResults=5` / `minScore=0.7` |
| 重排数量 / 最低分 | `RerankConfig` | `maxResults=5` / `minScore=0.7` |
| 文件处理并发 | `MinioService` | `Semaphore(4)` |
| 磁盘保护阈值 | `MinioService` | 2GB |
| 记忆 Key 前缀 | `RedissonChatMemoryStore` | `langchain4j:chat_memory:{sessionId}` |
| MinIO 桶 | 代码 | `vllmpt-temp`（临时）/ `vllmpt-data`（归档）/ `vllmpt-images`（配置） |

---

## API 接口说明

> 统一前缀 `/api`；统一响应体 `Result<T>{code, message, data}`；成功码 `200`。

### 多模态对话

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat/multimodal/multiple` | 同步多模态对话（含 ReAct 工具调用），返回 `{response}` |
| POST | `/api/chat/multimodal/stream` | SSE 流式对话（`text/event-stream`，`[DONE]` 结束） |
| POST | `/api/chat/multimodal/easyReply` | 占位接口（当前返回空，未实现） |
| POST | `/api/chat/multimodal/upload-and-chat` | 图片直传（multipart）+ 对话，当前仅返回上传后的 `imageUrl` |

对话请求体 `MultimodalChatRequest`：

| 字段 | 类型 | 说明 |
|------|------|------|
| `sessionId` | String | 会话 ID（记忆与锁的标识） |
| `text` | String | 用户输入文本 |
| `attachments` | List\<Attachment\> | 附件列表（`type`/`url`/`name`） |
| `modelName` | String | 指定模型，为空使用默认 |
| `temperature` | Double | 温度（归一化到 0.1 步长） |
| `maxTokens` | Integer | 最大 Token（归一化到 1024 倍数） |
| `isStream` | Boolean | 是否流式（接口另有区分） |

### Pipeline 对话

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/pipline/fack` | 同步 Pipeline 对话，返回 `Result<String>` |
| POST | `/api/pipline/stream` | SSE 流式 Pipeline 对话（`[DONE]` 结束） |

- 并发超额返回 `code=429`（「您的并发请求过多，请稍后重试」）；
- 会话占用返回 `code=409`（「该会话正在处理中，请稍候再试」，仅 `/fack`；`/stream` 推 `[ERROR]` 帧）。

### 知识库 / 文件向量化

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/indexAction/file` | 文件入库（校验 + 迁移 + 解析 + 向量化），返回归档对象名 |
| POST | `/api/indexAction/indexString` | 纯文本直接向量化入库 |
| POST | `/api/indexAction/searchEmbd` | 语义检索（调试用，日志输出结果） |
| POST | `/api/indexAction/DownloadFile` | 对象存储文件流式处理 |

### 文件上传

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/file/upload/UploadUrl` | 获取 MinIO 预签名上传 URL（`PUT`，5 分钟有效），返回 `{objectName, presignedUrl}` |
| POST | `/api/file/upload/image` | multipart 图片上传（≤10MB），返回 `{url}` |
| POST | `/api/file/upload/from-url` | 外链图片转存 MinIO，返回 `{url}` |

### 自检

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/stat/errorTest` | 返回固定错误响应 |
| POST | `/stat/errorTest2` | 抛出 `BusinessException` 验证全局异常处理 |

---

## 核心设计说明

### 1. Pipeline-Stage 责任链

一次对话被拆解为有序、可插拔的处理阶段（`org.albedo.vllmpt.core.order.pipeline`）：

```
ChatMemoryStage → ChatRagSearchStage → ChatFileStage
→ ChatLoadInfoStage → ChatAgentStage → ChatMemorySaveStage
```

- `ChatPipelineStage`：唯一抽象方法 `execute`，默认扩展点 `name()` / `shouldExecute()` / `onError()`；
- `ChatPipeline`：Builder 组装有序 Stage 列表（不可变）；
- `ChatPipelineExecutor`：统一执行骨架——每阶段「中断检查 → 条件跳过 → 执行 → 耗时日志 → 异常隔离」；
- `ChatPipelineContext`：单次请求唯一状态载体，含 `interrupt(reason)` 短路机制与 `attributes` Map。

### 2. RAG 检索增强

```
【入库】预签名直传(vllmpt-temp) → ETag 校验 → 桶迁移(vllmpt-data, 类型/日期 归档)
        → FileParserRegistry 按 MimeType 分派 → PlainTextParser 流式分块
        → bge-m3 向量化 → Chroma(vllmpt_knowledge_base)

【检索】Query 向量化 → Chroma 召回(minScore=0.7) → bge-reranker 重排(Top-5)
        → 结构化注入 System Prompt（【参考资料 N】来源 + 内容）
```

分块策略（`PlainTextParser`）：段落空行优先切分 → 达上限按中英文句末标点切分（1000 字符）→ Chunk 间重叠 150 字符 → 1.5 倍上限兜底强制切分；全程按字符块流式读取，避免单行超大导致 OOM。

### 3. ReAct 工具调用

模型在 `ChatAgentStage` 中多轮决策：若返回工具调用请求，则经 `ToolRegistry.executeSafely()` 执行并将结果回填上下文，继续下一轮；否则得到最终答复。工具通过 `@AiTool(groups=...)` + `@Tool` 声明，`ToolAutoConfig` 启动时自动收集（使用 `AopUtils.getTargetClass()` 兼容代理类），支持按分组暴露子集。

### 4. 会话记忆

`MessageWindowChatMemory`（窗口 10 条）绑定自定义 `ChatMemoryStore`，实际由 `RedissonChatMemoryStore` 基于 Redisson `RList` 实现，Key 为 `langchain4j:chat_memory:{sessionId}`，消息以防抖序列化器编解码，写入为「清空 + 全量覆盖」保证幂等。

### 5. 并发治理

- **单用户并发限制**（`ConcurrentLimitService`）：`ai:concurrent:{userId}` ZSet（member=requestId、score=时间戳），Lua 脚本原子完成「清理僵尸 member → 计数 → 判断 → 入队」，超限返回 `429`；
- **会话互斥**（`ChatSessionLockService`）：`ai:session:lock:{sessionId}` Redisson `RLock`，`tryLock(0, -1, SECONDS)` 立即返回并启用看门狗自动续期，`sessionId` 为空则放行。

---

## 项目状态

### 已实现

- 多模态对话（文本 + 图片 + 文档）与 SSE 流式输出；
- ReAct 工具调用与工具自动注册；
- RAG 管道（文本类文件：`text/plain`、`text/markdown`）与召回重排序；
- Redis 会话记忆、模型工厂（含实例缓存与参数归一化）；
- Pipeline-Stage 编排框架；
- 并发限制与会话互斥；
- MinIO 预签名直传、ETag 校验、桶迁移、并发/磁盘保护。

### 开发中 / 未完成（以代码注释与文档为准）

| 能力 | 现状 |
|------|------|
| JWT 认证 | `WebSecurityConfig` 当前对 `/**` 全部放行，JWT 过滤器未接入 |
| 每日 Token 配额 | `TokenQuotaService` 的 `preDeduct`/`settle`、`QuotaLimitResolver` 部分未接线/未实现 |
| 视频处理 | `VideoProcessor` 返回硬编码文本，未接入真实视频理解 |
| PDF 解析 | `PdfBoxDocumentExtractor.extractText` 返回空字符串（待实现） |
| Markdown 解析 | `MarkDownParser.process` 返回 `null` |
| 附件处理器注册 | `AttachmentProcessorRegistry` 目前仅注册 `image` |
| 模型智能路由 | `ModelRegistry` 为空类，路由策略未落地 |
| 对话摘要压缩 | 规划中，窗口满 10 条后由小模型总结 |
| 异步任务 | RabbitMQ 已配置，消费端拓扑待落地 |
| 多环境 | 仅有 dev，test/prod 配置缺失 |

---

## 常见问题

**Q1：启动时报数据库 / Redis 连接失败？**
请先执行 `docker compose -f doc/docker/docker-compose-env.yml up -d` 启动依赖，并核对 `application-dev.yml` 中的连接信息与端口。

**Q2：调用对话接口返回 401 或空响应？**
当前 `WebSecurityConfig` 对 `/**` 放行，一般不会 401；若模型无响应，请确认 `langchain4j.open-ai.chat-model.api-key` 已配置（或设置了 `SILICONFLOW_API_KEY`）且网络可访问 `https://api.siliconflow.cn/v1`。

**Q3：向量化 / 检索报错？**
请确认 Chroma 服务（默认 `http://localhost:8000`）已启动，且 `app.vector-store.*` 配置正确。测试环境无向量服务时，`ChatRagSearchStage` 会自动降级为通用提示词而不阻断对话。

**Q4：Elasticsearch 启动失败？**
多为内存不足或 `vm.max_map_count` 未设置。Linux 可执行 `sudo sysctl -w vm.max_map_count=262144`，并保证 Docker 可用内存 ≥ 4GB。

**Q5：并发请求被拒绝（429）？**
单用户并发上限默认 3（`ai.concurrent.limit`）。可调整该配置；请确保 `window-seconds` 大于最慢一次推理耗时，否则慢请求会被误判为僵尸。

**Q6：同一会话第二个请求返回 409 或 `[ERROR]` 帧？**
会话互斥锁生效：同一 `sessionId` 推理未结束时不允许并发。请等待上一请求完成，或使用不同 `sessionId`。

**Q7：文件上传报「Unsupported type」？**
`FileParserRegistry` 当前仅支持 `text/plain`、`text/markdown`；`AttachmentProcessorRegistry` 当前仅支持 `image`。其余类型待接入。

**更多问题**参见 `doc/配置说明.md`、`doc/docker/README.md`。

---

## 贡献指南

欢迎提交 Issue 与 Pull Request。

### 分支策略

- `main`：稳定分支
- `develop`：开发分支
- `feature/*`：功能分支

### 提交规范

Commit 信息遵循 `type(scope): description`，`type` 取值：`feat` / `fix` / `docs` / `style` / `refactor` / `test` / `chore`。

```
feat(chat): 支持对话中模型动态切换
fix(rag): 修复分块边界丢字问题
```

### 代码规范

- 遵循阿里巴巴 Java 开发手册；
- 使用 Lombok 消除样板代码；
- 统一异常处理与 `Result` 响应格式；
- 新增模块的 Mapper 包需在 `VllmptApplication` 的 `@MapperScan` 中补充。

### 提交前检查

```bash
./mvnw clean test     # 运行测试
./mvnw clean package  # 确认可构建
```

---

## 许可证

待补充：`pom.xml` 中 `<licenses>` 尚未声明许可证信息。

---

## 相关文档

- [AI Agent 后端项目文档](./AI%20Agent后端项目文档.md) —— 完整架构与实现说明
- [项目概览](./doc/项目概览.md)
- [配置说明](./doc/配置说明.md)
- [项目改进方案](./doc/project_improvement_plan.md)
- [Docker 环境说明](./doc/docker/README.md)

---

<p align="right">最后更新：2026-10-07</p>
