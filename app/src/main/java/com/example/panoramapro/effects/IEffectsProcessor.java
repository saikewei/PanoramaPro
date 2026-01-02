//package com.example.panoramapro.core;
//
//import android.graphics.Bitmap;
//import org.opencv.android.Utils;
//import org.opencv.core.CvType;
//import org.opencv.core.Mat;
//import org.opencv.core.Size;
//import org.opencv.imgproc.Imgproc;
//
//public class TinyPlanetProcessor implements IImageCompleter {
//
//    private float scale = 1.0f; // 缩放比例，建议范围 0.5 - 2.0
//    private int outputSize = 1024; // 输出图片的分辨率
//
//    /**
//     * 设置缩放比例（供 UI 调节）
//     */
//    public void setScale(float scale) {
//        this.scale = Math.max(0.1f, scale);
//    }
//
//    /**
//     * 设置输出图像大小
//     */
//    public void setOutputSize(int size) {
//        this.outputSize = size;
//    }
//
//    @Override
//    public Bitmap complete(Bitmap roughPanorama) {
//        if (roughPanorama == null) return null;
//
//        // 1. 将 Bitmap 转换为 OpenCV 的 Mat
//        Mat src = new Mat();
//        Utils.bitmapToMat(roughPanorama, src);
//
//        // 2. 创建映射表 (Maps)
//        // mapX 和 mapY 存储了输出图中每个像素点对应原图的哪个位置
//        Mat mapX = new Mat(outputSize, outputSize, CvType.CV_32FC1);
//        Mat mapY = new Mat(outputSize, outputSize, CvType.CV_32FC1);
//
//        int srcWidth = src.cols();
//        int srcHeight = src.rows();
//        float centerX = outputSize / 2f;
//        float centerY = outputSize / 2f;
//
//        // 最大半径定义了全景图高度映射的范围
//        float maxRadius = (outputSize / 2f) * scale;
//
//        // 3. 生成坐标映射逻辑
//        float[] xData = new float[outputSize * outputSize];
//        float[] yData = new float[outputSize * outputSize];
//
//        for (int row = 0; row < outputSize; row++) {
//            for (int col = 0; col < outputSize; col++) {
//                float dx = col - centerX;
//                float dy = row - centerY;
//
//                double distance = Math.sqrt(dx * dx + dy * dy);
//                double angle = Math.atan2(dy, dx); // [-PI, PI]
//
//                // 将极坐标映射回经纬度坐标（全景图坐标）
//                // 角度映射到 X (经度): [-PI, PI] -> [0, srcWidth]
//                float srcX = (float) (((angle + Math.PI) / (2.0 * Math.PI)) * srcWidth);
//
//                // 距离映射到 Y (纬度): [0, maxRadius] -> [srcHeight, 0] (地球在圆心)
//                // 这里反向映射实现“小行星”效果，srcHeight 是地面，0 是天空
//                float srcY = (float) ((1.0 - (distance / maxRadius)) * srcHeight);
//
//                // 缝隙平滑处理：利用 OpenCV 的边界模式，这里只需记录原始映射
//                xData[row * outputSize + col] = srcX;
//                yData[row * outputSize + col] = srcY;
//            }
//        }
//
//        mapX.put(0, 0, xData);
//        mapY.put(0, 0, yData);
//
//        // 4. 执行重映射 (Remap)
//        Mat resultMat = new Mat();
//        // BORDER_WRAP 是解决缝隙的关键：它会自动将坐标超出 360 度的像素绕回到另一侧
//        // INTER_LINEAR 提供双线性插值，保证缩放后的平滑抗锯齿
//        Imgproc.remap(src, resultMat, mapX, mapY, Imgproc.INTER_LINEAR, Imgproc.BORDER_WRAP);
//
//        // 5. 转回 Bitmap
//        Bitmap outputBitmap = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888);
//        Utils.matToBitmap(resultMat, outputBitmap);
//
//        // 释放内存
//        src.release();
//        mapX.release();
//        mapY.release();
//        resultMat.release();
//
//        return outputBitmap;
//    }
//
//    @Override
//    public void release() {
//        // 如果有缓存的 Mat，在这里释放
//    }
//}
