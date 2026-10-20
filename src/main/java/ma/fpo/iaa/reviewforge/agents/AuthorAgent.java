package ma.fpo.iaa.reviewforge.agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.AchieveREResponder;
import ma.fpo.iaa.reviewforge.domain.*;
import java.util.List;

public class AuthorAgent extends Agent {
    protected void setup() {
        MessageTemplate template = MessageTemplate.and(
                MessageTemplate.MatchConversationId(Protocol.CONVERSATION_ID),
                MessageTemplate.MatchOntology(Protocol.REBUTTAL_REQUEST));
        addBehaviour(new AchieveREResponder(this, template) {
            protected ACLMessage prepareResponse(ACLMessage request) {
                ACLMessage response = request.createReply();
                try {
                    ReviewBundle bundle = (ReviewBundle) request.getContentObject();
                    List<String> concerns = bundle.reviews().stream().flatMap(r -> r.concerns().stream()).toList();
                    List<String> responses = concerns.stream().limit(5)
                            .map(c -> "Réponse proposée à la critique : " + c).toList();
                    Rebuttal rebuttal = new Rebuttal(getLocalName(), responses.isEmpty()
                            ? List.of("Aucune critique majeure; nous clarifierons les éléments signalés.") : responses,
                            "Ajouter les tests statistiques manquants, expliciter les limites et réaliser un audit des biais.");
                    response.setPerformative(ACLMessage.INFORM);
                    response.setOntology(Protocol.AUTHOR_REBUTTAL);
                    response.setContentObject(rebuttal);
                    System.out.printf("%n[DEBATE] L'auteur répond à %d rapports et propose des corrections.%n",
                            bundle.reviews().size());
                } catch (Exception e) {
                    response.setPerformative(ACLMessage.FAILURE);
                    response.setContent(e.getMessage());
                }
                return response;
            }
        });
        addBehaviour(new CyclicBehaviour() {
            public void action() {
                ACLMessage message = receive(MessageTemplate.MatchOntology("SHUTDOWN"));
                if (message != null) doDelete(); else block();
            }
        });
        System.out.printf("[READY] %-22s rôle: réponse aux reviewers%n", getLocalName());
    }
}
