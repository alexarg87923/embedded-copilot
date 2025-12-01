package embeddedcopilot.views;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.CTabFolder2Adapter;
import org.eclipse.swt.custom.CTabFolderEvent;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Widget;
import org.eclipse.ui.part.ViewPart;

import embeddedcopilot.model.ChatHistory;
import embeddedcopilot.model.ChatMessage;
import embeddedcopilot.service.ClineService;
import embeddedcopilot.service.ProjectService;
import embeddedcopilot.service.TaskPollingService;
import embeddedcopilot.service.MessageProcessor;
import embeddedcopilot.service.MessageProcessor.Message;
import embeddedcopilot.ui.ChatUIManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Main view for the Embedded Copilot plugin.
 * Provides a chat interface for interacting with Cline AI assistant.
 */
public class SampleView extends ViewPart {
    public static final String ID = "embeddedcopilot.views.SampleView";

    private Display display;
    private Composite mainContainer;
    private CTabFolder tabFolder;
    private Composite historyView;
    private Composite historyListContainer;
    private org.eclipse.swt.custom.ScrolledComposite historyScrolled;
    private Text inputField;

    private ProjectService projectService;
    private ClineService clineService;
    private TaskPollingService pollingService;
    private ChatUIManager chatUIManager;

    private List<ChatHistory> chatHistories = new ArrayList<>();
    private int chatCounter = 0;
    private org.eclipse.ui.IEditorPart currentDiffEditor = null; // Track diff editor (real workspace file)
    private String currentDiffFilePath = null; // Track the file path being edited
    private java.io.File currentOriginalBackup = null; // Original file backup (for DENY - restore to pre-edit state)
    private java.io.File currentCleanEditedBackup = null; // Clean edited backup (for APPROVE - Cline's actual edits)
    private volatile boolean hasPendingApproval = false; // Track if there's ANY pending approval (file diff or command)
    private volatile boolean alreadyAutoApproved = false; // Track if we already auto-approved (to prevent double approval)
    private Set<String> displayedMessageIds = new HashSet<>(); // Track displayed messages to prevent duplicates

    @Override
    public void createPartControl(Composite parent) {
        display = parent.getDisplay();

        projectService = new ProjectService();
        clineService = new ClineService(projectService);
        pollingService = new TaskPollingService(clineService);
        chatUIManager = new ChatUIManager(display);

        mainContainer = new Composite(parent, SWT.NONE);
        GridLayout mainLayout = new GridLayout(1, false);
        mainLayout.marginWidth = 0;
        mainLayout.marginHeight = 0;
        mainLayout.verticalSpacing = 0;
        mainContainer.setLayout(mainLayout);

        tabFolder = new CTabFolder(mainContainer, SWT.BORDER | SWT.CLOSE);
        tabFolder.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        tabFolder.setVisible(false);

        tabFolder.addCTabFolder2Listener(new CTabFolder2Adapter() {
            @Override
            public void close(CTabFolderEvent event) {
                if (tabFolder.getItemCount() == 1) {
                    pollingService.stopPolling();
                    showHistoryView();
                }
            }
        });

        createHistoryView();

        createInputField();

        loadTaskHistoryFromCline();
    }

    /**
     * Creates the history view showing past conversations
     */
    private void createHistoryView() {
        historyView = new Composite(mainContainer, SWT.NONE);
        GridLayout historyLayout = new GridLayout(1, false);
        historyLayout.marginWidth = 0;
        historyLayout.marginHeight = 0;
        historyView.setLayout(historyLayout);
        historyView.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        historyView.setBackground(display.getSystemColor(SWT.COLOR_WHITE));

        Label titleLabel = new Label(historyView, SWT.NONE);
        titleLabel.setText("Chat History");
        titleLabel.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
        GridData titleData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        titleData.horizontalIndent = 15;
        titleData.verticalIndent = 15;
        titleLabel.setLayoutData(titleData);

        historyScrolled = new org.eclipse.swt.custom.ScrolledComposite(historyView, SWT.V_SCROLL);
        historyScrolled.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        historyScrolled.setExpandHorizontal(true);
        historyScrolled.setExpandVertical(true);
        historyScrolled.setBackground(display.getSystemColor(SWT.COLOR_WHITE));

        historyListContainer = new Composite(historyScrolled, SWT.NONE);
        GridLayout listLayout = new GridLayout(1, false);
        listLayout.marginWidth = 10;
        listLayout.marginHeight = 10;
        listLayout.verticalSpacing = 5;
        historyListContainer.setLayout(listLayout);
        historyListContainer.setBackground(display.getSystemColor(SWT.COLOR_WHITE));

        historyScrolled.setContent(historyListContainer);
    }

    /**
     * Creates the input field at the bottom of the view
     */
    private void createInputField() {
        Composite inputContainer = new Composite(mainContainer, SWT.NONE);
        GridLayout inputLayout = new GridLayout(1, false);
        inputLayout.marginWidth = 10;
        inputLayout.marginHeight = 10;
        inputContainer.setLayout(inputLayout);
        inputContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        inputContainer.setBackground(display.getSystemColor(SWT.COLOR_WHITE));

        inputField = new Text(inputContainer, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
        GridData inputData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        inputData.heightHint = 60;
        inputField.setLayoutData(inputData);
        inputField.setMessage("Type a message to start a new chat...");

        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.keyCode == SWT.CR || e.keyCode == SWT.KEYPAD_CR) {
                    if ((e.stateMask & SWT.SHIFT) == 0) {
                        e.doit = false;

                        if (tabFolder.getItemCount() == 0 && !inputField.getText().isEmpty()) {
                            createNewChat();
                        } else {
                            sendMessage();
                        }
                    }
                }
            }
        });
    }

    /**
     * Loads task history from Cline CLI
     */
    private void loadTaskHistoryFromCline() {
        chatHistories.clear();
        addPlaceholder("Loading task history...");
        refreshHistoryView();

        new Thread(() -> {
            try {
                List<ChatHistory> parsed = clineService.listTasks();
                System.out.println("[loadTaskHistoryFromCline] Loaded " + parsed.size() + " tasks");

                display.asyncExec(() -> {
                    chatHistories.clear();
                    if (parsed.isEmpty()) {
                        addPlaceholder("No tasks found.");
                    } else {
                        chatHistories.addAll(parsed);
                    }
                    refreshHistoryView();
                });

            } catch (Exception ex) {
                System.out.println("[loadTaskHistoryFromCline] Error: " + ex.getMessage());
                ex.printStackTrace();
                display.asyncExec(() -> {
                    chatHistories.clear();
                    addPlaceholder("Failed to load history: " + ex.getMessage());
                    refreshHistoryView();
                });
            }
        }, "ClineTaskListThread").start();
    }

    /**
     * Adds a placeholder message to the history
     */
    private void addPlaceholder(String msg) {
        ChatHistory ph = new ChatHistory("Task History");
        ph.addMessage(new ChatMessage(msg, false));
        chatHistories.add(ph);
    }

    /**
     * Refreshes the history view with current chat histories
     */
    private void refreshHistoryView() {
        for (Control child : historyListContainer.getChildren()) {
            child.dispose();
        }

        for (ChatHistory history : chatHistories) {
            createHistoryItem(history);
        }

        historyListContainer.layout(true, true);
        historyScrolled.setMinSize(historyListContainer.computeSize(SWT.DEFAULT, SWT.DEFAULT));
    }

    /**
     * Creates a clickable history item in the list
     */
    private void createHistoryItem(ChatHistory history) {
        Function<Widget, String> widgetName = w -> {
            String base = (w instanceof Composite) ? "Composite"
                    : (w instanceof Label) ? "Label"
                            : w.getClass().getSimpleName();
            return base + "@" + Integer.toHexString(System.identityHashCode(w));
        };

        Color normalBg = new Color(display, 250, 250, 250);
        Color hoverBg = new Color(display, 235, 235, 235);

        Composite item = new Composite(historyListContainer, SWT.BORDER);
        GridLayout itemLayout = new GridLayout(1, false);
        itemLayout.marginWidth = 12;
        itemLayout.marginHeight = 10;
        item.setLayout(itemLayout);
        item.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        item.setBackground(normalBg);
        item.setBackgroundMode(SWT.INHERIT_DEFAULT);

        Label titleLabel = new Label(item, SWT.NONE);
        titleLabel.setText(history.getTitle());
        titleLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label previewLabel = new Label(item, SWT.NONE);
        String preview = history.getMessages().isEmpty() ? "" : history.getMessages().get(0).getText();
        if (preview.length() > 60)
            preview = preview.substring(0, 60) + "...";
        previewLabel.setText(preview);
        previewLabel.setForeground(display.getSystemColor(SWT.COLOR_DARK_GRAY));
        previewLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        item.addListener(SWT.MouseDown, e -> {
            System.out.println("[createHistoryItem] MouseDown on item -> openChatFromHistory()");
            openChatFromHistory(history);
        });

        final boolean[] hovering = { false };

        Listener childEnter = e -> System.out
                .println("[createHistoryItem] MouseEnter child: " + widgetName.apply(e.widget));
        Listener childExit = e -> System.out
                .println("[createHistoryItem] MouseExit child: " + widgetName.apply(e.widget));

        titleLabel.addListener(SWT.MouseEnter, childEnter);
        titleLabel.addListener(SWT.MouseExit, childExit);
        previewLabel.addListener(SWT.MouseEnter, childEnter);
        previewLabel.addListener(SWT.MouseExit, childExit);

        Listener moveFilter = new Listener() {
            @Override
            public void handleEvent(Event e) {
                Point cursorDisplay = display.getCursorLocation();
                Point rel = historyListContainer.toControl(cursorDisplay);
                Rectangle bounds = item.getBounds();

                boolean inside = bounds.contains(rel);
                if (!inside && hovering[0]) {
                    System.out.println("[createHistoryItem] MouseMove outside item -> FORCE normalBg (bounds="
                            + bounds + ", rel=" + rel + ")");
                    hovering[0] = false;
                    if (!item.isDisposed())
                        item.setBackground(normalBg);
                }
            }
        };
        display.addFilter(SWT.MouseMove, moveFilter);

        item.addDisposeListener(e -> {
            System.out.println("[createHistoryItem] Dispose item -> remove moveFilter & dispose colors");
            try {
                display.removeFilter(SWT.MouseMove, moveFilter);
            } catch (Exception ignored) {
            }
            try {
                normalBg.dispose();
            } catch (Exception ignored) {
            }
            try {
                hoverBg.dispose();
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * Opens a chat from history
     */
    private void openChatFromHistory(ChatHistory history) {
        System.out.println("[openChatFromHistory] Opening chat: " + history.getTitle());

        historyView.setVisible(false);
        ((GridData) historyView.getLayoutData()).exclude = true;
        tabFolder.setVisible(true);
        ((GridData) tabFolder.getLayoutData()).exclude = false;

        CTabItem item = new CTabItem(tabFolder, SWT.CLOSE);
        item.setText(history.getTitle());

        Composite chatComposite = chatUIManager.createChatComposite(tabFolder);
        item.setControl(chatComposite);

        for (ChatMessage msg : history.getMessages()) {
            chatUIManager.addMessage(chatComposite, msg.getText(), msg.isUser());
        }

        tabFolder.setSelection(item);
        mainContainer.layout(true, true);
        inputField.setFocus();
    }

    /**
     * Creates a new chat and starts a Cline task
     */
    private void createNewChat() {
        System.out.println("[createNewChat] Starting createNewChat");
        String initialMessage = inputField.getText().trim();
        System.out.println("[createNewChat] Initial message: " + initialMessage);

        if (initialMessage.isEmpty()) {
            System.out.println("[createNewChat] Message is empty, returning");
            return;
        }

        System.out.println("[createNewChat] Setting up UI...");

        historyView.setVisible(false);
        ((GridData) historyView.getLayoutData()).exclude = true;
        tabFolder.setVisible(true);
        ((GridData) tabFolder.getLayoutData()).exclude = false;

        chatCounter++;
        System.out.println("[createNewChat] Chat counter: " + chatCounter);
        CTabItem item = new CTabItem(tabFolder, SWT.CLOSE);
        item.setText("Creating chat...");

        Composite chatComposite = chatUIManager.createChatComposite(tabFolder);
        item.setControl(chatComposite);

        tabFolder.setSelection(item);
        mainContainer.layout(true, true);

        String messageCopy = initialMessage;
        inputField.setText("");

        System.out.println("[createNewChat] Starting background thread...");

        new Thread(() -> {
            try {
                System.out.println("[createNewChat Thread] Thread started");
                System.out.println("[createNewChat Thread] Creating Cline task...");

                clineService.createTask(messageCopy);
                System.out.println("[createNewChat Thread] Task created");

                display.asyncExec(() -> {
                    System.out.println("[createNewChat Thread asyncExec] Updating UI with task info");
                    String shortTitle = messageCopy.length() > 30 ? messageCopy.substring(0, 30) + "..."
                            : messageCopy;
                    item.setText(shortTitle);

                    chatUIManager.addMessage(chatComposite, messageCopy, true);

                    System.out.println("[createNewChat Thread asyncExec] UI updated");
                });

                System.out.println("[createNewChat Thread] About to start polling...");

                startPolling(chatComposite, messageCopy, true); // true = new chat
                
                System.out.println("[createNewChat Thread] Polling started");

            } catch (Exception ex) {
                System.out.println("[createNewChat Thread] Exception occurred: " + ex.getMessage());
                ex.printStackTrace();
                display.asyncExec(() -> {
                    item.setText("Error");
                    chatUIManager.addMessage(chatComposite, "Failed to create chat: " + ex.getMessage(), false);
                });
            }
        }, "CreateClineTaskThread").start();

        System.out.println("[createNewChat] Method completed, thread running in background");
    }

    /**
     * Starts polling for task updates using TaskPollingService
     * 
     * @param chatComposite the chat composite to display messages in
     * @param skipFirstEchoText the first message text (for new chats) or null (for existing chats)
     * @param isNewChat true if this is a new chat (clears history), false if continuing existing chat (preserves history)
     */
	private void startPolling(Composite chatComposite, String skipFirstEchoText, boolean isNewChat) {
		System.out.println("[startPolling] Starting polling with TaskPollingService, isNewChat=" + isNewChat);

		if (skipFirstEchoText != null && !skipFirstEchoText.isEmpty()) {
			if (isNewChat) {
				// New chat → use setLastPrompt() which clears processedIds history
				pollingService.setLastPrompt(skipFirstEchoText);
				// Also clear displayedMessageIds for new chat
				displayedMessageIds.clear();
			} else {
				// Existing chat → use updatePrompt() which preserves processedIds history
				pollingService.updatePrompt(skipFirstEchoText);
			}
		} else if (isNewChat) {
			// New chat but no skipFirstEchoText - still clear displayedMessageIds
			displayedMessageIds.clear();
		}

		pollingService.startPolling(
			(msg) -> display.asyncExec(() -> {
				// Skip USER type messages - they're already displayed when the user sent them
				// These are just echoes from Cline confirming receipt
				if (msg.type == Message.Type.USER) {
					return;
				}

				// Check if this message has already been displayed (deduplication)
				String messageId = createMessageId(msg);
				if (displayedMessageIds.contains(messageId)) {
					System.out.println("[startPolling] Skipping duplicate message: " + messageId);
					return;
				}

				// Use the new filtering logic for all other messages
				if (msg.rawJson != null) {
					String jsonLine = msg.rawJson.toString();

					// Check if this is a file edit or creation tool request and show diff
					if (msg.askType != null && msg.askType.equals("tool") && msg.text != null) {
						try {
							JsonObject toolJson = JsonParser.parseString(msg.text).getAsJsonObject();
							String toolType = toolJson.has("tool") ? toolJson.get("tool").getAsString() : null;
							
							// Handle both editedExistingFile and newFileCreated
							if (toolType != null && (toolType.equals("editedExistingFile") || toolType.equals("newFileCreated"))) {
								String filePath = toolJson.has("path") ? toolJson.get("path").getAsString() : null;
								if (filePath != null) {
									// Clean up previous diff state
									cleanupPreviousDiff();

									// Store the file path for later use
									currentDiffFilePath = filePath;

									// Auto-approve in background, save backup, wait for Cline to apply, then show diff
									new Thread(() -> {
										try {
											// Save original backup first
											File originalBackup = projectService.saveBackup(filePath);
											if (originalBackup != null) {
												System.out.println("[SampleView] Saved original backup before auto-approving: " + filePath);
											}

											// Auto-approve (Cline will apply changes)
											clineService.sendAskResponse(true, "");
											alreadyAutoApproved = true; // Mark that we already approved
											System.out.println("[SampleView] Auto-approved tool request, waiting for Cline to apply changes...");

											// Wait a bit for Cline to apply changes
											Thread.sleep(1000);

											// Now show diff view (file should be modified by Cline now)
											display.asyncExec(() -> {
												projectService.showDiffViewFromBackup(filePath, originalBackup,
													(editor, origBackup, cleanBackup) -> {
														// Track the opened editor and BOTH backups
														currentDiffEditor = editor;
														currentOriginalBackup = origBackup;
														currentCleanEditedBackup = cleanBackup;
														System.out.println("[SampleView] Tracking backups - Original: " +
															(origBackup != null ? origBackup.getName() : "null") +
															", Clean: " + (cleanBackup != null ? cleanBackup.getName() : "null"));
													});
											});
										} catch (Exception e) {
											System.err.println("[SampleView] Error auto-approving and showing diff: " + e.getMessage());
											e.printStackTrace();
										}
									}, "AutoApproveAndShowDiffThread").start();
								}
							}
						} catch (Exception e) {
							System.out.println("[SampleView] Error parsing tool JSON for diff: " + e.getMessage());
						}
					}

					// Check if this message will show approval buttons
					// These are the ask types that require approval (from ChatUIManager.filterAskMessage)
					if (msg.askType != null &&
					    (msg.askType.equals("tool") ||
					     msg.askType.equals("command") ||
					     msg.askType.equals("api_req_failed") ||
					     msg.askType.equals("resume_task") ||
					     msg.askType.equals("resume_completed_task"))) {
						hasPendingApproval = true;
						System.out.println("[SampleView] Pending approval detected: " + msg.askType);
					}

					chatUIManager.processClineMessage(
						chatComposite,
						jsonLine,
						(askContainer) -> handleApprove(chatComposite, askContainer),
						(askContainer) -> handleDeny(chatComposite, askContainer)
					);
					
					// Mark this message as displayed
					displayedMessageIds.add(messageId);
					System.out.println("[startPolling] Added message to displayedMessageIds: " + messageId + " (total: " + displayedMessageIds.size() + ")");
				}
			}),
			() -> System.out.println("[startPolling] Polling completed"),
			(askJsonText) -> {
				// This callback is now handled by processClineMessage filtering
				// Keep it for backward compatibility but it's no longer needed
				System.out.println("[startPolling] Ask message detected (handled by filtering): " + askJsonText);
			},
			() -> display.asyncExec(() -> {
				// Refresh package explorer when tool is used
				projectService.refreshPackageExplorer();
			})
		);
	}

    /**
     * Creates a unique message ID for deduplication purposes
     * Uses timestamp, type, sayType, and askType to create a unique identifier
     */
    private String createMessageId(Message msg) {
        if (msg.rawJson == null) {
            // Fallback: use text content if no JSON
            return "msg_" + (msg.text != null ? msg.text.hashCode() : System.currentTimeMillis());
        }
        
        // Extract timestamp and type from JSON for unique ID
        long ts = msg.rawJson.has("ts") ? msg.rawJson.get("ts").getAsLong() : 0;
        String type = msg.rawJson.has("type") ? msg.rawJson.get("type").getAsString() : "";
        String say = msg.rawJson.has("say") ? msg.rawJson.get("say").getAsString() : "";
        String ask = msg.rawJson.has("ask") ? msg.rawJson.get("ask").getAsString() : "";
        
        // Create unique ID similar to MessageProcessor's approach
        return ts + "_" + type + "_" + say + "_" + ask;
    }

    /**
     * Shows the history view and hides the tab folder
     */
    private void showHistoryView() {
        tabFolder.setVisible(false);
        ((GridData) tabFolder.getLayoutData()).exclude = true;
        historyView.setVisible(true);
        ((GridData) historyView.getLayoutData()).exclude = false;
        mainContainer.layout(true, true);
    }

    /**
     * Handles an ask message that requires approval
     * 
     * @param chatComposite the chat composite
     * @param askJsonText the JSON text from the ask message
     */
    private void handleAskRequiresApproval(Composite chatComposite, String askJsonText) {
        System.out.println("[handleAskRequiresApproval] Ask requires approval: " + askJsonText);
        
        // Display the ask message with approve/deny buttons
        chatUIManager.addAskMessage(
            chatComposite,
            askJsonText,
            (askContainer) -> handleApprove(chatComposite, askContainer),
            (askContainer) -> handleDeny(chatComposite, askContainer)
        );
    }

    /**
     * Cleans up previous diff state (backup file, editor, etc.)
     */
    private void cleanupPreviousDiff() {
        // If there's a pending diff, clean it up
        if (currentOriginalBackup != null) {
            // Delete original backup file since user didn't approve or deny
            if (currentOriginalBackup.exists()) {
                currentOriginalBackup.delete();
                System.out.println("[SampleView] Deleted abandoned original backup file");
            }
            currentOriginalBackup = null;
        }
        if (currentCleanEditedBackup != null) {
            // Delete clean edited backup file since user didn't approve or deny
            if (currentCleanEditedBackup.exists()) {
                currentCleanEditedBackup.delete();
                System.out.println("[SampleView] Deleted abandoned clean edited backup file");
            }
            currentCleanEditedBackup = null;
        }
        currentDiffEditor = null;
        currentDiffFilePath = null;
        alreadyAutoApproved = false; // Reset auto-approval flag
        
        // Note: We don't clear hasPendingApproval here because it's handled separately in sendMessage
        // This allows command approvals (which don't have backup files) to be properly tracked
    }

    /**
     * Handles approve button click
     */
    private void handleApprove(Composite chatComposite, Composite askContainer) {
        System.out.println("[handleApprove] User approved");

        // Check if this is a stale button click (user already sent a new message)
        if (!hasPendingApproval) {
            System.out.println("[handleApprove] No active approval to process (stale button click)");
            if (askContainer != null) {
                chatUIManager.hideAskButtons(askContainer);
            }
            display.asyncExec(() -> {
                chatUIManager.addMessage(chatComposite, "⚠ This approval is no longer valid (you sent a new message)", false);
            });
            return;
        }

        // Get any feedback the user typed (but didn't send)
        String feedback = inputField.getText().trim();

        // Clear the pending approval flag
        hasPendingApproval = false;

        // Hide buttons immediately
        if (askContainer != null) {
            chatUIManager.hideAskButtons(askContainer);
        }

        // Save file path and backup files before clearing them (needed for restore)
        final String filePath = currentDiffFilePath;
        final java.io.File cleanEditedBackup = currentCleanEditedBackup;
        final java.io.File originalBackup = currentOriginalBackup;
        
        // Clear state immediately (restore will happen in thread)
        currentOriginalBackup = null;
        currentCleanEditedBackup = null;
        currentDiffEditor = null;
        currentDiffFilePath = null;

        // Clear input field since we're using the text as feedback
        if (!feedback.isEmpty()) {
            inputField.setText("");
        }

        new Thread(() -> {
            try {
                // Restore the clean edited version (Cline's actual edits without removed lines for highlighting)
                if (filePath != null && cleanEditedBackup != null) {
                    projectService.restoreFromBackup(filePath, cleanEditedBackup);
                    System.out.println("[SampleView] Restored clean edited version after approve");

                    // Give the restore operation time to complete before clearing highlights
                    // This ensures the document is updated first
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                // Clear diff highlights from the editor (will restore syntax highlighting)
                if (filePath != null) {
                    projectService.clearDiffHighlights(filePath);
                    System.out.println("[SampleView] Cleared diff highlights after approve");
                }
                
                // Clean up both backup files since changes are approved
                if (cleanEditedBackup != null && cleanEditedBackup.exists()) {
                    cleanEditedBackup.delete();
                    System.out.println("[SampleView] Deleted clean edited backup file (changes approved)");
                }
                if (originalBackup != null && originalBackup.exists()) {
                    originalBackup.delete();
                    System.out.println("[SampleView] Deleted original backup file (changes approved)");
                }
                
                // If we already auto-approved, don't send another approve (would be double approval)
                // Just send feedback as a regular message if provided
                if (alreadyAutoApproved) {
                    System.out.println("[handleApprove] Already auto-approved, skipping duplicate approve signal");
                    if (!feedback.isEmpty()) {
                        // Send feedback as a regular message
                        clineService.sendMessage(feedback);
                        System.out.println("[handleApprove] Sent feedback as regular message: " + feedback);
                    }
                } else {
                    // Normal approve flow (for non-file-diff approvals like commands)
                    String output = clineService.sendAskResponse(true, feedback);
                    System.out.println("[handleApprove] Output: " + output);
                }
                
                // Reset the flag
                alreadyAutoApproved = false;

                // Refresh package explorer after tool approval
                projectService.refreshPackageExplorer();

                final String feedbackCopy = feedback;
                display.asyncExec(() -> {
                    String approveMsg = feedbackCopy.isEmpty()
                        ? "✓ Approved"
                        : "✓ Approved with feedback: " + feedbackCopy;
                    chatUIManager.addMessage(chatComposite, approveMsg, false);

                    // Restart polling since we stopped it when we received the tool request
                    startPolling(chatComposite, null, false); // false = existing chat
                });
            } catch (Exception ex) {
                System.out.println("[handleApprove] Exception: " + ex.getMessage());
                ex.printStackTrace();
                display.asyncExec(() -> {
                    chatUIManager.addMessage(chatComposite, "Failed to approve: " + ex.getMessage(), false);
                });
            }
        }, "ApproveThread").start();
    }

    /**
     * Handles deny button click
     */
    private void handleDeny(Composite chatComposite, Composite askContainer) {
        System.out.println("[handleDeny] User denied");

        // Check if this is a stale button click (user already sent a new message)
        if (!hasPendingApproval) {
            System.out.println("[handleDeny] No active approval to process (stale button click)");
            if (askContainer != null) {
                chatUIManager.hideAskButtons(askContainer);
            }
            display.asyncExec(() -> {
                chatUIManager.addMessage(chatComposite, "⚠ This denial is no longer valid (you sent a new message)", false);
            });
            return;
        }

        // Get any feedback the user typed (but didn't send)
        String feedback = inputField.getText().trim();

        // Clear the pending approval flag
        hasPendingApproval = false;

        // Hide buttons immediately
        if (askContainer != null) {
            chatUIManager.hideAskButtons(askContainer);
        }

        // Restore file from original backup since changes are denied (if this was a file diff)
        final String filePath = currentDiffFilePath;
        final java.io.File originalBackup = currentOriginalBackup;
        final java.io.File cleanEditedBackup = currentCleanEditedBackup;

        // Clear state immediately (restore will happen synchronously)
        currentOriginalBackup = null;
        currentCleanEditedBackup = null;
        currentDiffEditor = null;
        currentDiffFilePath = null;

        // Restore the original version (pre-edit state)
        if (filePath != null && originalBackup != null) {
            projectService.restoreFromBackup(filePath, originalBackup);
            System.out.println("[SampleView] Restoring file from original backup (changes denied)");
        }

        // Clear input field since we're using the text as feedback
        if (!feedback.isEmpty()) {
            inputField.setText("");
        }

        new Thread(() -> {
            try {
                // Clean up both backup files since changes are denied
                if (originalBackup != null && originalBackup.exists()) {
                    originalBackup.delete();
                    System.out.println("[SampleView] Deleted original backup file (changes denied)");
                }
                if (cleanEditedBackup != null && cleanEditedBackup.exists()) {
                    cleanEditedBackup.delete();
                    System.out.println("[SampleView] Deleted clean edited backup file (changes denied)");
                }
                
                String output = clineService.sendAskResponse(false, feedback);
                System.out.println("[handleDeny] Output: " + output);

                final String feedbackCopy = feedback;
                display.asyncExec(() -> {
                    String denyMsg = feedbackCopy.isEmpty()
                        ? "✗ Denied"
                        : "✗ Denied with feedback: " + feedbackCopy;
                    chatUIManager.addMessage(chatComposite, denyMsg, false);
                    // Restart polling since we stopped it when we received the tool request
                    startPolling(chatComposite, null, false); // false = existing chat
                });
            } catch (Exception ex) {
                System.out.println("[handleDeny] Exception: " + ex.getMessage());
                ex.printStackTrace();
                display.asyncExec(() -> {
                    chatUIManager.addMessage(chatComposite, "Failed to deny: " + ex.getMessage(), false);
                });
            }
        }, "DenyThread").start();
    }

    /**
     * Sends a message in the current chat
     */
	private void sendMessage() {
		String message = inputField.getText().trim();
		if (message.isEmpty()) {
			return;
		}

		if (tabFolder.getItemCount() == 0) {
			createNewChat();
			return;
		}

		CTabItem activeTab = tabFolder.getSelection();
		if (activeTab == null) return;

		Composite chatComposite = (Composite) activeTab.getControl();

		// Check if there's a pending approval workflow - if so, auto-deny it
		if (hasPendingApproval) {
			System.out.println("[sendMessage] Pending approval detected - auto-denying before sending new message");
			hasPendingApproval = false;

			// Auto-deny the pending approval (no feedback for auto-deny)
			new Thread(() -> {
				try {
					clineService.sendAskResponse(false, "");
					System.out.println("[sendMessage] Auto-denied pending approval");

					display.asyncExec(() -> {
						chatUIManager.addMessage(chatComposite, "⚠ Previous approval request was automatically denied (you sent a new message)", false);
					});
				} catch (Exception ex) {
					System.out.println("[sendMessage] Failed to auto-deny: " + ex.getMessage());
				}
			}, "AutoDenyThread").start();

			// Give the auto-deny a moment to process before continuing
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		// Clean up any pending diff state (backup file will be deleted)
		// This invalidates any pending approve/deny buttons
		cleanupPreviousDiff();

		chatUIManager.addMessage(chatComposite, message, true);

		if (activeTab.getText().startsWith("Chat ") || activeTab.getText().equals("Creating chat...")) {
			String shortTitle = message.length() > 30 ? message.substring(0, 30) + "..." : message;
			activeTab.setText(shortTitle);
		}

		final String messageCopy = message;
		inputField.setText("");

		new Thread(() -> {
			try {
				System.out.println("[sendMessage] Sending message to cline task send: " + messageCopy);

				String output = clineService.sendMessage(messageCopy);
				System.out.println("[sendMessage] Output from cline task send: " + output);

				if (output.contains("Message sent successfully")) {
					System.out.println("[sendMessage] Message sent successfully");
					System.out.println("[sendMessage] Diagnostic: messageCopy='" + messageCopy + "', displayedMessageIds.size()=" + displayedMessageIds.size());
					pollingService.updatePrompt(messageCopy);
					display.asyncExec(() -> {
						startPolling(chatComposite, messageCopy, false); // false = existing chat
					});
				} else if (output.contains("Error:") || output.contains("failed")) {
					System.out.println("[sendMessage] Error sending message: " + output);
					display.asyncExec(() -> chatUIManager.addMessage(chatComposite, "Error sending message: " + output, false));
				}
			} catch (Exception ex) {
				System.out.println("[sendMessage] Exception sending message: " + ex.getMessage());
				ex.printStackTrace();
				display.asyncExec(() -> chatUIManager.addMessage(chatComposite, "Failed to send message: " + ex.getMessage(), false));
			}
		}, "SendMessageThread").start();
	}

    @Override
    public void setFocus() {
        inputField.setFocus();
    }

    @Override
    public void dispose() {
        System.out.println("[SampleView] Disposing view, stopping polling");
        pollingService.stopPolling();
        super.dispose();
    }
}