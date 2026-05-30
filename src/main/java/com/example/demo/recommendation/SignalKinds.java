package com.example.demo.recommendation;

import java.util.Map;

/**
 * Canonical signal kinds + their default model weights. Kept as plain
 * constants so the rest of the app can write a signal with a typo-safe
 * reference (`SignalKinds.LIKE`) instead of a magic string.
 *
 * <p>Tweak the weight map to retune the ranker without touching call
 * sites or migrations. Positive values teach "give me more of this";
 * negatives push items down ({@code SKIP}, {@code REPORT}).</p>
 */
public final class SignalKinds {

    public static final String VIEW           = "VIEW";
    public static final String LIKE           = "LIKE";
    public static final String COMMENT        = "COMMENT";
    public static final String SHARE          = "SHARE";
    public static final String SAVE           = "SAVE";
    public static final String FOLLOW         = "FOLLOW";
    public static final String UNFOLLOW       = "UNFOLLOW";
    public static final String COMPLETE_REEL  = "COMPLETE_REEL";
    public static final String REWATCH        = "REWATCH";
    public static final String SKIP           = "SKIP";
    public static final String REPORT         = "REPORT";
    public static final String PROFILE_VISIT  = "PROFILE_VISIT";
    public static final String LIVE_JOIN      = "LIVE_JOIN";
    public static final String LIVE_REACT     = "LIVE_REACT";
    public static final String STORY_REPLY    = "STORY_REPLY";

    /** Default weight applied when [FeedSignalService.record] is called
     *  with a {@code weight <= 0} (i.e. "use the table default"). */
    public static final Map<String, Double> DEFAULTS = Map.ofEntries(
            Map.entry(VIEW,           0.5),
            Map.entry(LIKE,           1.5),
            Map.entry(COMMENT,        2.5),
            Map.entry(SHARE,          3.0),
            Map.entry(SAVE,           3.0),
            Map.entry(FOLLOW,         4.0),
            Map.entry(UNFOLLOW,      -4.0),
            Map.entry(COMPLETE_REEL,  2.0),
            Map.entry(REWATCH,        2.5),
            Map.entry(SKIP,          -0.5),
            Map.entry(REPORT,        -5.0),
            Map.entry(PROFILE_VISIT,  1.0),
            Map.entry(LIVE_JOIN,      1.5),
            Map.entry(LIVE_REACT,     1.0),
            Map.entry(STORY_REPLY,    2.0)
    );

    /** Target types — kept in sync with the migration. */
    public static final String T_POST    = "POST";
    public static final String T_REEL    = "REEL";
    public static final String T_STORY   = "STORY";
    public static final String T_LIVE    = "LIVE";
    public static final String T_USER    = "USER";
    public static final String T_HASHTAG = "HASHTAG";

    private SignalKinds() {}

    public static double defaultWeight(String kind) {
        Double d = DEFAULTS.get(kind);
        return d == null ? 1.0 : d;
    }
}
