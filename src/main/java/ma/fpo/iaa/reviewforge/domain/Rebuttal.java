package ma.fpo.iaa.reviewforge.domain;

import java.io.Serializable;
import java.util.List;

public record Rebuttal(String author, List<String> responses, String revisionCommitment)
        implements Serializable {}
