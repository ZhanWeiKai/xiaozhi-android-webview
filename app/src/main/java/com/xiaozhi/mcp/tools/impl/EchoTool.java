package com.xiaozhi.mcp.tools.impl;

import com.xiaozhi.mcp.tools.Tool;

import java.util.HashMap;
import java.util.Map;

/**
 * Echo 工具 - 回显输入内容
 */
public class EchoTool implements Tool {

    @Override
    public String getName() {
        return "echo";
    }

    @Override
    public String getDescription() {
        return "回显用户输入的内容，用于测试 MCP 工具调用";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> textProp = new HashMap<>();
        textProp.put("type", "string");
        textProp.put("description", "要回显的文本内容");
        properties.put("text", textProp);

        schema.put("properties", properties);
        schema.put("required", java.util.Collections.singletonList("text"));

        return schema;
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        String text = (String) arguments.get("text");

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("echo", text);
        result.put("length", text != null ? text.length() : 0);
        result.put("timestamp", System.currentTimeMillis());

        return result;
    }
}
