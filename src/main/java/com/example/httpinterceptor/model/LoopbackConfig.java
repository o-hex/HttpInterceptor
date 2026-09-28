package com.example.httpinterceptor.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class LoopbackConfig {

    private final int statusCode;
    private final Map<String, List<String>> headers;
    private final String body;

    public LoopbackConfig(int statusCode, Map<String, List<String>> headers, String body) {
        this.statusCode = statusCode;
        this.headers = headers != null ? headers : Collections.emptyMap();
        this.body = body != null ? body : "";
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }
}
