package com.xiaozhi.mcp.protocol;

import java.util.HashMap;
import java.util.Map;

/**
 * JSON-RPC 响应
 */
public class JsonRpcResponse {
    private String jsonrpc = "2.0";
    private String id;
    private Object result;
    private Map<String, Object> error;

    public JsonRpcResponse() {}

    public static JsonRpcResponse success(String id, Object result) {
        JsonRpcResponse response = new JsonRpcResponse();
        response.setId(id);
        response.setResult(result);
        return response;
    }

    public static JsonRpcResponse error(String id, int code, String message) {
        JsonRpcResponse response = new JsonRpcResponse();
        response.setId(id);
        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put("code", code);
        errorMap.put("message", message);
        response.setError(errorMap);
        return response;
    }

    public String getJsonrpc() {
        return jsonrpc;
    }

    public void setJsonrpc(String jsonrpc) {
        this.jsonrpc = jsonrpc;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public Map<String, Object> getError() {
        return error;
    }

    public void setError(Map<String, Object> error) {
        this.error = error;
    }
}
