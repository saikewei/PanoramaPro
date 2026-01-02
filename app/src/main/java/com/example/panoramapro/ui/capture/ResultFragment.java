package com.example.panoramapro.ui.capture;

import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.example.panoramapro.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ResultFragment extends Fragment {
    private static final String TAG = "ResultFragment";

    private ImageView ivResult;
    private ProgressBar progressBar;
    private Button btnSave;
    private ImageView btnBack;

    private NavController navController;
    private Bitmap resultBitmap;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_result, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 初始化UI组件
        initUI(view);

        // 获取导航控制器
        navController = Navigation.findNavController(view);

        // 从参数中获取Bitmap
        if (getArguments() != null && getArguments().containsKey("result_bitmap")) {
            resultBitmap = getArguments().getParcelable("result_bitmap");
            if (resultBitmap != null) {
                displayImage(resultBitmap);
            } else {
                Toast.makeText(requireContext(), "图片加载失败", Toast.LENGTH_SHORT).show();
                navController.popBackStack();
            }
        } else {
            Toast.makeText(requireContext(), "没有图片可预览", Toast.LENGTH_SHORT).show();
            navController.popBackStack();
        }
    }

    private void initUI(View view) {
        ivResult = view.findViewById(R.id.iv_result);
        progressBar = view.findViewById(R.id.progress_bar);
        btnSave = view.findViewById(R.id.btn_save_image);
        btnBack = view.findViewById(R.id.btn_back);

        // 设置点击事件
        btnBack.setOnClickListener(v -> navController.popBackStack());
        btnSave.setOnClickListener(v -> saveImageToGallery());
    }

    private void displayImage(Bitmap bitmap) {
        if (bitmap != null && ivResult != null) {
            ivResult.setImageBitmap(bitmap);
            progressBar.setVisibility(View.GONE);
            btnSave.setEnabled(true);
        }
    }

    private void saveImageToGallery() {
        if (resultBitmap == null) {
            Toast.makeText(requireContext(), "没有可保存的图片", Toast.LENGTH_SHORT).show();
            return;
        }

        // 显示保存进度
        progressBar.setVisibility(View.VISIBLE);
        btnSave.setEnabled(false);

        // 在子线程中保存图片
        new Thread(() -> {
            boolean success = false;
            String errorMessage = null;

            try {
                // 生成文件名
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                        .format(new Date());
                String fileName = "PANORAMA_" + timeStamp + ".jpg";

                // 保存图片
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    success = saveImageToGalleryAPI29(fileName);
                } else {
                    success = saveImageToGalleryLegacy(fileName);
                }

            } catch (Exception e) {
                Log.e(TAG, "保存图片失败: " + e.getMessage());
                errorMessage = e.getMessage();
                success = false;
            }

            final boolean finalSuccess = success;
            final String finalErrorMessage = errorMessage;

            // 在主线程显示结果
            requireActivity().runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                btnSave.setEnabled(true);

                if (finalSuccess) {
                    Toast.makeText(requireContext(),
                            "图片已保存到相册",
                            Toast.LENGTH_LONG).show();
                } else {
                    String message = "保存失败，请检查权限";
                    if (finalErrorMessage != null && !finalErrorMessage.isEmpty()) {
                        message = "保存失败: " + finalErrorMessage;
                    }
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private boolean saveImageToGalleryAPI29(String fileName) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/PanoramaPro");

        Uri uri = requireContext().getContentResolver().insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        if (uri == null) {
            Log.e(TAG, "无法创建URI");
            return false;
        }

        try (OutputStream outputStream = requireContext().getContentResolver().openOutputStream(uri)) {
            if (outputStream == null) {
                Log.e(TAG, "无法打开输出流");
                return false;
            }
            boolean compressed = resultBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
            if (!compressed) {
                Log.e(TAG, "图片压缩失败");
                return false;
            }
            Log.i(TAG, "图片保存成功: " + uri.toString());
            return true;
        } catch (IOException e) {
            Log.e(TAG, "保存到相册失败: " + e.getMessage());
            return false;
        }
    }

    private boolean saveImageToGalleryLegacy(String fileName) {
        // 检查存储权限
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            Log.e(TAG, "外部存储不可用");
            return false;
        }

        File picturesDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);
        File panoramaDir = new File(picturesDir, "PanoramaPro");

        if (!panoramaDir.exists() && !panoramaDir.mkdirs()) {
            Log.e(TAG, "无法创建目录: " + panoramaDir.getAbsolutePath());
            return false;
        }

        File imageFile = new File(panoramaDir, fileName);

        try (FileOutputStream fos = new FileOutputStream(imageFile)) {
            boolean compressed = resultBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            if (!compressed) {
                Log.e(TAG, "图片压缩失败");
                return false;
            }

            // 通知系统相册更新
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            mediaScanIntent.setData(Uri.fromFile(imageFile));
            requireContext().sendBroadcast(mediaScanIntent);

            Log.i(TAG, "图片保存成功: " + imageFile.getAbsolutePath());
            return true;

        } catch (IOException e) {
            Log.e(TAG, "保存到相册失败: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 清理资源
        if (ivResult != null) {
            ivResult.setImageDrawable(null);
        }
        // 释放Bitmap内存
        if (resultBitmap != null && !resultBitmap.isRecycled()) {
            resultBitmap.recycle();
            resultBitmap = null;
        }
    }
}