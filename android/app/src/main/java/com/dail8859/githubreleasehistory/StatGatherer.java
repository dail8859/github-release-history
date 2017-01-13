package com.dail8859.githubreleasehistory;

import android.os.AsyncTask;
import android.util.Log;

import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import com.squareup.moshi.JsonAdapter;

import org.eclipse.jgit.lib.ProgressMonitor;

import java.io.IOException;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class StatGatherer extends AsyncTask<Void, String, String> {
    private OkHttpClient client;
    private String repoDir;
    private String username;
    private String api_key;
    private ProgressMonitor pm;
    private AsyncTaskCallback cb;

    // Keep track of some stats
    private String yesterday;
    private int etag_hit = 0;
    private int request_count = 0;

    public StatGatherer(String _repoDir, String _username, String _api_key, ProgressMonitor _pm, AsyncTaskCallback _cb) {
        client = new OkHttpClient.Builder().followRedirects(true).build();
        repoDir = _repoDir;
        username = _username;
        api_key = _api_key;
        pm = _pm;
        cb = _cb;

        // Get yesterdays's UTC date, e.g. 2016-05-31
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -1);
        yesterday = sdf.format(calendar.getTime());
    }

    @Override
    protected String doInBackground(Void... params) {
        Date start_time = new Date();

        List<RepoToLookUp> repos = Utilities.loadReposToLookUp(repoDir + "/repos.json");

        if (pm != null) {
            pm.start(1);
            pm.beginTask("Getting release stats...", repos.size());
        }

        for (RepoToLookUp rtlu : repos) {
            Log.d("TAG", "doInBackground: " + rtlu.user + "/" + rtlu.repo);
            RepositoryStats stats = Utilities.loadRepositoryStats(String.format("%s/data/%s/%s.json", repoDir, rtlu.user, rtlu.repo));

            // The first time
            if (stats == null) stats = new RepositoryStats();

            List<String> prev_etag = stats.etags == null ? null : new ArrayList<>(stats.etags); // Copy it
            Log.d("TAG", "doInBackground: stats.etags " + (stats.etags == null ? "null" : stats.etags.toString()));
            List<GithubRelease> releases = fetchRepositoryReleases(rtlu, stats);
            Log.d("TAG", "doInBackground: stats.etags " + (stats.etags == null ? "null" : stats.etags.toString()));

            // If something bad happened, or if there was an etags hit, releases will be null
            if (releases != null) {
                Log.d("TAG", "doInBackground: got releases");
                boolean has_updates = processUpdate(releases, stats);

                // We don't want to just commit an etags change
                // Example:
                //   2016-05-05 etags is abc123 and count is 5
                // If ran again later *in the same day*
                //   2016-05-05 etags is efg456 and count is 6
                // Then the download_count of the stat wouldn't get updated
                // because that entry already exists, thus just changing the etags
                // So...only update the etags if something actually got updated
                if (has_updates == false) {
                    stats.etags = prev_etag;
                }
            }

            Utilities.saveRepositoryStats(stats, String.format("%s/data/%s/%s.json", repoDir, rtlu.user, rtlu.repo));

            if (pm != null) pm.update(1);
        }

        if (pm != null) pm.endTask();

        Date end_time = new Date();

        // Build the commit string for these stats
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

        String gmtTime = sdf.format(end_time);

        String sb = String.format("Update for %s\n\n", yesterday) +
                String.format("Started at: %s\n", sdf.format(start_time)) +
                String.format("Finished at: %s\n", sdf.format(end_time)) +
                String.format("Etag hit: %d/%d %.2f%%\n", etag_hit, request_count, ((double) etag_hit / request_count) * 100.0);
        return sb;
    }

    private List<GithubRelease> fetchRepositoryReleases(RepoToLookUp rtlu, RepositoryStats stats) {
        Moshi moshi = new Moshi.Builder().build();
        Type listOfStuff = Types.newParameterizedType(List.class, GithubRelease.class);
        JsonAdapter<List<GithubRelease>> jsonAdapter = moshi.adapter(listOfStuff);
        List<GithubRelease> releases = new ArrayList<>();
        String url = String.format("https://api.github.com/repos/%s/%s/releases", rtlu.user, rtlu.repo);

        int page = 0;
        do {
            page++;

            Request.Builder b = new Request.Builder()
                    .url(url)
                    .addHeader("Accept", "application/vnd.github.v3+json")
                    .addHeader("user-agent", "github-release-history")
                    .addHeader("Authorization", "token " + api_key);

            // Add the etags if there was one available
            if (stats.etags != null && stats.etags.size() >= page) {
                Log.d("TAG", "fetchRepositoryReleases: using " + stats.etags.get(page - 1));
                b.addHeader("If-None-Match", "\"" + stats.etags.get(page - 1) + "\"");
            }

            try {
                // All your bits are belong to me
                Response response = client.newCall(b.build()).execute();

                // There could be multiple pages of releases, see if there is a Link header
                url = null;
                String next_link = response.header("Link");
                if (next_link != null) {
                    Log.d("TAG", "fetchRepositoryReleases: Link " + next_link);
                    Pattern pattern = Pattern.compile("<(.+?)>; rel=\"(.+?)\"");
                    Matcher matcher = pattern.matcher(next_link);
                    while (matcher.find()) {
                        if (matcher.group(2).equals("next")) {
                            url = matcher.group(1);
                            Log.d("TAG", "fetchRepositoryReleases: next page at " + url);
                            break;
                        }
                    }
                }

                request_count++;
                if (response.code() == 304) {
                    etag_hit++;
                    Log.d("TAG", "fetchRepositoryReleases: etags hit for page " + String.valueOf(page));
                    continue;
                }

                if (!response.isSuccessful()) {
                    // TODO: if code is 404: throw exception so the calling function can remove this repo
                    Log.d("TAG", "fetchRepositoryReleases: response " + String.valueOf(response.code()));
                    return null;
                }

                Log.d("TAG", "doInBackground: X-RateLimit-Remaining " + response.header("X-RateLimit-Remaining"));

                // Save the etag, remove the weak tag specifier
                String etag = response.header("etag");
                etag = etag.substring(3, etag.length() - 1);

                if (stats.etags == null) stats.etags = new ArrayList<>();

                Log.d("TAG", "fetchRepositoryReleases: " + stats.etags.toString() + " " + String.valueOf(page));
                if (stats.etags.size() < page) stats.etags.add(etag);
                else stats.etags.set(page - 1, etag);

                // Parse this json blob into releases
                releases.addAll(jsonAdapter.fromJson(response.body().source()));
                response.body().close();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        } while (url != null);

        return releases;
    }

    private boolean processUpdate(List<GithubRelease> releases, RepositoryStats stats) {
        // Keep track if anything gets updated in the RepositoryStats
        boolean has_updated = false;

        Log.d("TAG", "processUpdate: " + yesterday);

        // The magic
        for (GithubRelease release : releases) {
            // Early out, no need to keep track of releases with no assets
            if (release.assets.size() == 0) continue;

            if (stats.releases == null) stats.releases = new LinkedHashMap<>();

            String release_id = String.valueOf(release.id);

            // Allocate some junk in case it's the first time for this release
            if (!stats.releases.containsKey(release_id)) {
                stats.releases.put(release_id, new ReleaseStats());
                stats.releases.get(release_id).assets = new LinkedHashMap<>();
                stats.releases.get(release_id).created_at = release.created_at;
                has_updated = true;
            }

            // Always update the name since technically it could have changed
            // Use the tag name if the actual name is empty
            if (!release.name.isEmpty()) stats.releases.get(release_id).name = release.name;
            else stats.releases.get(release_id).name = release.tag_name;

            for (GithubAsset asset : release.assets) {
                String asset_id = String.valueOf(asset.id);

                // Allocate more junk in case it's the first time for this asset
                if (!stats.releases.get(release_id).assets.containsKey(asset_id)) {
                    stats.releases.get(release_id).assets.put(asset_id, new AssetStats());
                    stats.releases.get(release_id).assets.get(asset_id).downloads = new LinkedHashMap<>();
                    stats.releases.get(release_id).assets.get(asset_id).created_at = asset.created_at;
                    stats.releases.get(release_id).assets.get(asset_id).downloads.put(yesterday, asset.download_count);
                    has_updated = true;
                }

                AssetStats asset_stat = stats.releases.get(release_id).assets.get(asset_id);

                // Always update the name since technically it could have changed
                asset_stat.name = asset.name;

                // Get the most recent date that a download count has been stored for
                String recent_date = new ArrayList<>(asset_stat.downloads.keySet()).get(asset_stat.downloads.size() - 1);

                // Only update if "today" doesn't exist and the download count has changed
                if (!recent_date.equals(yesterday) && asset_stat.downloads.get(recent_date) != asset.download_count) {
                    asset_stat.downloads.put(yesterday, asset.download_count);
                    has_updated = true;
                }
            }
        }
        return has_updated;
    }

    @Override
    protected void onPostExecute(String result) {
        if (cb != null) {
            cb.onComplete(result);
        }
    }
}
