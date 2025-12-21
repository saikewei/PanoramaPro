package com.example.panoramapro.ui.gallery;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.example.panoramapro.databinding.FragmentGalleryBinding;

public class GalleryFragment extends Fragment {

    private FragmentGalleryBinding binding;
    private StitchingViewModel viewModel;

    // 注册图片选择器 (替代 startActivityResult)
    private final ActivityResultLauncher<String> pickImagesLauncher = registerForActivityResult(
            new ActivityResultContracts.GetMultipleContents(),
            uris -> {
                if (uris != null && !uris.isEmpty()) {
                    // 用户选完了图，传给 ViewModel
                    viewModel.startStitching(uris);
                }
            }
    );

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentGalleryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 初始化 ViewModel
        viewModel = new ViewModelProvider(this).get(StitchingViewModel.class);

        // 绑定按钮事件
        binding.btnStitch.setOnClickListener(v -> {
            // 打开系统相册，只选图片
            pickImagesLauncher.launch("image/*");
        });

        setupObservers();
    }

    private void setupObservers() {
        // 1. 观察 Loading 状态
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            if (isLoading) {
                binding.progressBar.setVisibility(View.VISIBLE);
                binding.btnStitch.setEnabled(false);
                binding.btnStitch.setText("正在处理中...");
            } else {
                binding.progressBar.setVisibility(View.GONE);
                binding.btnStitch.setEnabled(true);
                binding.btnStitch.setText("从相册选择图片拼接");
            }
        });

        // 2. 观察结果图片
        viewModel.getResultBitmap().observe(getViewLifecycleOwner(), bitmap -> {
            if (bitmap != null) {
                Glide.with(this).load(bitmap).into(binding.ivResult);
                Toast.makeText(getContext(), "处理成功！", Toast.LENGTH_SHORT).show();
            }
        });

        // 3. 观察错误信息
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // 防止内存泄漏
    }
}