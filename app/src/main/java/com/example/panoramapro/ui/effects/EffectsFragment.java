package com.example.panoramapro.ui.effects;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.panoramapro.R;
import com.example.panoramapro.effects.TinyPlanetProcessor;
import com.example.panoramapro.utils.BitmapSaver;

import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EffectsFragment extends Fragment {

    private ImageView ivResult;
    private ProgressBar progressBar;
    private Button btnSelect;
    private SeekBar sbScale;
    private TextView tvScaleValue;

    private Bitmap currentResultBitmap;
    private Uri currentSourceUri;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final TinyPlanetProcessor processor = new TinyPlanetProcessor();

    private final ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    processImage(uri);
                }
            }
    );

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_effects, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ivResult = view.findViewById(R.id.iv_effect_result);
        progressBar = view.findViewById(R.id.effect_progress);
        btnSelect = view.findViewById(R.id.btn_select_planet);
        sbScale = view.findViewById(R.id.sb_planet_scale);
        tvScaleValue = view.findViewById(R.id.tv_scale_value);

        btnSelect.setOnClickListener(v -> pickImageLauncher.launch("image/*"));

        // 滑块监听
        sbScale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float scale = progress / 100f;
                tvScaleValue.setText(String.format(Locale.getDefault(), "%.2fx", scale));
                if (fromUser) {
                    processor.setScale(scale);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (currentSourceUri != null) {
                    processImage(currentSourceUri);
                }
            }
        });

        // 长按保存
        ivResult.setOnLongClickListener(v -> {
            if (currentResultBitmap != null) {
                new AlertDialog.Builder(getContext())
                        .setTitle("保存图片")
                        .setMessage("是否将小行星效果图保存到相册？")
                        .setPositiveButton("保存", (dialog, which) -> {
                            String path = BitmapSaver.saveBitmapToPrivateStorage(requireContext(), currentResultBitmap);
                            if (path != null) {
                                Toast.makeText(getContext(), "已保存至: " + path, Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
            return true;
        });
    }

    private void processImage(Uri uri) {
        this.currentSourceUri = uri;
        progressBar.setVisibility(View.VISIBLE);
        btnSelect.setEnabled(false);

        executor.execute(() -> {
            try {
                InputStream is = requireContext().getContentResolver().openInputStream(uri);
                Bitmap source = BitmapFactory.decodeStream(is);

                if (source != null) {
                    processor.setOutputSize(1024);
                    Bitmap result = processor.applyLittlePlanetEffect(source);

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            currentResultBitmap = result;
                            ivResult.setImageBitmap(result);
                            progressBar.setVisibility(View.GONE);
                            btnSelect.setEnabled(true);
                        });
                    }
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "处理失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        progressBar.setVisibility(View.GONE);
                        btnSelect.setEnabled(true);
                    });
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}