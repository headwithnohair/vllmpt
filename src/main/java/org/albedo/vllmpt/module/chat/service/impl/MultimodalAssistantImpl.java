package org.albedo.vllmpt.module.chat.service.impl;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.document.Metadata;

import dev.langchain4j.data.message.*;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;

import dev.langchain4j.model.chat.request.ChatRequest;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.module.ai.service.ChatModelFactory;
import org.albedo.vllmpt.module.ai.service.EmbeddingModelFactory;
import org.albedo.vllmpt.module.ai.service.MultimodalContentResolver;
import org.albedo.vllmpt.module.chat.model.dto.AgentContext;
import org.albedo.vllmpt.module.chat.model.dto.MultimodalChatRequest;
import org.albedo.vllmpt.module.chat.service.Assistant;
import org.albedo.vllmpt.module.chat.service.MultimodalAssistant;
import org.albedo.vllmpt.module.chat.tool.ToolRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 多模态助手（支持图片+文本）
 */
@Service
@Slf4j
public class MultimodalAssistantImpl implements MultimodalAssistant {

    @Autowired
    private ChatMemoryProvider memoryProvider;

    @Autowired
    private ChatModelFactory chatModelFactory;

    @Autowired
    private MultimodalContentResolver contentResolver;




    @Autowired
    private KnowledgeBaseRagServiceImpl knowledgeBaseRagService;

    @Autowired
    private  ToolRegistry registry;
    //在代码中增加 MemoryMXBean 监控  自动熔断
    /**
     * 处理多张图片 + 文本
     */
    @Override
    public String chatWithMultipleFiles(MultimodalChatRequest request) {

        String sessionId = request.getSessionId();
        // 获取记忆
        ChatMemory memory = memoryProvider.get(sessionId);

        List<Content> currentContents = new ArrayList<>();
        List<ChatMessage> allMessages = new ArrayList<>();


        // 解析附件，获取多模态内容和用于记忆的纯文本
        MultimodalContentResolver.ResolveResult resolveResult = contentResolver.resolve(sessionId, request.getText(), request.getAttachments());

        //将记忆用的图片imageMessage替换为文字标签 放入list ,解析后的文件 也放入列表,对于模型不支持的文件,转换为文字Message后放入list
        currentContents.add(TextContent.from(resolveResult.memoryText));
        currentContents.addAll(resolveResult.contentsForModel);
        UserMessage currentUserMsg = UserMessage.from(currentContents);

        // 使用 预设知识库 进行搜索
        List<dev.langchain4j.rag.content.Content> list= knowledgeBaseRagService.searchRelevantTexts(request.getText(),5,0.7);

        String systemPrompt= buildSystemPromptWithContent(list);
        SystemMessage systemMessage =SystemMessage.from(systemPrompt);

        // 内存 token审查
        //  合并历史消息（历史 + 当前）

        allMessages.add(systemMessage);
        allMessages.addAll(memory.messages());
        allMessages.add(currentUserMsg);
        //  调用模型（动态创建，支持多轮）
        ChatModel chatModel = chatModelFactory.createModel(request.getModelName(), request.getTemperature(), request.getMaxTokens());

        // 检查状态 1.思考
        // 2. 调用tool   则继续while循环
        // 生成的文本放入当前对话 不放入记忆管理
        // 只在循环结束后 选择性加入记忆或者进行摘要

        AgentContext agentContext=AgentContext.builder()
                .stepCount(0)
                .currentMessages(allMessages)
                .build();
        List<ToolSpecification> specs=  registry.allSpecs();
        while(agentContext.getStepCount()<5)
        {
            log.info("计入循环{}",agentContext.getStepCount());
            ChatRequest chatRequest = ChatRequest.builder()
                    .toolSpecifications(specs)
                    .messages(allMessages)
                    .build();
            AiMessage aiMessage2= chatModel.chat(chatRequest).aiMessage();
           if (aiMessage2.hasToolExecutionRequests()){

               log.info("执行工具:{}",aiMessage2.toolExecutionRequests());

               agentContext.getCurrentMessages().add(aiMessage2);
               //按指针理解,应该是成功add到list里面的
               log.info("输出当前信息{}",agentContext.getCurrentMessages());
               // agentContext.setCurrentMessages();
               // chat(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications)
               // 上下文过大记得摘要
               // ② 逐个执行工具
               for (ToolExecutionRequest toolReq : aiMessage2.toolExecutionRequests()) {
                   log.info("调用工具: {}  参数: {}", toolReq.name(), toolReq.arguments());

                   // ★ 执行工具，拿到结果
//                   String result = doExecute(toolReq);

//                   log.info("工具返回: {}", result);
//
//                   // ③ 把结果包装成消息，加回上下文
//                   allMessages.add(ToolExecutionResultMessage.from(toolReq, result));
               }
           }else{
               agentContext.setFinalResult(aiMessage2);
                break;
           }
           agentContext.addOneStepCount();
            // 对当前进行摘要
        }

        AiMessage aiMessage =agentContext.getFinalResult();
        //  AiMessage aiMessage = chatModel.chat(allMessages).aiMessage();
        //  更新记忆（只存纯文本摘要）
        //
        memory.add(UserMessage.from(resolveResult.memoryText));
        memory.add(aiMessage);

        return aiMessage.text();
    }

    @Override
    public DefaultChatStream chatWithMultipleFilesStreaming(MultimodalChatRequest request) {
        String sessionId = request.getSessionId();

        // 1. 获取记忆
        ChatMemory memory = memoryProvider.get(sessionId);

        // 2. 解析附件
        MultimodalContentResolver.ResolveResult resolveResult = contentResolver.resolve(
                sessionId, request.getText(), request.getAttachments());

        // 3. 构建当前用户消息 (多模态)
        List<Content> currentContents = new ArrayList<>();
        currentContents.add(TextContent.from(resolveResult.memoryText));
        currentContents.addAll(resolveResult.contentsForModel);
        UserMessage currentUserMsg = UserMessage.from(currentContents);

        // 4. 合并历史消息
        List<ChatMessage> allMessages = new ArrayList<>(memory.messages());
        allMessages.add(currentUserMsg);

        // 5. 创建流式模型
        StreamingChatModel streamingModel = chatModelFactory.createStreamingModel(
                request.getModelName(), request.getTemperature(), request.getMaxTokens());

        // 6. 返回封装好的 Stream 对象 (将复杂的回调逻辑交给 DefaultChatStream 处理)
        return new DefaultChatStream(
                streamingModel,
                allMessages,
                memory,
                resolveResult.memoryText
        );
    }

    public String chatSimple(MultimodalChatRequest request){




        return "";
    }
    private String buildSystemPromptWithContent(List<dev.langchain4j.rag.content.Content> contents) {
        if (contents == null || contents.isEmpty()) {
            return """
                你是一个智能助手，请根据你的知识回答用户问题。
                如果用户的问题超出了你的知识范围，请如实告知。
                """;
        }

        // 构建结构化的参考资料文本
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("以下是与你问题相关的参考资料，请基于这些信息回答：\n\n");

        for (int i = 0; i < contents.size(); i++) {
            dev.langchain4j.rag.content.Content content = contents.get(i);
            TextSegment segment = content.textSegment();
            String text = segment.text();
            Metadata metadata = segment.metadata();

            // 格式化每条参考信息
            contextBuilder.append("【参考资料 ").append(i + 1).append("】\n");

            // 如果有元数据（如来源），优先展示
            String source = metadata != null ? metadata.getString("source") : null;
            if (source != null && !source.isEmpty()) {
                contextBuilder.append("来源：").append(source).append("\n");
            }

            // 如果有重排分数，展示（仅用于调试，可以不展示给模型）
            // 生产环境建议不展示分数，避免模型纠结于数字
            // contextBuilder.append("相关度：").append(String.format("%.2f", score)).append("\n");

            contextBuilder.append("内容：").append(text).append("\n\n");
        }

        // 添加使用说明
        contextBuilder.append("请严格基于以上参考资料回答用户问题。");
        contextBuilder.append("如果参考资料中没有明确答案，请告诉用户未找到相关信息，不要编造。");

        return contextBuilder.toString();
    }
}
