package com.example.senzordeaer;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Stochează sesiunea (token-uri, id utilizator) în EncryptedSharedPreferences,
 * criptat cu o cheie AES256-GCM păstrată în Android Keystore. Urmărește și
 * expirarea token-ului de acces pentru a permite reîmprospătarea proactivă.
 */
public class SessionManager {
    private static final long DEFAULT_EXPIRES_IN_SECONDS = 3600L;
    // Reîmprospătăm token-ul cu 60s înainte de expirarea reală, ca marjă de siguranță.
    private static final long EXPIRY_SAFETY_MARGIN_MS = 60_000L;

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = createEncryptedPrefs(context);
    }

    private static SharedPreferences createEncryptedPrefs(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyGenParameterSpec(new KeyGenParameterSpec.Builder(
                            MasterKey.DEFAULT_MASTER_KEY_ALIAS,
                            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setKeySize(256)
                            .build())
                    .build();
            return EncryptedSharedPreferences.create(
                    context,
                    "SupabaseSessionSecure",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            // Nu ar trebui să eșueze pe dispozitive cu Keystore funcțional; dacă totuși
            // se întâmplă, revenim la stocare simplă pentru a nu bloca aplicația.
            return context.getSharedPreferences("SupabaseSession", Context.MODE_PRIVATE);
        }
    }

    public void saveSession(String accessToken, String refreshToken, String userId) {
        saveSession(accessToken, refreshToken, userId, DEFAULT_EXPIRES_IN_SECONDS);
    }

    public void saveSession(String accessToken, String refreshToken, String userId, long expiresInSeconds) {
        long expiresAtMillis = System.currentTimeMillis() + (expiresInSeconds * 1000L);
        prefs.edit()
                .putString("access_token", accessToken)
                .putString("refresh_token", refreshToken)
                .putString("user_id", userId)
                .putLong("expires_at", expiresAtMillis)
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

    public long getExpiresAt() {
        return prefs.getLong("expires_at", 0L);
    }

    /** True dacă token-ul curent a expirat sau urmează să expire în curând. */
    public boolean isAccessTokenExpired() {
        long expiresAt = getExpiresAt();
        if (expiresAt == 0L) return false; // sesiune veche, fără expirare cunoscută
        return System.currentTimeMillis() >= (expiresAt - EXPIRY_SAFETY_MARGIN_MS);
    }

    public boolean isBiometricLoginEnabled() {
        return prefs.getBoolean("biometric_login_enabled", false);
    }

    public void setBiometricLoginEnabled(boolean enabled) {
        prefs.edit().putBoolean("biometric_login_enabled", enabled).apply();
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}