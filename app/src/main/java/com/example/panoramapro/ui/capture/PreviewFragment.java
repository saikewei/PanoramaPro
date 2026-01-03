package com.example.panoramapro.ui.capture;

import com.example.panoramapro.core.*;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView; // 添加 ImageView 导入
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.viewpager2.widget.ViewPager2;

import com.example.panoramapro.R;
import com.example.panoramapro.utils.BitmapSaver;
import com.example.panoramapro.utils.FileUtils;

import java.util.ArrayList;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.ProgressDialog; //用于显示加载框
import android.os.Handler;
import android.os.Looper;

public class PreviewFragment extends Fragment {
    private static final String TAG = "PreviewFragment";

    private ViewPager2 viewPager;
    private TextView tvPhotoCount;
    private Button btnNext;
    private Button btnReset;

    private StitchingViewModel viewModel;
    private ArrayList<Bitmap> captures;
    private PhotoPagerAdapter adapter;

    private final ExecutorService stitchingExecutor = Executors.newSingleThreadExecutor();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_preview, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 初始化UI组件
        initUI(view);

        // 初始化ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(StitchingViewModel.class);

        // 获取照片数据
        captures = viewModel.getCaptures();
        if (captures == null) {
            captures = new ArrayList<>();
        }

        // 设置适配器
        adapter = new PhotoPagerAdapter(captures);
        viewPager.setAdapter(adapter);

        // 更新UI
        updateUI();
    }

    private void initUI(View view) {
        // 初始化ViewPager2
        viewPager = view.findViewById(R.id.view_pager);

        // 初始化TextView显示照片数量
        tvPhotoCount = view.findViewById(R.id.tv_photo_count);

        // 初始化Next按钮（暂时留空）
        btnNext = view.findViewById(R.id.btn_next);
        btnNext.setText("开始拼接"); // 建议更改按钮文字
        btnNext.setOnClickListener(v -> {
            performStitching();
        });

        // 初始化Reset按钮
        btnReset = view.findViewById(R.id.btn_reset);
        btnReset.setOnClickListener(v -> showResetConfirmation());

        // 设置ViewPager页面变化监听器
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updatePhotoCounter(position);
            }
        });
    }

    private void performStitching() {
        if (captures == null || captures.size() < 2) {
            Toast.makeText(requireContext(), "照片数量不足，无法拼接", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. 显示加载提示 (ProgressDialog 或者 ProgressBar)
        ProgressDialog progressDialog = new ProgressDialog(requireContext());
        progressDialog.setMessage("正在拼接全景图，请稍候...");
        progressDialog.setCancelable(false); // 禁止点击外部取消
        progressDialog.show();
        Context context = requireActivity().getApplicationContext();

        // 2. 在子线程中执行拼接
        stitchingExecutor.execute(() -> {
            try {
                // =============== 核心调用开始 ===============
                ImageProcessorFactory factory = new ImageProcessorFactory(context);
                IStitcher stitch_photo = factory.getStitcher();

                String modelPath = FileUtils.copyAssetToFilesDir(context, "lama_fp32.onnx");
                if (modelPath == null) {
                    throw new Exception("模型文件拷贝失败");
                }

                Bitmap switchBitmap = stitch_photo.stitch(captures,true);// 进行图片拼接

                // 4. 执行 AI 补全 (Java -> C++)
                // 注意：LaMaCompleter 需要在不使用时 release，这里为了简单在方法内创建并释放
                IImageCompleter completer = factory.getCompleter();
                Bitmap resultBitmap = completer.complete(switchBitmap);
                completer.release(); // 释放 C++ 资源
                // =============== 核心调用结束 ===============

                // 3. 切换回主线程更新 UI
                new Handler(Looper.getMainLooper()).post(() -> {
                    progressDialog.dismiss(); // 关闭加载框

                    if (resultBitmap != null) {
                        onStitchingSuccess(resultBitmap);
                    } else {
                        Toast.makeText(requireContext(), "拼接失败，请重试", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Stitching error: " + e.getMessage(), e);
                // 异常处理
                new Handler(Looper.getMainLooper()).post(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(requireContext(), "发生错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void onStitchingSuccess(Bitmap stitchedBitmap) {
        if (stitchedBitmap == null) {
            Toast.makeText(requireContext(), "结果图片为空", Toast.LENGTH_SHORT).show();
            return;
        }

        // 创建并显示结果预览对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_preview_result, null);

        // 初始化对话框组件
        ImageView ivResultPreview = dialogView.findViewById(R.id.iv_result_preview);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        Button btnSave = dialogView.findViewById(R.id.btn_save);

        Bitmap displayBitmap = stitchedBitmap; // 默认指向原图

        // 定义一个安全的显示尺寸上限（例如 2048px，绝大多数手机都能安全渲染）
        int MAX_DISPLAY_SIZE = 2048;

        int w = stitchedBitmap.getWidth();
        int h = stitchedBitmap.getHeight();

        // 如果图片宽或高超过限制，则生成缩略图
        if (w > MAX_DISPLAY_SIZE || h > MAX_DISPLAY_SIZE) {
            float aspectRatio = (float) w / h;
            int newW, newH;

            if (w > h) {
                newW = MAX_DISPLAY_SIZE;
                newH = (int) (MAX_DISPLAY_SIZE / aspectRatio);
            } else {
                newH = MAX_DISPLAY_SIZE;
                newW = (int) (MAX_DISPLAY_SIZE * aspectRatio);
            }

            try {
                // 创建缩略图专门用于显示
                Log.i(TAG, "原图尺寸: " + w + "x" + h + " 太大，生成预览缩略图: " + newW + "x" + newH);
                displayBitmap = Bitmap.createScaledBitmap(stitchedBitmap, newW, newH, true);
            } catch (OutOfMemoryError e) {
                Log.e(TAG, "生成缩略图内存不足", e);
                // 如果缩略图都生成失败，可能内存极度紧张，尝试直接用原图（虽然可能会崩）或者显示占位图
            }
        }

        // 设置给 ImageView 的是（可能缩小过的）displayBitmap
        ivResultPreview.setImageBitmap(displayBitmap);
        // ==================== 【关键修改结束】 ====================

        if (stitchedBitmap.getHeight() > 600) {
            ivResultPreview.setMaxHeight(600);
            ivResultPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        }

        AlertDialog resultDialog = builder
                .setView(dialogView)
                .setCancelable(false)
                .create();

        // 这里的逻辑需要注意：displayBitmap 可能是新创建的，也可能是原图引用
        // 为了在 Close 时正确回收，我们需要一个 final 变量来引用可能需要回收的缩略图
        final Bitmap finalDisplayBitmap = (displayBitmap != stitchedBitmap) ? displayBitmap : null;

        btnCancel.setOnClickListener(v -> {
            resultDialog.dismiss();
            Toast.makeText(requireContext(), "已取消", Toast.LENGTH_SHORT).show();
            // 如果生成了额外的缩略图，记得回收
            if (finalDisplayBitmap != null && !finalDisplayBitmap.isRecycled()) {
                finalDisplayBitmap.recycle();
            }
            // 原图 stitchedBitmap 由调用者或后续逻辑决定何时回收，通常建议在 Fragment 销毁或重置时回收
        });

        btnSave.setOnClickListener(v -> {
            resultDialog.dismiss();
            // 只要对话框关闭，预览用的缩略图就可以回收了
            if (finalDisplayBitmap != null && !finalDisplayBitmap.isRecycled()) {
                finalDisplayBitmap.recycle();
            }

            ProgressDialog saveDialog = new ProgressDialog(requireContext());
            saveDialog.setMessage("正在保存图片...");
            saveDialog.setCancelable(false);
            saveDialog.show();

            stitchingExecutor.execute(() -> {
                // 【注意】保存时依然使用原始的高清 stitchedBitmap
                String savedPath = BitmapSaver.saveBitmapToPrivateStorage(
                        requireContext().getApplicationContext(),
                        stitchedBitmap
                );

                new Handler(Looper.getMainLooper()).post(() -> {
                    saveDialog.dismiss();
                    if (savedPath != null) {
                        String displayMessage = "全景图保存成功！\n\n路径：" + savedPath;
                        new AlertDialog.Builder(requireContext())
                                .setTitle("保存成功")
                                .setMessage(displayMessage)
                                .setPositiveButton("确定", null)
                                .show();
                    } else {
                        Toast.makeText(requireContext(), "保存失败，请重试", Toast.LENGTH_SHORT).show();
                    }
                });
            });
        });

        resultDialog.show();
        btnNext.setText("重新拼接");
        btnNext.setEnabled(true);

        // 显示信息时显示原图尺寸
        Toast.makeText(requireContext(),
                "拼接完成！原图尺寸: " + stitchedBitmap.getWidth() + "x" + stitchedBitmap.getHeight(),
                Toast.LENGTH_LONG).show();
    }

    private void updateUI() {
        if (captures == null || captures.isEmpty()) {
            tvPhotoCount.setText("无照片");
            btnNext.setEnabled(false);
            btnReset.setEnabled(false);
        } else {
            updatePhotoCounter(0);
            btnNext.setEnabled(true);
            btnReset.setEnabled(true);
        }
    }

    private void updatePhotoCounter(int currentPosition) {
        if (captures != null && !captures.isEmpty()) {
            tvPhotoCount.setText(String.format("Photo %d of %d", currentPosition + 1, captures.size()));
        }
    }

    private void showResetConfirmation() {
        if (captures == null || captures.isEmpty()) {
            Toast.makeText(requireContext(), "没有要清除的照片", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("清除所有照片")
                .setMessage("你确定要清除所有的 " + captures.size() + " 张照片？你将退回到拍照界面")
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 清空ViewModel中的数据
                        viewModel.clearCaptures();
                        viewModel.notifyResetNeeded();

                        // 显示确认消息
                        Toast.makeText(requireContext(),
                                "所有照片已清除",
                                Toast.LENGTH_SHORT).show();

                        // 返回到拍摄界面
                        navigateBackToCapture();

                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    private void navigateBackToCapture() {
        try {
            NavController navController = Navigation.findNavController(requireView());
            // 导航回到嵌套图的起始目的地（即拍照界面）
            navController.navigate(R.id.action_previewFragment_back_to_capture);
        } catch (Exception e) {
            Log.e(TAG, "Navigation failed: " + e.getMessage());
            requireActivity().onBackPressed();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (viewPager != null) {
            viewPager.unregisterOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {});
        }
    }
}