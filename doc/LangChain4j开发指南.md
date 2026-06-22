# LangChain4j 快速开发指南

## 📋 文档说明

本文档基于 LangChain4j 框架，为电商 AI 平台提供快速开发指引。适用于新手开发者，帮助快速上手多模态 AI 功能开发。

**技术选型理由：**
- ✅ 降低多模态开发门槛
- ✅ 加速项目交付
- ✅ 简化 Prompt 工程和会话管理
- ✅ 丰富的生态组件

---

## 🎯 目录

1. [环境准备](#一环境准备)
2. [依赖配置](#二依赖配置)
3. [核心概念](#三核心概念)
4. [快速开始](#四快速开始)
5. [多模态实现](#五多模态实现)
6. [业务场景路由](#六业务场景路由)
7. [会话记忆管理](#七会话记忆管理)
8. [流式响应](#八流式响应)
9. [工具调用](#九工具调用)
10. [测试与调试](#十测试与调试)
11. [常见问题](#十一常见问题)
12. [最佳实践](#十二最佳实践)

---

## 一、环境准备

### 1.1 前置条件

- Java 21+
- Spring Boot 3.x/4.x
- Maven 3.8+
- 硅基流动 API Key（或其他兼容 OpenAI 格式的 API）

### 1.2 获取 API Key

1. 注册硅基流动账号：https://cloud.siliconflow.cn/
2. 创建 API Key
3. 设置环境变量或配置文件

```bash
# Windows PowerShell
$env:SILICONFLOW_API_KEY="your-api-key-here"

# Linux/Mac
export SILICONFLOW_API_KEY="your-api-key-here"
```

---

## 二、依赖配置

### 2.1 修改 pom.xml

在 `<dependencies>` 部分添加以下依赖：

```xml
<!-- ========== LangChain4j AI 框架 ========== -->
<!-- LangChain4j Spring Boot Starter（核心） -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-spring-boot-starter</artifactId>
    <version>0.35.0</version>
</dependency>

<!-- OpenAI 兼容模型支持（硅基流动使用 OpenAI 格式） -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai-spring-boot-starter</artifactId>
    <version>0.35.0</version>
</dependency>

<!-- LangChain4j 核心库（多模态支持） -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>0.35.0</version>
</dependency>

<!-- Redis 支持（用于会话记忆） -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-store-redis</artifactId>
    <version>0.35.0</version>
</dependency>
```

### 2.2 清理旧依赖（可选）

如果之前有自研的 AI 相关依赖，可以考虑移除：
- 自定义的 HTTP Client 封装
- 自研的 Prompt 构建工具类

**注意：** 保留现有的 Spring WebFlux、Redisson 等基础依赖。

---

## 三、核心概念

### 3.1 LangChain4j 核心组件

| 组件 | 说明 | 用途 |
|------|------|------|
| **ChatLanguageModel** | 聊天模型接口 | 调用 LLM 进行对话 |
| **AiService** | AI 服务抽象 | 声明式 AI 接口 |
| **ChatMemory** | 会话记忆 | 管理对话历史 |
| **Tool** | 工具调用 | Function Calling |
| **Prompt Template** | 提示词模板 | 动态构建 Prompt |

### 3.2 消息类型

```java
// 系统消息（设定角色）
SystemMessage systemMsg = SystemMessage.from("你是一个专业的电商助手");

// 用户消息（支持多模态）
UserMessage userMsg = UserMessage.from("这件衣服怎么样？");

// 多模态消息（文本+图片）
UserMessage multimodalMsg = UserMessage.from(
    TextContent.from("这件衣服怎么样？"),
    ImageContent.from("https://example.com/image.jpg")
);

// AI 回复
AiMessage aiMsg = AiMessage.from("这件衣服设计很时尚...");
```

---

## 四、快速开始

### 4.1 配置文件设置

在 `src/main/resources/application-dev.yml` 中添加：

```yaml
langchain4j:
  open-ai:
    chat-model:
      # 硅基流动 API 地址
      base-url: https://api.siliconflow.cn/v1
      # API Key（从环境变量读取）
      api-key: ${SILICONFLOW_API_KEY}
      # 默认模型
      model-name: Qwen/Qwen2.5-72B-Instruct
      # 超时时间
      timeout: PT30S
      # 温度参数（0-1，越高越创意）
      temperature: 0.7
      # 最大 Token 数
      max-tokens: 8192
      # 顶部采样
      top-p: 0.9
```

### 4.2 创建第一个 AI 服务

**步骤 1：** 创建 AI 服务接口

文件路径：`src/main/java/org/albedo/vllmpt/ai/service/SimpleChatAssistant.java`

```java
package org.albedo.vllmpt.ai.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.spring.AiService;

/**
 * 简单聊天助手（最基础的用法）
 */
@AiService
public interface SimpleChatAssistant {
    
    /**
     * 简单对话
     * @param userMessage 用户消息
     * @return AI 回复
     */
    @SystemMessage("你是一个专业的电商智能助手，回答要简洁友好")
    String chat(@UserMessage String userMessage);
}
```

**步骤 2：** 创建 Controller 调用

文件路径：`src/main/java/org/albedo/vllmpt/chat/controller/SimpleChatController.java`

```java
package org.albedo.vllmpt.chat.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chat/simple")
public class SimpleChatController {

    @Autowired
    private SimpleChatAssistant chatAssistant;

    /**
     * 简单对话接口
     */
    @PostMapping
    public Map<String, String> chat(@RequestBody Map<String, String> request) {
        String userMessage = request.get("message");
        String response = chatAssistant.chat(userMessage);
        return Map.of("response", response);
    }
}
```

**步骤 3：** 测试

```bash
curl -X POST http://localhost:8080/api/chat/simple \
  -H "Content-Type: application/json" \
  -d '{"message": "你好，请介绍一下自己"}'
```

预期响应：
```json
{
  "response": "你好！我是电商智能助手，可以帮您解答商品相关问题..."
}
```

---

## 五、多模态实现

### 5.1 文本+图片对话

**步骤 1：** 创建多模态服务

文件路径：`src/main/java/org/albedo/vllmpt/ai/service/MultimodalAssistant.java`

```java
package org.albedo.vllmpt.ai.service;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 多模态助手（支持图片+文本）
 */
@Service
@Slf4j
public class MultimodalAssistant {
    
    @Autowired
    private ChatLanguageModel chatModel;
    
    /**
     * 处理单张图片 + 文本
     */
    public String chatWithImage(String text, String imageUrl) {
        log.info("处理多模态请求 - 文本: {}, 图片: {}", text, imageUrl);
        
        // 构建多模态消息
        UserMessage message = UserMessage.from(
            TextContent.from(text),
            ImageContent.from(imageUrl)
        );
        
        // 调用模型
        String response = chatModel.generate(message).content().text();
        
        log.info("多模态响应完成");
        return response;
    }
    
    /**
     * 处理多张图片 + 文本
     */
    public String chatWithMultipleImages(String text, List<String> imageUrls) {
        log.info("处理多图片请求 - 文本: {}, 图片数量: {}", text, imageUrls.size());
        
        // 构建包含多张图片的消息
        UserMessage.Builder builder = UserMessage.builder();
        builder.addContent(TextContent.from(text));
        
        for (String url : imageUrls) {
            builder.addContent(ImageContent.from(url));
        }
        
        UserMessage message = builder.build();
        return chatModel.generate(message).content().text();
    }
}
```

**步骤 2：** 创建 Controller

文件路径：`src/main/java/org/albedo/vllmpt/chat/controller/MultimodalController.java`

```java
package org.albedo.vllmpt.chat.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat/multimodal")
public class MultimodalController {

    @Autowired
    private org.albedo.vllmpt.chat.service.impl.MultimodalAssistant multimodalAssistant;

    /**
     * 单图对话
     */
    @PostMapping("/single-image")
    public Map<String, String> chatWithImage(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        String imageUrl = request.get("imageUrl");

        String response = multimodalAssistant.chatWithImage(text, imageUrl);
        return Map.of("response", response);
    }

    /**
     * 多图对话
     */
    @PostMapping("/multiple-images")
    public Map<String, String> chatWithMultipleImages(
            @RequestBody Map<String, Object> request) {
        String text = (String) request.get("text");
        List<String> imageUrls = (List<String>) request.get("imageUrls");

        String response = multimodalAssistant.chatWithMultipleImages(text, imageUrls);
        return Map.of("response", response);
    }
}
```

**步骤 3：** 测试

```bash
# 单图测试
curl -X POST http://localhost:8080/api/chat/multimodal/single-image \
  -H "Content-Type: application/json" \
  -d '{
    "text": "这件衣服怎么样？",
    "imageUrl": "https://example.com/clothes.jpg"
  }'

# 多图测试
curl -X POST http://localhost:8080/api/chat/multimodal/multiple-images \
  -H "Content-Type: application/json" \
  -d '{
    "text": "对比这两件衣服",
    "imageUrls": [
      "https://example.com/clothes1.jpg",
      "https://example.com/clothes2.jpg"
    ]
  }'
```

### 5.2 图片预处理（可选）

如果需要处理本地上传的图片，可以添加预处理逻辑：

```java
@Component
public class ImagePreprocessor {
    
    @Autowired
    private MinioService minioService;
    
    /**
     * 预处理图片（压缩、上传到 MinIO）
     */
    public String preprocessImage(MultipartFile file) {
        // 1. 验证图片格式和大小
        validateImage(file);
        
        // 2. 压缩图片（如果需要）
        byte[] compressed = compressIfNeeded(file.getBytes());
        
        // 3. 上传到 MinIO
        String imageUrl = minioService.upload(compressed, file.getOriginalFilename());
        
        return imageUrl;
    }
}
```

---

## 六、业务场景路由

### 6.1 智能路由器

**步骤 1：** 创建路由器

文件路径：`src/main/java/org/albedo/vllmpt/ai/router/SmartRouter.java`

```java
package org.albedo.vllmpt.ai.router;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 智能路由器（根据场景选择不同模型）
 */
@Component
@Slf4j
public class SmartRouter {
    
    // 缓存不同场景的模型实例
    private final Map<String, ChatLanguageModel> modelCache = new ConcurrentHashMap<>();
    
    private static final String BASE_URL = "https://api.siliconflow.cn/v1";
    private static final String API_KEY = System.getenv("SILICONFLOW_API_KEY");
    
    /**
     * 根据业务场景获取模型
     */
    public ChatLanguageModel getModelForScene(String scene) {
        return modelCache.computeIfAbsent(scene, this::createModelForScene);
    }
    
    private ChatLanguageModel createModelForScene(String scene) {
        String modelName;
        double temperature;
        
        switch (scene.toUpperCase()) {
            case "CUSTOMER_SERVICE":
                // 客服场景：高质量、稳定性优先
                modelName = "Qwen/Qwen2.5-72B-Instruct";
                temperature = 0.7;
                break;
                
            case "COPYWRITING":
                // 文案生成：创意性优先
                modelName = "Qwen/Qwen2.5-72B-Instruct";
                temperature = 0.9;  // 更高的温度增加创意
                break;
                
            case "IMAGE_AUDIT":
                // 图片审核：视觉模型
                modelName = "Qwen/Qwen2-VL-72B-Instruct";
                temperature = 0.3;  // 低温度保证准确性
                break;
                
            case "PRODUCT_ANALYSIS":
                // 商品分析：专业性强
                modelName = "Qwen/Qwen2.5-72B-Instruct";
                temperature = 0.5;
                break;
                
            case "GENERAL":
            default:
                // 通用场景：性价比优先
                modelName = "Qwen/Qwen2.5-7B-Instruct";
                temperature = 0.7;
                break;
        }
        
        log.info("为场景 [{}] 创建模型实例: {} (temperature: {})", 
                scene, modelName, temperature);
        
        return OpenAiChatModel.builder()
            .baseUrl(BASE_URL)
            .apiKey(API_KEY)
            .modelName(modelName)
            .temperature(temperature)
            .maxTokens(8192)
            .build();
    }
}
```

**步骤 2：** 创建场景识别器

文件路径：`src/main/java/org/albedo/vllmpt/ai/recognizer/SceneRecognizer.java`

```java
package org.albedo.vllmpt.ai.recognizer;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.HashMap;

/**
 * 业务场景识别器（基于关键词规则）
 */
@Component
public class SceneRecognizer {
    
    private static final Map<String, String> KEYWORD_RULES = new HashMap<>();
    
    static {
        // 客服相关关键词
        KEYWORD_RULES.put("价格", "CUSTOMER_SERVICE");
        KEYWORD_RULES.put("优惠", "CUSTOMER_SERVICE");
        KEYWORD_RULES.put("库存", "CUSTOMER_SERVICE");
        KEYWORD_RULES.put("物流", "CUSTOMER_SERVICE");
        KEYWORD_RULES.put("退款", "CUSTOMER_SERVICE");
        
        // 文案相关关键词
        KEYWORD_RULES.put("写一个", "COPYWRITING");
        KEYWORD_RULES.put("生成", "COPYWRITING");
        KEYWORD_RULES.put("描述", "COPYWRITING");
        KEYWORD_RULES.put("标题", "COPYWRITING");
        
        // 审核相关关键词
        KEYWORD_RULES.put("审核", "IMAGE_AUDIT");
        KEYWORD_RULES.put("违规", "IMAGE_AUDIT");
        
        // 商品分析
        KEYWORD_RULES.put("对比", "PRODUCT_ANALYSIS");
        KEYWORD_RULES.put("参数", "PRODUCT_ANALYSIS");
        KEYWORD_RULES.put("评测", "PRODUCT_ANALYSIS");
    }
    
    /**
     * 识别业务场景
     */
    public String recognize(String content) {
        if (content == null || content.isEmpty()) {
            return "GENERAL";
        }
        
        for (Map.Entry<String, String> entry : KEYWORD_RULES.entrySet()) {
            if (content.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        return "GENERAL";
    }
}
```

**步骤 3：** 整合到服务层

文件路径：`src/main/java/org/albedo/vllmpt/chat/service/SmartChatService.java`

```java
package org.albedo.vllmpt.chat.service;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.albedo.vllmpt.ai.recognizer.SceneRecognizer;
import org.albedo.vllmpt.ai.router.SmartRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SmartChatService {
    
    @Autowired
    private SmartRouter smartRouter;
    
    @Autowired
    private SceneRecognizer sceneRecognizer;
    
    /**
     * 智能对话（自动路由）
     */
    public String smartChat(String userMessage) {
        // 1. 识别业务场景
        String scene = sceneRecognizer.recognize(userMessage);
        log.info("识别场景: {}", scene);
        
        // 2. 获取对应模型
        ChatLanguageModel model = smartRouter.getModelForScene(scene);
        
        // 3. 调用模型
        String response = model.generate(userMessage).content().text();
        
        log.info("场景 [{}] 响应完成", scene);
        return response;
    }
}
```

**步骤 4：** 创建 Controller

```java
@RestController
@RequestMapping("/api/chat/smart")
public class SmartChatController {
    
    @Autowired
    private SmartChatService smartChatService;
    
    @PostMapping
    public Map<String, String> chat(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        String response = smartChatService.smartChat(message);
        return Map.of("response", response, "scene", "auto-detected");
    }
}
```

---

## 七、会话记忆管理

### 7.1 使用 Redis 存储会话历史

**步骤 1：** 添加依赖（已在第二步添加）

确保 `pom.xml` 中包含：
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-store-redis</artifactId>
    <version>0.35.0</version>
</dependency>
```

**步骤 2：** 配置 Redis Chat Memory

文件路径：`src/main/java/org/albedo/vllmpt/ai/config/ChatMemoryConfig.java`

```java
package org.albedo.vllmpt.ai.config;

import dev.langchain4j.store.memory.chat.redis.RedisChatMemoryStore;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatMemoryConfig {
    
    @Bean
    public RedisChatMemoryStore redisChatMemoryStore(RedissonClient redissonClient) {
        return RedisChatMemoryStore.builder()
            .redissonClient(redissonClient)
            .build();
    }
}
```

**步骤 3：** 创建带记忆的 AI 服务

文件路径：`src/main/java/org/albedo/vllmpt/ai/service/MemoryChatAssistant.java`

```java
package org.albedo.vllmpt.ai.service;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.spring.AiService;

/**
 * 带会话记忆的聊天助手
 */
@AiService
public interface MemoryChatAssistant {
    
    /**
     * 带记忆的对话
     * @param chatId 会话 ID（用于区分不同用户的对话）
     * @param userMessage 用户消息
     * @return AI 回复
     */
    @SystemMessage("你是一个专业的电商智能助手，记住之前的对话内容")
    String chat(@MemoryId String chatId, @UserMessage String userMessage);
    
    /**
     * 清除会话记忆
     */
    void clearMemory(@MemoryId String chatId);
}
```

**步骤 4：** 创建 Controller

```java
@RestController
@RequestMapping("/api/chat/memory")
public class MemoryChatController {
    
    @Autowired
    private MemoryChatAssistant memoryChatAssistant;
    
    /**
     * 带记忆的对话
     */
    @PostMapping
    public Map<String, String> chat(@RequestBody Map<String, String> request) {
        String chatId = request.get("chatId");  // 会话 ID
        String message = request.get("message");
        
        String response = memoryChatAssistant.chat(chatId, message);
        return Map.of("response", response, "chatId", chatId);
    }
    
    /**
     * 清除会话记忆
     */
    @DeleteMapping("/{chatId}")
    public Map<String, String> clearMemory(@PathVariable String chatId) {
        memoryChatAssistant.clearMemory(chatId);
        return Map.of("message", "会话记忆已清除", "chatId", chatId);
    }
}
```

**步骤 5：** 测试多轮对话

```bash
# 第一轮对话
curl -X POST http://localhost:8080/api/chat/memory \
  -H "Content-Type: application/json" \
  -d '{
    "chatId": "user123",
    "message": "我想买一件红色的连衣裙"
  }'

# 第二轮对话（AI 会记得上一轮的内容）
curl -X POST http://localhost:8080/api/chat/memory \
  -H "Content-Type: application/json" \
  -d '{
    "chatId": "user123",
    "message": "有什么推荐吗？"
  }'
```

---

## 八、流式响应

### 8.1 实现流式输出

**步骤 1：** 创建流式 AI 服务

文件路径：`src/main/java/org/albedo/vllmpt/ai/service/StreamChatAssistant.java`

```java
package org.albedo.vllmpt.ai.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.spring.AiService;
import reactor.core.publisher.Flux;

/**
 * 流式聊天助手
 */
@AiService
public interface StreamChatAssistant {
    
    /**
     * 流式对话
     */
    @SystemMessage("你是一个专业的电商智能助手")
    Flux<String> chatStream(@UserMessage String userMessage);
}
```

**步骤 2：** 创建 Controller（SSE 格式）

```java
@RestController
@RequestMapping("/api/chat/stream")
public class StreamChatController {
    
    @Autowired
    private StreamChatAssistant streamChatAssistant;
    
    /**
     * 流式响应（Server-Sent Events）
     */
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        return streamChatAssistant.chatStream(message);
    }
}
```

**步骤 3：** 前端调用示例

```javascript
// JavaScript 前端调用
const eventSource = new EventSource('/api/chat/stream', {
  headers: {
    'Content-Type': 'application/json'
  }
});

eventSource.onmessage = (event) => {
  console.log('收到数据:', event.data);
  // 逐字显示 AI 回复
  document.getElementById('response').textContent += event.data;
};

eventSource.onerror = (error) => {
  console.error('SSE 错误:', error);
  eventSource.close();
};
```

---

## 九、工具调用（Function Calling）

### 9.1 定义工具

**步骤 1：** 创建工具类

文件路径：`src/main/java/org/albedo/vllmpt/ai/tool/ProductSearchTool.java`

```java
package org.albedo.vllmpt.ai.tool;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 商品搜索工具
 */
@Component
@Slf4j
public class ProductSearchTool {
    
    /**
     * 搜索商品
     */
    @Tool("根据关键词搜索商品")
    public String searchProducts(String keyword) {
        log.info("搜索商品: {}", keyword);
        
        // TODO: 实际应该调用数据库或搜索引擎
        // 这里返回模拟数据
        return String.format(
            "找到以下商品：\n" +
            "1. %s - ¥99.00\n" +
            "2. %s - ¥199.00\n" +
            "3. %s - ¥299.00",
            keyword + " A款",
            keyword + " B款",
            keyword + " C款"
        );
    }
    
    /**
     * 查询商品价格
     */
    @Tool("查询指定商品的价格")
    public String getProductPrice(String productName) {
        log.info("查询商品价格: {}", productName);
        
        // TODO: 实际应该查询数据库
        return String.format("%s 的价格是 ¥199.00", productName);
    }
}
```

**步骤 2：** 创建带工具的 AI 服务

文件路径：`src/main/java/org/albedo/vllmpt/ai/service/ToolChatAssistant.java`

```java
package org.albedo.vllmpt.ai.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.spring.AiService;

/**
 * 带工具调用的聊天助手
 */
@AiService
public interface ToolChatAssistant {
    
    /**
     * 可以使用工具的对话
     */
    @SystemMessage("你是一个电商助手，可以使用工具查询商品信息")
    String chat(String userMessage);
}
```

**步骤 3：** 配置工具 Bean

文件路径：`src/main/java/org/albedo/vllmpt/ai/config/ToolConfig.java`

```java
package org.albedo.vllmpt.ai.config;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.albedo.vllmpt.ai.service.ToolChatAssistant;
import org.albedo.vllmpt.ai.tool.ProductSearchTool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ToolConfig {
    
    @Bean
    public ToolChatAssistant toolChatAssistant(
            ChatLanguageModel chatModel,
            ProductSearchTool productSearchTool) {
        
        return AiServices.builder(ToolChatAssistant.class)
            .chatLanguageModel(chatModel)
            .tools(productSearchTool)
            .build();
    }
}
```

**步骤 4：** 测试工具调用

```bash
curl -X POST http://localhost:8080/api/chat/tool \
  -H "Content-Type: application/json" \
  -d '{
    "message": "帮我搜索一下连衣裙"
  }'
```

AI 会自动调用 `searchProducts` 工具并返回结果。

---

## 十、测试与调试

### 10.1 单元测试

**步骤 1：** 创建测试类

文件路径：`src/test/java/org/albedo/vllmpt/ai/service/MultimodalAssistantTest.java`

```java
package org.albedo.vllmpt.ai.service;

import org.albedo.vllmpt.chat.service.impl.MultimodalAssistant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MultimodalAssistantTest {

    @Autowired
    private MultimodalAssistant multimodalAssistant;

    @Test
    void testChatWithImage() {
        String text = "这件衣服怎么样？";
        String imageUrl = "https://example.com/test.jpg";

        String response = multimodalAssistant.chatWithImage(text, imageUrl);

        assertNotNull(response);
        assertFalse(response.isEmpty());
        System.out.println("响应: " + response);
    }
}
```

### 10.2 集成测试

**步骤 1：** 创建 Controller 测试

```java
@SpringBootTest
@AutoConfigureMockMvc
class ChatControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void testSimpleChat() throws Exception {
        String requestBody = "{\"message\": \"你好\"}";
        
        mockMvc.perform(post("/api/chat/simple")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.response").exists());
    }
}
```

### 10.3 调试技巧

**启用详细日志：**

在 `application-dev.yml` 中添加：

```yaml
logging:
  level:
    dev.langchain4j: DEBUG
    org.albedo.vllmpt.ai: DEBUG
```

**查看 API 调用详情：**

LangChain4j 会自动记录：
- 发送的请求内容
- 接收的响应内容
- Token 使用情况
- 响应时间

---

## 十一、常见问题

### 11.1 API Key 配置问题

**问题：** 启动时报错 "API key is required"

**解决：**
```yaml
# 确保配置文件中正确引用环境变量
langchain4j:
  open-ai:
    chat-model:
      api-key: ${SILICONFLOW_API_KEY}
```

检查环境变量是否设置：
```bash
# Windows
echo $env:SILICONFLOW_API_KEY

# Linux/Mac
echo $SILICONFLOW_API_KEY
```

### 11.2 多模态图片无法访问

**问题：** 调用多模态接口时返回 "Image not accessible"

**解决：**
1. 确保图片 URL 是公网可访问的
2. 或使用 Base64 编码：
```java
// 将本地图片转为 Base64
String base64Image = Base64.getEncoder().encodeToString(imageBytes);
ImageContent.from(base64Image, "image/jpeg");
```

### 11.3 会话记忆不生效

**问题：** 多轮对话时 AI 不记得之前的内容

**解决：**
1. 检查 `chatId` 是否保持一致
2. 确认 Redis 连接正常
3. 检查 `MessageWindowChatMemory` 的最大消息数配置

### 11.4 流式响应中断

**问题：** SSE 连接提前断开

**解决：**
```yaml
# 增加超时时间
langchain4j:
  open-ai:
    chat-model:
      timeout: PT60S  # 60秒
```

前端增加重连机制：
```javascript
eventSource.onerror = () => {
  setTimeout(() => {
    // 重新连接
  }, 3000);
};
```

### 11.5 模型响应慢

**问题：** API 调用超过 30 秒

**解决：**
1. 切换到更快的模型（如 Qwen2.5-7B）
2. 减少 `max-tokens`
3. 降低 `temperature`

---

## 十二、最佳实践

### 12.1 代码组织

```
src/main/java/org/albedo/vllmpt/ai/
├── config/              # 配置类
│   ├── ChatMemoryConfig.java
│   └── ToolConfig.java
├── service/             # AI 服务
│   ├── SimpleChatAssistant.java
│   ├── MultimodalAssistant.java
│   └── StreamChatAssistant.java
├── router/              # 路由器
│   └── SmartRouter.java
├── recognizer/          # 识别器
│   └── SceneRecognizer.java
├── tool/                # 工具类
│   └── ProductSearchTool.java
└── model/               # 数据模型
    └── ModelInfo.java
```

### 12.2 错误处理

```java
@Service
public class RobustChatService {
    
    public String safeChat(String message) {
        try {
            return chatAssistant.chat(message);
        } catch (Exception e) {
            log.error("AI 调用失败", e);
            return "抱歉，服务暂时不可用，请稍后重试";
        }
    }
}
```

### 12.3 性能优化

1. **缓存模型实例**：避免重复创建
2. **异步处理**：使用 `CompletableFuture`
3. **批量请求**：合并多个小请求
4. **限流保护**：防止 API 调用过载

```java
@Component
public class RateLimitedChatService {
    
    private final Semaphore semaphore = new Semaphore(10);  // 最多 10 个并发
    
    public String rateLimitedChat(String message) throws InterruptedException {
        semaphore.acquire();
        try {
            return chatAssistant.chat(message);
        } finally {
            semaphore.release();
        }
    }
}
```

### 12.4 监控与统计

```java
@Aspect
@Component
public class AiMonitoringAspect {
    
    @Around("@annotation(dev.langchain4j.spring.AiService)")
    public Object monitorAiCall(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            
            // 记录指标
            Metrics.timer("ai.call.duration").record(duration, TimeUnit.MILLISECONDS);
            Metrics.counter("ai.call.success").increment();
            
            return result;
        } catch (Exception e) {
            Metrics.counter("ai.call.error").increment();
            throw e;
        }
    }
}
```

### 12.5 安全注意事项

1. **不要硬编码 API Key**：使用环境变量或配置中心
2. **输入验证**：过滤恶意输入
3. **输出过滤**：防止注入攻击
4. **速率限制**：防止滥用

```java
@Component
public class InputValidator {
    
    public String validateInput(String input) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("输入不能为空");
        }
        
        if (input.length() > 10000) {
            throw new IllegalArgumentException("输入过长");
        }
        
        // 过滤敏感词
        return filterSensitiveWords(input);
    }
}
```

---

## 📚 参考资料

- **LangChain4j 官方文档**：https://docs.langchain4j.dev/
- **GitHub 仓库**：https://github.com/langchain4j/langchain4j
- **示例代码**：https://github.com/langchain4j/langchain4j-examples
- **硅基流动文档**：https://docs.siliconflow.cn/

---

## 🔄 更新日志

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.0 | 2026-05-21 | 初始版本，基于 LangChain4j 0.35.0 |

---

**文档维护者：** AI Assistant  
**最后更新：** 2026-05-21
