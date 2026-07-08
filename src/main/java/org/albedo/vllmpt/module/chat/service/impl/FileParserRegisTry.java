package org.albedo.vllmpt.module.chat.service.impl;

import org.albedo.vllmpt.module.chat.service.FileParser;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileParserRegisTry {
    private final Map<String, FileParser> parserMap = new HashMap<>();
    private final List<String> supportSet = List.of("text/plain", "text/markdown");


    @Autowired
    public void  parserRegisTry(List<FileParser> parserList){




    }

}
