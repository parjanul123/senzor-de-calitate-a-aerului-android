package com.example.senzordeaer;

import okhttp3.*;
import com.google.gson.Gson;
import java.io.IOException;

public class SupabaseService {
    private static final String BASE_URL = "https://eakzxbfcwbgfxfujzote.supabase.co/rest/v1/";
    private static final String API_KEY = "sb_publishable_ofI6pPkeb2csAsw_ZqhCng_d3ADhRZU";
    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();

    public void createUserProfile(String accessToken, String userId, String username, String email) throws IOException {
        String json = String.format("{\"id\":\"%s\", \"username\":\"%s\", \"email\":\"%s\", \"owned_devices\":0}", userId, username, email);
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(BASE_URL + "users")
                .addHeader("apikey", API_KEY)
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String err = response.body() != null ? response.body().string() : "";
                throw new IOException("Eroare creare profil: " + response.code() + " " + err);
            }
        }
    }

    public UserProfile getUserProfile(String accessToken, String userId) throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + "users?id=eq." + userId + "&select=*")
                .addHeader("apikey", API_KEY)
                .addHeader("Authorization", "Bearer " + accessToken)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                UserProfile[] profiles = gson.fromJson(response.body().string(), UserProfile[].class);
                return (profiles != null && profiles.length > 0) ? profiles[0] : null;
            }
        }
        return null;
    }

    public Device[] getMyDevices(String accessToken, String userId) throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + "devices?owner_id=eq." + userId + "&select=*")
                .addHeader("apikey", API_KEY)
                .addHeader("Authorization", "Bearer " + accessToken)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return gson.fromJson(response.body().string(), Device[].class);
            }
        }
        return new Device[0];
    }
    
    public void claimDevice(String accessToken, String userId, String deviceId, String name) throws IOException {
        String json = String.format("{\"device_id\":\"%s\", \"owner_id\":\"%s\", \"name\":\"%s\"}", deviceId, userId, name);
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
        
        Request request = new Request.Builder()
                .url(BASE_URL + "devices?on_conflict=device_id")
                .addHeader("apikey", API_KEY)
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Eroare la adăugarea dispozitivului: " + response.code());
            }
        }
    }

    public void updateOwnedDevicesCount(String accessToken, String userId, int count) throws IOException {
        String json = String.format("{\"owned_devices\":%d}", count);
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
        
        Request request = new Request.Builder()
                .url(BASE_URL + "users?id=eq." + userId)
                .addHeader("apikey", API_KEY)
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("Prefer", "return=representation")
                .patch(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Update failed: " + response.code());
        }
    }
    
    public void updateDeviceLocation(String accessToken, String deviceId, String location) throws IOException {
        String json = String.format("{\"location\":\"%s\"}", location);
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
        
        Request request = new Request.Builder()
                .url(BASE_URL + "devices?device_id=eq." + deviceId)
                .addHeader("apikey", API_KEY)
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("Prefer", "return=representation")
                .patch(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Actualizare locație eșuată.");
            }
        }
    }

    public void deleteDevice(String accessToken, String userId, String deviceId) throws IOException {
        HttpUrl url = HttpUrl.parse(BASE_URL + "devices").newBuilder()
                .addQueryParameter("device_id", "eq." + deviceId)
                .addQueryParameter("owner_id", "eq." + userId)
                .build();
        RequestBody body = RequestBody.create("{\"owner_id\":null}", MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(url)
                .addHeader("apikey", API_KEY)
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("Prefer", "return=representation")
                .patch(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String error = response.body() != null ? response.body().string() : "";
                throw new IOException("Eliminare eșuată: " + response.code() + " " + error);
            }
            String deletedRows = response.body() != null ? response.body().string().trim() : "";
            if (deletedRows.equals("[]") || deletedRows.isEmpty()) {
                throw new IOException("Dispozitivul nu poate fi eliminat din acest cont.");
            }
        }
    }

    public Measurement[] getDeviceMeasurements(String accessToken, String deviceId) throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + "measurements?device_id=eq." + deviceId + "&select=*&order=created_at.desc&limit=50")
                .addHeader("apikey", API_KEY)
                .addHeader("Authorization", "Bearer " + accessToken)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return gson.fromJson(response.body().string(), Measurement[].class);
            }
        }
        return new Measurement[0];
    }
}