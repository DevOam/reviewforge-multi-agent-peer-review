package ma.fpo.iaa.reviewforge;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import ma.fpo.iaa.reviewforge.domain.Paper;
import ma.fpo.iaa.reviewforge.ui.ReviewForgeEvents;
import ma.fpo.iaa.reviewforge.ui.ReviewForgeFrame;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicInteger;

public final class ReviewForgeApplication {
    private final AtomicInteger analyses = new AtomicInteger();
    private AgentContainer container;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ReviewForgeApplication app = new ReviewForgeApplication();
            ReviewForgeFrame frame = new ReviewForgeFrame(app::analyze);
            frame.addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) { app.shutdown(); }
            });
            frame.setVisible(true);
            app.startPlatformAsync();
        });
    }

    private void startPlatformAsync() {
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception {
                Runtime runtime = Runtime.instance();
                runtime.setCloseVM(false);
                Profile profile = new ProfileImpl();
                profile.setParameter(Profile.MAIN_HOST, "127.0.0.1");
                profile.setParameter(Profile.LOCAL_HOST, "127.0.0.1");
                profile.setParameter(Profile.MAIN_PORT, "0");
                profile.setParameter(Profile.GUI, "false");
                container = runtime.createMainContainer(profile);
                start("reviewer-methodology", "ma.fpo.iaa.reviewforge.agents.MethodologyReviewerAgent", null);
                start("reviewer-results", "ma.fpo.iaa.reviewforge.agents.ResultsReviewerAgent", null);
                start("reviewer-ethics", "ma.fpo.iaa.reviewforge.agents.EthicsReviewerAgent", null);
                start("author", "ma.fpo.iaa.reviewforge.agents.AuthorAgent", null);
                return null;
            }

            protected void done() {
                try {
                    get();
                    ReviewForgeEvents.publish(ReviewForgeEvents.Type.PLATFORM_READY,
                            "Plateforme JADE prête — 5 agents disponibles");
                } catch (Exception e) {
                    ReviewForgeEvents.publish(ReviewForgeEvents.Type.ERROR,
                            "Impossible de démarrer JADE : " + e.getMessage());
                }
            }
        }.execute();
    }

    private void analyze(Paper paper) {
        if (container == null) {
            ReviewForgeEvents.publish(ReviewForgeEvents.Type.ERROR, "La plateforme JADE démarre encore.");
            return;
        }
        try {
            start("chair-" + analyses.incrementAndGet(),
                    "ma.fpo.iaa.reviewforge.agents.ChairAgent", new Object[]{paper});
        } catch (Exception e) {
            ReviewForgeEvents.publish(ReviewForgeEvents.Type.ERROR, e.getMessage());
        }
    }

    private void start(String name, String className, Object[] args) throws Exception {
        AgentController controller = container.createNewAgent(name, className, args);
        controller.start();
    }

    private void shutdown() {
        try { Runtime.instance().shutDown(); } catch (Exception ignored) {}
    }
}
