package com.example.senzordeaer;

import okhttp3.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;

public class SupabaseAuthClient {
    private static final String AUTH_URL = "https://eakzxbfcwbgfxfujzote.supabase.co/auth/v1/";
    private static final String API_KEY = "sb_publishable_ofI6pPkeb2csAsw_ZqhCng_d3ADhRZU";
    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();

    public JsonObject signUp(String email, String password) throws IOException {
        String json = String.format("{\"email\":\"%s\", \"password\":\"%s\"}", email, password);
        return performAuthRequest(AUTH_URL + "signup", json);
    }

    public JsonObject login(String email, String password) throws IOException {
        String json = String.format("{\"email\":\"%s\", \"password\":\"%s\"}", email, password);
        return performAuthRequest(AUTH_URL + "token?grant_type=password", json);
    }

    private JsonObject performAuthRequest(String url, String json) throws IOException {
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(url)
                .addHeader("apikey", API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "{}";
            if (!response.isSuccessful()) {
                // Aruncăm eroarea JSON mai departe pentru a putea fi parsată în MainActivity
                throw new IOException(responseBody);
            }
            return gson.fromJson(responseBody, JsonObject.class);
        }
    }
}