package com.xiaozhi.webview;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.xiaozhi.mcp.McpEvents;
import com.xiaozhi.mcp.McpService;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "XiaozhiWebView";
    private static final String URL_TEST = "https://xiaozhi-webtest.jamesweb.org/test_page.html";
    private static final String URL_WEBUI = "https://xiaozhi.jamesweb.org/";
    private static final String URL_DEFAULT = URL_TEST;

    // Permission request codes
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int PERMISSION_REQUEST_AUDIO = 1002;
    private static final int PERMISSION_REQUEST_STORAGE = 1003;

    private WebView webView;
    private ProgressBar progressBar;
    private View errorView;

    // MCP Status UI
    private View mcpStatusIndicator;
    private TextView mcpStatusText;
    private TextView mcpToggleBtn;
    private LinearLayout mcpDetailPanel;
    private TextView mcpLastTool;
    private TextView mcpLastResult;
    private TextView mcpLog;
    private boolean mcpDetailExpanded = false;
    private StringBuilder mcpLogBuilder = new StringBuilder();

    // Store pending permission request from WebView
    private PermissionRequest pendingPermissionRequest;

    // URL switch buttons
    private TextView btnTest;
    private TextView btnWebui;
    private String currentUrl;

    // MCP 事件接收器
    private BroadcastReceiver mcpEventReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initMcpViews();
        checkAndRequestPermissions();
        setupWebView();

        // 启动 MCP 服务
        startMcpService();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // 注册 MCP 事件接收器
        registerMcpEventReceiver();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 注销 MCP 事件接收器
        unregisterMcpEventReceiver();
    }

    /**
     * 启动 MCP 服务
     */
    private void startMcpService() {
        Log.i(TAG, "启动 MCP 服务");
        McpService.start(this);
    }

    private void initViews() {
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        errorView = findViewById(R.id.errorView);

        // Setup error view retry button
        errorView.findViewById(R.id.retryButton).setOnClickListener(v -> {
            errorView.setVisibility(View.GONE);
            loadUrl(currentUrl);
        });

        // Setup URL switch buttons
        btnTest = findViewById(R.id.btnTest);
        btnWebui = findViewById(R.id.btnWebui);
        currentUrl = URL_DEFAULT;

        btnTest.setOnClickListener(v -> {
            currentUrl = URL_TEST;
            updateSwitchButtons(true);
            loadUrl(URL_TEST);
        });

        btnWebui.setOnClickListener(v -> {
            currentUrl = URL_WEBUI;
            updateSwitchButtons(false);
            loadUrl(URL_WEBUI);
        });

        // Setup clear cache button
        TextView btnClear = findViewById(R.id.btnClear);
        btnClear.setOnClickListener(v -> {
            webView.clearCache(true);
            webView.clearHistory();
            webView.clearFormData();
            android.webkit.WebStorage.getInstance().deleteAllData();
            android.webkit.CookieManager.getInstance().removeAllCookies(null);
            Toast.makeText(this, "缓存已清除", Toast.LENGTH_SHORT).show();
            loadUrl(currentUrl);
        });

        // Setup refresh button
        TextView btnRefresh = findViewById(R.id.btnRefresh);
        btnRefresh.setOnClickListener(v -> webView.reload());

        // Set initial active state
        updateSwitchButtons(true);
    }

    /**
     * 初始化 MCP 状态 UI
     */
    private void initMcpViews() {
        mcpStatusIndicator = findViewById(R.id.mcpStatusIndicator);
        mcpStatusText = findViewById(R.id.mcpStatusText);
        mcpToggleBtn = findViewById(R.id.mcpToggleBtn);
        mcpDetailPanel = findViewById(R.id.mcpDetailPanel);
        mcpLastTool = findViewById(R.id.mcpLastTool);
        mcpLastResult = findViewById(R.id.mcpLastResult);
        mcpLog = findViewById(R.id.mcpLog);

        // 展开/收起详情面板
        View toggleArea = findViewById(R.id.mcpStatusPanel);
        toggleArea.setOnClickListener(v -> {
            mcpDetailExpanded = !mcpDetailExpanded;
            mcpDetailPanel.setVisibility(mcpDetailExpanded ? View.VISIBLE : View.GONE);
            mcpToggleBtn.setText(mcpDetailExpanded ? "收起" : "展开");
        });
    }

    /**
     * 注册 MCP 事件接收器
     */
    private void registerMcpEventReceiver() {
        if (mcpEventReceiver != null) return;

        Log.d(TAG, "注册 MCP 事件接收器");
        mcpEventReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                Log.d(TAG, "收到广播: action=" + action);
                if (action == null) return;

                switch (action) {
                    case McpEvents.ACTION_MCP_STATUS:
                        handleMcpStatus(intent);
                        break;
                    case McpEvents.ACTION_MCP_TOOL_CALL:
                        handleMcpToolCall(intent);
                        break;
                    case McpEvents.ACTION_MCP_LOG:
                        handleMcpLog(intent);
                        break;
                }
            }
        };

        registerReceiver(mcpEventReceiver, McpEvents.createIntentFilter(), Context.RECEIVER_NOT_EXPORTED);
        Log.d(TAG, "MCP 事件接收器已注册");
    }

    /**
     * 注销 MCP 事件接收器
     */
    private void unregisterMcpEventReceiver() {
        if (mcpEventReceiver != null) {
            unregisterReceiver(mcpEventReceiver);
            mcpEventReceiver = null;
        }
    }

    /**
     * 处理 MCP 状态更新
     */
    private void handleMcpStatus(Intent intent) {
        int status = intent.getIntExtra(McpEvents.EXTRA_STATUS, McpEvents.STATUS_DISCONNECTED);
        String message = intent.getStringExtra(McpEvents.EXTRA_LOG_MESSAGE);
        Log.d(TAG, "处理 MCP 状态: status=" + status + ", message=" + message);

        runOnUiThread(() -> {
            Log.d(TAG, "更新 UI 状态: " + McpEvents.getStatusText(status));
            String statusText = "MCP: " + McpEvents.getStatusText(status);
            mcpStatusText.setText(statusText);

            // 更新状态指示器颜色
            int drawableRes;
            switch (status) {
                case McpEvents.STATUS_CONNECTED:
                    drawableRes = R.drawable.status_indicator_connected;
                    break;
                case McpEvents.STATUS_CONNECTING:
                    drawableRes = R.drawable.status_indicator_connecting;
                    break;
                default:
                    drawableRes = R.drawable.status_indicator_disconnected;
                    break;
            }
            mcpStatusIndicator.setBackgroundResource(drawableRes);

            if (message != null) {
                appendMcpLog("[状态] " + message);
            }
        });
    }

    /**
     * 处理 MCP 工具调用
     */
    private void handleMcpToolCall(Intent intent) {
        String toolName = intent.getStringExtra(McpEvents.EXTRA_TOOL_NAME);
        String args = intent.getStringExtra(McpEvents.EXTRA_TOOL_ARGS);
        String result = intent.getStringExtra(McpEvents.EXTRA_TOOL_RESULT);

        runOnUiThread(() -> {
            // 更新最后调用的工具
            String toolDisplay = toolName;
            if (args != null && !args.isEmpty()) {
                toolDisplay += " (" + truncate(args, 30) + ")";
            }
            mcpLastTool.setText(toolDisplay);

            // 更新结果
            if (result != null) {
                mcpLastResult.setText(truncate(result, 100));
                mcpLastResult.setTextColor(
                    toolName.equals("ERROR")
                        ? 0xFFF44336  // 红色
                        : 0xFF4CAF50   // 绿色
                );
            }

            // 添加到日志
            appendMcpLog("[工具] " + toolName + " → " + truncate(result, 50));
        });
    }

    /**
     * 处理 MCP 日志
     */
    private void handleMcpLog(Intent intent) {
        String message = intent.getStringExtra(McpEvents.EXTRA_LOG_MESSAGE);
        if (message != null) {
            runOnUiThread(() -> appendMcpLog("[日志] " + message));
        }
    }

    /**
     * 添加 MCP 日志
     */
    private void appendMcpLog(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String logLine = timestamp + " " + message + "\n";

        mcpLogBuilder.insert(0, logLine);

        // 限制日志行数
        String logText = mcpLogBuilder.toString();
        String[] lines = logText.split("\n");
        if (lines.length > 50) {
            mcpLogBuilder = new StringBuilder();
            for (int i = 0; i < 50; i++) {
                mcpLogBuilder.insert(0, lines[i] + "\n");
            }
        }

        mcpLog.setText(mcpLogBuilder.toString());
    }

    /**
     * 截断字符串
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
    }

    /**
     * Check and request necessary permissions at app startup
     */
    private void checkAndRequestPermissions() {
        // Check RECORD_AUDIO permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Requesting RECORD_AUDIO permission");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISSION_REQUEST_AUDIO);
        } else {
            Log.d(TAG, "RECORD_AUDIO permission already granted");
        }

        // Android 13+ notification permission (optional)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Requesting POST_NOTIFICATIONS permission");
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        PERMISSION_REQUEST_CODE);
            }
        }

        // Storage permission for video access
        requestStoragePermission();
    }

    /**
     * Request storage permission for video playback
     */
    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ uses READ_MEDIA_VIDEO
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Requesting READ_MEDIA_VIDEO permission");
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_MEDIA_VIDEO},
                        PERMISSION_REQUEST_STORAGE);
            } else {
                Log.d(TAG, "READ_MEDIA_VIDEO permission already granted");
            }
        } else {
            // Android 12 and below uses READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Requesting READ_EXTERNAL_STORAGE permission");
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_STORAGE);
            } else {
                Log.d(TAG, "READ_EXTERNAL_STORAGE permission already granted");
            }
        }
    }

    private void setupWebView() {
        // Enable JavaScript
        webView.getSettings().setJavaScriptEnabled(true);

        // Enable DOM storage
        webView.getSettings().setDomStorageEnabled(true);

        // Enable database storage
        webView.getSettings().setDatabaseEnabled(true);

        // Enable cache
        webView.getSettings().setCacheMode(android.webkit.WebSettings.LOAD_DEFAULT);

        // Support zoom
        webView.getSettings().setSupportZoom(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);

        // Adaptive screen
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setLoadWithOverviewMode(true);

        // Enable Media playback without user gesture
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);

        // Setup WebViewClient
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                view.loadUrl(request.getUrl().toString());
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    showError();
                }
            }
        });

        // Setup WebChromeClient with permission handling
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }

            /**
             * Handle permission requests from WebView (getUserMedia, AudioWorklet, etc.)
             * This is the KEY method for enabling microphone access in WebView
             */
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                Log.d(TAG, "onPermissionRequest called");
                Log.d(TAG, "Resources requested: " + Arrays.toString(request.getResources()));

                // Check if RECORD_AUDIO permission is granted at app level
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                    // Store the request and ask for permission
                    Log.d(TAG, "RECORD_AUDIO not granted, requesting...");
                    pendingPermissionRequest = request;
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.RECORD_AUDIO},
                            PERMISSION_REQUEST_AUDIO);
                } else {
                    // Permission already granted, grant WebView request
                    grantWebViewPermission(request);
                }
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                Log.d(TAG, "onPermissionRequestCanceled");
                super.onPermissionRequestCanceled(request);
                if (pendingPermissionRequest == request) {
                    pendingPermissionRequest = null;
                }
            }
        });

        // Load URL
        loadUrl(currentUrl);
    }

    /**
     * Grant WebView permission for requested resources
     */
    private void grantWebViewPermission(PermissionRequest request) {
        // Get all requested resources
        String[] resources = request.getResources();

        // Filter to only grant resources we support
        java.util.List<String> grantedResources = new java.util.ArrayList<>();

        for (String resource : resources) {
            Log.d(TAG, "Processing resource: " + resource);

            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                // Check if we have RECORD_AUDIO permission
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED) {
                    grantedResources.add(resource);
                    Log.d(TAG, "Granting RESOURCE_AUDIO_CAPTURE");
                } else {
                    Log.w(TAG, "Cannot grant RESOURCE_AUDIO_CAPTURE - permission not granted");
                }
            } else if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                // We don't support video in this app, skip
                Log.d(TAG, "Skipping RESOURCE_VIDEO_CAPTURE (not supported)");
            } else if (PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID.equals(resource)) {
                // Protected media ID - usually not needed
                Log.d(TAG, "Skipping RESOURCE_PROTECTED_MEDIA_ID");
            } else {
                // Unknown resource - grant by default for compatibility
                Log.d(TAG, "Granting unknown resource: " + resource);
                grantedResources.add(resource);
            }
        }

        if (!grantedResources.isEmpty()) {
            String[] grantedArray = grantedResources.toArray(new String[0]);
            Log.d(TAG, "Granting permissions: " + Arrays.toString(grantedArray));
            request.grant(grantedArray);
        } else {
            Log.w(TAG, "No resources to grant, denying request");
            request.deny();
        }
    }

    /**
     * Handle permission request results
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, "onRequestPermissionsResult: requestCode=" + requestCode);

        if (requestCode == PERMISSION_REQUEST_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "RECORD_AUDIO permission granted");
                Toast.makeText(this, "麦克风权限已授权", Toast.LENGTH_SHORT).show();

                // Grant pending WebView permission request
                if (pendingPermissionRequest != null) {
                    grantWebViewPermission(pendingPermissionRequest);
                    pendingPermissionRequest = null;
                }
            } else {
                Log.w(TAG, "RECORD_AUDIO permission denied");
                Toast.makeText(this, "麦克风权限被拒绝，语音功能无法使用", Toast.LENGTH_LONG).show();

                // Deny pending WebView request
                if (pendingPermissionRequest != null) {
                    pendingPermissionRequest.deny();
                    pendingPermissionRequest = null;
                }
            }
        } else if (requestCode == PERMISSION_REQUEST_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Storage permission granted");
                Toast.makeText(this, "存储权限已授权，视频播放功能可用", Toast.LENGTH_SHORT).show();
            } else {
                Log.w(TAG, "Storage permission denied");
                Toast.makeText(this, "存储权限被拒绝，视频播放功能无法使用", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void loadUrl(String url) {
        errorView.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        Log.d(TAG, "Loading URL: " + url);
        webView.loadUrl(url);
    }

    private void updateSwitchButtons(boolean testActive) {
        btnTest.setTextColor(testActive ? 0xFFFFFFFF : 0xFF999999);
        btnTest.setTextSize(13);
        btnWebui.setTextColor(testActive ? 0xFF999999 : 0xFFFFFFFF);
        btnWebui.setTextSize(13);
    }

    private void showError() {
        webView.setVisibility(View.GONE);
        errorView.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Handle back button to go back in WebView history
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadDataWithBaseURL(null, "", "text/html", "utf-8", null);
            webView.clearHistory();

            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) {
                parent.removeView(webView);
            }

            webView.destroy();
        }

        // 停止 MCP 服务
        McpService.stop(this);

        super.onDestroy();
    }
}
