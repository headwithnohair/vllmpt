# AI Agent 后端项目文档 —— 多模态智能对话助手平台（vllmpt）

> **文档定位**：本项目文档以 5 年经验后端工程师视角编写，完整梳理 AI Agent 后端的功能模块与技术实现方案，可用于简历撰写、面试复盘、项目交接与二次开发。
> **文档版本**：v1.0 | **维护说明**：随项目演进持续更新

---

## 目录

1. [项目概述](#1-项目概述)
2. [技术栈](#2-技术栈)
3. [架构设计](#3-架构设计)
4. [核心业务功能](#4-核心业务功能)
5. [权限与角色管理](#5-权限与角色管理)
6. [状态管理方案](#6-状态管理方案)
7. [路由设计](#7-路由设计)
8. [组件封装](#8-组件封装)
9. [接口对接方式](#9-接口对接方式)
10. [性能优化](#10-性能优化)
11. [工程化配置](#11-工程化配置)
12. [部署方案](#12-部署方案)
13. [个人职责与量化成果](#13-个人职责与量化成果)
14. [附录：技术演进路线](#附录技术演进路线)

---

## 1. 项目概述

### 1.1 项目定位

本项目是一个**多模态 AI Agent 后端平台**，为上层业务（智能问答、知识库助手、智能审批助手等场景）提供统一的 AI 能力底座，核心能力包括：

- **多模态对话**：支持「文本 + 图片 + 文档 + 视频」混合输入的智能对话；
- **RAG 检索增强生成**：自建文本知识库，支持大文件上传、智能分块、向量化存储、语义检索与召回重排序；
- **Agent 工具调用（ReAct 模式）**：大模型自主决策调用业务工具，支持工具注册、分组管理与安全执行；
- **多轮对话记忆**：基于 Redis 的会话级短期记忆持久化与摘要压缩，支持长对话上下文管理；
- **流式输出（SSE）**：打字机式实时响应，优化首屏体验；
- **多模型动态切换**：运行时按请求指定模型、温度、最大 Token，工厂模式 + 实例缓存。

### 1.2 业务背景

企业知识密集型场景（智能客服、内部审批、知识问答）中普遍存在以下痛点：

| 痛点 | 本平台解决方案 |
|------|--------------|
| 人工客服/审批响应慢、成本高 | 7×24 小时 AI 实时响应，自动化处理高频咨询 |
| 企业私有知识无法被通用大模型利用 | RAG 管道：文档上传 → 分块 → 向量化 → 检索增强生成 |
| 单模型能力/成本/时延不可兼得 | 模型工厂 + 多模型注册，按场景与请求动态切换模型 |
| 多轮对话上下文丢失、Token 成本失控 | Redis 记忆窗口 + 摘要压缩 + 多模态内容文本化标记 |
| 业务数据无法触达对话（查库存、查审批状态） | ReAct Agent + Function Calling 工具体系 |

### 1.3 核心目标

1. **能力统一**：一套后端同时支撑「对话、检索、工具调用、文件管理」四大 AI 基础能力，业务方零成本复用；
2. **可扩展**：对话处理链路采用 Pipeline-Stage 责任链模式，新增处理环节（审核、限流、摘要）只需新增 Stage，不改主干代码；
3. **可观测**：全链路日志 + 阶段级耗时统计 + Actuator 指标暴露，问题可定位、性能可度量；
4. **成本可控**：模型实例缓存、记忆内容文本化降 Token、RAG 精准召回减少无效上下文。

### 1.4 解决的问题

- 解决多模态内容（图片/文档/视频）异构输入的统一建模与解析问题；
- 解决大文件知识库入库的内存安全问题（流式解析、强制截断、并发限流）；
- 解决对话上下文的持久化、多实例共享与 Token 膨胀问题；
- 解决工具调用的注册、发现、分组暴露与异常隔离问题；
- 解决对象存储直传的安全校验与生命周期管理问题（预签名 URL + 桶迁移）。

---

## 2. 技术栈

### 2.1 后端语言与框架

| 技术 | 版本 | 用途 | 选型理由 |
|------|------|------|---------|
| Java | 21（LTS，启用 Preview 特性） | 开发语言 | 虚拟线程、模式匹配等新特性，长期支持 |
| Spring Boot | 4.0.6 | 应用框架 | 生态成熟，自动装配，快速开发 |
| Spring WebFlux | - | SSE 流式响应（Reactor Flux） | 非阻塞 IO，适配流式场景 |
| Spring Security | - | 认证鉴权框架 | 过滤链体系，配合 JWT 实现无状态认证 |
| Spring Validation | - | 参数校验 | 声明式校验，统一异常转换 |
| MyBatis-Plus | 3.5.15 | ORM | 通用 CRUD、逻辑删除、字段自动映射 |
| Lombok / Hutool / Commons-Lang3 | - | 工具库 | 减少样板代码 |

### 2.2 数据库与中间件

| 技术 | 版本 | 用途 |
|------|------|------|
| MySQL | 8.0 | 业务关系数据（会话记录、文件元数据、用户/权限） |
| Redis | 7.x（Redisson 4.4.0） | 会话记忆持久化（RList）、缓存、分布式锁、限流计数 |
| RabbitMQ | 3.12（amqp-client 5.30.0） | 异步任务队列（文档向量化、日志投递）、事件驱动 |
| Elasticsearch | 9.4.1 客户端 | 全文检索、日志存储分析（配合 Logstash/Kibana） |
| MinIO | 8.5.7 SDK | 对象存储（图片/文档/视频），S3 兼容协议 |
| Chroma | latest（LangChain4j chroma 模块） | 向量数据库，知识库与用户画像双 Collection |

### 2.3 AI 相关组件

| 组件 | 版本/型号 | 用途 |
|------|----------|------|
| LangChain4j | 1.16.2（Spring Boot4 Starter） | AI 应用编排框架：消息模型、记忆抽象、RAG 抽象、工具调用 |
| 硅基流动 SiliconFlow | OpenAI 兼容 API | 模型推理服务统一接入层（可平替 OpenAI/vLLM 本地部署） |
| Qwen2.5-72B-Instruct | Chat Model | 文本对话主力模型 |
| Qwen2-VL-72B-Instruct | 多模态 Chat Model | 图文混合输入理解 |
| BAAI/bge-m3 | Embedding Model（1024 维） | 文本向量化（知识库索引与查询） |
| BAAI/bge-reranker-v2-m3 | Rerank Model（Cohere 协议） | RAG 召回重排序 |
| Apache PDFBox | -（LangChain4j pdfbox parser） | PDF 文档文本抽取 |

### 2.4 辅助与运维

| 技术 | 用途 |
|------|------|
| SpringDoc OpenAPI（Swagger UI） | 接口文档自动生成 |
| Spring Boot Actuator + Micrometer | 健康检查、指标暴露（Prometheus 格式） |
| Docker / Docker Compose | 环境容器化编排（8 个基础服务一键启动） |
| Maven（mvnw Wrapper） | 构建与依赖管理，JDK 21 release 锁定 |

---

## 3. 架构设计

### 3.1 整体架构图

```
┌────────────────────────────────────────────────────────────────────┐
│                         客户端层                                    │
│        Web 管理端 / H5 / 小程序 / 第三方业务系统                     │
└───────────────────────────┬────────────────────────────────────────┘
                            │ HTTP / SSE (HTTPS)
┌───────────────────────────▼────────────────────────────────────────┐
│                    接入层（Controller）                              │
│  MultimodalController   ChatPiplineController                      │
│  EmbeddingTextController   FileUploadController   MultimodalEnhanced│
│  ── Spring Security 过滤链（认证/鉴权） / 全局异常处理 / 统一响应    │
└───────────────────────────┬────────────────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────────────────┐
│                     业务编排层（Service）                            │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────┐      │
│  │         对话处理 Pipeline（责任链 + Builder 组装）          │      │
│  │  ChatLoadInfoStage → ChatMemoryStage → ChatFileStage     │      │
│  │      → ChatRagSearchStage → 模型调用 → 记忆回写           │      │
│  └──────────────────────────────────────────────────────────┘      │
│                                                                     │
│  MultimodalAssistantImpl（ReAct Agent 循环：while + ToolRegistry）  │
│  KnowledgeBaseRagService / UserProfileRagService（双域 RAG）        │
└───────┬──────────────┬──────────────┬───────────────┬──────────────┘
        │              │              │               │
┌───────▼─────┐ ┌──────▼──────┐ ┌─────▼──────┐ ┌──────▼───────────┐
│  AI 基础设施 │ │  文件服务    │ │  记忆服务   │ │  工具体系         │
│ ChatModel   │ │ MinioService│ │ ChatMemory │ │ ToolRegistry     │
│ Factory     │ │ 预签名直传   │ │ Provider   │ │ @AiTool 自动收集  │
│ Embedding   │ │ 桶迁移归档   │ │ Redisson   │ │ @Tool 方法扫描    │
│ Factory     │ │ 并发限流     │ │ ChatMemory │ │ 分组索引/安全执行  │
│ Rerank      │ └──────┬──────┘ │ Store      │ └──────────────────┘
│ Factory     │        │        └─────┬──────┘
│ Content     │        │              │
│ Resolver    │        │              │
└───────┬─────┘        │              │
        │              │              │
┌───────▼──────────────▼──────────────▼──────────────────────────────┐
│                          数据与中间件层                              │
│  MySQL(业务库)  Redis(记忆/缓存)  RabbitMQ(异步任务)  MinIO(对象)    │
│  Chroma(向量库: 知识库/用户画像)  Elasticsearch+Logstash+Kibana(日志)│
└───────────────────────────┬────────────────────────────────────────┘
                            │ OpenAI 兼容协议
┌───────────────────────────▼────────────────────────────────────────┐
│                     外部模型服务层                                   │
│      硅基流动 SiliconFlow（Qwen 系列 / bge-m3 / bge-reranker）        │
│      可平替：OpenAI / Azure / DashScope / 本地 vLLM / Ollama         │
└────────────────────────────────────────────────────────────────────┘
```

### 3.2 分层设计与模块划分

采用「**三层分包 + 领域模块化**」的组织方式，包结构如下：

```
org.albedo.vllmpt
├── common                          # 公共层：与业务无关的横切能力
│   ├── enums                       # BizScene/InputType/ModelType/MessageRole/
│   │                               # Role/RoutingStrategyType 六大枚举
│   ├── exception                   # BusinessException + 全局异常处理器
│   ├── result                      # Result<T> 统一响应体
│   └── security                    # 401/403 JSON 处理器
├── config                          # 配置层：中间件与 AI 组件 Bean 装配
│   ├── ChatMemoryConfig            # 记忆 Provider（窗口记忆 + Redis 存储）
│   ├── VectorStoreConfig           # Chroma 双 Collection
│   ├── RerankConfig                # ScoringModel + 重排聚合器
│   ├── ToolAutoConfig              # @AiTool 工具自动收集
│   ├── RedissonConfig / MinioConfig / WebSecurityConfig / ...
├── core                            # 核心层：与业务无关的框架抽象
│   └── order/pipeline              # Pipeline-Stage 责任链核心（5 个类）
└── module                          # 业务层：按领域划分
    ├── ai                          # AI 基础能力（模型工厂/文档抽取/模型注册）
    ├── chat                        # 对话域（最大模块：控制器/服务/工具/附件
    │                               # 处理器/文件解析器/Pipeline Stage）
    ├── file                        # 文件域（上传/MinIO 服务）
    └── stats                       # 统计域（调用统计与测试）
```

**分层职责说明**：

| 层次 | 职责 | 依赖方向 |
|------|------|---------|
| common | 枚举、异常、统一响应、安全处理 | 不依赖任何业务模块 |
| core | Pipeline 抽象等可复用框架件 | 仅依赖 common |
| config | 组件装配与外部中间件接入 | 依赖 core/module |
| module | 业务实现（chat/ai/file/stats） | 依赖 common/core，模块间通过接口协作 |

### 3.3 核心抽象：Pipeline-Stage 责任链

对话处理链路采用**责任链 + Builder**模式，将「一次对话」拆解为有序的、可审查的、可插拔的处理阶段：

```java
// Stage 函数式接口：唯一抽象方法 + 三个默认扩展点
public interface ChatPipelineStage<C extends ChatPipelineContext> {
    void execute(C context);                          // 核心处理逻辑
    default String name() { ... }                     // 阶段名（日志/审计）
    default boolean shouldExecute(C context) { ... }  // 条件跳过（动态开关）
    default void onError(C context, Throwable e) {...}// 异常回调（告警/补偿）
}

// Pipeline：Builder 组装有序 Stage 列表，不可变（unmodifiableList）
ChatPipeline.<ChatPipelineContext>builder("chat-main")
        .addStage(loadInfoStage)     // 1. 会话信息加载
        .addStage(memoryStage)       // 2. 短期记忆装配
        .addStage(fileStage)         // 3. 多模态附件解析
        .addStage(ragSearchStage)    // 4. 知识库检索增强
        .build();

// Executor：统一执行骨架
//   ① 每阶段执行前进行「中断检查」——支持任意 Stage 触发 interrupt(reason) 短路
//   ② shouldExecute 条件跳过——按上下文动态裁剪链路
//   ③ 阶段级 try-catch + onError 回调——异常隔离，可扩展告警与补偿
//   ④ 阶段级耗时埋点日志——Pipeline[x] stage[y] done in zms
```

**设计收益**：
- **可审查**：每个 Stage 有独立 name()，Executor 输出阶段耗时，天然形成处理审计日志；
- **易更新**：新增「内容安全审核」「Token 预算控制」「语义缓存」等能力 = 新增一个 Stage 类 + 一行 addStage；
- **可测试**：Stage 无状态（上下文通过 Context 传递），单测只需构造 Context 断言副作用。

### 3.4 数据流转（一次多模态对话）

```
用户请求(text+图片+文档) 
  → Controller 校验 
  → MultimodalAssistantImpl
     ① ChatMemoryProvider.get(sessionId)            # 从 Redis 加载历史消息
     ② MultimodalContentResolver.resolve()          # 附件分派处理器
        ├─ ImageProcessor    → ImageContent(模型用) + "[图片: xx]"(记忆用)
        ├─ DocumentProcessor → 摘要文本 + 触发向量化标记
        └─ VideoProcessor    → 描述文本
     ③ KnowledgeBaseRagService.searchRelevantTexts()# RAG 检索
        ├─ bge-m3 查询向量化
        ├─ Chroma Top-K 召回(minScore=0.7)
        └─ bge-reranker-v2-m3 重排 → Top-5
     ④ SystemMessage(RAG 注入) + 历史消息 + UserMessage 组装
     ⑤ ReAct 循环（while stepCount < 5）
        ├─ ChatModel.chat(带 ToolSpecifications)
        ├─ 若返回 ToolExecutionRequest → ToolRegistry 执行 → 结果回填 → 继续
        └─ 否则 → 得到 finalResult，退出循环
     ⑥ memory.add(文本化用户消息) + memory.add(AiMessage)  # Redis 持久化
  → 返回响应 / SSE 流式推送
```

---

## 4. 核心业务功能

### 4.1 多模态对话（核心链路）

**功能**：支持单轮输入中混合「文本 + 多图片 + 文档 + 视频」，模型侧与记忆侧双通道内容建模。

**实现要点**（`MultimodalAssistantImpl` / `MultimodalContentResolver`）：

1. **附件解析策略体系**：`AttachmentProcessor` 接口 + `AttachmentProcessorRegistry` 注册表 + 三类处理器：

   | 处理器 | 支持类型 | 模型侧输出 | 记忆侧输出 |
   |--------|---------|-----------|-----------|
   | ImageProcessor | image | `ImageContent.from(url)` | `[图片: 文件名]` |
   | DocumentProcessor | pdf/doc/txt/md | 短文本全文 / 长文档摘要 | `[文档内容: ...]` / `[长文档: xx 已索引]` |
   | VideoProcessor | video | 视频描述文本 | `[视频: xx 内容: ...]` |

2. **多模态记忆降 Token 设计**：图片在记忆中不保存 URL/二进制，而是替换为 `[图片: 名字]` 文本标记。**收益**：图片 URL 平均 100+ token，文本标记约 10 token，多轮多图对话下记忆 Token 消耗降低约 80%，且窗口记忆可容纳更多有效轮次。后续演进方向：智能替换回 URL（当后续轮次再次引用该图片时）。

3. **双流输出**：同步接口（阻塞返回完整结果）与 SSE 流式接口（打字机效果）共用同一套记忆与解析逻辑。

### 4.2 ReAct Agent 与工具调用（Function Calling）

**功能**：大模型根据用户意图自主决定是否调用工具、调用哪个工具、以什么参数调用，多步推理直至产出最终答案。

**实现要点**：

```
AgentContext { currentMessages / finalResult / stepCount }
while (agentContext.stepCount < 5) {                    # 最大步数熔断，防死循环
    ChatRequest = messages + toolSpecifications         # 全量注入工具描述
    AiMessage = chatModel.chat(chatRequest)
    if (aiMessage.hasToolExecutionRequests()) {
        messages.add(aiMessage)                          # 工具调用意图入上下文
        for (ToolExecutionRequest req : requests) {
            result = registry.execute(req)               # 执行工具
            messages.add(ToolExecutionResultMessage.from(req, result))
        }
    } else {
        agentContext.finalResult = aiMessage; break      # 终态退出
    }
    stepCount++
}
```

**工具注册表（ToolRegistry）设计**：

- **声明式注册**：业务类标注 `@AiTool(groups = {"weather", "public"})` 并实现 `AiToolProvider`，`ToolAutoConfig` 在容器启动时自动收集所有工具 Bean，反射扫描 `@Tool` 注解方法构建 `DefaultToolExecutor`；
- **AOP 代理兼容**：使用 `AopUtils.getTargetClass()` + `AnnotationUtils.findAnnotation()` 获取真实类注解，解决 CGLIB 代理导致注解丢失的问题；
- **分组索引**：`groupIndex: Map<group, Set<toolName>>`，支持按业务场景选择性暴露工具子集（`specsByGroup`），避免工具描述全量注入挤占上下文；
- **安全执行**：`executeSafely()` 捕获一切异常并返回错误描述给模型（而非中断流程），让模型具备基于失败结果的自我修正能力。

**示例工具**（`SimpleTool`）：`getWeather(place)`（实时天气查询）、`getAnswer()`（问候/兜底回复），均通过 `@Tool`/`@P` 注解声明自然语言描述与参数约束，供模型理解语义。

### 4.3 RAG 检索增强（知识库管道）

**功能**：自建文本知识库，支持大型文件上传入库与对话时语义检索增强，检索结果注入 System Prompt 约束模型「严格基于参考资料回答，不编造」。

**完整管道**：

```
【入库】
前端请求预签名URL → MinIO 直传(vllmpt-temp 桶, PUT 5分钟有效)
  → 后端 FileExistCheck(statObject + ETag 一致性校验)
  → FileChangeBucket(temp → data 桶, 按 类型/日期 目录归档)
  → FileParserRegistry 按 MimeType 分派解析器
  → PlainTextParser 流式分块(见下) 
  → TextEmbedding.chunkEmbeding → bge-m3 向量化 → Chroma 知识库 Collection

【检索】
用户 Query → bge-m3 向量化 
  → EmbeddingSearchRequest(maxResults=K, minScore=0.7) 
  → Chroma 语义召回 
  → ReRankingContentAggregator(bge-reranker-v2-m3) 重排 → Top-5
  → 结构化注入 System Prompt（【参考资料 N】来源+内容）
```

**智能分块策略**（`PlainTextParser`，四层策略）：

| 层级 | 策略 | 参数 |
|------|------|------|
| 第一层 | 自然段落边界切分（空行优先） | - |
| 第二层 | 达上限后在语义边界切分（中英文句末标点：。！？.!?；：等） | MAX_CHUNK_CHARS=1000 |
| 第三层 | Chunk 间重叠，防上下文在边界丢失 | OVERLAP_CHARS=150（约 15%） |
| 第四层 | 兜底强制截断（1.5 倍上限） | 1500 字符 |

**工程细节**：
- **流式按字符块读取**（8KB 缓冲），完全不依赖 `readLine()`，单行超大文本不 OOM；缓冲区异常增长（>10 万字符）时强制 flush 兜底；
- 预分配 `StringBuilder` 容量减少扩容开销；
- 每个 Chunk 附带 `Metadata{fileName, fileType, chunkIndex}`，检索结果可溯源展示「来源」。

**双向量域设计**（`VectorStoreConfig`）：`vllmpt_knowledge_base`（公共知识库）与 `vllmpt_user_profiles`（用户画像）两个 Chroma Collection 物理隔离，`KnowledgeBaseRagService` 与 `UserProfileRagService` 分别服务，为后续「个性化 RAG」（用户长期记忆检索注入）预留底座。

### 4.4 多轮对话记忆管理

**功能**：会话级短期记忆，跨请求上下文保持，多实例部署下记忆共享。

**实现**（`ChatMemoryConfig` + `RedissonChatMemoryStore`）：

- `MessageWindowChatMemory`（滑动窗口，maxMessages=10）绑定自定义 `ChatMemoryStore`；
- `RedissonChatMemoryStore` 基于 Redisson `RList` 实现，Key 规则 `langchain4j:chat_memory:{sessionId}`，消息以 LangChain4j 官方 JSON 序列化器（`ChatMessageSerializer/Deserializer`）编解码，`updateMessages` 全量覆盖写保证幂等；
- **摘要压缩规划**：窗口满 10 条后由小模型将前 N 条总结为摘要（总结后为 6 条），原始消息让位于摘要——长对话 Token 消耗曲线从线性增长降为近似常数。

### 4.5 SSE 流式输出

**功能**：`POST /api/chat/multimodal/stream`，`text/event-stream` 逐 token 推送，结尾以 `[DONE]` 标记。

**实现**（`DefaultChatStream` + Controller）：

- 自定义 `ChatStream` 流式编程模型（`onNext`/`onComplete`/`onError` 链式注册回调），将 LangChain4j 的 `StreamingChatResponseHandler` 回调风格适配为业务友好的链式风格；
- 所有回调提供默认空实现，防止调用方未注册时空指针；
- Controller 侧 `Flux.create(sink -> ...)` 桥接，完成时推送 `[DONE]` 后 `sink.complete()`；
- **记忆异步回写**：`onCompleteResponse` 中将「文本化用户消息 + AiMessage」写入记忆，不阻塞流式推送。

### 4.6 文件管理（MinIO 对象存储）

**功能**：预签名 URL 直传、存在性校验、桶生命周期迁移、流式下载处理。

**实现**（`MinioService`）：

| 能力 | 实现方案 |
|------|---------|
| 预签名直传 | `getPresignedObjectUrl(PUT, 5min)`，前端绕过后端直传 `vllmpt-temp` 桶，降低后端带宽压力 |
| 完整性校验 | `statObject` 回填 mimeType/size/ETag，ETag 比对确认上传成功 |
| 桶迁移归档 | `copyObject` + `removeObject`，目标路径 `类型/日期/objectName` 自动分类 |
| 并发限流 | `Semaphore(4)` 限制同时处理的文件数，防止突发大文件拖垮服务 |
| 磁盘保护 | 处理前检查本地临时目录可用空间（<2GB 拒绝处理） |
| 流式处理 | 8KB 缓冲边读边写本地临时文件，finally 块强制清理，失败文件留待兜底任务 |
| 类型识别 | MimeType 白名单映射（image/video/word 等办公文档类） |

### 4.7 对话处理 Pipeline 编排（chatPipeline）

将 4.1~4.4 的能力以 Stage 形式编排进统一管道（`OrderPipelineService` 装配 `chatPipeline` Bean）：

| Stage | 职责 | 状态 |
|-------|------|------|
| ChatLoadInfoStage | 加载会话元信息、模型参数装配 | 已落地骨架 |
| ChatMemoryStage | 按 sessionId 装配 Redis 短期记忆到 Context | 已实现 |
| ChatFileStage | 附件解析 → 多模态 UserMessage 构建 | 已实现 |
| ChatRagSearchStage | 知识库检索 + 重排 + Prompt 注入 | 已实现（服务层可复用） |

`ChatPipelineContext` 持有 `attributes Map`（松散 KV 传递）+ 强类型字段（`rawChatMemory`/`attachments`/`userMessage`）+ `interrupt(reason)` 中断机制，兼顾灵活与类型安全。

---

## 5. 权限与角色管理

### 5.1 认证方式

- **JWT 无状态认证**（技术选型 JJWT 0.13.0，`jjwt-api/impl/jackson` 三件套）：
  - 登录签发 Access Token（HS256 签名），敏感操作配合 Refresh Token（Redis 存储）续期；
  - 网关/过滤器层校验签名与过期时间，无共享 Session，天然支持水平扩展。
- **API Key（规划）**：面向第三方系统接入，用户级密钥 + 调用配额绑定。

### 5.2 权限模型

采用 **RBAC（基于角色的访问控制）**：

```
用户(User) ──N:M── 角色(Role) ──N:M── 权限(Permission) ──→ API/菜单/数据
```

角色划分规划：

| 角色 | 权限范围 |
|------|---------|
| SYSTEM（超级管理员） | 全部功能 + 用户/角色/模型配置管理 |
| ADMIN（运营管理员） | 知识库管理、会话审计、统计看板 |
| USER（普通用户） | 多模态对话、文件上传、个人会话管理 |
| API（第三方调用方） | OpenAPI 子集，受配额与限流约束 |

### 5.3 接口鉴权机制

- **Spring Security 过滤链**（`WebSecurityConfig`）：
  - 关闭 CSRF（纯前后端分离 + Token 认证场景）；
  - `authorizeHttpRequests` 声明式路径授权：`/actuator/**`、`/swagger-ui/**`、`/v3/api-docs/**` 白名单，其余 `authenticated()`；
  - 自定义 `CustomAuthenticationEntryPoint`（401）与 `CustomAccessDeniedHandler`（403），以统一 JSON 格式（`Result`）返回安全错误，避免默认 HTML 错误页；
- **方法级鉴权（规划）**：`@PreAuthorize("hasRole('ADMIN')")` 精细化控制；
- **数据级隔离（规划）**：会话、知识库按 userId 租户隔离，向量检索 metadata filter 注入 `userId` 条件。

> 说明：当前开发阶段过滤链对 `/**` 放行以便联调，JWT 过滤器（`JwtAuthenticationFilter`）为下一步 P0 项，依赖与安全骨架（401/403 处理器）已就绪。

---

## 6. 状态管理方案

### 6.1 会话状态（对话记忆）

| 维度 | 方案 |
|------|------|
| 存储 | Redis（Redisson RList），Key：`langchain4j:chat_memory:{sessionId}` |
| 结构 | 有序消息 JSON 数组（官方序列化器），窗口 10 条 |
| 读写 | 每轮对话读全量 → 追加当前轮 → 覆盖写回（幂等） |
| 共享 | Redis 集中存储，多实例部署下任意节点可恢复上下文 |
| 生命周期 | 会话活跃期驻留 Redis；**卸载策略（规划）**：用户退出会话后记忆卸载持久化至 MySQL，Redis Key 设置 TTL 兜底回收 |

### 6.2 Agent 运行状态（ReAct 循环）

- `AgentContext` 承载单次推理的运行态：`currentMessages`（累积消息链）、`finalResult`（终态答案）、`stepCount`（步数计数器）；
- **步数熔断**：`stepCount < 5` 硬上限，防止工具调用死循环；
- **中间态不入记忆**：工具调用轮次的消息仅保留在当前推理上下文，循环结束后仅将「用户文本化消息 + 最终答案」写入长期记忆——避免 ToolExecutionRequest/Result 污染记忆窗口；
- **状态机化（演进方向）**：将 ReAct 循环显式建模为状态机（THINKING → TOOL_CALLING → OBSERVING → FINAL），每态可持久化、可恢复，支撑长任务 Agent。

### 6.3 任务状态（异步文件处理）

- 文件入库任务状态流转：`UPLOADED(temp 桶) → VERIFIED(ETag 校验通过) → ARCHIVED(data 桶) → PARSING → INDEXED`；
- 当前以同步接口 + 日志埋点跟踪；**演进方案**：引入 RabbitMQ 异步化，任务表记录状态机字段，接口返回 traceId 供前端轮询/推送进度。

### 6.4 Pipeline 上下文状态

`ChatPipelineContext` 是单次请求内的**唯一状态载体**：

- `interrupted + interruptReason`：任意 Stage 可中断后续流程（如审核不通过），Executor 感知后短路返回；
- `attributes Map`：跨 Stage 松散传参；
- `StageAudit`（审计实体）：记录每个 Stage 的输入/输出消息数、Token 变化、耗时与操作摘要，为「流程可审查」提供数据结构支撑（配合 `ModelPipelineContext` 的 Token 预算字段：system/rag/history 三段预算分配）。

---

## 7. 路由设计

### 7.1 API 路由结构

统一前缀 `/api`，按业务域分组：

| 模块 | 路由 | 方法 | 说明 |
|------|------|------|------|
| 多模态对话 | `/api/chat/multimodal/multiple` | POST | 同步多模态对话（含 ReAct 工具调用） |
| | `/api/chat/multimodal/stream` | POST | SSE 流式对话（text/event-stream） |
| | `/api/chat/multimodal/upload-and-chat` | POST | 图片直传 + 对话一体化 |
| Pipeline | `/api/pipline/fack` | POST | 管道化对话入口（Pipeline 编排） |
| 知识库 | `/api/indexAction/file` | POST | 文件入库（校验+迁移+解析+向量化） |
| | `/api/indexAction/indexString` | POST | 纯文本直接向量化入库 |
| | `/api/indexAction/searchEmbd` | POST | 语义检索（调试用） |
| | `/api/indexAction/DownloadFile` | POST | 对象存储文件流式处理 |
| 文件服务 | `/api/file/upload/UploadUrl` | POST | 获取 MinIO 预签名上传 URL |
| | `/api/file/upload/image` | POST | multipart 图片上传（≤10MB） |
| | `/api/file/upload/from-url` | POST | 外链图片转存 MinIO |
| 统计 | `/stat/errorTest`、`/stat/errorTest2` | POST | 异常链路自检 |
| 运维 | `/actuator/**`、`/swagger-ui/**` | GET | 监控与文档（环境差异化暴露） |

### 7.2 版本控制与命名规范

- **版本策略**：RESTful 风格预留 `/api/v{n}` 版本段，破坏性变更升版本、旧版本 N-1 并行维护；
- **命名规范**：
  - 资源用名词复数、动作用 HTTP 语义（GET 查询 / POST 创建 / PUT 更新 / DELETE 删除）；复合操作（非纯 CRUD）使用 `名词/动词` 子路径（如 `indexAction/file`、`upload/UploadUrl`）；
  - 响应统一 `Result<T>{code, message, data}`，业务码：200 成功 / 400 参数错误 / 401 未认证 / 403 无权限 / 500 系统异常；
- **中间件链**：请求 → Spring Security 过滤链（认证鉴权）→ 参数校验（Validation）→ Controller → 全局异常处理器（`@RestControllerAdvice` 捕获 `BusinessException`/`MethodArgumentNotValidException`/未知异常，统一转 `Result`）。

---

## 8. 组件封装

### 8.1 公共组件

| 组件 | 说明 |
|------|------|
| `Result<T>` | 统一响应体，静态工厂方法（success/error/init） |
| `BusinessException` + `GlobalExceptionHandler` | 业务异常携带 code/message；全局兜底三种异常，日志分级（业务 warn / 系统 error） |
| 枚举体系 | `BizScene`（业务场景）、`ModelType`（TEXT/IMAGE/MULTIMODAL）、`InputType`、`MessageRole`、`RoutingStrategyType`（PRIORITY/COST/PERFORMANCE/ROUND_ROBIN）——场景路由与模型路由的语义基础 |
| 安全处理器 | 401/403 JSON 化输出 |

### 8.2 AI 能力组件（module/ai）

| 组件 | 封装内容 | 关键设计 |
|------|---------|---------|
| `ChatModelFactory` | ChatModel / StreamingChatModel 动态创建 | **参数归一化 + 实例缓存**：temperature 归一到 0.1 步长、maxTokens 归一到 1024 倍数，`ConcurrentHashMap` 以 `model:temp:tokens` 为 Key 缓存实例，同参数请求零重复构建开销；支持对话中模型热切换 |
| `EmbeddingModelFactory` | EmbeddingModel 创建 + 默认模型 DCL 单例 | 双重检查锁记录首个模型为默认模型 |
| `RerankModelFactory` | Cohere 协议 ScoringModel（重排）创建 | 实例缓存 |
| `MultimodalContentResolver` | 附件 →「模型 Contents + 记忆纯文本」双通道解析 | 策略模式委托 AttachmentProcessorRegistry |
| `RedissonChatMemoryStore` | ChatMemoryStore 的 Redis 实现 | LangChain4j SPI 扩展点实现，可插拔替换 |
| `DocumentExtractor`/`PdfBoxDocumentExtractor` | 文档抽取 + `DocumentSplitters.recursive(300, 30)` 分块 + 段落元数据注入 | 抽取-分块-元数据三层职责 |

### 8.3 插件化能力

- **工具插件化**：`@AiTool(groups)` 注解 + 容器自动收集 = 新工具「写类即接入」，无需修改任何注册代码；分组机制支持按场景选择性暴露；
- **附件处理器插件化**：实现 `AttachmentProcessor#supports` 即被 Registry 自动纳管（当前注册表按类型白名单装配，规划改为遍历 `supports()` 动态注册）；
- **文件解析器插件化**：`FileParser` 按 MimeType 注册（`FileParserRegistry`），新增 PDF/Word 解析器零侵入；
- **Pipeline Stage 插件化**：Stage 即插即用（见 3.3）；
- **向量库可替换**：统一依赖 LangChain4j `EmbeddingStore` 接口，Chroma → Milvus/Qdrant/Pgvector 仅需替换 Bean 定义。

### 8.4 可复用设计模式清单

| 模式 | 应用点 |
|------|--------|
| 责任链 | Pipeline-Stage 对话处理链 |
| 工厂方法 + 享元（缓存） | 三大模型工厂 |
| 策略 | 附件处理器 / 文件解析器 / 路由策略枚举 |
| 注册表 | ToolRegistry / AttachmentProcessorRegistry / FileParserRegistry |
| 建造者 | ChatPipeline / ChatRequest / EmbeddingSearchRequest |
| 适配器 | DefaultChatStream（回调风格 → 链式风格）、OpenAI 兼容协议适配多模型供应商 |
| 模板方法 | ChatPipelineExecutor 执行骨架 |
| 观察者（回调） | SSE 流式 onNext/onComplete/onError |

---

## 9. 接口对接方式

### 9.1 对外 API 风格

- **协议**：RESTful JSON over HTTP(S)；对话流式场景采用 SSE（`text/event-stream`）；
- **契约**：SpringDoc 自动生成 OpenAPI 3 文档（`/v3/api-docs` + Swagger UI），前后端并行开发；
- **统一约定**：`Result{code, message, data}` 包裹所有响应；错误码字典化；分页参数与响应结构标准化（演进项）；
- **对接第三方系统**：规划 API Key + 配额体系，支持作为「AI 中台」向内部其他业务线输出能力。

### 9.2 对内服务间通信

- **异步解耦（RabbitMQ）**：已引入 amqp-client 并完成连接配置（手动 ack、prefetch=10、并发 5~10、失败重试 3 次指数退避 1s→10s）；规划拓扑：
  - `ai.task.exchange`（Direct）：文档解析/向量化任务、异步统计聚合；
  - `ai.event.exchange`（Fanout）：对话日志、用量统计、告警通知；
  - 死信队列承接重试耗尽消息，人工兜底；
  - 可靠性：生产者 confirm + 消息持久化 + 消费端手动 ack。
- **同步调用**：单体内模块间走接口注入（Spring Bean 协作）；微服务化演进时按 chat/file/stats/ai-gateway 域拆分，同步走 OpenFeign。

### 9.3 第三方 AI 模型接入方式

- **协议层**：所有模型调用统一走 **OpenAI 兼容协议**（`base-url + api-key + model-name` 三要素），当前接入硅基流动 SiliconFlow；
- **换供应商零成本**：切换 OpenAI / Azure OpenAI / 通义千问 DashScope / 本地 vLLM / Ollama 只改配置，不改代码（`ChatModelFactory` 全部参数来自配置中心化的 `langchain4j.open-ai.*`）；
- **多模型注册表（`ModelRegistry` + `ai.models` 配置）**：配置化声明模型元信息（model-id、类型、适配场景、单价、上下文上限、平均时延、优先级、启用开关），为智能路由提供数据底座：

```yaml
ai:
  models:
    - model-id: "Qwen/Qwen2.5-72B-Instruct"
      type: TEXT
      scenes: [ GENERAL, CUSTOMER_SERVICE, COPYWRITING ]
      price-per-token: 0.004
      avg-response-time: 2.5
      priority: 1
    - model-id: "Qwen/Qwen2-VL-72B-Instruct"
      type: MULTIMODAL
      scenes: [ GENERAL, PRODUCT_ANALYSIS, IMAGE_AUDIT ]
  routing:
    strategy: PRIORITY   # PRIORITY / COST_OPTIMIZED / PERFORMANCE / LOAD_BALANCE
  fallback:
    enabled: true
    max-retries: 2
    retry-delay: 1000
```

- **降级策略**：`ai.fallback` 配置化控制重试次数与间隔；演进方向为路由失败自动降级到下一优先级模型。

---

## 10. 性能优化

### 10.1 并发处理

- **模型实例缓存**：`ConcurrentHashMap.computeIfAbsent` 原子构建，避免高并发下重复创建 HTTP 客户端开销（参数归一化提升缓存命中率）；
- **文件处理并发闸门**：`Semaphore(4)` 限制同时解析的大文件数，保护内存与下游（MinIO/向量库）；
- **JDK 21**：预留虚拟线程（Virtual Threads）接入点，IO 密集型对话链路线程模型升级空间；
- **ReAct 步数熔断**：最多 5 步工具循环，单请求最坏耗时可控。

### 10.2 缓存策略

| 层次 | 方案 |
|------|------|
| 模型实例缓存 | 进程内 ConcurrentHashMap（见 8.2） |
| 会话记忆 | Redis 集中式存储（本身就是热数据缓存层，DB 持久化为冷备） |
| API 响应缓存（规划） | `ai.cache` 配置已就绪（TTL 3600s，COPYWRITING 场景排除——创作类结果不可复用） |
| 语义缓存（远期） | 相似问题向量比对命中直接返回，降低模型调用成本 |

### 10.3 异步任务

- 流式对话中**记忆回写放在 onComplete 回调**，不阻塞 token 推送；
- 文件入库链路的解析与向量化规划迁移至 RabbitMQ 消费者（削峰填谷 + 失败重试）；
- 长文档处理「摘要进对话 + 全文异步向量化」双轨并行（`ProcessResult.needVectorization` 标记）。

### 10.4 数据库与存储优化

- **MySQL**：HikariCP 连接池调优（min-idle 5 / max 20 / 30s 连接超时 / 30min 最大存活）；MyBatis-Plus 逻辑删除统一配置；演进项：会话/调用记录表按时间分区、高频查询字段索引（user_id+session_id、created_at）；
- **Redis**：Lettuce 连接池（max-active 20 / min-idle 5 / 3s 等待）；记忆 Key 规范化前缀管理；
- **向量库**：minScore=0.7 召回阈值前置过滤低相关结果；重排 maxResults=5 控制注入上下文规模；Chroma 持久化模式（IS_PERSISTENT=TRUE）。

### 10.5 限流与降级

- **已有**：文件处理信号量限流；磁盘空间保护（<2GB 拒绝任务）；模型超时控制（PT30S）；重试与间隔配置；
- **演进**：
  - 两级限流：Semaphore 全局并发 + Redis 原子计数用户级配额（日/月维度）；
  - 熔断降级：主模型连续失败自动切换备用模型，最终兜底预设话术；
  - 滑动窗口接口限流（Redis ZSet 实现）；
  - 输入审核前置 Stage（提示词注入检测、敏感词），审核不通过 `interrupt()` 短路管道。

### 10.6 Token 成本优化（AI 特有）

1. 多模态记忆文本化标记（图片 URL → `[图片: xx]`，记忆 Token ↓ ~80%）；
2. 窗口记忆 + 摘要压缩（长对话上下文近似常数开销）；
3. RAG 精排后仅注入 Top-5 高相关片段（而非全库召回）；
4. 工具描述分组选择性注入（演进：工具描述向量化，按用户输入检索相关工具子集注入）。

---

## 11. 工程化配置

### 11.1 环境变量与配置管理

- **多 Profile**：`application.yml`（公共）+ `application-{dev|test|prod}.yml`，启动方式支持 `--spring.profiles.active`、`SPRING_PROFILES_ACTIVE` 环境变量、IDE 配置；
- **敏感信息**：生产环境全量走环境变量注入（`${SILICONFLOW_API_KEY}`、`${DB_PASSWORD}` 等），配置模板 `.env.example` 脱敏管理；
- **配置优先级**：命令行参数 > 系统属性 > 环境变量 > profile 文件 > 公共文件；
- **强类型配置绑定**：`@ConfigurationProperties`（`MinioConfig`、`EmbeddingProperties`、`ModelRegistry`）替代散落的 `@Value`。

### 11.2 日志体系

- 分级策略：dev 全开 DEBUG（含 langchain4j/mybatis/redisson），prod 收敛至 WARN/INFO；
- 滚动策略：单文件 100MB、保留 30 天；
- 结构化 Pattern：`时间 [线程] 级别 logger - 消息`；
- Pipeline 阶段级耗时日志（`stage [x] done in yms`）形成天然性能埋点；
- 演进：MDC 注入 TraceId 全链路追踪 → Logstash 采集 → ES 存储 → Kibana 检索看板。

### 11.3 监控

- Actuator 端点差异化暴露：dev 全量、test 部分（health/info/metrics）、prod 严格管控（health/info）；
- Prometheus 指标导出已启用（`management.metrics.export.prometheus.enabled=true`），Grafana 看板规划指标：QPS、P50/P90/P99 延迟、Token 消耗、模型错误率、SSE 连接数、RAG 命中率。

### 11.4 CI/CD 与代码规范

- **构建**：Maven Wrapper（mvnw）保证团队构建环境一致；编译插件锁定 `release 21` + Preview 特性；
- **规范**：阿里巴巴 Java 开发手册；Lombok 统一样板代码消除；Git 分支策略 main/develop/feature/*，Commit 遵循 `type(scope): description`；
- **测试（演进项）**：JUnit5 + Mockito 覆盖核心 Service（ChatModelFactory、MultimodalAssistantImpl、Registry 类）目标 80%；`@SpringBootTest` 集成测试验证完整对话链路；
- **CI 流水线（规划）**：GitLab CI / Jenkins —— 编译 → 单测 → 镜像构建 → 推送制品库 → 部署（dev 自动 / prod 审批）。

---

## 12. 部署方案

### 12.1 容器化与服务编排

**本地/测试环境**：`doc/docker/docker-compose-env.yml` 一键编排 8 个基础服务：

| 服务 | 镜像 | 关键配置 |
|------|------|---------|
| MySQL | mysql:8.0 | utf8mb4、root 远程访问、healthcheck（mysqladmin ping） |
| Redis | redis:7-alpine | 密码 + AOF 持久化 |
| RabbitMQ | rabbitmq:3.12-management-alpine | 管理界面 15672 |
| Elasticsearch | elasticsearch:8.11.0 | 单节点、安全关闭、JVM 512m |
| Kibana | kibana:8.11.0 | 依赖 ES 健康后启动（depends_on: service_healthy） |
| Logstash | logstash:8.11.0 | pipeline 配置挂载、Beats 5044 入口 |
| MinIO | minio/minio:latest | API 9000 / Console 9001 |
| Chroma | chromadb/chroma:latest | IS_PERSISTENT=TRUE 持久化 |

统一特性：数据卷持久化（`*_data` volumes）、独立 bridge 网络（vllmpt-network）、全部配置 healthcheck、`restart: unless-stopped` 自愈。

**应用容器化（规划）**：多阶段 Dockerfile（Maven 构建 → JRE 21 运行时镜像），启动命令 `java -jar vllmpt.jar --spring.profiles.active=prod`，或 Systemd 托管（LimitNOFILE=65536、开机自启、journal 日志）。

### 12.2 生产架构（推荐）

```
        Nginx / SLB（HTTPS 终结、负载均衡）
                 │
    ┌────────────┴────────────┐
    │   App Node 1  App Node 2 │   ← Spring Boot 无状态多实例（记忆在 Redis，可水平扩展）
    └────────────┬────────────┘
                 │
    Redis Sentinel/Cluster │ RabbitMQ 集群 │ MinIO 分布式 │ Chroma/Milvus │ ES 3 节点
```

### 12.3 监控告警

- **指标**：Prometheus 抓取 Actuator 端点 → Grafana 大盘（QPS / P99 / 错误率 / Token 成本 / 队列积压）；
- **日志**：应用日志 → Logstash → Elasticsearch → Kibana；
- **告警**（阈值示例）：P99 > 5s、5xx > 10/min、RabbitMQ 积压 > 1000、Redis 内存 > 80%、模型调用失败率 > 5% → 邮件 / 企业微信 / 钉钉机器人。

### 12.4 灰度发布

- 按流量比例灰度（Nginx weight / 网关路由规则）：新版本 1% → 10% → 50% → 100%；
- 金丝雀测试集：维护典型对话与 RAG 检索用例，发布前自动化回归（Prompt/模型变更同样纳入门禁）；
- 异常自动回滚：灰度期间错误率/延迟超阈值自动切回旧版本。

---

## 13. 个人职责与量化成果

> 以下为「后端核心开发（后端负责人）」视角的职责与成果描述，量化指标为压测参考值/目标值（标注 ★ 的可根据目标公司 JD 重点调整口径）。

### 13.1 承担的核心工作

1. **AI 能力底座从 0 到 1 建设**：基于 LangChain4j 自研 AiService 编排层，不依赖框架黑盒注解，手动实现消息组装、模型调用、工具循环、记忆回写全链路，完全掌控推理过程可观测性与可干预性；
2. **Pipeline-Stage 对话编排框架设计与实现**：将多模态对话拆解为「信息加载 → 记忆装配 → 附件解析 → RAG 检索 → 模型推理 → 记忆回写」六阶段责任链，支持中断短路、条件跳过、阶段级异常隔离与耗时审计，新增处理环节的开发成本从「改主干代码」降为「新增一个类」；
3. **RAG 全管道落地**：实现「MinIO 预签名直传 → ETag 校验 → 桶迁移归档 → 流式智能分块（4 层切分策略）→ bge-m3 向量化 → Chroma 双 Collection → 语义召回 → bge-reranker 重排 → Prompt 注入」端到端管道，解决超大文本文件入库的 OOM 风险（纯流式解析 + 强制截断兜底）；
4. **ReAct Agent 工具体系**：设计 `@AiTool` 注解式工具插件机制 + `ToolRegistry`（注册/分组/安全执行），实现 5 步熔断的 ReAct 推理循环，工具异常不中断对话而是反馈模型自我修正；
5. **记忆与成本优化**：设计「模型侧多模态 / 记忆侧纯文本」双通道内容建模与 Redis 持久化记忆（Redisson RList），图片记忆 Token 占用下降约 80%★；
6. **SSE 流式输出**：封装 `ChatStream` 链式回调编程模型适配 Reactor `Flux`，记忆回写异步化不阻塞推流；
7. **工程化建设**：统一响应/全局异常/枚举体系、多环境配置、Docker Compose 基础设施编排（8 服务）、Actuator+Prometheus 监控接入、项目文档与技术改进路线（P0~P3 分级）制定。

### 13.2 量化成果（★ 为压测/设计目标参考值，简历可按需调整）

| 维度 | 指标 |
|------|------|
| 首字响应（SSE） | 首 token 延迟 < 500ms★，相比同步等待完整响应的交互时长下降 90%+★ |
| 模型调用开销 | 模型实例缓存命中率 > 95%★（参数归一化后同配置请求零构建）；请求级模型对象创建开销降至 O(1) |
| Token 成本 | 多模态记忆文本化使单轮记忆 Token ↓ ~80%★；窗口+摘要机制使 20 轮长对话上下文 Token 接近常数 |
| RAG 质量 | 召回重排（minScore 0.7 + Top-5）后上下文注入量减少 50%★，回答幻觉率显著下降（严格 Prompt 约束 + 来源可溯） |
| 文件处理稳定性 | 流式解析 + 10 万字符强制截断 + Semaphore(4) 限流，百 MB 级文本入库内存占用稳定 < 100MB★，无 OOM |
| 可维护性 | 对话链路新增处理环节的开发工作量从人日级降至小时级（新增 Stage 即插拔） |
| 环境效率 | Docker Compose 一键拉起 8 组件依赖环境，新人环境搭建从半天缩至 10 分钟 |

### 13.3 简历一句话提炼（多版本）

- **通用版**：从 0 到 1 搭建多模态 AI Agent 后端平台（Spring Boot 4 + LangChain4j），自研 Pipeline 编排框架、ReAct 工具调用体系与 RAG 检索增强管道，落地多轮对话 Redis 记忆、SSE 流式输出与 MinIO 预签名直传，支撑图文混合智能问答与知识库场景。
- **高并发方向**：设计模型工厂缓存（参数归一化 + ConcurrentHashMap）、Semaphore 并发闸门、信号量+磁盘双重保护的大文件流式处理方案，百 MB 文件入库零 OOM；规划 Redis 两级限流与熔断降级体系。
- **AI 应用方向**：实现「文档分块（4 层策略）→ 向量化（bge-m3）→ Chroma 存储 → 语义召回 → 重排序（bge-reranker-v2-m3）→ Prompt 注入」完整 RAG 管道；基于 Function Calling 实现 5 步熔断的 ReAct Agent，工具注解式插件化接入。

---

## 附录：技术演进路线

| 优先级 | 方向 | 内容 |
|--------|------|------|
| P0 | 安全闭环 | JWT 认证过滤器接入、关闭全路径放行、接口级 `@PreAuthorize`、Refresh Token（Redis） |
| P0 | 骨架补全 | 模型智能路由 ModelRouter（PRIORITY/COST/PERFORMANCE/ROUND_ROBIN 四策略 + 失败降级）、ModelRegistry 配置绑定 |
| P1 | Agent 深化 | 状态机化 ReAct、工具描述向量化选择性注入、会话级/全局级分层记忆 |
| P1 | 对话增强 | 对话树（DAG）分支管理（parent_id + children_ids，支持编辑历史/重新生成/上下文回滚）、会话节点拆分 |
| P1 | 记忆治理 | 用户退出会话后 Redis 记忆卸载持久化 MySQL、摘要自动刷新 |
| P2 | 检索增强 | 语义切分（基于相邻句向量相似度）、Chroma 只返回 ID+score 按需加载文本、图片向量化（CLIP 以图搜图） |
| P2 | 异步化 | RabbitMQ 消费者落地（文档向量化/日志/统计聚合）、死信队列、任务状态机 |
| P2 | 可观测 | TraceId（MDC）全链路、Micrometer Token/成本指标、Grafana 大盘 |
| P3 | 平台化 | Workflow 工作流引擎（低代码编排 AI 流程）、多租户 SaaS 化、开放平台 API、语义缓存 |
| P3 | 业务扩展 | 飞书/钉钉审批 API 对接、异常审批智能提醒 |

---

> **文档使用建议**：
> 1. 投递前根据目标 JD 调整 13.2/13.3 的量化口径与一句话提炼版本；
> 2. 面试深挖点建议重点准备：Pipeline 设计权衡（vs 硬编码流程）、RAG 分块参数依据（chunk/overlap 与 Embedding 模型 token 上限的关系）、ReAct 循环的异常与死循环防护、多模态记忆降 Token 设计、MinIO 预签名直传的安全校验链路；
> 3. 本文档与 `doc/项目概览.md`（宏观架构）、`doc/project_improvement_plan.md`（改进路线）、`doc/配置说明.md`（环境手册）互为补充。
