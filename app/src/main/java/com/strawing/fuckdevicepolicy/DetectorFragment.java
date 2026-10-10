package com.strawing.fuckdevicepolicy;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.strawing.fuckdevicepolicy.databinding.FragmentDetectorBinding;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DetectorFragment extends Fragment {

    static final String PREFS = "settings";
    static final String KEY_NIGHT = "night_mode";

    // Kept across page switches so the last scan is still there when you come back.
    private static String lastResult = "";
    private static String lastShown = null;

    private FragmentDetectorBinding binding;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentDetectorBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        binding.switchTheme.setChecked(prefs.getBoolean(KEY_NIGHT, isSystemInDarkMode()));
        binding.switchTheme.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean(KEY_NIGHT, isChecked).apply();
            AppCompatDelegate.setDefaultNightMode(
                    isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        });

        if (lastShown != null) binding.txtResults.setText(lastShown);
        checkRootStatus();

        binding.btnGrab.setOnClickListener(v -> {
            binding.txtResults.setText("Requesting root, running dumpsys device_policy...");
            io.execute(() -> {
                String raw = RootPolicyScanner.dump();
                String parsed = RestrictionOutputParser.extractRestrictionNames(raw);
                main.post(() -> {
                    lastResult = parsed;
                    lastShown = parsed.isEmpty() ? "(no restrictions found)" : parsed;
                    if (binding == null) return;
                    binding.txtResults.setText(lastShown);
                    checkRootStatus();
                });
            });
        });

        binding.btnCopy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager)
                    requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("restrictions", lastResult));
            Toast.makeText(requireContext(), "Copied", Toast.LENGTH_SHORT).show();
        });
    }

    private void checkRootStatus() {
        io.execute(() -> {
            boolean granted = RootPolicyScanner.hasRootAccess();
            main.post(() -> {
                if (binding == null || !isAdded()) return;
                binding.txtRootStatus.setText(granted ? "Root granted" : "Root not granted");
                binding.txtRootStatus.setTextColor(ContextCompat.getColor(requireContext(),
                        granted ? R.color.shiny_green : R.color.shiny_red));
            });
        });
    }

    private boolean isSystemInDarkMode() {
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }
}
