package org.albedo.vllmpt.module.chat.model.dto;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AgentContext {

    List<ChatMessage> currentMessages;
    AiMessage finalResult;
    int stepCount;
    String toolExecutor; // 用来执行大模型要求的工具



    public  int  addOneStepCount(){
        return   ++this.stepCount;
    }
    public  List<ChatMessage>  addMessage(ChatMessage message){
        this.currentMessages.add(message);

        return  this.currentMessages;
    }
}
