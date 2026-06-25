# vllmpt 项目深化改进方案

> 目标：将当前"Demo 级"项目提升为具有真正竞争力的生产级 AI 中台系统

---

## 一、当前问题诊断

| 维度 | 当前状态 | 竞争性评估 |
|------|---------|-----------|
| **核心功能** | 仅实现了多模态对话的基础链路 | ❌ 太薄，无差异化 |
| **RAG 检索增强** | 代码已注释，完全未实现 | ❌ 缺少关键能力 |
| **流式输出** | SSE 实现有并发安全问题 | ⚠️ 不健壮 |
| **向量化** | EmbeddingModel 空实现（return null） | ❌ 致命缺陷 |
| **PDF/文档解析** | PdfBoxDocumentExtractor 空实现 | ❌ 不可用 |
| **视频处理** | 硬编码返回 "1231qadsad" | ❌ 假实现 |
| **模型路由** | ModelRegistry 空类，配置写了但没用 | ❌ 摆设 |
| **安全认证** | 全路径 permitAll，JWT 未接入 | ❌ 无防护 |
| **异常处理** | throw new Error()（应抛异常而非 Error） | ⚠️ 不规范 |
| **测试** | 零单元测试 | ❌ 不可交付 |
| **代码质量** | 字段命名不规范、static @Value 注入 bug | ⚠️ 有硬伤 |

---

## 二、改进路线图（按优先级排序）

### 🔴 P0 — 致命缺陷，必须立即修复

#### 2.1 修复 EmbeddingModelFactory 的 static @Value Bug
**文件**: `EmbeddingModelFactory.java`
- `EMBEDDING_BASE_URL` 声明为 `static` 但用 `@Value` 注入 → 永远是 null
- 改为实例字段，或使用 `@Value` 在 setter 上注入
- 同时移除方法内 `System.getenv()` 的硬编码覆盖逻辑，统一走配置

#### 2.2 实现 EmbeddingModel（向量化能力）
**文件**: `MultimodalEmbeddingModel.java`
- 当前 `embedAll()` 直接 `return null`
- 应委托给 `EmbeddingModelFactory` 创建的 `OpenAiEmbeddingModel` 实例
- 这是 RAG 和语义搜索的底座，没它整个检索链路全断

#### 2.3 实现 PDF 文档解析
**文件**: `PdfBoxDocumentExtractor.java`
- 引入 Apache PDFBox 依赖，实现真实的 PDF 文本提取
- 支持从 URL 下载 PDF 后解析（已有注释设计意图，补代码即可）
- 可选：增加 Apache POI 支持 doc/docx 格式

#### 2.4 修复 AttachmentProcessorRegistry 逻辑错误
**文件**: `AttachmentProcessorRegistry.java`
- `supportSet` 只注册了 `["image", "txt"]`，pdf/video/md 无法注册
- 应遍历所有处理器调用 `p.supports(type)`，而不是硬编码 supportSet
- `throw new Error()` 应改为 `throw new IllegalArgumentException()`

---

### 🟠 P1 — 核心能力补全，拉开差距

#### 2.5 实现 RAG 检索增强生成
**涉及文件**: `DocumentProcessor.java`、新增 `RagService.java`、新增 `VectorStoreConfig.java`
- 取消注释的 `VectorStore` 和 `TextSplitter` 代码
- 实现完整 RAG 管道：
  ```
  文档上传 → PDFBox 提取文本 → TextSplitter 分块(500字/块+100字重叠)
  → EmbeddingModel 向量化 → Chroma 向量库存储
  → 对话时语义检索 Top-K → 注入 Prompt → LLM 生成回答
  ```
- 这是当前 AI 应用最核心的差异化能力，没有 RAG 的 AI 平台毫无竞争力

#### 2.6 实现视频分析能力
**文件**: `VideoProcessor.java`
- 引入 `langchain4j` 的多模态能力：抽帧 → 多模态模型描述 → 文本注入
- 或对接第三方视频理解 API（如阿里云视频 AI）
- 去掉硬编码 `"1231qadsad"`

#### 2.7 实现模型智能路由
**文件**: `ModelRegistry.java`、新增 `ModelRouter.java`
- 补全 `ModelRegistry` 的配置绑定（`List<ModelInfo>`）
- 实现路由策略：
  - **PRIORITY**: 按优先级选模型，失败自动降级到下一优先级
  - **COST_OPTIMIZED**: 按价格升序选模型
  - **PERFORMANCE**: 按 avgResponseTime 升序选
  - **LOAD_BALANCE**: Round-Robin 轮询
- 集成配置文件中的降级配置（max-retries / retry-delay）
- 添加场景路由：根据 BizScene 自动匹配适合的模型

#### 2.8 完善流式输出（SSE）
**文件**: `MultimodalController.java`、`DefaultChatStream.java`
- `Flux.create` 不是线程安全的，改用 `Flux.push` 或 `Sinks.Many`
- 添加背压策略 `onBackpressureBuffer`
- 流式输出完成后异步更新记忆（避免阻塞 SSE 流）
- 增加心跳机制，防止连接超时断开

---

### 🟡 P2 — 工程化提升，达到生产级

#### 2.9 接入 JWT 认证体系
**文件**: `WebSecurityConfig.java`、新增 `JwtTokenProvider.java`、`JwtAuthenticationFilter.java`
- 实现 JWT 生成（登录时签发）与验证（请求时校验）
- 添加 `@PreAuthorize` 注解做接口级权限控制
- 支持 Token 刷新机制（RefreshToken 存 Redis）
- 关闭 `/**` 的 permitAll，改为按角色授权

#### 2.10 添加单元测试与集成测试
- 核心 Service 层至少 80% 覆盖率
- 使用 `spring-boot-starter-test` + JUnit 5 + Mockito
- 为 `ChatModelFactory`、`MultimodalAssistantImpl`、`AttachmentProcessorRegistry` 写单测
- 添加 `@SpringBootTest` 集成测试验证完整链路

#### 2.11 统一代码规范
- `ModelInfo.java` 字段改为 camelCase（`modelName` 而非 `ModelName`）
- 全局替换 `throw new Error()` 为正确的异常类型
- `DocumentExtractor` 接口去掉 `@Component` 注解
- `EmbeddingModelFactory.EMBEDDING_BASE_URL` 改为实例字段

#### 2.12 引入可观测性
- 对话链路加 TraceId（MDC + SLF4J），便于排查问题
- 为 LLM 调用添加耗时 Metrics（Micrometer Timer）
- 配置 Grafana 仪表盘监控 QPS、P99 延迟、Token 消耗、错误率

---

### 🟢 P3 — 差异化竞争力，形成壁垒

#### 2.13 实现 Function Calling / ReAct Agent 模式
- 模型可以自主决定调用工具（如查订单、查库存、退款）
- 使用 LangChain4j 的 `AiServices` + `@Tool` 注解
- 实现电商场景常用 Tool：
  - `OrderQueryTool`: 根据订单号查订单状态
  - `ProductSearchTool`: 根据关键词搜索商品
  - `InventoryCheckTool`: 查询库存
  - `RefundTool`: 发起退款流程

#### 2.14 实现对话摘要与会话压缩
**文件**: 新增 `ConversationSummarizer.java`
- 当对话轮次超过阈值时，自动调用小模型对历史对话做摘要
- 摘要替代原始消息存入记忆，大幅降低 Token 消耗
- 支持摘要回滚：用户可回到某轮对话前的状态

#### 2.15 实现图片向量化（CLIP 模型）
**文件**: 新增 `ImageEmbeddingService.java`
- 对商品图片生成向量，支持「以图搜图」
- 可用于图片审核（相似度匹配违规图片库）
- 对接 LangChain4j 的多模态 Embedding 或独立的 CLIP 服务

#### 2.16 实现上下文分支管理
- 支持对话树（DAG）结构：用户可以编辑历史消息、重新生成回答
- 每条消息记录 `parentId` + `childrenIds`
- 支持分支切换与回滚，类似 ChatGPT 的对话分支功能
- 参考 `doc/todo.txt` 中通义千问的请求结构设计

#### 2.17 实现 Workflow 工作流引擎
- 支持可视化编排 AI 处理流程（低代码）
- 典型场景：商品上架 → AI 生成标题/描述 → AI 图片审核 → 自动打标签 → 发布
- 可基于 Flowable 或自研轻量级 DAG 引擎

#### 2.18 多模型供应商支持
- 当前只接了硅基流动（SiliconFlow），应扩展支持：
  - OpenAI / Azure OpenAI
  - 阿里云通义千问（DashScope）
  - 百度文心一言
  - 本地部署的 vLLM / Ollama
- 使用适配器模式统一不同供应商的接口差异

---

## 三、改进后的技术架构图

```
┌─────────────────────────────────────────────────────────────┐
│                        接入层                                │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌─────────────┐ │
│  │ Web 前端  │  │ 小程序   │  │ OpenAPI  │  │ 第三方系统  │ │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └──────┬──────┘ │
│       └──────────────┴─────────────┴───────────────┘        │
│                          │ JWT Auth                         │
├──────────────────────────┴──────────────────────────────────┤
│                       API 网关层                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  MultimodalController  │  FileUploadController  │ ... │   │
│  └────────────────────────┴─────────────────────────────┘   │
├─────────────────────────────────────────────────────────────┤
│                       AI 核心层                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ ModelRouter  │  │ Agent/ReAct  │  │ Workflow Engine  │  │
│  │ (智能路由)   │  │ (FunctionCall)│  │ (DAG 编排)      │  │
│  └──────┬───────┘  └──────┬───────┘  └────────┬─────────┘  │
│         │                 │                    │            │
│  ┌──────┴─────────────────┴────────────────────┴─────────┐  │
│  │              MultimodalAssistant (多模态助手)          │  │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────────────┐    │  │
│  │  │ 附件解析  │  │ RAG 检索 │  │ 对话记忆管理     │    │  │
│  │  │ Image    │  │ Vector   │  │ 摘要/分支/回滚   │    │  │
│  │  │ Document │  │ Search   │  │ Redis 持久化     │    │  │
│  │  │ Video    │  │ Top-K    │  │ Token 优化       │    │  │
│  │  └──────────┘  └──────────┘  └──────────────────┘    │  │
│  └───────────────────────────────────────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│                      AI 基础设施                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │ Chat     │  │Embedding │  │ CLIP     │  │ Multi-   │   │
│  │ Model    │  │ Model    │  │ Image    │  │ Vendor   │   │
│  │ Factory  │  │ Factory  │  │ Embedding│  │ Adapter  │   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘   │
├─────────────────────────────────────────────────────────────┤
│                      数据层                                  │
│  ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐  ┌──────────────┐ │
│  │MySQL │  │Redis │  │MinIO │  │Chroma│  │Elasticsearch │ │
│  │业务库│  │缓存  │  │文件  │  │向量库│  │搜索引擎      │ │
│  │      │  │记忆  │  │存储  │  │      │  │              │ │
│  └──────┘  └──────┘  └──────┘  └──────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

---

## 四、改进前后对比

| 维度 | 改进前 | 改进后 |
|------|--------|--------|
| **AI 能力** | 单一多模态对话 | 对话 + RAG + Agent + Workflow |
| **文档处理** | 空实现 | 真实 PDF/DOCX 解析 + 向量化入库 |
| **视频处理** | 硬编码假数据 | 真实抽帧分析 + 多模态描述 |
| **模型管理** | 空路由表 | 智能路由 + 降级 + 多供应商 |
| **流式输出** | 有并发 bug | 健壮的 SSE + 心跳 + 背压 |
| **安全性** | 全路径公开 | JWT 认证 + 接口级权限 |
| **可观测性** | 无 | TraceId + Metrics + Grafana |
| **测试** | 0% | 80%+ 核心覆盖率 |
| **差异化** | 无 | ReAct Agent / 对话分支 / 图片向量化 |

---

## 五、建议实施顺序

```
第 1 周: P0 致命缺陷修复（Embedding/PdfBox/Registry Bug）
第 2 周: P1 核心能力补全（RAG / 视频分析 / 模型路由）
第 3 周: P1 流式输出加固 + P2 工程化（JWT / 测试 / 代码规范）
第 4 周: P2 可观测性 + P3 差异化特性启动（Function Calling）
第 5-6 周: P3 深度特性（Agent / 对话分支 / Workflow / 多供应商）
```

---

## 六、简历竞争力提升点

完成以上改进后，简历可新增以下关键词和亮点：

**新增技术关键词**：
- RAG 检索增强生成、语义搜索、向量数据库（Chroma）
- ReAct Agent、Function Calling、Tool Use
- 多模型供应商适配（SiliconFlow / OpenAI / DashScope）
- JWT 认证鉴权、接口级权限控制
- SSE 流式输出、背压控制
- 对话摘要压缩、上下文分支管理
- Workflow 工作流编排
- CLIP 图片向量化、以图搜图
- Prometheus + Grafana 全链路监控

**简历可新增的描述**：
- 设计并实现完整 RAG 管道：文档解析 → 智能分块 → 向量化 → 语义检索 → 增强生成
- 基于 LangChain4j AiServices 实现 ReAct Agent 模式，支持 Function Calling 自动调用订单查询/库存检查等业务工具
- 实现多模型智能路由与供应商适配器，支持优先级/成本/性能/负载均衡四种策略，失败自动降级
- 设计对话树（DAG）分支管理，支持历史消息编辑、重新生成与上下文回滚
