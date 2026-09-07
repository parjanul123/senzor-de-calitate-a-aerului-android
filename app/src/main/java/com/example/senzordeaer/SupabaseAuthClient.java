package com.example.senzordeaer;

import okhttp3.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;

public class SupabaseAuthClient {
    private static final String AUTH_URL = "https://eakzxbfcwbgfxfujzote.supabase.co/auth/v1/";
    private static final String API_KEY = "sb_publishable_ofI6pPkeb2csAsw_ZqhCng_d3ADhRZU";
    private final OkHttpClient client = NetworkSecurity.INSTANCE.hardenedClientBuilder().build();
    private final Gson gson = new Gson();

    public JsonObject signUp(String email, String password) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("email", email);
        payload.addProperty("password", password);
        return performAuthRequest(AUTH_URL + "signup", payload);
    }

    public JsonObject verifySignupOtp(String email, String token) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("email", email);
        payload.addProperty("token", token);
        payload.addProperty("type", "signup");
        return performAuthRequest(AUTH_URL + "verify", payload);
    }

    public void sendPasswordResetCode(String email) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("email", email);
        performAuthRequest(AUTH_URL + "recover", payload);
    }

    public JsonObject verifyPasswordResetOtp(String email, String token) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("email", email);
        payload.addProperty("token", token);
        payload.addProperty("type", "recovery");
        return performAuthRequest(AUTH_URL + "verify", payload);
    }

    public void updatePassword(String accessToken, String newPassword) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("password", newPassword);
        RequestBody body = RequestBody.create(gson.toJson(payload), MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(AUTH_URL + "user")
                .addHeader("apikey", API_KEY)
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("Content-Type", "application/json")
                .put(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException(responseBody);
            }
        }
    }

    public JsonObject login(String email, String password) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("email", email);
        payload.addProperty("password", password);
        return performAuthRequest(AUTH_URL + "token?grant_type=password", payload);
    }

    public JsonObject refreshSession(String refreshToken) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("refresh_token", refreshToken);
        return performAuthRequest(AUTH_URL + "token?grant_type=refresh_token", payload);
    }

    private JsonObject performAuthRequest(String url, JsonObject payload) throws IOException {
        RequestBody body = RequestBody.create(gson.toJson(payload), MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(url)
                .addHeader("apikey", API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "{}";
            if (!response.isSuccessful()) {
                throw new IOException(responseBody);
            }
            return gson.fromJson(responseBody, JsonObject.class);
        }
    }
}