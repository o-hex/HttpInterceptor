package com.example.httpinterceptor.advice;

import net.bytebuddy.asm.Advice;
import java.io.OutputStream;
import java.lang.reflect.Method;

public class OutputStreamAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static OutputStream onEnter(@Advice.This Object conn) {
        try {
            Object bridge = System.getProperties().get("httpinterceptor.bridge");
            if (bridge != null) {
                Method m = bridge.getClass().getMethod("handleOutputStreamEnter", Object.class);
                Object res = m.invoke(bridge, conn);
                if (res instanceof OutputStream os) return os;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    @Advice.OnMethodExit
    public static void onExit(
            @Advice.This Object conn,
            @Advice.Enter OutputStream enterVal,
            @Advice.Return(readOnly = false) OutputStream retVal) {
        try {
            if (enterVal != null) {
                retVal = enterVal;
                return;
            }
            Object bridge = System.getProperties().get("httpinterceptor.bridge");
            if (bridge != null) {
                Method m = bridge.getClass().getMethod("handleOutputStreamExit", Object.class, OutputStream.class);
                Object res = m.invoke(bridge, conn, retVal);
                if (res instanceof OutputStream os) {
                    retVal = os;
                }
            }
        } catch (Throwable ignored) {}
    }
}
