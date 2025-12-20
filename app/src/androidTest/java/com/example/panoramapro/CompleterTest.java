package com.example.panoramapro;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;

import com.example.panoramapro.core.IImageCompleter;
import com.example.panoramapro.core.OpencvCompleter;

@RunWith(AndroidJUnit4.class)
public class CompleterTest {

    private static final String TAG = "CompleterTest";

    @Test
    public void testCompleteImageFromAssets() throws IOException {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();

        // 1. 从 Assets 加载一张测试图 (例如 1.jpg)
        // 注意：这张图最好不要太大，否则下面加边框后容易 OOM
        Bitmap source = loadBitmapFromAssets(context, "completerTest.jpg");
        Assert.assertNotNull("图片加载失败", source);

//        // 2. 构造一个带有"黑边"的测试场景
//        // 模拟全景拼接后的情况：中间有图，四周是黑的
//        int borderSize = 50; // 边框宽度 50px
//        int newWidth = source.getWidth() + (borderSize * 2);
//        int newHeight = source.getHeight() + (borderSize * 2);
//
//        Bitmap roughImage = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888);
//        Canvas canvas = new Canvas(roughImage);
//
//        // 2.1 填充背景为纯黑 (这是需要被补全的区域)
//        canvas.drawColor(Color.BLACK);
//
//        // 2.2 把原图画在正中间
//        canvas.drawBitmap(source, borderSize, borderSize, null);
//
//        // --- 验证输入状态 ---
//        // 检查左上角区域应该是黑色的
//        Assert.assertEquals("输入图边缘应为黑色", Color.BLACK, roughImage.getPixel(10, 10));

        // 3. 执行补全算法
        IImageCompleter completer = new OpencvCompleter();

        Log.i(TAG, "开始补全...");
        long startTime = System.currentTimeMillis();

        Bitmap result = completer.complete(source);

        long endTime = System.currentTimeMillis();
        Log.i(TAG, "补全耗时: " + (endTime - startTime) + "ms");

        // 4. 验证结果
        Assert.assertNotNull("结果不应为空", result);

        // 核心验证：原本是黑色的边缘 (10, 10)，现在应该有颜色了
        int pixelColor = result.getPixel(10, 10);
        Log.i(TAG, "边缘点(10,10) 补全后的颜色: " + Integer.toHexString(pixelColor));

        Assert.assertNotEquals("边缘不应再是纯黑色，说明补全生效", Color.BLACK, pixelColor);
    }

    /**
     * 辅助方法：从 Assets 读取 Bitmap
     */
    private Bitmap loadBitmapFromAssets(Context context, String fileName) throws IOException {
        InputStream is = context.getAssets().open(fileName);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inMutable = true; // 设为可变
        // 为了防止 OOM，如果图片太大，可以缩小一点读
        // options.inSampleSize = 2;
        return BitmapFactory.decodeStream(is, null, options);
    }
}