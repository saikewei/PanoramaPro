package com.example.panoramapro.ui.capture;

import android.graphics.Bitmap;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

public class StitchingViewModel extends ViewModel {
    private MutableLiveData<ArrayList<Bitmap>> captures = new MutableLiveData<>(new ArrayList<>());
    private MutableLiveData<Boolean> resetRequested = new MutableLiveData<>(false);

    public void setCaptures(ArrayList<Bitmap> newCaptures) {
        captures.setValue(newCaptures);
    }

    public ArrayList<Bitmap> getCaptures() {
        return captures.getValue();
    }

    public void clearCaptures() {
        captures.setValue(new ArrayList<>());
    }

    // 通知CaptureFragment需要重置
    public void notifyResetNeeded() {
        resetRequested.setValue(true);
    }

    // 获取重置请求状态
    public MutableLiveData<Boolean> getResetRequested() {
        return resetRequested;
    }

    // 重置请求标记（在CaptureFragment处理完后调用）
    public void resetRequestHandled() {
        resetRequested.setValue(false);
    }

    // 在StitchingViewModel类中添加
    private MutableLiveData<List<String>> tempImagePaths = new MutableLiveData<>(new ArrayList<>());

    public MutableLiveData<List<String>> getTempImagePaths() {
        return tempImagePaths;
    }

    public void setTempImagePaths(List<String> paths) {
        tempImagePaths.setValue(paths);
    }

}