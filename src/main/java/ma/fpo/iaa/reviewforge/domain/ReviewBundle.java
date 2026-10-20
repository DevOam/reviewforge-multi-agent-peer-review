package ma.fpo.iaa.reviewforge.domain;

import java.io.Serializable;
import java.util.List;

public record ReviewBundle(Paper paper, List<Review> reviews) implements Serializable {}
