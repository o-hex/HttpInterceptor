package com.example.httpinterceptor.advice;

import net.bytebuddy.asm.Advice;
import java.lang.reflect.Method;

public class ConnectAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static boolean onEnter(@Advice.This Object conn) {
        try {
            Object bridge = System.getProperties().get("httpinterceptor.bridge");
            if (bridge != null) {
                Method m = bridge.getClass().getMethod("handleConnectEnter", Object.class);
                Object res = m.invoke(bridge, conn);
                if (res instanceof Boolean b && b) {
                    return true; // Skip connect() on loopback!
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }
}
