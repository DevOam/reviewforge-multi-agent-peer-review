package ma.fpo.iaa.reviewforge.agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.AchieveREResponder;
import ma.fpo.iaa.reviewforge.domain.*;

import java.io.Serializable;

public abstract class AbstractReviewerAgent extends Agent {
    @Override
    protected void setup() {
        MessageTemplate reviewTemplate = MessageTemplate.and(
                MessageTemplate.MatchConversationId(Protocol.CONVERSATION_ID),
                MessageTemplate.MatchOntology(Protocol.REVIEW_REQUEST));
        addBehaviour(new AchieveREResponder(this, reviewTemplate) {
            @Override
            protected ACLMessage prepareResponse(ACLMessage request) {
                try {
                    Paper paper = (Paper) request.getContentObject();
                    return reply(request, ACLMessage.INFORM, Protocol.INITIAL_REVIEW, evaluate(paper));
                } catch (Exception e) {
                    return failure(request, e);
                }
            }
        });

        MessageTemplate consensusTemplate = MessageTemplate.and(
                MessageTemplate.MatchConversationId(Protocol.CONVERSATION_ID),
                MessageTemplate.MatchOntology(Protocol.CONSENSUS_REQUEST));
        addBehaviour(new AchieveREResponder(this, consensusTemplate) {
            @Override
            protected ACLMessage prepareResponse(ACLMessage request) {
                try {
                    ConsensusContext context = (ConsensusContext) request.getContentObject();
                    return reply(request, ACLMessage.INFORM, Protocol.FINAL_REVIEW, reconsider(context));
                } catch (Exception e) {
                    return failure(request, e);
                }
            }
        });
        addBehaviour(new CyclicBehaviour() {
            public void action() {
                ACLMessage message = receive(MessageTemplate.MatchOntology("SHUTDOWN"));
                if (message != null) doDelete(); else block();
            }
        });
        System.out.printf("[READY] %-22s spécialité: %s%n", getLocalName(), specialty());
    }

    protected abstract String specialty();
    protected abstract Review evaluate(Paper paper);
    protected abstract Review reconsider(ConsensusContext context);

    protected Review ownReview(ConsensusContext context) {
        return context.peerReviews().stream()
                .filter(r -> r.reviewer().equals(getLocalName()))
                .findFirst().orElseThrow();
    }

    private ACLMessage reply(ACLMessage request, int performative, String ontology, Serializable body)
            throws Exception {
        ACLMessage response = request.createReply();
        response.setPerformative(performative);
        response.setOntology(ontology);
        response.setContentObject(body);
        return response;
    }

    private ACLMessage failure(ACLMessage request, Exception e) {
        ACLMessage response = request.createReply();
        response.setPerformative(ACLMessage.FAILURE);
        response.setContent(e.getMessage());
        return response;
    }
}
