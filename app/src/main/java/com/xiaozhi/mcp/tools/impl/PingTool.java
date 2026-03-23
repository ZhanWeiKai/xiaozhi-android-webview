package com.xiaozhi.mcp.tools.impl;

import com.xiaozhi.mcp.tools.Tool;

import java.util.HashMap;
import java.util.Map;

/**
 * Ping 工具 - 用于测试 MCP 连接
 */
public class PingTool implements Tool {

    @Override
    public String getName() {
        return "ping";
    }

    @Override
    public String getDescription() {
        return "测试 MCP 连接是否正常，返回 pong 响应";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> messageProp = new HashMap<>();
        messageProp.put("type", "string");
        messageProp.put("description", "可选的消息内容，将原样返回");
        properties.put("message", messageProp);

        schema.put("properties", properties);

        return schema;
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        String message = arguments.containsKey("message")
            ? (String) arguments.get("message")
            : null;

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("response", "pong");
        result.put("timestamp", System.currentTimeMillis());

        if (message != null) {
            result.put("echo", message);
        }

        return result;
    }
}
