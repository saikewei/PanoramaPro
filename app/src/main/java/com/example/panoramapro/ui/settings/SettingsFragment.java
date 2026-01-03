package com.example.panoramapro.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.example.panoramapro.R;

public class SettingsFragment extends Fragment {

    public static final String PREFS_NAME = "panorama_prefs";
    public static final String KEY_STITCH_ALGO = "stitch_algo";
    public static final String KEY_COMPLETE_ALGO = "complete_algo";

    private SharedPreferences prefs;
    private TextView tvStitch, tvComp;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        tvStitch = view.findViewById(R.id.tv_current_stitch);
        tvComp = view.findViewById(R.id.tv_current_comp);

        // 初始化文字
        refreshTextLabels();

        // 列表项点击事件
        view.findViewById(R.id.layout_stitch_choice).setOnClickListener(v -> {
            String[] options = {"APAP (As-Projective-As-Possible)", "SIFT拼接"};
            String[] values = {"APAP", "BASIC"};
            showChoiceDialog("选择拼接算法", KEY_STITCH_ALGO, options, values);
        });

        view.findViewById(R.id.layout_comp_choice).setOnClickListener(v -> {
            String[] options = {"LaMa AI (Large Mask Inpainting)", "Opencv 补全", "不进行补全 (None)"};
            String[] values = {"LAMA", "OPENCV", "NONE"};
            showChoiceDialog("选择补全算法", KEY_COMPLETE_ALGO, options, values);
        });
    }

    private void showChoiceDialog(String title, String key, String[] options, String[] values) {
        String currentVal = prefs.getString(key, values[0]);
        int checkedItem = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(currentVal)) {
                checkedItem = i;
                break;
            }
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                    prefs.edit().putString(key, values[which]).apply();
                    refreshTextLabels(); // 更新界面
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void refreshTextLabels() {
        String stitch = prefs.getString(KEY_STITCH_ALGO, "APAP");
        tvStitch.setText(stitch.equals("APAP") ? "APAP (As-Projective-As-Possible)" : "SIFT拼接");

        String comp = prefs.getString(KEY_COMPLETE_ALGO, "LAMA");
        String[] options = {"LaMa AI (Large Mask Inpainting)", "Opencv 补全", "不进行补全 (None)"};
        String[] values = {"LAMA", "OPENCV", "NONE"};
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(comp)) {
                tvComp.setText(options[i]);
                break;
            }
        }
    }
}