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
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.swt.custom.CTabFolderEvent;
import org.eclipse.swt.custom.CTabFolder2Adapter;
import java.io.InputStreamReader;
import java.io.InputStream;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Widget;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import java.util.function.Function;
import java.nio.file.StandardCopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.Bundle;
import org.eclipse.core.runtime.FileLocator;
import java.net.URL;


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
					showHistoryView();
				}
			}
		});

		createHistoryView();

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
				if (tabFolder.getItemCount() == 0 && !inputField.getText().isEmpty()) {
					createNewChat();
				}

				if (e.keyCode == SWT.CR || e.keyCode == SWT.KEYPAD_CR) {
					if ((e.stateMask & SWT.SHIFT) == 0) {
						e.doit = false;
						sendMessage();
					}
				}
			}
		});

		loadTaskHistoryFromCline();
	}

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
	
	private void loadTaskHistoryFromCline() {
	    chatHistories.clear();
	    addPlaceholder("Loading task history...");
	    refreshHistoryView();

	    new Thread(() -> {
	        try {
	            String cliBinaryPath = extractCliBinary();

	            ProcessBuilder pb = new ProcessBuilder(cliBinaryPath, "task", "list");
	            pb.redirectErrorStream(true);
	            Process proc = pb.start();

	            StringBuilder out = new StringBuilder();
	            try (BufferedReader br = new BufferedReader(
	                    new InputStreamReader(proc.getInputStream()))) {
	                String line;
	                while ((line = br.readLine()) != null) {
	                    out.append(line).append("\n");
	                }
	            }
	            proc.waitFor();

	            System.out.printf("Output from command: %s", out);

	            List<ChatHistory> parsed = parseClineTaskHistory(out.toString());

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
	            ex.printStackTrace();
	            display.asyncExec(() -> {
	                chatHistories.clear();
	                addPlaceholder("Failed to load history: " + ex.getMessage());
	                refreshHistoryView();
	            });
	        }
	    }, "ClineTaskListThread").start();
	}

	private String extractCliBinary() throws Exception {
	    String binaryName = "cline";

	    Bundle bundle = FrameworkUtil.getBundle(getClass());
	    if (bundle == null) {
	        throw new Exception("Could not get OSGi bundle");
	    }

	    URL bundleUrl = bundle.getEntry("bin/" + binaryName);
	    if (bundleUrl == null) {
	        throw new Exception("Binary not found in bundle: bin/" + binaryName);
	    }

	    URL fileUrl = FileLocator.toFileURL(bundleUrl);

	    InputStream in = fileUrl.openStream();

	    Path tempBinary = Files.createTempFile("cline", "");
	    Files.copy(in, tempBinary, StandardCopyOption.REPLACE_EXISTING);
	    in.close();

	    tempBinary.toFile().setExecutable(true);

	    return tempBinary.toString();
	}

	private void addPlaceholder(String msg) {
	    ChatHistory ph = new ChatHistory("Task History");
	    ph.messages.add(new ChatMessage(msg, false));
	    chatHistories.add(ph);
	}

	private List<ChatHistory> parseClineTaskHistory(String text) {
	    List<ChatHistory> result = new ArrayList<>();
	    String[] lines = text.split("\\R");

	    String id = null;
	    String message = null;
	    String usage = null;

	    for (String line : lines) {
	        if (line.startsWith("Task ID:")) {
	            if (message != null && id != null) {
	                ChatHistory h = new ChatHistory(message);
	                h.messages.add(new ChatMessage("Task ID: " + id, false));
	                if (usage != null) h.messages.add(new ChatMessage(usage, false));
	                result.add(h);
	                message = null;
	                usage = null;
	            }
	            id = line.substring("Task ID:".length()).trim();
	        } else if (line.startsWith("Message:")) {
	            message = line.substring("Message:".length()).trim();
	        } else if (line.startsWith("Usage")) {
	            usage = line.trim();
	        }
	    }
	    if (message != null && id != null) {
	        ChatHistory h = new ChatHistory(message);
	        h.messages.add(new ChatMessage("Task ID: " + id, false));
	        if (usage != null) h.messages.add(new ChatMessage(usage, false));
	        result.add(h);
	    }

	    return result;
	}

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

	private void createHistoryItem(ChatHistory history) {
	    Function<Widget, String> widgetName = w -> {
	        String base =
	            (w instanceof Composite) ? "Composite" :
	            (w instanceof Label)     ? "Label"     :
	            w.getClass().getSimpleName();
	        return base + "@" + Integer.toHexString(System.identityHashCode(w));
	    };

	    Color normalBg = new Color(display, 250, 250, 250);
	    Color hoverBg  = new Color(display, 235, 235, 235);

	    Composite item = new Composite(historyListContainer, SWT.BORDER);
	    GridLayout itemLayout = new GridLayout(1, false);
	    itemLayout.marginWidth = 12;
	    itemLayout.marginHeight = 10;
	    item.setLayout(itemLayout);
	    item.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

	    item.setBackground(normalBg);
	    item.setBackgroundMode(SWT.INHERIT_DEFAULT);

	    Label titleLabel = new Label(item, SWT.NONE);
	    titleLabel.setText(history.title);
	    titleLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

	    Label previewLabel = new Label(item, SWT.NONE);
	    String preview = history.messages.isEmpty() ? "" : history.messages.get(0).text;
	    if (preview.length() > 60) preview = preview.substring(0, 60) + "...";
	    previewLabel.setText(preview);
	    previewLabel.setForeground(display.getSystemColor(SWT.COLOR_DARK_GRAY));
	    previewLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

	    item.addListener(SWT.MouseDown, e -> {
	        System.out.println("MouseDown on item -> openChatFromHistory()");
	        openChatFromHistory(history);
	    });

	    final boolean[] hovering = { false };

//	    item.addListener(SWT.MouseEnter, e -> {
//	        System.out.println("MouseEnter " + widgetName.apply(item) + " -> set hoverBg");
//	        hovering[0] = true;
//	        item.setBackground(hoverBg);
//	        item.redraw();
//	        item.update();
//	    });
//
//	    item.addListener(SWT.MouseExit, e -> {
//	        System.out.println("MouseExit " + widgetName.apply(item) + " -> set normalBg");
//	        hovering[0] = false;
//	        item.setBackground(normalBg);
//	        item.redraw();
//	        item.update();
//	    });

	    Listener childEnter = e ->
	        System.out.println("MouseEnter child: " + widgetName.apply(e.widget));
	    Listener childExit  = e ->
	        System.out.println("MouseExit child: "  + widgetName.apply(e.widget));

	    titleLabel.addListener(SWT.MouseEnter, childEnter);
	    titleLabel.addListener(SWT.MouseExit,  childExit);
	    previewLabel.addListener(SWT.MouseEnter, childEnter);
	    previewLabel.addListener(SWT.MouseExit,  childExit);

	    Listener moveFilter = new Listener() {
	        @Override public void handleEvent(Event e) {
	            Point cursorDisplay = display.getCursorLocation();
	            Point rel = historyListContainer.toControl(cursorDisplay); // parent coords
	            Rectangle bounds = item.getBounds(); // parent coords

	            boolean inside = bounds.contains(rel);
	            if (!inside && hovering[0]) {
	                System.out.println("MouseMove outside item -> FORCE normalBg (bounds=" + bounds + ", rel=" + rel + ")");
	                hovering[0] = false;
	                if (!item.isDisposed()) item.setBackground(normalBg);
	            }
	        }
	    };
	    display.addFilter(SWT.MouseMove, moveFilter);

	    item.addDisposeListener(e -> {
	        System.out.println("Dispose item -> remove moveFilter & dispose colors");
	        try { display.removeFilter(SWT.MouseMove, moveFilter); } catch (Exception ignored) {}
	        try { normalBg.dispose(); } catch (Exception ignored) {}
	        try { hoverBg.dispose(); } catch (Exception ignored) {}
	    });
	}

	private void openChatFromHistory(ChatHistory history) {
		historyView.setVisible(false);
		((GridData) historyView.getLayoutData()).exclude = true;
		tabFolder.setVisible(true);
		((GridData) tabFolder.getLayoutData()).exclude = false;

		CTabItem item = new CTabItem(tabFolder, SWT.CLOSE);
		item.setText(history.title);
		
		Composite chatComposite = createChatComposite(tabFolder);
		item.setControl(chatComposite);

		for (ChatMessage msg : history.messages) {
			addMessageToComposite(chatComposite, msg.text, msg.isUser);
		}
		
		tabFolder.setSelection(item);
		mainContainer.layout(true, true);
		inputField.setFocus();
	}
	
	private void createNewChat() {
		historyView.setVisible(false);
		((GridData) historyView.getLayoutData()).exclude = true;
		tabFolder.setVisible(true);
		((GridData) tabFolder.getLayoutData()).exclude = false;

		chatCounter++;
		CTabItem item = new CTabItem(tabFolder, SWT.CLOSE);
		item.setText("Chat " + chatCounter);

		Composite chatComposite = createChatComposite(tabFolder);
		item.setControl(chatComposite);

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

		if (tabFolder.getItemCount() == 0) {
			createNewChat();
		}

		CTabItem activeTab = tabFolder.getSelection();
		if (activeTab == null) return;

		Composite chatComposite = (Composite) activeTab.getControl();

		addMessageToComposite(chatComposite, message, true);

		if (activeTab.getText().startsWith("Chat ")) {
			String shortTitle = message.length() > 30 ? message.substring(0, 30) + "..." : message;
			activeTab.setText(shortTitle);
		}

		inputField.setText("");

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