package ma.fpo.iaa.reviewforge.agents;

import ma.fpo.iaa.reviewforge.domain.*;
import java.util.ArrayList;
import java.util.List;

public class ResultsReviewerAgent extends AbstractReviewerAgent {
    protected String specialty() { return "Résultats et reproductibilité"; }

    protected Review evaluate(Paper p) {
        List<String> strengths = new ArrayList<>();
        List<String> concerns = new ArrayList<>();
        double score = 5.0;
        if (p.reportedAccuracy() >= .85) { score += 1.5; strengths.add(String.format("Accuracy détectée: %.1f%% — %s", p.reportedAccuracy()*100, p.proof("ACCURACY"))); }
        else if (p.reportedAccuracy() > 0) concerns.add(String.format("Accuracy limitée: %.1f%% — %s", p.reportedAccuracy()*100, p.proof("ACCURACY")));
        else concerns.add("Accuracy non détectée; vérifier si l'article utilise F1, AUC ou RMSE");
        if (p.hasBaseline()) { score += 1; strengths.add("Comparaison détectée — " + p.proof("BASELINE")); }
        if (!p.hasStatisticalTest()) { score -= 1; concerns.add("Le gain annoncé peut ne pas être statistiquement significatif"); }
        if (p.discussesLimitations()) strengths.add("Limites discutées — " + p.proof("LIMITATIONS"));
        else { score -= .5; concerns.add("Aucune section de limites détectée"); }
        return new Review(getLocalName(), specialty(), score, .82, strengths, concerns,
                score >= 7 ? "ACCEPT" : "MAJOR_REVISION", false);
    }

    protected Review reconsider(ConsensusContext c) {
        Review mine = ownReview(c);
        boolean limitationsAdded = c.rebuttal().revisionCommitment().contains("limites");
        return mine.revised(Math.min(10, mine.score() + (limitationsAdded ? .7 : 0)),
                limitationsAdded ? "MINOR_REVISION" : mine.recommendation(),
                limitationsAdded ? List.of("Contrôler la nouvelle analyse des limites") : mine.concerns());
    }
}
