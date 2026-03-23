package com.xiaozhi.mcp.client;

import android.util.Log;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * MCP WebSocket 客户端 (Android 版本，基于 OkHttp)
 */
public class McpWebSocketClient {
    private static final String TAG = "McpWebSocketClient";

    private final String serverUrl;
    private final Callback callback;
    private OkHttpClient client;
    private WebSocket webSocket;
    private volatile boolean connected = false;

    public interface Callback {
        void onConnected();
        void onDisconnected(String reason);
        void onMessage(String message);
        void onError(Exception ex);
    }

    public McpWebSocketClient(String serverUrl, Callback callback) {
        this.serverUrl = serverUrl;
        this.callback = callback;
    }

    /**
     * 连接到服务器
     */
    public void connect() {
        Log.d(TAG, "正在连接到: " + maskToken(serverUrl));

        client = new OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build();

        Request request = new Request.Builder()
            .url(serverUrl)
            .build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                Log.d(TAG, "WebSocket 已打开");
                connected = true;
                if (callback != null) {
                    callback.onConnected();
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                Log.d(TAG, "收到消息: " + text);
                if (callback != null) {
                    callback.onMessage(text);
                }
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "WebSocket 正在关闭: " + code + " - " + reason);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "WebSocket 已关闭: " + code + " - " + reason);
                connected = false;
                if (callback != null) {
                    callback.onDisconnected(reason);
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e(TAG, "WebSocket 失败: " + t.getMessage(), t);
                connected = false;
                if (callback != null) {
                    callback.onError(new Exception(t));
                }
            }
        });
    }

    /**
     * 发送消息
     */
    public boolean send(String message) {
        if (webSocket != null && connected) {
            Log.d(TAG, "发送消息: " + message);
            return webSocket.send(message);
        }
        Log.w(TAG, "无法发送消息，WebSocket 未连接");
        return false;
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "Client disconnecting");
            webSocket = null;
        }
        if (client != null) {
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
        }
        connected = false;
    }

    /**
     * 检查是否已连接
     */
    public boolean isConnected() {
        return connected;
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
