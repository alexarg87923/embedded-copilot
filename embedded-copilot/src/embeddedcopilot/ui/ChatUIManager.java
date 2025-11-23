package embeddedcopilot.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import embeddedcopilot.service.MessageProcessor.Message;
import embeddedcopilot.service.MessageProcessor;

/**
 * Manager for chat UI components and message rendering
 */
public class ChatUIManager {

    private final Display display;

    public ChatUIManager(Display display) {
        this.display = display;
    }

    /**
     * Creates a chat composite with scrolling support
     * 
     * @param parent the parent composite
     * @return the created chat composite
     */
    public Composite createChatComposite(Composite parent) {
        Composite container = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        container.setLayout(layout);

        ScrolledComposite scrolled = new ScrolledComposite(container, SWT.V_SCROLL | SWT.BORDER);
        scrolled.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        scrolled.setExpandHorizontal(true);
        scrolled.setExpandVertical(true);

        Composite chatContainer = new Composite(scrolled, SWT.NONE);
        GridLayout chatLayout = new GridLayout(1, false);
        chatLayout.marginWidth = 10;
        chatLayout.marginHeight = 10;
        chatLayout.verticalSpacing = 5;
        chatContainer.setLayout(chatLayout);
        chatContainer.setBackground(display.getSystemColor(SWT.COLOR_WHITE));

        scrolled.setContent(chatContainer);

        container.setData("scrolled", scrolled);
        container.setData("chatContainer", chatContainer);

        return container;
    }

    /**
     * Adds a message to the chat composite
     * 
     * @param chatComposite the chat composite
     * @param text the message text
     * @param isUser true if this is a user message, false if AI
     */
    public void addMessage(Composite chatComposite, String text, boolean isUser) {
        display.asyncExec(() -> {
            addMessageSync(chatComposite, text, isUser);
        });
    }

    /**
     * Updates the last AI message in the chat (for streaming)
     * 
     * @param chatComposite the chat composite
     * @param text the updated message text
     */
    public void updateLastAIMessage(Composite chatComposite, String text) {
        display.asyncExec(() -> {
            Composite chatContainer = (Composite) chatComposite.getData("chatContainer");
            Control[] children = chatContainer.getChildren();

            Composite lastAssistantContainer = null;
            for (int i = children.length - 1; i >= 0; i--) {
                if (children[i] instanceof Composite) {
                    Composite c = (Composite) children[i];
                    Object role = c.getData("role");
                    if ("assistant".equals(role)) {
                        lastAssistantContainer = c;
                        break;
                    }
                }
            }

            if (lastAssistantContainer != null) {
                StyledText messageText = (StyledText) lastAssistantContainer.getData("messageText");
                if (messageText != null && !messageText.isDisposed()) {
                    messageText.setText(text);
                    lastAssistantContainer.layout(true, true);
                    Composite scrolled = (Composite) chatComposite.getData("scrolled");
                    if (scrolled instanceof ScrolledComposite) {
                        ScrolledComposite sc = (ScrolledComposite) scrolled;

                        // Check if user is currently scrolled to the bottom
                        int currentScrollY = sc.getOrigin().y;
                        int oldContainerHeight = chatContainer.getSize().y;
                        int viewportHeight = sc.getClientArea().height;
                        int oldMaxScrollY = Math.max(0, oldContainerHeight - viewportHeight);
                        boolean wasAtBottom = currentScrollY >= oldMaxScrollY - 10;

                        int containerWidth = sc.getClientArea().width;
                        sc.setMinSize(chatContainer.computeSize(containerWidth, SWT.DEFAULT));

                        // Only auto-scroll if user was already at the bottom
                        if (wasAtBottom) {
                            display.asyncExec(() -> {
                                int newContainerHeight = chatContainer.getSize().y;
                                int newViewportHeight = sc.getClientArea().height;
                                int newMaxScrollY = Math.max(0, newContainerHeight - newViewportHeight);
                                sc.setOrigin(0, newMaxScrollY);
                            });
                        }
                    }
                    return;
                }
            }

            addMessageSync(chatComposite, text, false);
        });
    }

    /**
     * Synchronously adds a message (must be called from UI thread)
     * 
     * @param chatComposite the chat composite
     * @param text the message text
     * @param isUser true if this is a user message, false if AI
     */
    private void addMessageSync(Composite chatComposite, String text, boolean isUser) {
        ScrolledComposite scrolled = (ScrolledComposite) chatComposite.getData("scrolled");
        Composite chatContainer = (Composite) chatComposite.getData("chatContainer");

        Composite messageContainer = new Composite(chatContainer, SWT.NONE);
        messageContainer.setData("role", isUser ? "user" : "assistant");  // ← tag role

        GridLayout messageLayout = new GridLayout(1, false);
        messageLayout.marginWidth = 0;
        messageLayout.marginHeight = 0;
        messageLayout.verticalSpacing = 5;
        messageContainer.setLayout(messageLayout);
        messageContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        messageContainer.setBackground(display.getSystemColor(SWT.COLOR_WHITE));

        Label senderLabel = new Label(messageContainer, SWT.NONE);
        senderLabel.setText(isUser ? "You" : "AI Assistant");
        senderLabel.setForeground(display.getSystemColor(SWT.COLOR_DARK_GRAY));
        senderLabel.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
        senderLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Composite bubble = new Composite(messageContainer, SWT.NONE);
        GridLayout bubbleLayout = new GridLayout(1, false);
        bubbleLayout.marginWidth = 12;
        bubbleLayout.marginHeight = 10;
        bubble.setLayout(bubbleLayout);
        bubble.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Color bubbleColor = isUser ? new Color(display, 230, 240, 255)
                                : new Color(display, 245, 245, 245);
        bubble.setBackground(bubbleColor);

        StyledText messageText = new StyledText(bubble, SWT.WRAP | SWT.READ_ONLY);
        messageText.setText(text);
        messageText.setBackground(bubbleColor);
        messageText.setWordWrap(true);
        GridData textData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        textData.widthHint = 0;
        messageText.setLayoutData(textData);

        messageContainer.setData("messageText", messageText);

        bubble.addDisposeListener(e -> bubbleColor.dispose());

        // Check if user is currently scrolled to the bottom
        int currentScrollY = scrolled.getOrigin().y;
        int oldContainerHeight = chatContainer.getSize().y;
        int viewportHeight = scrolled.getClientArea().height;
        int oldMaxScrollY = Math.max(0, oldContainerHeight - viewportHeight);
        boolean wasAtBottom = currentScrollY >= oldMaxScrollY - 10; // 10px threshold

        chatContainer.layout(true, true);
        int containerWidth = scrolled.getClientArea().width;
        scrolled.setMinSize(chatContainer.computeSize(containerWidth, SWT.DEFAULT));

        // Only auto-scroll if user was already at the bottom
        if (wasAtBottom) {
            display.asyncExec(() -> {
                int newContainerHeight = chatContainer.getSize().y;
                int newViewportHeight = scrolled.getClientArea().height;
                int newMaxScrollY = Math.max(0, newContainerHeight - newViewportHeight);
                scrolled.setOrigin(0, newMaxScrollY);
            });
        }
    }

    /**
     * Adds an ask message requiring approval with approve/deny buttons
     * 
     * @param chatComposite the chat composite
     * @param askJsonText the JSON text from the ask message
     * @param onApprove callback when approve button is clicked (receives message container)
     * @param onDeny callback when deny button is clicked (receives message container)
     */
    public void addAskMessage(Composite chatComposite, String askJsonText, java.util.function.Consumer<Composite> onApprove, java.util.function.Consumer<Composite> onDeny) {
        display.asyncExec(() -> {
            ScrolledComposite scrolled = (ScrolledComposite) chatComposite.getData("scrolled");
            Composite chatContainer = (Composite) chatComposite.getData("chatContainer");

            Composite messageContainer = new Composite(chatContainer, SWT.NONE);
            messageContainer.setData("role", "ask");  // Special role for ask messages

            GridLayout messageLayout = new GridLayout(1, false);
            messageLayout.marginWidth = 0;
            messageLayout.marginHeight = 0;
            messageLayout.verticalSpacing = 5;
            messageContainer.setLayout(messageLayout);
            messageContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            messageContainer.setBackground(display.getSystemColor(SWT.COLOR_WHITE));

            Label senderLabel = new Label(messageContainer, SWT.NONE);
            senderLabel.setText("AI Assistant - Action Required");
            senderLabel.setForeground(display.getSystemColor(SWT.COLOR_DARK_GRAY));
            senderLabel.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
            senderLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

            Composite bubble = new Composite(messageContainer, SWT.BORDER);
            GridLayout bubbleLayout = new GridLayout(1, false);
            bubbleLayout.marginWidth = 12;
            bubbleLayout.marginHeight = 10;
            bubbleLayout.verticalSpacing = 8;
            bubble.setLayout(bubbleLayout);
            bubble.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

            // Use a slightly different color to indicate this needs attention
            Color bubbleColor = new Color(display, 255, 250, 230);
            bubble.setBackground(bubbleColor);

            // Parse and display the tool information in a user-friendly way
            String displayText = formatToolAskMessage(askJsonText);
            
            Label infoLabel = new Label(bubble, SWT.WRAP);
            infoLabel.setText("The AI wants to perform the following action:");
            infoLabel.setBackground(bubbleColor);
            infoLabel.setForeground(display.getSystemColor(SWT.COLOR_DARK_GRAY));
            GridData infoData = new GridData(SWT.FILL, SWT.CENTER, true, false);
            infoData.widthHint = 0;
            infoLabel.setLayoutData(infoData);

            StyledText toolText = new StyledText(bubble, SWT.WRAP | SWT.READ_ONLY | SWT.BORDER);
            toolText.setText(displayText);
            toolText.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
            toolText.setWordWrap(true);
            GridData toolData = new GridData(SWT.FILL, SWT.CENTER, true, false);
            toolData.widthHint = 0;
            toolData.heightHint = 100;
            toolText.setLayoutData(toolData);

            // Button container
            Composite buttonContainer = new Composite(bubble, SWT.NONE);
            GridLayout buttonLayout = new GridLayout(2, false);
            buttonLayout.marginWidth = 0;
            buttonLayout.marginHeight = 0;
            buttonLayout.horizontalSpacing = 10;
            buttonContainer.setLayout(buttonLayout);
            buttonContainer.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
            buttonContainer.setBackground(bubbleColor);
            messageContainer.setData("buttonContainer", buttonContainer); // Store reference

            // Approve button (green)
            Button approveButton = new Button(buttonContainer, SWT.PUSH);
            approveButton.setText("Approve");
            approveButton.setBackground(new Color(display, 76, 175, 80)); // Green
            approveButton.setForeground(display.getSystemColor(SWT.COLOR_WHITE));
            GridData approveData = new GridData(SWT.CENTER, SWT.CENTER, false, false);
            approveData.widthHint = 100;
            approveButton.setLayoutData(approveData);
            approveButton.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    if (onApprove != null) {
                        onApprove.accept(messageContainer);
                    }
                }
            });

            // Deny button (red)
            Button denyButton = new Button(buttonContainer, SWT.PUSH);
            denyButton.setText("Deny");
            denyButton.setBackground(new Color(display, 244, 67, 54)); // Red
            denyButton.setForeground(display.getSystemColor(SWT.COLOR_WHITE));
            GridData denyData = new GridData(SWT.CENTER, SWT.CENTER, false, false);
            denyData.widthHint = 100;
            denyButton.setLayoutData(denyData);
            denyButton.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    if (onDeny != null) {
                        onDeny.accept(messageContainer);
                    }
                }
            });

            bubble.addDisposeListener(e -> {
                bubbleColor.dispose();
                approveButton.getBackground().dispose();
                denyButton.getBackground().dispose();
            });

            // Check if user is currently scrolled to the bottom
            int currentScrollY = scrolled.getOrigin().y;
            int oldContainerHeight = chatContainer.getSize().y;
            int viewportHeight = scrolled.getClientArea().height;
            int oldMaxScrollY = Math.max(0, oldContainerHeight - viewportHeight);
            boolean wasAtBottom = currentScrollY >= oldMaxScrollY - 10; // 10px threshold

            chatContainer.layout(true, true);
            int containerWidth = scrolled.getClientArea().width;
            scrolled.setMinSize(chatContainer.computeSize(containerWidth, SWT.DEFAULT));

            // Only auto-scroll if user was already at the bottom
            if (wasAtBottom) {
                display.asyncExec(() -> {
                    int newContainerHeight = chatContainer.getSize().y;
                    int newViewportHeight = scrolled.getClientArea().height;
                    int newMaxScrollY = Math.max(0, newContainerHeight - newViewportHeight);
                    scrolled.setOrigin(0, newMaxScrollY);
                });
            }
        });
    }

    /**
     * Hides the approve/deny buttons in an ask message
     */
    public void hideAskButtons(Composite askMessageContainer) {
        display.asyncExec(() -> {
            Object buttonContainerObj = askMessageContainer.getData("buttonContainer");
            if (buttonContainerObj instanceof Composite) {
                Composite buttonContainer = (Composite) buttonContainerObj;
                buttonContainer.setVisible(false);
                buttonContainer.getParent().layout(true, true);
            }
        });
    }

    /**
     * Adds a message from MessageProcessor
     */
    public void addMessage(Composite chatComposite, Message msg) {
        if (msg == null) return;
        
        String displayText = MessageProcessor.formatMessage(msg);
        boolean isUser = msg.type == Message.Type.USER;
        addMessage(chatComposite, displayText, isUser);
    }

    /**
     * Formats the tool ask message JSON into a user-friendly display string
     * Only shows tool name and path, not content or workspace information
     */
    private String formatToolAskMessage(String toolJsonText) {
        try {
            JsonObject toolObj = JsonParser.parseString(toolJsonText).getAsJsonObject();
            StringBuilder sb = new StringBuilder();

            if (toolObj.has("tool")) {
                String toolName = toolObj.get("tool").getAsString();
                sb.append("Tool: ").append(formatToolName(toolName));
            }

            if (toolObj.has("path")) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append("Path: ").append(toolObj.get("path").getAsString());
            }

            return sb.toString();
        } catch (Exception e) {
            // If parsing fails, return a simple message
            return "Tool operation";
        }
    }

    /**
     * Formats tool names to be more readable
     */
    private String formatToolName(String toolName) {
        // Convert camelCase to Title Case
        return toolName.replaceAll("([a-z])([A-Z])", "$1 $2");
    }
}