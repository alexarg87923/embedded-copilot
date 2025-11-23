package embeddedcopilot.service;

import com.google.gson.JsonObject;
import java.util.HashSet;
import java.util.Set;

/**
 * Processes all messages from Cline and categorizes them as user or AI messages
 */
public class MessageProcessor {
    public static class Message {
        public enum Type { USER, AI, ASK_REQUIRES_APPROVAL }
        public final Type type;
        public final String text;
        public final String sayType; // e.g., "text", "reasoning", "tool", "command", etc.
        public final String askType; // e.g., "tool", "command", etc.
        public final JsonObject rawJson; // Full JSON object for reference
        
        public Message(Type type, String text, String sayType, String askType, JsonObject rawJson) {
            this.type = type;
            this.text = text;
            this.sayType = sayType;
            this.askType = askType;
            this.rawJson = rawJson;
        }
    }

    private String lastPrompt = null;
    private Long echoTs = null;
    private Set<String> processedIds = new HashSet<>(); // Track processed messages by timestamp+type

    public void startNewPrompt(String prompt) {
        lastPrompt = prompt;
        echoTs = null;
        processedIds.clear();
    }

    /**
     * Processes a JSON message and returns a Message object if it should be displayed
     */
    public Message process(JsonObject root) {
        String type = root.has("type") ? root.get("type").getAsString() : "";
        if (!"say".equals(type) && !"ask".equals(type)) {
            return null; // Ignore non-say/ask messages
        }

        String say = root.has("say") ? root.get("say").getAsString() : "";
        String ask = root.has("ask") ? root.get("ask").getAsString() : "";
        long ts = root.has("ts") ? root.get("ts").getAsLong() : Long.MIN_VALUE;
        String text = root.has("text") ? root.get("text").getAsString() : "";

        // Create unique ID for deduplication
        String messageId = ts + "_" + type + "_" + say + "_" + ask;
        if (processedIds.contains(messageId)) {
            return null; // Already processed
        }
        processedIds.add(messageId);

        // Detect user echo (user's message being echoed back)
        if (echoTs == null && "say".equals(type) && "text".equals(say) && 
            lastPrompt != null && lastPrompt.equals(text)) {
            echoTs = ts;
            return new Message(Message.Type.USER, text, say, ask, root);
        }

        // Handle ask messages requiring approval
        if ("ask".equals(type) && "tool".equals(ask)) {
            return new Message(Message.Type.ASK_REQUIRES_APPROVAL, text, say, ask, root);
        }

        // Everything after user echo is AI (or before if no echo detected yet)
        // Skip empty messages and certain internal messages
        if (text == null || text.isEmpty()) {
            // Some empty messages are still meaningful (like checkpoint_created)
            if ("checkpoint_created".equals(say) || "completion_result".equals(ask)) {
                return new Message(Message.Type.AI, "", say, ask, root);
            }
            return null;
        }

        // Skip user echo if we've already detected it
        if (echoTs != null && "say".equals(type) && "text".equals(say) && 
            lastPrompt != null && lastPrompt.equals(text)) {
            return null; // Duplicate user echo
        }

        // All other messages are AI messages
        return new Message(Message.Type.AI, text, say, ask, root);
    }

    /**
     * Formats a message for display based on its type
     */
    public static String formatMessage(Message msg) {
        if (msg == null) return "";

        switch (msg.sayType) {
            case "reasoning":
                return "[Thinking] " + msg.text;
            case "tool":
                return formatToolMessage(msg.text);
            case "command":
                return "[Command] " + msg.text;
            case "task_progress":
                return "[Progress]\n" + msg.text;
            case "command_output":
                return "[Output]\n" + msg.text;
            case "completion_result":
                return msg.text;
            case "text":
            default:
                return msg.text;
        }
    }

    private static String formatToolMessage(String toolJson) {
        try {
            JsonObject toolObj = com.google.gson.JsonParser.parseString(toolJson).getAsJsonObject();
            if (toolObj.has("tool")) {
                String toolName = toolObj.get("tool").getAsString();
                StringBuilder sb = new StringBuilder("[Tool: ").append(formatToolName(toolName)).append("]");
                
                if (toolObj.has("path")) {
                    sb.append("\nPath: ").append(toolObj.get("path").getAsString());
                }
                if (toolObj.has("command")) {
                    sb.append("\nCommand: ").append(toolObj.get("command").getAsString());
                }
                
                return sb.toString();
            }
        } catch (Exception e) {
            // If parsing fails, return a simple message
            return "[Tool usage]";
        }
        return "[Tool usage]";
    }

    private static String formatToolName(String toolName) {
        return toolName.replaceAll("([a-z])([A-Z])", "$1 $2");
    }
}

