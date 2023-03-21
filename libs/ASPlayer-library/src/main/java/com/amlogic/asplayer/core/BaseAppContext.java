package com.amlogic.asplayer.core;

import android.app.Application;
import android.content.Context;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class BaseAppContext {

    private static Context sAppContext;

    public static Context getAppContext() {
        if (sAppContext != null) {
            return sAppContext;
        }

        sAppContext = getAppByReflect();
        return sAppContext;
    }

    private static Application getAppByReflect() {
        try {
            Class cls = Class.forName("android.app.ActivityThread");
            Method method = cls.getMethod("currentApplication");
            method.setAccessible(true);
            Object application = method.invoke(null, (Object[])null);
            ASPlayerLog.i("getAppByReflect success, app: %s", application);
            return (Application) application;
        } catch (ClassNotFoundException e) {
            ASPlayerLog.w("getAppByReflect failed, ClassNotFoundException: %s", e != null ? e.getMessage() : "");
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            ASPlayerLog.w("getAppByReflect failed, NoSuchMethodException: %s", e != null ? e.getMessage() : "");
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            ASPlayerLog.w("getAppByReflect failed, IllegalAccessException: %s", e != null ? e.getMessage() : "");
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            ASPlayerLog.w("getAppByReflect failed, InvocationTargetException: %s", e != null ? e.getMessage() : "");
            e.printStackTrace();
        }
        return null;
    }
}
