package embeddedcopilot.service;

import com.google.gson.JsonObject;
import java.util.HashSet;
import java.util.Set;

public class TurnTracker {
    public static class Decision {
        public enum Kind { IGNORE, USER_ECHO, TURN_STARTED, APPEND_TO_TURN, FINAL_FOR_TURN }
        public final Kind kind;
        public final String text;
        public Decision(Kind k) { this(k, ""); }
        public Decision(Kind k, String t) { kind = k; text = t; }
    }

    private final Set<String> dedupe = new HashSet<>();
    private String lastPrompt = null;
    private Long echoTs = null;
    private Long apiStartTs = null;

    public void startNewPrompt(String prompt) {
        lastPrompt = prompt;
        echoTs = null;
        apiStartTs = null;
        dedupe.clear();
    }

    public Decision handle(JsonObject root) {
        String type = root.has("type") ? root.get("type").getAsString() : "";
        if (!"say".equals(type) && !"ask".equals(type)) return new Decision(Decision.Kind.IGNORE);

        String say  = root.has("say")  ? root.get("say").getAsString()  : "";
        long ts     = root.has("ts")   ? root.get("ts").getAsLong()     : Long.MIN_VALUE;
        String text = root.has("text") ? root.get("text").getAsString() : "";

        if (lastPrompt == null || lastPrompt.isEmpty()) return new Decision(Decision.Kind.IGNORE);

        if (echoTs == null && "say".equals(type) && "text".equals(say) && lastPrompt.equals(text)) {
            echoTs = ts;
            return new Decision(Decision.Kind.USER_ECHO);
        }

        if (echoTs == null) return new Decision(Decision.Kind.IGNORE);

        if (apiStartTs == null && "say".equals(type) && "api_req_started".equals(say) && ts >= echoTs) {
            apiStartTs = ts;
            return new Decision(Decision.Kind.TURN_STARTED);
        }

        long gateTs = (apiStartTs != null ? apiStartTs : echoTs);

        if ("say".equals(type) && "text".equals(say) && ts >= gateTs) {
            if (lastPrompt.equals(text)) return new Decision(Decision.Kind.IGNORE);
            if (text != null && !text.isEmpty() && dedupe.add(text)) {
                return new Decision(Decision.Kind.APPEND_TO_TURN, text);
            }
            return new Decision(Decision.Kind.IGNORE);
        }

        if ("say".equals(type) && ts >= gateTs && ("completion_result".equals(say) || "text".equals(say))) {
            if (lastPrompt.equals(text)) return new Decision(Decision.Kind.IGNORE);
            if (text != null && !text.isEmpty() && dedupe.add(text)) {
                return new Decision(Decision.Kind.FINAL_FOR_TURN, text);
            } else {
                return new Decision(Decision.Kind.FINAL_FOR_TURN, "");
            }
        }

        return new Decision(Decision.Kind.IGNORE);
    }
}
