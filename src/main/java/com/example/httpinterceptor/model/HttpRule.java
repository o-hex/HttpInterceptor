package com.example.httpinterceptor.model;

import java.util.regex.Pattern;

public class HttpRule {

    private final String name;
    private boolean enabled;
    private final String targetPlugin;
    private final Pattern urlPattern;
    private final String method;
    private final RuleAction action;
    private final LoopbackConfig loopbackConfig;
    private final ModifyConfig modifyConfig;

    public HttpRule(String name, boolean enabled, String targetPlugin, Pattern urlPattern,
                    String method, RuleAction action, LoopbackConfig loopbackConfig, ModifyConfig modifyConfig) {
        this.name = name;
        this.enabled = enabled;
        this.targetPlugin = targetPlugin != null ? targetPlugin.trim() : "*";
        this.urlPattern = urlPattern;
        this.method = method != null ? method.toUpperCase().trim() : "*";
        this.action = action;
        this.loopbackConfig = loopbackConfig;
        this.modifyConfig = modifyConfig;
    }

    public boolean matches(String pluginName, String url, String requestMethod) {
        if (!enabled) return false;

        // Check plugin match
        if (!"*".equals(targetPlugin) && !targetPlugin.equalsIgnoreCase(pluginName)) {
            return false;
        }

        // Check method match
        if (!"*".equals(method) && requestMethod != null && !method.equalsIgnoreCase(requestMethod)) {
            return false;
        }

        // Check URL pattern match
        if (urlPattern != null && url != null) {
            return urlPattern.matcher(url).matches();
        }

        return true;
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTargetPlugin() {
        return targetPlugin;
    }

    public Pattern getUrlPattern() {
        return urlPattern;
    }

    public String getMethod() {
        return method;
    }

    public RuleAction getAction() {
        return action;
    }

    public LoopbackConfig getLoopbackConfig() {
        return loopbackConfig;
    }

    public ModifyConfig getModifyConfig() {
        return modifyConfig;
    }
}
