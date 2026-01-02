package com.example.panoramapro.ui.gallery;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.panoramapro.R; // 确保你的项目中有一个 item layout，下面会说明

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GalleryAdapter extends RecyclerView.Adapter<GalleryAdapter.ViewHolder> {

    private List<File> imageFiles = new ArrayList<>();
    private final Set<File> selectedFiles = new HashSet<>();
    private boolean isSelectionMode = false;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(File file, int position);
        void onSelectionChanged(int selectedCount);
    }

    public GalleryAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setFiles(List<File> files) {
        this.imageFiles = files;
        notifyDataSetChanged();
    }

    public List<File> getSelectedFiles() {
        return new ArrayList<>(selectedFiles);
    }

    public void toggleSelectionMode(boolean enable) {
        isSelectionMode = enable;
        if (!enable) selectedFiles.clear();
        notifyDataSetChanged();
        listener.onSelectionChanged(selectedFiles.size());
    }

    private void toggleSelection(File file) {
        if (selectedFiles.contains(file)) {
            selectedFiles.remove(file);
        } else {
            selectedFiles.add(file);
        }
        if (selectedFiles.isEmpty()) {
            isSelectionMode = false;
        }
        notifyDataSetChanged();
        listener.onSelectionChanged(selectedFiles.size());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 这里我们需要一个新的 item_gallery_image.xml，后面会提供
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_gallery_image, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        File file = imageFiles.get(position);

        Glide.with(holder.itemView)
                .load(file)
                .centerCrop()
                .into(holder.ivThumbnail);

        // 处理选中状态的视觉效果
        if (isSelectionMode) {
            holder.ivCheck.setVisibility(View.VISIBLE);
            holder.ivCheck.setImageResource(selectedFiles.contains(file)
                    ? android.R.drawable.checkbox_on_background
                    : android.R.drawable.checkbox_off_background);
            holder.itemView.setAlpha(selectedFiles.contains(file) ? 0.7f : 1.0f);
        } else {
            holder.ivCheck.setVisibility(View.GONE);
            holder.itemView.setAlpha(1.0f);
        }

        holder.itemView.setOnClickListener(v -> {
            if (isSelectionMode) {
                toggleSelection(file);
            } else {
                listener.onItemClick(file, position);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (!isSelectionMode) {
                toggleSelectionMode(true);
                toggleSelection(file);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return imageFiles.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivThumbnail;
        ImageView ivCheck;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumbnail = itemView.findViewById(R.id.iv_thumbnail);
            ivCheck = itemView.findViewById(R.id.iv_check);
        }
    }
}