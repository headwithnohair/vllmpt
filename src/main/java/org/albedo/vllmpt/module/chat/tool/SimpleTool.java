package org.albedo.vllmpt.module.chat.tool;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class SimpleTool implements AiTools {

    @Tool("获取简单回答")
    public String  getAnswer(){

        return "简单回答";
    }
}
