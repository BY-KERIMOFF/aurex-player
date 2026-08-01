package com.bykerimoff.player.models;
import com.google.gson.annotations.SerializedName;
import java.util.List;

public class XtreamEpg {
    @SerializedName("epg_listings")
    private List<EpgListing> listings;

    public List<EpgListing> getListings() { return listings; }

    public static class EpgListing {
        @SerializedName("title")
        public String title;
        @SerializedName("start")
        public String start;
        @SerializedName("stop")
        public String stop;
    }
}
