package com.strawing.fuckdevicepolicy;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/** App-wide holder for the framework connection and the saved policy text. */
final class XposedLink {

    interface Listener {
        void onChanged();
    }

    private static final String LOCAL_PREFS = "policies_local";

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static volatile XposedService service;
    private static volatile Listener listener;
    private static volatile boolean pulled;
    private static SharedPreferences local;
    private static boolean initialized;

    private XposedLink() {}

    static synchronized void init(Context appContext) {
        if (initialized) return;
        initialized = true;
        local = appContext.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE);
        migrateOldPolicies(appContext);

        // The framework calls this when it connects to this app (may be a background thread).
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override
            public void onServiceBind(XposedService s) {
                service = s;
                IO.execute(() -> syncOnBind(s));
            }

            @Override
            public void onServiceDied(XposedService s) {
                service = null;
                notifyChanged();
            }
        });
    }

    static boolean isConnected() {
        return service != null;
    }

    static void setListener(Listener l) {
        listener = l;
    }

    static boolean consumePulled() {
        boolean p = pulled;
        pulled = false;
        return p;
    }

    static String loadLocal() {
        return local.getString(MainModule.PREFS_KEY, MainModule.DEFAULT_POLICIES);
    }

    static void saveLocal(String text) {
        local.edit().putString(MainModule.PREFS_KEY, text).apply();
    }

    /** Sends the text to the framework in the background; callback gets true on success. */
    static void pushAsync(String text, Consumer<Boolean> done) {
        final XposedService s = service;
        IO.execute(() -> done.accept(s != null && push(s, text)));
    }

    private static boolean push(XposedService s, String text) {
        try {
            return s.getRemotePreferences(MainModule.PREFS_GROUP)
                    .edit().putString(MainModule.PREFS_KEY, text).commit();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Local copy is the source of truth; if it is empty, adopt what the framework has. */
    private static void syncOnBind(XposedService s) {
        try {
            String mine = local.getString(MainModule.PREFS_KEY, null);
            if (mine != null) {
                push(s, mine);
            } else {
                String theirs = s.getRemotePreferences(MainModule.PREFS_GROUP)
                        .getString(MainModule.PREFS_KEY, null);
                if (theirs != null) {
                    saveLocal(theirs);
                    pulled = true;
                }
            }
        } catch (Throwable ignored) {
        }
        notifyChanged();
    }

    private static void notifyChanged() {
        Listener l = listener;
        if (l != null) l.onChanged();
    }

    /** One-time import from the pre-2.0 world-readable file in device-protected storage. */
    private static void migrateOldPolicies(Context ctx) {
        if (local.contains(MainModule.PREFS_KEY)) return;
        try {
            SharedPreferences old = ctx.createDeviceProtectedStorageContext()
                    .getSharedPreferences("policies", Context.MODE_PRIVATE);
            String v = old.getString(MainModule.PREFS_KEY, null);
            if (v != null) saveLocal(v);
        } catch (Throwable ignored) {
        }
    }
}
