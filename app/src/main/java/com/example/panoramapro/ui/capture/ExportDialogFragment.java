package com.example.panoramapro.ui.capture;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.panoramapro.R;
import com.google.android.material.button.MaterialButton;

public class ExportDialogFragment extends DialogFragment {

    private MaterialButton btnExport;
    private MaterialButton btnCancel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_simple, container, false);

        initUI(view);
        return view;
    }

    private void initUI(View view) {
        btnExport = view.findViewById(R.id.btn_export);
        btnCancel = view.findViewById(R.id.btn_cancel);

        // 导出按钮点击事件
        btnExport.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "导出功能暂未实现", Toast.LENGTH_SHORT).show();
            dismiss();
        });

        // 取消按钮
        btnCancel.setOnClickListener(v -> dismiss());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.setTitle("导出设置");
        return dialog;
    }
}