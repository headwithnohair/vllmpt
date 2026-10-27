package org.albedo.vllmpt.module.chat.order.stages.chatStage;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.albedo.vllmpt.common.security.UserContextHolder;
import org.albedo.vllmpt.core.order.pipeline.ChatPipelineContext;
import org.albedo.vllmpt.module.ai.service.ChatModelFactory;
import org.albedo.vllmpt.module.chat.tool.ToolContextHolder;
import org.albedo.vllmpt.module.chat.tool.ToolRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatAgentStageTest {

    @AfterEach
    void tearDown() {
        ToolContextHolder.clear();
        UserContextHolder.clear();
    }

    @Test
    void approvalSceneUsesApprovalGroupAndClearsContext() throws Exception {
        ChatModelFactory factory = mock(ChatModelFactory.class);
        ToolRegistry registry = mock(ToolRegistry.class);
        ChatModel model = mock(ChatModel.class);
        ChatResponse response = mock(ChatResponse.class);
        AiMessage aiMessage = mock(AiMessage.class);

        when(factory.createModel(anyString(), any(), any())).thenReturn(model);
        when(registry.specsByGroup("approval")).thenReturn(List.of(mock(ToolSpecification.class)));
        when(model.chat(any(ChatRequest.class))).thenReturn(response);
        when(response.aiMessage()).thenReturn(aiMessage);
        when(response.tokenUsage()).thenReturn(null);
        when(aiMessage.hasToolExecutionRequests()).thenReturn(false);

        ChatAgentStage stage = new ChatAgentStage();
        setField(stage, "chatModelFactory", factory);
        setField(stage, "toolRegistry", registry);

        ChatPipelineContext context = new ChatPipelineContext();
        context.setModelName("test-model");
        context.setToolGroup("approval");
        context.setUserId(1L);
        context.setSessionId("s1");
        context.setBizType("LEAVE");
        context.setAllMessages(List.of(SystemMessage.from("sys"), UserMessage.from("hi")));

        stage.execute(context);

        assertEquals(aiMessage, context.getFinalResult());
        verify(registry).specsByGroup("approval");
        verify(registry, never()).allSpecs();
        assertNull(ToolContextHolder.get());
        assertNull(UserContextHolder.getUserId());
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
