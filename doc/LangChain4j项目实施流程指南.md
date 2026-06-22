# LangChain4j 电商 AI 平台开发流程指南

## 📋 整体架构概览

```
用户请求 → 输入识别层 → 场景识别层 → 模型路由层 → 模型调用层 → 响应处理
```

---

## 🎯 第一阶段：基础环境验证（1天）

### 目标
确保开发环境和依赖配置正确，能够成功调用 AI API。

### 实施步骤

#### 1.1 检查依赖配置
- **查看文件**：`pom.xml`
- **确认内容**：LangChain4j 相关依赖已添加（版本 0.35.0）
  - `langchain4j-spring-boot-starter`
  - `langchain4j-open-ai-spring-boot-starter`
  - `langchain4j-store-redis`

#### 1.2 验证 API 配置
- **查看文件**：`src/main/resources/application.yml`
- **确认内容**：
  - 硅基流动 API 地址配置
  - API Key 从环境变量读取
  - 默认模型名称配置
  - 超时时间和温度参数设置

#### 1.3 环境变量设置
- **操作**：设置 `SILICONFLOW_API_KEY` 环境变量
- **参考文档**：[LangChain4j快速上手教程.md](./LangChain4j快速上手教程.md) 第 1.2 节

#### 1.4 启动测试
- **操作**：启动应用，确认无报错
- **验证**：检查日志中 LangChain4j 相关组件是否正常加载

---

## 💬 第二阶段：基础对话功能（1-2天）

### 目标
实现最简单的文本对话功能，理解 LangChain4j 的核心抽象。

### 实施步骤

#### 2.1 创建 AI 服务接口
- **包路径**：`org.albedo.vllmpt.ai.service`
- **文件**：`SimpleChatAssistant.java`
- **核心概念**：
  - 使用 `@AiService` 注解标记接口
  - 使用 `@SystemMessage` 设定 AI 角色
  - 使用 `@UserMessage` 标记用户输入参数
- **参考文档**：[LangChain4j快速上手教程.md](./LangChain4j快速上手教程.md) 第 5.1 节

#### 2.2 创建 Controller
- **包路径**：`org.albedo.vllmpt.chat.controller`
- **文件**：`SimpleChatController.java`
- **功能**：接收 HTTP 请求，调用 AI 服务，返回响应
- **参考文档**：[LangChain4j快速上手教程.md](./LangChain4j快速上手教程.md) 第 5.2 节

#### 2.3 测试验证
- **工具**：Postman 或 curl
- **接口**：`POST /api/chat/simple`
- **预期**：能够收到 AI 的文本回复
- **参考文档**：[LangChain4j快速上手教程.md](./LangChain4j快速上手教程.md) 第 5.3 节

### 学习重点
- 理解 `@AiService` 的工作原理（Spring 自动代理生成实现类）
- 掌握 SystemMessage 和 UserMessage 的作用
- 熟悉基本的 API 调用流程

---

## 🖼️ 第三阶段：多模态功能开发（2-3天）

### 目标
实现文本+图片的多模态对话能力。

### 实施步骤

#### 3.1 理解多模态消息结构
- **核心类**：
  - `UserMessage`：用户消息容器
  - `TextContent`：文本内容
  - `ImageContent`：图片内容
- **参考文档**：[LangChain4j开发指南.md](./LangChain4j开发指南.md) 第 3.2 节

#### 3.2 创建多模态服务类
- **包路径**：`org.albedo.vllmpt.ai.service`
- **文件**：`MultimodalAssistant.java`
- **功能设计**：
  - 单图对话方法：接收文本 + 单个图片 URL
  - 多图对话方法：接收文本 + 多个图片 URL
  - 构建多模态消息的逻辑
  - 调用 ChatLanguageModel 获取响应
- **参考文档**：[LangChain4j开发指南.md](./LangChain4j开发指南.md) 第 5.1 节

#### 3.3 创建多模态 Controller
- **包路径**：`org.albedo.vllmpt.chat.controller`
- **文件**：`MultimodalController.java`
- **接口设计**：
  - `POST /api/chat/multimodal/single-image`：单图对话
  - `POST /api/chat/multimodal/multiple-images`：多图对话
- **参考文档**：[LangChain4j开发指南.md](./LangChain4j开发指南.md) 第 5.2 节

#### 3.4 图片预处理（可选进阶）
- **包路径**：`org.albedo.vllmpt.file.service`
- **功能**：
  - 图片格式验证
  - 图片压缩处理
  - 上传到 MinIO 对象存储
  - 返回可访问的图片 URL
- **参考文档**：[LangChain4j开发指南.md](./LangChain4j开发指南.md) 第 5.2 节（图片预处理部分）

#### 3.5 测试验证
- **测试场景**：
  - 单图 + 文本对话
  - 多图对比分析
  - 纯文本对话（兼容性测试）
- **参考文档**：[LangChain4j开发指南.md](./LangChain4j开发指南.md) 第 5.3 节

### 学习重点
- 掌握多模态消息的构建方式
- 理解不同模型对多模态的支持差异
- 学会处理图片 URL 的预加工流程

---

## 💭 第四阶段：会话记忆管理（1-2天）

### 目标
实现多轮对话的上下文记忆功能。

### 实施步骤

#### 4.1 配置 Redis 记忆存储
- **包路径**：`org.albedo.vllmpt.ai.config`
- **文件**：`ChatMemoryConfig.java`
- **功能**：
  - 创建 `RedisChatMemoryStore` Bean
  - 注入 RedissonClient
  - 配置记忆存储参数
- **参考文档**：[LangChain4j开发指南.md](./LangChain4j开发指南.md) 第 7.1 节

#### 4.2 创建带记忆的 AI 服务
- **包路径**：`org.albedo.vllmpt.ai.service`
- **文件**：`MemoryChatAssistant.java`
- **核心概念**：
  - 使用 `@MemoryId` 标记会话 ID 参数
  - LangChain4j 自动管理该会话的历史消息
  - 提供清除记忆的方法
- **参考文档**：[LangChain4j开发指南.md](./LangChain4j开发指南.md) 第 7.2 节

#### 4.3 创建记忆管理 Controller
- **包路径**：`org.albedo.vllmpt.chat.controller`
- **文件**：`MemoryChatController.java`
- **接口设计**：
  - `POST /api/chat/memory`：带记忆的对话
  - `DELETE /api/chat/memory/{chatId}`：清除指定会话记忆
- **参考文档**：[LangChain4j开发指南.md](./LangChain4j开发指南.md) 第 7.3 节

#### 4.4 测试多轮对话
- **测试流程**：
  1. 第一轮：发送"我想买红色连衣裙"
  2. 第二轮：发送"有什么推荐"（AI 应记得上一轮的"红色连衣裙"）
  3. 清除记忆后重新对话
- **验证点**：AI 是否能正确引用历史对话内容
- **参考文档**：[LangChain4j开发指南.md](./LangChain4j开发指南.md) 第 7.4 节

### 学习重点
- 理解 `@MemoryId` 的工作机制
- 掌握 Redis 存储会话历史的原理
- 学会管理会话生命周期（创建、查询、清除）

---

## 🔧 第五阶段：工具调用（Function Calling）（2-3天）

### 目标
让 AI 能够调用外部工具（如商品搜索、价格查询等）。

### 实施步骤

#### 5.1 理解工具调用机制
- **核心概念**：
  - `@Tool` 注解标记可被 AI 调用的方法
  - 方法描述会传递给 AI，帮助它理解何时调用
  - AI 自主决定是否需要调用工具
- **参考文档**：[LangChain4j快速上手教程.md](./LangChain4j快速上手教程.md) 第 8.1 节

#### 5.2 创建工具类
- **包路径**：`org.albedo.vllmpt.ai.tool`
- **文件示例**：
  - `ProductSearchTool.java`：商品搜索工具
  - `PriceQueryTool.java`：价格查询工具
  - `InventoryCheckTool.java`：库存检查工具
- **设计要点**：
  - 每个方法添加 `@Tool` 注解
  - 编写清晰的方法描述（AI 通过描述判断何时调用）
  - 实现实际的业务逻辑（目前可用模拟数据）
- **参考文档**：[LangChain4j开发指南.md](./LangChain4j开发指南.md) 第 9.1 节

#### 5.3 创建带工具的 AI 服务
- **包路径**：`org.albedo.vllmpt.ai.service`
- **文件**：`ToolChatAssistant.java`
- **特点**：
  - 使用 `@AiService` 标记
  - 不需要特殊注解，框架会自动发现关联的工具
- **参考文档**：[LangChain4j开发指南.md](./LangChain4j开发指南.md) 第 9.2 节

#### 5.4 配置工具 Bean
- **包路径**：`org.albedo.vllmpt.ai.config`
- **文件**：`ToolConfig.java`
- **功能**：
  - 使用 `AiServices.builder()` 手动构建 AI 服务
  - 注册工具实例
  - 关联 ChatLanguageModel
- **参考文档**：[LangChain4j开发指南.md](./LangChain4j开发指南.md) 第 9.3 节

#### 5.5 创建工具调用 Controller
- **包路径**：`org.albedo.vllmpt.chat.controller`
- **文件**：`ToolChatController.java`
- **接口**：`POST /api/chat/tool`
- **参考文档**：[LangChain4j开发指南.md](./LangChain4j开发指南.md) 第 9.4 节

#### 5.6 测试工具调用
- **测试场景**：
  - "帮我搜索一下连衣裙"（应调用搜索工具）
  - "这件衣服多少钱"（应调用价格查询工具）
  - 普通对话（不应调用工具）
- **验证点**：观察日志中工具是否被正确调用
- **参考文档**：[LangChain4j开发指南.md](./LangChain4j开发指南.md) 第 9.4 节

### 学习重点
- 理解 Function Calling 的工作原理
- 学会编写清晰的工具描述（影响 AI 的判断准确性）
- 掌握工具与 AI 服务的绑定方式

---

## 🚀 第六阶段：智能路由系统（3-5天）

### 目标
根据输入类型和业务场景，自动选择最优的 AI 模型。

### 实施步骤

#### 6.1 定义枚举类
- **包路径**：`org.albedo.vllmpt.common.enums`
- **文件**：
  - `InputType.java`：输入类型枚举（TEXT_ONLY, IMAGE_ONLY, MULTIMODAL, FILE）
  - `BizScene.java`：业务场景枚举（CUSTOMER_SERVICE, COPYWRITING, IMAGE_AUDIT 等）
  - `ModelType.java`：模型类型枚举（TEXT, IMAGE, MULTIMODAL）
  - `RoutingStrategyType.java`：路由策略类型枚举
- **参考文档**：[AI模型路由与多模态选择方案.md](./AI模型路由与多模态选择方案.md) 第 1.1 节、第 2.1 节

#### 6.2 创建模型信息类
- **包路径**：`org.albedo.vllmpt.ai.model`
- **文件**：`ModelInfo.java`
- **字段设计**：
  - 模型 ID、名称、类型
  - 适用场景列表
  - 价格、最大 Token 数、平均响应时间
  - 优先级、是否启用
- **参考文档**：[AI模型路由与多模态选择方案.md](./AI模型路由与多模态选择方案.md) 第 3.1 节

#### 6.3 实现输入类型识别器
- **包路径**：`org.albedo.vllmpt.ai.recognizer`
- **文件**：`InputTypeRecognizer.java`
- **逻辑**：
  - 检查请求中是否包含文本
  - 检查请求中是否包含图片 URL
  - 检查请求中是否包含文件
  - 根据组合判断输入类型
- **参考文档**：[AI模型路由与多模态选择方案.md](./AI模型路由与多模态选择方案.md) 第 1.2 节

#### 6.4 实现业务场景识别器
- **包路径**：`org.albedo.vllmpt.ai.recognizer`
- **文件**：`SceneRecognizer.java`
- **策略**：基于关键词规则匹配
- **逻辑**：
  - 预定义关键词与场景的映射关系
  - 遍历用户内容，匹配关键词
  - 返回对应的业务场景
  - 未匹配则返回通用场景
- **参考文档**：[AI模型路由与多模态选择方案.md](./AI模型路由与多模态选择方案.md) 第 2.2 节（方案 A）

#### 6.5 创建模型注册表
- **包路径**：`org.albedo.vllmpt.ai.registry`
- **文件**：`ModelRegistry.java`
- **功能**：
  - 从配置文件加载模型列表
  - 提供模型筛选方法（按输入类型、业务场景）
  - 按优先级排序候选模型
- **配置方式**：在 `application.yml` 中配置模型清单
- **参考文档**：[AI模型路由与多模态选择方案.md](./AI模型路由与多模态选择方案.md) 第 3.1 节、第 3.2 节

#### 6.6 设计路由策略接口
- **包路径**：`org.albedo.vllmpt.ai.strategy`
- **文件**：`RoutingStrategy.java`（接口）
- **方法**：`ModelInfo route(InputType, BizScene, RoutingContext)`

#### 6.7 实现多种路由策略
- **包路径**：`org.albedo.vllmpt.ai.strategy`
- **文件**：
  - `PriorityRoutingStrategy.java`：优先级路由（默认）
  - `CostOptimizedRoutingStrategy.java`：成本优先路由
  - `PerformanceRoutingStrategy.java`：性能优先路由
  - `LoadBalanceRoutingStrategy.java`：负载均衡路由
- **策略说明**：
  - 优先级：选择 priority 值最小的模型
  - 成本：选择价格最低的模型
  - 性能：选择响应时间最短的模型
  - 负载均衡：轮询选择模型
- **参考文档**：[AI模型路由与多模态选择方案.md](./AI模型路由与多模态选择方案.md) 第 3.3 节

#### 6.8 创建路由上下文类
- **包路径**：`org.albedo.vllmpt.ai.model`
- **文件**：`RoutingContext.java`
- **字段**：用户 ID、会话 ID、内容长度、是否 VIP、预算限制等

#### 6.9 实现模型路由器
- **包路径**：`org.albedo.vllmpt.ai.router`
- **文件**：`SmartRouter.java` 或 `DefaultModelRouter.java`
- **路由流程**：
  1. 调用 InputTypeRecognizer 识别输入类型
  2. 调用 SceneRecognizer 识别业务场景
  3. 构建 RoutingContext
  4. 调用 ModelRegistry 筛选候选模型
  5. 调用 RoutingStrategy 选择最优模型
  6. 返回选中的 ModelInfo
- **参考文档**：[AI模型路由与多模态选择方案.md](./AI模型路由与多模态选择方案.md) 第 4.2 节

#### 6.10 配置策略切换
- **配置文件**：`application.yml`
- **配置项**：`ai.routing.strategy`（PRIORITY / COST_OPTIMIZED / PERFORMANCE / LOAD_BALANCE）
- **配置类**：`RoutingStrategyConfig.java`（根据配置注入不同的策略 Bean）
- **参考文档**：[AI模型路由与多模态选择方案.md](./AI模型路由与多模态选择方案.md) 第 4.3 节

#### 6.11 整合到聊天服务
- **包路径**：`org.albedo.vllmpt.chat.service`
- **文件**：`SmartChatService.java`
- **流程**：
  1. 接收用户请求
  2. 调用 SmartRouter 选择模型
  3. 根据选中的模型创建对应的 ChatLanguageModel
  4. 调用模型获取响应
  5. 返回结果
- **参考文档**：[LangChain4j开发指南.md](./LangChain4j开发指南.md) 第 6.3 节

#### 6.12 测试路由功能
- **测试场景**：
  - 客服场景："这个商品有优惠吗？"（应选择客服优化模型）
  - 文案场景："帮我写一个商品描述"（应选择创意性强的模型）
  - 图片审核：上传图片（应选择视觉模型）
  - 通用问答："今天天气怎么样？"（应选择性价比高的模型）
- **验证点**：观察日志中选择的模型是否符合预期

### 学习重点
- 理解三层识别机制（输入类型 → 业务场景 → 模型选择）
- 掌握策略模式的应用（不同路由策略可切换）
- 学会配置化管理模型清单
- 理解模型降级和容错机制

---

## 🌊 第七阶段：流式响应（1-2天）

### 目标
实现 SSE（Server-Sent Events）流式输出，提升用户体验。

### 实施步骤

#### 7.1 创建流式 AI 服务
- **包路径**：`org.albedo.vllmpt.ai.service`
- **文件**：`StreamChatAssistant.java`
- **特点**：
  - 返回类型为 `Flux<String>`（响应式流）
  - 使用 `@AiService` 标记
- **参考文档**：[LangChain4j开发指南.md](./LangChain4j开发指南.md) 第 8.1 节

#### 7.2 创建流式 Controller
- **包路径**：`org.albedo.vllmpt.chat.controller`
- **文件**：`StreamChatController.java`
- **关键点**：
  - 设置 `produces = MediaType.TEXT_EVENT_STREAM_VALUE`
  - 返回 `Flux<String>`
- **参考文档**：[LangChain4j开发指南.md](./LangChain4j开发指南.md) 第 8.2 节

#### 7.3 前端集成（可选）
- **技术**：JavaScript EventSource API
- **功能**：逐字显示 AI 回复
- **参考文档**：[LangChain4j开发指南.md](./LangChain4j开发指南.md) 第 8.3 节

#### 7.4 测试流式响应
- **工具**：浏览器或支持 SSE 的客户端
- **验证点**：观察响应是否逐块返回，而非一次性返回

### 学习重点
- 理解响应式编程（Reactor Flux）
- 掌握 SSE 协议的使用
- 了解流式响应对用户体验的提升

---

## 📊 第八阶段：监控与优化（2-3天）

### 目标
建立监控体系，优化性能和成本。

### 实施步骤

#### 8.1 记录模型调用统计
- **包路径**：`org.albedo.vllmpt.stats.service`
- **功能**：
  - 记录每次调用的模型 ID、场景、响应时间、Token 消耗、成本
  - 使用 Redis 存储统计数据
  - 按日期聚合统计
- **参考文档**：[AI模型路由与多模态选择方案.md](./AI模型路由与多模态选择方案.md) 第 8.1 节

#### 8.2 实现缓存机制
- **包路径**：`org.albedo.vllmpt.ai.cache`
- **功能**：
  - 对常见问题进行缓存
  - 减少重复的 API 调用
  - 降低成本和延迟
- **策略**：基于请求内容的哈希作为缓存 Key

#### 8.3 实现降级机制
- **包路径**：`org.albedo.vllmpt.ai.fallback`
- **功能**：
  - 主模型调用失败时，自动切换到备用模型
  - 备用模型选择策略（低成本、高稳定性）
  - 最终降级返回友好错误提示
- **参考文档**：[AI模型路由与多模态选择方案.md](./AI模型路由与多模态选择方案.md) 第 6.1 节（handleFallback 方法）

#### 8.4 添加日志和追踪
- **功能**：
  - 记录完整的请求链路（请求 → 识别 → 路由 → 调用 → 响应）
  - 记录关键指标（响应时间、Token 消耗、成本）
  - 便于问题排查和性能分析

#### 8.5 性能优化
- **优化点**：
  - 限制会话记忆的上下文长度（最多 10-20 条消息）
  - 异步处理耗时操作
  - 合理设置超时时间
  - 图片预处理（压缩、格式转换）

### 学习重点
- 掌握监控系统的设计思路
- 学会平衡性能、成本和质量
- 理解降级和容错的重要性

---

## 📝 第九阶段：测试与文档（1-2天）

### 目标
完善测试用例和项目文档。

### 实施步骤

#### 9.1 编写单元测试
- **测试范围**：
  - 每个 Service 类的核心方法
  - 识别器的准确性测试
  - 路由策略的正确性测试
- **参考文档**：[LangChain4j开发指南.md](./LangChain4j开发指南.md) 第 10.1 节

#### 9.2 编写集成测试
- **测试范围**：
  - 完整的 API 调用流程
  - 多轮对话的记忆功能
  - 工具调用的端到端测试
- **参考文档**：[LangChain4j开发指南.md](./LangChain4j开发指南.md) 第 10.2 节

#### 9.3 更新项目文档
- **文档内容**：
  - API 接口说明
  - 配置项说明
  - 部署指南
  - 常见问题解答

---

## 🎓 学习资源索引

### 核心文档
1. **[LangChain4j快速上手教程.md](./LangChain4j快速上手教程.md)**
   - 适合：初学者快速入门
   - 重点章节：第 5-9 节（实战示例）

2. **[LangChain4j开发指南.md](./LangChain4j开发指南.md)**
   - 适合：深入理解每个功能模块
   - 重点章节：第 5-9 节（详细实现步骤）

3. **[AI模型路由与多模态选择方案.md](./AI模型路由与多模态选择方案.md)**
   - 适合：理解智能路由系统设计
   - 重点章节：第 1-6 节（完整的路由架构）

### 官方资源
- LangChain4j 官方文档：https://docs.langchain4j.dev/
- 硅基流动 API 文档：https://docs.siliconflow.cn/

---

## ⚠️ 常见陷阱提醒

### 依赖相关
- **陷阱**：LangChain4j Redis 模块依赖名称错误
- **正确依赖**：`langchain4j-store-redis`（不是 `langchain4j-redis`）
- **参考**：记忆中 "LangChain4j Redis模块依赖名称错误"

### 配置相关
- **陷阱**：Redis 配置为空导致启动失败
- **解决**：确保 `application-dev.yml` 中 Redis 配置正确
- **参考**：记忆中 "避免空的Redis cluster.nodes配置导致启动失败"

### 模型相关
- **陷阱**：选择不支持多模态的模型处理图片
- **解决**：确保多模态场景使用 Qwen2-VL 等视觉模型
- **参考**：[AI模型路由与多模态选择方案.md](./AI模型路由与多模态选择方案.md) 第 3.2 节

---

## 📅 建议时间安排

| 阶段 | 预计时间 | 难度 | 优先级 |
|------|---------|------|--------|
| 第一阶段：环境验证 | 1天 | ⭐ | P0 |
| 第二阶段：基础对话 | 1-2天 | ⭐⭐ | P0 |
| 第三阶段：多模态 | 2-3天 | ⭐⭐⭐ | P1 |
| 第四阶段：会话记忆 | 1-2天 | ⭐⭐ | P1 |
| 第五阶段：工具调用 | 2-3天 | ⭐⭐⭐ | P2 |
| 第六阶段：智能路由 | 3-5天 | ⭐⭐⭐⭐ | P2 |
| 第七阶段：流式响应 | 1-2天 | ⭐⭐ | P3 |
| 第八阶段：监控优化 | 2-3天 | ⭐⭐⭐ | P3 |
| 第九阶段：测试文档 | 1-2天 | ⭐⭐ | P3 |

**总计**：约 14-23 个工作日（3-5 周）

---

## 🎯 下一步行动建议

1. **立即开始**：从第一阶段开始，验证环境配置
2. **循序渐进**：严格按照阶段顺序推进，不要跳跃
3. **每阶段验收**：完成一个阶段后，运行测试确保功能正常
4. **记录问题**：遇到问题时记录解决方案，形成个人知识库
5. **适时求助**：遇到阻塞问题时，查阅文档或寻求团队帮助

**祝你开发顺利！** 🚀
