package com.example.panoramapro.utils;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class FileUtils {
    private static final String TAG = "FileUtils";

    /**
     * 将 Assets 目录下的文件拷贝到 App 的私有内部存储目录 (filesDir)
     * 因为 Native C++ 层通常无法直接读取 Assets，需要拷贝出实体文件。
     *
     * @param context   上下文
     * @param assetName Assets 中的文件名 (例如 "lama_fp16.onnx")
     * @return 拷贝后的绝对路径 (例如 "/data/user/0/.../files/lama_fp16.onnx")，失败返回 null
     */
    public static String copyAssetToFilesDir(Context context, String assetName) {
        // 1. 获取目标路径: /data/data/com.package/files/assetName
        File outFile = new File(context.getFilesDir(), assetName);

        // 2. 检查文件是否已存在
        // 优化：如果文件已经存在，且大小 > 0，通常认为不需要再次拷贝
        // (生产环境中，如果更新了模型，可能需要结合版本号或 MD5 校验来强制覆盖)
        if (outFile.exists() && outFile.length() > 0) {
            Log.d(TAG, "文件已存在，跳过拷贝: " + outFile.getAbsolutePath());
            return outFile.getAbsolutePath();
        }

//        File parentDir = outFile.getParentFile();
//        if (parentDir != null && !parentDir.exists()) {
//            boolean created = parentDir.mkdirs();
//            Log.d(TAG, "父目录不存在，强制创建: " + parentDir.getAbsolutePath() + " 结果: " + created);
//        }

        Log.d(TAG, "开始写入文件: " + outFile.getAbsolutePath());

        Log.d(TAG, "开始从 Assets 拷贝文件: " + assetName);

        // 3. 执行流拷贝
        try (InputStream is = context.getAssets().open(assetName);
             OutputStream os = new FileOutputStream(outFile)) {

            byte[] buffer = new byte[4096]; // 4KB 缓冲区
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
            os.flush();

            Log.i(TAG, "文件拷贝成功: " + outFile.getAbsolutePath());
            return outFile.getAbsolutePath();

        } catch (IOException e) {
            Log.e(TAG, "文件拷贝失败: " + assetName, e);
            // 如果拷贝失败，尝试删除可能损坏的空文件
            if (outFile.exists()) {
                boolean delete = outFile.delete();
                Log.d(TAG, "删除损坏文件 " + outFile.getAbsolutePath() + ": " + delete);
            }
            return null;
        }
    }
}
