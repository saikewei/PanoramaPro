package com.example.panoramapro;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.panoramapro.utils.FileUtils;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import com.example.panoramapro.core.LaMaCompleter;

@RunWith(AndroidJUnit4.class)
public class LaMaInferenceTest {

    private static final String TAG = "LaMaTest";

    // 你的模型文件名 (确保 assets 里有这个文件)
    private static final String MODEL_NAME = "lama_fp32.onnx";
    // 测试图片文件名
    private static final String TEST_IMAGE_NAME = "completerTest.jpg";

    @Test
    public void testOpenImageFile() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Bitmap inputBitmap;

        try {
            InputStream is = context.getAssets().open(TEST_IMAGE_NAME);
            inputBitmap = BitmapFactory.decodeStream(is);
            Assert.assertNotNull("测试图片创建失败", inputBitmap);
        } catch (IOException e) {
            Log.e(TAG, "无法打开测试图片: " + TEST_IMAGE_NAME, e);
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testOpenModelFile() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        try {
            InputStream is = context.getAssets().open(MODEL_NAME);
            Assert.assertNotNull("模型文件打开失败", is);
        } catch (IOException e) {
            Log.e(TAG, "无法打开模型文件: " + MODEL_NAME, e);
            throw new RuntimeException(e);
        }

    }

    @Test
    public void testLaMaInpainting() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        // 1. 准备模型文件 (从 Assets 拷贝到 FilesDir)
        // ONNX Runtime 无法直接读取 Assets，必须拷贝出来
        Log.i(TAG, "正在拷贝模型文件...");
        String modelPath = FileUtils.copyAssetToFilesDir(context, MODEL_NAME);

        Assert.assertNotNull("模型拷贝失败，请检查 assets 目录下是否有 " + MODEL_NAME, modelPath);
        File modelFile = new File(modelPath);
        Assert.assertTrue("模型文件不存在", modelFile.exists());
        Assert.assertTrue("模型文件大小为0", modelFile.length() > 0);

        // 2. 准备输入图像
        Bitmap inputBitmap;

        try {
            InputStream is = context.getAssets().open(TEST_IMAGE_NAME);
            inputBitmap = BitmapFactory.decodeStream(is);
            Assert.assertNotNull("测试图片创建失败", inputBitmap);
        } catch (IOException e) {
            Log.e(TAG, "无法打开测试图片: " + TEST_IMAGE_NAME, e);
            throw new RuntimeException(e);
        }

//        // 保存输入图以便对比
//        saveBitmapForViewing(context, inputBitmap, "lama_input.jpg");

        // 3. 初始化 AI 引擎
        Log.i(TAG, "正在初始化 LaMa 引擎...");
        long initStart = System.currentTimeMillis();

        // 构造 LaMaCompleter
        LaMaCompleter completer = new LaMaCompleter(modelPath);

        long initEnd = System.currentTimeMillis();
        Log.i(TAG, "模型加载耗时: " + (initEnd - initStart) + "ms");

        // 4. 执行推理 (Inference)
        Log.i(TAG, "开始执行推理...");
        long inferStart = System.currentTimeMillis();

        Bitmap result = completer.complete(inputBitmap);

        long inferEnd = System.currentTimeMillis();
        Log.i(TAG, "推理耗时: " + (inferEnd - inferStart) + "ms");

        // 5. 释放资源
        completer.release();

        // 6. 验证结果
        Assert.assertNotNull("推理结果不应为空", result);
        Assert.assertEquals("宽度应保持不变", inputBitmap.getWidth(), result.getWidth());
        Assert.assertEquals("高度应保持不变", inputBitmap.getHeight(), result.getHeight());

        // 7. 核心验证：检查黑边是否被填充
        // 原图 (10, 10) 处是纯黑的，如果 AI 工作正常，这里应该被填上了颜色
        int pixelColor = result.getPixel(10, 10);
        Log.i(TAG, "边缘点(10,10) 颜色: " + Integer.toHexString(pixelColor));

        // 验证不是纯黑 (Alpha=FF, R=0, G=0, B=0 => -16777216)
        Assert.assertNotEquals("边缘点依然是黑色，AI 补全未生效", Color.BLACK, pixelColor);

//        // 8. 保存结果图 (这是最重要的，你要亲眼看看 AI 补得怎么样)
//        saveBitmapForViewing(context, result, "lama_output.jpg");
    }
}