package com.example.panoramapro.ui.capture;

import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.viewpager2.widget.ViewPager2;

import com.example.panoramapro.R;

import java.util.ArrayList;

public class PreviewFragment extends Fragment {
    private static final String TAG = "PreviewFragment";

    private ViewPager2 viewPager;
    private TextView tvPhotoCount;
    private Button btnNext;
    private Button btnReset;

    private StitchingViewModel viewModel;
    private ArrayList<Bitmap> captures;
    private PhotoPagerAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_preview, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 初始化UI组件
        initUI(view);

        // 初始化ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(StitchingViewModel.class);

        // 获取照片数据
        captures = viewModel.getCaptures();
        if (captures == null) {
            captures = new ArrayList<>();
        }

        // 设置适配器
        adapter = new PhotoPagerAdapter(captures);
        viewPager.setAdapter(adapter);

        // 更新UI
        updateUI();
    }

    private void initUI(View view) {
        // 初始化ViewPager2
        viewPager = view.findViewById(R.id.view_pager);

        // 初始化TextView显示照片数量
        tvPhotoCount = view.findViewById(R.id.tv_photo_count);

        // 初始化Next按钮（暂时留空）
        btnNext = view.findViewById(R.id.btn_next);
        btnNext.setOnClickListener(v -> {
            // TODO: 实现下一步功能
            Toast.makeText(requireContext(), "Next button clicked - Function to be implemented", Toast.LENGTH_SHORT).show();
        });

        // 初始化Reset按钮
        btnReset = view.findViewById(R.id.btn_reset);
        btnReset.setOnClickListener(v -> showResetConfirmation());

        // 设置ViewPager页面变化监听器
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updatePhotoCounter(position);
            }
        });
    }

    private void updateUI() {
        if (captures == null || captures.isEmpty()) {
            tvPhotoCount.setText("无照片");
            btnNext.setEnabled(false);
            btnReset.setEnabled(false);
        } else {
            updatePhotoCounter(0);
            btnNext.setEnabled(true);
            btnReset.setEnabled(true);
        }
    }

    private void updatePhotoCounter(int currentPosition) {
        if (captures != null && !captures.isEmpty()) {
            tvPhotoCount.setText(String.format("Photo %d of %d", currentPosition + 1, captures.size()));
        }
    }

    private void showResetConfirmation() {
        if (captures == null || captures.isEmpty()) {
            Toast.makeText(requireContext(), "没有要清除的照片", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Clear All Photos")
                .setMessage("你确定要清除所有的 " + captures.size() + " 张照片？你将退回到拍照界面")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 清空ViewModel中的数据
                        viewModel.clearCaptures();

                        // 重要：同时清空CaptureFragment中的captures列表
                        // 我们通过ViewModel来通知CaptureFragment
                        viewModel.notifyResetNeeded();

                        // 显示确认消息
                        Toast.makeText(requireContext(),
                                "所有照片已清除",
                                Toast.LENGTH_SHORT).show();

                        // 返回到拍照界面
                        try {
                            NavController navController = Navigation.findNavController(requireView());
                            navController.popBackStack();
                        } catch (Exception e) {
                            Log.e(TAG, "Navigation failed: " + e.getMessage());
                            requireActivity().onBackPressed();
                        }

                        dialog.dismiss();
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (viewPager != null) {
            viewPager.unregisterOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {});
        }
    }
}