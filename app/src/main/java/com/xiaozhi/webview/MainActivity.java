package com.xiaozhi.webview;

import android.Manifest;
import android.content.pm.PackageManager;
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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "XiaozhiWebView";
    private static final String URL = "https://xiaozhi.jamesweb.org";

    // Permission request codes
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int PERMISSION_REQUEST_AUDIO = 1002;

    private WebView webView;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private View errorView;

    // Store pending permission request from WebView
    private PermissionRequest pendingPermissionRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        checkAndRequestPermissions();
        setupWebView();
    }

    private void initViews() {
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        errorView = findViewById(R.id.errorView);

        // Setup SwipeRefreshLayout
        swipeRefreshLayout.setOnRefreshListener(() -> {
            webView.reload();
        });

        // Setup error view retry button
        errorView.findViewById(R.id.retryButton).setOnClickListener(v -> {
            errorView.setVisibility(View.GONE);
            webView.reload();
        });
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
                swipeRefreshLayout.setRefreshing(false);
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
                Log.d(TAG, "Resources requested: " + java.util.Arrays.toString(request.getResources()));

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
        loadUrl();
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
            Log.d(TAG, "Granting permissions: " + java.util.Arrays.toString(grantedArray));
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
        }
    }

    private void loadUrl() {
        errorView.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        Log.d(TAG, "Loading URL: " + URL);
        webView.loadUrl(URL);
    }

    private void showError() {
        webView.setVisibility(View.GONE);
        errorView.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);
        swipeRefreshLayout.setRefreshing(false);
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
        super.onDestroy();
    }
}
