package embeddedcopilot.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.runtime.FileLocator;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

import embeddedcopilot.model.ChatHistory;
import embeddedcopilot.model.ChatMessage;

/**
 * Service for interacting with the Cline CLI
 */
public class ClineService {

    private String cliBinaryDir = null;
    private final ProjectService projectService;

    public ClineService(ProjectService projectService) {
        this.projectService = projectService;
    }

    /**
     * Extracts the Cline CLI binary from the bundle
     * 
     * @return path to the cline binary
     * @throws Exception if extraction fails
     */
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

        System.out.println("[ClineService] Created temp directory: " + cliBinaryDir);

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
        System.out.println("[ClineService] Extracted cline to: " + clinePath);

        // Extract cline-host binary
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
        System.out.println("[ClineService] Extracted cline-host to: " + hostPath);

        return clinePath.toString();
    }

    /**
     * Executes a Cline CLI command
     * 
     * @param args command arguments
     * @return command output
     * @throws Exception if command execution fails
     */
    public String executeClineCommand(String... args) throws Exception {
        System.out.println("[ClineService] Starting with args: " + Arrays.toString(args));

        String cliBinaryPath = extractCliBinary();
        System.out.println("[ClineService] CLI binary path: " + cliBinaryPath);

        List<String> command = new ArrayList<>();
        command.add(cliBinaryPath);
        command.addAll(Arrays.asList(args));

        System.out.println("[ClineService] Full command: " + command);

        ProcessBuilder pb = new ProcessBuilder(command);

        File workingDir = new File(cliBinaryDir);
        pb.directory(workingDir);
        System.out.println("[ClineService] Working directory: " + workingDir.getAbsolutePath());

        Map<String, String> env = pb.environment();
        if (!env.containsKey("HOME") || env.get("HOME") == null || env.get("HOME").isEmpty()) {
            String home = System.getProperty("user.home");
            env.put("HOME", home);
            System.out.println("[ClineService] Set HOME to: " + home);
        }

        String projectRoot = projectService.getProjectRootDirectory();
        if (projectRoot != null) {
            env.put("CLINE_DIR", projectRoot);
            System.out.println("[ClineService] Set CLINE_DIR to: " + projectRoot);
        }

        String currentPath = env.get("PATH");
        String newPath = cliBinaryDir + File.pathSeparator + (currentPath != null ? currentPath : "");
        env.put("PATH", newPath);

        pb.redirectErrorStream(true);
        pb.redirectInput(ProcessBuilder.Redirect.PIPE);

        Process proc = pb.start();
        System.out.println("[ClineService] Process started");

        proc.getOutputStream().close();
        System.out.println("[ClineService] Closed stdin");

        StringBuilder output = new StringBuilder();

        Thread readerThread = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    System.out.println("[ClineService OUTPUT] " + line);
                    output.append(line).append("\n");
                }
                System.out.println("[ClineService] Finished reading output");
            } catch (Exception e) {
                System.out.println("[ClineService] Error reading: " + e.getMessage());
                e.printStackTrace();
            }
        });

        readerThread.start();

        boolean finished = proc.waitFor(10, TimeUnit.SECONDS);
        System.out.println("[ClineService] Process finished: " + finished);

        if (!finished) {
            System.out.println("[ClineService] Process timed out, destroying...");
            proc.destroyForcibly();
            throw new Exception("Command timed out after 10 seconds");
        }

        readerThread.join(2000);

        int exitCode = proc.exitValue();
        System.out.println("[ClineService] Process exited with code: " + exitCode);
        System.out.println("[ClineService] Output length: " + output.length());

        return output.toString();
    }

    /**
     * Creates a new Cline task
     * 
     * @param message the initial message for the task
     * @throws Exception if task creation fails
     */
    public void createTask(String message) throws Exception {
        System.out.println("[ClineService] Creating task with message: " + message);

        String output = executeClineCommand(
            "task",
            "new",
            "-s", "act-mode-api-provider=anthropic",
            "-s", "act-mode-api-model-id=claude-sonnet-4.5",
            message
        );

        System.out.println("[ClineService] Output from cline task new: " + output);

        if (!isTaskCreationSuccessful(output)) {
            throw new Exception("Failed to confirm task creation from output: " + output);
        }

        System.out.println("[ClineService] Task created successfully");
    }

    /**
     * Sends a message to the current Cline task
     * 
     * @param message the message to send
     * @return the command output
     * @throws Exception if sending fails
     */
    public String sendMessage(String message) throws Exception {
        System.out.println("[ClineService] Sending message: " + message);
        String output = executeClineCommand("task", "send", message);
        System.out.println("[ClineService] Output from cline task send: " + output);
        return output;
    }

    /**
     * Gets the current task view in JSON format
     * 
     * @return JSON output of the task view
     * @throws Exception if command fails
     */
    public String getTaskViewJson() throws Exception {
        return executeClineCommand("task", "view", "-F", "json");
    }

    /**
     * Lists all tasks
     * 
     * @return list of task history
     * @throws Exception if listing fails
     */
    public List<ChatHistory> listTasks() throws Exception {
        String output = executeClineCommand("task", "list");
        System.out.println("[ClineService] Output from task list: " + output);
        return parseTaskHistory(output);
    }

    /**
     * Checks if task creation was successful based on output
     * 
     * @param output the command output
     * @return true if successful
     */
    private boolean isTaskCreationSuccessful(String output) {
        String[] lines = output.split("\\R");

        for (String line : lines) {
            if (line.contains("Cancelled existing task to start new one") || 
                line.contains("Task created successfully")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Parses task history from command output
     * 
     * @param text the command output
     * @return list of parsed chat histories
     */
    private List<ChatHistory> parseTaskHistory(String text) {
        List<ChatHistory> result = new ArrayList<>();
        String[] lines = text.split("\\R");

        String id = null;
        String message = null;
        String usage = null;

        for (String line : lines) {
            if (line.startsWith("Task ID:")) {
                if (message != null && id != null) {
                    ChatHistory h = new ChatHistory(message);
                    h.addMessage(new ChatMessage("Task ID: " + id, false));
                    if (usage != null) {
                        h.addMessage(new ChatMessage(usage, false));
                    }
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
            h.addMessage(new ChatMessage("Task ID: " + id, false));
            if (usage != null) {
                h.addMessage(new ChatMessage(usage, false));
            }
            result.add(h);
        }

        return result;
    }
}