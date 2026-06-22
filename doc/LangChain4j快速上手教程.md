# LangChain4j 快速上手教程

## 📖 教程说明

本教程将带你从零开始，快速掌握如何在 Spring Boot 项目中使用 LangChain4j 框架开发 AI 应用。

**适合人群**：
- ✅ Java/Spring Boot 开发者
- ✅ 想要快速集成 AI 能力的开发人员
- ✅ 对 LangChain4j 框架感兴趣的初学者

**学习目标**：
1. 理解 LangChain4j 的核心概念
2. 掌握基础的对话功能开发
3. 学会使用多模态（文本+图片）功能
4. 实现会话记忆管理
5. 了解高级特性（工具调用、流式响应等）

---

## 🚀 第一步：环境准备

### 1.1 前置条件

确保你的开发环境已安装：
- Java 21+
- Maven 3.8+
- IDE（推荐 IntelliJ IDEA）
- 硅基流动 API Key（或其他兼容 OpenAI 格式的 API）

### 1.2 获取 API Key

1. 访问硅基流动官网：https://cloud.siliconflow.cn/
2. 注册账号并登录
3. 在控制台创建 API Key
4. 复制 API Key，稍后配置到项目中

### 1.3 设置环境变量

**Windows PowerShell**：
```powershell
$env:SILICONFLOW_API_KEY="sk-your-api-key-here"
```

**Linux/Mac**：
```bash
export SILICONFLOW_API_KEY="sk-your-api-key-here"
```

**或者在 IDEA 中配置**：
1. Run → Edit Configurations
2. 在 Environment variables 中添加：`SILICONFLOW_API_KEY=sk-your-api-key-here`

---

## 📦 第二步：项目依赖配置

### 2.1 检查 pom.xml

项目中已经配置了 LangChain4j 相关依赖（位于 `pom.xml` 第 49-69 行）：

```xml
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

<!-- Redis 支持（用于会话记忆） -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-store-redis</artifactId>
    <version>0.35.0</version>
</dependency>
```

**如果依赖不存在**，请手动添加到 `pom.xml` 的 `<dependencies>` 部分，然后执行：
```bash
mvn clean install
```

---

## ⚙️ 第三步：配置文件设置

### 3.1 检查 application.yml

项目中已经配置了 LangChain4j（位于 `application.yml` 第 119-134 行）：

```yaml
langchain4j:
  open-ai:
    chat-model:
      # 硅基流动的 API 地址
      base-url: https://api.siliconflow.cn/v1
      # 您的 API Key（从环境变量读取）
      api-key: ${SILICONFLOW_API_KEY:your-api-key-here}
      # 默认模型
      model-name: Qwen/Qwen2.5-72B-Instruct
      # 超时时间（30秒）
      timeout: PT30S
      # 温度参数（0-1，越高越有创意）
      temperature: 0.7
      # 最大 Token 数
      max-tokens: 8192
```

**关键配置说明**：
- `base-url`：API 地址，硅基流动使用 OpenAI 兼容格式
- `api-key`：从环境变量读取，避免硬编码
- `model-name`：默认使用的模型，可以根据需要切换
- `temperature`：控制输出的随机性（0=确定性，1=创造性）

---

## 💡 第四步：核心概念理解

### 4.1 LangChain4j 核心组件

| 组件 | 作用 | 类比 |
|------|------|------|
| **ChatLanguageModel** | 聊天模型接口 | 相当于 AI 的"大脑" |
| **AiService** | AI 服务抽象 | 声明式的 AI 接口（类似 Spring Data JPA） |
| **ChatMemory** | 会话记忆 | 记住之前的对话内容 |
| **Tool** | 工具调用 | 让 AI 可以调用外部函数 |
| **Prompt Template** | 提示词模板 | 动态构建 Prompt |

### 4.2 消息类型

```java
// 系统消息（设定 AI 的角色和行为）
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

## 🎯 第五步：第一个 AI 应用（5分钟上手）

### 5.1 创建 AI 服务接口

**文件路径**：`src/main/java/org/albedo/vllmpt/ai/service/SimpleChatAssistant.java`

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

**关键点**：
- `@AiService`：标记这是一个 AI 服务接口
- `@SystemMessage`：设定 AI 的系统提示词（角色设定）
- `@UserMessage`：标记用户输入的参数

### 5.2 创建 Controller 调用

**文件路径**：`src/main/java/org/albedo/vllmpt/chat/controller/SimpleChatController.java`

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

### 5.3 启动并测试

1. **启动应用**：
   ```bash
   mvn spring-boot:run
   ```

2. **测试接口**（使用 curl 或 Postman）：
   ```bash
   curl -X POST http://localhost:8080/api/chat/simple \
     -H "Content-Type: application/json" \
     -d '{"message": "你好，请介绍一下自己"}'
   ```

3. **预期响应**：
   ```json
   {
     "response": "你好！我是电商智能助手，可以帮您解答商品相关问题..."
   }
   ```

**恭喜！** 你已经成功创建了第一个 LangChain4j AI 应用！🎉

---

## 🖼️ 第六步：多模态功能（文本+图片）

### 6.1 创建多模态服务

**文件路径**：`src/main/java/org/albedo/vllmpt/ai/service/MultimodalAssistant.java`

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

### 6.2 创建 Controller

**文件路径**：`src/main/java/org/albedo/vllmpt/chat/controller/MultimodalController.java`

```java
package org.albedo.vllmpt.chat.controller;

import org.albedo.vllmpt.chat.service.impl.MultimodalAssistant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat/multimodal")
public class MultimodalController {

    @Autowired
    private MultimodalAssistant multimodalAssistant;

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

### 6.3 测试多模态功能

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

---

## 💭 第七步：会话记忆管理

### 7.1 创建带记忆的 AI 服务

**文件路径**：`src/main/java/org/albedo/vllmpt/ai/service/MemoryChatAssistant.java`

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

**关键点**：
- `@MemoryId`：标记会话 ID 参数，LangChain4j 会自动管理该会话的记忆
- `clearMemory()`：清除指定会话的记忆

### 7.2 创建 Controller

```java
package org.albedo.vllmpt.chat.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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

### 7.3 测试多轮对话

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

**预期效果**：第二轮对话时，AI 会记得你想要"红色连衣裙"，给出相关推荐。

---

## 🔧 第八步：工具调用（Function Calling）

### 8.1 创建工具类

**文件路径**：`src/main/java/org/albedo/vllmpt/ai/tool/ProductSearchTool.java`

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

**关键点**：
- `@Tool`：标记这是一个可以被 AI 调用的工具
- 方法描述会传递给 AI，帮助它理解何时调用这个工具

### 8.2 创建带工具的 AI 服务

**文件路径**：`src/main/java/org/albedo/vllmpt/ai/service/ToolChatAssistant.java`

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

### 8.3 配置工具 Bean

**文件路径**：`src/main/java/org/albedo/vllmpt/ai/config/ToolConfig.java`

```java
package org.albedo.vllmpt.ai.config;

import dev.langchain4j.service.AiServices;
import org.albedo.vllmpt.ai.service.ToolChatAssistant;
import org.albedo.vllmpt.ai.tool.ProductSearchTool;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

### 8.4 创建 Controller

```java
@RestController
@RequestMapping("/api/chat/tool")
public class ToolChatController {
    
    @Autowired
    private ToolChatAssistant toolChatAssistant;
    
    @PostMapping
    public Map<String, String> chat(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        String response = toolChatAssistant.chat(message);
        return Map.of("response", response);
    }
}
```

### 8.5 测试工具调用

```bash
curl -X POST http://localhost:8080/api/chat/tool \
  -H "Content-Type: application/json" \
  -d '{
    "message": "帮我搜索一下连衣裙"
  }'
```

**预期效果**：AI 会自动调用 `searchProducts` 工具，返回搜索结果。

---

## 🌊 第九步：流式响应（SSE）

### 9.1 创建流式 AI 服务

**文件路径**：`src/main/java/org/albedo/vllmpt/ai/service/StreamChatAssistant.java`

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

### 9.2 创建 Controller（SSE 格式）

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

### 9.3 前端调用示例

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

## 🎓 第十步：最佳实践总结

### 10.1 开发流程

```
1. 定义需求 → 2. 选择模型 → 3. 设计 Prompt → 4. 编写代码 → 5. 测试优化
```

### 10.2 Prompt 工程技巧

**好的 Prompt 结构**：
```
[角色设定] + [任务描述] + [输出格式] + [示例]
```

**示例**：
```java
@SystemMessage("""
    你是一个专业的电商客服助手。
    
    任务：回答用户关于商品的问题。
    
    要求：
    1. 回答要简洁友好
    2. 如果不确定，诚实告知
    3. 适当推荐相关产品
    
    输出格式：纯文本，不超过 200 字
""")
```

### 10.3 模型选择建议

| 场景 | 推荐模型 | 原因 |
|------|---------|------|
| 客服对话 | Qwen2.5-72B-Instruct | 高质量、稳定性好 |
| 文案生成 | Qwen2.5-72B-Instruct（temperature=0.9） | 创意性强 |
| 图片审核 | Qwen2-VL-72B-Instruct | 视觉理解能力强 |
| 简单问答 | Qwen2.5-7B-Instruct | 成本低、速度快 |

### 10.4 性能优化建议

1. **缓存常用回答**：减少 API 调用次数
2. **异步处理**：耗时操作使用异步
3. **限制上下文长度**：最多保留最近 10-20 条消息
4. **合理设置超时**：避免长时间等待

### 10.5 常见错误及解决

| 错误 | 原因 | 解决方案 |
|------|------|---------|
| API Key 无效 | Key 错误或过期 | 检查环境变量配置 |
| 超时错误 | 网络慢或模型响应慢 | 增加 timeout 配置 |
| 内存溢出 | 上下文太长 | 限制消息数量 |
| 工具调用失败 | 工具描述不清晰 | 优化 @Tool 描述 |

---

## 📚 学习资源

### 官方文档
- [LangChain4j 官方文档](https://docs.langchain4j.dev/)
- [LangChain4j GitHub](https://github.com/langchain4j/langchain4j)

### 硅基流动
- [硅基流动官网](https://cloud.siliconflow.cn/)
- [API 文档](https://docs.siliconflow.cn/)

### 本项目文档
- [LangChain4j开发指南.md](./LangChain4j开发指南.md) - 详细的技术文档
- [AI模型路由与多模态选择方案.md](./AI模型路由与多模态选择方案.md) - 模型路由策略

---

## 🎯 下一步学习

完成本教程后，你可以继续学习：

1. **RAG（检索增强生成）**：结合向量数据库，让 AI 访问私有知识
2. **Agent 开发**：创建能够自主完成任务的 AI Agent
3. **多模型路由**：根据场景自动选择最优模型
4. **监控与日志**：追踪 AI 调用情况和成本

---

## ❓ 常见问题

### Q1: 如何切换不同的模型？

**A**: 修改 `application.yml` 中的 `model-name`，或在代码中动态创建不同模型的实例。

### Q2: 如何降低 API 调用成本？

**A**: 
- 使用缓存存储常见问题的答案
- 选择性价比更高的模型（如 Qwen2.5-7B）
- 限制上下文长度，减少 Token 消耗

### Q3: 如何处理并发请求？

**A**: LangChain4j 的 `ChatLanguageModel` 是线程安全的，可以直接在多线程环境中使用。

### Q4: 如何调试 AI 的响应？

**A**: 
- 启用 DEBUG 日志：`logging.level.dev.langchain4j=DEBUG`
- 打印完整的 Prompt 和响应
- 使用 Postman 逐步测试

---

**祝你学习愉快！** 🚀

如有问题，欢迎查阅项目文档或联系团队。
