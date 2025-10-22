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
        chatLayout.verticalSpacing = 15;
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
                        ((ScrolledComposite) scrolled).setMinSize(
                            ((Composite) chatComposite.getData("chatContainer")).computeSize(SWT.DEFAULT, SWT.DEFAULT)
                        );
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

        chatContainer.layout(true, true);
        scrolled.setMinSize(chatContainer.computeSize(SWT.DEFAULT, SWT.DEFAULT));

        display.asyncExec(() -> scrolled.setOrigin(0, chatContainer.getSize().y));
    }
}