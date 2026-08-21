package com.example.senzordeaer;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getSharedPreferences("SupabaseSession", Context.MODE_PRIVATE);
    }

    public void saveSession(String accessToken, String refreshToken, String userId) {
        prefs.edit()
                .putString("access_token", accessToken)
                .putString("refresh_token", refreshToken)
                .putString("user_id", userId)
                .apply();
    }

    public String getAccessToken() {
        return prefs.getString("access_token", null);
    }

    public String getRefreshToken() {
        return prefs.getString("refresh_token", null);
    }

    public String getUserId() {
        return prefs.getString("user_id", null);
    }
    
    public void clear() {
        prefs.edit().clear().apply();
    }
}