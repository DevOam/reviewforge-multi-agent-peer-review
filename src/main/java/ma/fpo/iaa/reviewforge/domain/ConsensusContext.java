package ma.fpo.iaa.reviewforge.domain;

import java.io.Serializable;
import java.util.List;

public record ConsensusContext(Paper paper, List<Review> peerReviews, Rebuttal rebuttal)
        implements Serializable {}
