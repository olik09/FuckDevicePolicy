package com.strawing.fuckdevicepolicy;

import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;
import com.strawing.fuckdevicepolicy.databinding.FragmentFirstBinding;

public class FirstFragment extends Fragment {

    private FragmentFirstBinding binding;
    private View fab;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentFirstBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.textviewFirst.setMovementMethod(LinkMovementMethod.getInstance());
        binding.editTextTextMultiLine.setText(XposedLink.loadLocal());
        showStatus();

        XposedLink.setListener(() -> view.post(() -> {
            if (binding == null) return;
            showStatus();
            if (XposedLink.consumePulled()) {
                binding.editTextTextMultiLine.setText(XposedLink.loadLocal());
            }
        }));

        fab = requireActivity().findViewById(R.id.fab);
        fab.setOnClickListener(v -> {
            String text = binding.editTextTextMultiLine.getText().toString()
                    .trim().replaceAll("(?m)^[ \\t]*\\r?\\n", "");
            XposedLink.saveLocal(text);
            binding.editTextTextMultiLine.setText(text);

            if (!XposedLink.isConnected()) {
                Snackbar.make(v, R.string.saved_local_only, Snackbar.LENGTH_LONG).show();
                return;
            }
            XposedLink.pushAsync(text, ok -> view.post(() ->
                    Snackbar.make(v, ok ? R.string.saved_applied : R.string.save_failed,
                            Snackbar.LENGTH_LONG).show()));
        });
    }

    private void showStatus() {
        int res = XposedLink.isConnected() ? R.string.status_connected : R.string.status_disconnected;
        binding.textviewMode.setText(Html.fromHtml(getString(res), Html.FROM_HTML_MODE_LEGACY));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        XposedLink.setListener(null);
        if (fab != null) fab.setOnClickListener(null);
        fab = null;
        binding = null;
    }
}
