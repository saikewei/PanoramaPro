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
import com.example.panoramapro.utils.BitmapSaver;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StitchingViewModel extends AndroidViewModel {

    private static final String TAG = "StitchingVM";

    // 线程池，用于后台执行耗时算法
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    // LiveData 用于通知 UI 更新
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<List<File>> localImages = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public StitchingViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<List<File>> getLocalImages() { return localImages; }
    public LiveData<String> getErrorMessage() { return errorMessage; }

    private final MutableLiveData<String> stitchSuccessEvent = new MutableLiveData<>();
    public LiveData<String> getStitchSuccessEvent() { return stitchSuccessEvent; }

    /**
     * 加载本地存储的图片
     */
    public void loadLocalImages() {
        executorService.execute(() -> {
            File dir = BitmapSaver.getPanoramaDir(getApplication());
            File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".jpg") || name.toLowerCase().endsWith(".png"));

            List<File> fileList = new ArrayList<>();
            if (files != null) {
                fileList.addAll(Arrays.asList(files));
                // 按最后修改时间倒序排列（最新的在前面）
                Collections.sort(fileList, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
            }
            localImages.postValue(fileList);
        });
    }

    /**
     * 删除选中的图片
     */
    public void deleteImages(List<File> filesToDelete) {
        executorService.execute(() -> {
            for (File file : filesToDelete) {
                if (file.exists()) {
                    file.delete();
                }
            }
            loadLocalImages(); // 删除后刷新列表
        });
    }

    /**
     * 开始拼接任务
     * @param imageUris 用户选中的图片 Uri 列表
     */
    /**
     * 开始拼接任务 (支持 Content Uri 或 File Uri)
     */
    public void startStitching(List<Uri> imageUris) {
        if (imageUris == null || imageUris.size() < 2) {
            errorMessage.setValue("请至少选择两张图片进行拼接");
            return;
        }

        isLoading.setValue(true);

        executorService.execute(() -> {
            try {
                Context context = getApplication().getApplicationContext();
                List<Bitmap> inputBitmaps = new ArrayList<>();
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

                if (inputBitmaps.size() < 2) throw new Exception("图片加载失败");

                // 2. 拼接
                Bitmap stitched = stitcher.stitch(inputBitmaps, true);
                if (stitched == null) throw new Exception("拼接失败，特征点不足");

                // 3. AI 补全
                Bitmap finalResult = completer.complete(stitched);
                completer.release();
                if (finalResult == null) throw new Exception("AI 补全失败");

                // 4. 【修改点】保存结果到本地相册
                String savedPath = BitmapSaver.saveBitmapToPrivateStorage(context, finalResult);

                if (savedPath != null) {
                    // 通知 UI 成功
                    stitchSuccessEvent.postValue(savedPath);
                    // 刷新列表以显示新图片
                    loadLocalImages();
                } else {
                    throw new Exception("保存图片失败");
                }

            } catch (Exception e) {
                Log.e(TAG, "Process error", e);
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