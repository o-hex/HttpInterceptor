package com.example.httpinterceptor.advice;

import net.bytebuddy.asm.Advice;
import java.lang.reflect.Method;

public class ResponseCodeAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static int onEnter(@Advice.This Object conn) {
        try {
            Object bridge = System.getProperties().get("httpinterceptor.bridge");
            if (bridge != null) {
                Method m = bridge.getClass().getMethod("handleResponseCodeEnter", Object.class);
                Object res = m.invoke(bridge, conn);
                if (res instanceof Integer i) return i;
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return 0;
    }

    @Advice.OnMethodExit
    public static void onExit(
            @Advice.This Object conn,
            @Advice.Enter int enterVal,
            @Advice.Return(readOnly = false) int retVal) {
        try {
            Object bridge = System.getProperties().get("httpinterceptor.bridge");
            if (bridge != null) {
                Method m = bridge.getClass().getMethod("handleResponseCodeExit", Object.class, int.class, int.class);
                Object res = m.invoke(bridge, conn, enterVal, retVal);
                if (res instanceof Integer i) {
                    retVal = i;
                }
            } else if (enterVal != 0) {
                retVal = enterVal;
            }
        } catch (Throwable t) {
            t.printStackTrace();
            if (enterVal != 0) {
                retVal = enterVal;
            }
        }
    }
}
