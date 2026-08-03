package org.albedo.vllmpt.module.chat.tool;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class ToolRegistry {


    private final Map<String,Object> toolInstances =new LinkedHashMap<>();

    private final  Map<String, Set<String>> groupIndex = new LinkedHashMap<>();
}
