package com.bitoneko.gallery;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toolbar;
import android.view.View;
import android.view.Gravity;
import android.widget.ImageView;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.ArrayList;

public class AlbumViewActivity extends FragmentActivity {

    private ArrayList<GridItem> albumFiles = new ArrayList<>();
    private String albumPath = "";
    private RecyclerView rv;
    private GalleryRecyclerAdapter adapter;
    private Toolbar toolbar;
    private String originalTitle = "";
    private LinearLayout actionContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            albumPath = getIntent().getStringExtra("album_path");
            if (albumPath == null) {
                finish();
                return;
            }

            getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    if (com.bitoneko.gallery.GalleryRecyclerAdapter.isSelectMode) {
                        com.bitoneko.gallery.GalleryRecyclerAdapter.isSelectMode = false;
                        com.bitoneko.gallery.GalleryRecyclerAdapter.selectedPaths.clear();
                        updateSelectionActionBar(0);
                        if (adapter != null) {
                            adapter.notifyDataSetChanged();
                        }
                    } else {
                        setEnabled(false);
                        AlbumViewActivity.this.onBackPressed();
                    }
                }
            });

            File albumFolder = new File(albumPath);
            originalTitle = albumFolder.getName();

            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));
            root.setBackgroundColor(0xFF000000);

            toolbar = new Toolbar(this);
            toolbar.setBackgroundColor(0xFF1E1E1E);
            toolbar.setTitle(originalTitle);
            toolbar.setTitleTextColor(0xFFFFFFFF);
            toolbar.setNavigationIcon(android.R.drawable.ic_menu_revert);
            toolbar.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
            
            actionContainer = new LinearLayout(this);
            actionContainer.setOrientation(LinearLayout.HORIZONTAL);
            actionContainer.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
            Toolbar.LayoutParams alp = new Toolbar.LayoutParams(-2, -1);
            alp.gravity = Gravity.END;
            actionContainer.setLayoutParams(alp);
            toolbar.addView(actionContainer);
            
            root.addView(toolbar);

            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override 
                public void onClick(View v) { 
                    getOnBackPressedDispatcher().onBackPressed();
                }
            });

            rv = new RecyclerView(this);
            rv.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));
            rv.setHasFixedSize(true);
            root.addView(rv);

            buildRecyclerData();

            com.bitoneko.gallery.GalleryRecyclerAdapter.setSelectionListener(new com.bitoneko.gallery.GalleryRecyclerAdapter.SelectionListener() {
                @Override
                public void onSelectionChanged(int count) {
                    updateSelectionActionBar(count);
                }
            });

            setContentView(root);
        } catch (Exception e) {}
    }

    private android.net.Uri getItemContentUri(String filePath, boolean isVideo) {
        return FileMethods.getItemContentUri(this, filePath, isVideo);
    }

    private void finishDeleteCleanup() {
        com.bitoneko.gallery.GalleryRecyclerAdapter.isSelectMode = false;
        com.bitoneko.gallery.GalleryRecyclerAdapter.selectedPaths.clear();
        updateSelectionActionBar(0);
        com.bitoneko.gallery.MediaScanner.reloadMediaData(AlbumViewActivity.this);
        buildRecyclerData();
    }

    public void updateSelectionActionBar(int count) {
        if (com.bitoneko.gallery.GalleryRecyclerAdapter.isSelectMode && count > 0) {
            toolbar.setTitle("Selected: " + count);
            actionContainer.removeAllViews();
            
            int[] attrs = new int[]{android.R.attr.selectableItemBackground};
            android.content.res.TypedArray ta = obtainStyledAttributes(attrs);
            android.graphics.drawable.Drawable ripple = ta.getDrawable(0);
            ta.recycle();

            int selectAllResId = getResources().getIdentifier("ic_select_all", "drawable", getPackageName());
            if (selectAllResId != 0) {
                ImageView ivSelectAll = new ImageView(this);
                ivSelectAll.setImageResource(selectAllResId);
                ivSelectAll.setPadding(12, 12, 12, 12);
                ivSelectAll.setScaleType(ImageView.ScaleType.FIT_CENTER);
                
                int containerSize = (int) (48 * getResources().getDisplayMetrics().density);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(containerSize, containerSize);
                lp.gravity = Gravity.CENTER_VERTICAL;
                ivSelectAll.setLayoutParams(lp);
                
                if (ripple != null) ivSelectAll.setBackground(ripple.getConstantState().newDrawable());
                actionContainer.addView(ivSelectAll);
                ivSelectAll.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            com.bitoneko.gallery.GalleryRecyclerAdapter.selectedPaths.clear();
                            for (GridItem gi : albumFiles) {
                                if (gi.getItemType() == GridItem.TYPE_MEDIA && gi.getMediaItem() != null) {
                                    com.bitoneko.gallery.GalleryRecyclerAdapter.selectedPaths.add(gi.getMediaItem().getPath());
                                }
                            }
                            com.bitoneko.gallery.GalleryRecyclerAdapter.isSelectMode = true;
                            updateSelectionActionBar(com.bitoneko.gallery.GalleryRecyclerAdapter.selectedPaths.size());
                            if (adapter != null) {
                                adapter.notifyDataSetChanged();
                            }
                        } catch (Exception e) {}
                    }
                });
            }

            ImageView ivShare = new ImageView(this);
            ivShare.setImageResource(android.R.drawable.ic_menu_share);
            ivShare.setPadding(24, 16, 24, 16);
            if (ripple != null) ivShare.setBackground(ripple.getConstantState().newDrawable());
            actionContainer.addView(ivShare);
            ivShare.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        ArrayList<android.net.Uri> uris = new ArrayList<>();
                        String mimeType = "*/*";
                        
                        if (!com.bitoneko.gallery.GalleryRecyclerAdapter.selectedPaths.isEmpty()) {
                            String firstPath = com.bitoneko.gallery.GalleryRecyclerAdapter.selectedPaths.get(0).toLowerCase();
                            mimeType = (firstPath.endsWith(".mp4") || firstPath.endsWith(".mkv") || firstPath.endsWith(".webm")) ? "video/*" : "image/*";
                        }

                        for (String p : com.bitoneko.gallery.GalleryRecyclerAdapter.selectedPaths) {
                            boolean isVid = p.toLowerCase().endsWith(".mp4") || p.toLowerCase().endsWith(".mkv") || p.toLowerCase().endsWith(".webm");
                            android.net.Uri uri = getItemContentUri(p, isVid);
                            if (uri != null) {
                                uris.add(uri);
                            }
                        }

                        if (!uris.isEmpty()) {
                            Intent si = new Intent();
                            if (uris.size() == 1) {
                                si.setAction(Intent.ACTION_SEND);
                                si.putExtra(Intent.EXTRA_STREAM, uris.get(0));
                            } else {
                                si.setAction(Intent.ACTION_SEND_MULTIPLE);
                                si.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
                            }
                            
                            si.setType(mimeType);
                            
                            si.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            si.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                            Intent chooser = Intent.createChooser(si, "Share via");
                            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            chooser.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                            startActivity(chooser);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

            ImageView ivAbout = new ImageView(this);
            ivAbout.setImageResource(android.R.drawable.ic_dialog_info);
            ivAbout.setPadding(24, 16, 24, 16);
            if (ripple != null) ivAbout.setBackground(ripple.getConstantState().newDrawable());
            actionContainer.addView(ivAbout);
            ivAbout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        long totalSize = 0;
                        for (String p : com.bitoneko.gallery.GalleryRecyclerAdapter.selectedPaths) {
                            totalSize += new File(p).length();
                        }
                        double mb = totalSize / (1024.0 * 1024.0);
                        String sizeResultStr;
                        if (mb >= 1024.0) {
                            double gb = mb / 1024.0;
                            sizeResultStr = String.format(java.util.Locale.US, "%.2f GB", gb);
                        } else {
                            sizeResultStr = String.format(java.util.Locale.US, "%.2f MB", mb);
                        }
                        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(AlbumViewActivity.this);
                        b.setTitle("Batch Details");
                        b.setMessage("Selected files: " + com.bitoneko.gallery.GalleryRecyclerAdapter.selectedPaths.size() + "\n\nTotal Size: " + sizeResultStr);
                        b.setPositiveButton("OK", null);
                        b.show();
                    } catch (Exception e) {}
                }
            });

            ImageView ivDel = new ImageView(this);
            ivDel.setImageResource(android.R.drawable.ic_menu_delete);
            ivDel.setPadding(24, 16, 24, 16);
            if (ripple != null) ivDel.setBackground(ripple.getConstantState().newDrawable());
            actionContainer.addView(ivDel);
            ivDel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!com.bitoneko.gallery.GalleryRecyclerAdapter.selectedPaths.isEmpty()) {
                        FileMethods.showDeleteConfirmationDialog(
                            AlbumViewActivity.this, 
                            com.bitoneko.gallery.GalleryRecyclerAdapter.selectedPaths, 
                            200
                        );
                    }
                }
            });
        } else {
            toolbar.setTitle(originalTitle);
            actionContainer.removeAllViews();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 200 && resultCode == RESULT_OK) {
            finishDeleteCleanup();
        }
    }

    private void buildRecyclerData() {
        try {
            albumFiles.clear();
            for (MediaItem mi : MediaScanner.allMediaList) {
                File f = new File(mi.getPath());
                if (f.exists() && f.getParentFile() != null && f.getParentFile().getAbsolutePath().equals(albumPath)) {
                    albumFiles.add(new GridItem(mi));
                }
            }
            if (rv.getAdapter() == null) {
                adapter = new GalleryRecyclerAdapter(this, albumFiles, 4);
                int currentOrientation = getResources().getConfiguration().orientation;
                final int columns = (currentOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) ? 8 : 4;
                GridLayoutManager glm = new GridLayoutManager(this, columns);
                glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                    @Override
                    public int getSpanSize(int pos) {
                        return adapter.getItemViewType(pos) == GridItem.TYPE_HEADER ? columns : 1;
                    }
                });
                rv.setLayoutManager(glm);
                rv.setAdapter(adapter);
            } else {
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            }
        } catch (Exception e) {}
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        try {
            if (rv != null && adapter != null) {
                final int columns = newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE ? 8 : 4;
                
                int currentPosition = 0;
                int currentOffset = 0;
                RecyclerView.LayoutManager currentLm = rv.getLayoutManager();
                if (currentLm instanceof GridLayoutManager) {
                    currentPosition = ((GridLayoutManager) currentLm).findFirstVisibleItemPosition();
                    View firstVisibleView = currentLm.findViewByPosition(currentPosition);
                    if (firstVisibleView != null) {
                        currentOffset = firstVisibleView.getTop();
                    }
                }
                
                rv.getRecycledViewPool().clear();
                final GridLayoutManager glm = new GridLayoutManager(this, columns);
                glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                    @Override
                    public int getSpanSize(int pos) {
                        return adapter.getItemViewType(pos) == GridItem.TYPE_HEADER ? columns : 1;
                    }
                });
                rv.setLayoutManager(glm);
                
                final int finalPosition = currentPosition;
                final int finalOffset = currentOffset;
                rv.post(new Runnable() {
                    @Override
                    public void run() {
                        adapter.notifyDataSetChanged();
                        if (finalPosition >= 0 && finalPosition < adapter.getItemCount()) {
                            glm.scrollToPositionWithOffset(finalPosition, finalOffset);
                        }
                    }
                });
            }
        } catch (Exception e) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            ArrayList<MediaItem> cleanAllList = new ArrayList<>();
            for (MediaItem mi : MediaScanner.allMediaList) {
                if (new File(mi.getPath()).exists()) {
                    cleanAllList.add(mi);
                }
            }
            MediaScanner.allMediaList = cleanAllList;
            updateSelectionActionBar(com.bitoneko.gallery.GalleryRecyclerAdapter.selectedPaths.size());
            
            if (adapter != null) {
                albumFiles.clear();
                for (MediaItem mi : MediaScanner.allMediaList) {
                    File f = new File(mi.getPath());
                    if (f.exists() && f.getParentFile() != null && f.getParentFile().getAbsolutePath().equals(albumPath)) {
                        albumFiles.add(new GridItem(mi));
                    }
                }
                adapter.notifyDataSetChanged();
            } else {
                buildRecyclerData();
            }
        } catch (Exception e) {}
    }
}
