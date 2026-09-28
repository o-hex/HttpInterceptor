package com.example.httpinterceptor;

import com.example.httpinterceptor.advice.*;
import com.example.httpinterceptor.command.HttpCommand;
import com.example.httpinterceptor.manager.CaptureHistoryManager;
import com.example.httpinterceptor.manager.RuleManager;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.instrument.Instrumentation;
import java.net.HttpURLConnection;

public final class HttpInterceptorPlugin extends JavaPlugin {

    private static HttpInterceptorPlugin instance;
    private RuleManager ruleManager;
    private CaptureHistoryManager historyManager;
    private boolean agentActive = false;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.historyManager = new CaptureHistoryManager(this);
        this.ruleManager = new RuleManager(this);

        loadSettings();
        this.ruleManager.loadRules();

        // Register Command
        HttpCommand httpCmd = new HttpCommand(this);
        PluginCommand cmd = getCommand("httpinterceptor");
        if (cmd != null) {
            cmd.setExecutor(httpCmd);
            cmd.setTabCompleter(httpCmd);
        }

        // Register Bridge in System.getProperties()
        com.example.httpinterceptor.bridge.HttpInterceptorBridge.register(new com.example.httpinterceptor.bridge.HttpInterceptorBridge());

        // Install Bytecode Agent
        installAgent();

        getLogger().info("HttpInterceptor v" + getDescription().getVersion() + " initialized successfully!");
    }

    @Override
    public void onDisable() {
        com.example.httpinterceptor.bridge.HttpInterceptorBridge.unregister();
        getLogger().info("HttpInterceptor disabled.");
        instance = null;
    }

    public void loadSettings() {
        int limit = getConfig().getInt("settings.history-limit", 150);
        boolean liveLog = getConfig().getBoolean("settings.live-log-console", true);
        boolean notifyOps = getConfig().getBoolean("settings.notify-ops", true);
        int maxBody = getConfig().getInt("settings.max-body-capture-chars", 10000);

        this.historyManager.configure(limit, liveLog, notifyOps, maxBody);
    }

    private void installAgent() {
        try {
            getLogger().info("Dynamically attaching JVM Bytecode Agent...");
            Instrumentation instrumentation = ByteBuddyAgent.install();

            net.bytebuddy.matcher.ElementMatcher.Junction<net.bytebuddy.description.type.TypeDescription> httpMatcher =
                ElementMatchers.named("sun.net.www.protocol.http.HttpURLConnection");

            new AgentBuilder.Default()
                .disableClassFormatChanges()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .with(new AgentBuilder.Listener.Adapter() {
                    @Override
                    public void onTransformation(net.bytebuddy.description.type.TypeDescription typeDescription, ClassLoader classLoader, net.bytebuddy.utility.JavaModule module, boolean loaded, net.bytebuddy.dynamic.DynamicType dynamicType) {
                        getLogger().info("[Agent] Transformed class: " + typeDescription.getName());
                    }
                    @Override
                    public void onError(String typeName, ClassLoader classLoader, net.bytebuddy.utility.JavaModule module, boolean loaded, Throwable throwable) {
                        getLogger().warning("[Agent] Error transforming " + typeName + ": " + throwable.getMessage());
                        throwable.printStackTrace();
                    }
                })
                .ignore(ElementMatchers.not(httpMatcher), ElementMatchers.none())
                .type(httpMatcher)
                .transform((builder, typeDesc, classLoader, module, domain) -> {
                    return builder
                        .visit(Advice.to(ResponseCodeAdvice.class).on(ElementMatchers.named("getResponseCode")))
                        .visit(Advice.to(ConnectAdvice.class).on(ElementMatchers.named("connect").and(ElementMatchers.takesArguments(0))))
                        .visit(Advice.to(InputStreamAdvice.class).on(ElementMatchers.named("getInputStream")))
                        .visit(Advice.to(ErrorStreamAdvice.class).on(ElementMatchers.named("getErrorStream")))
                        .visit(Advice.to(OutputStreamAdvice.class).on(ElementMatchers.named("getOutputStream")))
                        .visit(Advice.to(HeaderAdvice.GetHeaderFieldsAdvice.class).on(ElementMatchers.named("getHeaderFields")))
                        .visit(Advice.to(HeaderAdvice.GetHeaderFieldAdvice.class).on(ElementMatchers.named("getHeaderField")
                                .and(ElementMatchers.takesArguments(String.class))))
                        .visit(Advice.to(HeaderAdvice.GetHeaderFieldIntAdvice.class).on(ElementMatchers.named("getHeaderField")
                                .and(ElementMatchers.takesArguments(int.class))));
                })
                .installOn(instrumentation);

            this.agentActive = true;
            getLogger().info("HTTP/HTTPS Bytecode interception successfully installed on JVM!");
        } catch (Throwable t) {
            this.agentActive = false;
            getLogger().severe("Failed to dynamically install Bytecode Agent: " + t.getMessage());
            t.printStackTrace();
        }
    }

    public static HttpInterceptorPlugin getInstance() {
        return instance;
    }

    public RuleManager getRuleManager() {
        return ruleManager;
    }

    public CaptureHistoryManager getHistoryManager() {
        return historyManager;
    }

    public boolean isAgentActive() {
        return agentActive;
    }
}
