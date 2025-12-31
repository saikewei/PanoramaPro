package com.example.panoramapro.core;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.panoramapro.ui.settings.SettingsFragment;
import com.example.panoramapro.utils.FileUtils;

public class ImageProcessorFactory {
    final SharedPreferences prefs;
    final Context appContext;

    public ImageProcessorFactory(Context context) {
        appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);
    }

    public IStitcher getStitcher() {
        if (prefs.getString(SettingsFragment.KEY_STITCH_ALGO, "APAP").equals("APAP")) {
            return new APAPStitcher();
        } else {
            return new SIFTStitcher();
        }
    }

    public IImageCompleter getCompleter() throws Exception {
        String compAlgo = prefs.getString(SettingsFragment.KEY_COMPLETE_ALGO, "LAMA");
        if (compAlgo.equals("LAMA")) {
            String modelPath = FileUtils.copyAssetToFilesDir(appContext, "lama_fp32.onnx");
            if (modelPath == null) {
                throw new Exception("模型文件拷贝失败");
            }
            return new LaMaCompleter(modelPath);
        } else if (compAlgo.equals("OPENCV")) {
            return new OpencvCompleter();
        } else {
            return new NoOpCompleter();
        }
    }
}
