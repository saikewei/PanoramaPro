// utils/BitmapSaver.java
package com.example.panoramapro.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BitmapSaver {
    private static final String TAG = "BitmapSaver";

    public static File getPanoramaDir(Context context) {
        File privateDir;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            privateDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        } else {
            privateDir = new File(context.getExternalFilesDir(null), "Pictures");
        }
        File panoramaDir = new File(privateDir, "Panorama");
        if (!panoramaDir.exists()) {
            panoramaDir.mkdirs();
        }
        return panoramaDir;
    }

    /**
     * 保存Bitmap到应用私有目录
     * @param context 上下文
     * @param bitmap 要保存的Bitmap
     * @return 保存的文件路径，失败返回null
     */
    public static String saveBitmapToPrivateStorage(Context context, Bitmap bitmap) {
        if (bitmap == null) {
            Log.e(TAG, "Bitmap is null");
            return null;
        }

        FileOutputStream fos = null;
        try {
            // 获取应用私有目录
            File privateDir;

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Android 10+ 使用Pictures目录
                privateDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            } else {
                // Android 10以下
                privateDir = new File(context.getExternalFilesDir(null), "Pictures");
            }

            // 创建Panorama子文件夹
            privateDir = new File(privateDir, "Panorama");

            // 确保目录存在
            if (!privateDir.exists() && !privateDir.mkdirs()) {
                Log.e(TAG, "Failed to create directory: " + privateDir.getAbsolutePath());
                return null;
            }

            // 生成文件名
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "panorama_" + timeStamp + ".jpg";

            // 创建文件
            File imageFile = new File(privateDir, fileName);

            // 保存Bitmap为JPEG
            fos = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos); // 90% 质量
            fos.flush();

            String filePath = imageFile.getAbsolutePath();
            Log.i(TAG, "Bitmap saved successfully: " + filePath);

            return filePath;

        } catch (IOException e) {
            Log.e(TAG, "Error saving bitmap: " + e.getMessage(), e);
            return null;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing stream: " + e.getMessage());
                }
            }
        }
    }
}