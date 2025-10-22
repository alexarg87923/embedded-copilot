package embeddedcopilot.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import embeddedcopilot.config.PollingConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility to help discover and auto-configure new polling termination patterns.
 * Use this in development/debug mode to identify new response formats.
 */
public class PollingPatternDiscovery {

    private static boolean discoveryMode = false;
    private static Map<String, Integer> sayTypeFrequency = new HashMap<>();
    private static Map<String, Integer> fieldFrequency = new HashMap<>();

    /**
     * Enables discovery mode - logs all unique patterns found
     */
    public static void enableDiscoveryMode() {
        discoveryMode = true;
        System.out.println("[PollingPatternDiscovery] Discovery mode ENABLED");
        System.out.println("[PollingPatternDiscovery] Will log all unique response patterns");
    }

    /**
     * Disables discovery mode
     */
    public static void disableDiscoveryMode() {
        discoveryMode = false;
        System.out.println("[PollingPatternDiscovery] Discovery mode DISABLED");
    }

    /**
     * Analyzes a JSON response and logs patterns
     * 
     * @param jsonOutput the JSON response to analyze
     */
    public static void analyzeResponse(String jsonOutput) {
        if (!discoveryMode || jsonOutput == null || jsonOutput.trim().isEmpty()) {
            return;
        }

        try {
            JsonObject root = JsonParser.parseString(jsonOutput).getAsJsonObject();

            if (root.has("type") && root.get("type").getAsString().equals("say")) {

                if (root.has("say")) {
                    String sayType = root.get("say").getAsString();
                    sayTypeFrequency.put(sayType, sayTypeFrequency.getOrDefault(sayType, 0) + 1);

                    if (sayTypeFrequency.get(sayType) == 1) {
                        System.out.println("[PollingPatternDiscovery] NEW SAY TYPE: " + sayType);
                        System.out.println("[PollingPatternDiscovery] Full message: " + jsonOutput);

                        if (root.has("text")) {
                            System.out.println("[PollingPatternDiscovery] ⚠️ This appears to be a completion message!");
                            System.out.println("[PollingPatternDiscovery] Consider adding to config:");
                            System.out.println("    PollingConfig.addFinalSayType(\"" + sayType + "\");");
                        }
                    }
                }

                for (String key : root.keySet()) {
                    if (isCompletionIndicatorCandidate(key)) {
                        fieldFrequency.put(key, fieldFrequency.getOrDefault(key, 0) + 1);
                        
                        if (fieldFrequency.get(key) == 1) {
                            System.out.println("[PollingPatternDiscovery] NEW COMPLETION FIELD: " + key);
                            System.out.println("[PollingPatternDiscovery] Consider adding to config:");
                            System.out.println("    PollingConfig.addCompletionIndicator(\"" + key + "\");");
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[PollingPatternDiscovery] Error analyzing response: " + e.getMessage());
        }
    }

    /**
     * Checks if a field name looks like it might indicate completion
     */
    private static boolean isCompletionIndicatorCandidate(String fieldName) {
        String lower = fieldName.toLowerCase();
        return lower.contains("complete") || 
               lower.contains("finish") || 
               lower.contains("done") || 
               lower.contains("checkpoint") || 
               lower.contains("final") ||
               lower.contains("hash");
    }

    /**
     * Prints statistics about discovered patterns
     */
    public static void printDiscoveryStats() {
        System.out.println("\n[PollingPatternDiscovery] === DISCOVERY STATISTICS ===");

        System.out.println("\nSay Types Found:");
        sayTypeFrequency.forEach((type, count) -> {
            String status = PollingConfig.getFinalSayTypes().contains(type) ? "[CONFIGURED]" : "[NEW]";
            System.out.println("  " + status + " " + type + " (count: " + count + ")");
        });

        System.out.println("\nCompletion Indicator Fields:");
        fieldFrequency.forEach((field, count) -> {
            String status = PollingConfig.getCompletionIndicators().contains(field) ? "[CONFIGURED]" : "[NEW]";
            System.out.println("  " + status + " " + field + " (count: " + count + ")");
        });

        System.out.println("\n[PollingPatternDiscovery] === END STATISTICS ===\n");
    }

    /**
     * Resets discovery statistics
     */
    public static void resetStats() {
        sayTypeFrequency.clear();
        fieldFrequency.clear();
        System.out.println("[PollingPatternDiscovery] Statistics reset");
    }

    /**
     * Auto-configures newly discovered patterns (use with caution!)
     */
    public static void autoConfigureDiscoveredPatterns() {
        int added = 0;

        for (String sayType : sayTypeFrequency.keySet()) {
            if (!PollingConfig.getFinalSayTypes().contains(sayType)) {
                PollingConfig.addFinalSayType(sayType);
                added++;
            }
        }

        for (String field : fieldFrequency.keySet()) {
            if (!PollingConfig.getCompletionIndicators().contains(field)) {
                PollingConfig.addCompletionIndicator(field);
                added++;
            }
        }

        System.out.println("[PollingPatternDiscovery] Auto-configured " + added + " new patterns");
    }
}