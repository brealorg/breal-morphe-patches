/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */
package app.morphe.extension.boostforreddit.upload;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.rubenmayayo.reddit.models.imgur.Upload;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;

import de.e;
import de.f;

/** ImgBB editor uploader using only Android/JDK network ABI. */
public final class ImgBbUploader implements de.b {
    public static final String CONTRACT_MARKER =
            "MORPHE_BOOST_IMGBB_EDITOR_UPLOAD_ISSUE66_V1";
    public static final String FRAMEWORK_MULTIPART_MARKER =
            "MORPHE_BOOST_IMGBB_FRAMEWORK_MULTIPART_ISSUE66_V2";

    private static final String API_URL = "https://api.imgbb.com/1/upload";
    private static final long MAX_IMAGE_BYTES = 32L * 1024L * 1024L;
    private static final int BUFFER_SIZE = 16 * 1024;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    @Override
    public void a(String title, List<Upload> uploads, f.b progress, e listener) {
        if (uploads == null || uploads.isEmpty()) {
            failure(listener, new IOException("No image selected"), "No image selected.");
            return;
        }
        c(uploads.get(0), progress, listener);
    }

    @Override
    public void b(Upload upload, f.b progress, e listener) {
        c(upload, progress, listener);
    }

    @Override
    public void c(Upload upload, f.b progress, e listener) {
        File image = upload == null ? null : upload.image;
        if (image == null || !image.isFile()) {
            failure(listener, new IOException("Selected image is unavailable"), "The selected image is unavailable.");
            return;
        }
        if (image.length() > MAX_IMAGE_BYTES) {
            failure(listener, new IOException("Image exceeds ImgBB limit"), "ImgBB supports images up to 32 MB.");
            return;
        }
        String apiKey = ExternalImageUploadSettings.getImgBbApiKey();
        if (TextUtils.isEmpty(apiKey)) {
            failure(listener, new IOException("ImgBB API key is missing"), "Add an ImgBB API key in Morphe settings.");
            return;
        }
        String mimeType = URLConnection.guessContentTypeFromName(image.getName());
        if (TextUtils.isEmpty(mimeType)) mimeType = "application/octet-stream";
        final String selectedMimeType = mimeType;
        new Thread(
                () -> upload(image, selectedMimeType, apiKey, progress, listener),
                "Morphe-ImgBB-upload"
        ).start();
    }

    private static void upload(File image, String mimeType, String apiKey, f.b progress, e listener) {
        HttpURLConnection connection = null;
        try {
            String endpoint = Uri.parse(API_URL).buildUpon()
                    .appendQueryParameter("key", apiKey).build().toString();
            String boundary = "----MorpheImgBB" + Long.toHexString(System.nanoTime());
            byte[] prefix = multipartPrefix(boundary, safeFilename(image.getName()), mimeType);
            byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
            long contentLength = prefix.length + image.length() + suffix.length;

            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(30_000);
            connection.setReadTimeout(60_000);
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(true);
            connection.setFixedLengthStreamingMode(contentLength);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            reportProgress(progress, 0);
            try (BufferedOutputStream output = new BufferedOutputStream(connection.getOutputStream());
                 FileInputStream input = new FileInputStream(image)) {
                output.write(prefix);
                byte[] buffer = new byte[BUFFER_SIZE];
                long written = 0L;
                int lastPercent = -1;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    written += read;
                    int percent = image.length() <= 0L ? 99 : (int) Math.min(99L, (written * 100L) / image.length());
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        reportProgress(progress, percent);
                    }
                }
                output.write(suffix);
                output.flush();
            }

            int statusCode = connection.getResponseCode();
            String json = readUtf8(statusCode >= 200 && statusCode < 400
                    ? connection.getInputStream() : connection.getErrorStream());
            JSONObject root = TextUtils.isEmpty(json) ? new JSONObject() : new JSONObject(json);
            JSONObject data = root.optJSONObject("data");
            String imageUrl = data == null ? "" : data.optString("url", "");
            boolean successful = statusCode >= 200 && statusCode < 300
                    && root.optBoolean("success", false) && imageUrl.startsWith("https://");
            if (!successful) {
                String message = errorMessage(root, statusCode);
                failure(listener, new IOException(message), message);
                return;
            }
            reportProgress(progress, 100);
            success(listener, imageUrl);
        } catch (Exception exception) {
            failure(listener, exception, networkErrorMessage(exception));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static byte[] multipartPrefix(String boundary, String filename, String mimeType) {
        String value = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"image\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + mimeType + "\r\n\r\n";
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String safeFilename(String value) {
        if (TextUtils.isEmpty(value)) return "image";
        return value.replace("\\", "_").replace("\"", "'").replace("\r", "_").replace("\n", "_");
    }

    private static String readUtf8(InputStream stream) throws IOException {
        if (stream == null) return "";
        try (BufferedInputStream input = new BufferedInputStream(stream);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String errorMessage(JSONObject root, int statusCode) {
        JSONObject error = root.optJSONObject("error");
        String message = error == null ? "" : error.optString("message", "");
        if (TextUtils.isEmpty(message)) message = root.optString("status_txt", "");
        if (TextUtils.isEmpty(message)) message = "ImgBB rejected the upload (HTTP " + statusCode + ").";
        return message;
    }

    private static String networkErrorMessage(Exception exception) {
        String detail = exception.getMessage();
        if (TextUtils.isEmpty(detail)) detail = exception.getClass().getSimpleName();
        return "ImgBB upload failed: " + detail;
    }

    private static void reportProgress(f.b progress, int percent) {
        if (progress != null) MAIN.post(() -> progress.a(percent));
    }

    private static void success(e listener, String imageUrl) {
        if (listener != null) MAIN.post(() -> listener.onSuccess(imageUrl));
    }

    private static void failure(e listener, Exception exception, String message) {
        if (listener != null) MAIN.post(() -> listener.c(exception, message));
    }
}
