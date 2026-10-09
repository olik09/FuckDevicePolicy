package com.strawing.fuckdevicepolicy;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.snackbar.Snackbar;
import com.strawing.fuckdevicepolicy.databinding.ActivityMainBinding;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public class MainActivity extends AppCompatActivity {

    private static final String LOCAL_PREFS = "policies_local";

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;
    private SharedPreferences localPrefs;
    private EditText policiesEdit;
    private TextView statusText;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private volatile XposedService service;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        TextView help = findViewById(R.id.textview_first);
        help.setMovementMethod(LinkMovementMethod.getInstance());
        statusText = findViewById(R.id.textview_mode);
        policiesEdit = findViewById(R.id.editTextTextMultiLine);

        localPrefs = getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE);
        migrateOldPolicies();
        policiesEdit.setText(localPrefs.getString(MainModule.PREFS_KEY, MainModule.DEFAULT_POLICIES));
        showStatus(false);

        // The framework calls this when it connects to this app (may be on a background thread).
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override
            public void onServiceBind(XposedService s) {
                service = s;
                io.execute(() -> syncOnBind(s));
            }

            @Override
            public void onServiceDied(XposedService s) {
                service = null;
                runOnUiThread(() -> showStatus(false));
            }
        });

        binding.fab.setOnClickListener(view -> {
            String text = policiesEdit.getText().toString().trim().replaceAll("(?m)^[ \\t]*\\r?\\n", "");
            localPrefs.edit().putString(MainModule.PREFS_KEY, text).apply();
            policiesEdit.setText(text);

            XposedService s = service;
            if (s == null) {
                Snackbar.make(view, R.string.saved_local_only, Snackbar.LENGTH_LONG).show();
                return;
            }
            io.execute(() -> {
                boolean ok = pushToFramework(s, text);
                runOnUiThread(() -> Snackbar.make(view,
                        ok ? R.string.saved_applied : R.string.save_failed,
                        Snackbar.LENGTH_LONG).show());
            });
        });
    }

    /** Local copy is the source of truth; if it is empty, adopt what the framework has. */
    private void syncOnBind(XposedService s) {
        try {
            SharedPreferences remote = s.getRemotePreferences(MainModule.PREFS_GROUP);
            String local = localPrefs.getString(MainModule.PREFS_KEY, null);
            if (local != null) {
                pushToFramework(s, local);
            } else {
                String r = remote.getString(MainModule.PREFS_KEY, null);
                if (r != null) {
                    localPrefs.edit().putString(MainModule.PREFS_KEY, r).apply();
                    runOnUiThread(() -> policiesEdit.setText(r));
                }
            }
        } catch (Throwable ignored) {
        }
        runOnUiThread(() -> showStatus(true));
    }

    private boolean pushToFramework(XposedService s, String text) {
        try {
            return s.getRemotePreferences(MainModule.PREFS_GROUP)
                    .edit().putString(MainModule.PREFS_KEY, text).commit();
        } catch (Throwable t) {
            return false;
        }
    }

    /** One-time import from the pre-2.0 world-readable file in device-protected storage. */
    private void migrateOldPolicies() {
        if (localPrefs.contains(MainModule.PREFS_KEY)) return;
        try {
            SharedPreferences old = createDeviceProtectedStorageContext()
                    .getSharedPreferences("policies", Context.MODE_PRIVATE);
            String v = old.getString(MainModule.PREFS_KEY, null);
            if (v != null) localPrefs.edit().putString(MainModule.PREFS_KEY, v).apply();
        } catch (Throwable ignored) {
        }
    }

    private void showStatus(boolean connected) {
        statusText.setText(Html.fromHtml(getString(connected ? R.string.status_connected : R.string.status_disconnected),
                Html.FROM_HTML_MODE_LEGACY));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }
}
