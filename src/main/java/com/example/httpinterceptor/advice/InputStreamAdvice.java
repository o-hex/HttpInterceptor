package com.example.httpinterceptor.advice;

import net.bytebuddy.asm.Advice;
import java.io.InputStream;
import java.lang.reflect.Method;

public class InputStreamAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static InputStream onEnter(@Advice.This Object conn) {
        try {
            Object bridge = System.getProperties().get("httpinterceptor.bridge");
            if (bridge != null) {
                Method m = bridge.getClass().getMethod("handleInputStreamEnter", Object.class);
                Object res = m.invoke(bridge, conn);
                if (res instanceof InputStream is) return is;
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return null;
    }

    @Advice.OnMethodExit
    public static void onExit(
            @Advice.This Object conn,
            @Advice.Enter InputStream enterVal,
            @Advice.Return(readOnly = false) InputStream retVal) {
        try {
            Object bridge = System.getProperties().get("httpinterceptor.bridge");
            if (bridge != null) {
                Method m = bridge.getClass().getMethod("handleInputStreamExit", Object.class, InputStream.class, InputStream.class);
                Object res = m.invoke(bridge, conn, enterVal, retVal);
                if (res instanceof InputStream is) {
                    retVal = is;
                }
            } else if (enterVal != null) {
                retVal = enterVal;
            }
        } catch (Throwable t) {
            t.printStackTrace();
            if (enterVal != null) {
                retVal = enterVal;
            }
        }
    }
}
