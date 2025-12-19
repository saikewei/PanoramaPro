package com.example.panoramapro.guidance;

public interface IGuidanceSystem {
    /**
     * 开启传感器监听
     */
    void startMonitoring();

    /**
     * 停止监听
     */
    void stopMonitoring();

    /**
     * 获取当前的指导建议
     * @return 状态枚举或字符串，如 "ROTATE_RIGHT", "LEVEL_OK", "TILT_UP"
     */
    String getCurrentInstruction();

    // 还需要一个回调接口来通知 UI 更新水平仪
    interface OnOrientationChangeListener {
        void onOrientationChanged(float azimuth, float pitch, float roll);
    }

    void setListener(OnOrientationChangeListener listener);
}
