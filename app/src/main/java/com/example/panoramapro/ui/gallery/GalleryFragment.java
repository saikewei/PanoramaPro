package com.example.panoramapro.ui.gallery;

import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import com.example.panoramapro.R;

import com.bumptech.glide.Glide;
import com.example.panoramapro.databinding.FragmentGalleryBinding;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class GalleryFragment extends Fragment {

    private FragmentGalleryBinding binding;
    private StitchingViewModel viewModel;
    private GalleryAdapter adapter;

    // 系统相册选择器
    private final ActivityResultLauncher<String> pickImagesLauncher = registerForActivityResult(
            new ActivityResultContracts.GetMultipleContents(),
            uris -> {
                if (uris != null && uris.size() >= 2) {
                    viewModel.startStitching(uris);
                } else if (uris != null) {
                    Toast.makeText(getContext(), "请至少选择2张图片", Toast.LENGTH_SHORT).show();
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
        viewModel = new ViewModelProvider(this).get(StitchingViewModel.class);

        setupRecyclerView();
        setupButtons();
        setupObservers();
    }

    private void setupRecyclerView() {
        adapter = new GalleryAdapter(new GalleryAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(File file, int position) {
                // 点击预览
                showPreviewDialog(file);
            }

            @Override
            public void onSelectionChanged(int selectedCount) {
                updateBottomBar(selectedCount > 0);
            }
        });

        binding.rvGallery.setLayoutManager(new GridLayoutManager(getContext(), 3));
        binding.rvGallery.setAdapter(adapter);
    }

    private void setupButtons() {
        // 1. 从系统相册导入并拼接
        binding.btnAddFromSystem.setOnClickListener(v -> pickImagesLauncher.launch("image/*"));

        // 2. 拼接当前相册中选中的图片
        binding.btnStitchSelected.setOnClickListener(v -> {
            List<File> selectedFiles = adapter.getSelectedFiles();
            if (selectedFiles.size() < 2) {
                Toast.makeText(getContext(), "请至少选择2张图片", Toast.LENGTH_SHORT).show();
                return;
            }
            // 将 File 转为 Uri
            List<Uri> uris = new ArrayList<>();
            for (File f : selectedFiles) {
                uris.add(Uri.fromFile(f));
            }

            // 退出选择模式并开始拼接
            exitSelectionMode();
            viewModel.startStitching(uris);
        });

        // 3. 删除选中
        binding.btnDelete.setOnClickListener(v -> {
            List<File> toDelete = adapter.getSelectedFiles();
            if (!toDelete.isEmpty()) {
                new AlertDialog.Builder(getContext())
                        .setTitle("确认删除")
                        .setMessage("确定要删除这 " + toDelete.size() + " 张图片吗？")
                        .setPositiveButton("删除", (dialog, which) -> {
                            viewModel.deleteImages(toDelete);
                            exitSelectionMode();
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        });

        // 4. 取消选择
        binding.btnCancelSelect.setOnClickListener(v -> exitSelectionMode());
    }

    private void updateBottomBar(boolean isSelectionMode) {
        binding.btnAddFromSystem.setVisibility(isSelectionMode ? View.GONE : View.VISIBLE);
        binding.layoutSelectionActions.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
    }

    private void exitSelectionMode() {
        adapter.toggleSelectionMode(false);
        updateBottomBar(false);
    }

    private void setupObservers() {
        // 监听本地文件列表
        viewModel.getLocalImages().observe(getViewLifecycleOwner(), files -> {
            if (files == null || files.isEmpty()) {
                binding.tvEmpty.setVisibility(View.VISIBLE);
                binding.rvGallery.setVisibility(View.GONE);
            } else {
                binding.tvEmpty.setVisibility(View.GONE);
                binding.rvGallery.setVisibility(View.VISIBLE);
            }
            adapter.setFiles(files);
        });

        // 监听 Loading
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            binding.bottomPanel.setVisibility(isLoading ? View.GONE : View.VISIBLE); // 处理时隐藏按钮防止误触
        });

        // 监听拼接成功
        viewModel.getStitchSuccessEvent().observe(getViewLifecycleOwner(), path -> {
            Toast.makeText(getContext(), "拼接成功并保存！", Toast.LENGTH_SHORT).show();
            // 可选：成功后直接预览结果
            showPreviewDialog(new File(path));
        });

        // 监听错误
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), msg -> {
            Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
        });
    }

    // 简单的全屏预览 Dialog
    private void showPreviewDialog(File file) {
        // 1. 使用全屏主题创建 Dialog
        android.app.Dialog dialog = new android.app.Dialog(getContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_preview_image);

        // 2. 获取控件
        ImageView ivPreview = dialog.findViewById(R.id.iv_preview);
        View progressBar = dialog.findViewById(R.id.pb_loading);
        View btnClose = dialog.findViewById(R.id.btn_close_preview);

        // 3. 绑定关闭按钮事件
        btnClose.setOnClickListener(v -> dialog.dismiss());

        // 4. 使用 Glide 加载图片 (带错误处理和进度条显隐)
        progressBar.setVisibility(View.VISIBLE);

        // 临时测试：设置一个白色背景，如果你看到白色方块说明 View 没问题，是 Glide 加载不出图
        // ivPreview.setBackgroundColor(android.graphics.Color.WHITE);

        Glide.with(this)
                .load(file)
                .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable com.bumptech.glide.load.engine.GlideException e, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                        progressBar.setVisibility(View.GONE);
                        android.util.Log.e("PREVIEW", "Glide 加载失败: " + file.getAbsolutePath());
                        if(e != null) e.logRootCauses("PREVIEW");

                        // 弹个 Toast 提示用户
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() ->
                                    android.widget.Toast.makeText(getContext(), "图片加载失败，请查看日志", android.widget.Toast.LENGTH_SHORT).show()
                            );
                        }
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                        progressBar.setVisibility(View.GONE);
                        return false;
                    }
                })
                .into(ivPreview);

        // 5. 显示 Dialog
        dialog.show();
    }

    @Override
    public void onResume() {
        super.onResume();
        // 每次界面可见时，重新扫描本地文件
        if (viewModel != null) {
            viewModel.loadLocalImages();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}