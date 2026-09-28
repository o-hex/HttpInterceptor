package com.example.httpinterceptor.command;

import com.example.httpinterceptor.HttpInterceptorPlugin;
import com.example.httpinterceptor.model.CapturedRequest;
import com.example.httpinterceptor.model.HttpRule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class HttpCommand implements CommandExecutor, TabCompleter {

    private final HttpInterceptorPlugin plugin;

    public HttpCommand(HttpInterceptorPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("httpinterceptor.admin")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "status" -> handleStatus(sender);
            case "live" -> handleLive(sender, args);
            case "notify" -> handleNotify(sender, args);
            case "list", "history" -> handleList(sender, args);
            case "view", "inspect" -> handleView(sender, args);
            case "rules" -> handleRules(sender);
            case "reload" -> handleReload(sender);
            case "test" -> handleTest(sender, args);
            case "test-all", "verify" -> handleTestAll(sender);
            case "benchmark", "bench" -> handleBenchmark(sender, args);
            case "clear" -> handleClear(sender);
            default -> sendHelp(sender, label);
        }

        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Component.text("--------- [ HttpInterceptor Commands ] ---------", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(cmdHelp("/" + label + " status", "Show agent attachment & traffic stats"));
        sender.sendMessage(cmdHelp("/" + label + " list [page]", "List captured HTTP requests"));
        sender.sendMessage(cmdHelp("/" + label + " view <id>", "Inspect headers & bodies of a request"));
        sender.sendMessage(cmdHelp("/" + label + " rules", "List all loopback and modify rules"));
        sender.sendMessage(cmdHelp("/" + label + " live [on|off]", "Toggle real-time console logging"));
        sender.sendMessage(cmdHelp("/" + label + " notify [on|off]", "Toggle in-game op alert broadcasts"));
        sender.sendMessage(cmdHelp("/" + label + " test <url> [plugin]", "Send test HTTP call to verify rules"));
        sender.sendMessage(cmdHelp("/" + label + " test-all", "Run automated 5-step test suite"));
        sender.sendMessage(cmdHelp("/" + label + " benchmark [reqs] [threads]", "Stress test throughput, latency & memory"));
        sender.sendMessage(cmdHelp("/" + label + " reload", "Reload rules from config.yml"));
        sender.sendMessage(cmdHelp("/" + label + " clear", "Clear captured history buffer"));
    }

    private Component cmdHelp(String usage, String desc) {
        return Component.text(usage, NamedTextColor.YELLOW)
                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                .append(Component.text(desc, NamedTextColor.GRAY));
    }

    private void handleStatus(CommandSender sender) {
        sender.sendMessage(Component.text("=== HttpInterceptor Status ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Byte Buddy Agent: ", NamedTextColor.YELLOW)
                .append(Component.text(plugin.isAgentActive() ? "ACTIVE (Instrumented)" : "FAILED", plugin.isAgentActive() ? NamedTextColor.GREEN : NamedTextColor.RED)));
        sender.sendMessage(Component.text("Active Rules: ", NamedTextColor.YELLOW)
                .append(Component.text(plugin.getRuleManager().getRules().size(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Captured in History: ", NamedTextColor.YELLOW)
                .append(Component.text(plugin.getHistoryManager().getCapturedCount(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Console Live Log: ", NamedTextColor.YELLOW)
                .append(Component.text(plugin.getHistoryManager().isLiveLogConsole() ? "ENABLED" : "DISABLED", NamedTextColor.AQUA)));
        sender.sendMessage(Component.text("In-game Alerts: ", NamedTextColor.YELLOW)
                .append(Component.text(plugin.getHistoryManager().isNotifyOps() ? "ENABLED" : "DISABLED", NamedTextColor.AQUA)));
    }

    private void handleLive(CommandSender sender, String[] args) {
        if (args.length > 1) {
            boolean enabled = "on".equalsIgnoreCase(args[1]) || "true".equalsIgnoreCase(args[1]);
            plugin.getHistoryManager().setLiveLogConsole(enabled);
        } else {
            plugin.getHistoryManager().setLiveLogConsole(!plugin.getHistoryManager().isLiveLogConsole());
        }
        sender.sendMessage(Component.text("Console live logging is now: ", NamedTextColor.GOLD)
                .append(Component.text(plugin.getHistoryManager().isLiveLogConsole() ? "ENABLED" : "DISABLED",
                        plugin.getHistoryManager().isLiveLogConsole() ? NamedTextColor.GREEN : NamedTextColor.RED)));
    }

    private void handleNotify(CommandSender sender, String[] args) {
        if (args.length > 1) {
            boolean enabled = "on".equalsIgnoreCase(args[1]) || "true".equalsIgnoreCase(args[1]);
            plugin.getHistoryManager().setNotifyOps(enabled);
        } else {
            plugin.getHistoryManager().setNotifyOps(!plugin.getHistoryManager().isNotifyOps());
        }
        sender.sendMessage(Component.text("In-game op alerts are now: ", NamedTextColor.GOLD)
                .append(Component.text(plugin.getHistoryManager().isNotifyOps() ? "ENABLED" : "DISABLED",
                        plugin.getHistoryManager().isNotifyOps() ? NamedTextColor.GREEN : NamedTextColor.RED)));
    }

    private void handleList(CommandSender sender, String[] args) {
        int page = 1;
        if (args.length > 1) {
            try {
                page = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException ignored) {}
        }

        List<CapturedRequest> requests = plugin.getHistoryManager().getRecentRequests(100);
        if (requests.isEmpty()) {
            sender.sendMessage(Component.text("No HTTP requests captured yet.", NamedTextColor.GRAY));
            return;
        }

        int pageSize = 8;
        int totalPages = (int) Math.ceil((double) requests.size() / pageSize);
        if (page > totalPages) page = totalPages;

        sender.sendMessage(Component.text("--- Captured Requests (Page " + page + "/" + totalPages + ") ---", NamedTextColor.GOLD));

        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, requests.size());

        for (int i = start; i < end; i++) {
            CapturedRequest r = requests.get(i);
            NamedTextColor tagColor = r.isLoopbacked() ? NamedTextColor.GOLD
                    : (r.isModified() ? NamedTextColor.AQUA : NamedTextColor.GREEN);
            String tag = r.isLoopbacked() ? "[LOOPBACK]" : (r.isModified() ? "[MODIFIED]" : "[REAL]");

            Component line = Component.text("#" + r.getId() + " ", NamedTextColor.YELLOW, TextDecoration.BOLD)
                    .hoverEvent(HoverEvent.showText(Component.text("Click to inspect request #" + r.getId())))
                    .clickEvent(ClickEvent.runCommand("/http view " + r.getId()))
                    .append(Component.text("[" + r.getPluginName() + "] ", NamedTextColor.GRAY))
                    .append(Component.text(r.getMethod() + " ", NamedTextColor.WHITE))
                    .append(Component.text(truncate(r.getUrl(), 32) + " ", NamedTextColor.DARK_AQUA))
                    .append(Component.text(tag + " ", tagColor))
                    .append(Component.text(r.getResponseCode() + " ", NamedTextColor.WHITE))
                    .append(Component.text("(" + r.getDurationMs() + "ms)", NamedTextColor.DARK_GRAY));

            sender.sendMessage(line);
        }
    }

    private void handleView(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /http view <id>", NamedTextColor.RED));
            return;
        }

        long id;
        try {
            id = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid ID: " + args[1], NamedTextColor.RED));
            return;
        }

        CapturedRequest req = plugin.getHistoryManager().getById(id);
        if (req == null) {
            sender.sendMessage(Component.text("Request #" + id + " not found in history.", NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.text("================ [ Request #" + req.getId() + " ] ================", NamedTextColor.GOLD));
        sender.sendMessage(detail("Timestamp", req.getFormattedTime()));
        sender.sendMessage(detail("Calling Plugin", req.getPluginName()));
        sender.sendMessage(detail("Thread", req.getThreadName()));
        sender.sendMessage(detail("URL", req.getUrl()));
        sender.sendMessage(detail("Method", req.getMethod()));
        sender.sendMessage(detail("Status Code", req.getResponseCode() + (req.isLoopbacked() ? " (MOCKED)" : "")));
        sender.sendMessage(detail("Duration", req.getDurationMs() + " ms"));
        if (req.getMatchedRuleName() != null) {
            sender.sendMessage(detail("Matched Rule", req.getMatchedRuleName()));
        }
        sender.sendMessage(detail("Loopbacked", String.valueOf(req.isLoopbacked())));
        sender.sendMessage(detail("Modified", String.valueOf(req.isModified())));

        sender.sendMessage(Component.text("--- Request Headers ---", NamedTextColor.YELLOW));
        if (req.getRequestHeaders().isEmpty()) {
            sender.sendMessage(Component.text(" (none)", NamedTextColor.DARK_GRAY));
        } else {
            req.getRequestHeaders().forEach((k, v) -> sender.sendMessage(Component.text(" " + k + ": ", NamedTextColor.AQUA).append(Component.text(String.join(", ", v), NamedTextColor.GRAY))));
        }

        if (req.getRequestBody() != null && !req.getRequestBody().isEmpty()) {
            sender.sendMessage(Component.text("--- Request Body ---", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text(req.getRequestBody(), NamedTextColor.WHITE));
        }

        sender.sendMessage(Component.text("--- Response Headers ---", NamedTextColor.YELLOW));
        if (req.getResponseHeaders().isEmpty()) {
            sender.sendMessage(Component.text(" (none)", NamedTextColor.DARK_GRAY));
        } else {
            req.getResponseHeaders().forEach((k, v) -> sender.sendMessage(Component.text(" " + k + ": ", NamedTextColor.AQUA).append(Component.text(String.join(", ", v), NamedTextColor.GRAY))));
        }

        sender.sendMessage(Component.text("--- Response Body ---", NamedTextColor.YELLOW));
        if (req.getResponseBody() != null && !req.getResponseBody().isEmpty()) {
            sender.sendMessage(Component.text(req.getResponseBody(), NamedTextColor.WHITE));
        } else {
            sender.sendMessage(Component.text(" (empty)", NamedTextColor.DARK_GRAY));
        }
    }

    private void handleRules(CommandSender sender) {
        List<HttpRule> rules = plugin.getRuleManager().getRules();
        sender.sendMessage(Component.text("=== Configured Rules (" + rules.size() + ") ===", NamedTextColor.GOLD));
        if (rules.isEmpty()) {
            sender.sendMessage(Component.text("No rules defined in config.yml.", NamedTextColor.GRAY));
            return;
        }

        for (HttpRule r : rules) {
            NamedTextColor stateColor = r.isEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED;
            Component line = Component.text("- " + r.getName() + " ", NamedTextColor.YELLOW)
                    .append(Component.text("[" + (r.isEnabled() ? "ENABLED" : "DISABLED") + "] ", stateColor))
                    .append(Component.text("Plugin: " + r.getTargetPlugin() + " ", NamedTextColor.AQUA))
                    .append(Component.text("Action: " + r.getAction().name() + " ", NamedTextColor.GOLD))
                    .append(Component.text("Pattern: " + r.getUrlPattern().pattern(), NamedTextColor.GRAY));
            sender.sendMessage(line);
        }
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        plugin.loadSettings();
        plugin.getRuleManager().loadRules();
        sender.sendMessage(Component.text("HttpInterceptor configuration & rules reloaded successfully!", NamedTextColor.GREEN));
    }

    private void handleTest(CommandSender sender, String[] args) {
        String testUrl = args.length > 1 ? args[1] : "https://api.example.com/v1/auth";
        String simulatePlugin = args.length > 2 ? args[2] : null;
        sender.sendMessage(Component.text("Dispatching test HTTP GET request to: " + testUrl
                + (simulatePlugin != null ? " (simulating plugin: " + simulatePlugin + ")" : ""), NamedTextColor.YELLOW));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (simulatePlugin != null) {
                    com.example.httpinterceptor.util.CallerIdentifier.SIMULATED_PLUGIN.set(simulatePlugin);
                }
                URL url = new URL(testUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                conn.setRequestProperty("User-Agent", "MinecraftServer-HttpInterceptor-Test");

                int code = conn.getResponseCode();
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        code >= 400 && conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                }

                plugin.getLogger().info("[Test Result] HTTP Status: " + code);
                plugin.getLogger().info("[Test Result] Body: " + sb.toString().trim());
                sender.sendMessage(Component.text("[Test Result] HTTP Status: " + code, NamedTextColor.GREEN));
                sender.sendMessage(Component.text("[Test Result] Body: " + sb.toString().trim(), NamedTextColor.WHITE));
            } catch (Exception e) {
                plugin.getLogger().warning("[Test Error] " + e.getMessage());
                sender.sendMessage(Component.text("[Test Error] " + e.getMessage(), NamedTextColor.RED));
            } finally {
                com.example.httpinterceptor.util.CallerIdentifier.SIMULATED_PLUGIN.remove();
            }
        });
    }

    private void handleTestAll(CommandSender sender) {
        sender.sendMessage(Component.text("Running HttpInterceptor Automated Verification Suite...", NamedTextColor.GOLD, TextDecoration.BOLD));
        plugin.getLogger().info("=== Running HttpInterceptor Automated Verification Suite ===");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int passed = 0;
            int total = 5;

            // TEST 1: Loopback JSON Response
            try {
                com.example.httpinterceptor.util.CallerIdentifier.SIMULATED_PLUGIN.set("LuckSMP");
                HttpURLConnection conn = (HttpURLConnection) new URL("https://api.example.com/v1/auth").openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.getOutputStream().write("{\"test\":\"auth\"}".getBytes(StandardCharsets.UTF_8));
                int code = conn.getResponseCode();
                String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                String contentType = conn.getHeaderField("Content-Type");

                if (code == 200 && body.contains("mock-token-xyz") && "application/json".equalsIgnoreCase(contentType)) {
                    passed++;
                    sender.sendMessage(Component.text("✔ [PASS] Test 1: Loopback Mock Response (Code 200, JSON, Body match)", NamedTextColor.GREEN));
                    plugin.getLogger().info("✔ [PASS] Test 1: Loopback Mock Response (Code 200, JSON, Body match)");
                } else {
                    sender.sendMessage(Component.text("✘ [FAIL] Test 1: Loopback Mock Response (code=" + code + ", body=" + body + ")", NamedTextColor.RED));
                    plugin.getLogger().warning("✘ [FAIL] Test 1: Loopback Mock Response (code=" + code + ", body=" + body + ")");
                }
            } catch (Exception e) {
                sender.sendMessage(Component.text("✘ [FAIL] Test 1: Loopback Mock Response error: " + e.getMessage(), NamedTextColor.RED));
                plugin.getLogger().warning("✘ [FAIL] Test 1: Loopback Mock Response error: " + e.getMessage());
            } finally {
                com.example.httpinterceptor.util.CallerIdentifier.SIMULATED_PLUGIN.remove();
            }

            // TEST 2: Per-Plugin Isolation Check
            try {
                com.example.httpinterceptor.util.CallerIdentifier.SIMULATED_PLUGIN.set("UnrelatedPlugin");
                HttpRule matched = plugin.getRuleManager().findMatchingRule("UnrelatedPlugin", "https://api.example.com/v1/auth", "POST");
                if (matched == null) {
                    passed++;
                    sender.sendMessage(Component.text("✔ [PASS] Test 2: Per-Plugin Rule Isolation (UnrelatedPlugin is NOT intercepted by LuckSMP rule)", NamedTextColor.GREEN));
                    plugin.getLogger().info("✔ [PASS] Test 2: Per-Plugin Rule Isolation (UnrelatedPlugin is NOT intercepted by LuckSMP rule)");
                } else {
                    sender.sendMessage(Component.text("✘ [FAIL] Test 2: Isolation failed, matched rule: " + matched.getName(), NamedTextColor.RED));
                    plugin.getLogger().warning("✘ [FAIL] Test 2: Isolation failed, matched rule: " + matched.getName());
                }
            } catch (Exception e) {
                sender.sendMessage(Component.text("✘ [FAIL] Test 2: Isolation check error: " + e.getMessage(), NamedTextColor.RED));
                plugin.getLogger().warning("✘ [FAIL] Test 2: Isolation check error: " + e.getMessage());
            } finally {
                com.example.httpinterceptor.util.CallerIdentifier.SIMULATED_PLUGIN.remove();
            }

            // TEST 3: Response Mutation (MODIFY action)
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL("https://icanhazip.com").openConnection();
                conn.setRequestProperty("User-Agent", "MinecraftServer-HttpInterceptor-Test");
                int code = conn.getResponseCode();
                String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();

                if (code == 201 && body.contains("SPOOFED BY HTTP INTERCEPTOR")) {
                    passed++;
                    sender.sendMessage(Component.text("✔ [PASS] Test 3: Response Modification (Status overridden to 201, Body text regex-mutated)", NamedTextColor.GREEN));
                    plugin.getLogger().info("✔ [PASS] Test 3: Response Modification (Status overridden to 201, Body text regex-mutated)");
                } else {
                    sender.sendMessage(Component.text("✘ [FAIL] Test 3: Response Modification (code=" + code + ", body=" + body + ")", NamedTextColor.RED));
                    plugin.getLogger().warning("✘ [FAIL] Test 3: Response Modification (code=" + code + ", body=" + body + ")");
                }
            } catch (Exception e) {
                sender.sendMessage(Component.text("✘ [FAIL] Test 3: Response Modification error: " + e.getMessage(), NamedTextColor.RED));
                plugin.getLogger().warning("✘ [FAIL] Test 3: Response Modification error: " + e.getMessage());
            }

            // TEST 4: POST Request Body Capture
            try {
                String testPayload = "{\"session_key\":\"unique-secret-999\",\"action\":\"verify\"}";
                com.example.httpinterceptor.util.CallerIdentifier.SIMULATED_PLUGIN.set("LuckSMP");
                HttpURLConnection conn = (HttpURLConnection) new URL("https://api.example.com/v1/auth").openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.getOutputStream().write(testPayload.getBytes(StandardCharsets.UTF_8));
                conn.getResponseCode();
                conn.getInputStream().readAllBytes();

                CapturedRequest lastReq = plugin.getHistoryManager().getRecentRequests(1).isEmpty() ? null : plugin.getHistoryManager().getRecentRequests(1).get(0);
                if (lastReq != null && testPayload.equals(lastReq.getRequestBody())) {
                    passed++;
                    sender.sendMessage(Component.text("✔ [PASS] Test 4: Outgoing Request Body Capture (Captured verbatim in history buffer)", NamedTextColor.GREEN));
                    plugin.getLogger().info("✔ [PASS] Test 4: Outgoing Request Body Capture (Captured verbatim in history buffer)");
                } else {
                    sender.sendMessage(Component.text("✘ [FAIL] Test 4: Request Body not captured accurately: " + (lastReq != null ? lastReq.getRequestBody() : "null"), NamedTextColor.RED));
                    plugin.getLogger().warning("✘ [FAIL] Test 4: Request Body not captured accurately");
                }
            } catch (Exception e) {
                sender.sendMessage(Component.text("✘ [FAIL] Test 4: Request Body Capture error: " + e.getMessage(), NamedTextColor.RED));
                plugin.getLogger().warning("✘ [FAIL] Test 4: Request Body Capture error: " + e.getMessage());
            } finally {
                com.example.httpinterceptor.util.CallerIdentifier.SIMULATED_PLUGIN.remove();
            }

            // TEST 5: Multithreaded Concurrency Stress Test
            try {
                int threads = 8;
                int reqs = 40;
                ExecutorService exec = Executors.newFixedThreadPool(threads);
                AtomicInteger successfulConcurrent = new AtomicInteger();
                CountDownLatch latch = new CountDownLatch(reqs);

                for (int i = 0; i < reqs; i++) {
                    exec.submit(() -> {
                        try {
                            com.example.httpinterceptor.util.CallerIdentifier.SIMULATED_PLUGIN.set("LuckSMP");
                            HttpURLConnection c = (HttpURLConnection) new URL("https://api.example.com/v1/auth").openConnection();
                            int cCode = c.getResponseCode();
                            byte[] cBytes = c.getInputStream().readAllBytes();
                            if (cCode == 200 && cBytes.length > 0) {
                                successfulConcurrent.incrementAndGet();
                            }
                        } catch (Exception ignored) {
                        } finally {
                            com.example.httpinterceptor.util.CallerIdentifier.SIMULATED_PLUGIN.remove();
                            latch.countDown();
                        }
                    });
                }

                latch.await(10, TimeUnit.SECONDS);
                exec.shutdown();

                if (successfulConcurrent.get() == reqs) {
                    passed++;
                    sender.sendMessage(Component.text("✔ [PASS] Test 5: Concurrent Multithreading Stress (" + reqs + "/" + reqs + " parallel requests successful)", NamedTextColor.GREEN));
                    plugin.getLogger().info("✔ [PASS] Test 5: Concurrent Multithreading Stress (" + reqs + "/" + reqs + " parallel requests successful)");
                } else {
                    sender.sendMessage(Component.text("✘ [FAIL] Test 5: Concurrency failed: " + successfulConcurrent.get() + "/" + reqs, NamedTextColor.RED));
                    plugin.getLogger().warning("✘ [FAIL] Test 5: Concurrency failed");
                }
            } catch (Exception e) {
                sender.sendMessage(Component.text("✘ [FAIL] Test 5: Concurrency error: " + e.getMessage(), NamedTextColor.RED));
                plugin.getLogger().warning("✘ [FAIL] Test 5: Concurrency error: " + e.getMessage());
            }

            sender.sendMessage(Component.text("--------------------------------------------", NamedTextColor.GOLD));
            NamedTextColor summaryColor = passed == total ? NamedTextColor.GREEN : NamedTextColor.RED;
            sender.sendMessage(Component.text("Test Suite Result: " + passed + "/" + total + " Passed", summaryColor, TextDecoration.BOLD));
            plugin.getLogger().info("=== Test Suite Result: " + passed + "/" + total + " Passed ===");
        });
    }

    private void handleBenchmark(CommandSender sender, String[] args) {
        int requestCount = args.length > 1 ? Integer.parseInt(args[1]) : 500;
        int threads = args.length > 2 ? Integer.parseInt(args[2]) : 10;
        sender.sendMessage(Component.text("Starting HttpInterceptor Benchmark: " + requestCount + " loopback requests across " + threads + " threads...", NamedTextColor.GOLD));
        plugin.getLogger().info(String.format("[Benchmark] Starting: %d loopback requests across %d threads...", requestCount, threads));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean prevLive = plugin.getHistoryManager().isLiveLogConsole();
            plugin.getHistoryManager().setLiveLogConsole(false); // Mute live log to avoid console I/O bottleneck
            try {
                // 1. Warm-up
                for (int i = 0; i < 20; i++) {
                    com.example.httpinterceptor.util.CallerIdentifier.SIMULATED_PLUGIN.set("LuckSMP");
                    HttpURLConnection conn = (HttpURLConnection) new URL("https://api.example.com/v1/auth").openConnection();
                    conn.getResponseCode();
                    conn.getInputStream().readAllBytes();
                    com.example.httpinterceptor.util.CallerIdentifier.SIMULATED_PLUGIN.remove();
                }

                // 2. Microbenchmark StackWalker CallerIdentifier
                long swStart = System.nanoTime();
                int swIterations = 5000;
                for (int i = 0; i < swIterations; i++) {
                    com.example.httpinterceptor.util.CallerIdentifier.getCallingPluginName();
                }
                long swElapsedNs = System.nanoTime() - swStart;
                double swAvgUs = (swElapsedNs / 1000.0) / swIterations;

                // 3. Multithreaded Loopback Benchmark
                ExecutorService executor = Executors.newFixedThreadPool(threads);
                List<Long> latenciesNs = Collections.synchronizedList(new ArrayList<>(requestCount));
                AtomicInteger successCount = new AtomicInteger();
                AtomicInteger failCount = new AtomicInteger();

                System.gc();
                long memBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                long totalStartNs = System.nanoTime();

                CountDownLatch latch = new CountDownLatch(requestCount);
                for (int i = 0; i < requestCount; i++) {
                    executor.submit(() -> {
                        long reqStart = System.nanoTime();
                        try {
                            com.example.httpinterceptor.util.CallerIdentifier.SIMULATED_PLUGIN.set("LuckSMP");
                            HttpURLConnection conn = (HttpURLConnection) new URL("https://api.example.com/v1/auth").openConnection();
                            conn.setRequestMethod("POST");
                            conn.setDoOutput(true);
                            conn.getOutputStream().write("{\"action\":\"benchmark\"}".getBytes(StandardCharsets.UTF_8));
                            int code = conn.getResponseCode();
                            byte[] resp = conn.getInputStream().readAllBytes();
                            long reqEnd = System.nanoTime();

                            if (code == 200 && resp.length > 0) {
                                successCount.incrementAndGet();
                                latenciesNs.add(reqEnd - reqStart);
                            } else {
                                failCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            failCount.incrementAndGet();
                        } finally {
                            com.example.httpinterceptor.util.CallerIdentifier.SIMULATED_PLUGIN.remove();
                            latch.countDown();
                        }
                    });
                }

                latch.await(30, TimeUnit.SECONDS);
                long totalElapsedNs = System.nanoTime() - totalStartNs;
                executor.shutdown();

                long memAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                double totalElapsedSec = totalElapsedNs / 1_000_000_000.0;
                double reqPerSec = successCount.get() / totalElapsedSec;

                // Calculate percentiles
                Collections.sort(latenciesNs);
                double minUs = latenciesNs.isEmpty() ? 0 : latenciesNs.get(0) / 1000.0;
                double maxUs = latenciesNs.isEmpty() ? 0 : latenciesNs.get(latenciesNs.size() - 1) / 1000.0;
                double avgUs = latenciesNs.isEmpty() ? 0 : (latenciesNs.stream().mapToLong(Long::longValue).average().orElse(0) / 1000.0);
                double p50Us = percentile(latenciesNs, 50) / 1000.0;
                double p95Us = percentile(latenciesNs, 95) / 1000.0;
                double p99Us = percentile(latenciesNs, 99) / 1000.0;

                // Output results to sender
                sender.sendMessage(Component.text("========== [ Benchmark Results ] ==========", NamedTextColor.GOLD, TextDecoration.BOLD));
                sender.sendMessage(detail("Total Requests", successCount.get() + " / " + requestCount + " (" + failCount.get() + " failed)"));
                sender.sendMessage(detail("Total Time", String.format("%.2f s", totalElapsedSec)));
                sender.sendMessage(detail("Throughput", String.format("%,.0f req/sec", reqPerSec)));
                sender.sendMessage(detail("Latency (Min / Avg / Max)", String.format("%.1f µs / %.1f µs / %.1f µs", minUs, avgUs, maxUs)));
                sender.sendMessage(detail("Percentiles (P50 / P95 / P99)", String.format("%.1f µs / %.1f µs / %.1f µs", p50Us, p95Us, p99Us)));
                sender.sendMessage(detail("StackWalker Overhead", String.format("%.2f µs / call (%d iterations)", swAvgUs, swIterations)));
                sender.sendMessage(detail("Memory Delta", String.format("%+.2f MB", (memAfter - memBefore) / (1024.0 * 1024.0))));
                sender.sendMessage(Component.text("===========================================", NamedTextColor.GOLD));

                // Output results to console log
                plugin.getLogger().info("========== [ Benchmark Results ] ==========");
                plugin.getLogger().info("Total Requests: " + successCount.get() + " / " + requestCount + " (" + failCount.get() + " failed)");
                plugin.getLogger().info("Total Time: " + String.format("%.2f s", totalElapsedSec));
                plugin.getLogger().info("Throughput: " + String.format("%,.0f req/sec", reqPerSec));
                plugin.getLogger().info("Latency (Min / Avg / Max): " + String.format("%.1f µs / %.1f µs / %.1f µs", minUs, avgUs, maxUs));
                plugin.getLogger().info("Percentiles (P50 / P95 / P99): " + String.format("%.1f µs / %.1f µs / %.1f µs", p50Us, p95Us, p99Us));
                plugin.getLogger().info("StackWalker Overhead: " + String.format("%.2f µs / call (%d iterations)", swAvgUs, swIterations));
                plugin.getLogger().info("Memory Delta: " + String.format("%+.2f MB", (memAfter - memBefore) / (1024.0 * 1024.0)));
                plugin.getLogger().info("===========================================");
            } catch (Exception e) {
                sender.sendMessage(Component.text("Benchmark failed: " + e.getMessage(), NamedTextColor.RED));
                plugin.getLogger().severe("Benchmark error: " + e.getMessage());
            } finally {
                plugin.getHistoryManager().setLiveLogConsole(prevLive);
            }
        });
    }

    private static long percentile(List<Long> sortedList, double p) {
        if (sortedList.isEmpty()) return 0;
        int idx = (int) Math.ceil((p / 100.0) * sortedList.size()) - 1;
        return sortedList.get(Math.max(0, Math.min(idx, sortedList.size() - 1)));
    }

    private void handleClear(CommandSender sender) {
        plugin.getHistoryManager().clear();
        sender.sendMessage(Component.text("Captured request history cleared.", NamedTextColor.GREEN));
    }

    private Component detail(String key, String value) {
        return Component.text(key + ": ", NamedTextColor.YELLOW)
                .append(Component.text(value != null ? value : "null", NamedTextColor.WHITE));
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> sub = Arrays.asList("status", "list", "view", "rules", "live", "notify", "test", "test-all", "benchmark", "reload", "clear");
            return sub.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2 && ("live".equalsIgnoreCase(args[0]) || "notify".equalsIgnoreCase(args[0]))) {
            return Arrays.asList("on", "off");
        }
        if (args.length == 3 && "test".equalsIgnoreCase(args[0])) {
            return Arrays.stream(Bukkit.getPluginManager().getPlugins())
                    .map(org.bukkit.plugin.Plugin::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
