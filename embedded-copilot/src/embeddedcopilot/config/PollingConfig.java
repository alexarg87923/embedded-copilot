package embeddedcopilot.config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Configuration for task polling behavior.
 * Minimal, modern version — no legacy completion indicators.
 */
public class PollingConfig {
    private static int pollingIntervalMs = 1000;
    private static int maxNoUpdatePolls = 30;  // 30 seconds of no updates before stopping
    private static final Set<String> finalSayTypes = new HashSet<>(Arrays.asList(
        "text",
        "completion_result"
    ));

    public static int getPollingIntervalMs() { return pollingIntervalMs; }
    public static int getMaxNoUpdatePolls() { return maxNoUpdatePolls; }
    public static Set<String> getFinalSayTypes() { return finalSayTypes; }

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

    /** Debug print helper */
    public static void printConfig() {
        System.out.println("[PollingConfig] Current Configuration:");
        System.out.println("  Polling Interval: " + pollingIntervalMs + "ms");
        System.out.println("  Max No-Update Polls: " + maxNoUpdatePolls);
        System.out.println("  Final Say Types: " + finalSayTypes);
    }
}
