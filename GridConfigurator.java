package com.bitoneko.gallery;

import android.app.Activity;
import android.widget.GridView;

public class GridConfigurator {

    public static void setupAllGrids(Activity act) {
        try {
            String pkg = act.getPackageName();
            String[] gridIds = {"grid_all", "grid_photos", "grid_videos", "grid_albums"};
            
            for (String idStr : gridIds) {
                int resId = act.getResources().getIdentifier(idStr, "id", pkg);
                if (resId != 0) {
                    final GridView gv = (GridView) act.findViewById(resId);
                    if (gv != null) {
                        gv.post(new Runnable() {
                            @Override
                            public void run() {
                                gv.setNumColumns(3);
                                gv.setHorizontalSpacing(0);
                                gv.setVerticalSpacing(0);
                                gv.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
                                gv.setPadding(0, 0, 0, 0);
                                gv.setClipToPadding(false);
                            }
                        });
                    }
                }
            }
        } catch (Exception e) {}
    }
}
