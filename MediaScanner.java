package com.bitoneko.gallery;

import android.app.Activity;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class MediaScanner {

    public static ArrayList<MediaItem> allMediaList = new ArrayList<>();
    public static ArrayList<MediaItem> photosList = new ArrayList<>();
    public static ArrayList<MediaItem> videosList = new ArrayList<>();
    public static ArrayList<AlbumItem> albumsList = new ArrayList<>();

    public static ArrayList<GridItem> allMediaRecyclerList = new ArrayList<>();
    public static ArrayList<GridItem> photosRecyclerList = new ArrayList<>();
    public static ArrayList<GridItem> videosRecyclerList = new ArrayList<>();
    
    private static GalleryRecyclerAdapter adapterAll;
    private static GalleryRecyclerAdapter adapterPhotos;
    private static GalleryRecyclerAdapter adapterVideos;
    private static GalleryAlbumsAdapter adapterAlbums;
    private static ContentObserver mediaObserver;
    
    public static MediaViewActivity activeViewActivityInstance = null;
    private static final String[] gridIds = {"grid_all", "grid_photos", "grid_videos", "grid_albums"};

    public static void startEngine(final Activity act) {
        final String pkg = act.getPackageName();
        
        act.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    int gAll = act.getResources().getIdentifier("grid_all", "id", pkg);
                    if (gAll != 0) {
                        RecyclerView rv = (RecyclerView) act.findViewById(gAll);
                        if (rv != null) { adapterAll = new GalleryRecyclerAdapter(act, allMediaRecyclerList, 0); setupRecyclerLayout(rv, adapterAll, 4); }
                    }

                    int gPho = act.getResources().getIdentifier("grid_photos", "id", pkg);
                    if (gPho != 0) {
                        RecyclerView rv = (RecyclerView) act.findViewById(gPho);
                        if (rv != null) { adapterPhotos = new GalleryRecyclerAdapter(act, photosRecyclerList, 1); setupRecyclerLayout(rv, adapterPhotos, 4); }
                    }

                    int gVid = act.getResources().getIdentifier("grid_videos", "id", pkg);
                    if (gVid != 0) {
                        RecyclerView rv = (RecyclerView) act.findViewById(gVid);
                        if (rv != null) { adapterVideos = new GalleryRecyclerAdapter(act, videosRecyclerList, 2); setupRecyclerLayout(rv, adapterVideos, 4); }
                    }

                    int gAlb = act.getResources().getIdentifier("grid_albums", "id", pkg);
                    if (gAlb != 0) {
                        RecyclerView rv = (RecyclerView) act.findViewById(gAlb);
                        if (rv != null) {
                            adapterAlbums = new GalleryAlbumsAdapter(act, albumsList);
                            rv.setLayoutManager(new GridLayoutManager(act, 4));
                            rv.setHasFixedSize(true);
                            rv.setAdapter(adapterAlbums);
                        }
                    }
                } catch(Exception e){}
            }
        });

        registerRealtimeObserver(act);
        reloadMediaData(act);
    }

    private static void setupRecyclerLayout(RecyclerView rv, final GalleryRecyclerAdapter adapter, int spanCount) {
        GridLayoutManager glm = new GridLayoutManager(rv.getContext(), spanCount);
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int pos) {
                return adapter.getItemViewType(pos) == GridItem.TYPE_HEADER ? 4 : 1;
            }
        });
        rv.setHasFixedSize(true);
        rv.setItemViewCacheSize(20);
        rv.setDrawingCacheEnabled(true);
        rv.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
        rv.setLayoutManager(glm);
        rv.setAdapter(adapter);
    }

    public static void reloadMediaData(final Activity act) {
        allMediaList.clear();
        photosList.clear();
        videosList.clear();
        albumsList.clear();

        HashMap<String, ArrayList<MediaItem>> albumGroupMap = new HashMap<>();
        ContentResolver resolver = act.getContentResolver();
        
        Uri[] uris = new Uri[] { MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.EXTERNAL_CONTENT_URI };
        String[] projection = { MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.SIZE };

        for (Uri uri : uris) {
            Cursor cursor = resolver.query(uri, projection, null, null, MediaStore.MediaColumns.DATE_ADDED + " DESC");
            if (cursor != null) {
                int dataIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
                int nameIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
                int dateIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED);
                int mimeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE);
                int sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE);

                while (cursor.moveToNext()) {
                    String path = cursor.getString(dataIdx);
                    String name = cursor.getString(nameIdx);
                    long dateAdded = cursor.getLong(dateIdx);
                    String mime = cursor.getString(mimeIdx);
                    long size = cursor.getLong(sizeIdx);

                    if (path == null || mime == null) continue;
                    File file = new File(path);
                    if (!file.exists()) continue;

                    boolean isVideo = mime.startsWith("video");
                    MediaItem item = new MediaItem(path, name != null ? name : file.getName(), dateAdded * 1000, mime, size, isVideo);

                    allMediaList.add(item);
                    if (isVideo) videosList.add(item);
                    else if (mime.startsWith("image")) photosList.add(item);

                    File parent = file.getParentFile();
                    if (parent != null) {
                        String aPath = parent.getAbsolutePath();
                        if (!albumGroupMap.containsKey(aPath)) { albumGroupMap.put(aPath, new ArrayList<MediaItem>()); }
                        albumGroupMap.get(aPath).add(item);
                    }
                }
                cursor.close();
            }
        }

        allMediaList.sort((o1, o2) -> Long.compare(o2.getDate(), o1.getDate()));
        photosList.sort((o1, o2) -> Long.compare(o2.getDate(), o1.getDate()));
        videosList.sort((o1, o2) -> Long.compare(o2.getDate(), o1.getDate()));

        for (String aPath : albumGroupMap.keySet()) {
            ArrayList<MediaItem> items = albumGroupMap.get(aPath);
            if (items != null && !items.isEmpty()) {
                items.sort((o1, o2) -> Long.compare(o2.getDate(), o1.getDate()));
                ArrayList<String> covers = new ArrayList<>();
                for (int i = 0; i < Math.min(4, items.size()); i++) { covers.add(items.get(i).getPath()); }
                albumsList.add(new AlbumItem(aPath, new File(aPath).getName(), items.size(), covers));
            }
        }
        
        generateGroupedRecyclerLists();

        act.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (adapterAll != null) adapterAll.notifyDataSetChanged();
                if (adapterPhotos != null) adapterPhotos.notifyDataSetChanged();
                if (adapterVideos != null) adapterVideos.notifyDataSetChanged();
                if (adapterAlbums != null) adapterAlbums.notifyDataSetChanged();
                checkEmptyState(act);
            }
        });
    }
    private static void generateGroupedRecyclerLists() {
        allMediaRecyclerList.clear(); photosRecyclerList.clear(); videosRecyclerList.clear();
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM d, yyyy", Locale.US);

        String lastDateAll = "";
        for (MediaItem mi : allMediaList) {
            String currentDate = sdf.format(new Date(mi.getDate()));
            if (!currentDate.equals(lastDateAll)) { allMediaRecyclerList.add(new GridItem(currentDate)); lastDateAll = currentDate; }
            allMediaRecyclerList.add(new GridItem(mi));
        }
        String lastDatePhotos = "";
        for (MediaItem mi : photosList) {
            String currentDate = sdf.format(new Date(mi.getDate()));
            if (!currentDate.equals(lastDatePhotos)) { photosRecyclerList.add(new GridItem(currentDate)); lastDatePhotos = currentDate; }
            photosRecyclerList.add(new GridItem(mi));
        }
        String lastDateVideos = "";
        for (MediaItem mi : videosList) {
            String currentDate = sdf.format(new Date(mi.getDate()));
            if (!currentDate.equals(lastDateVideos)) { videosRecyclerList.add(new GridItem(currentDate)); lastDateVideos = currentDate; }
            videosRecyclerList.add(new GridItem(mi));
        }
    }

    private static void checkEmptyState(Activity act) {
        try {
            String pkg = act.getPackageName();
            int emptyLayoutId = act.getResources().getIdentifier("layout_empty", "id", pkg);
            if (emptyLayoutId != 0) {
                View emptyLayout = act.findViewById(emptyLayoutId);
                if (emptyLayout != null) emptyLayout.setVisibility(allMediaList.isEmpty() ? View.VISIBLE : View.GONE);
            }
        } catch (Exception e) {}
    }

    public static void selectTab(Activity act, int tabIndex) {
        String pkg = act.getPackageName();
        for (int i = 0; i < gridIds.length; i++) {
            int gId = act.getResources().getIdentifier(gridIds[i], "id", pkg);
            if (gId != 0) {
                View grid = act.findViewById(gId);
                if (grid != null) grid.setVisibility((i == tabIndex) ? View.VISIBLE : View.GONE);
            }
        }
    }

    private static void registerRealtimeObserver(final Activity act) {
        if (mediaObserver != null) return;
        mediaObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange, Uri uri) { super.onChange(selfChange, uri); reloadMediaData(act); }
        };
        act.getContentResolver().registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, mediaObserver);
        act.getContentResolver().registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, mediaObserver);
    }
}