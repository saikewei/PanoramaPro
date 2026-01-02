package com.example.panoramapro.ui.capture;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 全景图拍照捕获Fragment
 * 主要功能：
 * 1. 使用CameraX API进行相机预览和拍照
 * 2. 实时监测设备方向变化，确保拍摄角度的一致性
 * 3. 捕获并处理图像，准备进行全景图拼接
 * 4. 提供直观的用户界面，显示拍照状态和设备方向信息
 *
 * 传感器监听器实现：用于监测设备方向和运动状态
 */
public class CaptureFragment extends Fragment implements SensorEventListener {
    private static final String TAG = "CaptureFragment";
    private static final int REQUEST_CAMERA_PERMISSION = 1001; // 相机权限请求码
    private static final int WARNING_THRESHOLD = 5; // 最大拍照数量阈值
    private static final float PITCH_THRESHOLD = 20.0f; // 俯仰角阈值（度）
    private static final float ROLL_THRESHOLD = 30.0f;  // 横滚角阈值（度）
    private static final int REQUIRED_MIN_ASPECT_RATIO = 1; // 最小宽高比要求（宽:高 = 4:3）
    // 滤波系数 (ALPHA)。范围 0~1。
    private static final float ALPHA = 0.06f;

    // 用于存储平滑后的传感器数据
    private float[] smoothAccelerometer = new float[3];
    private float[] smoothMagnetometer = new float[3];
    private float[] smoothedOrientation = new float[3];

    // 传感器相关变量
    private SensorManager sensorManager;
    private Sensor gyroSensor;          // 陀螺仪传感器
    private Sensor accelerometerSensor; // 加速度传感器
    private Sensor magnetometerSensor;  // 磁力计传感器

    // 当前传感器数据
    private float[] currentGyroValues = new float[3]; // x, y, z轴角速度
    private float[] currentOrientation = new float[3]; // 方位角，俯仰角，横滚角
    private float[] baseOrientation = null; // 第一张照片的基准方向

    // 方向偏移标志
    private boolean isOrientationExceeded = false;

    // 方向计算相关数据
    private float[] accelerometerData = new float[3];
    private float[] magnetometerData = new float[3];
    private boolean hasAccelerometerData = false;
    private boolean hasMagnetometerData = false;

    // UI组件
    private PreviewView previewView;     // 相机预览视图
    private ImageCapture imageCapture;   // 图像捕获对象
    private ExecutorService cameraExecutor; // 相机执行器（单线程）
    private ProcessCameraProvider cameraProvider; // 相机提供者
    private ArrayList<Bitmap> captures = new ArrayList<>(); // 捕获的图片列表
    private StitchingViewModel viewModel; // 视图模型，用于数据共享
    private boolean cameraStarted = false; // 相机是否已启动
    private boolean permissionRequested = false; // 是否已请求过权限
    // UI组件
    private LevelOverlayView levelOverlayView;

    // 界面控件
    private Button btnCapture;      // 拍照按钮
    private Button btnReset;        // 重置按钮
    private TextView tvCaptureCount; // 拍照计数显示
    private Button btnProceed;      // 处理按钮
    private TextView tvGyroStatus;   // 陀螺仪状态显示
    private TextView tvGyroInfo;     // 陀螺仪详细信息显示
    private TextView tvBaseOrientation; // 基准方向显示

    // 用于存储临时文件路径的列表
    private ArrayList<String> tempImagePaths = new ArrayList<>();

    // 相机状态枚举
    private enum CameraState {
        NO_PERMISSION,      // 无相机权限
        PERMISSION_GRANTED, // 权限已授予
        INITIALIZING,       // 初始化中
        READY,              // 准备就绪
        ERROR               // 错误状态
    }
    private CameraState cameraState = CameraState.NO_PERMISSION; // 当前相机状态

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 创建单线程执行器，用于相机操作
        cameraExecutor = Executors.newSingleThreadExecutor();

        // 初始化传感器管理器
        sensorManager = (SensorManager) requireActivity().getSystemService(requireActivity().SENSOR_SERVICE);
        if (sensorManager != null) {
            // 获取各类传感器
            gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            magnetometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

            // 记录传感器可用状态
            Log.i(TAG, "陀螺仪传感器可用: " + (gyroSensor != null));
            Log.i(TAG, "加速度传感器可用: " + (accelerometerSensor != null));
            Log.i(TAG, "磁力计传感器可用: " + (magnetometerSensor != null));
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // 加载布局文件
        return inflater.inflate(R.layout.fragment_capture, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initUI(view); // 初始化UI组件
        viewModel = new ViewModelProvider(requireActivity()).get(StitchingViewModel.class); // 获取ViewModel
        observeResetRequest(); // 监听重置请求
        checkCameraPermissionAndStart(); // 检查权限并启动相机
        updateUIState(); // 更新UI状态

        startGyroMonitoring(); // 启动陀螺仪监测
    }

    @Override
    public void onResume() {
        super.onResume();

        // 如果权限已授予但相机未启动，重新启动相机
        if (cameraState == CameraState.PERMISSION_GRANTED && !cameraStarted && previewView != null) {
            Log.i(TAG, "onResume - 重新启动相机");
            previewView.postDelayed(() -> startCamera(), 300);
        }

        startGyroMonitoring(); // 重新注册传感器监听
    }

    @Override
    public void onPause() {
        super.onPause();
        stopGyroMonitoring(); // 暂停时注销传感器监听以节省电量
    }

    /**
     * 低通滤波器
     * @param input 新的传感器原始数据
     * @param output 上一次平滑后的数据（既是输入也是输出）
     * @return 平滑后的数据
     */
    private float[] lowPass(float[] input, float[] output) {
        if (output == null) return input;

        for (int i = 0; i < input.length; i++) {
            // 公式：Output = Output + alpha * (Input - Output)
            // 也就是：保留大部分旧值，只接纳一小部分新值的变化
            output[i] = output[i] + ALPHA * (input[i] - output[i]);
        }
        return output;
    }

    /**
     * 启动陀螺仪监测
     * 注册陀螺仪、加速度计和磁力计传感器监听器
     */
    private void startGyroMonitoring() {
        if (sensorManager != null) {
            // 注册陀螺仪传感器（UI更新频率）
            if (gyroSensor != null) {
                sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_UI);
            }

            // 注册加速度计（用于计算方向）
            if (accelerometerSensor != null) {
                sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_UI);
            }

            // 注册磁力计（用于计算方向）
            if (magnetometerSensor != null) {
                sensorManager.registerListener(this, magnetometerSensor, SensorManager.SENSOR_DELAY_UI);
            }

            Log.i(TAG, "陀螺仪监测已启动");
        }
    }

    /**
     * 停止陀螺仪监测
     * 注销所有传感器监听器
     */
    private void stopGyroMonitoring() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
            Log.i(TAG, "陀螺仪监测已停止");
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        synchronized (this) {
            switch (event.sensor.getType()) {
                case Sensor.TYPE_GYROSCOPE:
                    System.arraycopy(event.values, 0, currentGyroValues, 0, 3);
                    break;

                case Sensor.TYPE_ACCELEROMETER:
                    // 【修改点】应用低通滤波，而不是直接复制
                    // 注意：第一次运行时 smoothAccelerometer 全是0，需要初始化
                    if (!hasAccelerometerData) {
                        System.arraycopy(event.values, 0, smoothAccelerometer, 0, 3);
                        hasAccelerometerData = true;
                    } else {
                        smoothAccelerometer = lowPass(event.values, smoothAccelerometer);
                    }
                    // 把平滑后的数据给 accelerometerData 用于后续计算
                    System.arraycopy(smoothAccelerometer, 0, accelerometerData, 0, 3);
                    break;

                case Sensor.TYPE_MAGNETIC_FIELD:
                    // 【修改点】磁力计也建议滤波
                    if (!hasMagnetometerData) {
                        System.arraycopy(event.values, 0, smoothMagnetometer, 0, 3);
                        hasMagnetometerData = true;
                    } else {
                        smoothMagnetometer = lowPass(event.values, smoothMagnetometer);
                    }
                    System.arraycopy(smoothMagnetometer, 0, magnetometerData, 0, 3);
                    break;
            }

            if (hasAccelerometerData && hasMagnetometerData) {
                calculateOrientation();
                updateGyroUI();
            }
        }
    }

    /**
     * 计算设备方向 (智能识别版 + 二级平滑)
     */
    private void calculateOrientation() {
        float[] rotationMatrix = new float[9];

        if (!SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerData, magnetometerData)) {
            return;
        }

        // ... (省略中间判断横竖屏的代码，保持不变) ...
        int displayRotation = requireActivity().getWindowManager().getDefaultDisplay().getRotation();

        // ... (省略重力检测横屏的代码，保持不变) ...
        if (displayRotation == android.view.Surface.ROTATION_0) {
            float xGravity = accelerometerData[0];
            if (xGravity < -4.5f) displayRotation = android.view.Surface.ROTATION_90;
            else if (xGravity > 4.5f) displayRotation = android.view.Surface.ROTATION_270;
        }

        float[] remappedMatrix = new float[9];
        int axisX, axisY;

        switch (displayRotation) {
            case android.view.Surface.ROTATION_90:
                axisX = SensorManager.AXIS_MINUS_Y;
                axisY = SensorManager.AXIS_Z;
                break;
            case android.view.Surface.ROTATION_270:
                axisX = SensorManager.AXIS_Y;
                axisY = SensorManager.AXIS_Z;
                break;
            case android.view.Surface.ROTATION_180:
                axisX = SensorManager.AXIS_MINUS_X;
                axisY = SensorManager.AXIS_Z;
                break;
            case android.view.Surface.ROTATION_0:
            default:
                axisX = SensorManager.AXIS_X;
                axisY = SensorManager.AXIS_Z;
                break;
        }

        SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remappedMatrix);

        float[] orientationValues = new float[3];
        SensorManager.getOrientation(remappedMatrix, orientationValues);

        // 【修改开始】应用二级平滑滤波 (Output Smoothing)
        // 这里的 ALPHA 使用同样的系数，或者定义一个新的 SMOOTH_FACTOR = 0.1f
        for (int i = 0; i < 3; i++) {
            float degree = (float) Math.toDegrees(orientationValues[i]);

            // 处理 360/0 度边界突变问题 (针对方位角)
            if (i == 0) {
                if (degree < 0) degree += 360;
                // 方位角特殊平滑，防止在 0 和 360 之间跳变
                float diff = degree - smoothedOrientation[i];
                if (diff > 180) degree -= 360;
                else if (diff < -180) degree += 360;
            }

            // 低通滤波公式：新值 = 旧值 + 系数 * (目标值 - 旧值)
            smoothedOrientation[i] = smoothedOrientation[i] + ALPHA * (degree - smoothedOrientation[i]);

            // 赋值给 currentOrientation
            currentOrientation[i] = smoothedOrientation[i];
        }

        // 修正方位角显示范围 (0-360)
        if (currentOrientation[0] < 0) currentOrientation[0] += 360;
        if (currentOrientation[0] >= 360) currentOrientation[0] -= 360;

        // 【修改结束】

        if (baseOrientation != null) {
            checkOrientationDeviation();
        }
    }

    /**
     * 检查方向偏移
     * 检查俯仰角(pitch)和横滚角(roll)的偏移
     * 使用不同的阈值：俯仰角20度，横滚角30度
     */
    private void checkOrientationDeviation() {
        if (baseOrientation == null) {
            return;
        }

        // 计算俯仰角(索引1)和横滚角(索引2)的偏移
        float pitchDeviation = Math.abs(currentOrientation[1] - baseOrientation[1]);
        float rollDeviation = Math.abs(currentOrientation[2] - baseOrientation[2]);

        // 检查是否超过各自的阈值
        boolean pitchExceeded = pitchDeviation > PITCH_THRESHOLD;
        boolean rollExceeded = rollDeviation > ROLL_THRESHOLD;

        // 当俯仰角或横滚角的偏移有一个超过阈值时，就标记为方向偏移过大
        isOrientationExceeded = pitchExceeded || rollExceeded;

        // 记录调试信息
        if (isOrientationExceeded) {
            String message = String.format(Locale.getDefault(),
                    "方向偏移过大: 俯仰角偏移=%.1f°/%s 横滚角偏移=%.1f°/%s (阈值: 俯仰角%.0f°, 横滚角%.0f°)",
                    pitchDeviation, pitchExceeded ? "超出" : "正常",
                    rollDeviation, rollExceeded ? "超出" : "正常",
                    PITCH_THRESHOLD, ROLL_THRESHOLD);
            Log.i(TAG, message);
        }
    }

    /**
     * 更新陀螺仪UI显示
     * 在主线程中更新传感器状态和信息显示
     * 显示当前俯仰角和横滚角，以及基准方向（如果已设置）
     */
    private void updateGyroUI() {
        // 检查视图是否存在
        if (getActivity() == null) return;

        requireActivity().runOnUiThread(() -> {

            // 1. 更新可视化的水平仪 (核心修改)
            if (levelOverlayView != null) {
                // 将当前方向和基准方向传给 View，让它自己计算偏移并绘制
                // 注意：如果 baseOrientation 为 null，view 内部会处理为默认状态
                levelOverlayView.updateOrientation(currentOrientation, baseOrientation);
            }

            // 2. 更新文字状态提示 (简化逻辑，辅助视觉)
            if (tvGyroStatus != null) {
                if (baseOrientation == null) {
                    tvGyroStatus.setText("请保持手机平稳");
                    tvGyroStatus.setTextColor(Color.WHITE);
                    tvGyroStatus.setBackgroundColor(Color.TRANSPARENT);
                } else if (isOrientationExceeded) {
                    tvGyroStatus.setText("⚠️ 偏移过大，请调整回圆心");
                    tvGyroStatus.setTextColor(Color.RED);
                    tvGyroStatus.setBackgroundColor(Color.parseColor("#66000000"));
                } else {
                    tvGyroStatus.setText("✓ 角度良好");
                    tvGyroStatus.setTextColor(Color.GREEN);
                    tvGyroStatus.setBackgroundColor(Color.TRANSPARENT);
                }
            }

            // 3. 更新基准方向文字提示
            if (tvBaseOrientation != null) {
                if (baseOrientation != null) {
                    // 已锁定基准
                    tvBaseOrientation.setText("基准已锁定");
                    tvBaseOrientation.setTextColor(Color.GREEN);
                } else {
                    // 未锁定
                    tvBaseOrientation.setText("拍摄第一张照片以锁定角度");
                    tvBaseOrientation.setTextColor(Color.LTGRAY);
                }
            }

            // 原有的 tvGyroInfo 如果设为了 gone，这里更新它也没关系，如果不显示就算了
        });
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 传感器精度变化回调，当前无需特殊处理
    }

    /**
     * 初始化UI组件
     * 通过资源ID查找并初始化所有界面控件
     */
    private void initUI(View view) {
        // 使用getIdentifier动态查找视图（避免硬编码ID）
        int previewViewId = getResources().getIdentifier("previewView", "id", requireContext().getPackageName());
        int btnCaptureId = getResources().getIdentifier("btn_capture", "id", requireContext().getPackageName());
        int btnResetId = getResources().getIdentifier("btn_reset", "id", requireContext().getPackageName());
        int tvCaptureCountId = getResources().getIdentifier("tv_capture_count", "id", requireContext().getPackageName());
        int btnProceedId = getResources().getIdentifier("btn_proceed", "id", requireContext().getPackageName());

        // 陀螺仪相关视图ID
        int tvGyroStatusId = getResources().getIdentifier("tv_gyro_status", "id", requireContext().getPackageName());
        int tvGyroInfoId = getResources().getIdentifier("tv_gyro_info", "id", requireContext().getPackageName());
        int tvBaseOrientationId = getResources().getIdentifier("tv_base_orientation", "id", requireContext().getPackageName());

        int levelOverlayId = getResources().getIdentifier("levelOverlay", "id", requireContext().getPackageName());
        if (levelOverlayId != 0) {
            levelOverlayView = view.findViewById(levelOverlayId);
            // 设置阈值
            levelOverlayView.setThresholds(PITCH_THRESHOLD, ROLL_THRESHOLD);
        }

        // 初始化相机预览视图
        if (previewViewId != 0) {
            previewView = view.findViewById(previewViewId);
        }

        // 初始化拍照按钮
        if (btnCaptureId != 0) {
            btnCapture = view.findViewById(btnCaptureId);
            if (btnCapture != null) {
                btnCapture.setOnClickListener(v -> capturePhoto());
            }
        }

        // 初始化重置按钮
        if (btnResetId != 0) {
            btnReset = view.findViewById(btnResetId);
            if (btnReset != null) {
                btnReset.setOnClickListener(v -> resetCaptures());
            }
        }

        // 初始化拍照计数显示
        if (tvCaptureCountId != 0) {
            tvCaptureCount = view.findViewById(tvCaptureCountId);
        }

        // 初始化处理按钮
        if (btnProceedId != 0) {
            btnProceed = view.findViewById(btnProceedId);
            if (btnProceed != null) {
                btnProceed.setText("拼接");
                btnProceed.setOnClickListener(v -> proceedToPreview());
            }
        }

        // 初始化陀螺仪状态显示
        if (tvGyroStatusId != 0) {
            tvGyroStatus = view.findViewById(tvGyroStatusId);
        }

        // 初始化陀螺仪信息显示
        if (tvGyroInfoId != 0) {
            tvGyroInfo = view.findViewById(tvGyroInfoId);
        }

        // 初始化基准方向显示
        if (tvBaseOrientationId != 0) {
            tvBaseOrientation = view.findViewById(tvBaseOrientationId);
        }

        // 如果布局中没有陀螺仪状态视图，动态创建
        if (tvGyroStatus == null) {
            tvGyroStatus = new TextView(requireContext());
            tvGyroStatus.setText("正在初始化陀螺仪...");
            tvGyroStatus.setTextColor(Color.WHITE);
            tvGyroStatus.setBackgroundColor(Color.parseColor("#66000000"));
            tvGyroStatus.setPadding(16, 8, 16, 8);

            ViewGroup rootView = (ViewGroup) view;
            ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            rootView.addView(tvGyroStatus, params);
        }

        // 如果布局中没有陀螺仪信息视图，动态创建
        if (tvGyroInfo == null) {
            tvGyroInfo = new TextView(requireContext());
            tvGyroInfo.setText("陀螺仪数据将在此显示\n阈值: 俯仰角≤20° 横滚角≤30°");
            tvGyroInfo.setTextColor(Color.WHITE);
            tvGyroInfo.setBackgroundColor(Color.parseColor("#66000000"));
            tvGyroInfo.setPadding(16, 8, 16, 8);
            tvGyroInfo.setTextSize(12);
            tvGyroInfo.setMaxLines(3); // 允许显示多行

            ViewGroup rootView = (ViewGroup) view;
            ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params = new ViewGroup.MarginLayoutParams(params);
            ((ViewGroup.MarginLayoutParams) params).topMargin = 100; // 放在状态文字下方
            rootView.addView(tvGyroInfo, params);
        }

        // 如果布局中没有基准方向视图，动态创建
        if (tvBaseOrientation == null) {
            tvBaseOrientation = new TextView(requireContext());
            tvBaseOrientation.setText("基准: 未设置（拍摄第一张照片时记录）");
            tvBaseOrientation.setTextColor(Color.LTGRAY);
            tvBaseOrientation.setPadding(16, 8, 16, 8);
            tvBaseOrientation.setTextSize(12);

            ViewGroup rootView = (ViewGroup) view;
            ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params = new ViewGroup.MarginLayoutParams(params);
            ((ViewGroup.MarginLayoutParams) params).topMargin = 180; // 放在陀螺仪信息下方
            rootView.addView(tvBaseOrientation, params);
        }

        // 如果未找到预览视图，在视图树中查找
        if (previewView == null) {
            previewView = findFirstPreviewView(view);
        }

        // 初始按钮状态
        if (btnCapture != null) {
            btnCapture.setEnabled(false);
            btnCapture.setText("等待相机启动...");
        }
    }

    /**
     * 监听重置请求
     * 观察ViewModel中的重置标志，当需要重置时清空数据
     */
    private void observeResetRequest() {
        viewModel.getResetRequested().observe(getViewLifecycleOwner(), resetNeeded -> {
            if (resetNeeded != null && resetNeeded) {
                // 清空本地captures列表
                captures.clear();
                // 清理临时文件
                cleanupTempFiles();
                // 重置基准方向
                baseOrientation = null;
                isOrientationExceeded = false;
                updateUIState();
                updateGyroUI();

                // 重置请求已处理
                viewModel.resetRequestHandled();

                Log.i(TAG, "通过重置请求清空照片");
                Toast.makeText(requireContext(), "所有照片已清空", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 检查相机权限并启动相机
     * 如果已有权限则直接启动相机，否则请求权限
     */
    private void checkCameraPermissionAndStart() {
        Log.i(TAG, "检查相机权限...");

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            cameraState = CameraState.PERMISSION_GRANTED;
            Log.i(TAG, "相机权限已授予");

            // 延迟一小段时间启动相机，确保视图完全加载
            if (previewView != null) {
                previewView.postDelayed(() -> {
                    if (!cameraStarted) {
                        Log.i(TAG, "从权限检查启动相机");
                        startCamera();
                    }
                }, 500);
            } else {
                Log.w(TAG, "预览视图为空，无法启动相机");
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
            Log.i(TAG, "相机权限未授予");
            // 如果还没有请求过权限，请求权限
            if (!permissionRequested) {
                permissionRequested = true;
                requestCameraPermission();
            }
        }
    }

    /**
     * 请求相机权限
     * 向用户请求相机使用权限
     */
    private void requestCameraPermission() {
        Log.i(TAG, "请求相机权限...");

        // 如果需要向用户解释为什么需要权限
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            Toast.makeText(requireContext(),
                    "需要相机权限来拍摄照片",
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
                Log.i(TAG, "通过请求授予相机权限");

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
                Log.w(TAG, "相机权限被拒绝");

                // 显示错误信息
                Toast.makeText(requireContext(),
                        "需要相机权限才能使用此功能",
                        Toast.LENGTH_LONG).show();

                // 禁用拍照按钮
                if (btnCapture != null) {
                    btnCapture.setEnabled(false);
                    btnCapture.setText("需要相机权限");
                }
            }
            updateUIState();
        }
    }

    /**
     * 启动相机
     * 初始化CameraX并绑定相机用例（预览和拍照）
     */
    private void startCamera() {
        Log.i(TAG, "调用启动相机。cameraStarted: " + cameraStarted + ", previewView: " + previewView);

        if (cameraStarted) {
            Log.w(TAG, "相机已启动，跳过...");
            return;
        }

        if (previewView == null) {
            Log.e(TAG, "预览视图为空，无法启动相机");
            if (getView() != null) {
                previewView = findFirstPreviewView(getView());
                if (previewView == null) {
                    Log.e(TAG, "仍无法找到预览视图");
                    Toast.makeText(requireContext(), "找不到相机预览", Toast.LENGTH_LONG).show();
                    return;
                }
            } else {
                return;
            }
        }

        cameraState = CameraState.INITIALIZING;
        Log.i(TAG, "启动相机...");

        // 获取相机提供者
        ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(requireContext());
        providerFuture.addListener(() -> {
            try {
                cameraProvider = providerFuture.get();
                if (cameraProvider != null) {
                    bindCameraUseCases(); // 绑定相机用例
                    cameraStarted = true;
                    cameraState = CameraState.READY;
                    Log.i(TAG, "相机启动成功");

                    // 更新UI，启用拍照按钮
                    requireActivity().runOnUiThread(() -> {
                        if (btnCapture != null) {
                            btnCapture.setEnabled(true);
                            btnCapture.setText("拍照");
                        }
                        Toast.makeText(requireContext(), "相机准备就绪", Toast.LENGTH_SHORT).show();
                        updateUIState();
                    });
                } else {
                    cameraState = CameraState.ERROR;
                    Log.e(TAG, "相机提供者为空");
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "获取相机提供者失败", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                cameraState = CameraState.ERROR;
                Log.e(TAG, "相机初始化失败", e);

                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(),
                            "相机初始化失败: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    /**
     * 绑定相机用例
     * 配置预览和拍照用例，并绑定到相机生命周期
     */
    private void bindCameraUseCases() {
        if (cameraProvider == null || previewView == null) {
            Log.e(TAG, "相机提供者或预览视图为空");
            return;
        }

        Log.i(TAG, "绑定相机用例...");

        // 先解绑所有用例
        try {
            cameraProvider.unbindAll();
            Log.i(TAG, "已解绑所有先前用例");
        } catch (Exception e) {
            Log.w(TAG, "解绑先前用例时出错: " + e.getMessage());
        }

        // 创建预览用例
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // 创建拍照用例（最小化延迟模式）
        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetRotation(previewView.getDisplay().getRotation())
                .build();

        // 选择后置摄像头
        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

        try {
            // 检查是否有后置摄像头
            if (!cameraProvider.hasCamera(cameraSelector)) {
                Log.w(TAG, "后置摄像头不可用，使用前置摄像头");
                cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
            }

            // 绑定到生命周期
            cameraProvider.bindToLifecycle(
                    getViewLifecycleOwner(),
                    cameraSelector,
                    preview,
                    imageCapture
            );
            Log.i(TAG, "相机用例绑定成功");
        } catch (Exception e) {
            Log.e(TAG, "用例绑定失败", e);

            // 尝试只绑定预览
            try {
                cameraProvider.bindToLifecycle(
                        getViewLifecycleOwner(),
                        cameraSelector,
                        preview
                );
                Log.i(TAG, "仅预览绑定成功");
                imageCapture = null;
            } catch (Exception ex) {
                Log.e(TAG, "仅预览绑定也失败", ex);
                throw ex;
            }
        }
    }

    /**
     * 拍照操作
     * 检查相机状态和拍照条件，然后执行拍照
     */
    private void capturePhoto() {
        Log.i(TAG, "拍照按钮点击。相机状态: " + cameraState);

        if (cameraState != CameraState.READY) {
            Log.e(TAG, "相机未就绪。状态: " + cameraState);
            Toast.makeText(requireContext(),
                    "相机未就绪。请稍候...",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (imageCapture == null) {
            Log.e(TAG, "图像捕获为空");
            Toast.makeText(requireContext(), "相机未就绪。请稍候...", Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查照片数量，超过5张禁止拍摄
//        if (captures.size() >= WARNING_THRESHOLD) {
//            // 直接显示提示，不询问是否继续
//            new AlertDialog.Builder(requireContext())
//                    .setTitle("达到最大照片数量")
//                    .setMessage("您已拍摄5张照片。已达到最大限制。请进行拼接或重置重新开始。")
//                    .setPositiveButton("确定", new DialogInterface.OnClickListener() {
//                        @Override
//                        public void onClick(DialogInterface dialog, int which) {
//                            dialog.dismiss();
//                        }
//                    })
//                    .show();
//            return;
//        }

        // 检查陀螺仪状态（从第二张照片开始检查）
        if (isOrientationExceeded && captures.size() > 0) {
            // 显示具体的偏移信息
            String message = "当前方向已超过基准方向：\n";
            if (baseOrientation != null) {
                float pitchDeviation = Math.abs(currentOrientation[1] - baseOrientation[1]);
                float rollDeviation = Math.abs(currentOrientation[2] - baseOrientation[2]);

                if (pitchDeviation > PITCH_THRESHOLD) {
                    message += String.format(Locale.getDefault(), "• 俯仰角偏移: %.1f° (超过%.0f°阈值)\n", pitchDeviation, PITCH_THRESHOLD);
                }
                if (rollDeviation > ROLL_THRESHOLD) {
                    message += String.format(Locale.getDefault(), "• 横滚角偏移: %.1f° (超过%.0f°阈值)\n", rollDeviation, ROLL_THRESHOLD);
                }
            }
            message += "\n是否继续？";

            new AlertDialog.Builder(requireContext())
                    .setTitle("警告：方向偏移过大")
                    .setMessage(message)
                    .setPositiveButton("继续", (dialog, which) -> {
                        dialog.dismiss();
                        takePhoto();
                    })
                    .setNegativeButton("取消", (dialog, which) -> {
                        dialog.dismiss();
                    })
                    .show();
            return;
        }

        takePhoto();
    }

    /**
     * 执行拍照
     * 创建图像文件并调用CameraX拍照API
     */
    private void takePhoto() {
        // 再次检查照片数量（防止在对话框显示期间有其他操作）
//        if (captures.size() >= WARNING_THRESHOLD) {
//            Toast.makeText(requireContext(), "已达到最大照片数量限制", Toast.LENGTH_SHORT).show();
//            return;
//        }

        // 禁用拍摄按钮避免连续点击
        if (btnCapture != null) {
            btnCapture.setEnabled(false);
            btnCapture.setText("拍摄中...");
        }

        Log.i(TAG, "拍摄照片...");

        // 创建照片文件
        File photoFile = createImageFile();
        if (photoFile == null) {
            Log.e(TAG, "创建图像文件失败");
            Toast.makeText(requireContext(), "创建图像文件失败", Toast.LENGTH_SHORT).show();
            if (btnCapture != null) {
                btnCapture.setEnabled(true);
                btnCapture.setText("拍照");
            }
            return;
        }

        // 配置输出文件选项
        ImageCapture.OutputFileOptions outputFileOptions =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        // 执行拍照
        imageCapture.takePicture(
                outputFileOptions,
                ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        Log.i(TAG, "照片保存成功到: " + photoFile.getAbsolutePath());

                        // 处理照片：旋转并调整方向
                        processAndSavePhoto(photoFile);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "拍摄失败: " + exception.getMessage(), exception);
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(),
                                    "拍摄失败: " + exception.getMessage(),
                                    Toast.LENGTH_SHORT).show();

                            if (btnCapture != null) {
                                btnCapture.setEnabled(true);
                                btnCapture.setText("拍照");
                            }
                        });
                    }
                }
        );
    }

    /**
     * 计算采样率 (inSampleSize)
     * 保证加载的图片宽高不超过 reqWidth 和 reqHeight
     */
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // 原图的宽高
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // 计算最大的 inSampleSize 值，该值必须是 2 的幂
            // 且保证宽和高都大于期望的宽高
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    /**
     * 处理并保存照片
     * 在后台线程中处理照片：降采样、旋转、检查宽高比、保存处理后的版本
     */
    private void processAndSavePhoto(File photoFile) {
        // 在后台线程处理照片
        cameraExecutor.execute(() -> {
            try {
                // 定义最大分辨率限制 (例如 2000px)
                // 原图 4000x3000 -> 内存约 48MB
                // 降采样后 2000x1500 -> 内存约 12MB (ARGB_8888) 或 6MB (RGB_565)
                final int MAX_DIMENSION = 3000;

                // 1. 第一次解析：只读取图片的宽高信息，不加载到内存
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true; // 只读边框
                BitmapFactory.decodeFile(photoFile.getAbsolutePath(), options);

                // 2. 计算缩放比例
                options.inSampleSize = calculateInSampleSize(options, MAX_DIMENSION, MAX_DIMENSION);

                // 3. 第二次解析：真正加载图片 (使用计算好的缩放比例)
                options.inJustDecodeBounds = false;

                // 【内存优化】使用 RGB_565 格式，比默认的 ARGB_8888 节省 50% 内存
                // 对于拍照拼接，去掉透明通道通常没有影响
                options.inPreferredConfig = Bitmap.Config.RGB_565;

                // 加载降采样后的图片
                Bitmap originalBitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath(), options);

                if (originalBitmap != null) {
                    Log.i(TAG, "图片加载成功，原始尺寸缩放至: " + originalBitmap.getWidth() + "x" + originalBitmap.getHeight());

                    // 旋转图片：确保短边为左右，长边为上下（横屏模式）
                    Bitmap rotatedBitmap = rotateToLandscape(originalBitmap);

                    // 检查宽高比
                    if (!checkAspectRatio(rotatedBitmap)) {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(),
                                    "警告：照片宽高比太小。请确保横屏拍摄。",
                                    Toast.LENGTH_LONG).show();
                        });
                    }

                    // 保存处理后的图片到缓存
                    // 注意：这里保存的文件已经是缩小后的版本了
                    Bitmap finalBitmap = saveToCache(rotatedBitmap, photoFile);

                    if (finalBitmap != null) {
                        // 注意：虽然这里 add 了 bitmap，但建议按照之前的建议，ViewModel 只存路径
                        // 如果你还没改 ViewModel，这里暂时不动
                        captures.add(finalBitmap);
                        Log.i(TAG, "照片已捕获并处理。总数: " + captures.size());

                        // 如果是第一张照片，记录基准方向
                        if (captures.size() == 1) {
                            synchronized (this) {
                                baseOrientation = new float[3];
                                System.arraycopy(currentOrientation, 0, baseOrientation, 0, 3);
                                Log.i(TAG, "基准方向已记录: " +
                                        String.format(Locale.getDefault(), "方位角=%.1f°, 俯仰角=%.1f°, 横滚角=%.1f°",
                                                baseOrientation[0], baseOrientation[1], baseOrientation[2]));

                                requireActivity().runOnUiThread(() -> {
                                    Toast.makeText(requireContext(),
                                            "基准方向已记录",
                                            Toast.LENGTH_SHORT).show();
                                    // 更新UI显示基准方向
                                    updateGyroUI();
                                });
                            }
                        }

                        // 更新UI
                        requireActivity().runOnUiThread(() -> {
                            updateUIState();
                            // 注意：移除了 WARNING_THRESHOLD 的显示，如果你之前已经删了
                            Toast.makeText(requireContext(),
                                    String.format("已拍摄照片 %d 张", captures.size()),
                                    Toast.LENGTH_SHORT).show();
                        });
                    }

                    // 回收原始bitmap
                    if (originalBitmap != null && !originalBitmap.isRecycled()) {
                        originalBitmap.recycle();
                    }
                    if (rotatedBitmap != null && rotatedBitmap != finalBitmap && rotatedBitmap != originalBitmap && !rotatedBitmap.isRecycled()) {
                        rotatedBitmap.recycle();
                    }
                } else {
                    Log.e(TAG, "从文件解码bitmap失败");
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "处理图像失败", Toast.LENGTH_SHORT).show();
                    });
                }

                // 删除临时文件 (原始大图)
                if (photoFile.exists()) {
                    boolean deleted = photoFile.delete();
                    Log.i(TAG, "临时文件删除: " + deleted);
                }

                // 恢复拍照按钮
                requireActivity().runOnUiThread(() -> {
                    if (btnCapture != null) {
                        btnCapture.setEnabled(cameraState == CameraState.READY);
                        btnCapture.setText("拍照");
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "处理照片时出错: " + e.getMessage(), e);
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "处理照片时出错", Toast.LENGTH_SHORT).show();
                    if (btnCapture != null) {
                        btnCapture.setEnabled(true);
                        btnCapture.setText("拍照");
                    }
                });
            } catch (OutOfMemoryError oom) {
                // 捕获 OOM 异常，防止应用崩溃
                Log.e(TAG, "内存不足，无法处理照片", oom);
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "内存不足，请重启应用或减少拍摄数量", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * 旋转到横屏
     * 如果照片是竖屏拍摄，则旋转90度转换为横屏
     */
    private Bitmap rotateToLandscape(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // 如果高度大于宽度，说明是竖屏拍摄，需要旋转90度
        if (height > width) {
            Log.i(TAG, "将竖屏照片旋转为横屏");
            // 旋转90度
            android.graphics.Matrix matrix = new android.graphics.Matrix();
            matrix.postRotate(90);

            // 创建旋转后的bitmap
            Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
            bitmap.recycle(); // 回收原始bitmap
            return rotatedBitmap;
        }

        return bitmap; // 已经是横屏，直接返回
    }

    /**
     * 检查宽高比
     * 确保照片满足最小宽高比要求
     */
    private boolean checkAspectRatio(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // 计算宽高比
        float aspectRatio = (float) Math.max(width, height) / Math.min(width, height);

        Log.i(TAG, String.format("照片宽高比: %.2f (宽度=%d, 高度=%d)", aspectRatio, width, height));

        // 检查是否达到最小宽高比要求
        return aspectRatio >= REQUIRED_MIN_ASPECT_RATIO;
    }

    /**
     * 保存到缓存
     * 将处理后的bitmap保存到应用的缓存目录，并返回预览版本
     */
    private Bitmap saveToCache(Bitmap bitmap, File originalFile) {
        try {
            // 创建处理后的文件名
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String imageFileName = "PANORAMA_TEMP_" + timeStamp + "_" + captures.size() + ".jpg";

            // 获取缓存目录
            File cacheDir = requireContext().getCacheDir();
            File outputFile = new File(cacheDir, imageFileName);

            // 保存图片
            FileOutputStream fos = new FileOutputStream(outputFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos); // 90% 质量
            fos.flush();
            fos.close();

            Log.i(TAG, "处理后的照片保存到缓存: " + outputFile.getAbsolutePath());

            // 记录临时文件路径
            tempImagePaths.add(outputFile.getAbsolutePath());

            // 返回一个缩小的版本用于预览，以节省内存
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2; // 缩小一半
            Bitmap previewBitmap = BitmapFactory.decodeFile(outputFile.getAbsolutePath(), options);

            return previewBitmap != null ? previewBitmap : bitmap;

        } catch (IOException e) {
            Log.e(TAG, "保存到缓存失败: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * 创建图像文件
     * 创建临时文件用于存储原始照片
     */
    private File createImageFile() {
        try {
            // 创建临时文件目录 - 使用缓存目录
            File storageDir = requireContext().getCacheDir();

            // 确保目录存在
            if (!storageDir.exists()) {
                storageDir.mkdirs();
            }

            // 创建文件名
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String imageFileName = "PANORAMA_RAW_" + timeStamp + "_" + captures.size();
            File imageFile = File.createTempFile(
                    imageFileName,  /* 前缀 */
                    ".jpg",         /* 后缀 */
                    storageDir      /* 目录 */
            );

            return imageFile;
        } catch (IOException e) {
            Log.e(TAG, "创建图像文件失败", e);
            return null;
        }
    }

    /**
     * 清理临时文件
     * 删除所有保存在缓存中的图片文件
     */
    private void cleanupTempFiles() {
        int deletedCount = 0;
        for (String path : tempImagePaths) {
            File file = new File(path);
            if (file.exists()) {
                boolean deleted = file.delete();
                if (deleted) {
                    deletedCount++;
                    Log.i(TAG, "已删除临时文件: " + path);
                } else {
                    Log.w(TAG, "删除临时文件失败: " + path);
                }
            }
        }
        tempImagePaths.clear();
        Log.i(TAG, "共清理了 " + deletedCount + " 个临时文件");
    }

    /**
     * 显示照片过多警告
     * 当照片数量超过5张时显示警告（当前未使用）
     */
    private void showTooManyPhotosWarning() {
        new AlertDialog.Builder(requireContext())
                .setTitle("照片过多")
                .setMessage("您已拍摄超过5张照片。照片过多可能会影响拼接质量和性能。")
                .setPositiveButton("继续", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
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

    /**
     * 更新UI状态
     * 根据当前拍照数量和相机状态更新界面显示
     */
    private void updateUIState() {
        if (getView() == null) return;

        // 更新计数显示
        if (tvCaptureCount != null) {
            tvCaptureCount.setText(String.format("已拍摄: %d", captures.size()));
            tvCaptureCount.setTextColor(Color.WHITE); // 始终保持白色，不再变红
        }

        // 更新按钮状态
        // 2. 修改按钮状态逻辑
        if (btnCapture != null) {
            // 原代码: boolean canCapture = cameraState == CameraState.READY && captures.size() < WARNING_THRESHOLD;
            // 修改为: 只要相机准备好就可以拍
            boolean canCapture = cameraState == CameraState.READY;

            btnCapture.setEnabled(canCapture);

            if (cameraState == CameraState.READY) {
                // 移除 "已达上限" 的判断
                btnCapture.setText("拍照");
            } else if (cameraState == CameraState.NO_PERMISSION) {
                btnCapture.setText("需要权限");
            } else {
                btnCapture.setText("相机加载中...");
            }
        }

        // 重置按钮状态
        if (btnReset != null) {
            btnReset.setEnabled(!captures.isEmpty());
        }

        // 处理按钮状态
        if (btnProceed != null) {
            btnProceed.setEnabled(captures.size() >= 2);
            // 移除 "立即拼接!" 的强制提示，始终显示 "拼接"
            btnProceed.setText("拼接");
        }
    }

    /**
     * 重置捕获的照片
     * 清空所有已拍摄的照片，重置基准方向，并清理临时文件
     */
    private void resetCaptures() {
        if (captures.isEmpty()) {
            Toast.makeText(requireContext(), "没有照片可清除", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("清空所有照片")
                .setMessage("确定要清空所有 " + captures.size() + " 张照片吗？")
                .setPositiveButton("是", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        captures.clear();
                        // 清理临时文件
                        cleanupTempFiles();
                        baseOrientation = null;
                        isOrientationExceeded = false;
                        updateUIState();
                        updateGyroUI();
                        Toast.makeText(requireContext(), "所有照片已清空", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("否", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    /**
     * 进入预览界面
     * 将捕获的照片传递给ViewModel，并导航到预览Fragment
     * 注意：这里不再清理临时文件，因为预览和拼接过程还需要这些文件
     */
    private void proceedToPreview() {
        if (captures.size() < 2) {
            Toast.makeText(requireContext(), "至少需要2张照片才能拼接", Toast.LENGTH_SHORT).show();
            return;
        }

        // 保存到ViewModel
        viewModel.setCaptures(new ArrayList<>(captures));
        // 传递临时文件路径给ViewModel，以便在拼接完成后清理
        viewModel.setTempImagePaths(new ArrayList<>(tempImagePaths));

        // 显示成功消息
        Toast.makeText(requireContext(),
                String.format("%d 张照片准备预览", captures.size()),
                Toast.LENGTH_SHORT).show();

        Log.i(TAG, "准备预览 " + captures.size() + " 张照片");

        // 尝试导航到预览界面
        try {
            // 使用 Navigation.findNavController
            NavController navController = Navigation.findNavController(requireView());

            // 尝试导航
            try {
                navController.navigate(R.id.action_captureFragment_to_previewFragment);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "导航操作未找到: " + e.getMessage());
                // 如果action不存在，使用手动方法
                showPreviewFragmentManually();
            }
        } catch (Exception e) {
            Log.e(TAG, "导航失败: " + e.getMessage());
            // 如果找不到NavController，使用手动方法
            showPreviewFragmentManually();
        }
    }

    /**
     * 手动显示预览Fragment
     * 当导航失败时，使用Fragment事务手动显示预览界面
     */
    private void showPreviewFragmentManually() {
        try {
            // 创建预览Fragment
            PreviewFragment previewFragment = new PreviewFragment();

            // 使用Fragment事务显示
            // 注意：这里假设你的Activity有一个容器View，id为"nav_host_fragment"
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.nav_host_fragment, previewFragment) // 使用导航宿主容器ID
                    .addToBackStack("capture") // 添加到返回栈
                    .commit();

            Log.i(TAG, "预览Fragment手动显示");
        } catch (Exception e) {
            Log.e(TAG, "手动显示预览Fragment失败: " + e.getMessage());
            Toast.makeText(requireContext(), "显示预览失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 在视图树中查找第一个 PreviewView
     * 递归遍历视图树，查找PreviewView组件
     */
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

    @Override
    public void onStop() {
        super.onStop();
        // 停止相机
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        cameraStarted = false;
        Log.i(TAG, "相机已停止");

        // 停止陀螺仪监听
        stopGyroMonitoring();
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
        Log.i(TAG, "onDestroyView中释放相机资源");

        // 停止陀螺仪监听
        stopGyroMonitoring();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 关闭执行器
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }

        // 清理临时文件（如果还有未处理的）
        if (!tempImagePaths.isEmpty()) {
            cleanupTempFiles();
        }

        Log.i(TAG, "Fragment已销毁");
    }
}