package com.aosmi.youtubeaudio;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YouTubeExtractor {

    public static class VideoResult {
        public final String title;
        public final String downloadUrl;
        public final String userAgent;

        public VideoResult(String title, String downloadUrl, String userAgent) {
            this.title = title;
            this.downloadUrl = downloadUrl;
            this.userAgent = userAgent;
        }
    }

    public static String extractVideoId(String url) {
        if (url == null) return null;
        Pattern pattern = Pattern.compile(
                "(?:youtube\\.com/(?:watch\\?v=|shorts/|embed/|live/)|youtu\\.be/)([A-Za-z0-9_-]{11})"
        );
        Matcher matcher = pattern.matcher(url.trim());
        return matcher.find() ? matcher.group(1) : null;
    }

    public static VideoResult getDirectMp4Url(String youtubeUrl) throws Exception {
        return getDirectMp4Url(youtubeUrl, null);
    }

    public static VideoResult getDirectMp4Url(String youtubeUrl, String cookies) throws Exception {
        String videoId = extractVideoId(youtubeUrl);
        if (videoId == null) {
            throw new IllegalArgumentException("Некорректная ссылка на YouTube");
        }

        String[] clients = {"ANDROID_VR", "IOS", "ANDROID"};

        Exception lastError = null;
        for (String client : clients) {
            try {
                VideoResult res = requestPlayer(videoId, client, cookies);
                if (res != null) return res;
            } catch (Exception e) {
                android.util.Log.e("YouTubeExtractor", "Client " + client + " failed", e);
                lastError = e;
            }
        }
        throw (lastError != null) ? lastError : new Exception("Не удалось получить поток видео");
    }

    private static JSONObject buildClient(String name) throws Exception {
        JSONObject c = new JSONObject();
        c.put("clientName", name);
        c.put("hl", "en");
        c.put("gl", "US");
        switch (name) {
            case "ANDROID_VR":
                c.put("clientVersion", "1.65.10");
                c.put("deviceMake", "Oculus");
                c.put("deviceModel", "Quest 3");
                c.put("androidSdkVersion", 32);
                c.put("osName", "Android");
                c.put("osVersion", "12L");
                c.put("userAgent", "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip");
                break;
            case "IOS":
                c.put("clientVersion", "20.10.4");
                c.put("deviceMake", "Apple");
                c.put("deviceModel", "iPhone16,2");
                c.put("osName", "iPhone");
                c.put("osVersion", "18.3.2.22D82");
                c.put("userAgent", "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X)");
                break;
            default: 
                c.put("clientVersion", "20.10.38");
                c.put("androidSdkVersion", 30);
                c.put("osName", "Android");
                c.put("osVersion", "11");
                c.put("userAgent", "com.google.android.youtube/20.10.38 (Linux; U; Android 11) gzip");
        }
        return c;
    }

    private static VideoResult requestPlayer(String videoId, String clientName, String cookies) throws Exception {
        JSONObject clientObj = buildClient(clientName);
        String userAgent = clientObj.getString("userAgent");
        int clientId = "ANDROID_VR".equals(clientName) ? 28 : "IOS".equals(clientName) ? 5 : 3;

        URL url = new URL("https://www.youtube.com/youtubei/v1/player?prettyPrint=false");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("User-Agent", userAgent);
        conn.setRequestProperty("Origin", "https://www.youtube.com");
        conn.setRequestProperty("X-YouTube-Client-Name", String.valueOf(clientId));
        conn.setRequestProperty("X-YouTube-Client-Version", clientObj.getString("clientVersion"));

        JSONObject contextObj = new JSONObject().put("client", clientObj);

        JSONObject payload = new JSONObject();
        payload.put("context", contextObj);
        payload.put("videoId", videoId);
        payload.put("contentCheckOk", true);
        payload.put("racyCheckOk", true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        if (code != 200) {
            String body = "";
            InputStream es = conn.getErrorStream();
            if (es != null) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(es, StandardCharsets.UTF_8))) {
                    StringBuilder eb = new StringBuilder();
                    String l;
                    while ((l = br.readLine()) != null) eb.append(l);
                    body = eb.toString();
                }
            }
            throw new Exception("HTTP " + code + ": " + body);
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }

        JSONObject json = new JSONObject(sb.toString());
        JSONObject playability = json.optJSONObject("playabilityStatus");
        if (playability != null && !"OK".equals(playability.optString("status"))) {
            throw new Exception("Видео недоступно: " + playability.optString("reason", "ограничение YouTube"));
        }

        String title = "youtube_video_" + videoId;
        JSONObject details = json.optJSONObject("videoDetails");
        if (details != null) {
            title = details.optString("title", title).replaceAll("[\\\\/:*?\"<>|]", "_");
        }

        JSONObject streamingData = json.optJSONObject("streamingData");
        if (streamingData == null) return null;

        JSONArray formats = streamingData.optJSONArray("formats");
        if (formats == null) return null;

        for (int i = 0; i < formats.length(); i++) {
            JSONObject fmt = formats.getJSONObject(i);
            String mimeType = fmt.optString("mimeType", "");
            String streamUrl = fmt.optString("url", "");
            if (mimeType.startsWith("video/mp4") && !streamUrl.isEmpty()) {
                return new VideoResult(title, streamUrl, userAgent);
            }
        }
        return null;
    }

    public static String extractCookieValue(String cookies, String key) {
        if (cookies == null) return null;
        for (String part : cookies.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(key + "=")) {
                return trimmed.substring(key.length() + 1);
            }
        }
        return null;
    }

    public static String buildSapisidHash(String cookies) {
        String sapisid = extractCookieValue(cookies, "SAPISID");
        if (sapisid == null) {
            sapisid = extractCookieValue(cookies, "__Secure-3PAPISID");
        }
        if (sapisid == null) return null;

        try {
            long timestamp = System.currentTimeMillis() / 1000L;
            String origin = "https://www.youtube.com";
            String input = timestamp + " " + sapisid + " " + origin;

            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return "SAPISIDHASH " + timestamp + "_" + hex;
        } catch (Exception e) {
            return null;
        }
    }
}
