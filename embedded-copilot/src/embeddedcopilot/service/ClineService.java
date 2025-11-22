package embeddedcopilot.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
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
    private boolean clineInitialized = false;
    private String nodeJsPath = null;
    private String nodePathValue = null;

    public ClineService(ProjectService projectService) {
        this.projectService = projectService;
    }
    
    /**
     * Initializes cline by extracting binaries, detecting Node.js, and starting interactive session in background.
     * This should be called once at application startup.
     */
    public void initialize() throws Exception {
        if (clineInitialized) {
            return;
        }
        
        // Extract binaries first
        String cliBinaryPath = extractCliBinary();
        
        // Detect Node.js and set up NODE_PATH (once during initialization)
        setupNodeJsEnvironment();
        
        // Start interactive cline in background to initialize default instance
        ensureClineInitialized(cliBinaryPath);
        
        clineInitialized = true;
        System.out.println("[ClineService] Cline initialization completed");
    }
    
    /**
     * Sets up Node.js environment (PATH and NODE_PATH) - called once during initialization
     */
    private void setupNodeJsEnvironment() {
        System.out.println("[ClineService] ========== Detecting Node.js ==========");
        
        String home = System.getProperty("user.home");
        String nvmVersionsDir = home + "/.nvm/versions/node";
        
        // Try to find node in common nvm locations
        if (Files.exists(new File(nvmVersionsDir).toPath())) {
            try {
                File[] versionDirs = new File(nvmVersionsDir).listFiles(File::isDirectory);
                if (versionDirs != null && versionDirs.length > 0) {
                    Arrays.sort(versionDirs, (a, b) -> b.getName().compareTo(a.getName()));
                    for (File versionDir : versionDirs) {
                        Path nodeBinary = new File(versionDir, "bin/node").toPath();
                        if (Files.exists(nodeBinary) && Files.isExecutable(nodeBinary)) {
                            nodeJsPath = new File(versionDir, "bin").getAbsolutePath();
                            System.out.println("[ClineService] ✓ Found Node.js in nvm: " + nodeBinary);
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("[ClineService] Could not scan nvm versions: " + e.getMessage());
            }
        }
        
        // Fallback to specific common paths
        if (nodeJsPath == null) {
            String[] possibleNodePaths = {
                home + "/.nvm/versions/node/v22.20.0/bin",
                home + "/.nvm/versions/node/v22.19.0/bin",
                home + "/.nvm/versions/node/v22.18.0/bin",
                "/usr/local/bin",
                "/opt/homebrew/bin",
                "/usr/bin"
            };
            
            for (String possiblePath : possibleNodePaths) {
                Path nodeBinary = new File(possiblePath, "node").toPath();
                if (Files.exists(nodeBinary) && Files.isExecutable(nodeBinary)) {
                    nodeJsPath = possiblePath;
                    break;
                }
            }
        }
        
        // If not found, try which command
        if (nodeJsPath == null) {
            try {
                Process whichProcess = new ProcessBuilder("which", "node").start();
                whichProcess.waitFor(2, TimeUnit.SECONDS);
                if (whichProcess.exitValue() == 0) {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(whichProcess.getInputStream()))) {
                        String nodeFullPath = reader.readLine();
                        if (nodeFullPath != null && !nodeFullPath.isEmpty()) {
                            File nodeFile = new File(nodeFullPath);
                            nodeJsPath = nodeFile.getParent();
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("[ClineService] Could not find node via 'which': " + e.getMessage());
            }
        }
        
        if (nodeJsPath == null) {
            System.err.println("[ClineService] WARNING: Could not find Node.js in common locations");
        }
        
        // Set up NODE_PATH
        Path tempDir = Paths.get(cliBinaryDir);
        Path nodeModulesPath = tempDir.resolve("node_modules");
        String osName = System.getProperty("os.name").toLowerCase();
        String osArch = System.getProperty("os.arch").toLowerCase();
        String platformDir = osName.contains("mac") || osName.contains("darwin") 
            ? (osArch.contains("aarch64") || osArch.contains("arm64") ? "darwin-arm64" : "darwin-x64")
            : (osName.contains("linux") 
                ? (osArch.contains("aarch64") || osArch.contains("arm64") ? "linux-arm64" : "linux-x64")
                : "win-x64");
        Path platformBinariesPath = tempDir.resolve("binaries").resolve(platformDir).resolve("node_modules");
        
        List<String> nodePathParts = new ArrayList<>();
        if (Files.exists(platformBinariesPath)) {
            nodePathParts.add(platformBinariesPath.toString());
            System.out.println("[ClineService] ✓ Found platform-specific binaries at: " + platformBinariesPath);
        }
        if (Files.exists(nodeModulesPath)) {
            nodePathParts.add(nodeModulesPath.toString());
        }
        
        if (!nodePathParts.isEmpty()) {
            nodePathValue = String.join(File.pathSeparator, nodePathParts);
        }
    }

    /**
     * Extracts the Cline CLI binary and standalone.zip contents from the bundle
     * 
     * @return path to the cline binary
     * @throws Exception if extraction fails
     */
    private String extractCliBinary() throws Exception {
        if (cliBinaryDir != null) {
            // Even if we're returning early, check if better-sqlite3 needs to be copied
            ensureBetterSqlite3Copied(Paths.get(cliBinaryDir));
            return cliBinaryDir + "/bin/cline";
        }

        Bundle bundle = FrameworkUtil.getBundle(getClass());
        if (bundle == null) {
            throw new Exception("Could not get OSGi bundle");
        }

        Path tempDir = Files.createTempDirectory("cline-binaries");
        cliBinaryDir = tempDir.toString();

        System.out.println("[ClineService] Created temp directory: " + cliBinaryDir);

        // First, extract standalone.zip (contains cline-core.js, node_modules, etc.)
        System.out.println("[ClineService] Looking for standalone.zip in bundle...");
        URL zipUrl = bundle.getEntry("resources/standalone.zip");
        System.out.println("[ClineService] Bundle entry 'resources/standalone.zip': " + (zipUrl != null ? "FOUND" : "NOT FOUND"));
        
        // Try alternative paths
        if (zipUrl == null) {
            zipUrl = bundle.getEntry("standalone.zip");
            System.out.println("[ClineService] Bundle entry 'standalone.zip': " + (zipUrl != null ? "FOUND" : "NOT FOUND"));
        }
        
        if (zipUrl != null) {
            System.out.println("[ClineService] ✓ Found standalone.zip, extracting...");
            Path zipPath = tempDir.resolve("standalone.zip");
            
            // Copy zip file to temp directory first
            // Use bundle URL directly - FileLocator might not work correctly for zip files
            try (InputStream zipIn = zipUrl.openStream()) {
                long bytesCopied = Files.copy(zipIn, zipPath, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("[ClineService] Copied " + bytesCopied + " bytes to " + zipPath);
                
                // Verify the file exists and has content
                if (!Files.exists(zipPath)) {
                    throw new Exception("Zip file was not copied successfully");
                }
                long fileSize = Files.size(zipPath);
                if (fileSize == 0) {
                    throw new Exception("Zip file is empty (0 bytes)");
                }
                System.out.println("[ClineService] Zip file size: " + fileSize + " bytes");
            } catch (Exception e) {
                System.err.println("[ClineService] ERROR: Failed to copy zip file: " + e.getMessage());
                e.printStackTrace();
                throw new Exception("Failed to copy standalone.zip from bundle: " + e.getMessage(), e);
            }
            
            // Extract the zip file
            try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                int entryCount = 0;
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    Path entryPath = tempDir.resolve(entry.getName());
                    
                    if (entry.isDirectory()) {
                        Files.createDirectories(entryPath);
                    } else {
                        Files.createDirectories(entryPath.getParent());
                        try (InputStream entryIn = zipFile.getInputStream(entry)) {
                            Files.copy(entryIn, entryPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                    entryCount++;
                }
                System.out.println("[ClineService] Extracted " + entryCount + " entries from zip");
            } catch (Exception e) {
                System.err.println("[ClineService] ERROR: Failed to extract zip file: " + e.getMessage());
                System.err.println("[ClineService] Zip file path: " + zipPath);
                System.err.println("[ClineService] Zip file exists: " + Files.exists(zipPath));
                if (Files.exists(zipPath)) {
                    try {
                        System.err.println("[ClineService] Zip file size: " + Files.size(zipPath) + " bytes");
                    } catch (Exception ex) {
                        System.err.println("[ClineService] Could not get file size: " + ex.getMessage());
                    }
                }
                e.printStackTrace();
                throw new Exception("Failed to extract standalone.zip: " + e.getMessage(), e);
            }
            
            // Delete the zip file after extraction
            try {
                Files.delete(zipPath);
            } catch (Exception e) {
                System.err.println("[ClineService] WARNING: Could not delete zip file: " + e.getMessage());
            }
            System.out.println("[ClineService] ✓ Extracted standalone.zip");
            
            // Copy better-sqlite3 from platform-specific binaries to main node_modules
            ensureBetterSqlite3Copied(tempDir);
        } else {
            System.out.println("[ClineService] WARNING: standalone.zip not found in bundle");
        }

        // Create bin subdirectory and extract binaries
        Path binDir = tempDir.resolve("bin");
        Files.createDirectories(binDir);

       URL clineUrl = bundle.getEntry("bin/cline");
        if (clineUrl == null) {
            throw new Exception("Binary not found in bundle: bin/cline");
        }
        URL clineFileUrl = FileLocator.toFileURL(clineUrl);
        InputStream clineIn = clineFileUrl.openStream();
        Path clinePath = binDir.resolve("cline");
        Files.copy(clineIn, clinePath, StandardCopyOption.REPLACE_EXISTING);
        clineIn.close();
        clinePath.toFile().setExecutable(true);
        System.out.println("[ClineService] Extracted cline to: " + clinePath);

        URL hostUrl = bundle.getEntry("bin/cline-host");
        if (hostUrl != null) {
            URL hostFileUrl = FileLocator.toFileURL(hostUrl);
            InputStream hostIn = hostFileUrl.openStream();
            Path hostPath = binDir.resolve("cline-host");
            Files.copy(hostIn, hostPath, StandardCopyOption.REPLACE_EXISTING);
            hostIn.close();
            hostPath.toFile().setExecutable(true);
        }

        // Verify final structure
        System.out.println("[ClineService] Final structure:");
        System.out.println("[ClineService]   - cline binary: " + clinePath);
        System.out.println("[ClineService]   - cline-core.js should be at: " + tempDir.resolve("cline-core.js"));
        System.out.println("[ClineService]   - cline will look for ../cline-core.js from bin/cline");

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
        // Ensure cline is initialized (should already be done, but check just in case)
        if (!clineInitialized) {
            initialize();
        }
        
        return executeClineCommandInternal(args);
    }
    
    /**
     * Internal method to execute cline commands (extracted to avoid duplication)
     */
    private String executeClineCommandInternal(String... args) throws Exception {
        String cliBinaryPath = extractCliBinary();
        List<String> command = new ArrayList<>();
        command.add(cliBinaryPath);
        command.addAll(Arrays.asList(args));

        ProcessBuilder pb = new ProcessBuilder(command);

        Map<String, String> env = pb.environment();
        String home = System.getProperty("user.home");
        
        if (!env.containsKey("HOME") || env.get("HOME") == null || env.get("HOME").isEmpty()) {
            env.put("HOME", home);
        }

        String projectRoot = projectService.getProjectRootDirectory();
        pb.directory(new File(projectRoot));
        
        // Set CLINE_WORKSPACE environment variable so cline-host knows the correct workspace
        env.put("CLINE_WORKSPACE", projectRoot);
        System.out.println("[ClineService] Set CLINE_WORKSPACE=" + projectRoot);
        
        // Use pre-configured Node.js path and NODE_PATH from initialization
        String currentPath = env.get("PATH");
        if (nodeJsPath != null) {
            String newPath = nodeJsPath + File.pathSeparator + (currentPath != null ? currentPath : "");
            env.put("PATH", newPath);
        }
        
        // Add bin directory to PATH
        String binPath = cliBinaryDir + "/bin";
        String newPath2 = binPath + File.pathSeparator + (env.get("PATH") != null ? env.get("PATH") : "");
        env.put("PATH", newPath2);
        
        // Set NODE_PATH (pre-configured during initialization)
        if (nodePathValue != null) {
            env.put("NODE_PATH", nodePathValue);
        }

        // Redirect both stdout and stderr to capture all output
        pb.redirectErrorStream(true);
        pb.redirectInput(ProcessBuilder.Redirect.PIPE);

        Process proc = pb.start();
        proc.getOutputStream().close();

        StringBuilder output = new StringBuilder();

        Thread readerThread = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    System.out.println("[ClineService OUTPUT] " + line);
                    output.append(line).append("\n");
                }
            } catch (Exception e) {
                System.out.println("[ClineService] Error reading: " + e.getMessage());
                e.printStackTrace();
            }
        });

        readerThread.start();

        // Wait longer for cline to start cline-core and provide verbose output
        boolean finished = proc.waitFor(30, TimeUnit.SECONDS);
        System.out.println("[ClineService] Process finished: " + finished);

        if (!finished) {
            System.out.println("[ClineService] Process timed out, destroying...");
            proc.destroyForcibly();
            throw new Exception("Command timed out after 30 seconds");
        }

        // Wait longer for output reader to finish
        readerThread.join(5000);

        int exitCode = proc.exitValue();
        System.out.println("[ClineService] Process exited with code: " + exitCode);
        System.out.println("[ClineService] Output length: " + output.length());
        
        // If we got errors, check cline logs for more details
        String outputStr = output.toString();
        if (outputStr.contains("No instances available") || 
            outputStr.contains("instance not found in registry") ||
            outputStr.contains("failed to start instance") ||
            outputStr.contains("failed to ensure default instance") ||
            outputStr.contains("error reading from server") ||
            outputStr.contains("rpc error") ||
            outputStr.contains("code = Unavailable")) {
            Path logsDir = new File(home, ".cline/logs").toPath();
            if (Files.exists(logsDir)) {
                System.out.println("[ClineService] Checking cline logs in: " + logsDir);
                try {
                    // List recent log files (both cline-core and cline-host)
                    File[] logFiles = logsDir.toFile().listFiles((dir, name) -> 
                        (name.startsWith("cline-core-") || name.startsWith("cline-host-")) && name.endsWith(".log"));
                    if (logFiles != null && logFiles.length > 0) {
                        // Sort by last modified, get most recent
                        Arrays.sort(logFiles, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                        System.out.println("[ClineService] Found " + logFiles.length + " log files");
                        // Show the 3 most recent log files
                        int filesToShow = Math.min(3, logFiles.length);
                        for (int i = 0; i < filesToShow; i++) {
                            File logFile = logFiles[i];
                            long age = System.currentTimeMillis() - logFile.lastModified();
                            System.out.println("[ClineService] Log file " + (i+1) + ": " + logFile.getName() + 
                                " (modified " + (age / 1000) + " seconds ago)");
                        }
                        
                        // Find the most recent cline-core log (not cline-host)
                        File latestCoreLog = null;
                        for (File logFile : logFiles) {
                            if (logFile.getName().startsWith("cline-core-")) {
                                latestCoreLog = logFile;
                                break;
                            }
                        }
                        if (latestCoreLog == null) {
                            latestCoreLog = logFiles[0]; // Fallback to most recent if no core log found
                        }
                        
                        long logAge = System.currentTimeMillis() - latestCoreLog.lastModified();
                        System.out.println("[ClineService] Reading latest cline-core log: " + latestCoreLog.getName() + 
                            " (modified " + (logAge / 1000) + " seconds ago)");
                        
                        File latestLog = latestCoreLog;
                        // Read last 100 lines to see more context
                        try (BufferedReader logReader = Files.newBufferedReader(latestLog.toPath())) {
                            List<String> lines = new ArrayList<>();
                            String line;
                            while ((line = logReader.readLine()) != null) {
                                lines.add(line);
                                if (lines.size() > 100) {
                                    lines.remove(0);
                                }
                            }
                            System.out.println("[ClineService] Last 100 lines from log:");
                            for (String logLine : lines) {
                                System.out.println("[ClineService LOG] " + logLine);
                            }
                        }
                    } else {
                        System.out.println("[ClineService] No log files found in: " + logsDir);
                    }
                } catch (Exception e) {
                    System.out.println("[ClineService] Could not read cline logs: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

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
            "-v",
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
        String output = executeClineCommand("-v", "task", "send", message);
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
        return executeClineCommand("-v", "task", "view", "-F", "json");
    }

    /**
     * Lists all tasks
     * 
     * @return list of task history
     * @throws Exception if listing fails
     */
    public List<ChatHistory> listTasks() throws Exception {
        String output = executeClineCommand("-v", "task", "list");
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
    
    /**
     * Recursively copies a directory from source to destination
     * 
     * @param source the source directory
     * @param destination the destination directory
     * @throws IOException if copying fails
     */
    private void copyDirectory(Path source, Path destination) throws IOException {
        Files.walk(source).forEach(sourcePath -> {
            try {
                Path targetPath = destination.resolve(source.relativize(sourcePath));
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to copy " + sourcePath + " to " + destination, e);
            }
        });
    }

    /**
     * Ensures better-sqlite3 is copied from platform-specific binaries to main node_modules.
     * This is needed because the Go code sets NODE_PATH to main node_modules, not platform-specific.
     * 
     * @param tempDir the temporary directory where binaries are extracted
     */
    private void ensureBetterSqlite3Copied(Path tempDir) {
        try {
            String osName = System.getProperty("os.name").toLowerCase();
            String osArch = System.getProperty("os.arch").toLowerCase();
            
            String platformDir;
            if (osName.contains("mac") || osName.contains("darwin")) {
                platformDir = osArch.contains("aarch64") || osArch.contains("arm64") ? "darwin-arm64" : "darwin-x64";
            } else if (osName.contains("linux")) {
                platformDir = osArch.contains("aarch64") || osArch.contains("arm64") ? "linux-arm64" : "linux-x64";
            } else if (osName.contains("win")) {
                platformDir = "win-x64";
            } else {
                platformDir = "unknown";
            }
            
            Path platformBetterSqlite3 = tempDir.resolve("binaries").resolve(platformDir).resolve("node_modules").resolve("better-sqlite3");
            Path mainBetterSqlite3 = tempDir.resolve("node_modules").resolve("better-sqlite3");
            
            if (Files.exists(platformBetterSqlite3) && !Files.exists(mainBetterSqlite3)) {
                copyDirectory(platformBetterSqlite3, mainBetterSqlite3);
                System.out.println("[ClineService] ✓ Copied better-sqlite3 to main node_modules");
            } else if (Files.exists(mainBetterSqlite3)) {
                // Already exists, no need to log
            } else {
                System.err.println("[ClineService] ERROR: better-sqlite3 not found at: " + platformBetterSqlite3);
            }
        } catch (Exception e) {
            System.err.println("[ClineService] ERROR: Could not copy better-sqlite3: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Ensures a cline instance exists by creating one if none exists.
     * This starts cline-host and cline-core processes which stay alive independently.
     * We don't need to keep the CLI process itself running.
     * 
     * @param cliBinaryPath path to the cline binary
     */
    private void ensureClineInitialized(String cliBinaryPath) {
        try {
            System.out.println("[ClineService] Ensuring cline instance exists...");
            
            // First check if any instances exist
            String listOutput = executeClineCommandInternal("-v", "instance", "list");
            
            // If no instances found, create a new one
            if (listOutput.contains("No Cline instances found") || 
                listOutput.contains("No instances available")) {
                System.out.println("[ClineService] No instances found, creating new instance...");
                String newOutput = executeClineCommandInternal("-v", "instance", "new", "--default");
                System.out.println("[ClineService] ✓ Created new cline instance");
            } else {
                System.out.println("[ClineService] ✓ Cline instance already exists (cline-host and cline-core processes run independently)");
            }
        } catch (Exception e) {
            System.out.println("[ClineService] Warning: Could not ensure cline instance: " + e.getMessage());
            // Don't fail completely - the next command will try to ensure instance anyway
        }
    }
    
    /**
     * Finds the Node.js path (helper method for interactive cline initialization)
     */
    private String findNodeJsPath() {
        String home = System.getProperty("user.home");
        
        // Check nvm first
        File nvmDir = new File(home, ".nvm");
        if (nvmDir.exists() && nvmDir.isDirectory()) {
            File[] versions = nvmDir.listFiles((dir, name) -> 
                name.startsWith("versions") && new File(dir, name).isDirectory());
            if (versions != null && versions.length > 0) {
                File versionsDir = versions[0];
                File[] nodeVersions = versionsDir.listFiles((dir, name) -> 
                    name.startsWith("v") && new File(dir, name).isDirectory());
                if (nodeVersions != null && nodeVersions.length > 0) {
                    // Get the latest version (sorted by name, highest first)
                    Arrays.sort(nodeVersions, (a, b) -> b.getName().compareTo(a.getName()));
                    File nodeBin = new File(nodeVersions[0], "bin");
                    if (nodeBin.exists()) {
                        return nodeBin.getAbsolutePath();
                    }
                }
            }
        }
        
        return null;
    }
}