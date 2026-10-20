package ma.fpo.iaa.reviewforge.domain;

public final class Protocol {
    public static final String CONVERSATION_ID = "reviewforge-peer-review-v1";
    public static final String REVIEW_REQUEST = "REVIEW_REQUEST";
    public static final String INITIAL_REVIEW = "INITIAL_REVIEW";
    public static final String REBUTTAL_REQUEST = "REBUTTAL_REQUEST";
    public static final String AUTHOR_REBUTTAL = "AUTHOR_REBUTTAL";
    public static final String CONSENSUS_REQUEST = "CONSENSUS_REQUEST";
    public static final String FINAL_REVIEW = "FINAL_REVIEW";

    private Protocol() {}
}
