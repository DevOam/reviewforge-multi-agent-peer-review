package ma.fpo.iaa.reviewforge.domain;

import java.io.Serializable;
import java.util.List;

public record Review(
        String reviewer,
        String specialty,
        double score,
        double confidence,
        List<String> strengths,
        List<String> concerns,
        String recommendation,
        boolean revised
) implements Serializable {
    public Review revised(double newScore, String newRecommendation, List<String> newConcerns) {
        return new Review(reviewer, specialty, newScore, confidence, strengths,
                newConcerns, newRecommendation, true);
    }
}
