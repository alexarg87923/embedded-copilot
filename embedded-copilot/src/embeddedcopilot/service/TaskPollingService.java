package embeddedcopilot.service;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import embeddedcopilot.config.PollingConfig;

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
    private final Object pollingLock = new Object();
    private final TurnTracker tracker = new TurnTracker();

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
            stopPollingInternal();
            shouldStopPolling = false;

            pollingThread = new Thread(() -> {
                StringBuilder currentAIMessage = new StringBuilder();
                boolean receivedFinalResponse = false;
                int noUpdateCount = 0;
                int pollCount = 0;

                try {
                    while (!shouldStopPolling && !receivedFinalResponse) {
                        long startTime = System.currentTimeMillis();
                        pollCount++;
                        boolean hadAnyUpdateThisPoll = false;

                        try {
                            String jsonOutput = clineService.getTaskViewJson();
                            if (jsonOutput != null && !jsonOutput.trim().isEmpty()) {
                                int firstBrace = jsonOutput.indexOf('{');
                                if (firstBrace >= 0) {
                                    String cleanJson = jsonOutput.substring(firstBrace);
                                    String[] jsonObjects = cleanJson.split("\\n(?=\\{)");
                                    for (String raw : jsonObjects) {
                                        String jsonStr = raw.trim();
                                        if (jsonStr.isEmpty()) continue;
                                        int lastBrace = jsonStr.lastIndexOf('}');
                                        if (lastBrace > 0) jsonStr = jsonStr.substring(0, lastBrace + 1);

                                        try {
                                            JsonObject root = JsonParser.parseString(jsonStr).getAsJsonObject();
                                            TurnTracker.Decision d = tracker.handle(root);
                                            switch (d.kind) {
                                                case USER_ECHO:
                                                case TURN_STARTED:
                                                case IGNORE:
                                                    break;
                                                case APPEND_TO_TURN:
                                                    currentAIMessage.append(d.text);
                                                    onTextUpdate.accept(currentAIMessage.toString());
                                                    hadAnyUpdateThisPoll = true;
                                                    noUpdateCount = 0;
                                                    break;
                                                case FINAL_FOR_TURN:
                                                    if (d.text != null && !d.text.isEmpty()) {
                                                        currentAIMessage.append(d.text);
                                                        onTextUpdate.accept(currentAIMessage.toString());
                                                    } else {
                                                        onTextUpdate.accept(currentAIMessage.toString());
                                                    }
                                                    receivedFinalResponse = true;
                                                    break;
                                            }
                                            if (receivedFinalResponse) break;
                                        } catch (Exception ignored) {}
                                    }
                                } else {
                                    noUpdateCount++;
                                }
                            } else {
                                noUpdateCount++;
                            }

                            if (receivedFinalResponse) break;

                            if (!hadAnyUpdateThisPoll) noUpdateCount++;
                            if (noUpdateCount >= PollingConfig.getMaxNoUpdatePolls()) break;

                            long elapsedTime = System.currentTimeMillis() - startTime;
                            long sleepTime = PollingConfig.getPollingIntervalMs() - elapsedTime;
                            if (sleepTime > 0) {
                                Thread.sleep(sleepTime);
                            } else {
                                Thread.yield();
                            }
                        } catch (InterruptedException ie) {
                            break;
                        } catch (Exception e) {
                            try {
                                Thread.sleep(PollingConfig.getPollingIntervalMs());
                            } catch (InterruptedException ie2) {
                                break;
                            }
                        }
                    }
                } finally {
                    if (onComplete != null) onComplete.run();
                }
            }, "TaskViewPollingThread");

            pollingThread.setDaemon(true);
            pollingThread.start();
        }
    }

    public void setLastPrompt(String prompt) {
        tracker.startNewPrompt(prompt);
    }


    /**
     * Stops the polling thread (internal method, does not acquire lock)
     */
    private void stopPollingInternal() {
        System.out.println("[TaskPollingService] Stopping polling (internal)");
        shouldStopPolling = true;

        if (pollingThread != null && pollingThread.isAlive()) {
            try {
                System.out.println("[TaskPollingService] Interrupting thread ID: " + pollingThread.getName());
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
}