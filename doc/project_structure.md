# vllmpt 项目文件结构说明

> 本文档按照**阿里巴巴Java开发手册**规范，对项目文件结构进行说明。
> 生成日期：2026-06-25

---

## 一、顶层结构

```
vllmpt/
├── pom.xml                          # Maven 项目配置文件
├── mvnw / mvnw.cmd                  # Maven Wrapper
├── src/
│   ├── main/
│   │   ├── java/org/albedo/vllmpt/  # Java 源代码
│   │   └── resources/               # 资源文件（配置、模板等）
│   └── test/                        # 测试代码
├── doc/                             # 项目文档
└── logs/                            # 日志文件
```

---

## 二、Java 源代码结构（阿里巴巴规范分层）

### 根包路径：`org.albedo.vllmpt`

```
org/albedo/vllmpt/
├── VllmptApplication.java           # Spring Boot 应用入口
│
├── config/                          # 【全局配置层】所有 @Configuration 类统一放置
│   ├── ChatMemoryConfig.java        # 聊天记忆配置（ChatMemoryProvider Bean）
│   ├── EmbeddingProperties.java     # Embedding 配置属性
│   ├── MinioConfig.java             # MinIO 客户端配置
│   ├── RedissonConfig.java          # Redisson + RedisTemplate 配置
│   └── WebSecurityConfig.java       # Spring Security 安全配置
│
├── common/                          # 【公共模块】跨模块共享的组件
│   ├── constant/                    # 常量定义（预留）
│   ├── enums/                       # 枚举类
│   │   ├── BizScene.java            # 业务场景枚举（通用问答/智能客服/文案生成等）
│   │   ├── InputType.java           # 输入类型枚举（纯文本/纯图片/多模态/文件）
│   │   ├── MessageRole.java         # 消息角色枚举（用户/助手/系统）
│   │   ├── ModelType.java           # 模型类型枚举（文本/图像/多模态）
│   │   └── RoutingStrategyType.java # 路由策略枚举（优先级/成本/性能/轮询）
│   ├── exception/                   # 异常定义
│   │   ├── BusinessException.java   # 业务异常
│   │   └── GlobalExceptionHandler.java  # 全局异常处理器（@RestControllerAdvice）
│   ├── result/                      # 统一响应体
│   │   └── Result.java              # 统一返回结果封装（code/message/data）
│   ├── security/                    # 安全相关处理器
│   │   ├── CustomAccessDeniedHandler.java         # 403 权限不足处理器
│   │   └── CustomAuthenticationEntryPoint.java    # 401 未认证处理器
│   └── util/                        # 工具类（预留）
│
└── module/                          # 【业务模块层】按业务领域划分
    │
    ├── ai/                          # AI 模型模块
    │   ├── config/
    │   │   └── ModelRegistry.java   # AI 模型注册表（@ConfigurationProperties）
    │   ├── extractor/               # 文档提取器
    │   │   ├── DocumentExtractor.java       # 文档文本提取接口
    │   │   └── impl/
    │   │       └── PdfBoxDocumentExtractor.java  # PDFBox 实现
    │   ├── model/
    │   │   ├── entity/
    │   │   │   └── ModelInfo.java   # 模型元数据实体
    │   │   └── dto/                 # 数据传输对象（预留）
    │   └── service/                 # AI 服务层
    │       ├── ChatModelFactory.java            # ChatModel/StreamingChatModel 工厂+缓存
    │       ├── EmbeddingModelFactory.java       # EmbeddingModel 工厂+缓存
    │       ├── MultimodalContentResolver.java   # 多模态内容解析器
    │       ├── MultimodalEmbeddingModel.java    # 多模态 Embedding 模型实现
    │       └── RedissonChatMemoryStore.java     # Redis 聊天记忆持久化存储
    │
    ├── chat/                        # 聊天对话模块
    │   ├── controller/              # 控制器层
    │   │   ├── MultimodalController.java          # 多模态聊天 API（/multiple, /stream）
    │   │   ├── MultimodalEnhancedController.java  # 增强多模态（上传+聊天）
    │   │   └── SimpleChatController.java          # 简单测试接口
    │   ├── model/
    │   │   ├── dto/
    │   │   │   └── MultimodalChatRequest.java     # 多模态聊天请求 DTO
    │   │   ├── entity/
    │   │   │   ├── Attachment.java    # 附件实体
    │   │   │   └── ProcessResult.java # 处理结果实体
    │   │   └── vo/                    # 视图对象（预留）
    │   ├── service/                   # 服务接口层
    │   │   ├── AttachmentProcessor.java       # 附件处理策略接口
    │   │   ├── ChatStream.java                # 流式聊天抽象接口
    │   │   ├── MultimodalAssistant.java       # 多模态助手接口
    │   │   └── impl/                          # 服务实现层
    │   │       ├── AttachmentProcessorRegistry.java  # 附件处理器注册表
    │   │       ├── DefaultChatStream.java             # 流式聊天实现
    │   │       └── MultimodalAssistantImpl.java       # 多模态助手实现
    │   └── handler/                  # 策略处理器（策略模式实现）
    │       ├── DocumentProcessor.java  # 文档处理器（pdf/doc/txt/md）
    │       ├── ImageProcessor.java     # 图片处理器
    │       └── VideoProcessor.java     # 视频处理器
    │
    ├── file/                         # 文件模块
    │   ├── controller/
    │   │   └── FileUploadController.java  # 文件上传 API
    │   └── service/
    │       ├── FileUploadService.java     # MinIO 文件上传服务
    │       └── impl/                      # 服务实现（预留）
    │
    └── stats/                        # 统计/测试模块
        └── controller/
            └── TestController.java   # 错误处理测试接口
```

---

## 三、分层规范说明

### 3.1 全局配置层 (`config/`)

按照阿里巴巴规范，所有 `@Configuration` 类统一放置在顶层 `config` 包中，不再分散在各业务模块内。便于集中管理和快速定位。

| 文件 | 功能 | 关键注解 |
|------|------|----------|
| `ChatMemoryConfig.java` | 聊天记忆配置 | `@Configuration`, `@Bean` |
| `EmbeddingProperties.java` | Embedding 属性配置 | `@ConfigurationProperties` |
| `MinioConfig.java` | MinIO 客户端配置 | `@Configuration`, `@ConfigurationProperties` |
| `RedissonConfig.java` | Redis/Redisson 配置 | `@Configuration`, `@Bean` |
| `WebSecurityConfig.java` | 安全配置 | `@Configuration`, `@EnableWebSecurity` |

### 3.2 公共模块 (`common/`)

跨模块共享的基础组件，按功能分类：

| 子包 | 用途 | 文件数 |
|------|------|--------|
| `constant/` | 常量定义 | 预留 |
| `enums/` | 枚举类 | 5 |
| `exception/` | 异常定义 + 全局异常处理 | 2 |
| `result/` | 统一响应体封装 | 1 |
| `security/` | 安全认证处理器 | 2 |
| `util/` | 工具类 | 预留 |

### 3.3 业务模块层 (`module/`)

每个业务模块内部遵循 **controller → service → service/impl → mapper（预留）** 的分层结构：

```
module/<模块名>/
├── controller/     # 控制层：接收请求、参数校验、调用 service
├── service/        # 服务接口：业务逻辑接口定义
│   └── impl/       # 服务实现：业务逻辑具体实现
├── model/
│   ├── entity/     # 实体类：与数据库表对应的 POJO
│   ├── dto/        # 数据传输对象：请求/响应参数封装
│   └── vo/         # 视图对象：前端展示数据
├── handler/        # 策略处理器（策略模式）
└── mapper/         # 数据访问层：MyBatis Mapper 接口（预留）
```

### 3.4 命名规范

| 层次 | 命名规范 | 示例 |
|------|----------|------|
| Controller | `XxxController` | `MultimodalController` |
| Service 接口 | `XxxService` / 业务名词 | `MultimodalAssistant`, `FileUploadService` |
| Service 实现 | `XxxServiceImpl` / `XxxImpl` | `MultimodalAssistantImpl` |
| Entity | 业务名词 | `Attachment`, `ProcessResult` |
| DTO | `XxxRequest` / `XxxDTO` | `MultimodalChatRequest` |
| VO | `XxxVO` | 预留 |
| Mapper | `XxxMapper` | 预留 |
| Enum | 枚举名词 | `BizScene`, `ModelType` |
| Exception | `XxxException` | `BusinessException` |
| Config | `XxxConfig` / `XxxProperties` | `RedissonConfig`, `EmbeddingProperties` |
| Handler | `XxxHandler` / `XxxProcessor` | `ImageProcessor`, `DocumentProcessor` |

---

## 四、资源文件结构

```
src/main/resources/
├── application.yml          # 主配置：公共属性、AI models 注册、langchain4j、日志、MyBatis-Plus
├── application-dev.yml      # 开发环境：DB/Redis/RabbitMQ/MinIO/ES 连接信息
├── application-prod.yml     # 生产环境：全部环境变量化，Swagger 关闭
├── application-test.yml     # 测试环境
├── static/                  # 静态资源（预留）
└── templates/               # 模板文件（预留）
```

---

## 五、模块依赖关系

```
config/  ←── 全局配置，被所有模块引用
  ↑
common/  ←── 公共组件，被所有模块引用
  ↑
module/
  ├── ai/    ←── AI 模型工厂、Embedding、记忆存储
  │   ↑
  ├── chat/  ←── 聊天对话（依赖 ai 模块的模型工厂和内容解析器）
  ├── file/  ←── 文件上传（依赖 config 中的 MinioConfig）
  └── stats/ ←── 测试模块（独立）
```

核心调用链：`controller → service → service/impl → ai模块的工厂/解析器`

---

## 六、技术栈总结

| 类别 | 技术 | 版本 |
|------|------|------|
| 基础框架 | Spring Boot | 4.0.6 |
| JDK | Java | 21 (--enable-preview) |
| AI 框架 | LangChain4j | 1.16.2 |
| 数据库 | MyBatis-Plus + MySQL | 3.5.15 / 9.7.0 |
| 缓存 | Redis (Spring Data Redis + Redisson) | 4.4.0 |
| 消息队列 | RabbitMQ | 5.30.0 |
| 搜索引擎 | Elasticsearch | 9.4.1 |
| 文件存储 | MinIO | 8.5.7 |
| 安全认证 | Spring Security + JJWT | 0.13.0 |
| 工具库 | Lombok / Hutool / Commons Lang3 | 1.18.46 / 5.8.44 / 3.20.0 |
| API 文档 | SpringDoc OpenAPI | 2.3.0 |
| HTTP 客户端 | Spring WebFlux | - |
| 监控 | Spring Boot Actuator | - |
