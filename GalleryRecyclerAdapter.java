package com.bitoneko.gallery;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import java.util.ArrayList;

public class GalleryRecyclerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final Context ctx;
    private final ArrayList<GridItem> items;
    private final int tabIndex;
    private final String pkg;
    private final int thumbSize;

    public static boolean isSelectMode = false;
    public static ArrayList<String> selectedPaths = new ArrayList<>();
    private static SelectionListener selectionListener;

    public interface SelectionListener {
        void onSelectionChanged(int count);
    }

    public static void setSelectionListener(SelectionListener listener) {
        selectionListener = listener;
    }

    public GalleryRecyclerAdapter(Context ctx, ArrayList<GridItem> items, int tabIndex) {
        this.ctx = ctx;
        this.items = items;
        this.tabIndex = tabIndex;
        this.pkg = ctx.getPackageName();
        this.thumbSize = ctx.getResources().getDisplayMetrics().widthPixels / 4;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).getItemType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == GridItem.TYPE_HEADER) {
            TextView tv = new TextView(ctx);
            tv.setLayoutParams(new ViewGroup.LayoutParams(-1, -2));
            tv.setPadding(42, 36, 16, 12);
            tv.setTextSize(15);
            tv.setTextColor(0xFF02DAC5);
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
            return new HeaderViewHolder(tv);
        } else {
            int layoutId = ctx.getResources().getIdentifier("gallery_item", "layout", pkg);
            View v = LayoutInflater.from(ctx).inflate(layoutId, parent, false);
            
            int currentOrientation = ctx.getResources().getConfiguration().orientation;
            int currentColumns = (currentOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) ? 8 : 4;
            int dynamicSize = ctx.getResources().getDisplayMetrics().widthPixels / currentColumns;
            
            v.setLayoutParams(new ViewGroup.LayoutParams(dynamicSize, dynamicSize));
            
            try {
                int[] attrs = new int[]{android.R.attr.selectableItemBackground};
                android.content.res.TypedArray ta = ctx.obtainStyledAttributes(attrs);
                android.graphics.drawable.Drawable ripple = ta.getDrawable(0);
                ta.recycle();
                if (ripple != null && android.os.Build.VERSION.SDK_INT >= 23) { v.setForeground(ripple); }
            } catch(Exception e){}
            
            return new MediaViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        GridItem item = items.get(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).tvDate.setText(item.getDateText());
        } else if (holder instanceof MediaViewHolder) {
            MediaViewHolder mHolder = (MediaViewHolder) holder;
            final MediaItem media = item.getMediaItem();
            
            int iconId = ctx.getResources().getIdentifier("img_video_icon", "id", pkg);
            if (iconId != 0 && mHolder.itemView.findViewById(iconId) != null) {
                mHolder.itemView.findViewById(iconId).setVisibility(media.isVideo() ? View.VISIBLE : View.GONE);
            }

            int txtId = ctx.getResources().getIdentifier("txt_name", "id", pkg);
            if (txtId != 0 && mHolder.itemView.findViewById(txtId) != null) {
                mHolder.itemView.findViewById(txtId).setVisibility(View.GONE);
            }

            int thumbId = ctx.getResources().getIdentifier("img_thumbnail", "id", pkg);
            if (thumbId != 0) {
                ImageView iv = (ImageView) mHolder.itemView.findViewById(thumbId);
                if (iv != null) {
                    Glide.with(ctx.getApplicationContext())
                        .load(media.getPath())
                        .asBitmap()
                        .override(thumbSize, thumbSize)
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.RESULT)
                        .into(iv);
                }
            }

            int indicatorId = ctx.getResources().getIdentifier("img_select_indicator", "id", pkg);
            if (indicatorId != 0) {
                View indicator = mHolder.itemView.findViewById(indicatorId);
                if (indicator != null) {
                    indicator.setVisibility((isSelectMode && selectedPaths.contains(media.getPath())) ? View.VISIBLE : View.GONE);
                }
            }

            mHolder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (isSelectMode) {
                        toggleSelection(media.getPath());
                    } else {
                        android.content.Intent intent = new android.content.Intent(ctx, MediaViewActivity.class);
                        intent.putExtra("media_path", media.getPath());
                        if (ctx instanceof AlbumViewActivity) {
                            intent.putExtra("album_path", ((AlbumViewActivity) ctx).getIntent().getStringExtra("album_path"));
                            intent.putExtra("tab_index", 4); 
                        } else {
                            intent.putExtra("tab_index", tabIndex);
                        }
                        ctx.startActivity(intent);
                    }
                }
            });

            mHolder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (!isSelectMode) {
                        isSelectMode = true;
                        toggleSelection(media.getPath());
                        return true;
                    }
                    return false;
                }
            });
        }
    }

    private void toggleSelection(String path) {
        if (selectedPaths.contains(path)) {
            selectedPaths.remove(path);
        } else {
            selectedPaths.add(path);
        }
        if (selectedPaths.isEmpty()) {
            isSelectMode = false;
        }
        notifyDataSetChanged();
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(selectedPaths.size());
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate;
        HeaderViewHolder(View v) { super(v); tvDate = (TextView) v; }
    }

    static class MediaViewHolder extends RecyclerView.ViewHolder {
        MediaViewHolder(View v) { super(v); }
    }
}