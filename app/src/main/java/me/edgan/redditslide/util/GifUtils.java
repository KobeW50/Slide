package me.edgan.redditslide.util;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource;
import com.google.android.exoplayer2.source.dash.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;
import com.google.android.exoplayer2.source.dash.manifest.Representation;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSourceInputStream;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.mp4parser.Container;
import org.mp4parser.muxer.Movie;
import org.mp4parser.muxer.Track;
import org.mp4parser.muxer.builder.DefaultMp4Builder;
import org.mp4parser.muxer.container.mp4.MovieCreator;
import org.mp4parser.muxer.tracks.ClippedTrack;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.edgan.redditslide.Activities.MediaView;
import me.edgan.redditslide.Activities.Website;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.Views.ExoVideoView;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import androidx.documentfile.provider.DocumentFile;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * GIF handling utilities
 */
public class GifUtils {
    /**
     * Create a notification that opens a newly-saved GIF
     *
     * @param docFile File referencing the GIF
     * @param c
     */
    public static void doNotifGif(DocumentFile docFile, Activity c) {
        try {
            final Intent shareIntent = new Intent(Intent.ACTION_VIEW);
            shareIntent.setDataAndType(docFile.getUri(), "video/mp4");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            PendingIntent contentIntent = PendingIntent.getActivity(c, 0, shareIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT);

            Notification notif = new NotificationCompat.Builder(c, Reddit.CHANNEL_IMG)
                    .setContentTitle(c.getString(R.string.gif_saved))
                    .setSmallIcon(R.drawable.ic_save)
                    .setContentIntent(contentIntent)
                    .build();

            NotificationManager mNotificationManager = ContextCompat.getSystemService(c, NotificationManager.class);
            if (mNotificationManager != null) {
                mNotificationManager.notify((int) System.currentTimeMillis(), notif);
            }
        } catch (Exception e) {
            Log.e("GifUtils", "Error showing notification", e);
        }
    }

    private static void showErrorDialog(final Activity a) {
        DialogUtil.showErrorDialog((MediaView) a);
    }

    private static void showFirstDialog(final Activity a) {
        DialogUtil.showFirstDialog((MediaView) a);
    }

    public static void downloadGif(String url, GifDownloadCallback callback, Context context, String submissionTitle) {
        new DownloadGifTask(url, callback, context, submissionTitle).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static void downloadGif(String url, GifDownloadCallback callback, Context context) {
        new DownloadGifTask(url, callback, context).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private static class DownloadGifTask extends AsyncTask<Void, Void, File> {
        private String url;
        private GifDownloadCallback callback;
        private Context context;
        private Exception exception;
        private String submissionTitle;

        DownloadGifTask(String url, GifDownloadCallback callback, Context context) {
            this.url = url;
            this.callback = callback;
            this.context = context.getApplicationContext();
            this.submissionTitle = null;
        }

        DownloadGifTask(String url, GifDownloadCallback callback, Context context, String submissionTitle) {
            this.url = url;
            this.callback = callback;
            this.context = context.getApplicationContext();
            this.submissionTitle = submissionTitle;
        }

        @Override
        protected File doInBackground(Void... voids) {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(url).build();
            Response response = null;
            try {
                response = client.newCall(request).execute();
                if (!response.isSuccessful()) {
                    throw new Exception("Failed to download GIF: " + response);
                }

                // Create unique filename based on URL
                String fileName;
                if (submissionTitle != null && !submissionTitle.trim().isEmpty()) {
                    fileName = FileUtil.getValidFileName(submissionTitle, "", ".gif");
                } else {
                    fileName = FileUtil.getValidFileName("redditslide", "gif_", ".gif");
                }
                File gifFile = new File(context.getCacheDir(), fileName);

                InputStream inputStream = response.body().byteStream();
                OutputStream outputStream = new FileOutputStream(gifFile);

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                outputStream.close();
                inputStream.close();

                return gifFile;
            } catch (Exception e) {
                Log.e("EmoteDebug", "Error downloading GIF", e);
                exception = e;
                return null;
            } finally {
                if (response != null) {
                    response.close();
                }
            }
        }

        @Override
        protected void onPostExecute(File gifFile) {
            if (gifFile != null) {
                callback.onGifDownloaded(gifFile);
            } else {
                callback.onGifDownloadFailed(exception != null ? exception : new Exception("Unknown error"));
            }
        }
    }

    public interface GifDownloadCallback {
        void onGifDownloaded(File gifFile);
        void onGifDownloadFailed(Exception e);
    }

    public static void cacheSaveGif(Uri uri, Activity activity, String subreddit, String submissionTitle, boolean save) {
        if (save) {
            try {
                Toast.makeText(activity, activity.getString(R.string.mediaview_notif_video), Toast.LENGTH_SHORT).show();
            } catch (Exception ignored) {
            }
        }

        Uri storageUri = StorageUtil.getStorageUri(activity);
        if (storageUri == null || !StorageUtil.hasStorageAccess(activity)) {
            showFirstDialog(activity);
        } else {
            new AsyncTask<Void, Integer, DocumentFile>() {
                NotificationManager notifMgr = ContextCompat.getSystemService(activity, NotificationManager.class);
                Exception saveError;

                @Override
                protected DocumentFile doInBackground(Void... voids) {
                    InputStream in = null;
                    OutputStream out = null;
                    try {
                        Log.d("GifUtils", "Starting save process for URI: " + uri);
                        DocumentFile parentDir = DocumentFile.fromTreeUri(activity, storageUri);
                        if (parentDir == null) {
                            saveError = new Exception("Could not access storage directory");
                            return null;
                        }

                        // Create subreddit subfolder if needed
                        if (SettingValues.imageSubfolders && !subreddit.isEmpty()) {
                            DocumentFile subFolder = parentDir.findFile(subreddit);
                            if (subFolder == null) {
                                subFolder = parentDir.createDirectory(subreddit);
                            }
                            if (subFolder == null) {
                                saveError = new Exception("Could not create subreddit folder");
                                return null;
                            }
                            parentDir = subFolder;
                        }

                        // Create output file with .mp4 extension
                        String fileName = FileUtil.getValidFileName(submissionTitle, "", ".mp4");
                        Log.d("GifUtils", "Creating output file: " + fileName);
                        DocumentFile outDocFile = parentDir.createFile("video/mp4", fileName);
                        if (outDocFile == null) {
                            saveError = new Exception("Could not create output file");
                            return null;
                        }

                        String urlStr = uri.toString();
                        if (urlStr.contains("v.redd.it") && urlStr.contains("DASHPlaylist.mpd")) {
                            // Extract the base URL (everything before DASHPlaylist.mpd)
                            String baseUrl = urlStr.substring(0, urlStr.indexOf("DASHPlaylist.mpd"));

                            // Download the manifest
                            OkHttpClient client = Reddit.client;
                            Request request = new Request.Builder().url(urlStr).build();
                            Response response = client.newCall(request).execute();

                            if (!response.isSuccessful()) {
                                saveError = new Exception("Failed to download manifest");
                                return null;
                            }

                            String manifestContent = response.body().string();
                            response.close();

                            // Find the highest quality video URL
                            String highestQualityUrl = null;
                            int maxQuality = 0;

                            // Look for DASH_XXX.mp4 in the manifest where XXX is the quality
                            Pattern pattern = Pattern.compile("DASH_(\\d+)\\.mp4");
                            Matcher matcher = pattern.matcher(manifestContent);

                            while (matcher.find()) {
                                int quality = Integer.parseInt(matcher.group(1));
                                if (quality > maxQuality) {
                                    maxQuality = quality;
                                    highestQualityUrl = baseUrl + "DASH_" + quality + ".mp4";
                                }
                            }

                            if (highestQualityUrl == null) {
                                saveError = new Exception("Could not find video URL in manifest");
                                return null;
                            }

                            urlStr = highestQualityUrl;
                        }

                        // Download and save the video
                        Request videoRequest = new Request.Builder().url(urlStr).build();
                        Response videoResponse = Reddit.client.newCall(videoRequest).execute();

                        if (!videoResponse.isSuccessful()) {
                            saveError = new Exception("Failed to download video: " + videoResponse);
                            return null;
                        }

                        in = videoResponse.body().byteStream();
                        out = activity.getContentResolver().openOutputStream(outDocFile.getUri());

                        byte[] buffer = new byte[8192];
                        long total = 0;
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                            total += read;
                        }

                        if (total == 0) {
                            saveError = new Exception("Downloaded file is empty");
                            return null;
                        }

                        return outDocFile;

                    } catch (Exception e) {
                        Log.e("GifUtils", "Error saving video", e);
                        saveError = e;
                        return null;
                    } finally {
                        try {
                            if (in != null) in.close();
                        } catch (IOException e) {
                            Log.e("GifUtils", "Error closing input stream", e);
                        }
                        try {
                            if (out != null) out.close();
                        } catch (IOException e) {
                            Log.e("GifUtils", "Error closing output stream", e);
                        }
                    }
                }

                @Override
                protected void onPostExecute(DocumentFile result) {
                    if (save) {
                        notifMgr.cancel(1);
                        if (result != null) {
                            doNotifGif(result, activity);
                        } else {
                            Log.e("GifUtils", "Save failed: " + (saveError != null ? saveError.getMessage() : "Unknown error"));
                            showErrorDialog(activity);
                        }
                    }
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    public static class AsyncLoadGif extends AsyncTask<String, Void, Uri> {

        private Activity c;
        private ExoVideoView video;
        private ProgressBar progressBar;
        private View placeholder;
        private View gifSave;
        private boolean closeIfNull;
        private Runnable doOnClick;
        private boolean autostart;
        public String subreddit;
        public String submissionTitle;

        private TextView size;

        public AsyncLoadGif(@NonNull Activity c, @NonNull ExoVideoView video,
                @Nullable ProgressBar p, @Nullable View placeholder, @Nullable Runnable gifSave,
                boolean closeIfNull, boolean autostart, String subreddit) {
            this.c = c;
            this.subreddit = subreddit;
            this.video = video;
            this.progressBar = p;
            this.closeIfNull = closeIfNull;
            this.placeholder = placeholder;
            this.doOnClick = gifSave;
            this.autostart = autostart;
        }

        public AsyncLoadGif(@NonNull Activity c, @NonNull ExoVideoView video,
                @Nullable ProgressBar p, @Nullable View placeholder, @Nullable Runnable gifSave,
                boolean closeIfNull, boolean autostart, TextView size, String subreddit, String submissionTitle) {
            this.c = c;
            this.video = video;
            this.subreddit = subreddit;
            this.progressBar = p;
            this.closeIfNull = closeIfNull;
            this.placeholder = placeholder;
            this.doOnClick = gifSave;
            this.autostart = autostart;
            this.size = size;
            this.submissionTitle = submissionTitle;
        }

        public void onError() {

        }

        public AsyncLoadGif(@NonNull Activity c, @NonNull ExoVideoView video,
                @Nullable ProgressBar p, @Nullable View placeholder, boolean closeIfNull,
                boolean autostart, String subreddit) {
            this.c = c;
            this.video = video;
            this.subreddit = subreddit;
            this.progressBar = p;
            this.closeIfNull = closeIfNull;
            this.placeholder = placeholder;
            this.autostart = autostart;
        }

        public void cancel() {
            LogUtil.v("cancelling");
            video.stop();
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            gson = new Gson();
        }

        Gson gson;

        public enum VideoType {
            IMGUR, STREAMABLE, GFYCAT, DIRECT, OTHER, VREDDIT, REDDIT_GALLERY;

            public boolean shouldLoadPreview() {
                return this == OTHER;
            }
        }

        /**
         * Format a video URL correctly and strip unnecessary parts
         *
         * @param s URL to format
         * @return Formatted URL
         */
        public static String formatUrl(String s) {
            if (s.endsWith("v") && !s.contains("streamable.com")) {
                s = s.substring(0, s.length() - 1);
            } else if (s.contains("gfycat") && (!s.contains("mp4") && !s.contains("webm"))) {
                s = s.replace("-size_restricted", "");
                s = s.replace(".gif", "");
            }
            if ((s.contains(".webm") || s.contains(".gif")) && !s.contains(".gifv") && s.contains("imgur.com")) {
                s = s.replace(".gif", ".mp4");
                s = s.replace(".webm", ".mp4");
            }
            if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
            if (s.endsWith("?r")) s = s.substring(0, s.length() - 2);
            if (s.contains("v.redd.it") && !s.contains("DASHPlaylist")) {
                if (s.contains("DASH")) {
                    s = s.substring(0, s.indexOf("DASH"));
                }
                if (s.endsWith("/")) {
                    s = s.substring(0, s.length() - 1);
                }

                s += "/DASHPlaylist.mpd";
            }

            return s;
        }

        /**
         * Identifies the type of a video URL
         *
         * @param url URL to identify the type of
         * @return The type of video
         */
        public static VideoType getVideoType(String url) {
            String realURL = url.toLowerCase(Locale.ENGLISH);

            if (realURL.contains("i.redd.it/gallery/")) {
                return VideoType.REDDIT_GALLERY;
            }

            if (realURL.contains("v.redd.it")) {
                return VideoType.VREDDIT;
            }

            if (realURL.contains(".mp4") || realURL.contains("webm") || realURL.contains("redditmedia.com")
                    || realURL.contains("preview.redd.it")) {
                return VideoType.DIRECT;
            }

            if (realURL.contains("gfycat") && !realURL.contains("mp4")) return VideoType.GFYCAT;

            if (realURL.contains("redgifs") && !realURL.contains("mp4")) return VideoType.GFYCAT;

            if (realURL.contains("imgur.com")) return VideoType.IMGUR;

            if (realURL.contains("streamable.com")) return VideoType.STREAMABLE;

            return VideoType.OTHER;
        }

        public static Map<String, String> makeHeaderMap(String domain){
          Map<String, String> map = new HashMap<>();
          map.put("Host", domain);
          map.put("Sec-Fetch-Dest", "empty");
          map.put("Sec-Fetch-Mode", "cors");
          map.put("Sec-Fetch-Site", "same-origin");
          map.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; rv:107.0) Gecko/20100101 Firefox/107.0");
          return map;
        }

        /**
         * Get an API response for a given host and gfy name
         *
         * @param host the host to send the req to
         * @param name the name of the gfy
         * @return the result
         */
        JsonObject getApiResponse(String host, String name){
          String domain = "api." + host + ".com";
          String gfycatUrl = "https://" + domain + "/v1/gfycats" + name;

          return HttpUtil.getJsonObject(client, gson, gfycatUrl, makeHeaderMap(domain));
        }

        /**
         * Get the correct mp4/mobile url from a given result JsonObject
         *
         * @param result the result to check
         * @return the video url
         */
        String getUrlFromApi(JsonObject result){
          if (!SettingValues.hqgif && result.getAsJsonObject("gfyItem").has("mobileUrl")) {
            return result.getAsJsonObject("gfyItem").get("mobileUrl").getAsString();
          } else {
            return result.getAsJsonObject("gfyItem").get("mp4Url").getAsString();
          }
        }


        OkHttpClient client = Reddit.client;

        /**
         * Load the correct URL for a gfycat gif
         *
         * @param name    Name of the gfycat gif
         * @param fullUrl full URL to the gfycat
         * @param gson
         * @return Correct URL
         */
        Uri loadGfycat(String name, String fullUrl, Gson gson) {
            showProgressBar(c, progressBar, true);
            String host = "gfycat";
            if (fullUrl.contains("redgifs")) {
                host = "redgifs";
            }
            if (!name.startsWith("/")) name = "/" + name;
            if (name.contains("-")) {
                name = name.split("-")[0];
            }
            final JsonObject result = getApiResponse(host, name);
            if (result == null || result.get("gfyItem") == null || result.getAsJsonObject("gfyItem")
                    .get("mp4Url")
                    .isJsonNull()) {
                // If the result null, the gfycat link may be redirecting to gifdeliverynetwork which is powered by redgifs.
                // Try getting the redirected url from gfycat and check if redirected url is gifdeliverynetwork and if it is,
                // we fetch the actual .mp4/.webm url from the redgifs api
                if (result == null) {
                    try {
                        URL newUrl = new URL(fullUrl);
                        HttpURLConnection ucon = (HttpURLConnection) newUrl.openConnection();
                        ucon.setInstanceFollowRedirects(false);
                        String secondURL = new URL(ucon.getHeaderField("location")).toString();
                        if (secondURL.contains("gifdeliverynetwork")){
                            return Uri.parse(getUrlFromApi(getApiResponse("redgifs", name.toLowerCase())));
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                onError();
                if (closeIfNull) {
                    c.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                new AlertDialog.Builder(c)
                                        .setTitle(R.string.gif_err_title)
                                        .setMessage(R.string.gif_err_msg)
                                        .setCancelable(false)
                                        .setPositiveButton(R.string.btn_ok, (dialog, which) ->
                                                c.finish())
                                        .setNeutralButton(R.string.open_externally, (dialog, which) -> {
                                            LinkUtil.openExternally(fullUrl);
                                            c.finish();
                                        })
                                        .create()
                                        .show();
                            } catch (Exception ignored) {
                            }
                        }
                    });
                }

                return null;
            }

            return Uri.parse(getUrlFromApi(result));
        }

        // Handles failures of loading a DASH mp4 or muxing a Reddit video
        private void catchVRedditFailure(Exception e, String url) {
            LogUtil.e(e,
                    "Error loading URL " + url); // Most likely is an image, not a gif!
            if (c instanceof MediaView && url.contains("imgur.com") && url.endsWith(
                    ".mp4")) {
                c.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        (c).startActivity(new Intent(c, MediaView.class).putExtra(
                                MediaView.EXTRA_URL, url.replace(".mp4",
                                        ".png"))); // Link is likely an image and not a gif
                        (c).finish();
                    }
                });
            } else {
                openWebsite(url);
            }
        }

        @Override
        protected Uri doInBackground(String... sub) {
            MediaView.didLoadGif = false;
            Gson gson = new Gson();
            final String url = formatUrl(sub[0]);
            VideoType videoType = getVideoType(url);
            LogUtil.v(url + ", VideoType: " + videoType);
            if (size != null) {
                getRemoteFileSize(url, client, size, c);
            }
            switch (videoType) {
                case VREDDIT:
                    return Uri.parse(url);
                case GFYCAT:
                    String name = url.substring(url.lastIndexOf("/"));
                    String gfycatUrl = "https://api.gfycat.com/v1/gfycats" + name;

                    // Check if resolved gfycat link is gifdeliverynetwork. If it is gifdeliverynetwork, open the link externally
                    try {
                        Uri uri = loadGfycat(name, url, gson);
                        if(uri.toString().contains("gifdeliverynetwork")){
                            openWebsite(url);
                            return null;
                        } else return uri;
                    } catch (Exception e) {
                        LogUtil.e(e, "Error loading gfycat video url = ["
                                + url
                                + "] gfycatUrl = ["
                                + gfycatUrl
                                + "]");
                    }
                    break;
                case REDDIT_GALLERY:
                    return Uri.parse(url);
                case DIRECT:
                case IMGUR:
                    try {
                        return Uri.parse(url);
                    } catch (Exception e) {
                        LogUtil.e(e,
                                "Error loading URL " + url); // Most likely is an image, not a gif!
                        if (c instanceof MediaView && url.contains("imgur.com") && url.endsWith(
                                ".mp4")) {
                            c.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    (c).startActivity(new Intent(c, MediaView.class).putExtra(
                                            MediaView.EXTRA_URL, url.replace(".mp4",
                                                    ".png"))); // Link is likely an image and not a gif
                                    (c).finish();
                                }
                            });
                        } else {
                            openWebsite(url);
                        }
                    }
                    break;
                case STREAMABLE:
                    String hash = url.substring(url.lastIndexOf("/") + 1);
                    String streamableUrl = "https://api.streamable.com/videos/" + hash;
                    LogUtil.v(streamableUrl);
                    try {
                        final JsonObject result =
                                HttpUtil.getJsonObject(client, gson, streamableUrl);
                        String obj;
                        if (result == null
                                || result.get("files") == null
                                || !(result.getAsJsonObject("files").has("mp4")
                                || result.getAsJsonObject("files").has("mp4-mobile"))) {

                            onError();
                            if (closeIfNull) {
                                c.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        new AlertDialog.Builder(c)
                                                .setTitle(R.string.error_video_not_found)
                                                .setMessage(R.string.error_video_message)
                                                .setCancelable(false)
                                                .setPositiveButton(R.string.btn_ok, (dialog, which) ->
                                                        c.finish())
                                                .create()
                                                .show();
                                    }
                                });
                            }
                        } else {
                            if (result.getAsJsonObject()
                                    .get("files")
                                    .getAsJsonObject()
                                    .has("mp4-mobile") && !result.getAsJsonObject()
                                    .get("files")
                                    .getAsJsonObject()
                                    .get("mp4-mobile")
                                    .getAsJsonObject()
                                    .get("url")
                                    .getAsString()
                                    .isEmpty()) {
                                obj = result.getAsJsonObject()
                                        .get("files")
                                        .getAsJsonObject()
                                        .get("mp4-mobile")
                                        .getAsJsonObject()
                                        .get("url")
                                        .getAsString();
                            } else {
                                obj = result.getAsJsonObject()
                                        .get("files")
                                        .getAsJsonObject()
                                        .get("mp4")
                                        .getAsJsonObject()
                                        .get("url")
                                        .getAsString();
                            }
                            return Uri.parse(obj);
                        }
                    } catch (Exception e) {
                        LogUtil.e(e, "Error loading streamable video url = ["
                                + url
                                + "] streamableUrl = ["
                                + streamableUrl
                                + "]");

                        c.runOnUiThread(this::onError);
                        if (closeIfNull) {
                            c.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        new AlertDialog.Builder(c)
                                                .setTitle(R.string.error_video_not_found)
                                                .setMessage(R.string.error_video_message)
                                                .setCancelable(false)
                                                .setPositiveButton(R.string.btn_ok, (dialog, which) ->
                                                        c.finish())
                                                .create()
                                                .show();
                                    } catch (Exception ignored) {
                                    }
                                }
                            });
                        }
                    }
                    break;
                case OTHER:
                    LogUtil.e("We shouldn't be here!");
                    // unless it's a .gif that reddit didn't generate a preview vid for, then we should be here
                    // e.g. https://www.reddit.com/r/testslideforreddit/comments/hpht5o/stinky/
                    openWebsite(url);
                    break;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Uri uri) {
            if (uri == null) {
                cancel();
                return;
            }
            progressBar.setIndeterminate(true);

            if (gifSave != null) {
                gifSave.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        cacheSaveGif(uri, c, subreddit, submissionTitle, true);
                    }
                });
            } else if (doOnClick != null) {
                MediaView.doOnClick = new Runnable() {
                    @Override
                    public void run() {
                        cacheSaveGif(uri, c, subreddit, submissionTitle, true);
                    }
                };
            }

            ExoVideoView.VideoType type = uri.getHost().equals("v.redd.it")
                    ? ExoVideoView.VideoType.DASH : ExoVideoView.VideoType.STANDARD;
            video.setVideoURI(uri, type, new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_READY) {
                        progressBar.setVisibility(View.GONE);
                        if (size != null) {
                            size.setVisibility(View.GONE);
                        }
                    } else if (playbackState == Player.STATE_BUFFERING) {
                        progressBar.setVisibility(View.VISIBLE);
                        if (size != null) {
                            size.setVisibility(View.VISIBLE);
                        }
                    }
                }
            });
            if (autostart) {
                video.play();
            }
        }

        /**
         * Get a remote video's file size
         *
         * @param url      URL of video (or v.redd.it DASH manifest) to get
         * @param client   OkHttpClient
         * @param sizeText TextView to put size into
         * @param c        Activity
         */
        static void getRemoteFileSize(String url, OkHttpClient client,
                final TextView sizeText, Activity c) {
            if (!url.contains("v.redd.it")) {
                Request request = new Request.Builder().url(url).head().build();
                Response response;
                try {
                    response = client.newCall(request).execute();
                    final long size = response.body().contentLength();
                    response.close();
                    c.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            sizeText.setText(FileUtil.readableFileSize(size));
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                DataSource.Factory downloader =
                        new OkHttpDataSource.Factory(Reddit.client)
                                .setUserAgent(c.getString(R.string.app_name));
                DataSource.Factory cacheDataSourceFactory =
                        new CacheDataSource.Factory()
                                .setCache(Reddit.videoCache)
                                .setUpstreamDataSourceFactory(downloader);
                InputStream dashManifestStream = new DataSourceInputStream(cacheDataSourceFactory.createDataSource(),
                        new DataSpec(Uri.parse(url)));
                try {
                    DashManifest dashManifest = new DashManifestParser().parse(Uri.parse(url), dashManifestStream);
                    dashManifestStream.close();
                    long videoSize = 0;
                    long audioSize = 0;

                    for (int i = 0; i < dashManifest.getPeriodCount(); i++) {
                        for (AdaptationSet as : dashManifest.getPeriod(i).adaptationSets) {
                            boolean isAudio = false;
                            int bitrate = 0;
                            String hqUri = null;
                            for (Representation r : as.representations) {
                                if (r.format.bitrate > bitrate) {
                                    bitrate = r.format.bitrate;
                                    hqUri = r.baseUrl;
                                }
                                if (MimeTypes.isAudio(r.format.sampleMimeType)) {
                                    isAudio = true;
                                }
                            }

                            Request request = new Request.Builder().url(hqUri).head().build();
                            Response response = null;
                            try {
                                response = client.newCall(request).execute();
                                if (isAudio) {
                                    audioSize = response.body().contentLength();
                                } else {
                                    videoSize = response.body().contentLength();
                                }
                                response.close();
                            } catch (IOException e) {
                                if (response != null)
                                    response.close();
                            }
                        }
                    }
                    final long totalSize = videoSize + audioSize;
                    c.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // We can't know which quality will be selected, so we display <= the highest quality size
                            if (totalSize > 0)
                                sizeText.setText("≤ " + FileUtil.readableFileSize(totalSize));
                        }
                    });
                } catch (IOException ignored) {
                }
            }
        }

        private void openWebsite(String url){
            if (closeIfNull) {
                Intent web = new Intent(c, Website.class);
                web.putExtra(LinkUtil.EXTRA_URL, url);
                web.putExtra(LinkUtil.EXTRA_COLOR, Color.BLACK);
                c.startActivity(web);
                c.finish();
            }
        }

    }

    /**
     * Shows a ProgressBar in the UI. If this method is called from a non-main thread, it will run
     * the UI code on the main thread
     *
     * @param activity        The activity context to use to display the ProgressBar
     * @param progressBar     The ProgressBar to display
     * @param isIndeterminate True to show an indeterminate ProgressBar, false otherwise
     */
    private static void showProgressBar(final Activity activity, final ProgressBar progressBar,
            final boolean isIndeterminate) {
        if (activity == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // Current Thread is Main Thread.
            if (progressBar != null) progressBar.setIndeterminate(isIndeterminate);
        } else {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (progressBar != null) progressBar.setIndeterminate(isIndeterminate);
                }
            });
        }
    }

    /**
     * Mux a video and audio file (e.g. from DASH) together into a single video
     *
     * @param videoFile  Video file
     * @param audioFile  Audio file
     * @param outputFile File to output muxed video to
     * @return Whether the muxing completed successfully
     */
    private static boolean mux(String videoFile, String audioFile, String outputFile) {
        Movie rawVideo;
        try {
            rawVideo = MovieCreator.build(videoFile);
        } catch (RuntimeException | IOException e) {
            e.printStackTrace();
            return false;
        }

        Movie audio;
        try {
            audio = MovieCreator.build(audioFile);
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
            return false;
        }

        Track audioTrack = audio.getTracks().get(0);
        Track videoTrack = rawVideo.getTracks().get(0);
        Movie video = new Movie();

        ClippedTrack croppedTrackAudio = new ClippedTrack(audioTrack, 0, audioTrack.getSamples().size());
        video.addTrack(croppedTrackAudio);
        ClippedTrack croppedTrackVideo = new ClippedTrack(videoTrack, 0, videoTrack.getSamples().size());
        video.addTrack(croppedTrackVideo);

        Container out = new DefaultMp4Builder().build(video);

        FileOutputStream fos;
        try {
            fos = new FileOutputStream(outputFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        }
        BufferedWritableFileByteChannel byteBufferByteChannel =
                new BufferedWritableFileByteChannel(fos);
        try {
            out.writeContainer(byteBufferByteChannel);
            byteBufferByteChannel.close();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static class BufferedWritableFileByteChannel implements WritableByteChannel {
        private static final int BUFFER_CAPACITY = 1000000;

        private boolean isOpen = true;
        private final OutputStream outputStream;
        private final ByteBuffer byteBuffer;
        private final byte[] rawBuffer = new byte[BUFFER_CAPACITY];

        private BufferedWritableFileByteChannel(OutputStream outputStream) {
            this.outputStream = outputStream;
            this.byteBuffer = ByteBuffer.wrap(rawBuffer);
        }

        @Override
        public int write(ByteBuffer inputBuffer) {
            int inputBytes = inputBuffer.remaining();

            if (inputBytes > byteBuffer.remaining()) {
                dumpToFile();
                byteBuffer.clear();

                if (inputBytes > byteBuffer.remaining()) {
                    throw new BufferOverflowException();
                }
            }

            byteBuffer.put(inputBuffer);

            return inputBytes;
        }

        @Override
        public boolean isOpen() {
            return isOpen;
        }

        @Override
        public void close() {
            dumpToFile();
            isOpen = false;
        }

        private void dumpToFile() {
            try {
                outputStream.write(rawBuffer, 0, byteBuffer.position());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
