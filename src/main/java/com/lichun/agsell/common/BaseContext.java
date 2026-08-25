package com.lichun.agsell.common;

public class BaseContext {

    public static ThreadLocal<Long> threadLocal = new ThreadLocal<>();
    public static ThreadLocal<String> jtiLocal = new ThreadLocal<>();

    public static void setCurrentId(Long id) {
        threadLocal.set(id);
    }

    public static void setCurrentId(Long id, String jti) {
        threadLocal.set(id);
        jtiLocal.set(jti);
    }

    public static Long getCurrentId() {
        return threadLocal.get();
    }

    public static String getCurrentJti() {
        return jtiLocal.get();
    }

    public static void removeCurrentId() {
        threadLocal.remove();
        jtiLocal.remove();
    }

}