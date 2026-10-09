package com.strawing.fuckdevicepolicy;

import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam;

/**
 * Modern Xposed (libxposed API 102) entry. Runs inside system_server and makes the
 * chosen user restrictions look "not set" to the rest of Android.
 */
public class MainModule extends XposedModule {

    private static final String TAG = "FuckDevicePolicy";
    static final String PREFS_GROUP = "policies";
    static final String PREFS_KEY = "policies_string";
    static final String DEFAULT_POLICIES = "no_install_unknown_sources_globally\nno_debugging_features";

    /** Immutable pair so the raw text and the parsed set are always consistent. */
    private static final class Snapshot {
        final String raw;
        final Set<String> policies;

        Snapshot(String raw, Set<String> policies) {
            this.raw = raw;
            this.policies = policies;
        }
    }

    private volatile Snapshot snapshot = new Snapshot(null, Collections.emptySet());
    private SharedPreferences prefs;

    @Override
    public void onSystemServerStarting(@NonNull SystemServerStartingParam param) {
        try {
            prefs = getRemotePreferences(PREFS_GROUP); // read-only on this side
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Remote preferences unavailable, using default policies", t);
        }
        log(Log.INFO, TAG, "Fuck these policies: " + currentPolicies());

        final Class<?> ums;
        try {
            ums = Class.forName("com.android.server.pm.UserManagerService", false, param.getClassLoader());
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "UserManagerService not found", t);
            return;
        }

        hookPolicyMethod(ums, "getUserRestrictionSource", new Class<?>[]{String.class, int.class}, 0);
        hookPolicyMethod(ums, "getUserRestrictionSources", new Class<?>[]{String.class, int.class}, Collections.emptyList());
        hookPolicyMethod(ums, "hasUserRestriction", new Class<?>[]{String.class, int.class}, Boolean.FALSE);
        hookPolicyMethod(ums, "hasUserRestrictionOnAnyUser", new Class<?>[]{String.class}, Boolean.FALSE);
        log(Log.INFO, TAG, "Hooks applied.");
    }

    private void hookPolicyMethod(Class<?> cls, String name, Class<?>[] params, final Object blockedResult) {
        try {
            Method m = cls.getDeclaredMethod(name, params);
            hook(m).intercept(chain -> {
                Object key = chain.getArg(0);
                if (key instanceof String && currentPolicies().contains(key)) {
                    return blockedResult;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Error on apply hook [" + name + "]", t);
        }
    }

    /** Re-parses only when the saved text changed, so edits in the app apply live. */
    private Set<String> currentPolicies() {
        String raw = DEFAULT_POLICIES;
        if (prefs != null) {
            try {
                raw = prefs.getString(PREFS_KEY, DEFAULT_POLICIES);
            } catch (Throwable ignored) {
                Snapshot last = snapshot;
                if (last.raw != null) return last.policies;
            }
        }
        Snapshot cur = snapshot;
        if (raw.equals(cur.raw)) return cur.policies;

        Set<String> set = new HashSet<>();
        for (String line : raw.split("\n")) {
            String p = line.trim();
            if (!p.isEmpty()) set.add(p);
        }
        snapshot = new Snapshot(raw, Collections.unmodifiableSet(set));
        return snapshot.policies;
    }
}
