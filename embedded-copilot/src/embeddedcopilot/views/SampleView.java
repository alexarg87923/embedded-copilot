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
import java.util.Arrays;
import java.io.File;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SampleView extends ViewPart {
	public static final String ID = "embeddedcopilot.views.SampleView";

	private Display display;
	private Composite mainContainer;
	private CTabFolder tabFolder;
	private Composite historyView;
	private Composite historyListContainer;
	private ScrolledComposite historyScrolled;
	private Text inputField;
	private String activeTaskId = null;
	private List<ChatHistory> chatHistories = new ArrayList<>();
	private int chatCounter = 0;
	private String cliBinaryDir = null;
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
				String output = executeClineCommand("task", "list");
				System.out.printf("Output from command: %s", output);

				List<ChatHistory> parsed = parseClineTaskHistory(output);

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
	    if (cliBinaryDir != null) {
	        return cliBinaryDir + "/cline";
	    }

	    Bundle bundle = FrameworkUtil.getBundle(getClass());
	    if (bundle == null) {
	        throw new Exception("Could not get OSGi bundle");
	    }

	    Path tempDir = Files.createTempDirectory("cline-binaries");
	    cliBinaryDir = tempDir.toString();
	    
	    System.out.println("[extractCliBinary] Created temp directory: " + cliBinaryDir);

	    URL clineUrl = bundle.getEntry("bin/cline");
	    if (clineUrl == null) {
	        throw new Exception("Binary not found in bundle: bin/cline");
	    }
	    URL clineFileUrl = FileLocator.toFileURL(clineUrl);
	    InputStream clineIn = clineFileUrl.openStream();
	    Path clinePath = tempDir.resolve("cline");
	    Files.copy(clineIn, clinePath, StandardCopyOption.REPLACE_EXISTING);
	    clineIn.close();
	    clinePath.toFile().setExecutable(true);
	    System.out.println("[extractCliBinary] Extracted cline to: " + clinePath);

	    URL hostUrl = bundle.getEntry("bin/cline-host");
	    if (hostUrl == null) {
	        throw new Exception("Binary not found in bundle: bin/cline-host");
	    }
	    URL hostFileUrl = FileLocator.toFileURL(hostUrl);
	    InputStream hostIn = hostFileUrl.openStream();
	    Path hostPath = tempDir.resolve("cline-host");
	    Files.copy(hostIn, hostPath, StandardCopyOption.REPLACE_EXISTING);
	    hostIn.close();
	    hostPath.toFile().setExecutable(true);
	    System.out.println("[extractCliBinary] Extracted cline-host to: " + hostPath);

	    return clinePath.toString();
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

	private String createClineTask(String message) throws Exception {
	    System.out.println("[createClineTask] Starting with message: " + message);

	    try {
	        String output = executeClineCommand(
	            "task",
	            "new",
	            message,
	            "-s", "act-mode-api-provider=anthropic",
	            "-s", "act-mode-api-model-id=claude-sonnet-4.5"
	        );

	        System.out.println("[createClineTask] Output from cline task new: " + output);

	        String taskId = parseTaskIdFromOutput(output);
	        System.out.println("[createClineTask] Parsed task ID: " + taskId);

	        if (taskId == null) {
	            throw new Exception("Failed to parse task ID from output: " + output);
	        }

	        return taskId;
	    } catch (Exception ex) {
	        System.out.println("[createClineTask] Exception in createClineTask: " + ex.getMessage());
	        ex.printStackTrace();
	        throw ex;
	    }
	}

	private String parseTaskIdFromOutput(String output) {
	    System.out.println("[parseTaskIdFromOutput] Parsing output: " + output);

	    String[] lines = output.split("\\R");
	    System.out.println("[parseTaskIdFromOutput] Split into " + lines.length + " lines");

	    for (int i = 0; i < lines.length; i++) {
	        String line = lines[i];
	        System.out.println("[parseTaskIdFromOutput] Line " + i + ": " + line);
	        
	        if (line.contains("Task created successfully with ID:")) {
	            String[] parts = line.split(":");
	            if (parts.length >= 2) {
	                String taskId = parts[parts.length - 1].trim();
	                System.out.println("[parseTaskIdFromOutput] Found task ID: " + taskId);
	                return taskId;
	            }
	        }
	    }

	    System.out.println("[parseTaskIdFromOutput] No task ID found");
	    return null;
	}

	private String executeClineCommand(String... args) throws Exception {
	    System.out.println("[executeClineCommand] Starting with args: " + Arrays.toString(args));

	    String cliBinaryPath = extractCliBinary();
	    System.out.println("[executeClineCommand] CLI binary path: " + cliBinaryPath);

	    List<String> command = new ArrayList<>();
	    command.add(cliBinaryPath);
	    command.addAll(Arrays.asList(args));

	    System.out.println("[executeClineCommand] Full command: " + command);

	    ProcessBuilder pb = new ProcessBuilder(command);

	    File workingDir = new File(cliBinaryDir);
	    pb.directory(workingDir);
	    System.out.println("[executeClineCommand] Working directory: " + workingDir.getAbsolutePath());

	    Map<String, String> env = pb.environment();
	    if (!env.containsKey("HOME") || env.get("HOME") == null || env.get("HOME").isEmpty()) {
	        String home = System.getProperty("user.home");
	        env.put("HOME", home);
	        System.out.println("[executeClineCommand] Set HOME to: " + home);
	    }

	    String currentPath = env.get("PATH");
	    String newPath = cliBinaryDir + File.pathSeparator + (currentPath != null ? currentPath : "");
	    env.put("PATH", newPath);

	    pb.redirectErrorStream(true);

	    pb.redirectInput(ProcessBuilder.Redirect.PIPE);

	    Process proc = pb.start();
	    System.out.println("[executeClineCommand] Process started");

	    proc.getOutputStream().close();
	    System.out.println("[executeClineCommand] Closed stdin");

	    StringBuilder output = new StringBuilder();

	    final boolean[] readComplete = {false};
	    Thread readerThread = new Thread(() -> {
	        try (BufferedReader br = new BufferedReader(
	                new InputStreamReader(proc.getInputStream()))) {
	            String line;
	            while ((line = br.readLine()) != null) {
	                System.out.println("[executeClineCommand OUTPUT] " + line);
	                output.append(line).append("\n");
	            }
	            readComplete[0] = true;
	            System.out.println("[executeClineCommand] Finished reading output");
	        } catch (Exception e) {
	            System.out.println("[executeClineCommand] Error reading: " + e.getMessage());
	            e.printStackTrace();
	        }
	    });

	    readerThread.start();

	    boolean finished = proc.waitFor(10, TimeUnit.SECONDS);
	    System.out.println("[executeClineCommand] Process finished: " + finished);

	    if (!finished) {
	        System.out.println("[executeClineCommand] Process timed out, destroying...");
	        proc.destroyForcibly();
	        throw new Exception("Command timed out after 10 seconds");
	    }

	    readerThread.join(2000);
	    
	    int exitCode = proc.exitValue();
	    System.out.println("[executeClineCommand] Process exited with code: " + exitCode);
	    System.out.println("[executeClineCommand] Output length: " + output.length());

	    return output.toString();
	}

	
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

	    Composite chatComposite = createChatComposite(tabFolder);
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
	            String taskId = createClineTask(messageCopy);
	            System.out.println("[createNewChat Thread] Task created with ID: " + taskId);
	            activeTaskId = taskId;

	            display.asyncExec(() -> {
	                System.out.println("[createNewChat Thread asyncExec] Updating UI with task info");
	                String shortTitle = messageCopy.length() > 30 ?
	                    messageCopy.substring(0, 30) + "..." : messageCopy;
	                item.setText(shortTitle);

	                System.out.println("[createNewChat Thread asyncExec] UI updated");
	            });

	            System.out.println("[createNewChat Thread] About to call followTaskOutput...");
	            followTaskOutput(chatComposite);
	            System.out.println("[createNewChat Thread] followTaskOutput completed");

	        } catch (Exception ex) {
	            System.out.println("[createNewChat Thread] Exception occurred: " + ex.getMessage());
	            ex.printStackTrace();
	            display.asyncExec(() -> {
	                item.setText("Error");
	                addMessageToComposite(chatComposite, "Failed to create chat: " + ex.getMessage(), false);
	            });
	        }
	    }, "CreateClineTaskThread").start();
	    
	    System.out.println("[createNewChat] Method completed, thread running in background");
	}

	private void followTaskOutput(Composite chatComposite) throws Exception {
	    System.out.println("[followTaskOutput] Starting to follow task output");
	    String cliBinaryPath = extractCliBinary();

	    List<String> command = new ArrayList<>();
	    command.add(cliBinaryPath);
	    command.add("task");
	    command.add("follow");
	    command.add("-o");
	    command.add("json");
	    
	    ProcessBuilder pb = new ProcessBuilder(command);
	    
	    File workingDir = new File(cliBinaryDir);
	    pb.directory(workingDir);
	    
	    Map<String, String> env = pb.environment();
	    if (!env.containsKey("HOME") || env.get("HOME") == null || env.get("HOME").isEmpty()) {
	        String home = System.getProperty("user.home");
	        env.put("HOME", home);
	    }
	    
	    String currentPath = env.get("PATH");
	    String newPath = cliBinaryDir + File.pathSeparator + (currentPath != null ? currentPath : "");
	    env.put("PATH", newPath);
	    
	    pb.redirectErrorStream(true);
	    pb.redirectInput(ProcessBuilder.Redirect.PIPE);
	    
	    Process proc = pb.start();
	    proc.getOutputStream().close();
	    System.out.println("[followTaskOutput] Process started");

	    StringBuilder aiMessage = new StringBuilder();
	    StringBuilder jsonBuffer = new StringBuilder();
	    boolean isUserMessage = true; // Alternate between user and AI
	    final boolean[] hasDisplayedAIMessage = {false};
	    
	    try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
	        String line;
	        
	        System.out.println("[followTaskOutput] Starting to read JSON lines...");
	        while ((line = reader.readLine()) != null) {
	            System.out.println("[followTaskOutput] JSON line: " + line);
	            
	            jsonBuffer.append(line).append("\n");
	            
	            if (line.trim().equals("}")) {
	                String jsonObject = jsonBuffer.toString();
	                System.out.println("[followTaskOutput] Complete JSON object: " + jsonObject);
	                
	                try {
	                    String textToAppend = parseJsonEvent(jsonObject);
	                    
	                    if (textToAppend != null && !textToAppend.isEmpty()) {
	                        if (isUserMessage) {
	                            System.out.println("[followTaskOutput] User message: " + textToAppend);
	                            String userMsg = textToAppend;
	                            display.asyncExec(() -> {
	                                addMessageToComposite(chatComposite, userMsg, true);
	                            });
	                            isUserMessage = false;
	                        } else {
	                            System.out.println("[followTaskOutput] AI message: " + textToAppend);
	                            aiMessage.append(textToAppend).append("\n\n");
	                            
	                            String messageSoFar = aiMessage.toString();
	                            display.asyncExec(() -> {
	                                Composite chatContainer = (Composite) chatComposite.getData("chatContainer");
	                                Control[] children = chatContainer.getChildren();
	                                
	                                if (hasDisplayedAIMessage[0] && children.length > 0) {
	                                    Control lastChild = children[children.length - 1];
	                                    if (lastChild instanceof Composite) {
	                                        lastChild.dispose();
	                                    }
	                                }
	                                
	                                addMessageToComposite(chatComposite, messageSoFar, false);
	                                hasDisplayedAIMessage[0] = true;
	                            });
	                            
	                            isUserMessage = true;
	                        }
	                    }
	                } catch (Exception e) {
	                    System.out.println("[followTaskOutput] Error parsing JSON: " + e.getMessage());
	                }
	                
	                jsonBuffer = new StringBuilder();
	            }
	        }
	    }
	    
	    proc.waitFor();
	    System.out.println("[followTaskOutput] Process completed");
	}

	private String parseJsonEvent(String jsonObject) {
	    // Check if this is a "say" event with "text" type
	    if (jsonObject.contains("\"type\": \"say\"") && jsonObject.contains("\"say\": \"text\"")) {
	        // Extract text field using simple string parsing
	        int textStart = jsonObject.indexOf("\"text\": \"");
	        if (textStart != -1) {
	            textStart += 9; // length of "\"text\": \""
	            int textEnd = jsonObject.indexOf("\"", textStart);
	            
	            // Handle escaped quotes
	            while (textEnd > 0 && jsonObject.charAt(textEnd - 1) == '\\') {
	                textEnd = jsonObject.indexOf("\"", textEnd + 1);
	            }
	            
	            if (textEnd != -1) {
	                String text = jsonObject.substring(textStart, textEnd);
	                // Unescape JSON string
	                text = text.replace("\\n", "\n")
	                          .replace("\\\"", "\"")
	                          .replace("\\\\", "\\");
	                return text;
	            }
	        }
	    }
	    
	    return null;
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
	    messageText.setWordWrap(true);
	    GridData textData = new GridData(SWT.FILL, SWT.CENTER, true, false);
	    textData.widthHint = 0;
	    messageText.setLayoutData(textData);

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