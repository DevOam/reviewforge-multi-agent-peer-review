package ma.fpo.iaa.reviewforge.agents;

import ma.fpo.iaa.reviewforge.domain.*;
import java.util.ArrayList;
import java.util.List;

public class EthicsReviewerAgent extends AbstractReviewerAgent {
    protected String specialty() { return "Éthique, biais et confidentialité"; }

    protected Review evaluate(Paper p) {
        List<String> strengths = new ArrayList<>();
        List<String> concerns = new ArrayList<>();
        double score = 4.5;
        if (p.hasEthicsStatement()) { score += 1.5; strengths.add("Déclaration éthique détectée — " + p.proof("ETHICS")); }
        else concerns.add("Aucune déclaration éthique détectée dans le PDF");
        if (p.anonymizedData()) { score += 1.5; strengths.add("Anonymisation détectée — " + p.proof("ANONYMIZATION")); }
        else { score -= 2; concerns.add("Aucune preuve d'anonymisation détectée"); }
        if (!p.discussesLimitations()) { score -= .5; concerns.add("Biais potentiels non discutés"); }
        return new Review(getLocalName(), specialty(), score, .95, strengths, concerns,
                score >= 7 ? "ACCEPT" : "MAJOR_REVISION", false);
    }

    protected Review reconsider(ConsensusContext c) {
        Review mine = ownReview(c);
        boolean biasPlan = c.rebuttal().revisionCommitment().contains("biais");
        return mine.revised(Math.min(10, mine.score() + (biasPlan ? .6 : 0)),
                biasPlan ? "MINOR_REVISION" : mine.recommendation(),
                biasPlan ? List.of("Auditer l'analyse de biais promise") : mine.concerns());
    }
}
