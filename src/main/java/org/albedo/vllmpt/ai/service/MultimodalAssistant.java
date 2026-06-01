package org.albedo.vllmpt.ai.service;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 多模态助手（支持图片+文本）
 */
@Service
@Slf4j
public class MultimodalAssistant {

    @Autowired
    private ChatLanguageModel chatModel;

    public String chatWithImage(String text,String imageUrl){
        log.info("处理多模态请求 - 文本: {}, 图片: {}", text, imageUrl);
        UserMessage message = UserMessage.from(
                TextContent.from(text),
                ImageContent.from(imageUrl)
        );
        String response = chatModel.generate(message).content().text();

        log.info("多模态响应完成");
        return response;
    }

    /**
     * 处理多张图片 + 文本
     */
    public String chatWithMultipleImages(String text, List<String> imageUrls) {
        TextContent textContent = TextContent.from(text);

        // 2. 将所有图片URL或Base64数据转换为ImageContent对象列表
        // 你可以混合使用URL和Base64数据，只要每个ImageContent都能正确构建即可。
        List<ImageContent> imageContentList = imageUrls.stream()
                .map(ImageContent::from)      // 假设图片是URL格式，直接转换
                .toList();

        // 3. 将所有Content（文本+图片）组合成一个列表
        List<Content> contents = new ArrayList<>();
        contents.add(textContent);
        contents.addAll(imageContentList);

        // 4. 用内容列表创建UserMessage
        UserMessage userMessage = UserMessage.from(contents);

        // 5. 发送消息并获取回复
        String response = chatModel.generate(userMessage).content().text();

        log.info("多模态响应完成");
        return response;
    }
}