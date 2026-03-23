package com.xiaozhi.mcp.client;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xiaozhi.mcp.McpEvents;
import com.xiaozhi.mcp.protocol.JsonRpcError;
import com.xiaozhi.mcp.protocol.JsonRpcResponse;
import com.xiaozhi.mcp.tools.Tool;
import com.xiaozhi.mcp.tools.ToolRegistry;
import com.xiaozhi.mcp.tools.impl.DeviceInfoTool;
import com.xiaozhi.mcp.tools.impl.EchoTool;
import com.xiaozhi.mcp.tools.impl.PingTool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 连接管理器
 * 管理 WebSocket 连接和 MCP 协议处理
 */
public class McpConnection {
    private static final String TAG = "McpConnection";

    private final Context context;
    private final String serverUrl;
    private final ToolRegistry toolRegistry;
    private McpWebSocketClient wsClient;
    private final Gson gson;
    private volatile boolean running = false;

    public McpConnection(Context context, String serverUrl) {
        this.context = context;
        this.serverUrl = serverUrl;
        this.toolRegistry = new ToolRegistry();
        this.gson = new Gson();

        // 注册默认工具
        registerDefaultTools();
    }

    /**
     * 连接到 MCP 服务器
     */
    public void connect() {
        Log.i(TAG, "正在连接到 MCP 服务器: " + maskToken(serverUrl));
        broadcastLog("正在连接到 MCP 服务器...");
        McpEvents.broadcastStatus(context, McpEvents.STATUS_CONNECTING, "正在连接...");

        wsClient = new McpWebSocketClient(serverUrl, new McpWebSocketClient.Callback() {
            @Override
            public void onConnected() {
                Log.i(TAG, "WebSocket 已连接，等待服务器初始化请求");
                broadcastLog("WebSocket 已连接");
                running = true;
            }

            @Override
            public void onDisconnected(String reason) {
                Log.w(TAG, "WebSocket 已断开: " + reason);
                broadcastLog("连接断开: " + reason);
                running = false;
                McpEvents.broadcastStatus(context, McpEvents.STATUS_DISCONNECTED, "连接断开: " + reason);
            }

            @Override
            public void onMessage(String message) {
                handleMessage(message);
            }

            @Override
            public void onError(Exception ex) {
                Log.e(TAG, "WebSocket 错误", ex);
                broadcastLog("连接错误: " + ex.getMessage());
                McpEvents.broadcastStatus(context, McpEvents.STATUS_ERROR, "错误: " + ex.getMessage());
            }
        });

        wsClient.connect();
    }

    /**
     * 处理收到的消息
     */
    private void handleMessage(String message) {
        try {
            Log.d(TAG, "处理消息: " + message);

            JsonObject root = JsonParser.parseString(message).getAsJsonObject();

            // 检查是否是响应（有 result 或 error 字段且有 id）
            if (root.has("id") && (root.has("result") || root.has("error"))) {
                handleResponse(root);
            }
            // 检查是否是请求（有 method 字段）
            else if (root.has("method")) {
                handleRequest(root);
            } else {
                Log.w(TAG, "未知的消息格式: " + message);
            }

        } catch (Exception e) {
            Log.e(TAG, "处理消息失败: " + message, e);
        }
    }

    /**
     * 处理响应
     */
    private void handleResponse(JsonObject root) {
        String id = root.get("id").getAsString();
        Log.d(TAG, "收到响应: id=" + id);
        // 响应处理逻辑（如果需要处理异步响应）
    }

    /**
     * 处理请求
     */
    private void handleRequest(JsonObject root) {
        try {
            String method = root.get("method").getAsString();
            String id = root.has("id") && !root.get("id").isJsonNull()
                ? root.get("id").getAsString()
                : null;

            Log.i(TAG, "处理请求: method=" + method + ", id=" + id);
            broadcastLog("收到请求: " + method);

            switch (method) {
                case "initialize":
                    handleInitialize(id, root);
                    break;

                case "tools/list":
                    handleToolsList(id);
                    break;

                case "tools/call":
                    handleToolsCall(id, root);
                    break;

                case "notifications/initialized":
                    // 服务器初始化完成通知，无需响应
                    Log.i(TAG, "收到 initialized 通知");
                    broadcastLog("MCP 初始化完成");
                    McpEvents.broadcastStatus(context, McpEvents.STATUS_CONNECTED, "已连接");
                    break;

                default:
                    Log.w(TAG, "未知的方法: " + method);
                    if (id != null) {
                        sendError(id, JsonRpcError.METHOD_NOT_FOUND, "未知的方法: " + method);
                    }
            }

        } catch (Exception e) {
            Log.e(TAG, "处理请求失败", e);
        }
    }

    /**
     * 处理 initialize 请求
     */
    private void handleInitialize(String requestId, JsonObject requestRoot) {
        try {
            Log.i(TAG, "处理 initialize 请求");
            broadcastLog("处理 initialize 请求");

            // 构建响应
            Map<String, Object> serverInfo = new HashMap<>();
            serverInfo.put("name", "android-mcp-client");
            serverInfo.put("version", "1.0.0");

            Map<String, Object> capabilities = new HashMap<>();

            Map<String, Object> result = new HashMap<>();
            result.put("protocolVersion", "2024-11-05");
            result.put("capabilities", capabilities);
            result.put("serverInfo", serverInfo);

            sendResponse(requestId, result);

            Log.i(TAG, "initialize 响应已发送");
            broadcastLog("initialize 响应已发送");

            // 更新状态为已连接
            McpEvents.broadcastStatus(context, McpEvents.STATUS_CONNECTED, "已连接");

            // 发送 initialized 通知
            sendInitializedNotification();

        } catch (Exception e) {
            Log.e(TAG, "处理 initialize 失败", e);
            sendError(requestId, JsonRpcError.INTERNAL_ERROR, e.getMessage());
        }
    }

    /**
     * 发送初始化通知（MCP 协议要求）
     */
    private void sendInitializedNotification() {
        try {
            String message = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";
            wsClient.send(message);
            Log.i(TAG, "initialized 通知已发送");
            broadcastLog("initialized 通知已发送");
        } catch (Exception e) {
            Log.e(TAG, "发送 initialized 通知失败", e);
        }
    }

    /**
     * 处理 tools/list 请求
     */
    private void handleToolsList(String requestId) {
        try {
            List<Tool> tools = toolRegistry.getAllTools();

            List<Map<String, Object>> toolsJson = new ArrayList<>();
            for (Tool tool : tools) {
                toolsJson.add(toolToJson(tool));
            }

            Map<String, Object> result = new HashMap<>();
            result.put("tools", toolsJson);

            sendResponse(requestId, result);

            Log.i(TAG, "已返回工具列表: " + tools.size() + " 个工具");
            broadcastLog("返回工具列表: " + tools.size() + " 个");

        } catch (Exception e) {
            Log.e(TAG, "处理 tools/list 失败", e);
            sendError(requestId, JsonRpcError.INTERNAL_ERROR, e.getMessage());
        }
    }

    /**
     * 处理 tools/call 请求
     */
    private void handleToolsCall(String requestId, JsonObject requestRoot) {
        try {
            JsonObject paramsNode = requestRoot.getAsJsonObject("params");
            String toolName = paramsNode.get("name").getAsString();

            // 解析 arguments
            Map<String, Object> arguments = new HashMap<>();
            if (paramsNode.has("arguments") && paramsNode.get("arguments").isJsonObject()) {
                JsonObject argsNode = paramsNode.getAsJsonObject("arguments");
                for (Map.Entry<String, JsonElement> entry : argsNode.entrySet()) {
                    arguments.put(entry.getKey(), convertJsonElementToObject(entry.getValue()));
                }
            }

            Log.i(TAG, "调用工具: " + toolName + " 参数: " + arguments);
            broadcastLog("调用工具: " + toolName);

            // 执行工具
            Object toolResult = toolRegistry.executeTool(toolName, arguments);
            String resultJson = gson.toJson(toolResult);

            // 构建响应
            Map<String, Object> contentItem = new HashMap<>();
            contentItem.put("type", "text");
            contentItem.put("text", resultJson);

            Map<String, Object> result = new HashMap<>();
            result.put("content", Collections.singletonList(contentItem));

            sendResponse(requestId, result);

            Log.i(TAG, "工具调用成功: " + toolName);
            broadcastLog("工具调用成功: " + toolName);

            // 广播工具调用事件给 UI
            McpEvents.broadcastToolCall(context, toolName, gson.toJson(arguments), resultJson);

        } catch (IllegalArgumentException e) {
            Log.e(TAG, "工具不存在", e);
            sendError(requestId, JsonRpcError.METHOD_NOT_FOUND, e.getMessage());
            McpEvents.broadcastToolCall(context, "ERROR", "", e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "工具执行失败", e);
            sendError(requestId, JsonRpcError.INTERNAL_ERROR, "工具执行失败: " + e.getMessage());
            McpEvents.broadcastToolCall(context, "ERROR", "", "执行失败: " + e.getMessage());
        }
    }

    /**
     * 发送响应
     */
    private void sendResponse(String requestId, Object result) {
        JsonRpcResponse response = JsonRpcResponse.success(requestId, result);
        String message = gson.toJson(response);
        wsClient.send(message);
        Log.d(TAG, "发送响应: " + message);
    }

    /**
     * 发送错误
     */
    private void sendError(String requestId, int code, String message) {
        JsonRpcResponse response = JsonRpcResponse.error(requestId, code, message);
        String json = gson.toJson(response);
        wsClient.send(json);
        Log.d(TAG, "发送错误: " + json);
    }

    /**
     * 将工具转换为 JSON 格式
     */
    private Map<String, Object> toolToJson(Tool tool) {
        Map<String, Object> toolJson = new HashMap<>();
        toolJson.put("name", tool.getName());
        toolJson.put("description", tool.getDescription());
        toolJson.put("inputSchema", tool.getInputSchema());
        return toolJson;
    }

    /**
     * 注册默认工具
     */
    private void registerDefaultTools() {
        Log.i(TAG, "注册默认工具...");
        broadcastLog("注册 MCP 工具...");

        toolRegistry.register(new PingTool());
        toolRegistry.register(new EchoTool());
        toolRegistry.register(new DeviceInfoTool());

        Log.i(TAG, "默认工具注册完成，共 " + toolRegistry.getToolCount() + " 个工具");
        broadcastLog("已注册 " + toolRegistry.getToolCount() + " 个工具");
    }

    /**
     * 关闭连接
     */
    public void close() {
        Log.i(TAG, "关闭 MCP 连接");
        broadcastLog("关闭 MCP 连接");
        running = false;
        if (wsClient != null) {
            wsClient.disconnect();
        }
    }

    /**
     * 检查是否运行中
     */
    public boolean isRunning() {
        return running && wsClient != null && wsClient.isConnected();
    }

    /**
     * 获取工具注册表（用于外部注册工具）
     */
    public ToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    /**
     * 广播日志
     */
    private void broadcastLog(String message) {
        McpEvents.broadcastLog(context, message);
    }

    /**
     * 辅助方法：将 JsonElement 转换为 Java 对象
     */
    private Object convertJsonElementToObject(JsonElement element) {
        if (element.isJsonNull()) {
            return null;
        } else if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isString()) {
                return element.getAsString();
            } else if (element.getAsJsonPrimitive().isNumber()) {
                return element.getAsNumber();
            } else if (element.getAsJsonPrimitive().isBoolean()) {
                return element.getAsBoolean();
            }
        } else if (element.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonElement item : element.getAsJsonArray()) {
                list.add(convertJsonElementToObject(item));
            }
            return list;
        } else if (element.isJsonObject()) {
            Map<String, Object> map = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                map.put(entry.getKey(), convertJsonElementToObject(entry.getValue()));
            }
            return map;
        }
        return element.toString();
    }

    /**
     * 隐藏 token 用于日志
     */
    private String maskToken(String url) {
        if (url == null) return "null";
        int idx = url.indexOf("token=");
        return idx >= 0 ? url.substring(0, idx) + "token=***" : url;
    }
}
