package com.example.httpinterceptor.manager;

import com.example.httpinterceptor.HttpInterceptorPlugin;
import com.example.httpinterceptor.model.CapturedRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class CaptureHistoryManager {

    private final HttpInterceptorPlugin plugin;
    private final AtomicLong idGenerator = new AtomicLong(1);
    private final Deque<CapturedRequest> history = new ArrayDeque<>();
    private final Map<Long, CapturedRequest> idMap = new ConcurrentHashMap<>();

    private int historyLimit = 150;
    private boolean liveLogConsole = true;
    private boolean notifyOps = true;
    private int maxBodyChars = 10000;

    public CaptureHistoryManager(HttpInterceptorPlugin plugin) {
        this.plugin = plugin;
    }

    public void configure(int historyLimit, boolean liveLogConsole, boolean notifyOps, int maxBodyChars) {
        this.historyLimit = historyLimit;
        this.liveLogConsole = liveLogConsole;
        this.notifyOps = notifyOps;
        this.maxBodyChars = maxBodyChars;
    }

    public long nextId() {
        return idGenerator.getAndIncrement();
    }

    public synchronized void recordRequest(CapturedRequest request) {
        if (request.getRequestBody() != null && request.getRequestBody().length() > maxBodyChars) {
            request.setRequestBody(request.getRequestBody().substring(0, maxBodyChars) + "... [truncated]");
        }
        if (request.getResponseBody() != null && request.getResponseBody().length() > maxBodyChars) {
            request.setResponseBody(request.getResponseBody().substring(0, maxBodyChars) + "... [truncated]");
        }

        history.addFirst(request);
        idMap.put(request.getId(), request);

        while (history.size() > historyLimit) {
            CapturedRequest removed = history.removeLast();
            idMap.remove(removed.getId());
        }

        // Live notification
        dispatchNotifications(request);
    }

    private void dispatchNotifications(CapturedRequest req) {
        String tag = req.isLoopbacked() ? "[LOOPBACK]" : (req.isModified() ? "[MODIFIED]" : "[REAL]");
        String message = String.format("%s #%d [%s] %s %s -> HTTP %d (%dms)",
                tag, req.getId(), req.getPluginName(), req.getMethod(), req.getUrl(), req.getResponseCode(), req.getDurationMs());

        if (liveLogConsole) {
            plugin.getLogger().info(message);
        }

        if (notifyOps && Bukkit.getServer() != null) {
            NamedTextColor color = req.isLoopbacked() ? NamedTextColor.GOLD
                    : (req.isModified() ? NamedTextColor.AQUA : NamedTextColor.GREEN);

            Component text = Component.text("[HTTP] ", NamedTextColor.DARK_GRAY)
                    .append(Component.text("#" + req.getId() + " ", NamedTextColor.GRAY))
                    .append(Component.text("[" + req.getPluginName() + "] ", NamedTextColor.YELLOW))
                    .append(Component.text(req.getMethod() + " ", NamedTextColor.WHITE))
                    .append(Component.text(req.getUrl() + " ", NamedTextColor.GRAY))
                    .append(Component.text("-> " + req.getResponseCode() + " ", color))
                    .append(Component.text(tag, color));

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("httpinterceptor.notify")) {
                    player.sendMessage(text);
                }
            }
        }
    }

    public synchronized List<CapturedRequest> getRecentRequests(int limit) {
        List<CapturedRequest> list = new ArrayList<>(Math.min(limit, history.size()));
        int count = 0;
        for (CapturedRequest req : history) {
            list.add(req);
            if (++count >= limit) break;
        }
        return list;
    }

    public CapturedRequest getById(long id) {
        return idMap.get(id);
    }

    public synchronized void clear() {
        history.clear();
        idMap.clear();
    }

    public int getCapturedCount() {
        return idMap.size();
    }

    public boolean isLiveLogConsole() {
        return liveLogConsole;
    }

    public void setLiveLogConsole(boolean liveLogConsole) {
        this.liveLogConsole = liveLogConsole;
    }

    public boolean isNotifyOps() {
        return notifyOps;
    }

    public void setNotifyOps(boolean notifyOps) {
        this.notifyOps = notifyOps;
    }
}
