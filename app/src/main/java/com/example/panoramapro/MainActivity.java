package com.example.panoramapro;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import com.example.panoramapro.databinding.ActivityMainBinding;

import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 获取 NavHostFragment
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);

        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();

            // 配置 NavigationUI
            NavigationUI.setupWithNavController(binding.navView, navController);

            // 监听导航变化，确保预览界面时"拍摄"被选中
            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                // 如果当前在预览界面，确保"拍摄"被选中
                if (destination.getId() == R.id.previewFragment) {
                    binding.navView.getMenu().findItem(R.id.navigation_capture).setChecked(true);
                }
            });
        }
    }
}