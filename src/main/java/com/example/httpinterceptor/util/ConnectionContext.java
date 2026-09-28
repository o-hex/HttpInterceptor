package com.example.httpinterceptor.util;

import com.example.httpinterceptor.model.CapturedRequest;
import com.example.httpinterceptor.model.HttpRule;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ConnectionContext {

    private final String pluginName;
    private final String threadName;
    private final long startTime;
    private CapturedRequest request;
    private HttpRule matchedRule;

    private boolean loopback = false;
    private boolean modified = false;

    private int loopbackStatusCode = 200;
    private byte[] loopbackBytes;
    private Map<String, List<String>> loopbackHeaders;
    private ByteArrayOutputStream requestBodyBuffer;
    private byte[] cachedResponseBodyBytes;
    private InputStream wrappedInputStream;
    private boolean requestLogged = false;

    public byte[] getCachedResponseBodyBytes() {
        return cachedResponseBodyBytes;
    }

    public void setCachedResponseBodyBytes(byte[] cachedResponseBodyBytes) {
        this.cachedResponseBodyBytes = cachedResponseBodyBytes;
    }

    public InputStream getWrappedInputStream() {
        return wrappedInputStream;
    }

    public void setWrappedInputStream(InputStream wrappedInputStream) {
        this.wrappedInputStream = wrappedInputStream;
    }

    public ConnectionContext(String pluginName, String threadName) {
        this.pluginName = pluginName;
        this.threadName = threadName;
        this.startTime = System.currentTimeMillis();
    }

    public String getPluginName() {
        return pluginName;
    }

    public String getThreadName() {
        return threadName;
    }

    public long getStartTime() {
        return startTime;
    }

    public CapturedRequest getRequest() {
        return request;
    }

    public void setRequest(CapturedRequest request) {
        this.request = request;
    }

    public HttpRule getMatchedRule() {
        return matchedRule;
    }

    public void setMatchedRule(HttpRule matchedRule) {
        this.matchedRule = matchedRule;
    }

    public boolean isLoopback() {
        return loopback;
    }

    public void setLoopback(boolean loopback) {
        this.loopback = loopback;
    }

    public boolean isModified() {
        return modified;
    }

    public void setModified(boolean modified) {
        this.modified = modified;
    }

    public int getLoopbackStatusCode() {
        return loopbackStatusCode;
    }

    public void setLoopbackStatusCode(int loopbackStatusCode) {
        this.loopbackStatusCode = loopbackStatusCode;
    }

    public byte[] getLoopbackBytes() {
        return loopbackBytes;
    }

    public void setLoopbackBytes(byte[] loopbackBytes) {
        this.loopbackBytes = loopbackBytes;
    }

    public Map<String, List<String>> getLoopbackHeaders() {
        return loopbackHeaders;
    }

    public void setLoopbackHeaders(Map<String, List<String>> loopbackHeaders) {
        this.loopbackHeaders = loopbackHeaders != null ? loopbackHeaders : Collections.emptyMap();
    }

    public ByteArrayOutputStream getRequestBodyBuffer() {
        if (requestBodyBuffer == null) {
            requestBodyBuffer = new ByteArrayOutputStream();
        }
        return requestBodyBuffer;
    }

    public boolean isRequestLogged() {
        return requestLogged;
    }

    public void setRequestLogged(boolean requestLogged) {
        this.requestLogged = requestLogged;
    }
}
