package com.aosmi.youtubeaudio;
import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {
    private PermissionHelper permissionHelper;
    private EditText url_search;
    private TextView click_download;
    private TextView click_login;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        
        setContentView(R.layout.activity_main);

        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                close(1);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);

        UpdateSystemBar.updateTopPanelMargin(this);
        permissionHelper = new PermissionHelper(this);
        
        url_search = findViewById(R.id.url_search);
        click_download = findViewById(R.id.click_download);
        click_login = findViewById(R.id.click_login);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets imeAndBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(0, imeAndBars.top, 0, imeAndBars.bottom);
            return insets;
        });

        click_login.setOnClickListener(view -> {
            Intent intent = new Intent(this, MainActivity2.class);
            startActivity(intent);
        });

        click_download.setOnClickListener(view ->{
            String url = url_search.getText().toString().trim();
            if (url.isEmpty()) {
                Toast.makeText(this, "Вставьте ссылку на видео", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent serviceIntent = new Intent(this, DownloadService.class);
            serviceIntent.putExtra(DownloadService.EXTRA_URL, url);
            androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent);
            Toast.makeText(this, "Загрузка началась...", Toast.LENGTH_SHORT).show();
        });

        permission();
    }

    private void checkLoginState() {
        SharedPreferences prefs = SecureStorage.getPrefs(this);
        String cookies = prefs.getString("cookies", null);
        boolean isLoggedIn = (cookies != null && !cookies.isEmpty());
        if (isLoggedIn) {
            click_login.setVisibility(View.GONE);
            click_download.setVisibility(View.VISIBLE);
        } else {
            click_login.setVisibility(View.VISIBLE);
            click_download.setVisibility(View.GONE);
        }
    }

    private void permission(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU){
            permissionHelper.check(Manifest.permission.POST_NOTIFICATIONS, status -> {
                if (status != 1) {
                    Toast.makeText(this, "Уведомления отключены", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void close(int a){
        if (a == 1) {
            finishAffinity();
            overridePendingTransition(0, 0);
        } else {
            Toast.makeText(this, "Нет доступа!", Toast.LENGTH_SHORT).show();
            finish();
            overridePendingTransition(0, 0);
        }
    }

    @Override
    protected void onResume(){
        super.onResume();
        UpdateSystemBar.updateTopPanelMargin(MainActivity.this);
        checkLoginState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        getWindow().getDecorView().post(() -> {
            UpdateSystemBar.Update(MainActivity.this);
            UpdateSystemBar.updateTopPanelMargin(MainActivity.this);
        });
    }
              }
