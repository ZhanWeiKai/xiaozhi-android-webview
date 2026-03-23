package com.xiaozhi.mcp;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

/**
 * MCP 事件广播工具类
 * 用于 Service 和 Activity 之间的通信
 */
public class McpEvents {

    // Action 常量
    public static final String ACTION_MCP_STATUS = "com.xiaozhi.mcp.STATUS";
    public static final String ACTION_MCP_TOOL_CALL = "com.xiaozhi.mcp.TOOL_CALL";
    public static final String ACTION_MCP_LOG = "com.xiaozhi.mcp.LOG";

    // Extra 常量
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_TOOL_NAME = "tool_name";
    public static final String EXTRA_TOOL_ARGS = "tool_args";
    public static final String EXTRA_TOOL_RESULT = "tool_result";
    public static final String EXTRA_LOG_MESSAGE = "log_message";
    public static final String EXTRA_TIMESTAMP = "timestamp";

    // 状态常量
    public static final int STATUS_DISCONNECTED = 0;
    public static final int STATUS_CONNECTING = 1;
    public static final int STATUS_CONNECTED = 2;
    public static final int STATUS_ERROR = 3;
    public static final int STATUS_DISCONNECTING = 4;

    /**
     * 发送状态更新广播
     */
    public static void broadcastStatus(Context context, int status, String message) {
        android.util.Log.d("McpEvents", "广播状态: status=" + status + ", message=" + message);
        Intent intent = new Intent(ACTION_MCP_STATUS);
        intent.putExtra(EXTRA_STATUS, status);
        intent.putExtra(EXTRA_LOG_MESSAGE, message);
        intent.putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis());
        intent.setPackage(context.getPackageName()); // 显式设置包名，确保广播能被接收
        context.sendBroadcast(intent);
    }

    /**
     * 发送工具调用广播
     */
    public static void broadcastToolCall(Context context, String toolName, String args, String result) {
        android.util.Log.d("McpEvents", "广播工具调用: tool=" + toolName);
        Intent intent = new Intent(ACTION_MCP_TOOL_CALL);
        intent.putExtra(EXTRA_TOOL_NAME, toolName);
        intent.putExtra(EXTRA_TOOL_ARGS, args);
        intent.putExtra(EXTRA_TOOL_RESULT, result);
        intent.putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis());
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    /**
     * 发送日志广播
     */
    public static void broadcastLog(Context context, String message) {
        android.util.Log.d("McpEvents", "广播日志: " + message);
        Intent intent = new Intent(ACTION_MCP_LOG);
        intent.putExtra(EXTRA_LOG_MESSAGE, message);
        intent.putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis());
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    /**
     * 创建 IntentFilter
     */
    public static IntentFilter createIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_MCP_STATUS);
        filter.addAction(ACTION_MCP_TOOL_CALL);
        filter.addAction(ACTION_MCP_LOG);
        return filter;
    }

    /**
     * 获取状态文本
     */
    public static String getStatusText(int status) {
        switch (status) {
            case STATUS_DISCONNECTED:
                return "未连接";
            case STATUS_CONNECTING:
                return "连接中...";
            case STATUS_CONNECTED:
                return "已连接";
            case STATUS_ERROR:
                return "连接错误";
            case STATUS_DISCONNECTING:
                return "断开中...";
            default:
                return "未知";
        }
    }
}
