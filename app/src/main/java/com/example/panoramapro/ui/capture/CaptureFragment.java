package com.example.panoramapro.ui.capture;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.example.panoramapro.R;

public class CaptureFragment extends Fragment {
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // 确保你有一个简单的 layout 文件：R.layout.fragment_capture
        return inflater.inflate(R.layout.fragment_capture, container, false);
    }
}