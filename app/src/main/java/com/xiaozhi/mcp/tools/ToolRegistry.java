package com.xiaozhi.mcp.tools;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册表
 */
public class ToolRegistry {
    private static final String TAG = "ToolRegistry";

    private final Map<String, Tool> tools = new ConcurrentHashMap<>();

    /**
     * 注册工具
     */
    public void register(Tool tool) {
        tools.put(tool.getName(), tool);
        Log.i(TAG, "工具已注册: " + tool.getName() + " - " + tool.getDescription());
    }

    /**
     * 获取工具
     */
    public Tool getTool(String name) {
        return tools.get(name);
    }

    /**
     * 获取所有工具
     */
    public List<Tool> getAllTools() {
        return new ArrayList<>(tools.values());
    }

    /**
     * 检查工具是否存在
     */
    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }

    /**
     * 执行工具
     */
    public Object executeTool(String name, Map<String, Object> arguments) {
        Tool tool = getTool(name);
        if (tool == null) {
            throw new IllegalArgumentException("工具不存在: " + name);
        }

        Log.i(TAG, "执行工具: " + name + " 参数: " + arguments);
        try {
            long startTime = System.currentTimeMillis();
            Object result = tool.execute(arguments);
            long duration = System.currentTimeMillis() - startTime;
            Log.i(TAG, "工具执行完成: " + name + " 耗时: " + duration + "ms");
            return result;
        } catch (Exception e) {
            Log.e(TAG, "工具执行失败: " + name, e);
            throw new RuntimeException("工具执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取工具数量
     */
    public int getToolCount() {
        return tools.size();
    }

    /**
     * 获取所有工具名称
     */
    public Set<String> getToolNames() {
        return new HashSet<>(tools.keySet());
    }
}
