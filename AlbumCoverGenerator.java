package com.bitoneko.gallery;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.media.MediaMetadataRetriever;
import android.widget.ImageView;
import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.Executors;

public class AlbumCoverGenerator {

    private static final android.util.LruCache<String, Bitmap> memoryCache = new android.util.LruCache<>(30);

    public static void loadCompositeCover(final Context ctx, final AlbumItem album, final ImageView iv) {
        if (album == null || iv == null) return;
        final String cacheKey = album.getAlbumPath() + "_" + album.getAlbumCount();
        Bitmap cachedBmp = memoryCache.get(cacheKey);
        if (cachedBmp != null) {
            iv.setImageBitmap(cachedBmp);
            return;
        }

        iv.setImageResource(android.R.drawable.ic_menu_gallery);
        iv.setTag(cacheKey);

        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    ArrayList<String> paths = album.getAlbumCovers();
                    int totalFiles = paths.size();
                    if (totalFiles == 0) return;

                    int targetSize = 300;
                    final Bitmap finalCollage = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.RGB_565);
                    Canvas canvas = new Canvas(finalCollage);

                    int size2 = targetSize / 2;

                    if (totalFiles >= 4) {
                        drawPart(paths.get(0), canvas, new Rect(0, 0, size2, size2));
                        drawPart(paths.get(1), canvas, new Rect(size2, 0, targetSize, size2));
                        drawPart(paths.get(2), canvas, new Rect(0, size2, size2, targetSize));
                        drawPart(paths.get(3), canvas, new Rect(size2, size2, targetSize, targetSize));
                    } else if (totalFiles == 3) {
                        drawPart(paths.get(0), canvas, new Rect(0, 0, size2, size2));
                        drawPart(paths.get(1), canvas, new Rect(size2, 0, targetSize, size2));
                        drawPart(paths.get(2), canvas, new Rect(0, size2, targetSize, targetSize));
                    } else if (totalFiles == 2) {
                        drawPart(paths.get(0), canvas, new Rect(0, 0, size2, targetSize));
                        drawPart(paths.get(1), canvas, new Rect(size2, 0, targetSize, targetSize));
                    } else {
                        drawPart(paths.get(0), canvas, new Rect(0, 0, targetSize, targetSize));
                    }

                    new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            memoryCache.put(cacheKey, finalCollage);
                            if (cacheKey.equals(iv.getTag())) {
                                iv.setImageBitmap(finalCollage);
                            }
                        }
                    });

                } catch (Exception e) {}
            }
        });
    }

    private static void drawPart(String path, Canvas canvas, Rect destRect) {
        Bitmap srcBmp = null;
        String lowercase = path.toLowerCase();
        boolean isVideo = lowercase.endsWith(".mp4") || lowercase.endsWith(".mkv") || lowercase.endsWith(".3gp") || lowercase.endsWith(".webm");

        try {
            if (isVideo) {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                retriever.setDataSource(path);
                srcBmp = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                retriever.release();
            } else {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 4;
                srcBmp = BitmapFactory.decodeFile(path, opts);
            }

            if (srcBmp != null) {
                int w = srcBmp.getWidth();
                int h = srcBmp.getHeight();
                int minSide = Math.min(w, h);
                Rect srcRect = new Rect((w - minSide) / 2, (h - minSide) / 2, (w + minSide) / 2, (h + minSide) / 2);
                canvas.drawBitmap(srcBmp, srcRect, destRect, null);
                srcBmp.recycle();
            }
        } catch (Exception e) {}
    }
}
