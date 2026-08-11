package com.bitoneko.gallery;

import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toolbar;
import android.widget.VideoView;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.github.chrisbanes.photoview.OnViewTapListener;
import com.github.chrisbanes.photoview.PhotoView;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;

public class MediaViewActivity extends FragmentActivity {

    public static class SafeViewPager extends ViewPager {
        public SafeViewPager(@NonNull Context context) {
            super(context);
        }

        public SafeViewPager(@NonNull Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            try {
                return super.onInterceptTouchEvent(ev);
            } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
                return false;
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            try {
                return super.onTouchEvent(ev);
            } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
                return false;
            }
        }
    }

    private boolean isControlsVisible = true;
    private Toolbar toolbar;
    private View controlsView;
    private View videoLayoutOverlay;
    private FrameLayout rootLayout;
    private SafeViewPager viewPager;
    private MediaPagerAdapter pagerAdapter;
    
    private VideoView videoView;
    private SeekBar seekBar;
    private TextView txtCurrent, txtTotal;
    private ImageView playPauseBtn;
    
    private Handler progressHandler = new Handler(Looper.getMainLooper());
    private Runnable progressRunnable;
    private boolean isTracking = false;
    private int startPosition = 0;
    private int activeTab = 0;
    private String pkg = "";
    private ArrayList<MediaItem> activeList = new ArrayList<>();
    private int videoSavedPosition = -1;
    private boolean forceKeepPaused = false;
    private boolean isTrashMode = false;
    private boolean isAnimatingRemoval = false;

    private static final int REQ_DELETE_MEDIA = 200;
    private static final int REQ_RESTORE_MEDIA = 201;
    private static final int REQ_DELETE_PERM_MEDIA = 202;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Window w = getWindow();
        w.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        String startPath = getIntent().getStringExtra("media_path");
        activeTab = getIntent().getIntExtra("tab_index", 0);
        String albumPath = getIntent().getStringExtra("album_path");
        isTrashMode = getIntent().getBooleanExtra("is_trash", false);

        activeList = new ArrayList<>();
        if (isTrashMode) {
            activeList.addAll(TrashScanner.getTrashItems(this));
        } else if (albumPath != null && !albumPath.trim().isEmpty()) {
            for (MediaItem mi : MediaScanner.allMediaList) {
                File f = new File(mi.getPath());
                if (f.exists() && f.getParentFile() != null && f.getParentFile().getAbsolutePath().equals(albumPath)) {
                    activeList.add(mi);
                }
            }
        } else if (activeTab == 1) {
            activeList.addAll(MediaScanner.photosList);
        } else if (activeTab == 2) {
            activeList.addAll(MediaScanner.videosList);
        } else if (activeTab == 4) {
            String currentFolder = new File(startPath).getParent();
            if (currentFolder != null) {
                for (MediaItem mi : MediaScanner.allMediaList) {
                    File f = new File(mi.getPath());
                    if (f.exists() && f.getParentFile() != null && f.getParentFile().getAbsolutePath().equals(currentFolder)) {
                        activeList.add(mi);
                    }
                }
            }
        } else {
            activeList.addAll(MediaScanner.allMediaList);
        }

        startPosition = 0;
        for (int i = 0; i < activeList.size(); i++) {
            if (activeList.get(i).getPath().equals(startPath)) {
                startPosition = i;
                break;
            }
        }

        pkg = getPackageName();
        rootLayout = new FrameLayout(this);
        rootLayout.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        rootLayout.setBackgroundColor(0xFF000000);
        rootLayout.setClickable(true);
        rootLayout.setFocusable(true);

        viewPager = new SafeViewPager(this);
        viewPager.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        rootLayout.addView(viewPager);

        toolbar = new Toolbar(this);
        toolbar.setBackgroundColor(0x80000000);
        toolbar.setTitle("");
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_revert);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-1, -2);
        tl.setMargins(0, 36, 0, 0);
        toolbar.setLayoutParams(tl);
        rootLayout.addView(toolbar);

        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        try {
            int layoutRes = getResources().getIdentifier(isTrashMode ? "trash_view_controls" : "media_view_controls", "layout", pkg);
            if (layoutRes != 0) {
                controlsView = LayoutInflater.from(this).inflate(layoutRes, rootLayout, false);
                FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-1, -2);
                clp.gravity = Gravity.BOTTOM;
                clp.setMargins(0, 0, 0, 48);
                controlsView.setLayoutParams(clp);
                rootLayout.addView(controlsView);
                if (isTrashMode) {
                    setupTrashActionButtons(controlsView);
                } else {
                    setupActionButtons(controlsView);
                }
            }
        } catch(Exception e){}

        pagerAdapter = new MediaPagerAdapter();
        viewPager.setAdapter(pagerAdapter);
        viewPager.setCurrentItem(startPosition, false);
        
        viewPager.setPageTransformer(true, new ViewPager.PageTransformer() {
            @Override
            public void transformPage(View page, float position) {
                if (isAnimatingRemoval) return;
                if (position < -1) { page.setAlpha(0f); }
                else if (position <= 0) { page.setAlpha(1f); page.setTranslationX(0f); page.setScaleX(1f); page.setScaleY(1f); }
                else if (position <= 1) { page.setAlpha(1f - position); page.setTranslationX(-page.getWidth() * position); float sf = 0.75f + (1f - 0.75f) * (1f - Math.abs(position)); page.setScaleX(sf); page.setScaleY(sf); }
                else { page.setAlpha(0f); }
            }
        });

        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override public void onPageScrolled(int p, float po, int px) {}
            @Override public void onPageScrollStateChanged(int s) {}
            @Override public void onPageSelected(int p) { stopVideoPlaybackEngine(); resetPreviousViewsZoom(); }
        });

        setContentView(rootLayout);
    }

    private void resetPreviousViewsZoom() {
        for (int i = 0; i < viewPager.getChildCount(); i++) {
            View child = viewPager.getChildAt(i);
            if (child instanceof FrameLayout) {
                View img = ((FrameLayout) child).getChildAt(0);
                if (img instanceof PhotoView) {
                    ((PhotoView) img).setScale(1.0f, true);
                }
            }
        }
    }
    
    private android.net.Uri getItemContentUri(MediaItem item) {
        return FileMethods.getItemContentUri(this, item.getPath(), item.isVideo());
    }

    private void setupTrashActionButtons(View controls) {
        int restoreId = getResources().getIdentifier("btn_restore", "id", pkg);
        int deletePermId = getResources().getIdentifier("btn_delete_perm", "id", pkg);

        View btnRestore = controls.findViewById(restoreId);
        View btnDeletePerm = controls.findViewById(deletePermId);

        int[] attrs = new int[]{android.R.attr.selectableItemBackground};
        android.content.res.TypedArray ta = obtainStyledAttributes(attrs);
        android.graphics.drawable.Drawable ripple = ta.getDrawable(0);
        ta.recycle();

        if (btnRestore != null) {
            btnRestore.setClickable(true); btnRestore.setFocusable(true);
            if (ripple != null) btnRestore.setBackground(ripple.getConstantState().newDrawable());
            btnRestore.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        int pos = viewPager.getCurrentItem();
                        if (pos >= 0 && pos < activeList.size()) {
                            MediaItem item = activeList.get(pos);
                            java.util.ArrayList<String> pathsToRestore = new java.util.ArrayList<>();
                            pathsToRestore.add(item.getPath());
                            FileMethods.showRestoreConfirmationDialog(MediaViewActivity.this, pathsToRestore, REQ_RESTORE_MEDIA);
                        }
                    } catch (Exception e) {}
                }
            });
        }

        if (btnDeletePerm != null) {
            btnDeletePerm.setClickable(true); btnDeletePerm.setFocusable(true);
            if (ripple != null) btnDeletePerm.setBackground(ripple.getConstantState().newDrawable());
            btnDeletePerm.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        int pos = viewPager.getCurrentItem();
                        if (pos >= 0 && pos < activeList.size()) {
                            MediaItem item = activeList.get(pos);
                            java.util.ArrayList<String> pathsToDelete = new java.util.ArrayList<>();
                            pathsToDelete.add(item.getPath());
                            FileMethods.showPermanentDeleteConfirmationDialog(MediaViewActivity.this, pathsToDelete, REQ_DELETE_PERM_MEDIA);
                        }
                    } catch (Exception e) {}
                }
            });
        }
    }

    private void setupActionButtons(View controls) {
        int shareId = getResources().getIdentifier("btn_share", "id", pkg);
        int aboutId = getResources().getIdentifier("btn_about", "id", pkg);
        int editId = getResources().getIdentifier("btn_edit", "id", pkg);
        int deleteId = getResources().getIdentifier("btn_delete", "id", pkg);

        View btnShare = controls.findViewById(shareId);
        View btnAbout = controls.findViewById(aboutId);
        View btnEdit = controls.findViewById(editId);
        View btnDelete = controls.findViewById(deleteId);

        int[] attrs = new int[]{android.R.attr.selectableItemBackground};
        android.content.res.TypedArray ta = obtainStyledAttributes(attrs);
        android.graphics.drawable.Drawable ripple = ta.getDrawable(0);
        ta.recycle();

        if (btnShare != null) {
            btnShare.setClickable(true); btnShare.setFocusable(true);
            if (ripple != null) btnShare.setBackground(ripple.getConstantState().newDrawable());
            btnShare.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        MediaItem item = activeList.get(viewPager.getCurrentItem());
                        android.net.Uri uri = getItemContentUri(item);
                        Intent si = new Intent(Intent.ACTION_SEND);
                        si.setType(item.isVideo() ? "video/*" : "image/*");
                        si.putExtra(Intent.EXTRA_STREAM, uri);
                        si.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(si, "Share via"));
                    } catch (Exception e) {}
                }
            });
        }
        
        if (btnAbout != null) {
            btnAbout.setClickable(true); btnAbout.setFocusable(true);
            if (ripple != null) btnAbout.setBackground(ripple.getConstantState().newDrawable());
            btnAbout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        MediaItem item = activeList.get(viewPager.getCurrentItem());
                        File file = new File(item.getPath());
                        
                        if (file.exists()) {
                            double bytes = file.length();
                            double megabytes = bytes / (1024.0 * 1024.0);
                            String sizeStr;
                            if (megabytes >= 1024.0) {
                                double gigabytes = megabytes / 1024.0;
                                sizeStr = String.format(java.util.Locale.US, "%.2f GB", gigabytes);
                            } else {
                                sizeStr = String.format(java.util.Locale.US, "%.2f MB", megabytes);
                            }

                            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault());
                            String dateModifiedStr = sdf.format(new Date(file.lastModified()));
                            
                            String dateCreatedStr = dateModifiedStr; 
                            try {
                                if (android.os.Build.VERSION.SDK_INT >= 26) {
                                    java.nio.file.Path pathObj = java.nio.file.Paths.get(item.getPath());
                                    java.nio.file.attribute.BasicFileAttributes attr = java.nio.file.Files.readAttributes(pathObj, java.nio.file.attribute.BasicFileAttributes.class);
                                    dateCreatedStr = sdf.format(new Date(attr.creationTime().toMillis()));
                                }
                            } catch (Exception e1) {
                                if (item.getDate() > 0) {
                                    dateCreatedStr = sdf.format(new Date(item.getDate()));
                                }
                            }
                            
                            String extStr = "Unknown";
                            String mimeTypeStr = item.isVideo() ? "video/*" : "image/*";
                            String name = file.getName();
                            int dotIdx = name.lastIndexOf('.');
                            if (dotIdx > 0 && dotIdx < name.length() - 1) {
                                String ext = name.substring(dotIdx + 1).toLowerCase();
                                extStr = ext.toUpperCase();
                                String systemMime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                                if (systemMime != null) {
                                    mimeTypeStr = systemMime;
                                }
                            }
                            
                            String resolutionStr = "Unknown";
                            String durationStr = "";
                            try {
                                if (item.isVideo()) {
                                    android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
                                    try {
                                        retriever.setDataSource(item.getPath());
                                    } catch (Exception ex) {
                                        retriever.setDataSource(MediaViewActivity.this, getItemContentUri(item));
                                    }
                                    String width = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                                    String height = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                                    if (width != null && height != null) {
                                        resolutionStr = width + " x " + height;
                                    }
                                    
                                    String dur = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
                                    if (dur != null) {
                                        long ms = Long.parseLong(dur);
                                        long sec = (ms / 1000) % 60;
                                        long min = (ms / (1000 * 60)) % 60;
                                        long hrs = (ms / (1000 * 60 * 60)) % 24;
                                        if (hrs > 0) {
                                            durationStr = String.format(java.util.Locale.US, "\n\nDuration: %02d:%02d:%02d", hrs, min, sec);
                                        } else {
                                            durationStr = String.format(java.util.Locale.US, "\n\nDuration: %02d:%02d", min, sec);
                                        }
                                    }
                                    retriever.release();
                                } else {
                                    android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
                                    options.inJustDecodeBounds = true;
                                    android.graphics.BitmapFactory.decodeFile(item.getPath(), options);
                                    if (options.outWidth > 0 && options.outHeight > 0) {
                                        resolutionStr = options.outWidth + " x " + options.outHeight;
                                    }
                                }
                            } catch (Exception e2) {}
                            
                            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(MediaViewActivity.this);
                            builder.setTitle("File Details");
                            builder.setMessage(
                                "Name: " + file.getName() + "\n\n" +
                                "Format: " + extStr + " (" + mimeTypeStr + ")\n\n" +
                                "Path: " + item.getPath() + "\n\n" +
                                "Size: " + sizeStr + "\n\n" +
                                "Resolution: " + resolutionStr + 
                                durationStr + "\n\n" +
                                "Modified: " + dateModifiedStr + "\n\n" +
                                "Created: " + dateCreatedStr
                            );
                            builder.setPositiveButton("OK", null);
                            builder.show();
                        }
                    } catch (Exception e) {}
                }
            });
        }

        if (btnEdit != null) {
            btnEdit.setClickable(true); btnEdit.setFocusable(true);
            if (ripple != null) btnEdit.setBackground(ripple.getConstantState().newDrawable());
            btnEdit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        MediaItem item = activeList.get(viewPager.getCurrentItem());
                        android.net.Uri uri = getItemContentUri(item);
                        Intent ei = new Intent(Intent.ACTION_EDIT);
                        ei.setDataAndType(uri, item.isVideo() ? "video/*" : "image/*");
                        ei.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(ei, "Edit via"));
                    } catch (Exception e) {}
                }
            });
        }

        if (btnDelete != null) {
            btnDelete.setClickable(true); btnDelete.setFocusable(true);
            if (ripple != null) btnDelete.setBackground(ripple.getConstantState().newDrawable());
            btnDelete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        int pos = viewPager.getCurrentItem();
                        if (pos >= 0 && pos < activeList.size()) {
                            MediaItem item = activeList.get(pos);
                            ArrayList<String> pathsToDelete = new ArrayList<>();
                            pathsToDelete.add(item.getPath());
                            FileMethods.showDeleteConfirmationDialog(MediaViewActivity.this, pathsToDelete, REQ_DELETE_MEDIA);
                        }
                    } catch (Exception e) {}
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQ_DELETE_MEDIA || requestCode == REQ_RESTORE_MEDIA || requestCode == REQ_DELETE_PERM_MEDIA) {
                int pos = viewPager.getCurrentItem();
                if (pos >= 0 && pos < activeList.size()) {
                    animateAndRemoveItem(pos);
                }
            }
        }
    }

    private void animateAndRemoveItem(final int position) {
        if (isAnimatingRemoval) return;
        isAnimatingRemoval = true;

        stopVideoPlaybackEngine();

        final View currentView = viewPager.findViewWithTag(position);
        final View nextView = viewPager.findViewWithTag(position + 1);

        if (currentView == null) {
            isAnimatingRemoval = false;
            handleLocalFileDeleted(position);
            return;
        }

        currentView.setPivotX(currentView.getWidth() / 2f);
        currentView.setPivotY(currentView.getHeight() / 2f);

        currentView.animate()
                .scaleX(0f)
                .scaleY(0f)
                .alpha(0f)
                .setDuration(300)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        if (nextView != null) {
            float screenWidth = viewPager.getWidth();
            nextView.setTranslationX(screenWidth);
            nextView.animate()
                    .translationX(0f)
                    .setDuration(300)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                currentView.setScaleX(1f);
                currentView.setScaleY(1f);
                currentView.setAlpha(1f);
                if (nextView != null) {
                    nextView.setTranslationX(0f);
                }
                
                isAnimatingRemoval = false;
                handleLocalFileDeleted(position);
            }
        }, 300);
    }

    private void handleLocalFileDeleted(int position) {
        try {
            if (position < 0 || position >= activeList.size()) return;
            MediaItem removedItem = activeList.remove(position);
            
            if (!isTrashMode && removedItem != null) {
                String path = removedItem.getPath();
                for (int i = MediaScanner.allMediaList.size() - 1; i >= 0; i--) {
                    if (MediaScanner.allMediaList.get(i).getPath().equals(path)) { MediaScanner.allMediaList.remove(i); break; }
                }
                for (int i = MediaScanner.photosList.size() - 1; i >= 0; i--) {
                    if (MediaScanner.photosList.get(i).getPath().equals(path)) { MediaScanner.photosList.remove(i); break; }
                }
                for (int i = MediaScanner.videosList.size() - 1; i >= 0; i--) {
                    if (MediaScanner.videosList.get(i).getPath().equals(path)) { MediaScanner.videosList.remove(i); break; }
                }
            }
            
            if (activeList.isEmpty()) { 
                finish(); 
            } else { 
                pagerAdapter.notifyDataSetChanged();
                viewPager.setAdapter(pagerAdapter); 
                viewPager.setCurrentItem(Math.min(position, activeList.size() - 1), false); 
            }
        } catch(Exception e){}
    }

    private void startVideoPlaybackEngine(String filePath, FrameLayout itemContainer) {
        stopVideoPlaybackEngine();
        try {
            int videoLayoutRes = getResources().getIdentifier("video_player_view", "layout", pkg);
            if (videoLayoutRes != 0) {
                videoLayoutOverlay = LayoutInflater.from(this).inflate(videoLayoutRes, itemContainer, false);
                videoLayoutOverlay.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
                itemContainer.addView(videoLayoutOverlay);

                int vvId = getResources().getIdentifier("video_view_component", "id", pkg);
                int sbId = getResources().getIdentifier("video_seekbar", "id", pkg);
                int tcId = getResources().getIdentifier("txt_video_current", "id", pkg);
                int ttId = getResources().getIdentifier("txt_video_total", "id", pkg);
                int btnId = getResources().getIdentifier("play_pause_btn", "id", pkg);

                if (vvId != 0) videoView = (VideoView) videoLayoutOverlay.findViewById(vvId);
                if (sbId != 0) seekBar = (SeekBar) videoLayoutOverlay.findViewById(sbId);
                if (tcId != 0) txtCurrent = (TextView) videoLayoutOverlay.findViewById(tcId);
                if (ttId != 0) txtTotal = (TextView) videoLayoutOverlay.findViewById(ttId);
                if (btnId != 0) playPauseBtn = (ImageView) videoLayoutOverlay.findViewById(btnId);
                
                if (controlsView != null) { controlsView.setVisibility(View.GONE); }

                View.OnClickListener toggleListener = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        toggleControls(true);
                    }
                };

                videoLayoutOverlay.setOnClickListener(toggleListener);
                if (videoView != null) {
                    videoView.setOnClickListener(toggleListener);
                }

                if (playPauseBtn != null) {
                    int pauseIconId = getResources().getIdentifier("ic_pause", "drawable", pkg);
                    if (pauseIconId != 0) playPauseBtn.setImageResource(pauseIconId);
                    else playPauseBtn.setImageResource(android.R.drawable.ic_media_pause);
                    
                    playPauseBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (videoView != null) {
                                int pIcon = getResources().getIdentifier("ic_play", "drawable", pkg);
                                int psIcon = getResources().getIdentifier("ic_pause", "drawable", pkg);
                                if (videoView.isPlaying()) {
                                    videoView.pause();
                                    forceKeepPaused = true;
                                    if (pIcon != 0) playPauseBtn.setImageResource(pIcon);
                                    else playPauseBtn.setImageResource(android.R.drawable.ic_media_play);
                                } else {
                                    videoView.start();
                                    forceKeepPaused = false;
                                    if (psIcon != 0) playPauseBtn.setImageResource(psIcon);
                                    else playPauseBtn.setImageResource(android.R.drawable.ic_media_pause);
                                }
                            }
                        }
                    });
                }
            }
        } catch(Exception e){}

        if (videoView != null) {
            try {
                videoView.setVideoPath(filePath);
            } catch (Exception e) {
                int pos = viewPager.getCurrentItem();
                if (pos >= 0 && pos < activeList.size()) {
                    videoView.setVideoURI(getItemContentUri(activeList.get(pos)));
                }
            }

            videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    if (videoSavedPosition > 0) { 
                        videoView.seekTo(videoSavedPosition); 
                    }
                    int pIcon = getResources().getIdentifier("ic_play", "drawable", pkg);
                    int psIcon = getResources().getIdentifier("ic_pause", "drawable", pkg);
                    if (forceKeepPaused) {
                        videoView.pause();
                        if (playPauseBtn != null) {
                            if (pIcon != 0) playPauseBtn.setImageResource(pIcon);
                            else playPauseBtn.setImageResource(android.R.drawable.ic_media_play);
                        }
                    } else {
                        videoView.start();
                        if (psIcon != 0) playPauseBtn.setImageResource(psIcon);
                        else playPauseBtn.setImageResource(android.R.drawable.ic_media_pause);
                    }
                    if (seekBar != null) seekBar.setMax(videoView.getDuration());
                    setupProgressTracker();
                }
            });

            videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer mp) { stopVideoPlaybackEngine(); }
            });

            if (seekBar != null) {
                seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override 
                    public void onProgressChanged(SeekBar sb, int prg, boolean fUser) { 
                        if (fUser) { 
                            videoView.seekTo(prg);
                            if (txtCurrent != null) {
                                boolean showHours = videoView.getDuration() >= 3600000;
                                txtCurrent.setText(formatTime(prg, showHours));
                            }
                        } 
                    }
                    @Override public void onStartTrackingTouch(SeekBar sb) { isTracking = true; }
                    @Override public void onStopTrackingTouch(SeekBar sb) { isTracking = false; }
                });
            }
        }
    }

    private void stopVideoPlaybackEngine() {
        if (progressRunnable != null) progressHandler.removeCallbacks(progressRunnable);
        if (videoView != null) { try { videoView.stopPlayback(); } catch(Exception e){} videoView = null; }
        if (videoLayoutOverlay != null) {
            ViewGroup parent = (ViewGroup) videoLayoutOverlay.getParent();
            if (parent != null) parent.removeView(videoLayoutOverlay);
            videoLayoutOverlay = null;
        }
        if (pagerAdapter != null) {
            View cv = viewPager.findViewWithTag(viewPager.getCurrentItem());
            if (cv != null) {
                View bb = cv.findViewById(getResources().getIdentifier("ic_play_big_id", "id", pkg));
                if (bb != null) bb.setVisibility(View.VISIBLE);
            }
        }
        toolbar.setVisibility(View.VISIBLE);
        if (controlsView != null) controlsView.setVisibility(View.VISIBLE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        isControlsVisible = true;
        videoSavedPosition = -1;
        forceKeepPaused = false;
    }

    private void toggleControls(boolean isVideoMode) {
        isControlsVisible = !isControlsVisible;
        if (isControlsVisible) {
            toolbar.setVisibility(View.VISIBLE);
            if (isVideoMode && videoLayoutOverlay != null) {
                View sb = videoLayoutOverlay.findViewById(getResources().getIdentifier("video_seekbar", "id", pkg));
                View tc = videoLayoutOverlay.findViewById(getResources().getIdentifier("txt_video_current", "id", pkg));
                View tt = videoLayoutOverlay.findViewById(getResources().getIdentifier("txt_video_total", "id", pkg));
                if (sb != null) sb.setVisibility(View.VISIBLE);
                if (tc != null) tc.setVisibility(View.VISIBLE);
                if (tt != null) tt.setVisibility(View.VISIBLE);
                if (playPauseBtn != null) playPauseBtn.setVisibility(View.VISIBLE);
            } else {
                if (controlsView != null) controlsView.setVisibility(View.VISIBLE);
            }
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            toolbar.setVisibility(View.GONE);
            if (isVideoMode && videoLayoutOverlay != null) {
                View sb = videoLayoutOverlay.findViewById(getResources().getIdentifier("video_seekbar", "id", pkg));
                View tc = videoLayoutOverlay.findViewById(getResources().getIdentifier("txt_video_current", "id", pkg));
                View tt = videoLayoutOverlay.findViewById(getResources().getIdentifier("txt_video_total", "id", pkg));
                if (sb != null) sb.setVisibility(View.GONE);
                if (tc != null) tc.setVisibility(View.GONE);
                if (tt != null) tt.setVisibility(View.GONE);
                if (playPauseBtn != null) playPauseBtn.setVisibility(View.GONE);
            } else {
                if (controlsView != null) controlsView.setVisibility(View.GONE);
            }
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    private void setupProgressTracker() {
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (videoView != null && videoView.isPlaying()) {
                    int current = videoView.getCurrentPosition();
                    int total = videoView.getDuration();
                    if (seekBar != null && !isTracking) { seekBar.setProgress(current); }
                    boolean showHours = total >= 3600000;
                    if (txtCurrent != null && !isTracking) { txtCurrent.setText(formatTime(current, showHours)); }
                    if (txtTotal != null) txtTotal.setText(formatTime(total, showHours));
                }
                progressHandler.postDelayed(this, 1000);
            }
        };
        progressHandler.post(progressRunnable);
    }

    private String formatTime(int ms, boolean showHours) {
        int seconds = (ms / 1000) % 60; int minutes = (ms / (1000 * 60)) % 60; int hours = (ms / (1000 * 60 * 60)) % 24;
        return showHours ? String.format("%02d:%02d:%02d", hours, minutes, seconds) : String.format("%02d:%02d", minutes, seconds);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView != null) {
            videoSavedPosition = videoView.getCurrentPosition();
            forceKeepPaused = true;
            videoView.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (videoView != null && videoSavedPosition > 0) {
            videoView.seekTo(videoSavedPosition);
            videoView.pause();
        }
    }

    @Override
    protected void onDestroy() {
        if (progressRunnable != null) progressHandler.removeCallbacks(progressRunnable);
        if (videoView != null) { try { videoView.stopPlayback(); } catch(Exception e){} }
        super.onDestroy();
    }

    private class MediaPagerAdapter extends PagerAdapter {
        @Override public int getCount() { return activeList.size(); }
        @Override public boolean isViewFromObject(View v, Object obj) { return v == obj; }
        @Override public int getItemPosition(Object object) { return POSITION_NONE; }
        
        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            final MediaItem item = activeList.get(position);
            final FrameLayout itemRoot = new FrameLayout(MediaViewActivity.this);
            itemRoot.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
            itemRoot.setTag(position);

            final PhotoView photoView = new PhotoView(MediaViewActivity.this);
            photoView.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));

            if (item.isVideo()) {
                photoView.setZoomable(false);
                photoView.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { toggleControls(true); }
                });

                try {
                    File videoFile = new File(item.getPath());
                    Object loadTarget = videoFile.exists() ? videoFile : getItemContentUri(item);

                    Glide.with(getApplicationContext())
                            .load(loadTarget)
                            .asBitmap()
                            .encoder(new com.bumptech.glide.load.resource.bitmap.BitmapEncoder())
                            .diskCacheStrategy(DiskCacheStrategy.RESULT)
                            .dontAnimate()
                            .into(photoView);
                } catch (Exception ex) {
                    photoView.setImageResource(android.R.drawable.ic_menu_gallery);
                }
            } else {
                photoView.setZoomable(true);
                photoView.setScaleLevels(1.0f, 4.0f, 10.0f);

                photoView.setOnViewTapListener(new OnViewTapListener() {
                    @Override
                    public void onViewTap(View view, float x, float y) {
                        toggleControls(false);
                    }
                });

                try {
                    Glide.with(getApplicationContext())
                            .load(item.getPath())
                            .thumbnail(0.1f)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .skipMemoryCache(false)
                            .dontAnimate()
                            .into(photoView);
                } catch(Exception e){}
            }

            itemRoot.addView(photoView);

            if (item.isVideo()) {
                final ImageView bigBtn = new ImageView(MediaViewActivity.this);
                bigBtn.setId(getResources().getIdentifier("ic_play_big_id", "id", pkg));
                int bigPlayId = getResources().getIdentifier("ic_play_big", "drawable", pkg);
                if (bigPlayId != 0) bigBtn.setImageResource(bigPlayId);
                else bigBtn.setImageResource(android.R.drawable.ic_media_play);

                float density = getResources().getDisplayMetrics().density;
                bigBtn.setLayoutParams(new FrameLayout.LayoutParams((int)(96 * density), (int)(96 * density), Gravity.CENTER));
                itemRoot.addView(bigBtn);

                bigBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        bigBtn.setVisibility(View.GONE);
                        startVideoPlaybackEngine(item.getPath(), itemRoot);
                    }
                });
            }

            container.addView(itemRoot);
            return itemRoot;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
        }
    }
}
