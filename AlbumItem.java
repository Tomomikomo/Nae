package com.bitoneko.gallery;

import java.util.ArrayList;

public class AlbumItem {
    private String albumPath;
    private String albumName;
    private int albumCount;
    private ArrayList<String> albumCovers;

    public AlbumItem(String albumPath, String albumName, int albumCount, ArrayList<String> albumCovers) {
        this.albumPath = albumPath;
        this.albumName = albumName;
        this.albumCount = albumCount;
        this.albumCovers = albumCovers;
    }

    public String getAlbumPath() { return albumPath; }
    public String getAlbumName() { return albumName; }
    public int getAlbumCount() { return albumCount; }
    public ArrayList<String> getAlbumCovers() { return albumCovers; }
}
