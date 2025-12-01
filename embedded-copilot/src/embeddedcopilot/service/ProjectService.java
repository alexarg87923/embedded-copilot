package embeddedcopilot.service;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.TextViewer;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.jface.text.TextPresentation;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for Eclipse project-related operations
 */
public class ProjectService {

    /**
     * State for tracking active diff highlights in editors
     */
    private static class DiffHighlightState {
        final List<CombinedLine> combined;
        final String documentText;
        final StyleRange[] styleRanges;
        final Color greenBg;
        final Color greenFg;
        final Color redBg;
        final Color redFg;
        PaintListener paintListener;
        volatile boolean isReapplying = false; // Flag to prevent infinite recursion

        DiffHighlightState(List<CombinedLine> combined, String documentText, StyleRange[] styleRanges,
                          Color greenBg, Color greenFg, Color redBg, Color redFg) {
            this.combined = combined;
            this.documentText = documentText;
            this.styleRanges = styleRanges;
            this.greenBg = greenBg;
            this.greenFg = greenFg;
            this.redBg = redBg;
            this.redFg = redFg;
        }
    }

    /**
     * Map to track active diff editors with their highlight state
     * Key: ITextEditor, Value: DiffHighlightState
     */
    private final Map<ITextEditor, DiffHighlightState> activeDiffEditors = new HashMap<>();

    /**
     * Gets the root directory of the currently selected project,
     * or the first project in the workspace as fallback
     * 
     * @return the project root directory path, or null if no project found
     */
    public String getProjectRootDirectory() {
        try {
            IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            if (window != null) {
                ISelectionService service = window.getSelectionService();
                ISelection selection = service.getSelection();
                
                if (selection instanceof IStructuredSelection) {
                    Object element = ((IStructuredSelection) selection).getFirstElement();

                    IProject project = null;
                    if (element instanceof IResource) {
                        project = ((IResource) element).getProject();
                    } else if (element instanceof IAdaptable) {
                        IResource resource = ((IAdaptable) element).getAdapter(IResource.class);
                        if (resource != null) {
                            project = resource.getProject();
                        }
                    }

                    if (project != null) {
                        return project.getLocation().toOSString();
                    }
                }
            }

            IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
            if (projects.length > 0) {
                return projects[0].getLocation().toOSString();
            }
        } catch (Exception e) {
            System.out.println("[ProjectService] Error getting project root: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Refreshes the package explorer to reflect file system changes
     */
    public void refreshPackageExplorer() {
        try {
            IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
            IProgressMonitor monitor = new NullProgressMonitor();
            for (IProject project : projects) {
                if (project.isOpen()) {
                    project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
                }
            }
            System.out.println("[ProjectService] Refreshed package explorer");
        } catch (CoreException e) {
            System.out.println("[ProjectService] Error refreshing package explorer: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Gets or creates dedicated backup directory for Cline file backups.
     * Uses user-specific directory to avoid conflicts.
     *
     * @return backup directory file object
     */
    private File getBackupDirectory() {
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        File backupDir = new File(tempDir, "cline_backups_" + System.getProperty("user.name"));
        if (!backupDir.exists()) {
            backupDir.mkdirs();
            System.out.println("[ProjectService] Created backup directory: " + backupDir.getAbsolutePath());
        }
        return backupDir;
    }

    /**
     * Saves the clean edited version (Cline's actual edits before inserting removed lines).
     * This is needed for the APPROVE flow - we restore this version when user approves.
     *
     * @param filePath the relative path to the file
     * @param cleanContent the clean content after Cline's edits (no removed lines inserted)
     * @return the backup file containing the clean version
     */
    private File saveCleanEditedVersion(String filePath, String cleanContent) throws Exception {
        File backupDir = getBackupDirectory();
        String baseName = new File(filePath).getName();
        int dotIndex = baseName.lastIndexOf('.');
        String nameWithoutExt = dotIndex > 0 ? baseName.substring(0, dotIndex) : baseName;
        String ext = dotIndex > 0 ? baseName.substring(dotIndex) : "";

        File cleanBackup = File.createTempFile(nameWithoutExt + "_clean_", ext, backupDir);
        Files.write(cleanBackup.toPath(), cleanContent.getBytes());
        System.out.println("[ProjectService] Saved clean edited version: " + cleanBackup.getAbsolutePath());
        return cleanBackup;
    }

    /**
     * Saves a backup of the current file before Cline applies changes.
     *
     * @param filePath the relative path to the file
     * @return the backup file, or null if file doesn't exist (new file)
     */
    public File saveBackup(String filePath) {
        try {
            String projectRoot = getProjectRootDirectory();
            if (projectRoot == null) {
                System.err.println("[ProjectService] Cannot save backup: no project root found");
                return null;
            }

            // Get the full file path
            File file = new File(projectRoot, filePath);
            
            // Read current file content if it exists
            String currentContent = "";
            if (file.exists()) {
                currentContent = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())));
            } else {
                // New file - return null (backup is empty)
                System.out.println("[ProjectService] New file, no backup needed: " + filePath);
                return null;
            }

            // Create a backup file in dedicated backup directory
            File tempDir = getBackupDirectory();
            String baseName = file.getName();
            int dotIndex = baseName.lastIndexOf('.');
            String nameWithoutExt = dotIndex > 0 ? baseName.substring(0, dotIndex) : baseName;
            String ext = dotIndex > 0 ? baseName.substring(dotIndex) : "";

            File backupFile = File.createTempFile(nameWithoutExt + "_backup_", ext, tempDir);
            Files.write(Paths.get(backupFile.getAbsolutePath()), currentContent.getBytes());
            System.out.println("[ProjectService] Created backup: " + backupFile.getAbsolutePath());

            return backupFile;
        } catch (Exception e) {
            System.err.println("[ProjectService] Error saving backup: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Callback interface for diff view - receives editor and BOTH backup files
     */
    @FunctionalInterface
    public interface DiffViewCallback {
        void accept(IEditorPart editor, File originalBackup, File cleanEditedBackup);
    }

    /**
     * Shows a diff view by comparing the backup file with the current (modified) file.
     * Cline has already applied the changes, so we compute the diff and show highlights.
     * NEW SIMPLIFIED VERSION - uses single diff computation with line number offsets.
     * CRITICAL FIX: Maintains TWO backups for proper approve/deny handling.
     *
     * @param filePath the relative path to the file being edited
     * @param originalBackup the backup file with original content (for DENY)
     * @param onEditorOpened callback invoked with editor and BOTH backups
     */
    public void showDiffViewFromBackup(String filePath, File originalBackup, DiffViewCallback onEditorOpened) {
        Display display = PlatformUI.getWorkbench().getDisplay();
        display.asyncExec(() -> {
            try {
                // 1. Read original backup content (before any edits)
                String beforeContent = "";
                if (originalBackup != null && originalBackup.exists()) {
                    beforeContent = new String(Files.readAllBytes(Paths.get(originalBackup.getAbsolutePath())));
                }

                // 2. Find the workspace file
                IFile workspaceFile = findWorkspaceFile(filePath);
                if (workspaceFile == null || !workspaceFile.exists()) {
                    System.err.println("[ProjectService] File not found after Cline applied changes: " + filePath);
                    return;
                }

                // 3. Read clean edited content (after Cline's edits, BEFORE we insert removed lines)
                String afterContent = readWorkspaceFileContent(workspaceFile);

                System.out.println("[ProjectService] Before content: " + beforeContent.split("\n").length + " lines");
                System.out.println("[ProjectService] After content: " + afterContent.split("\n").length + " lines");

                // 4. SAVE THE CLEAN EDITED VERSION - needed for APPROVE
                File cleanEditedBackup = saveCleanEditedVersion(filePath, afterContent);
                System.out.println("[ProjectService] Saved clean edited backup for approve flow");

                // 5. Compute diff ONCE using Unix diff
                List<DiffOperation> operations = computeDiffOperations(beforeContent, afterContent);

                // 6. Build combined content with removed lines inserted (for highlighting only)
                String[] afterLines = afterContent.split("\n", -1);
                List<CombinedLine> combined = buildCombinedContent(afterLines, operations);
                String combinedContent = combinedLinesToString(combined);

                // 7. Write COMBINED content to workspace file (for display only)
                InputStream combinedStream = new ByteArrayInputStream(combinedContent.getBytes());
                workspaceFile.setContents(combinedStream, IResource.FORCE, new NullProgressMonitor());
                workspaceFile.refreshLocal(IResource.DEPTH_ZERO, new NullProgressMonitor());
                System.out.println("[ProjectService] Inserted removed lines into file for highlighting");

                // 8. Open editor
                IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
                if (window == null) {
                    System.err.println("[ProjectService] No active workbench window");
                    return;
                }

                IWorkbenchPage page = window.getActivePage();
                if (page == null) {
                    System.err.println("[ProjectService] No active page");
                    return;
                }

                IEditorPart editor = IDE.openEditor(page, workspaceFile);

                // 9. Notify callback with editor and BOTH backups
                if (onEditorOpened != null) {
                    onEditorOpened.accept(editor, originalBackup, cleanEditedBackup);
                }

                // 10. Apply highlights using line numbers (NO content matching)
                if (editor instanceof ITextEditor) {
                    // Wait for editor to load
                    display.timerExec(500, () -> {
                        applyHighlightsFromCombined((ITextEditor) editor, combined, combinedContent);
                    });
                }

                System.out.println("[ProjectService] Opened diff view for: " + filePath);
            } catch (Exception e) {
                System.err.println("[ProjectService] Error showing diff view from backup: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Finds a workspace file by path, trying multiple locations
     */
    private IFile findWorkspaceFile(String filePath) {
        IFile workspaceFile = ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(filePath));
        if (workspaceFile.exists()) {
            return workspaceFile;
        }

        // Try finding in all projects
        IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
        for (IProject project : projects) {
            if (project.isOpen()) {
                IFile file = project.getFile(filePath);
                if (file.exists()) {
                    return file;
                }
                // Also try with src/ prefix removed
                if (filePath.startsWith("src/")) {
                    String relativePath = filePath.substring(4);
                    file = project.getFile(relativePath);
                    if (file.exists()) {
                        return file;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Reads content from a workspace file
     */
    private String readWorkspaceFileContent(IFile workspaceFile) throws Exception {
        try (java.io.InputStream is = workspaceFile.getContents()) {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            return baos.toString("UTF-8");
        }
    }



    /**
     * Restores a file from backup (used when user denies changes)
     */
    public void restoreFromBackup(String filePath, File backupFile) {
        Display display = PlatformUI.getWorkbench().getDisplay();
        display.asyncExec(() -> {
            try {
                if (backupFile == null || !backupFile.exists()) {
                    System.err.println("[ProjectService] Backup file not found, cannot restore");
                    return;
                }

                // Read backup content
                String backupContent = new String(Files.readAllBytes(Paths.get(backupFile.getAbsolutePath())));

                // Find the workspace file
                IFile workspaceFile = null;
                workspaceFile = ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(filePath));
                if (!workspaceFile.exists()) {
                    IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
                    for (IProject project : projects) {
                        if (project.isOpen()) {
                            IFile file = project.getFile(filePath);
                            if (file.exists()) {
                                workspaceFile = file;
                                break;
                            }
                            if (filePath.startsWith("src/")) {
                                String relativePath = filePath.substring(4);
                                file = project.getFile(relativePath);
                                if (file.exists()) {
                                    workspaceFile = file;
                                    break;
                                }
                            }
                        }
                    }
                }

                if (workspaceFile != null && workspaceFile.exists()) {
                    // If backup is empty, this was a new file - delete it instead of restoring
                    if (backupContent.isEmpty()) {
                        workspaceFile.delete(IResource.FORCE, new NullProgressMonitor());
                        System.out.println("[ProjectService] Deleted new file (denied creation): " + filePath);
                    } else {
                        // Restore backup content to workspace file
                        InputStream backupStream = new ByteArrayInputStream(backupContent.getBytes());
                        workspaceFile.setContents(backupStream, IResource.FORCE, new NullProgressMonitor());
                        System.out.println("[ProjectService] Restored file from backup: " + filePath);
                    }

                    // Refresh the file to ensure Eclipse sees the changes
                    workspaceFile.refreshLocal(IResource.DEPTH_ZERO, new NullProgressMonitor());
                    System.out.println("[ProjectService] Refreshed workspace file after restore: " + filePath);

                    // Clear diff highlights from the editor if it's open (only if file still exists)
                    if (workspaceFile.exists()) {
                        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
                        if (window != null) {
                            IWorkbenchPage page = window.getActivePage();
                            if (page != null) {
                                IEditorPart editor = IDE.openEditor(page, workspaceFile);
                                if (editor instanceof ITextEditor) {
                                    clearDiffHighlights((ITextEditor) editor);
                                }
                            }
                        }
                    }

                    // Delete backup file
                    backupFile.delete();
                    System.out.println("[ProjectService] Deleted backup file");
                } else {
                    System.err.println("[ProjectService] Workspace file not found for restore: " + filePath);
                }

            } catch (Exception e) {
                System.err.println("[ProjectService] Error restoring from backup: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    /**
     * Clears all diff highlights from the editor for a given file path.
     * Public method that can be called from SampleView to clear highlights.
     * 
     * @param filePath the relative path to the file
     */
    public void clearDiffHighlights(String filePath) {
        Display display = PlatformUI.getWorkbench().getDisplay();
        display.asyncExec(() -> {
            try {
                // Find the workspace file
                IFile workspaceFile = null;
                workspaceFile = ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(filePath));
                if (!workspaceFile.exists()) {
                    IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
                    for (IProject project : projects) {
                        if (project.isOpen()) {
                            IFile file = project.getFile(filePath);
                            if (file.exists()) {
                                workspaceFile = file;
                                break;
                            }
                            if (filePath.startsWith("src/")) {
                                String relativePath = filePath.substring(4);
                                file = project.getFile(relativePath);
                                if (file.exists()) {
                                    workspaceFile = file;
                                    break;
                                }
                            }
                        }
                    }
                }

                if (workspaceFile == null || !workspaceFile.exists()) {
                    System.err.println("[ProjectService] File not found for clearing highlights: " + filePath);
                    return;
                }

                // Get the editor for this file
                IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
                if (window == null) {
                    System.err.println("[ProjectService] No active workbench window");
                    return;
                }

                IWorkbenchPage page = window.getActivePage();
                if (page == null) {
                    System.err.println("[ProjectService] No active page");
                    return;
                }

                // Try to find existing editor first
                IEditorPart editor = page.findEditor(new FileEditorInput(workspaceFile));
                if (editor == null) {
                    // Editor not open, try to open it
                    editor = IDE.openEditor(page, workspaceFile);
                }

                if (editor instanceof ITextEditor) {
                    clearDiffHighlights((ITextEditor) editor);
                } else {
                    System.out.println("[ProjectService] Editor is not a text editor, cannot clear highlights");
                }
            } catch (Exception e) {
                System.err.println("[ProjectService] Error clearing diff highlights by file path: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Clears all diff highlights from the editor
     * Called when user denies changes and file is restored
     */
    private void clearDiffHighlights(ITextEditor textEditor) {
        try {
            Display display = PlatformUI.getWorkbench().getDisplay();
            display.timerExec(100, () -> {
                try {
                    // Get the StyledText widget from the editor's source viewer
                    StyledText styledText = null;

                    // Try to get via ISourceViewer (most reliable for text editors)
                    ISourceViewer sourceViewer = textEditor.getAdapter(ISourceViewer.class);
                    if (sourceViewer != null) {
                        styledText = sourceViewer.getTextWidget();
                    }

                    // Fallback: try getting control directly
                    if (styledText == null) {
                        org.eclipse.swt.widgets.Control control = textEditor.getAdapter(org.eclipse.swt.widgets.Control.class);
                        if (control instanceof StyledText) {
                            styledText = (StyledText) control;
                        } else if (control instanceof org.eclipse.swt.widgets.Composite) {
                            styledText = findStyledText((org.eclipse.swt.widgets.Composite) control);
                        }
                    }

                    if (styledText != null && !styledText.isDisposed()) {
                        // Remove PaintListener if it exists
                        DiffHighlightState state = activeDiffEditors.remove(textEditor);
                        if (state != null && state.paintListener != null) {
                            styledText.removePaintListener(state.paintListener);
                            System.out.println("[ProjectService] Removed PaintListener from editor");
                            // Dispose colors
                            if (state.greenBg != null) state.greenBg.dispose();
                            if (state.greenFg != null) state.greenFg.dispose();
                            if (state.redBg != null) state.redBg.dispose();
                            if (state.redFg != null) state.redFg.dispose();
                        }
                        
                        // Clear all style ranges by setting an empty array
                        styledText.setStyleRanges(new StyleRange[0]);
                        System.out.println("[ProjectService] Cleared diff highlights from editor");
                    } else {
                        // Still clean up state even if we can't get the widget
                        DiffHighlightState state = activeDiffEditors.remove(textEditor);
                        if (state != null) {
                            if (state.greenBg != null) state.greenBg.dispose();
                            if (state.greenFg != null) state.greenFg.dispose();
                            if (state.redBg != null) state.redBg.dispose();
                            if (state.redFg != null) state.redFg.dispose();
                        }
                        System.out.println("[ProjectService] Could not get StyledText widget to clear highlights");
                    }
                } catch (Exception e) {
                    System.err.println("[ProjectService] Error clearing diff highlights: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            System.err.println("[ProjectService] Error setting up clear diff highlights: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Recursively finds StyledText widget in a composite
     */
    private StyledText findStyledText(org.eclipse.swt.widgets.Composite composite) {
        for (org.eclipse.swt.widgets.Control control : composite.getChildren()) {
            if (control instanceof StyledText) {
                return (StyledText) control;
            } else if (control instanceof org.eclipse.swt.widgets.Composite) {
                StyledText result = findStyledText((org.eclipse.swt.widgets.Composite) control);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    /**
     * Applies syntax highlighting to the editor based on combined lines.
     * Uses line number offsets - NO content matching, NO infinite loops.
     *
     * @param textEditor the text editor to apply highlights to
     * @param combined the combined lines with highlight information
     * @param documentText the current document text
     */
    private void applyHighlightsFromCombined(ITextEditor textEditor, List<CombinedLine> combined, String documentText) {
        try {
            // Get StyledText widget from editor's source viewer
            StyledText styledText = null;

            // Try to get via ISourceViewer (most reliable for text editors)
            ISourceViewer sourceViewer = textEditor.getAdapter(ISourceViewer.class);
            if (sourceViewer != null) {
                styledText = sourceViewer.getTextWidget();
            }

            // Fallback: try getting control directly
            if (styledText == null) {
                org.eclipse.swt.widgets.Control control = textEditor.getAdapter(org.eclipse.swt.widgets.Control.class);
                if (control instanceof StyledText) {
                    styledText = (StyledText) control;
                } else if (control instanceof org.eclipse.swt.widgets.Composite) {
                    styledText = findStyledText((org.eclipse.swt.widgets.Composite) control);
                }
            }

            if (styledText == null || styledText.isDisposed()) {
                System.err.println("[ProjectService] Could not get StyledText widget");
                return;
            }

            final StyledText finalStyledText = styledText;
            final Display display = styledText.getDisplay();

            // Build line offset array for O(1) lookup
            int[] lineOffsets = buildLineOffsets(documentText);

            // Create colors
            Color greenBg = new Color(display, 50, 150, 50); // Darker green for better contrast
            Color greenFg = new Color(display, 200, 255, 200); // Light green text
            Color redBg = new Color(display, 150, 50, 50); // Darker red for better contrast
            Color redFg = new Color(display, 255, 200, 200); // Light red text

            List<StyleRange> styleRanges = new ArrayList<>();

            // Apply highlights based on line numbers
            for (int lineNum = 0; lineNum < combined.size(); lineNum++) {
                CombinedLine line = combined.get(lineNum);

                if (line.highlight == CombinedLine.HighlightType.NONE) {
                    continue; // Skip unchanged lines
                }

                // Calculate character position for this line
                if (lineNum >= lineOffsets.length) {
                    System.err.println("[ProjectService] Line number out of bounds: " + lineNum);
                    continue;
                }

                int lineStart = lineOffsets[lineNum];
                int lineEnd;
                if (lineNum + 1 < lineOffsets.length) {
                    lineEnd = lineOffsets[lineNum + 1]; // Next line's start position
                } else {
                    lineEnd = documentText.length(); // Last line
                }

                int lineLength = lineEnd - lineStart;
                if (lineLength <= 0) continue;

                // Create style range
                StyleRange range = new StyleRange();
                range.start = lineStart;
                range.length = lineLength;

                if (line.highlight == CombinedLine.HighlightType.ADDED) {
                    range.background = greenBg;
                    range.foreground = greenFg;
                    System.out.println("[ProjectService] Highlighting ADDED line " + (lineNum + 1));
                } else { // REMOVED
                    range.background = redBg;
                    range.foreground = redFg;
                    System.out.println("[ProjectService] Highlighting REMOVED line " + (lineNum + 1));
                }

                styleRanges.add(range);
            }

            // Apply all styles at once
            StyleRange[] styleRangeArray = styleRanges.toArray(new StyleRange[0]);
            if (styleRangeArray.length > 0) {
                finalStyledText.setStyleRanges(styleRangeArray);
                System.out.println("[ProjectService] Successfully applied " + styleRangeArray.length + " highlight styles");
            } else {
                System.out.println("[ProjectService] No highlights to apply");
            }

            // Store state for this editor (including colors and style ranges for re-application)
            DiffHighlightState state = new DiffHighlightState(combined, documentText, styleRangeArray,
                    greenBg, greenFg, redBg, redFg);
            
            // Remove any existing PaintListener for this editor
            DiffHighlightState existingState = activeDiffEditors.get(textEditor);
            if (existingState != null && existingState.paintListener != null) {
                finalStyledText.removePaintListener(existingState.paintListener);
                // Dispose old colors
                if (existingState.greenBg != null) existingState.greenBg.dispose();
                if (existingState.greenFg != null) existingState.greenFg.dispose();
                if (existingState.redBg != null) existingState.redBg.dispose();
                if (existingState.redFg != null) existingState.redFg.dispose();
            }

            // Create PaintListener that re-applies highlights after every paint
            PaintListener paintListener = new PaintListener() {
                @Override
                public void paintControl(PaintEvent e) {
                    // Re-apply highlights to ensure they persist after syntax highlighting
                    // Use flag to prevent infinite recursion
                    if (!finalStyledText.isDisposed() && !state.isReapplying && styleRangeArray.length > 0) {
                        state.isReapplying = true;
                        try {
                            // Re-apply the style ranges asynchronously to avoid recursion
                            display.asyncExec(() -> {
                                if (!finalStyledText.isDisposed() && styleRangeArray.length > 0) {
                                    finalStyledText.setStyleRanges(styleRangeArray);
                                }
                                state.isReapplying = false;
                            });
                        } catch (Exception ex) {
                            state.isReapplying = false;
                        }
                    }
                }
            };
            
            // Add PaintListener to re-apply highlights when widget is painted
            finalStyledText.addPaintListener(paintListener);
            state.paintListener = paintListener;
            activeDiffEditors.put(textEditor, state);
            System.out.println("[ProjectService] Added PaintListener to re-apply diff highlights");

            // Dispose colors when widget is disposed
            finalStyledText.addDisposeListener(e -> {
                // Clean up state when widget is disposed
                DiffHighlightState disposedState = activeDiffEditors.remove(textEditor);
                if (disposedState != null && disposedState.paintListener != null) {
                    finalStyledText.removePaintListener(disposedState.paintListener);
                }
                greenBg.dispose();
                greenFg.dispose();
                redBg.dispose();
                redFg.dispose();
            });

        } catch (Exception e) {
            System.err.println("[ProjectService] Error applying highlights: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ========================================================================
    // NEW DIFF IMPLEMENTATION - Simple, Sequential, No Infinite Loops
    // ========================================================================

    /**
     * Represents a single diff operation from Unix diff output
     */
    private static class DiffOperation {
        enum Type { CHANGE, DELETE, ADD }

        final Type type;
        final int beforeStart;  // 1-indexed line in before file
        final int beforeEnd;    // 1-indexed line in before file (inclusive)
        final int afterStart;   // 1-indexed line in after file
        final int afterEnd;     // 1-indexed line in after file (inclusive)
        final List<String> removedLines;  // Lines removed (< prefix)
        final List<String> addedLines;    // Lines added (> prefix)

        DiffOperation(Type type, int beforeStart, int beforeEnd,
                      int afterStart, int afterEnd,
                      List<String> removedLines, List<String> addedLines) {
            this.type = type;
            this.beforeStart = beforeStart;
            this.beforeEnd = beforeEnd;
            this.afterStart = afterStart;
            this.afterEnd = afterEnd;
            this.removedLines = removedLines;
            this.addedLines = addedLines;
        }
    }

    /**
     * Represents a line in the final combined content with highlight information
     */
    private static class CombinedLine {
        enum HighlightType { NONE, ADDED, REMOVED }

        final String content;
        final HighlightType highlight;

        CombinedLine(String content, HighlightType highlight) {
            this.content = content;
            this.highlight = highlight;
        }
    }

    /**
     * Parses Unix diff output format into structured DiffOperation objects.
     * Handles formats like:
     *   1c1          - change line 1 to line 1
     *   4,5c4,6      - change lines 4-5 to lines 4-6
     *   5d4          - delete line 5
     *   7a9,11       - add after line 7, becoming lines 9-11
     */
    private List<DiffOperation> parseDiffOutput(String diffOutput) {
        List<DiffOperation> operations = new ArrayList<>();
        String[] lines = diffOutput.split("\n");
        int i = 0;

        java.util.regex.Pattern opPattern = java.util.regex.Pattern.compile("^(\\d+)(,(\\d+))?([acd])(\\d+)(,(\\d+))?$");

        while (i < lines.length) {
            String line = lines[i];
            java.util.regex.Matcher m = opPattern.matcher(line);

            if (m.matches()) {
                // Parse line numbers
                int beforeStart = Integer.parseInt(m.group(1));
                int beforeEnd = m.group(3) != null ? Integer.parseInt(m.group(3)) : beforeStart;
                char operation = m.group(4).charAt(0);
                int afterStart = Integer.parseInt(m.group(5));
                int afterEnd = m.group(7) != null ? Integer.parseInt(m.group(7)) : afterStart;

                DiffOperation.Type type;
                if (operation == 'c') type = DiffOperation.Type.CHANGE;
                else if (operation == 'd') type = DiffOperation.Type.DELETE;
                else type = DiffOperation.Type.ADD;

                i++; // Move to content lines

                // Collect removed lines (< prefix)
                List<String> removed = new ArrayList<>();
                while (i < lines.length && lines[i].startsWith("< ")) {
                    removed.add(lines[i].substring(2)); // Remove "< " prefix
                    i++;
                }

                // Skip separator (---)
                if (i < lines.length && lines[i].equals("---")) {
                    i++;
                }

                // Collect added lines (> prefix)
                List<String> added = new ArrayList<>();
                while (i < lines.length && lines[i].startsWith("> ")) {
                    added.add(lines[i].substring(2)); // Remove "> " prefix
                    i++;
                }

                operations.add(new DiffOperation(type, beforeStart, beforeEnd,
                                                 afterStart, afterEnd, removed, added));
            } else {
                i++;
            }
        }

        System.out.println("[ProjectService] Parsed " + operations.size() + " diff operations");
        return operations;
    }

    /**
     * Builds combined content with removed lines inserted at their original positions.
     * Uses sequential processing - no complex state machine, no infinite loops.
     *
     * @param afterLines the lines from the after file (Cline's changes)
     * @param operations the parsed diff operations
     * @return list of combined lines with highlight information
     */
    private List<CombinedLine> buildCombinedContent(String[] afterLines, List<DiffOperation> operations) {
        List<CombinedLine> combined = new ArrayList<>();
        int afterIndex = 0; // 0-indexed position in afterLines array

        for (DiffOperation op : operations) {
            // Add unchanged lines from after file up to this operation
            int insertPosition = op.afterStart - 1; // Convert to 0-indexed

            // Add unchanged lines before this operation
            while (afterIndex < insertPosition && afterIndex < afterLines.length) {
                combined.add(new CombinedLine(afterLines[afterIndex], CombinedLine.HighlightType.NONE));
                afterIndex++;
            }

            // Add removed lines with REMOVED highlight
            for (String removed : op.removedLines) {
                combined.add(new CombinedLine(removed, CombinedLine.HighlightType.REMOVED));
            }

            // Add added lines with ADDED highlight (and consume from after array)
            for (String added : op.addedLines) {
                combined.add(new CombinedLine(added, CombinedLine.HighlightType.ADDED));
                afterIndex++; // Consume corresponding line from after file
            }
        }

        // Add remaining unchanged lines
        while (afterIndex < afterLines.length) {
            combined.add(new CombinedLine(afterLines[afterIndex], CombinedLine.HighlightType.NONE));
            afterIndex++;
        }

        System.out.println("[ProjectService] Built combined content with " + combined.size() + " lines");
        return combined;
    }

    /**
     * Converts list of CombinedLine objects to a single string with newlines
     */
    private String combinedLinesToString(List<CombinedLine> combined) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < combined.size(); i++) {
            sb.append(combined.get(i).content);
            if (i < combined.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Builds array of character offsets for each line in the text.
     * This enables O(1) lookup of line start positions.
     *
     * @param text the document text
     * @return array where index i contains the character offset of line i
     */
    private int[] buildLineOffsets(String text) {
        List<Integer> offsets = new ArrayList<>();
        offsets.add(0); // Line 0 starts at position 0

        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                offsets.add(i + 1); // Next line starts after newline
            }
        }

        return offsets.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Computes diff operations by calling Unix diff command and parsing the output.
     *
     * @param beforeContent content before changes
     * @param afterContent content after changes
     * @return list of diff operations
     */
    private List<DiffOperation> computeDiffOperations(String beforeContent, String afterContent)
            throws java.io.IOException, InterruptedException {

        File beforeFile = null;
        File afterFile = null;

        try {
            // Create temp files
            beforeFile = File.createTempFile("diff_before_", ".txt");
            afterFile = File.createTempFile("diff_after_", ".txt");

            // Write contents
            Files.write(beforeFile.toPath(), beforeContent.getBytes());
            Files.write(afterFile.toPath(), afterContent.getBytes());

            // Execute diff command
            ProcessBuilder pb = new ProcessBuilder("diff",
                    beforeFile.getAbsolutePath(),
                    afterFile.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();

            // Exit code 0 = files identical, 1 = files differ, 2+ = error
            if (exitCode == 0) {
                System.out.println("[ProjectService] Files are identical, no diff operations");
                return new ArrayList<>(); // No differences
            } else if (exitCode > 1) {
                throw new java.io.IOException("diff command failed with exit code: " + exitCode);
            }

            // Parse diff output
            return parseDiffOutput(output.toString());

        } finally {
            // Clean up temp files
            if (beforeFile != null) {
                beforeFile.delete();
            }
            if (afterFile != null) {
                afterFile.delete();
            }
        }
    }

    // ========================================================================

    /**
     * Constructs new file content by applying Cline's SEARCH/REPLACE format.
     * This is a port of Cline's constructNewFileContentV1 function from diff.ts.
     *
     * The diff format uses these markers:
     *   ------- SEARCH
     *   [Exact content to find]
     *   =======
     *   [Content to replace with]
     *   +++++++ REPLACE
     */
    private String constructNewFileContent(String diffContent, String originalContent) {
        return constructNewFileContentV1(diffContent, originalContent, true);
    }

    /**
     * Port of Cline's constructNewFileContentV1 function.
     * Handles SEARCH/REPLACE parsing with fallback matching strategies.
     */
    private String constructNewFileContentV1(String diffContent, String originalContent, boolean isFinal) {
        StringBuilder result = new StringBuilder();
        int lastProcessedIndex = 0;

        StringBuilder currentSearchContent = new StringBuilder();
        StringBuilder currentReplaceContent = new StringBuilder();
        boolean inSearch = false;
        boolean inReplace = false;

        int searchMatchIndex = -1;
        int searchEndIndex = -1;

        // Track all replacements to handle out-of-order edits
        java.util.List<Replacement> replacements = new java.util.ArrayList<>();
        boolean pendingOutOfOrderReplacement = false;

        String[] lines = diffContent.split("\n", -1);
        java.util.List<String> linesList = new java.util.ArrayList<>(java.util.Arrays.asList(lines));

        // If the last line looks like a partial marker but isn't recognized, remove it
        if (linesList.size() > 0) {
            String lastLine = linesList.get(linesList.size() - 1);
            if ((lastLine.startsWith("-") || lastLine.startsWith("<") || 
                 lastLine.startsWith("=") || lastLine.startsWith("+") || 
                 lastLine.startsWith(">")) &&
                !isSearchBlockStart(lastLine) &&
                !isSearchBlockEnd(lastLine) &&
                !isReplaceBlockEnd(lastLine)) {
                linesList.remove(linesList.size() - 1);
            }
        }

        for (String line : linesList) {
            if (isSearchBlockStart(line)) {
                inSearch = true;
                currentSearchContent = new StringBuilder();
                currentReplaceContent = new StringBuilder();
                continue;
            }

            if (isSearchBlockEnd(line)) {
                inSearch = false;
                inReplace = true;

                String searchText = currentSearchContent.toString();

                if (searchText.isEmpty()) {
                    // Empty search block
                    if (originalContent.isEmpty()) {
                        // New file scenario: nothing to match, just start inserting
                        searchMatchIndex = 0;
                        searchEndIndex = 0;
                    } else {
                        throw new IllegalArgumentException(
                            "Empty SEARCH block detected with non-empty file. This usually indicates a malformed SEARCH marker.\n" +
                            "Please ensure your SEARCH marker follows the correct format:\n" +
                            "- Use '------- SEARCH' (7+ dashes + space + SEARCH)\n"
                        );
                    }
                } else {
                    // Exact search match scenario
                    int exactIndex = originalContent.indexOf(searchText, lastProcessedIndex);
                    if (exactIndex != -1) {
                        searchMatchIndex = exactIndex;
                        searchEndIndex = exactIndex + searchText.length();
                    } else {
                        // Attempt fallback line-trimmed matching
                        int[] lineMatch = lineTrimmedFallbackMatch(originalContent, searchText, lastProcessedIndex);
                        if (lineMatch != null) {
                            searchMatchIndex = lineMatch[0];
                            searchEndIndex = lineMatch[1];
                        } else {
                            // Try block anchor fallback for larger blocks
                            int[] blockMatch = blockAnchorFallbackMatch(originalContent, searchText, lastProcessedIndex);
                            if (blockMatch != null) {
                                searchMatchIndex = blockMatch[0];
                                searchEndIndex = blockMatch[1];
                            } else {
                                // Last resort: search the entire file from the beginning
                                int fullFileIndex = originalContent.indexOf(searchText, 0);
                                if (fullFileIndex != -1) {
                                    // Found in the file - could be out of order
                                    searchMatchIndex = fullFileIndex;
                                    searchEndIndex = fullFileIndex + searchText.length();
                                    if (searchMatchIndex < lastProcessedIndex) {
                                        pendingOutOfOrderReplacement = true;
                                    }
                                } else {
                                    throw new IllegalArgumentException(
                                        "The SEARCH block:\n" + searchText.trim() + "\n...does not match anything in the file."
                                    );
                                }
                            }
                        }
                    }
                }

                // Check if this is an out-of-order replacement
                if (searchMatchIndex < lastProcessedIndex) {
                    pendingOutOfOrderReplacement = true;
                }

                // For in-order replacements, output everything up to the match location
                if (!pendingOutOfOrderReplacement) {
                    result.append(originalContent, lastProcessedIndex, searchMatchIndex);
                }
                continue;
            }

            if (isReplaceBlockEnd(line)) {
                // Finished one replace block
                if (searchMatchIndex == -1) {
                    throw new IllegalArgumentException(
                        "The SEARCH block:\n" + currentSearchContent.toString().trim() + "\n...is malformatted."
                    );
                }

                // Store this replacement
                replacements.add(new Replacement(searchMatchIndex, searchEndIndex, currentReplaceContent.toString()));

                // If this was an in-order replacement, advance lastProcessedIndex
                if (!pendingOutOfOrderReplacement) {
                    lastProcessedIndex = searchEndIndex;
                }

                // Reset for next block
                inSearch = false;
                inReplace = false;
                currentSearchContent = new StringBuilder();
                currentReplaceContent = new StringBuilder();
                searchMatchIndex = -1;
                searchEndIndex = -1;
                pendingOutOfOrderReplacement = false;
                continue;
            }

            // Accumulate content for search or replace
            if (inSearch) {
                currentSearchContent.append(line).append("\n");
            } else if (inReplace) {
                currentReplaceContent.append(line).append("\n");
                // Only output replacement lines immediately for in-order replacements
                if (searchMatchIndex != -1 && !pendingOutOfOrderReplacement) {
                    result.append(line).append("\n");
                }
            }
        }

        // If this is the final chunk, we need to apply all replacements and build the final result
        if (isFinal) {
            // Handle the case where we're still in replace mode when processing ends
            if (inReplace && searchMatchIndex != -1) {
                // Store this replacement
                replacements.add(new Replacement(searchMatchIndex, searchEndIndex, currentReplaceContent.toString()));

                // If this was an in-order replacement, advance lastProcessedIndex
                if (!pendingOutOfOrderReplacement) {
                    lastProcessedIndex = searchEndIndex;
                }
            }

            // Sort replacements by start position
            replacements.sort((a, b) -> Integer.compare(a.start, b.start));

            // Rebuild the entire result by applying all replacements
            result = new StringBuilder();
            int currentPos = 0;

            for (Replacement replacement : replacements) {
                // Add original content up to this replacement
                result.append(originalContent, currentPos, replacement.start);
                // Add the replacement content
                result.append(replacement.content);
                // Move position to after the replaced section
                currentPos = replacement.end;
            }

            // Add any remaining original content
            result.append(originalContent.substring(currentPos));
        }

        return result.toString();
    }

    /**
     * Helper class to track replacements for out-of-order edits
     */
    private static class Replacement {
        final int start;
        final int end;
        final String content;

        Replacement(int start, int end, String content) {
            this.start = start;
            this.end = end;
            this.content = content;
        }
    }

    /**
     * Checks if a line is a SEARCH block start marker.
     * Port of Cline's isSearchBlockStart function.
     */
    private boolean isSearchBlockStart(String line) {
        return line.matches("^[-]{3,} SEARCH>?$") || line.matches("^[<]{3,} SEARCH>?$");
    }

    /**
     * Checks if a line is a SEARCH block end marker (=======).
     * Port of Cline's isSearchBlockEnd function.
     */
    private boolean isSearchBlockEnd(String line) {
        return line.matches("^[=]{3,}$");
    }

    /**
     * Checks if a line is a REPLACE block end marker.
     * Port of Cline's isReplaceBlockEnd function.
     */
    private boolean isReplaceBlockEnd(String line) {
        return line.matches("^[+]{3,} REPLACE>?$") || line.matches("^[>]{3,} REPLACE>?$");
    }

    /**
     * Attempts a line-trimmed fallback match for the given search content.
     * Port of Cline's lineTrimmedFallbackMatch function.
     * Returns [matchStartIndex, matchEndIndex] if found, or null if not found.
     */
    private int[] lineTrimmedFallbackMatch(String originalContent, String searchContent, int startIndex) {
        String[] originalLines = originalContent.split("\n", -1);
        String[] searchLines = searchContent.split("\n", -1);

        // Trim trailing empty line if exists
        java.util.List<String> searchLinesList = new java.util.ArrayList<>(java.util.Arrays.asList(searchLines));
        if (searchLinesList.size() > 0 && searchLinesList.get(searchLinesList.size() - 1).isEmpty()) {
            searchLinesList.remove(searchLinesList.size() - 1);
        }
        searchLines = searchLinesList.toArray(new String[0]);

        // Find the line number where startIndex falls
        int startLineNum = 0;
        int currentIndex = 0;
        while (currentIndex < startIndex && startLineNum < originalLines.length) {
            currentIndex += originalLines[startLineNum].length() + 1; // +1 for \n
            startLineNum++;
        }

        // For each possible starting position in original content
        for (int i = startLineNum; i <= originalLines.length - searchLines.length; i++) {
            boolean matches = true;

            // Try to match all search lines from this position
            for (int j = 0; j < searchLines.length; j++) {
                String originalTrimmed = originalLines[i + j].trim();
                String searchTrimmed = searchLines[j].trim();

                if (!originalTrimmed.equals(searchTrimmed)) {
                    matches = false;
                    break;
                }
            }

            // If we found a match, calculate the exact character positions
            if (matches) {
                // Find start character index
                int matchStartIndex = 0;
                for (int k = 0; k < i; k++) {
                    matchStartIndex += originalLines[k].length() + 1; // +1 for \n
                }

                // Find end character index
                int matchEndIndex = matchStartIndex;
                for (int k = 0; k < searchLines.length; k++) {
                    matchEndIndex += originalLines[i + k].length() + 1; // +1 for \n
                }

                return new int[]{matchStartIndex, matchEndIndex};
            }
        }

        return null;
    }

    /**
     * Attempts to match blocks of code by using the first and last lines as anchors.
     * Port of Cline's blockAnchorFallbackMatch function.
     * Returns [matchStartIndex, matchEndIndex] if found, or null if not found.
     */
    private int[] blockAnchorFallbackMatch(String originalContent, String searchContent, int startIndex) {
        String[] originalLines = originalContent.split("\n", -1);
        String[] searchLines = searchContent.split("\n", -1);

        // Only use this approach for blocks of 3+ lines
        if (searchLines.length < 3) {
            return null;
        }

        // Trim trailing empty line if exists
        java.util.List<String> searchLinesList = new java.util.ArrayList<>(java.util.Arrays.asList(searchLines));
        if (searchLinesList.size() > 0 && searchLinesList.get(searchLinesList.size() - 1).isEmpty()) {
            searchLinesList.remove(searchLinesList.size() - 1);
        }
        searchLines = searchLinesList.toArray(new String[0]);

        String firstLineSearch = searchLines[0].trim();
        String lastLineSearch = searchLines[searchLines.length - 1].trim();
        int searchBlockSize = searchLines.length;

        // Find the line number where startIndex falls
        int startLineNum = 0;
        int currentIndex = 0;
        while (currentIndex < startIndex && startLineNum < originalLines.length) {
            currentIndex += originalLines[startLineNum].length() + 1; // +1 for \n
            startLineNum++;
        }

        // Look for matching start and end anchors
        for (int i = startLineNum; i <= originalLines.length - searchBlockSize; i++) {
            // Check if first line matches
            if (!originalLines[i].trim().equals(firstLineSearch)) {
                continue;
            }

            // Check if last line matches at the expected position
            if (!originalLines[i + searchBlockSize - 1].trim().equals(lastLineSearch)) {
                continue;
            }

            // Calculate exact character positions
            int matchStartIndex = 0;
            for (int k = 0; k < i; k++) {
                matchStartIndex += originalLines[k].length() + 1; // +1 for \n
            }

            int matchEndIndex = matchStartIndex;
            for (int k = 0; k < searchBlockSize; k++) {
                matchEndIndex += originalLines[i + k].length() + 1; // +1 for \n
            }

            return new int[]{matchStartIndex, matchEndIndex};
        }

        return null;
    }

}