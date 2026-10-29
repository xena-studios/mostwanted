package co.xenastudios.mostwanted.backend.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The support and application forms: what each asks and how long an answer can be.
 * The website shows them from /v1/forms, so they're written once, here.
 */
public final class Forms {

    /** One question. {@code options} non-empty: a choice of those; otherwise text up to {@code max}. */
    public record Question(String id, String label, String hint, int max, boolean required, List<String> options) {

        static Question text(String id, String label, String hint, int max) {
            return new Question(id, label, hint, max, true, List.of());
        }

        static Question optional(String id, String label, String hint, int max) {
            return new Question(id, label, hint, max, false, List.of());
        }

        static Question choice(String id, String label, List<String> options) {
            return new Question(id, label, "", 32, true, options);
        }
    }

    /** What a ticket is about, and what each is called on the site. */
    public static final Map<String, String> CATEGORIES = new LinkedHashMap<>();

    static {
        CATEGORIES.put("report", "Report a player");
        CATEGORIES.put("appeal", "Appeal a punishment");
        CATEGORIES.put("bug", "A bug");
        CATEGORIES.put("store", "A purchase");
        CATEGORIES.put("other", "Something else");
    }

    public static final int SUBJECT_MAX = 100;
    public static final int BODY_MAX = 4000;

    private static final String AVAILABILITY = "Days, hours and your time zone.";

    /** Each application's questions, in order. */
    public static final Map<String, List<Question>> APPLICATIONS = Map.of(
            "guard", List.of(
                    Question.text("why", "Why do you want to be a guard?", "", 1000),
                    Question.text("experience", "Have you been a guard, or staff, anywhere before?", "Where, and what you did.", 1000),
                    Question.text("scenario", "A criminal is standing in a safe zone, taunting you. What do you do?", "", 1000),
                    Question.text("availability", "When can you play?", AVAILABILITY, 200)),
            "staff", List.of(
                    Question.choice("role", "Which role?", List.of("Helper", "Moderator", "Builder")),
                    Question.text("why", "Why do you want to join the team?", "", 1000),
                    Question.text("experience", "What have you done before?", "Staff on other servers, or builds you've made.", 1000),
                    Question.text("scenario", "Two players are arguing in chat, and one starts using slurs. What do you do?",
                            "Builders can skip to the next question with a short answer.", 1000),
                    Question.optional("portfolio", "Links to your work", "Builders: screenshots or a portfolio.", 500),
                    Question.text("availability", "When can you play?", AVAILABILITY, 200)),
            "creator", List.of(
                    Question.text("channels", "Where do you post?", "Links to your channels.", 500),
                    Question.text("audience", "How many people watch or follow you?", "Roughly, on your biggest channel.", 100),
                    Question.text("plans", "What would you make on MostWanted?", "", 1000)));

    private Forms() {}

    /** The answers as they're kept: only this form's questions, trimmed. Null with a reason if they won't do. */
    public static Map<String, String> check(String kind, Map<?, ?> answers, StringBuilder problem) {
        List<Question> questions = APPLICATIONS.get(kind);
        if (questions == null) {
            problem.append("There's no such application.");
            return null;
        }
        Map<String, String> kept = new LinkedHashMap<>();
        for (Question question : questions) {
            Object value = answers == null ? null : answers.get(question.id());
            String text = value instanceof String s ? s.strip() : "";
            if (text.isEmpty()) {
                if (question.required()) {
                    problem.append("Answer \"").append(question.label()).append("\".");
                    return null;
                }
                continue;
            }
            if (text.length() > question.max()) {
                problem.append("\"").append(question.label()).append("\" is ").append(question.max())
                        .append(" characters at most.");
                return null;
            }
            if (!question.options().isEmpty() && !question.options().contains(text)) {
                problem.append("Pick one for \"").append(question.label()).append("\".");
                return null;
            }
            kept.put(question.id(), text);
        }
        return kept;
    }
}
