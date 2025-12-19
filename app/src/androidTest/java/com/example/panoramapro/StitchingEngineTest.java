package com.example.panoramapro;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.example.panoramapro.core.StitchingEngine;

@RunWith(AndroidJUnit4.class)
public class StitchingEngineTest {

    private static final String TAG = "StitchingTest";

    @Test
    public void testStitchImages() throws IOException {
        // 1. 获取 Context (用于访问 Assets)
        Context context = InstrumentationRegistry.getInstrumentation().getContext();

        // 2. 从 Assets 加载图片
        List<Bitmap> inputBitmaps = new ArrayList<>();
        inputBitmaps.add(loadBitmapFromAssets(context, "1.jpg")); // 替换你的图片名
        inputBitmaps.add(loadBitmapFromAssets(context, "2.jpg"));

        Assert.assertNotNull("图片1加载失败", inputBitmaps.get(0));
        Assert.assertNotNull("图片2加载失败", inputBitmaps.get(1));

        // 3. 初始化引擎并执行拼接
        StitchingEngine engine = new StitchingEngine();

        long startTime = System.currentTimeMillis();
        Bitmap result = engine.stitchImages(inputBitmaps, true);
        long endTime = System.currentTimeMillis();

        Log.i(TAG, "拼接耗时: " + (endTime - startTime) + "ms");

        // 4. 验证结果
        Assert.assertNotNull("拼接结果不应为空", result);
        Assert.assertTrue("结果宽度应大于0", result.getWidth() > 0);
        Assert.assertTrue("结果高度应大于0", result.getHeight() > 0);
    }

    /**
     * 辅助方法：从 Assets 读取 Bitmap
     */
    private Bitmap loadBitmapFromAssets(Context context, String fileName) throws IOException {
        InputStream is = context.getAssets().open(fileName);
        // 使用 ARGB_8888 确保格式符合你 JNI 层的要求
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        // 使得 Bitmap 可变 (虽然对于输入不是必须的，但有时候避免坑)
        options.inMutable = true;
        return BitmapFactory.decodeStream(is, null, options);
    }
}
