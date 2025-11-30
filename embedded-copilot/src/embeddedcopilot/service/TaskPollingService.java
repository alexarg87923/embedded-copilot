package embeddedcopilot.service;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import embeddedcopilot.config.PollingConfig;
import embeddedcopilot.service.MessageProcessor.Message;

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
    private final MessageProcessor messageProcessor = new MessageProcessor();

    public TaskPollingService(ClineService clineService) {
        this.clineService = clineService;
    }

    /**
     * Starts polling for task updates.
     * Thread-safe - will stop any existing polling before starting new one.
     * 
     * @param onMessage callback for each message (receives Message object)
     * @param onComplete callback when polling completes
     * @param onAskRequiresApproval callback when an ask message requiring approval is detected (receives ask JSON text)
     * @param onToolUsed callback when a tool is used (for refreshing package explorer)
     */
    public void startPolling(Consumer<Message> onMessage, Runnable onComplete, Consumer<String> onAskRequiresApproval, Runnable onToolUsed) {
        synchronized (pollingLock) {
            stopPollingInternal();
            shouldStopPolling = false;

            pollingThread = new Thread(() -> {
                int pollCount = 0;

                try {
                    pollingLoop: while (!shouldStopPolling) {
                        long startTime = System.currentTimeMillis();
                        pollCount++;

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
                                            Message msg = messageProcessor.process(root);
                                            
                                            if (msg != null) {
                                                // Send all messages (including ASK_REQUIRES_APPROVAL) to the main callback
                                                // The ChatUIManager filtering will handle display logic
                                                if (onMessage != null) {
                                                    onMessage.accept(msg);
                                                }
                                                
                                                // Check if polling should stop based on this message
                                                String stopReason = shouldStopPolling(msg);
                                                if (stopReason != null) {
                                                    System.out.println("[TaskPollingService] " + stopReason);
                                                    shouldStopPolling = true;
                                                    break pollingLoop;
                                                }
                                                
                                                // Check if tool was used (for refreshing package explorer)
                                                // Check both "say" messages with tool and "ask" messages with tool
                                                boolean isToolMessage = (msg.sayType != null && msg.sayType.equals("tool")) ||
                                                                      (msg.askType != null && msg.askType.equals("tool"));
                                                
                                                if (isToolMessage && msg.text != null) {
                                                    // Check if it's a file creation/modification tool
                                                    String toolText = msg.text.toLowerCase();
                                                    if (toolText.contains("newfilecreated") || 
                                                        toolText.contains("write_to_file") || 
                                                        toolText.contains("editedexistingfile") ||
                                                        toolText.contains("filedeleted")) {
                                                        if (onToolUsed != null) {
                                                            onToolUsed.run();
                                                        }
                                                    }
                                                }
                                            }
                                        } catch (Exception e) {
                                            System.out.println("[TaskPollingService] Error processing message: " + e.getMessage());
                                        }
                                    }
                                }
                            }

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
        messageProcessor.startNewPrompt(prompt);
    }

    /**
     * Updates the prompt for the next message without clearing conversation history.
     * Use this when sending subsequent messages in an ongoing conversation.
     */
    public void updatePrompt(String prompt) {
        messageProcessor.updatePrompt(prompt);
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
    
    /**
     * Determines if polling should stop based on the received message.
     * Returns a reason string if polling should stop, null otherwise.
     *
     * @param msg the message to check
     * @return reason string if polling should stop, null if polling should continue
     */
    private String shouldStopPolling(Message msg) {
        // Stop polling if we receive an "ask" message requesting tool usage
        if (msg.askType != null && msg.askType.equals("tool")) {
            return "Received tool request (ask), stopping polling";
        }

        // Stop polling if we receive an "ask" message requesting command execution
        if (msg.askType != null && msg.askType.equals("command")) {
            return "Received command request (ask), stopping polling";
        }

        // Stop polling if we receive a completion_result (task finished)
        if (msg.askType != null && msg.askType.equals("completion_result")) {
            return "Task completed (completion_result), stopping polling";
        }

        // Add more stop conditions here as needed
        // Example:
        // if (someOtherCondition) {
        //     return "Some other reason to stop polling";
        // }

        return null; // Continue polling
    }
}