package com.aosmi.youtubeaudio;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadService extends Service {
public static final String EXTRA_URL = "extra_youtube_url";
private static final String CHANNEL_ID = "yt_download_channel";
private static final int NOTIFICATION_ID = 1001;

private ExecutorService executor;
private NotificationManager notificationManager;

@Override
public void onCreate() {
super.onCreate();
executor = Executors.newSingleThreadExecutor();
notificationManager = getSystemService(NotificationManager.class);
createChannel();
}

@Override
public int onStartCommand(Intent intent, int flags, int startId) {
String youtubeUrl = intent != null ? intent.getStringExtra(EXTRA_URL) : null;

Notification startNotification = buildNotification("Подготовка к загрузке...", 0, true);
int serviceType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC : 0;
ServiceCompat.startForeground(this, NOTIFICATION_ID, startNotification, serviceType);

if (youtubeUrl == null || youtubeUrl.trim().isEmpty()) {
showToast("Пустая ссылка");
stopSelf(startId);
return START_NOT_STICKY;
}

executor.execute(() -> {
File tempVideoFile = null;
File tempAudioFile = null;
try {
SharedPreferences prefs = SecureStorage.getPrefs(DownloadService.this);
String cookies = prefs.getString("cookies", null);
YouTubeExtractor.VideoResult video = YouTubeExtractor.getDirectMp4Url(youtubeUrl, cookies);
// не сразу скачивается
tempVideoFile = new File(getCacheDir(), "temp_yt_" + System.currentTimeMillis() + ".mp4");
downloadVideoToTempFile(video, cookies, tempVideoFile);
updateNotification("Извлечение аудиодорожки...", 100, true);
tempAudioFile = new File(getCacheDir(), "temp_audio_" + System.currentTimeMillis() + ".m4a");
extractAudioTrack(tempVideoFile, tempAudioFile);
updateNotification("Сохранение в папку Музыка...", 100, true);
saveAudioToMusicFolder(tempAudioFile, video.title);

showToast("Аудио сохранено в папку Музыка: " + video.title + ".m4a");

} catch (Exception e) {
android.util.Log.e("DownloadService", "Error during processing", e);
showToast("Ошибка: " + e.getMessage());
} finally {
if (tempVideoFile != null && tempVideoFile.exists()) tempVideoFile.delete();
if (tempAudioFile != null && tempAudioFile.exists()) tempAudioFile.delete();
stopSelf(startId);
}
});
return START_NOT_STICKY;
}
private void downloadVideoToTempFile(YouTubeExtractor.VideoResult video, String cookies, File tempFile) throws Exception {
URL url = new URL(video.downloadUrl);
HttpURLConnection conn = (HttpURLConnection) url.openConnection();
conn.setRequestProperty("User-Agent", video.userAgent);
conn.setRequestProperty("Referer", "https://www.youtube.com/");
conn.setRequestProperty("Origin", "https://www.youtube.com");
if (cookies != null && !cookies.isEmpty()) conn.setRequestProperty("Cookie", cookies);

conn.setConnectTimeout(15000);
conn.setReadTimeout(30000);
conn.connect();

long totalBytes = conn.getContentLengthLong();
try (InputStream in = conn.getInputStream();
OutputStream out = new FileOutputStream(tempFile)) {

byte[] buffer = new byte[8192];
int bytesRead;
long downloaded = 0;
int lastProgress = -1;

while ((bytesRead = in.read(buffer)) != -1) {
out.write(buffer, 0, bytesRead);
downloaded += bytesRead;

if (totalBytes > 0) {
int progress = (int) ((downloaded * 100) / totalBytes);
if (progress != lastProgress) {
lastProgress = progress;
updateNotification("Скачивание видео: " + video.title, progress, false);
}
}
}
} finally {
conn.disconnect();
}
}
private void extractAudioTrack(File videoFile, File outputFile) throws Exception {
MediaExtractor extractor = new MediaExtractor();
MediaMuxer muxer = null;

try {
extractor.setDataSource(videoFile.getAbsolutePath());
int audioTrackIndex = -1;
for (int i = 0; i < extractor.getTrackCount(); i++) {
MediaFormat format = extractor.getTrackFormat(i);
String mime = format.getString(MediaFormat.KEY_MIME);
if (mime != null && mime.startsWith("audio/")) {
audioTrackIndex = i;
break;
}
}

if (audioTrackIndex == -1) {
throw new Exception("В этом видео нет аудиодорожки!");
}

extractor.selectTrack(audioTrackIndex);
MediaFormat trackFormat = extractor.getTrackFormat(audioTrackIndex);

muxer = new MediaMuxer(outputFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
int writeAudioIndex = muxer.addTrack(trackFormat);
muxer.start();

MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
boolean isRunning = true;

while (isRunning) {
bufferInfo.offset = 0;
bufferInfo.size = extractor.readSampleData(buffer, 0);

if (bufferInfo.size < 0) {
isRunning = false;
bufferInfo.size = 0;
} else {
bufferInfo.presentationTimeUs = extractor.getSampleTime();
bufferInfo.flags = MediaCodec.BUFFER_FLAG_SYNC_FRAME;
muxer.writeSampleData(writeAudioIndex, buffer, bufferInfo);
extractor.advance();
}
}
} finally {
if (muxer != null) {
try { muxer.stop(); muxer.release(); } catch (Exception ignored) {}
}
if (extractor != null) {
extractor.release();
}
}
}
private void saveAudioToMusicFolder(File audioFile, String title) throws Exception {
ContentResolver resolver = getContentResolver();
ContentValues values = new ContentValues();

String safeTitle = title.replaceAll("[\\\\/:*?\"<>|]", "_");
values.put(MediaStore.Audio.Media.DISPLAY_NAME, safeTitle + ".m4a");
values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/m4a");

if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
values.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/YouTubeAudio");
values.put(MediaStore.Audio.Media.IS_PENDING, 1);
}

Uri uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
if (uri == null) throw new Exception("Не удалось создать файл в папке Музыка");

try (OutputStream out = resolver.openOutputStream(uri);
InputStream in = new FileInputStream(audioFile)) {
byte[] buffer = new byte[4096];
int read;
while ((read = in.read(buffer)) != -1) {
out.write(buffer, 0, read);
}
}

if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
ContentValues doneValues = new ContentValues();
doneValues.put(MediaStore.Audio.Media.IS_PENDING, 0);
resolver.update(uri, doneValues, null, null);
}
}
private void updateNotification(String text, int progress, boolean indeterminate) {
notificationManager.notify(NOTIFICATION_ID, buildNotification(text, progress, indeterminate));
}

private Notification buildNotification(String text, int progress, boolean indeterminate) {
return new NotificationCompat.Builder(this, CHANNEL_ID)
.setContentTitle("YouTube to Audio")
.setContentText(text)
.setSmallIcon(android.R.drawable.stat_sys_download)
.setOngoing(true)
.setOnlyAlertOnce(true)
.setProgress(100, progress, indeterminate)
.build();
}

private void createChannel() {
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
NotificationChannel channel = new NotificationChannel(
CHANNEL_ID,
"Конвертация видео в аудио",
NotificationManager.IMPORTANCE_LOW
);
notificationManager.createNotificationChannel(channel);
}
}

private void showToast(String msg) {
new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show());
}

@Override
public void onDestroy() {
super.onDestroy();
if (executor != null) executor.shutdownNow();
}

@Nullable
@Override
public IBinder onBind(Intent intent) {
return null;
}
}