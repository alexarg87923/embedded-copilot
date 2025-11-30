package embeddedcopilot.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
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
import com.google.gson.JsonElement;
import embeddedcopilot.service.MessageProcessor.Message;
import embeddedcopilot.service.MessageProcessor;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manager for chat UI components and message rendering
 */
public class ChatUIManager {

    private final Display display;
    private boolean debugMode = false; // Set to true to see all messages
    private Long lastReasoningTimestamp = null; // Track reasoning start time

    public ChatUIManager(Display display) {
        this.display = display;
    }

    /**
     * Message display action after filtering
     */
    public enum DisplayAction {
        SHOW,           // Display as regular message
        SHOW_ASK,       // Display as ask message with approve/deny buttons
        HIDE,           // Don't display
        DEBUG_ONLY      // Only display if debugMode is enabled
    }

    /**
     * Result of message filtering
     */
    public static class FilteredMessage {
        public final DisplayAction action;
        public final String displayText;
        public final String rawJson;

        public FilteredMessage(DisplayAction action, String displayText, String rawJson) {
            this.action = action;
            this.displayText = displayText;
            this.rawJson = rawJson;
        }
    }

    /**
     * Enable or disable debug mode to see internal messages
     */
    public void setDebugMode(boolean enabled) {
        this.debugMode = enabled;
    }

    /**
     * Filters a ClineService JSON message and determines how to display it
     * 
     * @param jsonLine the JSON line from ClineService output
     * @return FilteredMessage indicating what to do with this message
     */
    public FilteredMessage filterClineMessage(String jsonLine) {
        try {
            JsonObject json = JsonParser.parseString(jsonLine).getAsJsonObject();
            
            // Check if this is a "say" message
            if (json.has("type") && "say".equals(json.get("type").getAsString())) {
                return filterSayMessage(json);
            }
            
            // Check if this is an "ask" message
            if (json.has("type") && "ask".equals(json.get("type").getAsString())) {
                return filterAskMessage(json);
            }
            
            // Unknown message type - hide by default
            return new FilteredMessage(DisplayAction.HIDE, null, jsonLine);
            
        } catch (Exception e) {
            // If we can't parse it, hide it
            System.err.println("[ChatUIManager] Failed to parse message: " + e.getMessage());
            return new FilteredMessage(DisplayAction.HIDE, null, jsonLine);
        }
    }

    /**
     * Filters "say" type messages
     */
    private FilteredMessage filterSayMessage(JsonObject json) {
        String say = json.has("say") ? json.get("say").getAsString() : "";
        String text = json.has("text") ? json.get("text").getAsString() : "";
        long timestamp = json.has("ts") ? json.get("ts").getAsLong() : 0;
        
        // Handle reasoning - just store timestamp
        if ("reasoning".equals(say)) {
            lastReasoningTimestamp = timestamp;
            return new FilteredMessage(DisplayAction.HIDE, null, json.toString());
        }
        
        switch (say) {
            // Messages that should be displayed
            case "text":
                return new FilteredMessage(DisplayAction.SHOW, text, json.toString());
            
            case "error":
                return new FilteredMessage(DisplayAction.SHOW, 
                    "❌ Error: " + text, json.toString());
            
            case "completion_result":
                return new FilteredMessage(DisplayAction.SHOW, 
                    "✅ " + text, json.toString());
            
            // Task progress - show to user
            case "task_progress":
                return new FilteredMessage(DisplayAction.SHOW, 
                    "📋 Task Progress:\n" + text, json.toString());
            
            // Command being executed - show to user
            case "command":
                return new FilteredMessage(DisplayAction.SHOW, 
                    "⚡ Running: " + text, json.toString());
            
            // Messages that should be hidden (internal state)
            case "user_feedback":
            case "user_feedback_diff":
            case "api_req_started":
            case "api_req_finished":
            case "api_req_retried":
            case "command_output":
            case "tool":
            case "browser_action":
            case "browser_action_launch":
            case "shell_integration_warning":
            case "inspect_site_result":
            case "mcp_server_request_started":
            case "checkpoint_created":  // Internal checkpoint tracking
                return new FilteredMessage(DisplayAction.HIDE, null, json.toString());
            
            // Messages for debugging only
            case "api_req_failed":
            case "api_req_canceled":
                String debugText = "⚠️ API Request Issue: " + text;
                return new FilteredMessage(DisplayAction.DEBUG_ONLY, debugText, json.toString());
            
            // Unknown say type - hide by default
            default:
                System.out.println("[ChatUIManager] Unknown say type: " + say);
                return new FilteredMessage(DisplayAction.DEBUG_ONLY, 
                    "[" + say + "] " + text, json.toString());
        }
    }

    /**
     * Filters "ask" type messages
     */
    private FilteredMessage filterAskMessage(JsonObject json) {
        String ask = json.has("ask") ? json.get("ask").getAsString() : "";
        String text = json.has("text") ? json.get("text").getAsString() : "";
        
        switch (ask) {
            // Ask messages that require user approval with buttons
            case "tool":
            case "command":
            case "api_req_failed":
            case "resume_task":
            case "resume_completed_task":
                // Extract tool information for display
                String displayText = formatAskMessageForDisplay(json);
                return new FilteredMessage(DisplayAction.SHOW_ASK, displayText, json.toString());
            
            // Completion result - simple acknowledgment (no buttons needed)
            case "completion_result":
                return new FilteredMessage(DisplayAction.SHOW, "✅ Task completed", json.toString());
            
            // Command output - show as regular message (no approve/deny buttons)
            case "command_output":
                // Show the output so user can see what's happening, but no buttons
                return new FilteredMessage(DisplayAction.SHOW, text, json.toString());
            
            // Ask messages that should be hidden (handled internally)
            case "request_limit_reached":
            case "followup":
                return new FilteredMessage(DisplayAction.HIDE, null, json.toString());
            
            // Unknown ask type - show for safety (better to ask than auto-approve)
            default:
                System.out.println("[ChatUIManager] Unknown ask type: " + ask);
                return new FilteredMessage(DisplayAction.SHOW_ASK, text, json.toString());
        }
    }

    /**
     * Formats an ask message for user-friendly display
     */
    private String formatAskMessageForDisplay(JsonObject json) {
        String ask = json.has("ask") ? json.get("ask").getAsString() : "";
        String text = json.has("text") ? json.get("text").getAsString() : "";
        
        StringBuilder sb = new StringBuilder();
        
        switch (ask) {
            case "tool":
                sb.append("🔧 Tool Request\n\n");
                if (json.has("tool")) {
                    String toolName = json.get("tool").getAsString();
                    sb.append("Tool: ").append(formatToolName(toolName)).append("\n");
                }
                if (json.has("path")) {
                    sb.append("Path: ").append(json.get("path").getAsString()).append("\n");
                }
                if (!text.isEmpty()) {
                    sb.append("\n").append(text);
                }
                break;
            
            case "command":
                sb.append("⚡ Command Execution\n\n");
                if (json.has("command")) {
                    sb.append("Command: ").append(json.get("command").getAsString()).append("\n");
                }
                if (!text.isEmpty()) {
                    sb.append("\n").append(text);
                }
                break;
            
            case "api_req_failed":
                sb.append("⚠️ API Request Failed\n\n");
                sb.append(text);
                break;
            
            case "resume_task":
                sb.append("🔄 Resume Task\n\n");
                sb.append(text);
                break;
            
            case "resume_completed_task":
                sb.append("🔄 Resume Completed Task\n\n");
                sb.append(text);
                break;
            
            default:
                sb.append(text);
                break;
        }
        
        return sb.toString();
    }

    /**
     * Processes and displays a ClineService message based on filtering rules
     * 
     * @param chatComposite the chat composite
     * @param jsonLine the JSON line from ClineService
     * @param onApprove callback for ask messages when approved
     * @param onDeny callback for ask messages when denied
     */
    public void processClineMessage(Composite chatComposite, String jsonLine, 
            java.util.function.Consumer<Composite> onApprove, 
            java.util.function.Consumer<Composite> onDeny) {
        
        FilteredMessage filtered = filterClineMessage(jsonLine);
        
        // If we're about to show a message and there was recent reasoning, show thinking duration first
        if ((filtered.action == DisplayAction.SHOW || filtered.action == DisplayAction.SHOW_ASK) 
                && lastReasoningTimestamp != null) {
            try {
                JsonObject json = JsonParser.parseString(jsonLine).getAsJsonObject();
                if (json.has("ts")) {
                    long currentTimestamp = json.get("ts").getAsLong();
                    long durationMs = currentTimestamp - lastReasoningTimestamp;
                    long durationSeconds = Math.round(durationMs / 1000.0);
                    
                    // Show thinking indicator with duration (rounded to nearest second)
                    String thinkingMsg = String.format("🤔 Thought for %ds", durationSeconds);
                    addMessage(chatComposite, thinkingMsg, false);
                }
            } catch (Exception e) {
                System.err.println("[ChatUIManager] Error calculating reasoning duration: " + e.getMessage());
            }
            lastReasoningTimestamp = null; // Reset after showing
        }
        
        switch (filtered.action) {
            case SHOW:
                addMessage(chatComposite, filtered.displayText, false);
                break;
            
            case SHOW_ASK:
                addAskMessage(chatComposite, filtered.rawJson, onApprove, onDeny);
                break;
            
            case DEBUG_ONLY:
                if (debugMode) {
                    addMessage(chatComposite, "[DEBUG] " + filtered.displayText, false);
                }
                break;
            
            case HIDE:
                // Do nothing
                break;
        }
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
        messageText.setBackground(bubbleColor);
        messageText.setWordWrap(true);
        GridData textData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        textData.widthHint = 0;
        messageText.setLayoutData(textData);

        // Apply markdown rendering for AI messages
        if (!isUser) {
            applyMarkdownStyling(messageText, text, bubbleColor);
        } else {
            messageText.setText(text);
        }

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
     * Applies markdown styling to a StyledText widget.
     * Handles checkboxes, code blocks, inline code, bold, italic, and headers.
     */
    private void applyMarkdownStyling(StyledText styledText, String text, Color backgroundColor) {
        List<StyleRange> styleRanges = new ArrayList<>();
        StringBuilder plainText = new StringBuilder();

        // Track fonts and colors to dispose later
        List<Font> fontsToDispose = new ArrayList<>();
        List<Color> colorsToDispose = new ArrayList<>();

        // Process text line by line to handle different markdown elements
        String[] lines = text.split("\n", -1);
        int currentPos = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineStart = currentPos;

            // Handle checkboxes (task list items)
            if (line.matches("^\\s*-\\s*\\[[ x]\\].*")) {
                Matcher checkboxMatcher = Pattern.compile("^(\\s*-\\s*)(\\[[ x]\\])(.*)").matcher(line);
                if (checkboxMatcher.find()) {
                    String prefix = checkboxMatcher.group(1);
                    String checkbox = checkboxMatcher.group(2);
                    String content = checkboxMatcher.group(3);

                    plainText.append(prefix);
                    currentPos += prefix.length();

                    // Add checkbox with special styling
                    String checkboxSymbol = checkbox.equals("[x]") ? "☑" : "☐";
                    plainText.append(checkboxSymbol);

                    StyleRange checkboxStyle = new StyleRange();
                    checkboxStyle.start = currentPos;
                    checkboxStyle.length = checkboxSymbol.length();
                    if (checkbox.equals("[x]")) {
                        Color checkedColor = new Color(display, 76, 175, 80);  // Green for checked
                        colorsToDispose.add(checkedColor);
                        checkboxStyle.foreground = checkedColor;
                    } else {
                        checkboxStyle.foreground = display.getSystemColor(SWT.COLOR_DARK_GRAY);  // Gray for unchecked
                    }
                    checkboxStyle.fontStyle = SWT.BOLD;
                    styleRanges.add(checkboxStyle);

                    currentPos += checkboxSymbol.length();
                    plainText.append(content);
                    currentPos += content.length();
                }
            }
            // Handle headers (# ## ###)
            else if (line.matches("^#{1,6}\\s+.*")) {
                Matcher headerMatcher = Pattern.compile("^(#{1,6})\\s+(.*)").matcher(line);
                if (headerMatcher.find()) {
                    String hashes = headerMatcher.group(1);
                    String headerText = headerMatcher.group(2);

                    plainText.append(headerText);

                    // Create bold, larger font for headers
                    FontData[] fontData = styledText.getFont().getFontData();
                    FontData headerFontData = new FontData(fontData[0].getName(),
                        fontData[0].getHeight() + (4 - hashes.length()), SWT.BOLD);
                    Font headerFont = new Font(display, headerFontData);
                    fontsToDispose.add(headerFont);

                    StyleRange headerStyle = new StyleRange();
                    headerStyle.start = currentPos;
                    headerStyle.length = headerText.length();
                    headerStyle.font = headerFont;
                    headerStyle.foreground = display.getSystemColor(SWT.COLOR_DARK_BLUE);
                    styleRanges.add(headerStyle);

                    currentPos += headerText.length();
                }
            }
            // Regular line - process inline markdown
            else {
                currentPos = processInlineMarkdown(line, plainText, styleRanges, currentPos, fontsToDispose, colorsToDispose, backgroundColor);
            }

            // Add newline if not the last line
            if (i < lines.length - 1) {
                plainText.append("\n");
                currentPos++;
            }
        }

        // Set the plain text (with markdown removed)
        styledText.setText(plainText.toString());

        // Apply all style ranges
        for (StyleRange style : styleRanges) {
            try {
                styledText.setStyleRange(style);
            } catch (Exception e) {
                System.err.println("[ChatUIManager] Error applying style range: " + e.getMessage());
            }
        }

        // Dispose fonts and colors when widget is disposed
        styledText.addDisposeListener(e -> {
            for (Font font : fontsToDispose) {
                if (font != null && !font.isDisposed()) {
                    font.dispose();
                }
            }
            for (Color color : colorsToDispose) {
                if (color != null && !color.isDisposed()) {
                    color.dispose();
                }
            }
        });
    }

    /**
     * Processes inline markdown within a line (bold, italic, code, etc.)
     */
    private int processInlineMarkdown(String line, StringBuilder plainText,
            List<StyleRange> styleRanges, int currentPos, List<Font> fontsToDispose,
            List<Color> colorsToDispose, Color backgroundColor) {

        int pos = 0;

        // Handle code blocks first (```)
        Pattern codeBlockPattern = Pattern.compile("```([\\s\\S]*?)```");
        Matcher codeBlockMatcher = codeBlockPattern.matcher(line);

        if (codeBlockMatcher.find()) {
            // Add text before code block
            if (codeBlockMatcher.start() > 0) {
                String before = line.substring(0, codeBlockMatcher.start());
                plainText.append(before);
                currentPos += before.length();
            }

            // Add code block content with styling
            String code = codeBlockMatcher.group(1);
            plainText.append(code);

            FontData[] fontData = plainText.toString().isEmpty() ?
                display.getSystemFont().getFontData() :
                display.getSystemFont().getFontData();
            Font codeFont = new Font(display, "Courier New", fontData[0].getHeight(), SWT.NORMAL);
            fontsToDispose.add(codeFont);

            Color codeBackground = new Color(display, 240, 240, 240);
            Color codeForeground = new Color(display, 200, 0, 0);
            colorsToDispose.add(codeBackground);
            colorsToDispose.add(codeForeground);

            StyleRange codeStyle = new StyleRange();
            codeStyle.start = currentPos;
            codeStyle.length = code.length();
            codeStyle.font = codeFont;
            codeStyle.background = codeBackground;
            codeStyle.foreground = codeForeground;
            styleRanges.add(codeStyle);

            currentPos += code.length();

            // Add text after code block
            if (codeBlockMatcher.end() < line.length()) {
                String after = line.substring(codeBlockMatcher.end());
                plainText.append(after);
                currentPos += after.length();
            }

            return currentPos;
        }

        // Process character by character for inline code, bold, italic
        while (pos < line.length()) {
            // Check for inline code `code`
            if (line.charAt(pos) == '`' && pos + 1 < line.length()) {
                int endPos = line.indexOf('`', pos + 1);
                if (endPos > pos) {
                    String code = line.substring(pos + 1, endPos);
                    plainText.append(code);

                    FontData[] fontData = display.getSystemFont().getFontData();
                    Font codeFont = new Font(display, "Courier New", fontData[0].getHeight(), SWT.NORMAL);
                    fontsToDispose.add(codeFont);

                    Color codeBackground = new Color(display, 240, 240, 240);
                    Color codeForeground = new Color(display, 200, 0, 0);
                    colorsToDispose.add(codeBackground);
                    colorsToDispose.add(codeForeground);

                    StyleRange codeStyle = new StyleRange();
                    codeStyle.start = currentPos;
                    codeStyle.length = code.length();
                    codeStyle.font = codeFont;
                    codeStyle.background = codeBackground;
                    codeStyle.foreground = codeForeground;
                    styleRanges.add(codeStyle);

                    currentPos += code.length();
                    pos = endPos + 1;
                    continue;
                }
            }

            // Check for bold **text**
            if (pos + 1 < line.length() && line.charAt(pos) == '*' && line.charAt(pos + 1) == '*') {
                int endPos = line.indexOf("**", pos + 2);
                if (endPos > pos) {
                    String boldText = line.substring(pos + 2, endPos);
                    plainText.append(boldText);

                    StyleRange boldStyle = new StyleRange();
                    boldStyle.start = currentPos;
                    boldStyle.length = boldText.length();
                    boldStyle.fontStyle = SWT.BOLD;
                    styleRanges.add(boldStyle);

                    currentPos += boldText.length();
                    pos = endPos + 2;
                    continue;
                }
            }

            // Check for italic *text* (but not part of **)
            if (line.charAt(pos) == '*' &&
                (pos == 0 || line.charAt(pos - 1) != '*') &&
                (pos + 1 >= line.length() || line.charAt(pos + 1) != '*')) {
                int endPos = line.indexOf('*', pos + 1);
                if (endPos > pos && (endPos + 1 >= line.length() || line.charAt(endPos + 1) != '*')) {
                    String italicText = line.substring(pos + 1, endPos);
                    plainText.append(italicText);

                    StyleRange italicStyle = new StyleRange();
                    italicStyle.start = currentPos;
                    italicStyle.length = italicText.length();
                    italicStyle.fontStyle = SWT.ITALIC;
                    styleRanges.add(italicStyle);

                    currentPos += italicText.length();
                    pos = endPos + 1;
                    continue;
                }
            }

            // Regular character
            plainText.append(line.charAt(pos));
            currentPos++;
            pos++;
        }

        return currentPos;
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
     * 
     * @deprecated Use formatAskMessageForDisplay instead
     */
    private String formatToolAskMessage(String toolJsonText) {
        try {
            JsonObject toolObj = JsonParser.parseString(toolJsonText).getAsJsonObject();
            return formatAskMessageForDisplay(toolObj);
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