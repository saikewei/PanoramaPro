package com.example.panoramapro.ui.gallery;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;


import com.example.panoramapro.core.IImageCompleter;
import com.example.panoramapro.core.IStitcher;
import com.example.panoramapro.core.ImageProcessorFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StitchingViewModel extends AndroidViewModel {

    private static final String TAG = "StitchingVM";

    // 线程池，用于后台执行耗时算法
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    // LiveData 用于通知 UI 更新
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<Bitmap> resultBitmap = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public StitchingViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<Bitmap> getResultBitmap() { return resultBitmap; }
    public LiveData<String> getErrorMessage() { return errorMessage; }

    /**
     * 开始拼接任务
     * @param imageUris 用户选中的图片 Uri 列表
     */
    public void startStitching(List<Uri> imageUris) {
        if (imageUris == null || imageUris.size() < 2) {
            errorMessage.setValue("请至少选择两张图片进行拼接");
            return;
        }

        isLoading.setValue(true);

        // 在子线程执行
        executorService.execute(() -> {
            try {
                Context context = getApplication().getApplicationContext();
                List<Bitmap> inputBitmaps = new ArrayList<>();

                // 创建工厂获取拼接器和补全器
                ImageProcessorFactory factory = new ImageProcessorFactory(context);
                IStitcher stitcher = factory.getStitcher();
                IImageCompleter completer = factory.getCompleter();

                // 1. 加载图片
                for (Uri uri : imageUris) {
                    try (InputStream is = context.getContentResolver().openInputStream(uri)) {
                        Bitmap bmp = BitmapFactory.decodeStream(is);
                        if (bmp != null) inputBitmaps.add(bmp);
                    }
                }

                if (inputBitmaps.size() < 2) {
                    throw new Exception("图片加载失败，无法获取 Bitmap");
                }

                // 2. 执行拼接 (Java -> C++)
                Bitmap stitched = stitcher.stitch(inputBitmaps, true);

                if (stitched == null) {
                    throw new Exception("拼接失败，可能是特征点不足");
                }

                Log.i(TAG, "拼接完成，开始准备模型...");

                // 3. 执行 AI 补全 (Java -> C++)
                Bitmap finalResult = completer.complete(stitched);
                completer.release(); // 释放 C++ 资源

                if (finalResult == null) {
                    throw new Exception("AI 补全返回空结果");
                }

                // 4. 成功，更新 UI (postValue 会自动切换到主线程)
                resultBitmap.postValue(finalResult);

            } catch (Exception e) {
                Log.e(TAG, "处理过程中发生错误", e);
                errorMessage.postValue("处理失败: " + e.getMessage());
            } finally {
                isLoading.postValue(false);
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown(); // 销毁 ViewModel 时关闭线程池
    }
}