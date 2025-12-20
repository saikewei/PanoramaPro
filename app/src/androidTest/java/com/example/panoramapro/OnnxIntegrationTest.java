package com.example.panoramapro;

import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class OnnxIntegrationTest {

    private static final String TAG = "OnnxTest";

    @Test
    public void testOnnxRuntimeLoading() {
        Log.i(TAG, "开始检查 ONNX Runtime 环境...");

        OnnxEnvironment envChecker = new OnnxEnvironment();

        // 调用 Native 方法
        String result = envChecker.checkRuntime();

        Log.i(TAG, "检查结果: " + result);

        // 验证 1: 结果不为空
        Assert.assertNotNull("Native 方法返回了空指针", result);

        // 验证 2: 必须包含 "Success"
        // 如果包含 "Error"，说明 C++ 层捕获到了异常
        Assert.assertTrue("ONNX 初始化失败: " + result, result.startsWith("Success"));

        // 验证 3: 确保版本号不是空的 (防止获取到空字符串)
        Assert.assertTrue("版本号异常", result.contains("Version:"));
    }
}
