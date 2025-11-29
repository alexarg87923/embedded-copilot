package embeddedcopilot.service;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.jface.text.IDocument;
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
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for Eclipse project-related operations
 */
public class ProjectService {

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
     * Shows a diff view for file edits using Eclipse's compare editor.
     * Applies changes to the REAL workspace file immediately and saves a backup.
     * User can approve (delete backup) or deny (restore from backup).
     *
     * @param filePath the relative path to the file being edited
     * @param diffContent the diff content in SEARCH/REPLACE format
     * @param onEditorOpened callback invoked with the editor part and backup file path when opened (for tracking/cleanup)
     */
    public void showDiffView(String filePath, String diffContent, java.util.function.BiConsumer<IEditorPart, File> onEditorOpened) {
        try {
            String projectRoot = getProjectRootDirectory();
            if (projectRoot == null) {
                System.err.println("[ProjectService] Cannot show diff: no project root found");
                return;
            }

            // Parse the diff content to extract all SEARCH/REPLACE blocks
            List<SearchReplaceBlock> blocks = parseDiffContent(diffContent);
            if (blocks == null || blocks.isEmpty()) {
                System.err.println("[ProjectService] Failed to parse diff content or no blocks found");
                return;
            }

            System.out.println("[ProjectService] Parsed " + blocks.size() + " SEARCH/REPLACE blocks");

            // Get the full file path
            File file = new File(projectRoot, filePath);
            String fullPath = file.getAbsolutePath();

            // Read current file content if it exists
            String currentContent = "";
            if (file.exists()) {
                currentContent = new String(Files.readAllBytes(Paths.get(fullPath)));
            }

            // The "before" content is the current file
            String beforeContent = currentContent;

            // The "after" content is the current file with all SEARCH/REPLACE blocks applied sequentially
            String afterContent = currentContent;

            // Apply each SEARCH/REPLACE block sequentially
            for (int i = 0; i < blocks.size(); i++) {
                SearchReplaceBlock block = blocks.get(i);

                if (afterContent.contains(block.searchText)) {
                    // Replace the FIRST occurrence (since each block should be unique)
                    int index = afterContent.indexOf(block.searchText);
                    afterContent = afterContent.substring(0, index) +
                                   block.replaceText +
                                   afterContent.substring(index + block.searchText.length());

                    System.out.println("[ProjectService] Applied block " + (i + 1) + " replacement");
                } else {
                    System.err.println("[ProjectService] WARNING: Block " + (i + 1) + " SEARCH text not found in file!");
                    System.err.println("[ProjectService] SEARCH text (first 100 chars): " +
                        block.searchText.substring(0, Math.min(100, block.searchText.length())));
                }
            }

            System.out.println("[ProjectService] Before content: " + beforeContent.split("\n").length + " lines");
            System.out.println("[ProjectService] After content: " + afterContent.split("\n").length + " lines");

            // Apply changes to the REAL file and open it with highlighting
            applyChangesToRealFile(filePath, beforeContent, afterContent, onEditorOpened);

            System.out.println("[ProjectService] Applied changes and opened diff view for: " + filePath);
        } catch (Exception e) {
            System.err.println("[ProjectService] Error showing diff view: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Applies changes to the REAL workspace file and opens it with highlighting.
     * Saves a backup of the original content for potential rollback.
     */
    private void applyChangesToRealFile(String filePath, String beforeContent, String afterContent, java.util.function.BiConsumer<IEditorPart, File> onEditorOpened) {
        Display display = PlatformUI.getWorkbench().getDisplay();
        display.asyncExec(() -> {
            try {
                // Get the workspace file
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

                // Find the file in the workspace - try multiple locations
                IFile workspaceFile = null;

                // Try direct path first
                workspaceFile = ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(filePath));
                if (!workspaceFile.exists()) {
                    // Try finding in all projects
                    IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
                    for (IProject project : projects) {
                        if (project.isOpen()) {
                            IFile file = project.getFile(filePath);
                            if (file.exists()) {
                                workspaceFile = file;
                                break;
                            }
                            // Also try with src/ prefix removed if filePath starts with src/
                            if (filePath.startsWith("src/")) {
                                String relativePath = filePath.substring(4); // Remove "src/"
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
                    System.err.println("[ProjectService] File not found in workspace: " + filePath);
                    return;
                }

                // Create a backup file with the original content
                File tempDir = new File(System.getProperty("java.io.tmpdir"));
                String baseName = workspaceFile.getName();
                int dotIndex = baseName.lastIndexOf('.');
                String nameWithoutExt = dotIndex > 0 ? baseName.substring(0, dotIndex) : baseName;
                String ext = dotIndex > 0 ? baseName.substring(dotIndex) : "";

                File backupFile = File.createTempFile(nameWithoutExt + "_backup_", ext, tempDir);
                Files.write(Paths.get(backupFile.getAbsolutePath()), beforeContent.getBytes());
                System.out.println("[ProjectService] Created backup: " + backupFile.getAbsolutePath());

                // Apply changes to the REAL workspace file
                InputStream afterStream = new ByteArrayInputStream(afterContent.getBytes());
                workspaceFile.setContents(afterStream, IResource.FORCE, new NullProgressMonitor());
                System.out.println("[ProjectService] Applied changes to workspace file: " + filePath);

                // Open the real workspace file in editor
                IEditorPart editor = IDE.openEditor(page, workspaceFile);

                // Notify callback with the opened editor and backup file
                if (onEditorOpened != null) {
                    onEditorOpened.accept(editor, backupFile);
                }

                // Add color highlights to show what changed
                if (editor instanceof ITextEditor) {
                    addDiffHighlights((ITextEditor) editor, beforeContent, afterContent);
                }

            } catch (Exception e) {
                System.err.println("[ProjectService] Error applying changes to file: " + e.getMessage());
                e.printStackTrace();
            }
        });
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
                    // Restore backup content to workspace file
                    InputStream backupStream = new ByteArrayInputStream(backupContent.getBytes());
                    workspaceFile.setContents(backupStream, IResource.FORCE, new NullProgressMonitor());
                    System.out.println("[ProjectService] Restored file from backup: " + filePath);

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
     * Adds color highlights to the editor to show what changed
     * Green background for added lines
     */
    private void addDiffHighlights(ITextEditor textEditor, String beforeContent, String afterContent) {
        try {
            IDocument document = textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
            if (document == null) {
                System.err.println("[ProjectService] Cannot get document from editor");
                return;
            }
            
            // Wait a bit for the editor to fully load
            Display display = PlatformUI.getWorkbench().getDisplay();
            display.timerExec(500, () -> {
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

                    if (styledText != null) {
                        System.out.println("[ProjectService] Successfully got StyledText widget");

                        // Compute line-by-line diff
                        String[] beforeLines = beforeContent.split("\n", -1);
                        String[] afterLines = afterContent.split("\n", -1);
                        List<DiffLine> diffLines = computeDiffLines(beforeLines, afterLines);

                        // Log what we're computing
                        System.out.println("[ProjectService] Diff computation:");
                        System.out.println("[ProjectService]   Before lines: " + beforeLines.length);
                        System.out.println("[ProjectService]   After lines: " + afterLines.length);
                        System.out.println("[ProjectService]   Diff lines: " + diffLines.size());
                        int addedCount = 0, removedCount = 0, unchangedCount = 0;
                        for (DiffLine dl : diffLines) {
                            if (dl.type == DiffLineType.ADDED) addedCount++;
                            else if (dl.type == DiffLineType.REMOVED) removedCount++;
                            else unchangedCount++;
                        }
                        System.out.println("[ProjectService]   Added: " + addedCount + ", Removed: " + removedCount + ", Unchanged: " + unchangedCount);
                        
                        // Create style ranges for added lines using line numbers
                        List<StyleRange> styleRanges = new ArrayList<>();
                        // Use a more subtle green that works better in dark mode
                        Color greenColor = new Color(display, 50, 150, 50); // Darker green for better contrast
                        Color greenTextColor = new Color(display, 200, 255, 200); // Light green text
                        
                        String documentText = document.get();
                        String[] documentLines = documentText.split("\n", -1);
                        
                        // Track which line in the document we're on
                        int documentLineIndex = 0;
                        
                        for (DiffLine diffLine : diffLines) {
                            if (diffLine.type == DiffLineType.ADDED) {
                                // This is an added line - highlight it
                                if (documentLineIndex < documentLines.length) {
                                    // Find the start offset of this line
                                    int lineStart = 0;
                                    for (int i = 0; i < documentLineIndex; i++) {
                                        lineStart += documentLines[i].length() + 1; // +1 for newline
                                    }
                                    
                                    // Find the end offset (including newline if present)
                                    int lineLength = documentLines[documentLineIndex].length();
                                    boolean hasNewline = documentLineIndex < documentLines.length - 1 || 
                                                         documentText.endsWith("\n");
                                    if (hasNewline) {
                                        lineLength++; // Include newline
                                    }
                                    
                                    StyleRange styleRange = new StyleRange();
                                    styleRange.start = lineStart;
                                    styleRange.length = lineLength;
                                    styleRange.background = greenColor;
                                    styleRange.foreground = greenTextColor; // Set text color for better contrast
                                    styleRanges.add(styleRange);
                                    
                                    System.out.println("[ProjectService] Highlighting added line " + (documentLineIndex + 1) + 
                                        ": \"" + (documentLines[documentLineIndex].length() > 50 ? 
                                        documentLines[documentLineIndex].substring(0, 50) + "..." : 
                                        documentLines[documentLineIndex]) + "\"");
                                    
                                    documentLineIndex++;
                                }
                            } else if (diffLine.type == DiffLineType.UNCHANGED) {
                                // Unchanged line - skip it (don't highlight)
                                if (documentLineIndex < documentLines.length) {
                                    documentLineIndex++;
                                }
                            }
                            // REMOVED lines don't exist in the document, so we don't increment documentLineIndex
                        }
                        
                        // Apply the styles
                        if (!styleRanges.isEmpty()) {
                            System.out.println("[ProjectService] About to apply " + styleRanges.size() + " style ranges");
                            System.out.println("[ProjectService] StyledText has " + styledText.getCharCount() + " characters");
                            System.out.println("[ProjectService] Document has " + document.get().length() + " characters");

                            styledText.setStyleRanges(styleRanges.toArray(new StyleRange[0]));
                            System.out.println("[ProjectService] Successfully applied " + styleRanges.size() + " highlight styles to editor");

                            // Verify styles were applied
                            StyleRange[] appliedStyles = styledText.getStyleRanges();
                            System.out.println("[ProjectService] Verified: editor now has " + appliedStyles.length + " style ranges");
                        } else {
                            System.out.println("[ProjectService] No styles to apply - no added lines found");
                        }
                    } else {
                        System.err.println("[ProjectService] Failed to get StyledText widget from editor");
                    }
                } catch (Exception e) {
                    System.err.println("[ProjectService] Error applying highlights: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            
        } catch (Exception e) {
            System.err.println("[ProjectService] Error adding diff highlights: " + e.getMessage());
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
     * Computes line-by-line diff between before and after content
     * Uses a simple longest common subsequence approach
     */
    private List<DiffLine> computeDiffLines(String[] beforeLines, String[] afterLines) {
        List<DiffLine> result = new ArrayList<>();
        
        int beforeIndex = 0;
        int afterIndex = 0;
        
        // Simple line-by-line matching algorithm
        while (beforeIndex < beforeLines.length || afterIndex < afterLines.length) {
            if (beforeIndex >= beforeLines.length) {
                // Only after lines remain - all additions
                result.add(new DiffLine(DiffLineType.ADDED, afterLines[afterIndex]));
                afterIndex++;
            } else if (afterIndex >= afterLines.length) {
                // Only before lines remain - all deletions
                result.add(new DiffLine(DiffLineType.REMOVED, beforeLines[beforeIndex]));
                beforeIndex++;
            } else if (beforeLines[beforeIndex].equals(afterLines[afterIndex])) {
                // Lines match - unchanged
                result.add(new DiffLine(DiffLineType.UNCHANGED, beforeLines[beforeIndex]));
                beforeIndex++;
                afterIndex++;
            } else {
                // Lines don't match - need to decide if it's a deletion, addition, or both
                // Look ahead to see if we can find a match
                boolean foundMatch = false;
                
                // Check if next before line matches current after line (deletion)
                if (beforeIndex + 1 < beforeLines.length && 
                    beforeLines[beforeIndex + 1].equals(afterLines[afterIndex])) {
                    result.add(new DiffLine(DiffLineType.REMOVED, beforeLines[beforeIndex]));
                    beforeIndex++;
                    foundMatch = true;
                }
                // Check if next after line matches current before line (addition)
                else if (afterIndex + 1 < afterLines.length && 
                         beforeLines[beforeIndex].equals(afterLines[afterIndex + 1])) {
                    result.add(new DiffLine(DiffLineType.ADDED, afterLines[afterIndex]));
                    afterIndex++;
                    foundMatch = true;
                }
                
                if (!foundMatch) {
                    // Both lines changed - mark as removed then added
                    result.add(new DiffLine(DiffLineType.REMOVED, beforeLines[beforeIndex]));
                    result.add(new DiffLine(DiffLineType.ADDED, afterLines[afterIndex]));
                    beforeIndex++;
                    afterIndex++;
                }
            }
        }
        
        return result;
    }
    
    /**
     * Represents a line in the diff
     */
    private static class DiffLine {
        final DiffLineType type;
        final String content;
        
        DiffLine(DiffLineType type, String content) {
            this.type = type;
            this.content = content;
        }
    }
    
    /**
     * Type of diff line
     */
    private enum DiffLineType {
        UNCHANGED,
        ADDED,
        REMOVED
    }

    /**
     * Parses diff content in SEARCH/REPLACE format.
     * The format can have multiple blocks:
     * ------- SEARCH\n<old content>\n=======\n<new content>\n+++++++ REPLACE\n
     *
     * Returns a list of SEARCH/REPLACE pairs to be applied sequentially.
     */
    private List<SearchReplaceBlock> parseDiffContent(String diffContent) {
        List<SearchReplaceBlock> blocks = new ArrayList<>();

        try {
            // Split by the SEARCH marker
            String[] searchBlocks = diffContent.split("------- SEARCH");

            for (int i = 1; i < searchBlocks.length; i++) { // Start at 1 to skip content before first SEARCH
                String block = searchBlocks[i];

                // Find the ======= separator
                int separatorIndex = block.indexOf("=======");
                if (separatorIndex < 0) {
                    System.err.println("[ProjectService] No ======= separator found in block " + i);
                    continue;
                }

                // Find the +++++++ REPLACE marker
                int replaceMarkerIndex = block.indexOf("+++++++ REPLACE");
                if (replaceMarkerIndex < 0) {
                    System.err.println("[ProjectService] No +++++++ REPLACE marker found in block " + i);
                    continue;
                }

                // Extract SEARCH content (between start and =======)
                String searchContent = block.substring(0, separatorIndex).trim();

                // Extract REPLACE content (between ======= and +++++++ REPLACE)
                String replaceContent = block.substring(separatorIndex + "=======".length(), replaceMarkerIndex).trim();

                blocks.add(new SearchReplaceBlock(searchContent, replaceContent));

                System.out.println("[ProjectService] Parsed block " + i + ": SEARCH=" + searchContent.length() + " chars, REPLACE=" + replaceContent.length() + " chars");
            }

            return blocks;
        } catch (Exception e) {
            System.err.println("[ProjectService] Error parsing diff: " + e.getMessage());
            e.printStackTrace();
            return blocks;
        }
    }

    /**
     * Represents a single SEARCH/REPLACE block
     */
    private static class SearchReplaceBlock {
        final String searchText;
        final String replaceText;

        SearchReplaceBlock(String searchText, String replaceText) {
            this.searchText = searchText;
            this.replaceText = replaceText;
        }
    }

}