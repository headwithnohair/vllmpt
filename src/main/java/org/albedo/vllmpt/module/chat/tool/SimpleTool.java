package org.albedo.vllmpt.module.chat.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class SimpleTool implements AiTools {

        @Tool("兜底回复工具。当用户的输入是纯问候语（如你好、嗨）、感谢语，或无法归类到其他业务工具时，调用此工具返回通用文案")
    public String  getAnswer(){

        return "您好，有什么可以帮您的？";
    }

    @Tool("查询中国内地任意城市的实时天气状况，返回温度、湿度、风速和出行指数。")
    public String  getWeather(@P("必填。城市的中文全称（例如：'广州市'、'杭州市'）。" +
            "请勿使用简称或英文。若用户只说了'天气'没提城市，" +
            "必须反问用户获取城市名。")
                                  String place){

        return "晴天26度微风湿度50%适合出行";
    }
}
