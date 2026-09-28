package com.example.httpinterceptor.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.StackWalker.StackFrame;
import java.lang.reflect.Method;
import java.util.Optional;

public final class CallerIdentifier {

    private static final StackWalker WALKER =
            StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    public static final ThreadLocal<String> SIMULATED_PLUGIN = new ThreadLocal<>();

    private CallerIdentifier() {}

    public static String getCallingPluginName() {
        String sim = SIMULATED_PLUGIN.get();
        if (sim != null && !sim.isEmpty()) {
            return sim;
        }

        return WALKER.walk(frames -> {
            Optional<String> detected = frames
                .filter(CallerIdentifier::isRelevantFrame)
                .map(CallerIdentifier::resolvePluginFromFrame)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();

            return detected.orElse("Server/Unknown");
        });
    }

    private static boolean isRelevantFrame(StackFrame frame) {
        String className = frame.getClassName();
        // Ignore JDK internal, Netty, and HttpInterceptor's own classes
        return !className.startsWith("java.")
                && !className.startsWith("jdk.")
                && !className.startsWith("sun.")
                && !className.startsWith("com.example.httpinterceptor.")
                && !className.startsWith("net.bytebuddy.");
    }

    private static Optional<String> resolvePluginFromFrame(StackFrame frame) {
        Class<?> declaringClass = frame.getDeclaringClass();
        ClassLoader cl = declaringClass.getClassLoader();
        if (cl == null) {
            return Optional.empty();
        }

        // Method 1: Check if classloader is Bukkit PluginClassLoader via getPlugin()
        try {
            if (cl.getClass().getName().contains("PluginClassLoader")) {
                Method getPluginMethod = cl.getClass().getMethod("getPlugin");
                Object pluginObj = getPluginMethod.invoke(cl);
                if (pluginObj instanceof Plugin p) {
                    String name = p.getName();
                    if (!"HttpInterceptor".equalsIgnoreCase(name)) {
                        return Optional.of(name);
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Method 2: Match against registered Bukkit plugins' classloaders
        try {
            if (Bukkit.getServer() != null && Bukkit.getPluginManager() != null) {
                for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
                    if (p.getClass().getClassLoader() == cl) {
                        String name = p.getName();
                        if (!"HttpInterceptor".equalsIgnoreCase(name)) {
                            return Optional.of(name);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return Optional.empty();
    }
}
