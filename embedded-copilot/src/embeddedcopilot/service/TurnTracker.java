package embeddedcopilot.service;

import com.google.gson.JsonObject;
import java.util.HashSet;
import java.util.Set;

public class TurnTracker {
    public static class Decision {
        public enum Kind { IGNORE, USER_ECHO, TURN_STARTED, APPEND_TO_TURN, FINAL_FOR_TURN, ASK_REQUIRES_APPROVAL }
        public final Kind kind;
        public final String text;
        public final JsonObject askData; // For ask messages, contains the full JSON object
        public Decision(Kind k) { this(k, "", null); }
        public Decision(Kind k, String t) { this(k, t, null); }
        public Decision(Kind k, String t, JsonObject askData) { 
            kind = k; 
            text = t; 
            this.askData = askData;
        }
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
        String ask  = root.has("ask")  ? root.get("ask").getAsString()  : "";
        long ts     = root.has("ts")   ? root.get("ts").getAsLong()     : Long.MIN_VALUE;
        String text = root.has("text") ? root.get("text").getAsString() : "";

        if (lastPrompt == null || lastPrompt.isEmpty()) return new Decision(Decision.Kind.IGNORE);

        // User echo detection
        if (echoTs == null && "say".equals(type) && "text".equals(say) && lastPrompt.equals(text)) {
            echoTs = ts;
            return new Decision(Decision.Kind.USER_ECHO);
        }

        if (echoTs == null) return new Decision(Decision.Kind.IGNORE);

        // API request started detection
        if (apiStartTs == null && "say".equals(type) && "api_req_started".equals(say) && ts >= echoTs) {
            apiStartTs = ts;
            return new Decision(Decision.Kind.TURN_STARTED);
        }

        long gateTs = (apiStartTs != null ? apiStartTs : echoTs);

        // Handle ask messages requiring approval (ask with tool)
        if ("ask".equals(type) && "tool".equals(ask) && ts >= gateTs) {
            // Return special decision type that requires approval
            // Store the full JSON object so we can parse and display it nicely
            return new Decision(Decision.Kind.ASK_REQUIRES_APPROVAL, text != null ? text : "", root);
        }

        // Handle say messages with tool (also may need approval, but currently just display)
        if ("say".equals(type) && "tool".equals(say) && ts >= gateTs) {
            if (text != null && !text.isEmpty()) {
                String toolMessage = formatToolMessage(text);
                if (dedupe.add(toolMessage)) {
                    return new Decision(Decision.Kind.APPEND_TO_TURN, toolMessage);
                }
            }
            return new Decision(Decision.Kind.IGNORE);
        }

        // Handle reasoning messages (optional - can be enabled to show AI's thinking)
        if ("say".equals(type) && "reasoning".equals(say) && ts >= gateTs) {
            if (text != null && !text.isEmpty() && dedupe.add(text)) {
                return new Decision(Decision.Kind.APPEND_TO_TURN, "\n[Thinking] " + text + "\n");
            }
            return new Decision(Decision.Kind.IGNORE);
        }

        // Handle regular text messages
        if ("say".equals(type) && "text".equals(say) && ts >= gateTs) {
            if (lastPrompt.equals(text)) return new Decision(Decision.Kind.IGNORE);
            if (text != null && !text.isEmpty() && dedupe.add(text)) {
                return new Decision(Decision.Kind.APPEND_TO_TURN, text);
            }
            return new Decision(Decision.Kind.IGNORE);
        }

        // Handle completion messages
        if (ts >= gateTs) {
            // Check for completion_result in both "say" and "ask" types
            if (("say".equals(type) && "completion_result".equals(say)) || 
                ("ask".equals(type) && "completion_result".equals(ask))) {
                if (lastPrompt.equals(text)) return new Decision(Decision.Kind.IGNORE);
                if (text != null && !text.isEmpty() && dedupe.add(text)) {
                    return new Decision(Decision.Kind.FINAL_FOR_TURN, text);
                } else {
                    return new Decision(Decision.Kind.FINAL_FOR_TURN, "");
                }
            }
        }

        return new Decision(Decision.Kind.IGNORE);
    }

    /**
     * Formats tool usage messages to be more readable
     */
    private String formatToolMessage(String toolJson) {
        try {
            JsonObject toolObj = com.google.gson.JsonParser.parseString(toolJson).getAsJsonObject();
            if (toolObj.has("tool")) {
                String toolName = toolObj.get("tool").getAsString();
                StringBuilder sb = new StringBuilder("\n[Using tool: ").append(toolName).append("]");
                
                // Add relevant parameters based on tool type
                if (toolObj.has("path")) {
                    sb.append("\nPath: ").append(toolObj.get("path").getAsString());
                }
                if (toolObj.has("command")) {
                    sb.append("\nCommand: ").append(toolObj.get("command").getAsString());
                }
                if (toolObj.has("regex")) {
                    sb.append("\nSearching for: ").append(toolObj.get("regex").getAsString());
                }
                
                sb.append("\n");
                return sb.toString();
            }
        } catch (Exception e) {
            // If parsing fails, return a simple message
            return "\n[Tool usage detected]\n";
        }
        return "\n[Tool usage]\n";
    }
}
