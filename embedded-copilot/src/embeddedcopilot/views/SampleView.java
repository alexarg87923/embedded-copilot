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
import embeddedcopilot.ui.ChatUIManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Button;

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

    private Font inputFont;
    private Color inputBorderColor;
    private Color inputBgColor;
    private Composite welcomeScreen;
    private Font welcomeTitleFont;
    private Font welcomeSubtitleFont;

    @Override
    public void createPartControl(Composite parent) {
        display = parent.getDisplay();

        projectService = new ProjectService();
        clineService = new ClineService(projectService);
        pollingService = new TaskPollingService(clineService);
        chatUIManager = new ChatUIManager(display);

        initializeInputStyling();

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

        createWelcomeScreen();

        createHistoryView();

        createInputField();

        loadTaskHistoryFromCline();
    }

    /**
     * Initialize styling resources for input field
     */
    private void initializeInputStyling() {
        FontData[] fontData = display.getSystemFont().getFontData();
        inputFont = new Font(display, fontData[0].getName(), 11, SWT.NORMAL);
        welcomeTitleFont = new Font(display, fontData[0].getName(), 14, SWT.BOLD);
        welcomeSubtitleFont = new Font(display, fontData[0].getName(), 10, SWT.NORMAL);
        inputBorderColor = new Color(display, 209, 209, 209);
        inputBgColor = new Color(display, 255, 255, 255);
    }

    /**
     * Creates the welcome screen
     */
    private void createWelcomeScreen() {
        welcomeScreen = new Composite(mainContainer, SWT.NONE);
        GridLayout welcomeLayout = new GridLayout(1, false);
        welcomeLayout.marginWidth = 0;
        welcomeLayout.marginHeight = 0;
        welcomeScreen.setLayout(welcomeLayout);
        welcomeScreen.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        welcomeScreen.setBackground(display.getSystemColor(SWT.COLOR_WHITE));

        // Scrollable container for welcome content
        org.eclipse.swt.custom.ScrolledComposite welcomeScrolled = new org.eclipse.swt.custom.ScrolledComposite(
            welcomeScreen, SWT.V_SCROLL);
        welcomeScrolled.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        welcomeScrolled.setExpandHorizontal(true);
        welcomeScrolled.setExpandVertical(true);
        welcomeScrolled.setBackground(display.getSystemColor(SWT.COLOR_WHITE));

        // Center container
        Composite centerContainer = new Composite(welcomeScrolled, SWT.NONE);
        GridLayout centerLayout = new GridLayout(1, false);
        centerLayout.marginWidth = 15;
        centerLayout.marginHeight = 20;
        centerLayout.verticalSpacing = 8;
        centerContainer.setLayout(centerLayout);
        centerContainer.setBackground(display.getSystemColor(SWT.COLOR_WHITE));

        welcomeScrolled.setContent(centerContainer);

        // Title
        Label titleLabel = new Label(centerContainer, SWT.NONE);
        titleLabel.setText("AI Copilot");
        titleLabel.setFont(welcomeTitleFont);
        titleLabel.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
        GridData titleData = new GridData(SWT.CENTER, SWT.CENTER, true, false);
        titleLabel.setLayoutData(titleData);

        // Subtitle
        Label subtitleLabel = new Label(centerContainer, SWT.NONE);
        subtitleLabel.setText("Your AI coding assistant");
        subtitleLabel.setFont(welcomeSubtitleFont);
        subtitleLabel.setForeground(new Color(display, 115, 115, 115));
        subtitleLabel.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
        GridData subtitleData = new GridData(SWT.CENTER, SWT.CENTER, true, false);
        subtitleLabel.setLayoutData(subtitleData);

        // Spacer
        Label spacer = new Label(centerContainer, SWT.NONE);
        GridData spacerData = new GridData(SWT.CENTER, SWT.CENTER, true, false);
        spacerData.heightHint = 5;
        spacer.setLayoutData(spacerData);
        spacer.setBackground(display.getSystemColor(SWT.COLOR_WHITE));

        // View History Button
        Button historyButton = new Button(centerContainer, SWT.PUSH);
        historyButton.setText("View Chat History");
        historyButton.setFont(inputFont);
        GridData buttonData = new GridData(SWT.CENTER, SWT.CENTER, false, false);
        buttonData.widthHint = 130;
        buttonData.heightHint = 28;
        historyButton.setLayoutData(buttonData);
        
        historyButton.addListener(SWT.Selection, e -> {
            showHistoryView();
        });

        // Instructions
        Label instructionsLabel = new Label(centerContainer, SWT.WRAP | SWT.CENTER);
        instructionsLabel.setText("Type a message below to start a new conversation");
        instructionsLabel.setFont(inputFont);
        instructionsLabel.setForeground(new Color(display, 140, 140, 140));
        instructionsLabel.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
        GridData instructionsData = new GridData(SWT.CENTER, SWT.CENTER, true, false);
        instructionsData.widthHint = 200;
        instructionsLabel.setLayoutData(instructionsData);

        // Set scrolled composite content size
        centerContainer.setSize(centerContainer.computeSize(SWT.DEFAULT, SWT.DEFAULT));
        welcomeScrolled.setMinSize(centerContainer.computeSize(SWT.DEFAULT, SWT.DEFAULT));
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

        // Hide history view by default
        historyView.setVisible(false);
        ((GridData) historyView.getLayoutData()).exclude = true;

        // Header with title and back button
        Composite headerContainer = new Composite(historyView, SWT.NONE);
        GridLayout headerLayout = new GridLayout(2, false);
        headerLayout.marginWidth = 15;
        headerLayout.marginHeight = 15;
        headerContainer.setLayout(headerLayout);
        headerContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        headerContainer.setBackground(display.getSystemColor(SWT.COLOR_WHITE));

        Label titleLabel = new Label(headerContainer, SWT.NONE);
        titleLabel.setText("Chat History");
        titleLabel.setFont(welcomeSubtitleFont);
        titleLabel.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
        titleLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Button backButton = new Button(headerContainer, SWT.PUSH);
        backButton.setText("← Back");
        backButton.setFont(inputFont);
        GridData backButtonData = new GridData(SWT.END, SWT.CENTER, false, false);
        backButton.setLayoutData(backButtonData);
        
        backButton.addListener(SWT.Selection, e -> {
            showWelcomeScreen();
        });

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
        inputLayout.marginWidth = 16;
        inputLayout.marginHeight = 16;
        inputContainer.setLayout(inputLayout);
        inputContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        inputContainer.setBackground(display.getSystemColor(SWT.COLOR_WHITE));

        // Wrapper for the input field to add custom border
        Composite inputWrapper = new Composite(inputContainer, SWT.NONE);
        GridLayout wrapperLayout = new GridLayout(1, false);
        wrapperLayout.marginWidth = 0;
        wrapperLayout.marginHeight = 0;
        inputWrapper.setLayout(wrapperLayout);
        inputWrapper.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        inputWrapper.setBackground(inputBgColor);
        
        // Add custom border with rounded corners
        inputWrapper.addPaintListener(e -> {
            e.gc.setForeground(inputBorderColor);
            e.gc.setLineWidth(1);
            e.gc.drawRoundRectangle(0, 0, inputWrapper.getSize().x - 1, inputWrapper.getSize().y - 1, 8, 8);
        });

        inputField = new Text(inputWrapper, SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
        GridData inputData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        inputData.heightHint = 80;
        inputData.horizontalIndent = 12;
        inputData.verticalIndent = 12;
        inputField.setLayoutData(inputData);
        inputField.setMessage("Message Assistant...");
        inputField.setFont(inputFont);
        inputField.setBackground(inputBgColor);

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

        welcomeScreen.setVisible(false);
        ((GridData) welcomeScreen.getLayoutData()).exclude = true;

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

        welcomeScreen.setVisible(false);
        ((GridData) welcomeScreen.getLayoutData()).exclude = true;

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

                startPolling(chatComposite, messageCopy);
                
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
     */
	private void startPolling(Composite chatComposite, String skipFirstEchoText) {
		System.out.println("[startPolling] Starting polling with TaskPollingService");

		if (skipFirstEchoText != null && !skipFirstEchoText.isEmpty()) {
			pollingService.setLastPrompt(skipFirstEchoText);
		}

		pollingService.startPolling(
			(text) -> display.asyncExec(() -> chatUIManager.updateLastAIMessage(chatComposite, text)),
			() -> System.out.println("[startPolling] Polling completed")
		);
	}

    /**
     * Shows the welcome screen and hides other views
     */
    private void showWelcomeScreen() {
        tabFolder.setVisible(false);
        ((GridData) tabFolder.getLayoutData()).exclude = true;
        historyView.setVisible(false);
        ((GridData) historyView.getLayoutData()).exclude = true;
        welcomeScreen.setVisible(true);
        ((GridData) welcomeScreen.getLayoutData()).exclude = false;
        mainContainer.layout(true, true);
    }

    /**
     * Shows the history view and hides the tab folder
     */
    private void showHistoryView() {
        welcomeScreen.setVisible(false);
        ((GridData) welcomeScreen.getLayoutData()).exclude = true;
    
        tabFolder.setVisible(false);
        ((GridData) tabFolder.getLayoutData()).exclude = true;
        historyView.setVisible(true);
        ((GridData) historyView.getLayoutData()).exclude = false;
        mainContainer.layout(true, true);
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
					pollingService.setLastPrompt(messageCopy);
					display.asyncExec(() -> {
						startPolling(chatComposite, messageCopy);
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

        chatUIManager.dispose();

        // Clean up styling resources
        if (inputFont != null && !inputFont.isDisposed()) {
            inputFont.dispose();
        }
        if (inputBorderColor != null && !inputBorderColor.isDisposed()) {
            inputBorderColor.dispose();
        }
        if (inputBgColor != null && !inputBgColor.isDisposed()) {
            inputBgColor.dispose();
        }
        if (welcomeTitleFont != null && !welcomeTitleFont.isDisposed()) {
            welcomeTitleFont.dispose();
        }
        if (welcomeSubtitleFont != null && !welcomeSubtitleFont.isDisposed()) {
            welcomeSubtitleFont.dispose();
        }

        super.dispose();
    }
}