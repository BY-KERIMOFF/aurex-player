package com.bykerimoff.player.api;

import com.bykerimoff.player.models.RadioStation;
import com.bykerimoff.player.models.XtreamCategory;
import com.bykerimoff.player.models.XtreamChannel;
import com.bykerimoff.player.models.XtreamEpg;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Url;

public interface ApiService {
    @GET
    Call<ApiResponse> checkMac(@Url String url);

    @GET
    Call<List<XtreamCategory>> getXtreamCategories(@Url String url);

    @GET
    Call<List<XtreamChannel>> getXtreamChannels(@Url String url);

    @GET
    Call<List<XtreamChannel>> getXtreamVod(@Url String url);

    @GET
    Call<List<XtreamCategory>> getXtreamVodCategories(@Url String url);

    @GET
    Call<List<XtreamCategory>> getXtreamSeriesCategories(@Url String url);

    @GET
    Call<XtreamEpg> getXtreamEpg(@Url String url);

    @GET
    Call<okhttp3.ResponseBody> getRawResponse(@Url String url);

    @GET
    Call<List<RadioStation>> getRadioStations(@Url String url);
}
