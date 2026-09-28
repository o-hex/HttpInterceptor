package com.example.httpinterceptor.advice;

import net.bytebuddy.asm.Advice;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

public class HeaderAdvice {

    public static class GetHeaderFieldsAdvice {
        @Advice.OnMethodExit
        public static void onExit(
                @Advice.This Object conn,
                @Advice.Return(readOnly = false) Map<String, List<String>> retVal) {
            try {
                Object bridge = System.getProperties().get("httpinterceptor.bridge");
                if (bridge != null) {
                    Method m = bridge.getClass().getMethod("handleHeaderFieldsExit", Object.class, Map.class);
                    Object res = m.invoke(bridge, conn, retVal);
                    if (res instanceof Map) {
                        retVal = (Map<String, List<String>>) res;
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    public static class GetHeaderFieldAdvice {
        @Advice.OnMethodExit
        public static void onExit(
                @Advice.This Object conn,
                @Advice.Argument(0) String name,
                @Advice.Return(readOnly = false) String retVal) {
            try {
                Object bridge = System.getProperties().get("httpinterceptor.bridge");
                if (bridge != null) {
                    Method m = bridge.getClass().getMethod("handleHeaderFieldExit", Object.class, String.class, String.class);
                    Object res = m.invoke(bridge, conn, name, retVal);
                    if (res instanceof String s) {
                        retVal = s;
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    public static class GetHeaderFieldIntAdvice {
        @Advice.OnMethodExit
        public static void onExit(
                @Advice.This Object conn,
                @Advice.Argument(0) int index,
                @Advice.Return(readOnly = false) String retVal) {
            try {
                Object bridge = System.getProperties().get("httpinterceptor.bridge");
                if (bridge != null) {
                    Method m = bridge.getClass().getMethod("handleHeaderFieldIntExit", Object.class, int.class, String.class);
                    Object res = m.invoke(bridge, conn, index, retVal);
                    if (res instanceof String s) {
                        retVal = s;
                    }
                }
            } catch (Throwable ignored) {}
        }
    }
}
