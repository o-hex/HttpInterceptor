package com.example.httpinterceptor.manager;

import com.example.httpinterceptor.HttpInterceptorPlugin;
import com.example.httpinterceptor.model.*;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

public class RuleManager {

    private final HttpInterceptorPlugin plugin;
    private final List<HttpRule> rules = new CopyOnWriteArrayList<>();

    public RuleManager(HttpInterceptorPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadRules() {
        rules.clear();
        ConfigurationSection rulesSection = plugin.getConfig().getConfigurationSection("rules");
        if (rulesSection == null) {
            // Also check list of maps format
            List<Map<?, ?>> ruleList = plugin.getConfig().getMapList("rules");
            if (ruleList != null) {
                for (Map<?, ?> map : ruleList) {
                    try {
                        HttpRule rule = parseRuleFromMap(map);
                        if (rule != null) {
                            rules.add(rule);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to parse rule from list: " + e.getMessage());
                    }
                }
            }
            plugin.getLogger().info("Loaded " + rules.size() + " HTTP rules from config.");
            return;
        }

        for (String key : rulesSection.getKeys(false)) {
            ConfigurationSection sec = rulesSection.getConfigurationSection(key);
            if (sec == null) continue;

            try {
                String name = sec.getString("name", key);
                boolean enabled = sec.getBoolean("enabled", true);
                String targetPlugin = sec.getString("plugin", "*");
                String urlPatternStr = sec.getString("url-pattern", ".*");
                Pattern urlPattern = Pattern.compile(urlPatternStr);
                String method = sec.getString("method", "*");
                String actionStr = sec.getString("action", "CAPTURE_ONLY").toUpperCase();
                RuleAction action = RuleAction.valueOf(actionStr);

                LoopbackConfig loopbackConfig = null;
                if (action == RuleAction.LOOPBACK) {
                    int statusCode = sec.getInt("loopback.status-code", 200);
                    String body = sec.getString("loopback.body", "");
                    Map<String, List<String>> headers = new HashMap<>();
                    ConfigurationSection hSec = sec.getConfigurationSection("loopback.headers");
                    if (hSec != null) {
                        for (String hKey : hSec.getKeys(false)) {
                            headers.put(hKey, Collections.singletonList(hSec.getString(hKey)));
                        }
                    }
                    loopbackConfig = new LoopbackConfig(statusCode, headers, body);
                }

                ModifyConfig modifyConfig = null;
                if (action == RuleAction.MODIFY) {
                    Integer overrideStatus = sec.contains("modify.override-status-code") ? sec.getInt("modify.override-status-code") : null;
                    List<ModifyConfig.TextReplacement> replacements = new ArrayList<>();
                    List<Map<?, ?>> repMaps = sec.getMapList("modify.replace-text");
                    for (Map<?, ?> rm : repMaps) {
                        String from = String.valueOf(rm.get("from"));
                        String to = String.valueOf(rm.get("to"));
                        boolean isRegex = Boolean.parseBoolean(String.valueOf(getMapVal(rm, "regex", "false")));
                        replacements.add(new ModifyConfig.TextReplacement(from, to, isRegex));
                    }
                    modifyConfig = new ModifyConfig(overrideStatus, Collections.emptyMap(), replacements);
                }

                HttpRule rule = new HttpRule(name, enabled, targetPlugin, urlPattern, method, action, loopbackConfig, modifyConfig);
                rules.add(rule);
            } catch (Exception e) {
                plugin.getLogger().warning("Error loading rule '" + key + "': " + e.getMessage());
            }
        }

        plugin.getLogger().info("Loaded " + rules.size() + " HTTP rules from config.");
    }

    private static Object getMapVal(Map<?, ?> map, String key, Object defaultVal) {
        if (map == null) return defaultVal;
        Object v = map.get(key);
        return v != null ? v : defaultVal;
    }

    private HttpRule parseRuleFromMap(Map<?, ?> map) {
        String name = String.valueOf(getMapVal(map, "name", "unnamed"));
        boolean enabled = Boolean.parseBoolean(String.valueOf(getMapVal(map, "enabled", "true")));
        String targetPlugin = String.valueOf(getMapVal(map, "plugin", "*"));
        String urlPatternStr = String.valueOf(getMapVal(map, "url-pattern", ".*"));
        Pattern urlPattern = Pattern.compile(urlPatternStr);
        String method = String.valueOf(getMapVal(map, "method", "*"));
        String actionStr = String.valueOf(getMapVal(map, "action", "CAPTURE_ONLY")).toUpperCase();
        RuleAction action = RuleAction.valueOf(actionStr);

        LoopbackConfig loopbackConfig = null;
        if (action == RuleAction.LOOPBACK && map.containsKey("loopback")) {
            Object lbObj = map.get("loopback");
            if (lbObj instanceof Map<?, ?> lbMap) {
                int status = Integer.parseInt(String.valueOf(getMapVal(lbMap, "status-code", 200)));
                String body = String.valueOf(getMapVal(lbMap, "body", ""));
                Map<String, List<String>> headers = new HashMap<>();
                if (lbMap.get("headers") instanceof Map<?, ?> hm) {
                    for (Map.Entry<?, ?> e : hm.entrySet()) {
                        headers.put(String.valueOf(e.getKey()), Collections.singletonList(String.valueOf(e.getValue())));
                    }
                }
                loopbackConfig = new LoopbackConfig(status, headers, body);
            }
        }

        ModifyConfig modifyConfig = null;
        if (action == RuleAction.MODIFY && map.containsKey("modify")) {
            Object modObj = map.get("modify");
            if (modObj instanceof Map<?, ?> modMap) {
                Integer overrideStatus = modMap.containsKey("override-status-code") ? Integer.parseInt(String.valueOf(modMap.get("override-status-code"))) : null;
                List<ModifyConfig.TextReplacement> replacements = new ArrayList<>();
                if (modMap.get("replace-text") instanceof List<?> repList) {
                    for (Object ro : repList) {
                        if (ro instanceof Map<?, ?> rm) {
                            String from = String.valueOf(rm.get("from"));
                            String to = String.valueOf(rm.get("to"));
                            boolean isRegex = Boolean.parseBoolean(String.valueOf(getMapVal(rm, "regex", "false")));
                            replacements.add(new ModifyConfig.TextReplacement(from, to, isRegex));
                        }
                    }
                }
                modifyConfig = new ModifyConfig(overrideStatus, Collections.emptyMap(), replacements);
            }
        }

        return new HttpRule(name, enabled, targetPlugin, urlPattern, method, action, loopbackConfig, modifyConfig);
    }

    public HttpRule findMatchingRule(String pluginName, String url, String method) {
        for (HttpRule rule : rules) {
            if (rule.matches(pluginName, url, method)) {
                return rule;
            }
        }
        return null;
    }

    public List<HttpRule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    public void addRule(HttpRule rule) {
        rules.add(rule);
    }
}
