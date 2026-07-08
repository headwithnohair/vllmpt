package org.albedo.vllmpt.module.chat.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.module.chat.service.FileParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class FileParserRegistry {
    private final Map<String, FileParser> parserMap = new HashMap<>();
    private final List<String> supportSet = List.of("text/plain", "text/markdown");

    //可以考虑PostConstruct 注解
    @Autowired
    public void  init(List<FileParser> parserList){

        for (FileParser fileParser : parserList)
        {
            if (supportSet.contains(fileParser.getSupportTypes())){
                parserMap.put(fileParser.getSupportTypes(),fileParser);
            }
        }
    }

    public FileParser getParser(String type) {
        FileParser processor = parserMap.get(type);
        if (processor == null) {
            throw new Error("Unsupported type: " + type);
        }
        return processor;
    }
}
