package com.example.panoramapro; // 👈 确保这里是你的包名

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class OpenCVIntegrationTest {

    // 测试 1: 验证 OpenCV 库能否初始化
    @Test
    public void verifyOpenCVLoaded() {
        // initDebug() 尝试加载 OpenCV 库
        boolean success = OpenCVLoader.initDebug();

        // 如果 success 为 true，说明 OpenCV 库链接成功
        assertTrue("OpenCV 库加载失败！请检查 CMake 或库文件路径", success);
    }

    // 测试 2: 验证你的 Native 库 (APAP算法) 能否加载
    @Test
    public void verifyMyNativeLibLoaded() {
        try {
            System.loadLibrary("panoramapro"); // 👈 名字要和你 CMakeLists.txt 里 add_library 的名字一致
            // 如果没抛出异常，说明成功
        } catch (UnsatisfiedLinkError e) {
            fail("你的 C++ 库加载失败: " + e.getMessage());
        }
    }

    // 测试 3: 验证能不能真正使用 OpenCV 的 C++ 对象 (Mat)
    @Test
    public void verifyMatrixCreation() {
        // 先加载库
        OpenCVLoader.initDebug();

        // 尝试创建一个 3x3 的矩阵
        Mat mat = new Mat(3, 3, CvType.CV_8UC1);

        // 填充颜色 (只是为了证明能操作内存)
        mat.setTo(new Scalar(255));

        // 断言：检查行数是否正确
        assertEquals("矩阵行数应该是 3", 3, mat.rows());
        assertEquals("矩阵列数应该是 3", 3, mat.cols());

        // 释放内存
        mat.release();
    }
}