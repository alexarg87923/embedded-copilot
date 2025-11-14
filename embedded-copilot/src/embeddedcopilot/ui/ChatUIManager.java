package embeddedcopilot.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;

/**
 * Manager for chat UI components and message rendering
 * Modern, Claude-inspired design
 */
public class ChatUIManager {

    private final Display display;
    private Font messageFont;
    private Font senderFont;
    
    // Modern color palette
    private Color bgColor;
    private Color userBubbleColor;
    private Color aiBubbleColor;
    private Color userTextColor;
    private Color aiTextColor;
    private Color senderTextColor;
    private Color borderColor;

    public ChatUIManager(Display display) {
        this.display = display;
        initializeResources();
    }

    /**
     * Initialize fonts and colors
     */
    private void initializeResources() {
        // Fonts
        FontData[] fontData = display.getSystemFont().getFontData();
        messageFont = new Font(display, fontData[0].getName(), 10, SWT.NORMAL);
        senderFont = new Font(display, fontData[0].getName(), 10, SWT.BOLD);
        
        // Modern color palette (Claude-inspired)
        bgColor = new Color(display, 255, 255, 255);              // Clean white background
        userBubbleColor = new Color(display, 242, 242, 237);      // Warm off-white for user
        aiBubbleColor = new Color(display, 255, 255, 255);        // Pure white for AI
        userTextColor = new Color(display, 25, 25, 25);           // Almost black text
        aiTextColor = new Color(display, 25, 25, 25);             // Almost black text
        senderTextColor = new Color(display, 115, 115, 115);      // Medium gray for labels
        borderColor = new Color(display, 209, 209, 209);          // Border for both bubbles
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

        ScrolledComposite scrolled = new ScrolledComposite(container, SWT.V_SCROLL);
        scrolled.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        scrolled.setExpandHorizontal(true);
        scrolled.setExpandVertical(true);
        scrolled.setBackground(bgColor);

        Composite chatContainer = new Composite(scrolled, SWT.NONE);
        GridLayout chatLayout = new GridLayout(1, false);
        chatLayout.marginWidth = 0;
        chatLayout.marginHeight = 20;
        chatLayout.verticalSpacing = 24;  // Generous spacing between messages
        chatContainer.setLayout(chatLayout);
        chatContainer.setBackground(bgColor);

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
                    chatContainer.layout(true, true);
                    ScrolledComposite scrolled = (ScrolledComposite) chatComposite.getData("scrolled");
                    if (scrolled instanceof ScrolledComposite) {
                        scrolled.setMinSize(chatContainer.computeSize(SWT.DEFAULT, SWT.DEFAULT));
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

        // Outer container with padding
        Composite outerContainer = new Composite(chatContainer, SWT.NONE);
        outerContainer.setData("role", isUser ? "user" : "assistant");
        GridLayout outerLayout = new GridLayout(1, false);
        outerLayout.marginWidth = 48;  // Side padding like Claude
        outerLayout.marginHeight = 0;
        outerContainer.setLayout(outerLayout);
        outerContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        outerContainer.setBackground(bgColor);

        // Inner message container
        Composite messageContainer = new Composite(outerContainer, SWT.NONE);
        GridLayout messageLayout = new GridLayout(1, false);
        messageLayout.marginWidth = 0;
        messageLayout.marginHeight = 0;
        messageLayout.verticalSpacing = 8;  // Space between label and bubble
        messageContainer.setLayout(messageLayout);
        messageContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        messageContainer.setBackground(bgColor);

        // Sender label
        Label senderLabel = new Label(messageContainer, SWT.NONE);
        senderLabel.setText(isUser ? "You" : "Assistant");
        senderLabel.setFont(senderFont);
        senderLabel.setForeground(senderTextColor);
        senderLabel.setBackground(bgColor);
        GridData labelData = new GridData(isUser ? SWT.END : SWT.FILL, SWT.CENTER, true, false);
        labelData.horizontalIndent = 0;
        senderLabel.setLayoutData(labelData);

        // Message bubble
        Composite bubble = new Composite(messageContainer, SWT.NONE);
        GridLayout bubbleLayout = new GridLayout(1, false);
        bubbleLayout.marginWidth = 16;   // Comfortable padding
        bubbleLayout.marginHeight = 14;  // Vertical padding
        bubble.setLayout(bubbleLayout);
        
        // User messages align right with max width, AI messages fill width
        GridData bubbleData = new GridData(isUser ? SWT.END : SWT.FILL, SWT.CENTER, !isUser, false);
        if (isUser) {
            bubbleData.widthHint = 400;  // Max width for user bubbles
        }
        bubble.setLayoutData(bubbleData);

        Color bubbleColor = isUser ? userBubbleColor : aiBubbleColor;
        Color textColor = isUser ? userTextColor : aiTextColor;
        
        bubble.setBackground(bubbleColor);
        
        // Add subtle border for both message types
        bubble.addPaintListener(e -> {
            e.gc.setForeground(borderColor);
            e.gc.setLineWidth(1);
            e.gc.drawRoundRectangle(0, 0, bubble.getSize().x - 1, bubble.getSize().y - 1, 8, 8);
        });

        // Message text
        StyledText messageText = new StyledText(bubble, SWT.WRAP | SWT.READ_ONLY);
        messageText.setText(text);
        messageText.setFont(messageFont);
        messageText.setBackground(bubbleColor);
        messageText.setForeground(textColor);
        messageText.setWordWrap(true);
        messageText.setMargins(0, 0, 0, 0);
        messageText.setLineSpacing(1);  // Better line height
        
        GridData textData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        textData.widthHint = 0;
        messageText.setLayoutData(textData);

        // Store reference for updates
        outerContainer.setData("messageText", messageText);

        chatContainer.layout(true, true);
        
        // Update scrollable area size
        scrolled.setMinSize(chatContainer.computeSize(SWT.DEFAULT, SWT.DEFAULT));

        // Scroll to bottom
        display.asyncExec(() -> {
            if (!scrolled.isDisposed() && !chatContainer.isDisposed()) {
                scrolled.showControl(outerContainer);
            }
        });
    }

    /**
     * Dispose of resources when done
     */
    public void dispose() {
        if (messageFont != null && !messageFont.isDisposed()) {
            messageFont.dispose();
        }
        if (senderFont != null && !senderFont.isDisposed()) {
            senderFont.dispose();
        }
        if (bgColor != null && !bgColor.isDisposed()) {
            bgColor.dispose();
        }
        if (userBubbleColor != null && !userBubbleColor.isDisposed()) {
            userBubbleColor.dispose();
        }
        if (aiBubbleColor != null && !aiBubbleColor.isDisposed()) {
            aiBubbleColor.dispose();
        }
        if (userTextColor != null && !userTextColor.isDisposed()) {
            userTextColor.dispose();
        }
        if (aiTextColor != null && !aiTextColor.isDisposed()) {
            aiTextColor.dispose();
        }
        if (senderTextColor != null && !senderTextColor.isDisposed()) {
            senderTextColor.dispose();
        }
        if (borderColor != null && !borderColor.isDisposed()) {
            borderColor.dispose();
        }
    }
}