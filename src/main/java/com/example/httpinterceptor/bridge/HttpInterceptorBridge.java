package com.example.httpinterceptor.bridge;

import com.example.httpinterceptor.HttpInterceptorPlugin;
import com.example.httpinterceptor.model.*;
import com.example.httpinterceptor.util.CallerIdentifier;
import com.example.httpinterceptor.util.ConnectionContext;

import java.io.*;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class HttpInterceptorBridge {

    public static final String SYS_PROP_KEY = "httpinterceptor.bridge";
    private static HttpInterceptorBridge instance;
    private static final ThreadLocal<Boolean> RECURSION_GUARD = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static final Map<Object, ConnectionContext> CONTEXTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    public HttpInterceptorBridge() {}

    public static synchronized void register(HttpInterceptorBridge bridge) {
        instance = bridge;
        System.getProperties().put(SYS_PROP_KEY, bridge);
    }

    public static synchronized void unregister() {
        System.getProperties().remove(SYS_PROP_KEY);
        instance = null;
        CONTEXTS.clear();
    }

    private static HttpInterceptorPlugin getPlugin() {
        return HttpInterceptorPlugin.getInstance();
    }

    public static ConnectionContext getOrCreateContext(Object connObj) {
        if (!(connObj instanceof HttpURLConnection conn)) {
            return null;
        }

        ConnectionContext ctx = CONTEXTS.get(connObj);
        if (ctx != null) {
            return ctx;
        }

        String pluginName = CallerIdentifier.getCallingPluginName();
        String threadName = Thread.currentThread().getName();
        ctx = new ConnectionContext(pluginName, threadName);

        String url = "";
        try {
            if (conn.getURL() != null) url = conn.getURL().toString();
        } catch (Throwable ignored) {}

        String method = "GET";
        try {
            if (conn.getRequestMethod() != null) method = conn.getRequestMethod();
        } catch (Throwable ignored) {}

        Map<String, List<String>> reqHeaders;
        try {
            reqHeaders = conn.getRequestProperties();
        } catch (Throwable ignored) {
            reqHeaders = Collections.emptyMap();
        }

        HttpRule rule = getPlugin() != null && getPlugin().getRuleManager() != null
                ? getPlugin().getRuleManager().findMatchingRule(pluginName, url, method)
                : null;

        ctx.setMatchedRule(rule);

        long reqId = getPlugin() != null && getPlugin().getHistoryManager() != null
                ? getPlugin().getHistoryManager().nextId() : 0;
        CapturedRequest request = new CapturedRequest(reqId, pluginName, threadName, url, method, reqHeaders);
        ctx.setRequest(request);

        if (rule != null) {
            request.setMatchedRuleName(rule.getName());
            if (rule.getAction() == RuleAction.LOOPBACK && rule.getLoopbackConfig() != null) {
                ctx.setLoopback(true);
                ctx.setLoopbackStatusCode(rule.getLoopbackConfig().getStatusCode());
                ctx.setLoopbackBytes(rule.getLoopbackConfig().getBody().getBytes(StandardCharsets.UTF_8));
                ctx.setLoopbackHeaders(rule.getLoopbackConfig().getHeaders());
                request.setLoopbacked(true);
            } else if (rule.getAction() == RuleAction.MODIFY) {
                ctx.setModified(true);
                request.setModified(true);
            }
        }

        CONTEXTS.put(connObj, ctx);
        return ctx;
    }

    // --- Advice entry/exit handlers ---

    public static int handleResponseCodeEnter(Object conn) {
        if (RECURSION_GUARD.get()) return 0;
        RECURSION_GUARD.set(true);
        try {
            ConnectionContext ctx = getOrCreateContext(conn);
            if (ctx != null && ctx.isLoopback()) {
                return ctx.getLoopbackStatusCode();
            }
            return 0;
        } finally {
            RECURSION_GUARD.set(false);
        }
    }

    public static int handleResponseCodeExit(Object conn, int enterCode, int retCode) {
        if (enterCode != 0) {
            return enterCode;
        }
        if (RECURSION_GUARD.get()) return retCode;
        RECURSION_GUARD.set(true);
        try {
            ConnectionContext ctx = CONTEXTS.get(conn);
            if (ctx != null) {
                CapturedRequest req = ctx.getRequest();
                if (ctx.isModified() && ctx.getMatchedRule() != null) {
                    ModifyConfig mc = ctx.getMatchedRule().getModifyConfig();
                    if (mc != null && mc.getOverrideStatusCode() != null) {
                        if (req != null) req.setResponseCode(mc.getOverrideStatusCode());
                        return mc.getOverrideStatusCode();
                    }
                }
                if (req != null) req.setResponseCode(retCode);
            }
            return retCode;
        } finally {
            RECURSION_GUARD.set(false);
        }
    }

    public static InputStream handleInputStreamEnter(Object conn) {
        if (RECURSION_GUARD.get()) return null;
        RECURSION_GUARD.set(true);
        try {
            ConnectionContext ctx = getOrCreateContext(conn);
            if (ctx != null) {
                if (ctx.isLoopback()) {
                    return new ByteArrayInputStream(ctx.getLoopbackBytes());
                }
                if (ctx.getCachedResponseBodyBytes() != null) {
                    return new ByteArrayInputStream(ctx.getCachedResponseBodyBytes());
                }
                if (ctx.getWrappedInputStream() != null) {
                    return ctx.getWrappedInputStream();
                }
            }
            return null;
        } finally {
            RECURSION_GUARD.set(false);
        }
    }

    public static InputStream handleInputStreamExit(Object conn, InputStream enterVal, InputStream retVal) {
        if (RECURSION_GUARD.get()) return enterVal != null ? enterVal : retVal;
        RECURSION_GUARD.set(true);
        try {
            ConnectionContext ctx = CONTEXTS.get(conn);
            if (ctx == null) {
                return enterVal != null ? enterVal : retVal;
            }

            CapturedRequest req = ctx.getRequest();
            long duration = System.currentTimeMillis() - ctx.getStartTime();
            req.setDurationMs(duration);

            // Capture request body if collected
            if (ctx.getRequestBodyBuffer().size() > 0 && req.getRequestBody() == null) {
                req.setRequestBody(ctx.getRequestBodyBuffer().toString(StandardCharsets.UTF_8));
            }

            if (enterVal != null) {
                // If loopbacked or cached
                if (ctx.isLoopback()) {
                    req.setResponseCode(ctx.getLoopbackStatusCode());
                    req.setResponseHeaders(ctx.getLoopbackHeaders());
                    req.setResponseBody(new String(ctx.getLoopbackBytes(), StandardCharsets.UTF_8));
                    finalizeRequest(ctx);
                }
                return enterVal;
            }

            if (retVal == null) {
                return null;
            }

            if (conn instanceof HttpURLConnection httpConn) {
                if (req.getResponseCode() <= 0) {
                    try {
                        req.setResponseCode(httpConn.getResponseCode());
                    } catch (Throwable ignored) {}
                }
                if (req.getResponseHeaders().isEmpty()) {
                    try {
                        req.setResponseHeaders(httpConn.getHeaderFields());
                    } catch (Throwable ignored) {}
                }
            }

            // Real stream: if modified, mutate payload
            if (ctx.isModified() && ctx.getMatchedRule() != null && ctx.getMatchedRule().getModifyConfig() != null) {
                if (ctx.getCachedResponseBodyBytes() != null) {
                    return new ByteArrayInputStream(ctx.getCachedResponseBodyBytes());
                }
                ModifyConfig mc = ctx.getMatchedRule().getModifyConfig();
                try {
                    byte[] rawBytes = retVal.readAllBytes();
                    String bodyStr = new String(rawBytes, StandardCharsets.UTF_8);

                    for (ModifyConfig.TextReplacement tr : mc.getTextReplacements()) {
                        bodyStr = tr.apply(bodyStr);
                    }

                    if (mc.getOverrideStatusCode() != null) {
                        req.setResponseCode(mc.getOverrideStatusCode());
                    }

                    byte[] modifiedBytes = bodyStr.getBytes(StandardCharsets.UTF_8);
                    ctx.setCachedResponseBodyBytes(modifiedBytes);
                    req.setResponseBody(bodyStr);
                    finalizeRequest(ctx);
                    return new ByteArrayInputStream(modifiedBytes);
                } catch (IOException e) {
                    return retVal;
                }
            }

            // Normal passthrough: check if already wrapped
            if (ctx.getWrappedInputStream() != null) {
                return ctx.getWrappedInputStream();
            }

            ByteArrayOutputStream capturedResp = new ByteArrayOutputStream();
            InputStream wrapped = new FilterInputStream(retVal) {
                @Override
                public int read() throws IOException {
                    int b = super.read();
                    if (b != -1) {
                        capturedResp.write(b);
                    } else {
                        onStreamFinished();
                    }
                    return b;
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    int count = super.read(b, off, len);
                    if (count != -1) {
                        capturedResp.write(b, off, count);
                    } else {
                        onStreamFinished();
                    }
                    return count;
                }

                @Override
                public void close() throws IOException {
                    super.close();
                    onStreamFinished();
                }

                private void onStreamFinished() {
                    if (!ctx.isRequestLogged()) {
                        req.setResponseBody(capturedResp.toString(StandardCharsets.UTF_8));
                        finalizeRequest(ctx);
                    }
                }
            };
            ctx.setWrappedInputStream(wrapped);
            return wrapped;
        } finally {
            RECURSION_GUARD.set(false);
        }
    }

    public static InputStream handleErrorStreamEnter(Object conn) {
        if (RECURSION_GUARD.get()) return null;
        RECURSION_GUARD.set(true);
        try {
            ConnectionContext ctx = getOrCreateContext(conn);
            if (ctx != null) {
                if (ctx.isLoopback()) {
                    if (ctx.getLoopbackStatusCode() >= 400) {
                        return new ByteArrayInputStream(ctx.getLoopbackBytes());
                    }
                    return null;
                }
                if (ctx.isModified() && ctx.getMatchedRule() != null && ctx.getMatchedRule().getModifyConfig() != null) {
                    ModifyConfig mc = ctx.getMatchedRule().getModifyConfig();
                    if (mc.getOverrideStatusCode() != null && mc.getOverrideStatusCode() >= 400 && ctx.getCachedResponseBodyBytes() != null) {
                        return new ByteArrayInputStream(ctx.getCachedResponseBodyBytes());
                    }
                }
                if (ctx.getCachedResponseBodyBytes() != null && ctx.getRequest().getResponseCode() >= 400) {
                    return new ByteArrayInputStream(ctx.getCachedResponseBodyBytes());
                }
            }
            return null;
        } finally {
            RECURSION_GUARD.set(false);
        }
    }

    public static InputStream handleErrorStreamExit(Object conn, InputStream enterVal, InputStream retVal) {
        if (RECURSION_GUARD.get()) return enterVal != null ? enterVal : retVal;
        RECURSION_GUARD.set(true);
        try {
            ConnectionContext ctx = CONTEXTS.get(conn);
            if (ctx == null) return enterVal != null ? enterVal : retVal;

            if (enterVal != null) {
                return enterVal;
            }

            if (retVal == null) return null;

            CapturedRequest req = ctx.getRequest();
            long duration = System.currentTimeMillis() - ctx.getStartTime();
            req.setDurationMs(duration);

            if (conn instanceof HttpURLConnection httpConn) {
                if (req.getResponseCode() <= 0) {
                    try {
                        req.setResponseCode(httpConn.getResponseCode());
                    } catch (Throwable ignored) {}
                }
                if (req.getResponseHeaders().isEmpty()) {
                    try {
                        req.setResponseHeaders(httpConn.getHeaderFields());
                    } catch (Throwable ignored) {}
                }
            }

            // If modified, also mutate error stream
            if (ctx.isModified() && ctx.getMatchedRule() != null && ctx.getMatchedRule().getModifyConfig() != null) {
                if (ctx.getCachedResponseBodyBytes() != null) {
                    return new ByteArrayInputStream(ctx.getCachedResponseBodyBytes());
                }
                ModifyConfig mc = ctx.getMatchedRule().getModifyConfig();
                try {
                    byte[] rawBytes = retVal.readAllBytes();
                    String bodyStr = new String(rawBytes, StandardCharsets.UTF_8);

                    for (ModifyConfig.TextReplacement tr : mc.getTextReplacements()) {
                        bodyStr = tr.apply(bodyStr);
                    }

                    if (mc.getOverrideStatusCode() != null) {
                        req.setResponseCode(mc.getOverrideStatusCode());
                    }

                    byte[] modifiedBytes = bodyStr.getBytes(StandardCharsets.UTF_8);
                    ctx.setCachedResponseBodyBytes(modifiedBytes);
                    req.setResponseBody(bodyStr);
                    finalizeRequest(ctx);
                    return new ByteArrayInputStream(modifiedBytes);
                } catch (IOException e) {
                    return retVal;
                }
            }

            ByteArrayOutputStream errResp = new ByteArrayOutputStream();
            return new FilterInputStream(retVal) {
                @Override
                public int read() throws IOException {
                    int b = super.read();
                    if (b != -1) errResp.write(b);
                    else onStreamFinished();
                    return b;
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    int count = super.read(b, off, len);
                    if (count != -1) errResp.write(b, off, count);
                    else onStreamFinished();
                    return count;
                }

                @Override
                public void close() throws IOException {
                    super.close();
                    onStreamFinished();
                }

                private void onStreamFinished() {
                    if (!ctx.isRequestLogged()) {
                        req.setResponseBody(errResp.toString(StandardCharsets.UTF_8));
                        finalizeRequest(ctx);
                    }
                }
            };
        } finally {
            RECURSION_GUARD.set(false);
        }
    }

    public static boolean handleConnectEnter(Object conn) {
        if (RECURSION_GUARD.get()) return false;
        RECURSION_GUARD.set(true);
        try {
            ConnectionContext ctx = getOrCreateContext(conn);
            if (ctx != null && ctx.isLoopback()) {
                return true;
            }
            return false;
        } finally {
            RECURSION_GUARD.set(false);
        }
    }

    public static OutputStream handleOutputStreamEnter(Object conn) {
        if (RECURSION_GUARD.get()) return null;
        RECURSION_GUARD.set(true);
        try {
            ConnectionContext ctx = getOrCreateContext(conn);
            if (ctx != null && ctx.isLoopback()) {
                return ctx.getRequestBodyBuffer();
            }
            return null;
        } finally {
            RECURSION_GUARD.set(false);
        }
    }

    public static OutputStream handleOutputStreamExit(Object conn, OutputStream retVal) {
        ConnectionContext ctx = getOrCreateContext(conn);
        if (ctx == null) {
            return retVal;
        }

        if (ctx.isLoopback()) {
            // Loopbacked: dummy output stream to collect payload
            return ctx.getRequestBodyBuffer();
        }

        // Real: TeeOutputStream to capture outgoing request body
        return new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                ctx.getRequestBodyBuffer().write(b);
                if (retVal != null) retVal.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                ctx.getRequestBodyBuffer().write(b, off, len);
                if (retVal != null) retVal.write(b, off, len);
            }

            @Override
            public void flush() throws IOException {
                if (retVal != null) retVal.flush();
            }

            @Override
            public void close() throws IOException {
                if (retVal != null) retVal.close();
            }
        };
    }

    public static Map<String, List<String>> handleHeaderFieldsExit(Object conn, Map<String, List<String>> retVal) {
        if (RECURSION_GUARD.get()) return retVal;
        RECURSION_GUARD.set(true);
        try {
            ConnectionContext ctx = CONTEXTS.get(conn);
            if (ctx != null && ctx.isLoopback()) {
                return ctx.getLoopbackHeaders();
            }
            return retVal;
        } finally {
            RECURSION_GUARD.set(false);
        }
    }

    public static String handleHeaderFieldExit(Object conn, String name, String retVal) {
        if (RECURSION_GUARD.get()) return retVal;
        RECURSION_GUARD.set(true);
        try {
            ConnectionContext ctx = CONTEXTS.get(conn);
            if (ctx != null && ctx.isLoopback() && name != null) {
                List<String> vals = ctx.getLoopbackHeaders().get(name);
                if (vals != null && !vals.isEmpty()) {
                    return vals.get(0);
                }
            }
            return retVal;
        } finally {
            RECURSION_GUARD.set(false);
        }
    }

    public static String handleHeaderFieldIntExit(Object conn, int index, String retVal) {
        if (RECURSION_GUARD.get()) return retVal;
        RECURSION_GUARD.set(true);
        try {
            ConnectionContext ctx = CONTEXTS.get(conn);
            if (ctx != null && index == 0) {
                if (ctx.isLoopback()) {
                    int code = ctx.getLoopbackStatusCode();
                    return "HTTP/1.1 " + code + (code == 200 ? " OK" : " Mocked");
                }
                if (ctx.isModified() && ctx.getMatchedRule() != null && ctx.getMatchedRule().getModifyConfig() != null) {
                    Integer override = ctx.getMatchedRule().getModifyConfig().getOverrideStatusCode();
                    if (override != null) {
                        return "HTTP/1.1 " + override + " Modified";
                    }
                }
            }
            return retVal;
        } finally {
            RECURSION_GUARD.set(false);
        }
    }

    private static void finalizeRequest(ConnectionContext ctx) {
        if (ctx.isRequestLogged()) return;
        ctx.setRequestLogged(true);

        if (getPlugin() != null && getPlugin().getHistoryManager() != null) {
            getPlugin().getHistoryManager().recordRequest(ctx.getRequest());
        }
    }
}
