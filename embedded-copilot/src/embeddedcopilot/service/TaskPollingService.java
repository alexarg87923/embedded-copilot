package embeddedcopilot.service;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import embeddedcopilot.config.PollingConfig;
import embeddedcopilot.util.PollingPatternDiscovery;

/**
 * Service for polling Cline task updates.
 * Uses PollingConfig for flexible, runtime-configurable behavior.
 * Thread-safe to prevent multiple concurrent polling threads.
 */
public class TaskPollingService {
    private final ClineService clineService;
    private Thread pollingThread = null;
    private volatile boolean shouldStopPolling = false;
    private Set<String> processedTextChunks = new HashSet<>();
    private final Object pollingLock = new Object(); // Lock to prevent multiple threads

    public TaskPollingService(ClineService clineService) {
        this.clineService = clineService;
    }

    /**
     * Starts polling for task updates.
     * Thread-safe - will stop any existing polling before starting new one.
     * 
     * @param onTextUpdate callback for text updates (receives accumulated text)
     * @param onComplete callback when polling completes
     */
    public void startPolling(Consumer<String> onTextUpdate, Runnable onComplete) {
        synchronized (pollingLock) {
            System.out.println("[TaskPollingService] Starting polling");

            stopPollingInternal();

            processedTextChunks.clear();

            shouldStopPolling = false;

            pollingThread = new Thread(() -> {
                System.out.println("[PollingThread] Thread started with ID: " + Thread.currentThread().getId());

                StringBuilder currentAIMessage = new StringBuilder();
                boolean receivedFinalResponse = false;
                int noUpdateCount = 0;
                int pollCount = 0;

                try {
                    while (!shouldStopPolling && !receivedFinalResponse) {
                        long startTime = System.currentTimeMillis();
                        pollCount++;

                        try {
                            System.out.println("[PollingThread] Poll #" + pollCount + " starting...");
                            String jsonOutput = clineService.getTaskViewJson();

                            if (jsonOutput != null && !jsonOutput.trim().isEmpty()) {
                                PollingPatternDiscovery.analyzeResponse(jsonOutput);

                                JsonObject root = JsonParser.parseString(jsonOutput).getAsJsonObject();

                                if (isFinalResponse(root)) {
                                    if (root.has("text")) {
                                        String text = root.get("text").getAsString();

                                        System.out.println("[PollingThread] Received final response: " + text);

                                        if (!processedTextChunks.contains(text)) {
                                            processedTextChunks.add(text);
                                            currentAIMessage.append(text);

                                            String finalMessage = currentAIMessage.toString();
                                            onTextUpdate.accept(finalMessage);
                                        }
                                    }

                                    receivedFinalResponse = true;
                                    System.out.println("[PollingThread] Received final response, stopping polling");
                                    break;
                                }

                                if (root.has("streamingText")) {
                                    JsonArray streamingArray = root.getAsJsonArray("streamingText");

                                    boolean hadUpdate = false;
                                    for (JsonElement element : streamingArray) {
                                        JsonObject textObj = element.getAsJsonObject();

                                        if (textObj.has("type") && textObj.get("type").getAsString().equals("say")) {
                                            if (textObj.has("say") && textObj.get("say").getAsString().equals("text")) {
                                                if (textObj.has("text")) {
                                                    String text = textObj.get("text").getAsString();

                                                    if (!processedTextChunks.contains(text)) {
                                                        processedTextChunks.add(text);
                                                        currentAIMessage.append(text);

                                                        String messageSoFar = currentAIMessage.toString();
                                                        onTextUpdate.accept(messageSoFar);
                                                        hadUpdate = true;
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (hadUpdate) {
                                        noUpdateCount = 0;
                                    } else {
                                        noUpdateCount++;
                                    }
                                } else {
                                    noUpdateCount++;
                                }

                                if (noUpdateCount >= PollingConfig.getMaxNoUpdatePolls()) {
                                    double secondsWaited = (PollingConfig.getMaxNoUpdatePolls() * PollingConfig.getPollingIntervalMs()) / 1000.0;
                                    System.out.println("[PollingThread] No updates for " + secondsWaited + " seconds, stopping polling");
                                    break;
                                }
                            }

                            long elapsedTime = System.currentTimeMillis() - startTime;
                            long sleepTime = PollingConfig.getPollingIntervalMs() - elapsedTime;

                            if (sleepTime > 0) {
                                System.out.println("[PollingThread] Poll #" + pollCount + " took " + elapsedTime + "ms, sleeping for " + sleepTime + "ms");
                                Thread.sleep(sleepTime);
                            } else {
                                System.out.println("[PollingThread] WARNING: Poll #" + pollCount + " took " + elapsedTime + "ms, longer than interval " + PollingConfig.getPollingIntervalMs() + "ms");
                                Thread.yield();
                            }

                        } catch (InterruptedException e) {
                            System.out.println("[PollingThread] Interrupted during poll #" + pollCount);
                            break;
                        } catch (Exception e) {
                            System.out.println("[PollingThread] Error during poll #" + pollCount + ": " + e.getMessage());
                            e.printStackTrace();
                            try {
                                Thread.sleep(PollingConfig.getPollingIntervalMs());
                            } catch (InterruptedException ie) {
                                System.out.println("[PollingThread] Interrupted during error recovery sleep");
                                break;
                            }
                        }
                    }
                } finally {
                    System.out.println("[PollingThread] Polling stopped after " + pollCount + " polls");
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
            }, "TaskViewPollingThread");

            pollingThread.setDaemon(true);
            pollingThread.start();
            
            System.out.println("[TaskPollingService] Polling thread started with ID: " + pollingThread.getId());
        }
    }

    /**
     * Checks if a JSON response is a final response that should terminate polling
     * 
     * @param root the JSON object to check
     * @return true if this is a final response
     */
    private boolean isFinalResponse(JsonObject root) {
        if (!root.has("type") || !root.get("type").getAsString().equals("say")) {
            return false;
        }

        if (root.has("say")) {
            String sayType = root.get("say").getAsString();
            if (PollingConfig.getFinalSayTypes().contains(sayType)) {
                System.out.println("[isFinalResponse] Detected final say type: " + sayType);
                return true;
            }
        }

        for (String indicator : PollingConfig.getCompletionIndicators()) {
            if (root.has(indicator)) {
                System.out.println("[isFinalResponse] Detected completion indicator: " + indicator);
                return true;
            }
        }

        return false;
    }

    /**
     * Stops the polling thread (internal method, does not acquire lock)
     */
    private void stopPollingInternal() {
        System.out.println("[TaskPollingService] Stopping polling (internal)");
        shouldStopPolling = true;

        if (pollingThread != null && pollingThread.isAlive()) {
            try {
                System.out.println("[TaskPollingService] Interrupting thread ID: " + pollingThread.getId());
                pollingThread.interrupt();
                pollingThread.join(2000);
                System.out.println("[TaskPollingService] Thread stopped");
            } catch (InterruptedException e) {
                System.out.println("[TaskPollingService] Interrupted while stopping polling thread");
            }
        }
        
        pollingThread = null;
    }

    /**
     * Stops the polling thread (public method, acquires lock)
     */
    public void stopPolling() {
        synchronized (pollingLock) {
            stopPollingInternal();
        }
    }

    /**
     * Clears processed text chunks (call when starting a new task)
     */
    public void clearProcessedChunks() {
        processedTextChunks.clear();
    }
    
    /**
     * Checks if polling is currently active
     */
    public boolean isPolling() {
        return pollingThread != null && pollingThread.isAlive() && !shouldStopPolling;
    }

    /**
     * Adds a new final "say" type that should trigger polling termination
     * @deprecated Use PollingConfig.addFinalSayType() instead
     */
    @Deprecated
    public static void addFinalSayType(String sayType) {
        PollingConfig.addFinalSayType(sayType);
    }

    /**
     * Adds a new completion indicator field
     * @deprecated Use PollingConfig.addCompletionIndicator() instead
     */
    @Deprecated
    public static void addCompletionIndicator(String fieldName) {
        PollingConfig.addCompletionIndicator(fieldName);
    }
}