package com.bykerimoff.player.models;

import com.google.gson.annotations.SerializedName;

public class XtreamCategory {
    @SerializedName("category_id")
    private String id;
    @SerializedName("category_name")
    private String name;

    public String getId() { 
        if (id == null) return "0";
        String clean = id.trim();
        if (clean.endsWith(".0")) clean = clean.substring(0, clean.length() - 2);
        return clean;
    }
    public String getName() { return name != null ? name : ""; }
}
