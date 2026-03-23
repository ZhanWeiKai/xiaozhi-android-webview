package com.xiaozhi.mcp.tools;

import java.util.Map;

/**
 * MCP 工具接口
 */
public interface Tool {
    /**
     * 获取工具名称
     */
    String getName();

    /**
     * 获取工具描述
     */
    String getDescription();

    /**
     * 获取输入参数的JSON Schema
     */
    Map<String, Object> getInputSchema();

    /**
     * 执行工具
     *
     * @param arguments 工具参数
     * @return 执行结果
     */
    Object execute(Map<String, Object> arguments);
}
