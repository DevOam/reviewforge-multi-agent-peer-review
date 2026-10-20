package ma.fpo.iaa.reviewforge.ui;

import ma.fpo.iaa.reviewforge.domain.Rebuttal;
import ma.fpo.iaa.reviewforge.domain.Review;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class ReviewForgeEvents {
    public enum Type { PLATFORM_READY, PHASE, LOG, INITIAL_REVIEWS, REBUTTAL, FINAL_REVIEWS, DECISION, ERROR }
    public record Event(Type type, String message, Object data) {}
    public record Decision(double score, String label, List<Review> reviews, Rebuttal rebuttal) {}
    private static final List<Consumer<Event>> LISTENERS = new CopyOnWriteArrayList<>();
    private ReviewForgeEvents() {}
    public static void subscribe(Consumer<Event> listener) { LISTENERS.add(listener); }
    public static void publish(Type type, String message) { publish(type, message, null); }
    public static void publish(Type type, String message, Object data) {
        Event event = new Event(type, message, data);
        LISTENERS.forEach(listener -> listener.accept(event));
    }
}
