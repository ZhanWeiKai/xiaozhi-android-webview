package com.xiaozhi.mcp.tools.impl;

import android.os.Build;
import android.util.Log;

import com.xiaozhi.mcp.tools.Tool;

import java.util.HashMap;
import java.util.Map;

/**
 * 设备信息工具 - 返回 Android 设备基本信息
 */
public class DeviceInfoTool implements Tool {
    private static final String TAG = "DeviceInfoTool";

    @Override
    public String getName() {
        return "get_device_info";
    }

    @Override
    public String getDescription() {
        return "获取当前 Android 设备的基本信息，包括型号、系统版本等";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", new HashMap<>());
        return schema;
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        Log.d(TAG, "执行 get_device_info");

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);

        Map<String, Object> deviceInfo = new HashMap<>();
        deviceInfo.put("brand", Build.BRAND);
        deviceInfo.put("manufacturer", Build.MANUFACTURER);
        deviceInfo.put("model", Build.MODEL);
        deviceInfo.put("device", Build.DEVICE);
        deviceInfo.put("androidVersion", Build.VERSION.RELEASE);
        deviceInfo.put("sdkVersion", Build.VERSION.SDK_INT);
        deviceInfo.put("product", Build.PRODUCT);

        result.put("device", deviceInfo);
        result.put("timestamp", System.currentTimeMillis());

        return result;
    }
}
