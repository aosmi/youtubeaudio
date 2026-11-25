package com.aosmi.youtubeaudio;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

public class SecureStorage {
    public static SharedPreferences getPrefs(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();

            return EncryptedSharedPreferences.create(context, "yt_auth_secure", masterKey, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            android.util.Log.e("SecureStorage", "Failed to init EncryptedSharedPreferences", e);
            return context.getSharedPreferences("yt_auth_fallback", Context.MODE_PRIVATE);
        }
    }
}
