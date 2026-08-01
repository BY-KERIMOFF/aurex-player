package com.bykerimoff.player.models;

import com.google.gson.annotations.SerializedName;

public class XtreamChannel {
    @SerializedName("num")
    private String num;
    @SerializedName("name")
    private String name;
    @SerializedName("stream_id")
    private String streamId;
    @SerializedName("series_id")
    private String seriesId;
    @SerializedName("vod_id")
    private String vodId;
    @SerializedName("stream_icon")
    private String logo;
    @SerializedName("cover")
    private String cover;
    @SerializedName("category_id")
    private String categoryId;
    @SerializedName("container_extension")
    private String containerExtension;

    public String getNum() { return num; }
    public String getName() { return name; }
    public String getStreamId() { 
        if (streamId != null && !streamId.trim().isEmpty()) return streamId.trim();
        if (seriesId != null && !seriesId.trim().isEmpty()) return seriesId.trim();
        if (vodId != null && !vodId.trim().isEmpty()) return vodId.trim();
        return ""; 
    }
    public String getLogo() { 
        if (logo != null && !logo.trim().isEmpty()) return logo.trim();
        if (cover != null && !cover.trim().isEmpty()) return cover.trim();
        return ""; 
    }
    public String getCategoryId() { 
        if (categoryId == null) return "0";
        String clean = categoryId.trim();
        if (clean.endsWith(".0")) clean = clean.substring(0, clean.length() - 2);
        return clean;
    }
    public String getContainerExtension() {
        if (containerExtension != null && !containerExtension.trim().isEmpty()) {
            return containerExtension.trim();
        }
        return "mp4";
    }
}
