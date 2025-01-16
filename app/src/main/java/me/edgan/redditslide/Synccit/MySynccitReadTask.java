package me.edgan.redditslide.Synccit;

/** Created by carlo_000 on 2/16/2016. */
import me.edgan.redditslide.Adapters.SubmissionDisplay;
import me.edgan.redditslide.HasSeen;
import me.edgan.redditslide.SettingValues;

import java.util.HashSet;

public class MySynccitReadTask extends SynccitReadTask {

    private static final String MY_DEV_NAME = "slide_for_reddit";
    private SubmissionDisplay displayer;

    public MySynccitReadTask(SubmissionDisplay displayer) {
        super(MY_DEV_NAME);
        this.displayer = displayer;
    }

    public MySynccitReadTask() {
        super(MY_DEV_NAME);
    }

    @Override
    protected void onVisited(HashSet<String> visitedThreadIds) {
        SynccitRead.visitedIds.addAll(visitedThreadIds);

        // save the newly "seen" synccit posts to SEEN
        if (SettingValues.storeHistory) {
            for (String id : visitedThreadIds) {
                HasSeen.addSeen(id);
            }
        }
    }

    @Override
    protected String getUsername() {
        return SettingValues.synccitName;
    }

    @Override
    protected String getAuth() {
        return SettingValues.synccitAuth;
    }

    @Override
    protected String getUserAgent() {
        return "slide_for_reddit";
    }

    @Override
    public void onPostExecute(SynccitResponse result) {
        super.onPostExecute(result);
        if (displayer != null) displayer.updateViews();
    }
}
