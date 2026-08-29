package com.bykerimoff.player.api;

import com.google.gson.annotations.SerializedName;

public class ApiResponse {
    @SerializedName("status")
    private String status;

    @SerializedName("expiry_date")
    private String expiryDate;

    @SerializedName("expire_date")
    private String expireDate;

    @SerializedName("expiry")
    private String expiry;

    @SerializedName("exp_date")
    private String expDate;

    @SerializedName("end_date")
    private String endDate;

    @SerializedName("expire")
    private String expire;

    @SerializedName("valid_until")
    private String validUntil;

    @SerializedName("date")
    private String date;

    @SerializedName("finish_date")
    private String finishDate;

    @SerializedName("sub_end")
    private String subEnd;

    @SerializedName("active_until")
    private String activeUntil;

    @SerializedName("exp")
    private String exp;

    @SerializedName("expires_at")
    private String expiresAt;

    @SerializedName("bitis_tarixi")
    private String bitisTarixi;

    @SerializedName("bitme_vaxti")
    private String bitmeVaxti;

    @SerializedName("bitis")
    private String bitis;

    @SerializedName("tarix")
    private String tarix;

    @SerializedName("message")
    private String message;

    @SerializedName("playlist_type")
    private String playlistType;

    @SerializedName("m3u_url")
    private String m3uUrl;

    @SerializedName("vod")
    private Object vod;

    @SerializedName("series")
    private Object series;

    @SerializedName("vod_enabled")
    private Object vodEnabledField;

    @SerializedName("series_enabled")
    private Object seriesEnabledField;

    @SerializedName("seriya")
    private String seriya;

    @SerializedName("xtream")
    private XtreamInfo xtream;

    @SerializedName("test_mode")
    private Object testMode;

    @SerializedName("test_expire")
    private String testExpire;

    @SerializedName("test_expire_formatted")
    private String testExpireFormatted;

    @SerializedName("countdown")
    private String countdown;

    @SerializedName("warning")
    private String warning;

    @SerializedName("warning_level")
    private String warningLevel;

    @SerializedName("test_remaining_seconds")
    private Object testRemainingSeconds;

    @SerializedName("test_time_left")
    private String testTimeLeft;

    @SerializedName("test_message")
    private String testMessage;

    @SerializedName("test_color")
    private String testColor;

    @SerializedName("test_warning")
    private String testWarning;

    @SerializedName("showAnnouncement")
    private Object showAnnouncementField;

    @SerializedName("announcement")
    private String announcement;

    @SerializedName("announcementColor")
    private String announcementColor;

    @SerializedName("announcementSpeed")
    private int announcementSpeed;

    @SerializedName("detail")
    private String detail;

    @SerializedName("is_adult")
    private int isAdult;

    @SerializedName("is_sport")
    private int isSport;

    public static class XtreamInfo {
        @SerializedName("host")
        private String host;
        @SerializedName("username")
        private String username;
        @SerializedName("password")
        private String password;

        public String getHost() { return host; }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
    }

    public String getPlaylistType() { return playlistType; }
    public String getM3uUrl() { return m3uUrl; }
    public XtreamInfo getXtream() { return xtream; }

    public boolean isVodEnabled() {
        Object v = (vodEnabledField != null) ? vodEnabledField : vod;
        if (v == null) return true;
        String val = String.valueOf(v).trim().toLowerCase();
        return !(val.equals("0") || val.equals("false") || val.equals("disabled") || val.equals("null") || val.equals(""));
    }

    public boolean isSeriesEnabled() {
        Object s = (seriesEnabledField != null) ? seriesEnabledField : (series != null ? series : seriya);
        if (s == null) return true;
        String val = String.valueOf(s).trim().toLowerCase();
        return !(val.equals("0") || val.equals("false") || val.equals("disabled") || val.equals("null") || val.equals(""));
    }

    public String getStatus() {
        return status;
    }

    public String getExpiryDate() {
        if (isValid(expiryDate)) return expiryDate;
        if (isValid(expireDate)) return expireDate;
        if (isValid(expiry)) return expiry;
        if (isValid(expDate)) return expDate;
        if (isValid(endDate)) return endDate;
        if (isValid(expire)) return expire;
        if (isValid(validUntil)) return validUntil;
        if (isValid(date)) return date;
        if (isValid(finishDate)) return finishDate;
        if (isValid(subEnd)) return subEnd;
        if (isValid(activeUntil)) return activeUntil;
        if (isValid(exp)) return exp;
        if (isValid(expiresAt)) return expiresAt;
        if (isValid(bitisTarixi)) return bitisTarixi;
        if (isValid(bitmeVaxti)) return bitmeVaxti;
        if (isValid(bitis)) return bitis;
        if (isValid(tarix)) return tarix;
        
        return null;
    }
    
    public String getAllKeys() {
        // Bu metod xətanın səbəbini tapmağa kömək edəcək
        return "JSON-da olan sahələr: status, message, ... (və digərləri)";
    }

    private boolean isValid(String val) {
        return val != null && !val.trim().isEmpty() && !val.equalsIgnoreCase("null");
    }

    public String getMessage() {
        return message;
    }

    public boolean isTestMode() {
        if (testMode == null) return false;
        if (testMode instanceof Boolean) return (Boolean) testMode;
        if (testMode instanceof Number) return ((Number) testMode).intValue() == 1;
        if (testMode instanceof String) {
            String s = ((String) testMode).trim().toLowerCase();
            return s.equals("1") || s.equals("true") || s.equals("yes");
        }
        return false;
    }

    public String getTestExpire() {
        return testExpire;
    }

    public String getTestExpireFormatted() {
        return testExpireFormatted;
    }

    public String getCountdown() {
        return countdown;
    }

    public String getWarning() {
        return warning;
    }

    public String getWarningLevel() {
        return warningLevel;
    }

    public long getTestRemainingSeconds() {
        if (testRemainingSeconds == null) return 0;
        if (testRemainingSeconds instanceof Number) return ((Number) testRemainingSeconds).longValue();
        if (testRemainingSeconds instanceof String) {
            try {
                // Double.parseDouble is safer for strings like "3600.0"
                return (long) Double.parseDouble((String) testRemainingSeconds);
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    public String getTestTimeLeft() {
        return testTimeLeft;
    }

    public String getTestMessage() {
        return testMessage;
    }

    public String getTestColor() {
        return testColor;
    }

    public String getTestWarning() {
        return testWarning;
    }

    public boolean isAdultEnabled() {
        return isAdult == 1;
    }

    public boolean isSportEnabled() {
        return isSport == 1;
    }

    public String getDetail() {
        return detail;
    }

    public boolean isShowAnnouncement() {
        if (showAnnouncementField == null) return false;
        if (showAnnouncementField instanceof Boolean) return (Boolean) showAnnouncementField;
        if (showAnnouncementField instanceof Number) return ((Number) showAnnouncementField).intValue() == 1;
        String val = String.valueOf(showAnnouncementField).trim().toLowerCase();
        return val.equals("1") || val.equals("true");
    }

    public String getAnnouncement() {
        return (announcement != null && !announcement.isEmpty()) ? announcement : null;
    }

    public String getAnnouncementColor() {
        return announcementColor;
    }

    public int getAnnouncementSpeed() {
        return announcementSpeed;
    }
}
