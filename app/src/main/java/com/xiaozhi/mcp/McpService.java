package com.xiaozhi.mcp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.xiaozhi.webview.R;
import com.xiaozhi.mcp.client.McpConnection;

/**
 * MCP 后台服务
 * - 管理 MCP 连接生命周期
 * - 作为前台服务保持连接稳定
 */
public class McpService extends Service {
    private static final String TAG = "McpService";
    private static final String CHANNEL_ID = "mcp_service_channel";
    private static final int NOTIFICATION_ID = 1001;

    // 默认配置
    private static final String DEFAULT_ENDPOINT = "ws://100.69.157.38:8004/mcp_endpoint/mcp/";
    private static final String DEFAULT_TOKEN = "1qWkfjXARUCYLy0dlScvSf1756PTsRiK3Om23or3TAZ8OV8VODOYdkg3Ozig3F8p";

    private static final String PREFS_NAME = "mcp_config";
    private static final String KEY_ENDPOINT = "endpoint";
    private static final String KEY_TOKEN = "token";

    private McpConnection mcpConnection;

    public static void start(Context context) {
        Intent intent = new Intent(context, McpService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, McpService.class);
        context.stopService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "MCP Service onCreate");
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification("正在连接..."));

        // 初始化 MCP 连接
        String endpoint = getConfig(KEY_ENDPOINT, DEFAULT_ENDPOINT);
        String token = getConfig(KEY_TOKEN, DEFAULT_TOKEN);
        String fullUrl = endpoint + "?token=" + token;

        mcpConnection = new McpConnection(this, fullUrl);
        mcpConnection.connect();

        // 更新通知
        updateNotification("MCP 已连接");
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "MCP Service onDestroy");
        if (mcpConnection != null) {
            mcpConnection.close();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "MCP 服务",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("小智 MCP 连接服务");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification(String contentText) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("小智服务运行中")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build();
    }

    private void updateNotification(String contentText) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification(contentText));
        }
    }

    private String getConfig(String key, String defaultValue) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getString(key, defaultValue);
    }

    public static void saveConfig(Context context, String endpoint, String token) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
            .putString(KEY_ENDPOINT, endpoint)
            .putString(KEY_TOKEN, token)
            .apply();
    }

    /**
     * 检查服务是否运行中
     */
    public boolean isRunning() {
        return mcpConnection != null && mcpConnection.isRunning();
    }
}
