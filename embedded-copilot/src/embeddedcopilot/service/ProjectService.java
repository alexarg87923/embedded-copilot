package embeddedcopilot.service;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.ISelectionService;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;

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
}