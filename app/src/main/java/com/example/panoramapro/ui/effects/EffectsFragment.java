package com.example.panoramapro.ui.effects;

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
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.panoramapro.R;
import com.example.panoramapro.effects.TinyPlanetProcessor;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EffectsFragment extends Fragment {

    private ImageView ivResult;
    private ProgressBar progressBar;
    private Button btnSelect;

    // 使用线程池处理耗时的 C++ 算法映射逻辑
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final TinyPlanetProcessor processor = new TinyPlanetProcessor();

    // 注册系统相册选择器
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
        // 使用自定义的高级布局
        return inflater.inflate(R.layout.fragment_effects, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ivResult = view.findViewById(R.id.iv_effect_result);
        progressBar = view.findViewById(R.id.effect_progress);
        btnSelect = view.findViewById(R.id.btn_select_planet);

        btnSelect.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
    }

    private void processImage(Uri uri) {
        progressBar.setVisibility(View.VISIBLE);
        btnSelect.setEnabled(false);

        executor.execute(() -> {
            try {
                InputStream is = requireContext().getContentResolver().openInputStream(uri);
                Bitmap source = BitmapFactory.decodeStream(is);

                if (source != null) {
                    // 设置输出尺寸为 1024 (正方形) 保证美观和性能平衡
                    processor.setOutputSize(1024);
                    processor.setScale(1.2f); // 略微放大增强视觉冲击力

                    // 调用 Native C++ 极坐标转换算法
                    Bitmap result = processor.applyLittlePlanetEffect(source);

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
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