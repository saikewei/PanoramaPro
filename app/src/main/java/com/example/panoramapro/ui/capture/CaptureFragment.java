package com.example.panoramapro.ui.capture;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.example.panoramapro.R;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CaptureFragment extends Fragment {
    private static final String TAG = "CaptureFragment";
    private static final int REQUEST_CAMERA_PERMISSION = 1001;
    private static final int WARNING_THRESHOLD = 5; // 超过5张显示警告

    private PreviewView previewView;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private ArrayList<Bitmap> captures = new ArrayList<>();
    private StitchingViewModel viewModel;
    private boolean cameraStarted = false;
    private boolean permissionRequested = false;

    // UI组件
    private Button btnCapture;
    private Button btnReset;
    private TextView tvCaptureCount;
    private Button btnProceed;

    // 相机状态
    private enum CameraState {
        NO_PERMISSION,
        PERMISSION_GRANTED,
        INITIALIZING,
        READY,
        ERROR
    }
    private CameraState cameraState = CameraState.NO_PERMISSION;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_capture, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 初始化UI组件
        initUI(view);

        // 初始化ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(StitchingViewModel.class);

        // 观察重置请求
        observeResetRequest();

        // 立即检查权限并启动相机
        checkCameraPermissionAndStart();

        // 更新UI状态
        updateUIState();
    }

    @Override
    public void onResume() {
        super.onResume();
        // 在onResume中重新检查相机状态
        if (cameraState == CameraState.PERMISSION_GRANTED && !cameraStarted && previewView != null) {
            Log.i(TAG, "onResume - Restarting camera");
            previewView.postDelayed(() -> startCamera(), 300);
        }
    }

    // 观察重置请求的方法
    private void observeResetRequest() {
        viewModel.getResetRequested().observe(getViewLifecycleOwner(), resetNeeded -> {
            if (resetNeeded != null && resetNeeded) {
                // 清空本地captures列表
                captures.clear();
                updateUIState();

                // 重置请求已处理
                viewModel.resetRequestHandled();

                Log.i(TAG, "Captures cleared from reset request");
                Toast.makeText(requireContext(), "All photos have been cleared", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initUI(View view) {
        // 使用getIdentifier来查找视图，避免编译时错误
        int previewViewId = getResources().getIdentifier("previewView", "id", requireContext().getPackageName());
        int btnCaptureId = getResources().getIdentifier("btn_capture", "id", requireContext().getPackageName());
        int btnResetId = getResources().getIdentifier("btn_reset", "id", requireContext().getPackageName());
        int tvCaptureCountId = getResources().getIdentifier("tv_capture_count", "id", requireContext().getPackageName());
        int btnProceedId = getResources().getIdentifier("btn_proceed", "id", requireContext().getPackageName());

        if (previewViewId != 0) {
            previewView = view.findViewById(previewViewId);
        }

        if (btnCaptureId != 0) {
            btnCapture = view.findViewById(btnCaptureId);
            if (btnCapture != null) {
                btnCapture.setOnClickListener(v -> capturePhoto());
            }
        }

        if (btnResetId != 0) {
            btnReset = view.findViewById(btnResetId);
            if (btnReset != null) {
                btnReset.setOnClickListener(v -> resetCaptures());
            }
        }

        if (tvCaptureCountId != 0) {
            tvCaptureCount = view.findViewById(tvCaptureCountId);
        }

        if (btnProceedId != 0) {
            btnProceed = view.findViewById(btnProceedId);
            if (btnProceed != null) {
                btnProceed.setText("Stitch");
                btnProceed.setOnClickListener(v -> proceedToPreview());
            }
        }

        // 如果预览视图为空，尝试在视图树中查找
        if (previewView == null) {
            previewView = findFirstPreviewView(view);
        }

        // 初始按钮状态
        if (btnCapture != null) {
            btnCapture.setEnabled(false);
            btnCapture.setText("Waiting for Camera...");
        }
    }

    private void checkCameraPermissionAndStart() {
        Log.i(TAG, "Checking camera permission...");

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            cameraState = CameraState.PERMISSION_GRANTED;
            Log.i(TAG, "Camera permission already granted");

            // 延迟一小段时间启动相机，确保视图已经完全加载
            if (previewView != null) {
                previewView.postDelayed(() -> {
                    if (!cameraStarted) {
                        Log.i(TAG, "Starting camera from permission check");
                        startCamera();
                    }
                }, 500);
            } else {
                Log.w(TAG, "PreviewView is null, cannot start camera");
                // 重新查找预览视图
                if (getView() != null) {
                    previewView = findFirstPreviewView(getView());
                    if (previewView != null) {
                        previewView.postDelayed(() -> {
                            if (!cameraStarted) {
                                startCamera();
                            }
                        }, 500);
                    }
                }
            }
        } else {
            cameraState = CameraState.NO_PERMISSION;
            Log.i(TAG, "Camera permission not granted");
            // 如果还没有请求过权限，就请求权限
            if (!permissionRequested) {
                permissionRequested = true;
                requestCameraPermission();
            }
        }
    }

    private void requestCameraPermission() {
        Log.i(TAG, "Requesting camera permission...");

        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            // 向用户解释为什么需要权限
            Toast.makeText(requireContext(),
                    "Camera permission is required to take photos",
                    Toast.LENGTH_LONG).show();
        }

        // 请求权限
        requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                cameraState = CameraState.PERMISSION_GRANTED;
                Log.i(TAG, "Camera permission granted via request");

                // 权限被授予，启动相机
                if (previewView != null) {
                    previewView.postDelayed(() -> {
                        if (!cameraStarted) {
                            startCamera();
                        }
                    }, 500);
                } else {
                    // 重新查找预览视图
                    if (getView() != null) {
                        previewView = findFirstPreviewView(getView());
                        if (previewView != null) {
                            previewView.postDelayed(() -> {
                                if (!cameraStarted) {
                                    startCamera();
                                }
                            }, 500);
                        }
                    }
                }
            } else {
                cameraState = CameraState.NO_PERMISSION;
                Log.w(TAG, "Camera permission denied");

                // 显示友好的错误信息
                Toast.makeText(requireContext(),
                        "Camera permission is required to use this feature",
                        Toast.LENGTH_LONG).show();

                // 禁用拍照按钮
                if (btnCapture != null) {
                    btnCapture.setEnabled(false);
                    btnCapture.setText("Need Camera Permission");
                }
            }
            updateUIState();
        }
    }

    private void startCamera() {
        Log.i(TAG, "startCamera called. cameraStarted: " + cameraStarted + ", previewView: " + previewView);

        if (cameraStarted) {
            Log.w(TAG, "Camera already started, skipping...");
            return;
        }

        if (previewView == null) {
            Log.e(TAG, "PreviewView is null, cannot start camera");
            if (getView() != null) {
                previewView = findFirstPreviewView(getView());
                if (previewView == null) {
                    Log.e(TAG, "Still cannot find PreviewView");
                    Toast.makeText(requireContext(), "Cannot find camera preview", Toast.LENGTH_LONG).show();
                    return;
                }
            } else {
                return;
            }
        }

        cameraState = CameraState.INITIALIZING;
        Log.i(TAG, "Starting camera...");

        ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(requireContext());
        providerFuture.addListener(() -> {
            try {
                cameraProvider = providerFuture.get();
                if (cameraProvider != null) {
                    bindCameraUseCases();
                    cameraStarted = true;
                    cameraState = CameraState.READY;
                    Log.i(TAG, "Camera started successfully");

                    // 更新UI，启用拍照按钮
                    requireActivity().runOnUiThread(() -> {
                        if (btnCapture != null) {
                            btnCapture.setEnabled(true);
                            btnCapture.setText("Capture");
                        }
                        Toast.makeText(requireContext(), "Camera ready", Toast.LENGTH_SHORT).show();
                        updateUIState();
                    });
                } else {
                    cameraState = CameraState.ERROR;
                    Log.e(TAG, "CameraProvider is null");
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Failed to get camera provider", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                cameraState = CameraState.ERROR;
                Log.e(TAG, "Camera initialization failed", e);

                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(),
                            "Camera initialization failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null || previewView == null) {
            Log.e(TAG, "CameraProvider or PreviewView is null");
            return;
        }

        Log.i(TAG, "Binding camera use cases...");

        // 先解绑所有用例
        try {
            cameraProvider.unbindAll();
            Log.i(TAG, "Unbound all previous use cases");
        } catch (Exception e) {
            Log.w(TAG, "Error unbinding previous use cases: " + e.getMessage());
        }

        // 创建预览用例
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // 创建拍照用例
        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetRotation(previewView.getDisplay().getRotation())
                .build();

        // 选择后置摄像头
        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

        try {
            // 检查是否有后置摄像头
            if (!cameraProvider.hasCamera(cameraSelector)) {
                Log.w(TAG, "Back camera not available, using front camera");
                cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
            }

            // 绑定到生命周期
            cameraProvider.bindToLifecycle(
                    getViewLifecycleOwner(),
                    cameraSelector,
                    preview,
                    imageCapture
            );
            Log.i(TAG, "Camera use cases bound successfully");
        } catch (Exception e) {
            Log.e(TAG, "Use case binding failed", e);

            // 尝试只绑定预览
            try {
                cameraProvider.bindToLifecycle(
                        getViewLifecycleOwner(),
                        cameraSelector,
                        preview
                );
                Log.i(TAG, "Only preview bound successfully");
                imageCapture = null;
            } catch (Exception ex) {
                Log.e(TAG, "Failed to bind even preview only", ex);
                throw ex;
            }
        }
    }

    private void capturePhoto() {
        Log.i(TAG, "Capture button clicked. CameraState: " + cameraState);

        // 检查相机状态
        if (cameraState != CameraState.READY) {
            Log.e(TAG, "Camera not ready. State: " + cameraState);
            Toast.makeText(requireContext(),
                    "Camera is not ready. Please wait...",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (imageCapture == null) {
            Log.e(TAG, "imageCapture is null");
            Toast.makeText(requireContext(), "Camera not ready. Please wait...", Toast.LENGTH_SHORT).show();
            return;
        }

        // 当照片超过5张时显示警告
        if (captures.size() >= WARNING_THRESHOLD) {
            showTooManyPhotosWarning();
        }

        // 禁用拍摄按钮避免连续点击
        if (btnCapture != null) {
            btnCapture.setEnabled(false);
            btnCapture.setText("Capturing...");
        }

        Log.i(TAG, "Taking photo...");

        // 创建照片文件
        File photoFile = createImageFile();
        if (photoFile == null) {
            Log.e(TAG, "Failed to create image file");
            Toast.makeText(requireContext(), "Failed to create image file", Toast.LENGTH_SHORT).show();
            if (btnCapture != null) {
                btnCapture.setEnabled(true);
                btnCapture.setText("Capture");
            }
            return;
        }

        ImageCapture.OutputFileOptions outputFileOptions =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(
                outputFileOptions,
                ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        Log.i(TAG, "Photo saved successfully to: " + photoFile.getAbsolutePath());

                        // 从文件加载图片
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inSampleSize = 2; // 缩小图片以节省内存
                        Bitmap bitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath(), options);

                        if (bitmap != null) {
                            captures.add(bitmap);
                            Log.i(TAG, "Photo captured successfully. Total: " + captures.size());

                            requireActivity().runOnUiThread(() -> {
                                updateUIState();
                                Toast.makeText(requireContext(),
                                        String.format("Captured photo %d", captures.size()),
                                        Toast.LENGTH_SHORT).show();
                            });

                            // 删除临时文件
                            if (photoFile.exists()) {
                                boolean deleted = photoFile.delete();
                                Log.i(TAG, "Temp file deleted: " + deleted);
                            }
                        } else {
                            Log.e(TAG, "Failed to decode bitmap from file");
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(requireContext(), "Failed to process image", Toast.LENGTH_SHORT).show();
                            });
                        }

                        requireActivity().runOnUiThread(() -> {
                            if (btnCapture != null) {
                                btnCapture.setEnabled(cameraState == CameraState.READY);
                                btnCapture.setText("Capture");
                            }
                        });
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "Capture failed: " + exception.getMessage(), exception);
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(),
                                    "Capture failed: " + exception.getMessage(),
                                    Toast.LENGTH_SHORT).show();

                            if (btnCapture != null) {
                                btnCapture.setEnabled(true);
                                btnCapture.setText("Capture");
                            }
                        });
                    }
                }
        );
    }

    private void showTooManyPhotosWarning() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Too Many Photos")
                .setMessage("You have taken more than 5 photos. Too many photos may affect stitching quality and performance.")
                .setPositiveButton("Continue", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    private File createImageFile() {
        try {
            // 创建临时文件目录
            File storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (storageDir == null) {
                storageDir = requireContext().getFilesDir();
            }

            // 确保目录存在
            if (!storageDir.exists()) {
                storageDir.mkdirs();
            }

            // 创建文件名
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String imageFileName = "PANORAMA_" + timeStamp + "_" + captures.size();
            File imageFile = File.createTempFile(
                    imageFileName,  /* 前缀 */
                    ".jpg",         /* 后缀 */
                    storageDir      /* 目录 */
            );

            return imageFile;
        } catch (IOException e) {
            Log.e(TAG, "Failed to create image file", e);
            return null;
        }
    }

    private void updateUIState() {
        if (getView() == null) return;

        // 更新计数显示 - 只显示当前拍照数量
        if (tvCaptureCount != null) {
            tvCaptureCount.setText(String.format("Captured: %d", captures.size()));
        }

        // 更新按钮状态
        if (btnCapture != null) {
            btnCapture.setEnabled(cameraState == CameraState.READY);

            if (cameraState == CameraState.READY) {
                btnCapture.setText("Capture");
            } else if (cameraState == CameraState.NO_PERMISSION) {
                btnCapture.setText("Need Permission");
            } else {
                btnCapture.setText("Camera Loading...");
            }
        }

        if (btnReset != null) {
            btnReset.setEnabled(!captures.isEmpty());
        }

        if (btnProceed != null) {
            btnProceed.setEnabled(captures.size() >= 2);
        }
    }

    private void resetCaptures() {
        if (captures.isEmpty()) {
            Toast.makeText(requireContext(), "No photos to clear", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Clear All Photos")
                .setMessage("Are you sure you want to clear all " + captures.size() + " photos?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        captures.clear();
                        updateUIState();
                        Toast.makeText(requireContext(), "All photos cleared", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    private void proceedToPreview() {
        if (captures.size() < 2) {
            Toast.makeText(requireContext(), "Need at least 2 photos for stitching", Toast.LENGTH_SHORT).show();
            return;
        }

        // 保存到ViewModel
        viewModel.setCaptures(new ArrayList<>(captures));

        // 显示成功消息
        Toast.makeText(requireContext(),
                String.format("%d photos ready for preview", captures.size()),
                Toast.LENGTH_SHORT).show();

        Log.i(TAG, "Proceeding to preview with " + captures.size() + " photos");

        // 尝试导航到预览界面
        try {
            // 方法1：使用 Navigation.findNavController
            NavController navController = Navigation.findNavController(requireView());

            // 尝试导航
            try {
                navController.navigate(R.id.action_captureFragment_to_previewFragment);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Navigation action not found: " + e.getMessage());
                // 如果action不存在，使用手动方法
                showPreviewFragmentManually();
            }
        } catch (Exception e) {
            Log.e(TAG, "Navigation failed: " + e.getMessage());
            // 如果找不到NavController，使用手动方法
            showPreviewFragmentManually();
        }
    }

    private void showPreviewFragmentManually() {
        try {
            // 创建预览Fragment
            PreviewFragment previewFragment = new PreviewFragment();

            // 使用Fragment事务显示
            // 注意：这里假设你的Activity有一个容器View，id为"fragment_container"
            // 请根据你的实际布局文件修改这个ID
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.nav_host_fragment, previewFragment) // 使用你的容器ID
                    .addToBackStack("capture") // 添加到返回栈
                    .commit();

            Log.i(TAG, "Preview fragment shown manually");
        } catch (Exception e) {
            Log.e(TAG, "Failed to show preview fragment manually: " + e.getMessage());
            Toast.makeText(requireContext(), "Failed to show preview: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        // 停止相机
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        cameraStarted = false;
        Log.i(TAG, "Camera stopped");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 清理相机资源
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            cameraProvider = null;
        }
        cameraStarted = false;
        cameraState = CameraState.NO_PERMISSION;
        Log.i(TAG, "Camera resources released in onDestroyView");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        Log.i(TAG, "Fragment destroyed");
    }

    // 在视图树中查找第一个 PreviewView
    private PreviewView findFirstPreviewView(View root) {
        if (root == null) return null;
        if (root instanceof PreviewView) return (PreviewView) root;
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View child = vg.getChildAt(i);
                PreviewView found = findFirstPreviewView(child);
                if (found != null) return found;
            }
        }
        return null;
    }
}