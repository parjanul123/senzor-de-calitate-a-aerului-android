package com.example.senzordeaer;

import okhttp3.*;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;

public class SupabaseService {
    private static final String BASE_URL = "https://eakzxbfcwbgfxfujzote.supabase.co/rest/v1/";
    private static final String API_KEY = "sb_publishable_ofI6pPkeb2csAsw_ZqhCng_d3ADhRZU";
    private final OkHttpClient client = NetworkSecurity.INSTANCE.hardenedClientBuilder().build();
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

    public String getTransportProfiles(String accessToken, String userId) throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + "profiles?user_id=eq." + userId + "&select=id,name,device_id,temperature_min,temperature_max,humidity_min,humidity_max,pressure_min,pressure_max,co2_min,co2_max,pm25_min,pm25_max,pm10_min,pm10_max,light_min,light_max")
                .addHeader("apikey", API_KEY)
                .addHeader("Authorization", "Bearer " + accessToken)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String error = response.body() != null ? response.body().string() : "";
                throw new IOException("Citirea profilelor a eșuat: " + response.code() + " " + error);
            }
            String body = response.body() != null ? response.body().string() : "[]";
            JsonArray rows = JsonParser.parseString(body).getAsJsonArray();
            return rows.toString();
        }
    }

    public void saveTransportProfiles(String accessToken, String userId, String deviceId, String profilesJson) throws IOException {
        JsonArray profiles = JsonParser.parseString(profilesJson).getAsJsonArray();
        for (int index = 0; index < profiles.size(); index++) {
            JsonObject profile = profiles.get(index).getAsJsonObject();
            JsonObject limits = profile.has("limits") ? profile.getAsJsonObject("limits") : new JsonObject();
            profile.remove("limits");
            profile.remove("is_standard");
            profile.addProperty("user_id", userId);
            profile.addProperty("device_id", deviceId);
            addLimitColumns(profile, limits, "temperature", "temperature_min", "temperature_max");
            addLimitColumns(profile, limits, "humidity", "humidity_min", "humidity_max");
            addLimitColumns(profile, limits, "pressure", "pressure_min", "pressure_max");
            addLimitColumns(profile, limits, "co2", "co2_min", "co2_max");
            addLimitColumns(profile, limits, "pm25", "pm25_min", "pm25_max");
            addLimitColumns(profile, limits, "pm10", "pm10_min", "pm10_max");
            addLimitColumns(profile, limits, "light", "light_min", "light_max");
        }
        RequestBody body = RequestBody.create(profiles.toString(), MediaType.parse("application/json"));
        Request request = new Request.Builder()
            .url(BASE_URL + "profiles?on_conflict=id")
                .addHeader("apikey", API_KEY)
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String error = response.body() != null ? response.body().string() : "";
                throw new IOException("Salvarea profilelor a eșuat: " + response.code() + " " + error);
            }
            String savedRows = response.body() != null ? response.body().string().trim() : "";
            if (savedRows.equals("[]") || savedRows.isEmpty()) {
                throw new IOException("Profilul nu a putut fi creat sau actualizat.");
            }
        }
    }

    private void addLimitColumns(JsonObject profile, JsonObject limits, String parameter, String minimumColumn, String maximumColumn) {
        JsonObject limit = limits.has(parameter) && limits.get(parameter).isJsonObject()
                ? limits.getAsJsonObject(parameter) : null;
        if (limit == null || !limit.has("minimum") || limit.get("minimum").isJsonNull()) profile.add(minimumColumn, null);
        else profile.add(minimumColumn, limit.get("minimum"));
        if (limit == null || !limit.has("maximum") || limit.get("maximum").isJsonNull()) profile.add(maximumColumn, null);
        else profile.add(maximumColumn, limit.get("maximum"));
    }

    public void deleteTransportProfile(String accessToken, String userId, String profileId) throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + "profiles?user_id=eq." + userId + "&id=eq." + profileId)
                .addHeader("apikey", API_KEY)
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("Prefer", "return=representation")
                .delete()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String error = response.body() != null ? response.body().string() : "";
                throw new IOException("Ștergerea profilului a eșuat: " + response.code() + " " + error);
            }
        }
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