package com.bitoneko.gallery;

public class GridItem {
    public static final int TYPE_HEADER = 0;
    public static final int TYPE_MEDIA = 1;

    private int itemType;
    private String dateText;
    private MediaItem mediaItem;

    public GridItem(String dateText) {
        this.itemType = TYPE_HEADER;
        this.dateText = dateText;
    }

    public GridItem(MediaItem mediaItem) {
        this.itemType = TYPE_MEDIA;
        this.mediaItem = mediaItem;
    }

    public int getItemType() { return itemType; }
    public String getDateText() { return dateText; }
    public MediaItem getMediaItem() { return mediaItem; }
}
