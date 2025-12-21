package com.example.panoramapro.ui.effects;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.example.panoramapro.R;

public class EffectsFragment extends Fragment {
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // 确保你有一个简单的 layout 文件：R.layout.fragment_effects
        return inflater.inflate(R.layout.fragment_effects, container, false);
    }
}