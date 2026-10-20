package ma.fpo.iaa.reviewforge.domain;

import java.io.Serializable;
import java.util.List;

public record Paper(
        String title,
        String abstractText,
        int datasetSize,
        boolean hasBaseline,
        boolean hasStatisticalTest,
        boolean discussesLimitations,
        boolean hasEthicsStatement,
        boolean anonymizedData,
        double reportedAccuracy,
        String sourceFile,
        int pageCount,
        List<Evidence> evidence
) implements Serializable {
    public Paper {
        sourceFile = sourceFile == null ? "Saisie manuelle" : sourceFile;
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public Paper(String title, String abstractText, int datasetSize, boolean hasBaseline,
                 boolean hasStatisticalTest, boolean discussesLimitations,
                 boolean hasEthicsStatement, boolean anonymizedData, double reportedAccuracy) {
        this(title, abstractText, datasetSize, hasBaseline, hasStatisticalTest,
                discussesLimitations, hasEthicsStatement, anonymizedData, reportedAccuracy,
                "Saisie manuelle", 0, List.of());
    }

    public List<Evidence> evidenceFor(String criterion) {
        return evidence.stream().filter(e -> e.criterion().equals(criterion)).toList();
    }

    public String proof(String criterion) {
        return evidenceFor(criterion).stream().findFirst().map(Evidence::display).orElse("Aucune preuve textuelle détectée");
    }
    public static Paper demoPaper() {
        return new Paper(
                "Détection précoce du stress étudiant par apprentissage automatique",
                "Une Random Forest prédit le stress à partir de questionnaires universitaires.",
                180, true, false, false, true, true, 0.91
        );
    }
}
