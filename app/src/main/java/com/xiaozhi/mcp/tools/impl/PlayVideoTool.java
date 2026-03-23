package com.xiaozhi.mcp.tools.impl;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.xiaozhi.mcp.tools.Tool;
import com.xiaozhi.webview.R;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 视频播放工具 - 根据关键词搜索本地视频并播放
 */
public class PlayVideoTool implements Tool {
    private static final String TAG = "PlayVideoTool";

    private final Context context;

    // 支持的视频格式
    private static final String[] VIDEO_EXTENSIONS = {".mp4", ".3gp", ".mkv", ".avi", ".mov", ".webm"};

    // 视频搜索目录
    private static final String[] VIDEO_DIRS = {
        "DCIM/Camera",
        "Movies",
        "Download",
        "Downloads",
        "Videos"
    };

    public PlayVideoTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "play_video";
    }

    @Override
    public String getDescription() {
        return "根据关键词搜索手机本地视频并播放最匹配的视频。例如：play_video(query='生日') 会搜索并播放文件名包含'生日'的视频";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> queryProp = new HashMap<>();
        queryProp.put("type", "string");
        queryProp.put("description", "视频名称关键词，用于搜索匹配的视频文件");

        Map<String, Object> properties = new HashMap<>();
        properties.put("query", queryProp);

        schema.put("properties", properties);
        schema.put("required", List.of("query"));
        return schema;
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        String query = arguments.get("query") != null ? arguments.get("query").toString() : "";
        Log.i(TAG, "执行 play_video, query=" + query);

        if (query.isEmpty()) {
            return createErrorResult("请提供搜索关键词");
        }

        // 搜索视频文件
        List<VideoInfo> allVideos = scanAllVideos();
        Log.d(TAG, "扫描到 " + allVideos.size() + " 个视频文件");

        if (allVideos.isEmpty()) {
            return createErrorResult("未找到任何视频文件");
        }

        // 匹配视频
        VideoInfo bestMatch = findBestMatch(query, allVideos);

        if (bestMatch == null) {
            return createErrorResult("未找到匹配 '" + query + "' 的视频");
        }

        // 播放视频
        boolean played = playVideo(bestMatch.file);

        if (played) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "正在播放视频");
            result.put("video_path", bestMatch.file.getAbsolutePath());
            result.put("video_name", bestMatch.file.getName());
            result.put("match_score", bestMatch.score);
            return result;
        } else {
            return createErrorResult("无法播放视频: " + bestMatch.file.getName());
        }
    }

    /**
     * 扫描所有视频目录
     */
    private List<VideoInfo> scanAllVideos() {
        List<VideoInfo> videos = new ArrayList<>();
        File storageDir = Environment.getExternalStorageDirectory();

        for (String dirPath : VIDEO_DIRS) {
            File dir = new File(storageDir, dirPath);
            if (dir.exists() && dir.isDirectory()) {
                scanDirectory(dir, videos);
            }
        }

        return videos;
    }

    /**
     * 递归扫描目录
     */
    private void scanDirectory(File dir, List<VideoInfo> videos) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, videos);
            } else if (isVideoFile(file)) {
                videos.add(new VideoInfo(file));
            }
        }
    }

    /**
     * 检查是否是视频文件
     */
    private boolean isVideoFile(File file) {
        String name = file.getName().toLowerCase(Locale.getDefault());
        for (String ext : VIDEO_EXTENSIONS) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 查找最佳匹配
     */
    private VideoInfo findBestMatch(String query, List<VideoInfo> videos) {
        String queryLower = query.toLowerCase(Locale.getDefault());
        VideoInfo bestMatch = null;
        double bestScore = 0;

        for (VideoInfo video : videos) {
            String fileName = video.file.getName().toLowerCase(Locale.getDefault());

            // 计算匹配分数
            double score = calculateMatchScore(queryLower, fileName);
            video.score = score;

            if (score > bestScore) {
                bestScore = score;
                bestMatch = video;
            }
        }

        // 只返回有一定匹配度的结果
        if (bestScore > 0) {
            return bestMatch;
        }
        return null;
    }

    /**
     * 计算匹配分数
     */
    private double calculateMatchScore(String query, String fileName) {
        // 移除文件扩展名
        String nameWithoutExt = fileName;
        for (String ext : VIDEO_EXTENSIONS) {
            if (nameWithoutExt.endsWith(ext)) {
                nameWithoutExt = nameWithoutExt.substring(0, nameWithoutExt.length() - ext.length());
                break;
            }
        }

        // 简单的包含匹配
        if (nameWithoutExt.contains(query)) {
            // 计算相对位置分数（越靠前分数越高）
            int index = nameWithoutExt.indexOf(query);
            double positionScore = 1.0 - (double) index / (nameWithoutExt.length() + 1);
            // 计算长度比例分数（匹配占比越高分数越高）
            double lengthScore = (double) query.length() / nameWithoutExt.length();
            return 0.7 * positionScore + 0.3 * lengthScore;
        }

        return 0;
    }

    /**
     * 播放视频
     */
    private boolean playVideo(File videoFile) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri;

            // Android 7+ 需要 FileProvider
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                uri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    videoFile
                );
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                uri = Uri.fromFile(videoFile);
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setDataAndType(uri, "video/*");

            context.startActivity(intent);
            Log.i(TAG, "成功启动视频播放: " + videoFile.getName());
            return true;

        } catch (Exception e) {
            Log.e(TAG, "播放视频失败: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 创建错误结果
     */
    private Map<String, Object> createErrorResult(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("error", message);
        return result;
    }

    /**
     * 视频信息内部类
     */
    private static class VideoInfo {
        File file;
        double score;

        VideoInfo(File file) {
            this.file = file;
            this.score = 0;
        }
    }
}
