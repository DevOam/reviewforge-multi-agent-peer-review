package ma.fpo.iaa.reviewforge.agents;

import ma.fpo.iaa.reviewforge.domain.*;
import java.util.ArrayList;
import java.util.List;

public class MethodologyReviewerAgent extends AbstractReviewerAgent {
    protected String specialty() { return "Méthodologie"; }

    protected Review evaluate(Paper p) {
        List<String> strengths = new ArrayList<>();
        List<String> concerns = new ArrayList<>();
        double score = 5.0;
        if (p.datasetSize() >= 150) { score += 1; strengths.add("Dataset détecté: " + p.datasetSize() + " — " + p.proof("DATASET")); }
        else if (p.datasetSize() > 0) concerns.add("Échantillon limité (" + p.datasetSize() + ") — " + p.proof("DATASET"));
        else concerns.add("Taille du dataset non détectée dans le PDF");
        if (p.hasBaseline()) { score += 1; strengths.add("Baseline détectée — " + p.proof("BASELINE")); }
        else concerns.add("Aucune comparaison avec une baseline détectée");
        if (p.hasStatisticalTest()) { score += 1.5; strengths.add("Test statistique détecté — " + p.proof("STATISTICS")); }
        else { score -= 1; concerns.add("Aucun test statistique détecté dans le texte"); }
        return new Review(getLocalName(), specialty(), score, .90, strengths, concerns,
                score >= 7 ? "ACCEPT" : "MAJOR_REVISION", false);
    }

    protected Review reconsider(ConsensusContext c) {
        Review mine = ownReview(c);
        boolean promised = c.rebuttal().revisionCommitment().contains("tests statistiques");
        List<String> concerns = promised ? List.of("Vérifier les tests statistiques dans la version révisée") : mine.concerns();
        return mine.revised(Math.min(10, mine.score() + (promised ? .8 : 0)),
                promised ? "MINOR_REVISION" : mine.recommendation(), concerns);
    }
}
