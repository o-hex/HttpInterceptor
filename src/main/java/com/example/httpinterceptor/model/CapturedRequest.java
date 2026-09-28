package com.example.httpinterceptor.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CapturedRequest {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private final long id;
    private final long timestamp;
    private final String pluginName;
    private final String threadName;
    private final String url;
    private final String method;
    private final Map<String, List<String>> requestHeaders;
    private String requestBody;

    private int responseCode = -1;
    private Map<String, List<String>> responseHeaders = Collections.emptyMap();
    private String responseBody;

    private boolean loopbacked = false;
    private boolean modified = false;
    private String matchedRuleName;
    private long durationMs = -1;

    public CapturedRequest(long id, String pluginName, String threadName, String url, String method, Map<String, List<String>> requestHeaders) {
        this.id = id;
        this.timestamp = System.currentTimeMillis();
        this.pluginName = pluginName;
        this.threadName = threadName;
        this.url = url;
        this.method = method;
        this.requestHeaders = requestHeaders != null ? requestHeaders : Collections.emptyMap();
    }

    public long getId() {
        return id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getFormattedTime() {
        return TIME_FORMATTER.format(Instant.ofEpochMilli(timestamp));
    }

    public String getPluginName() {
        return pluginName;
    }

    public String getThreadName() {
        return threadName;
    }

    public String getUrl() {
        return url;
    }

    public String getMethod() {
        return method;
    }

    public Map<String, List<String>> getRequestHeaders() {
        return requestHeaders;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public int getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(int responseCode) {
        this.responseCode = responseCode;
    }

    public Map<String, List<String>> getResponseHeaders() {
        return responseHeaders;
    }

    public void setResponseHeaders(Map<String, List<String>> responseHeaders) {
        this.responseHeaders = responseHeaders != null ? responseHeaders : Collections.emptyMap();
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public boolean isLoopbacked() {
        return loopbacked;
    }

    public void setLoopbacked(boolean loopbacked) {
        this.loopbacked = loopbacked;
    }

    public boolean isModified() {
        return modified;
    }

    public void setModified(boolean modified) {
        this.modified = modified;
    }

    public String getMatchedRuleName() {
        return matchedRuleName;
    }

    public void setMatchedRuleName(String matchedRuleName) {
        this.matchedRuleName = matchedRuleName;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }
}
