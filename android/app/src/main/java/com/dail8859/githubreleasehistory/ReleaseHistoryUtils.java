package com.dail8859.githubreleasehistory;

import android.util.Log;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonWriter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import okio.Buffer;

// Pulled from the repos.json file
class RepoToLookUp {
    public String user;
    public String repo;

    @Override
    public boolean equals(Object o) {
        return o instanceof RepoToLookUp && ((RepoToLookUp) o).user.equals(user) && ((RepoToLookUp) o).repo.equals(repo);
    }
}

// The Release that came from Github's json blob
class GithubRelease {
    int id;
    String name;
    String tag_name;
    String created_at;
    List<GithubAsset> assets;
}

// The Asset that came from Github's json blob
class GithubAsset {
    int id;
    String name;
    String created_at;
    int download_count;
}

// The locally stored json
class RepositoryStats {
    List<String> etags;
    Map<String, ReleaseStats> releases;
}

// The locally stored json
class ReleaseStats {
    String name;
    String created_at;
    Map<String, AssetStats> assets;
}

// The locally stored json
class AssetStats {
    String name;
    String created_at;
    Map<String, Integer> downloads;
}

final class Utilities {
    private Utilities() {};

    public static List<RepoToLookUp> loadReposToLookUp(String filename) {
        try {
            File file = new File(filename);
            String content = FileUtils.readFileToString(file);

            Log.d("TAG", "loadReposToLookUp: " + content);
            Moshi moshi = new Moshi.Builder().build();
            Type listOfStuff = Types.newParameterizedType(List.class, RepoToLookUp.class);
            JsonAdapter<List<RepoToLookUp>> jsonAdapter = moshi.adapter(listOfStuff);

            return jsonAdapter.fromJson(content);
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public static void saveReposToLookUp(String filename, List<RepoToLookUp> repos) {
        Buffer buffer = new Buffer();
        JsonWriter jsonWriter = JsonWriter.of(buffer);

        jsonWriter.setIndent("  ");

        FileOutputStream stream = null;
        try {
            //Type listOfStuff = Types.newParameterizedType(List.class, RepoToLookUp.class);
            //JsonAdapter<List<RepoToLookUp>> jsonAdapter = moshi.adapter(listOfStuff);
            //jsonAdapter.toJson(jsonWriter, repos);

            // Do some custom writing to make it a bit more compact
            jsonWriter.beginArray();
            for (RepoToLookUp rtlu : repos) {
                jsonWriter.beginObject();
                jsonWriter.setIndent("");
                jsonWriter.name("user").value(rtlu.user);
                jsonWriter.name("repo").value(rtlu.repo);
                jsonWriter.endObject();
                jsonWriter.setIndent("  ");
            }
            jsonWriter.endArray();

            String json = buffer.readUtf8();

            Log.d("TAG", "saveReposToLookUp: " + json);
            stream = new FileOutputStream(new File(filename));
            stream.write(json.getBytes());

            stream.close();
        } catch (FileNotFoundException e) {
            // Something really bad happened because the file should be there
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static RepositoryStats loadRepositoryStats(String filename) {
        Moshi moshi = new Moshi.Builder().build();
        JsonAdapter<RepositoryStats> jsonAdapter = moshi.adapter(RepositoryStats.class);

        File file = new File(filename);

        Log.d("TAG", "loadRepositoryStats: " + filename);

        if (!file.exists()) {
            try {
                // Make sure the directory exists
                new File(filename.substring(0,filename.lastIndexOf("/"))).mkdirs();

                Log.d("TAG", "loadRepositoryStats: creating new file");

                // Create it and write out an empty json blob
                file.createNewFile();
                FileOutputStream stream = new FileOutputStream(file);
                stream.write("{}".getBytes());
                stream.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return null;
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        try {
            String content = FileUtils.readFileToString(file);
            return jsonAdapter.fromJson(content);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static void saveRepositoryStats(RepositoryStats stats, String filename) {
        Moshi moshi = new Moshi.Builder().build();
        Buffer buffer = new Buffer();
        JsonWriter jsonWriter = JsonWriter.of(buffer);

        jsonWriter.setIndent(" ");

        FileOutputStream stream = null;
        try {
            moshi.adapter(RepositoryStats.class).toJson(jsonWriter, stats);
            String json = buffer.readUtf8();

            stream = new FileOutputStream(new File(filename));
            stream.write(json.getBytes());

            stream.close();
        } catch (FileNotFoundException e) {
            // Something really bad happened because the file should be there
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}