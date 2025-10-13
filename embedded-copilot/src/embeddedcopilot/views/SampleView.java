package embeddedcopilot.views;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;

import java.util.ArrayList;
import java.util.List;

public class SampleView extends ViewPart {

	public static final String ID = "embeddedcopilot.views.SampleView";
	
	private Display display;
	private Composite mainContainer;
	private CTabFolder tabFolder;
	private Composite historyView;
	private Composite historyListContainer;
	private ScrolledComposite historyScrolled;
	private Text inputField;
	
	private List<ChatHistory> chatHistories = new ArrayList<>();
	private int chatCounter = 0;
	
	// Inner class to store chat history
	private class ChatHistory {
		String title;
		List<ChatMessage> messages = new ArrayList<>();
		
		ChatHistory(String title) {
			this.title = title;
		}
	}
	
	private class ChatMessage {
		String text;
		boolean isUser;
		
		ChatMessage(String text, boolean isUser) {
			this.text = text;
			this.isUser = isUser;
		}
	}
	
	@Override
	public void createPartControl(Composite parent) {
		display = parent.getDisplay();
		
		// Main container
		mainContainer = new Composite(parent, SWT.NONE);
		GridLayout mainLayout = new GridLayout(1, false);
		mainLayout.marginWidth = 0;
		mainLayout.marginHeight = 0;
		mainLayout.verticalSpacing = 0;
		mainContainer.setLayout(mainLayout);
		
		// Create tab folder (initially hidden)
		tabFolder = new CTabFolder(mainContainer, SWT.BORDER | SWT.CLOSE);
		tabFolder.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		tabFolder.setVisible(false);
		
		// Add close listener for tabs
		tabFolder.addCTabFolder2Listener(new org.eclipse.swt.custom.CTabFolder2Adapter() {
			@Override
			public void close(org.eclipse.swt.custom.CTabFolderEvent event) {
				// If closing the last tab, show history view
				if (tabFolder.getItemCount() == 1) {
					showHistoryView();
				}
			}
		});
		
		// Create history view
		createHistoryView();
		
		// Input area at the bottom
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
		
		// Add key listener - create new chat on first keypress
		inputField.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				// If no tabs exist and user starts typing, create a new chat
				if (tabFolder.getItemCount() == 0 && !inputField.getText().isEmpty()) {
					createNewChat();
				}
				
				// Send on Enter (but allow Shift+Enter for new line)
				if (e.keyCode == SWT.CR || e.keyCode == SWT.KEYPAD_CR) {
					if ((e.stateMask & SWT.SHIFT) == 0) {
						e.doit = false;
						sendMessage();
					}
				}
			}
		});
		
		// Add some demo chat histories
		createDemoChatHistories();
	}
	
	private void createHistoryView() {
		historyView = new Composite(mainContainer, SWT.NONE);
		GridLayout historyLayout = new GridLayout(1, false);
		historyLayout.marginWidth = 0;
		historyLayout.marginHeight = 0;
		historyView.setLayout(historyLayout);
		historyView.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		historyView.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
		
		// Title
		Label titleLabel = new Label(historyView, SWT.NONE);
		titleLabel.setText("Chat History");
		titleLabel.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
		GridData titleData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		titleData.horizontalIndent = 15;
		titleData.verticalIndent = 15;
		titleLabel.setLayoutData(titleData);
		
		// Scrolled composite for history items
		historyScrolled = new ScrolledComposite(historyView, SWT.V_SCROLL);
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
	
	private void createDemoChatHistories() {
		// Create some demo chat histories
		ChatHistory chat1 = new ChatHistory("How to refactor Eclipse plugin?");
		chat1.messages.add(new ChatMessage("How do I refactor my Eclipse plugin?", true));
		chat1.messages.add(new ChatMessage("I can help you refactor your Eclipse plugin. What would you like to change?", false));
		chatHistories.add(chat1);
		
		ChatHistory chat2 = new ChatHistory("SWT Layout questions");
		chat2.messages.add(new ChatMessage("What's the best layout for SWT?", true));
		chat2.messages.add(new ChatMessage("It depends on your needs. GridLayout is very flexible for most cases.", false));
		chatHistories.add(chat2);
		
		ChatHistory chat3 = new ChatHistory("Plugin.xml configuration");
		chat3.messages.add(new ChatMessage("How do I configure plugin.xml?", true));
		chat3.messages.add(new ChatMessage("You can configure plugin.xml by adding extensions...", false));
		chatHistories.add(chat3);
		
		refreshHistoryView();
	}
	
	private void refreshHistoryView() {
		// Clear existing items
		for (org.eclipse.swt.widgets.Control child : historyListContainer.getChildren()) {
			child.dispose();
		}
		
		// Add history items
		for (ChatHistory history : chatHistories) {
			createHistoryItem(history);
		}
		
		historyListContainer.layout(true, true);
		historyScrolled.setMinSize(historyListContainer.computeSize(SWT.DEFAULT, SWT.DEFAULT));
	}
	
	private void createHistoryItem(ChatHistory history) {
		// Create color objects for hover effect
		Color normalBg = new Color(display, 250, 250, 250);
		Color hoverBg = new Color(display, 235, 235, 235);
		
		Composite item = new Composite(historyListContainer, SWT.BORDER);
		GridLayout itemLayout = new GridLayout(1, false);
		itemLayout.marginWidth = 12;
		itemLayout.marginHeight = 10;
		item.setLayout(itemLayout);
		item.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		item.setBackground(normalBg);
		
		Label titleLabel = new Label(item, SWT.NONE);
		titleLabel.setText(history.title);
		titleLabel.setBackground(normalBg);
		titleLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		
		Label previewLabel = new Label(item, SWT.NONE);
		String preview = history.messages.isEmpty() ? "" : history.messages.get(0).text;
		if (preview.length() > 60) {
			preview = preview.substring(0, 60) + "...";
		}
		previewLabel.setText(preview);
		previewLabel.setForeground(display.getSystemColor(SWT.COLOR_DARK_GRAY));
		previewLabel.setBackground(normalBg);
		previewLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		
		// Click to open chat
		item.addListener(SWT.MouseDown, e -> openChatFromHistory(history));
		titleLabel.addListener(SWT.MouseDown, e -> openChatFromHistory(history));
		previewLabel.addListener(SWT.MouseDown, e -> openChatFromHistory(history));
		
		// Hover effect - add to all widgets
		org.eclipse.swt.widgets.Listener hoverEnter = e -> {
			item.setBackground(hoverBg);
			titleLabel.setBackground(hoverBg);
			previewLabel.setBackground(hoverBg);
		};
		
		org.eclipse.swt.widgets.Listener hoverExit = e -> {
			item.setBackground(normalBg);
			titleLabel.setBackground(normalBg);
			previewLabel.setBackground(normalBg);
		};
		
		item.addListener(SWT.MouseEnter, hoverEnter);
		item.addListener(SWT.MouseExit, hoverExit);
		titleLabel.addListener(SWT.MouseEnter, hoverEnter);
		titleLabel.addListener(SWT.MouseExit, hoverExit);
		previewLabel.addListener(SWT.MouseEnter, hoverEnter);
		previewLabel.addListener(SWT.MouseExit, hoverExit);
		
		// Dispose colors when item is disposed
		item.addDisposeListener(e -> {
			normalBg.dispose();
			hoverBg.dispose();
		});
	}
	
	private void openChatFromHistory(ChatHistory history) {
		// Hide history, show tab folder
		historyView.setVisible(false);
		((GridData) historyView.getLayoutData()).exclude = true;
		tabFolder.setVisible(true);
		((GridData) tabFolder.getLayoutData()).exclude = false;
		
		// Create new tab with history
		CTabItem item = new CTabItem(tabFolder, SWT.CLOSE);
		item.setText(history.title);
		
		Composite chatComposite = createChatComposite(tabFolder);
		item.setControl(chatComposite);
		
		// Restore messages
		for (ChatMessage msg : history.messages) {
			addMessageToComposite(chatComposite, msg.text, msg.isUser);
		}
		
		tabFolder.setSelection(item);
		mainContainer.layout(true, true);
		inputField.setFocus();
	}
	
	private void createNewChat() {
		// Hide history, show tab folder
		historyView.setVisible(false);
		((GridData) historyView.getLayoutData()).exclude = true;
		tabFolder.setVisible(true);
		((GridData) tabFolder.getLayoutData()).exclude = false;
		
		chatCounter++;
		CTabItem item = new CTabItem(tabFolder, SWT.CLOSE);
		item.setText("Chat " + chatCounter);
		
		Composite chatComposite = createChatComposite(tabFolder);
		item.setControl(chatComposite);
		
		// Add initial AI greeting
		addMessageToComposite(chatComposite, "Hello! How can I help you today?", false);
		
		tabFolder.setSelection(item);
		mainContainer.layout(true, true);
	}
	
	private Composite createChatComposite(Composite parent) {
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
		
		// Store references for later use
		container.setData("scrolled", scrolled);
		container.setData("chatContainer", chatContainer);
		
		return container;
	}
	
	private void showHistoryView() {
		tabFolder.setVisible(false);
		((GridData) tabFolder.getLayoutData()).exclude = true;
		historyView.setVisible(true);
		((GridData) historyView.getLayoutData()).exclude = false;
		mainContainer.layout(true, true);
	}
	
	private void sendMessage() {
		String message = inputField.getText().trim();
		if (message.isEmpty()) {
			return;
		}
		
		// If no active tab, create one
		if (tabFolder.getItemCount() == 0) {
			createNewChat();
		}
		
		// Get current tab's chat composite
		CTabItem activeTab = tabFolder.getSelection();
		if (activeTab == null) return;
		
		Composite chatComposite = (Composite) activeTab.getControl();
		
		// Add user message
		addMessageToComposite(chatComposite, message, true);
		
		// Update tab title with first message if it's still "Chat X"
		if (activeTab.getText().startsWith("Chat ")) {
			String shortTitle = message.length() > 30 ? message.substring(0, 30) + "..." : message;
			activeTab.setText(shortTitle);
		}
		
		// Clear input
		inputField.setText("");
		
		// Simulate AI response
		Display.getDefault().timerExec(500, () -> {
			addMessageToComposite(chatComposite, message, false);
		});
	}
	
	private void addMessageToComposite(Composite chatComposite, String text, boolean isUser) {
		ScrolledComposite scrolled = (ScrolledComposite) chatComposite.getData("scrolled");
		Composite chatContainer = (Composite) chatComposite.getData("chatContainer");
		
		Composite messageContainer = new Composite(chatContainer, SWT.NONE);
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
		
		Color bubbleColor;
		if (isUser) {
			bubbleColor = new Color(display, 230, 240, 255);
		} else {
			bubbleColor = new Color(display, 245, 245, 245);
		}
		bubble.setBackground(bubbleColor);
		
		StyledText messageText = new StyledText(bubble, SWT.WRAP | SWT.READ_ONLY);
		messageText.setText(text);
		messageText.setBackground(bubbleColor);
		messageText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		
		bubble.addDisposeListener(e -> bubbleColor.dispose());
		
		chatContainer.layout(true, true);
		scrolled.setMinSize(chatContainer.computeSize(SWT.DEFAULT, SWT.DEFAULT));
		
		Display.getDefault().asyncExec(() -> {
			scrolled.setOrigin(0, chatContainer.getSize().y);
		});
	}

	@Override
	public void setFocus() {
		inputField.setFocus();
	}
}