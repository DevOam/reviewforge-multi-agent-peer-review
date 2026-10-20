package ma.fpo.iaa.reviewforge.agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import ma.fpo.iaa.reviewforge.domain.*;
import ma.fpo.iaa.reviewforge.ui.ReviewForgeEvents;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChairAgent extends Agent {
    private final List<String> reviewers = List.of("reviewer-methodology", "reviewer-results", "reviewer-ethics");

    protected void setup() {
        System.out.printf("[READY] %-22s rôle: présidence du comité%n", getLocalName());
        addBehaviour(new OneShotBehaviour() {
            public void action() {
                try { runConference(); }
                catch (Exception e) {
                    System.err.println("[ERROR] " + e.getMessage());
                    ReviewForgeEvents.publish(ReviewForgeEvents.Type.ERROR, e.getMessage());
                } finally { doDelete(); }
            }
        });
    }

    private void runConference() throws Exception {
        Object[] args = getArguments();
        Paper paper = args != null && args.length > 0 && args[0] instanceof Paper p ? p : Paper.demoPaper();
        System.out.println("\n[SUBMISSION] " + paper.title());
        ReviewForgeEvents.publish(ReviewForgeEvents.Type.PHASE, "Article reçu par le Chair Agent");
        System.out.println("[PHASE 1] Distribution parallèle aux reviewers spécialisés...");
        ReviewForgeEvents.publish(ReviewForgeEvents.Type.LOG, "REQUEST → trois reviewers spécialisés");
        String phase1 = UUID.randomUUID().toString();
        reviewers.forEach(r -> sendObject(r, ACLMessage.REQUEST, Protocol.REVIEW_REQUEST, phase1, paper));
        List<Review> initial = receiveReviews(phase1, Protocol.INITIAL_REVIEW);
        printReviews("RAPPORTS INITIAUX", initial);
        ReviewForgeEvents.publish(ReviewForgeEvents.Type.INITIAL_REVIEWS, "Rapports initiaux reçus", initial);

        System.out.println("\n[PHASE 2] Transmission des critiques à l'auteur (rebuttal)...");
        ReviewForgeEvents.publish(ReviewForgeEvents.Type.PHASE, "Phase de rebuttal contradictoire");
        String rebuttalId = UUID.randomUUID().toString();
        sendObject("author", ACLMessage.REQUEST, Protocol.REBUTTAL_REQUEST, rebuttalId,
                new ReviewBundle(paper, initial));
        ACLMessage authorReply = blockingReceive(replyTemplate(rebuttalId, Protocol.AUTHOR_REBUTTAL), 10_000);
        if (authorReply == null) throw new IllegalStateException("Timeout: aucune réponse de l'auteur");
        Rebuttal rebuttal = (Rebuttal) authorReply.getContentObject();
        ReviewForgeEvents.publish(ReviewForgeEvents.Type.REBUTTAL, "Réponse argumentée de l'auteur", rebuttal);
        rebuttal.responses().forEach(r -> System.out.println("  • " + r));
        System.out.println("  Engagement: " + rebuttal.revisionCommitment());

        System.out.println("\n[PHASE 3] Consensus: chaque reviewer reconsidère son avis...");
        ReviewForgeEvents.publish(ReviewForgeEvents.Type.PHASE, "Consensus et seconde évaluation");
        String phase3 = UUID.randomUUID().toString();
        ConsensusContext context = new ConsensusContext(paper, initial, rebuttal);
        reviewers.forEach(r -> sendObject(r, ACLMessage.REQUEST, Protocol.CONSENSUS_REQUEST, phase3, context));
        List<Review> finals = receiveReviews(phase3, Protocol.FINAL_REVIEW);
        printReviews("RAPPORTS APRÈS DÉBAT", finals);
        ReviewForgeEvents.publish(ReviewForgeEvents.Type.FINAL_REVIEWS, "Consensus terminé", finals);
        printDecision(paper, finals, rebuttal);
    }

    private List<Review> receiveReviews(String replyWith, String ontology) throws Exception {
        List<Review> reports = new ArrayList<>();
        while (reports.size() < reviewers.size()) {
            ACLMessage message = blockingReceive(replyTemplate(replyWith, ontology), 10_000);
            if (message == null) throw new IllegalStateException("Timeout: rapports incomplets");
            reports.add((Review) message.getContentObject());
        }
        return reports;
    }

    private void sendObject(String receiver, int performative, String ontology,
                            String replyWith, Serializable object) {
        try {
            ACLMessage message = new ACLMessage(performative);
            message.addReceiver(new AID(receiver, AID.ISLOCALNAME));
            message.setConversationId(Protocol.CONVERSATION_ID);
            message.setOntology(ontology);
            message.setReplyWith(replyWith);
            message.setContentObject(object);
            send(message);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private MessageTemplate replyTemplate(String inReplyTo, String ontology) {
        return MessageTemplate.and(
                MessageTemplate.MatchConversationId(Protocol.CONVERSATION_ID),
                MessageTemplate.and(MessageTemplate.MatchInReplyTo(inReplyTo),
                        MessageTemplate.MatchOntology(ontology)));
    }

    private void printReviews(String title, List<Review> reports) {
        System.out.println("\n--- " + title + " ---");
        reports.stream().sorted((a, b) -> a.specialty().compareTo(b.specialty())).forEach(r -> {
            System.out.printf("%-34s score %.1f/10 | confiance %.0f%% | %s%n",
                    r.specialty(), r.score(), r.confidence() * 100, r.recommendation());
            r.concerns().forEach(c -> System.out.println("  ⚠ " + c));
        });
    }

    private void printDecision(Paper paper, List<Review> reports, Rebuttal rebuttal) {
        double weighted = reports.stream().mapToDouble(r -> r.score() * r.confidence()).sum()
                / reports.stream().mapToDouble(Review::confidence).sum();
        String decision = weighted >= 7.5 ? "ACCEPTÉ" : weighted >= 6 ? "RÉVISION MINEURE" :
                weighted >= 4.5 ? "RÉVISION MAJEURE" : "REJETÉ";
        System.out.println("\n============================================================");
        System.out.println(" DÉCISION EXPLICABLE DU CHAIR AGENT");
        System.out.println("============================================================");
        System.out.println("Article       : " + paper.title());
        System.out.printf("Score pondéré : %.2f/10%n", weighted);
        System.out.println("Décision      : " + decision);
        System.out.println("Justification : consensus calculé selon score × confiance;");
        System.out.println("                les engagements de l'auteur ont été pris en compte.");
        System.out.println("Traçabilité   : 3 avis spécialisés + rebuttal + seconde évaluation.");
        System.out.println("============================================================\n");
        ReviewForgeEvents.publish(ReviewForgeEvents.Type.DECISION, decision,
                new ReviewForgeEvents.Decision(weighted, decision, reports, rebuttal));
    }
}
