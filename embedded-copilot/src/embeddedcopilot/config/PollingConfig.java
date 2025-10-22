package embeddedcopilot.config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Configuration for task polling behavior.
 * Can be modified at runtime without code changes.
 */
public class PollingConfig {
    private static int pollingIntervalMs = 1000;
    private static int maxNoUpdatePolls = 20; // 20 seconds with 1000ms interval
    private static Set<String> finalSayTypes = new HashSet<>(Arrays.asList(
        "text",
        "completion_result",
        "reasoning"
    ));
    private static Set<String> completionIndicators = new HashSet<>(Arrays.asList(
        "lastCheckpointHash"
    ));

    public static int getPollingIntervalMs() {
        return pollingIntervalMs;
    }

    public static int getMaxNoUpdatePolls() {
        return maxNoUpdatePolls;
    }

    public static Set<String> getFinalSayTypes() {
        return new HashSet<>(finalSayTypes);
    }

    public static Set<String> getCompletionIndicators() {
        return new HashSet<>(completionIndicators);
    }

    public static void setPollingIntervalMs(int intervalMs) {
        if (intervalMs > 0) {
            pollingIntervalMs = intervalMs;
            System.out.println("[PollingConfig] Set polling interval to " + intervalMs + "ms");
        }
    }

    public static void setMaxNoUpdatePolls(int maxPolls) {
        if (maxPolls > 0) {
            maxNoUpdatePolls = maxPolls;
            System.out.println("[PollingConfig] Set max no-update polls to " + maxPolls);
        }
    }

    public static void addFinalSayType(String sayType) {
        finalSayTypes.add(sayType);
        System.out.println("[PollingConfig] Added final say type: " + sayType);
    }

    public static void removeFinalSayType(String sayType) {
        finalSayTypes.remove(sayType);
        System.out.println("[PollingConfig] Removed final say type: " + sayType);
    }

    public static void addCompletionIndicator(String indicator) {
        completionIndicators.add(indicator);
        System.out.println("[PollingConfig] Added completion indicator: " + indicator);
    }

    public static void removeCompletionIndicator(String indicator) {
        completionIndicators.remove(indicator);
        System.out.println("[PollingConfig] Removed completion indicator: " + indicator);
    }

    /**
     * Resets all configuration to defaults
     */
    public static void resetToDefaults() {
        pollingIntervalMs = 1000;
        maxNoUpdatePolls = 20;
        finalSayTypes = new HashSet<>(Arrays.asList("text", "completion_result", "reasoning"));
        completionIndicators = new HashSet<>(Arrays.asList("lastCheckpointHash"));
        System.out.println("[PollingConfig] Reset to default configuration");
    }

    /**
     * Prints current configuration
     */
    public static void printConfig() {
        System.out.println("[PollingConfig] Current Configuration:");
        System.out.println("  Polling Interval: " + pollingIntervalMs + "ms");
        System.out.println("  Max No-Update Polls: " + maxNoUpdatePolls);
        System.out.println("  Final Say Types: " + finalSayTypes);
        System.out.println("  Completion Indicators: " + completionIndicators);
    }
}