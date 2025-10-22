package embeddedcopilot.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.views.WorkbenchViewerSetup;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.IEditorPart;
import org.eclipse.jface.dialogs.MessageDialog;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.IPath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.SWT;
import org.eclipse.ui.part.ViewPart;

public class SampleHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
        try {
            page.showView("embeddedcopilot.views.SampleView");
        } catch (PartInitException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void getAllParts() {
        // Get the top-level workbench instance
        IWorkbench workbench = PlatformUI.getWorkbench();

        // Get the active workbench window (the one with focus)
        IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();

        // Check if there is an active window
        if (window != null) {
            // Get the active page (which represents the current perspective)
            IWorkbenchPage page = window.getActivePage();

            try {
				String snapshot = Files.readString(
					    Path.of(Platform.getLogFileLocation().toOSString()),
					    StandardCharsets.UTF_8
				);
				
				System.out.println(snapshot);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

            // Check if there is an active page
//            if (page != null) {
//                System.out.println("Getting all open editors...");
//                IEditorReference[] editorReferences = page.getEditorReferences();
//                for (IEditorReference editorRef : editorReferences) {
//                    System.out.println("  Editor: " + editorRef.getTitle());
//                }
//                
//                Platform.addLogListener((status, pluginId) -> {
//                	  // This is exactly what the Error Log shows
//                	  System.out.printf("[%s] %s: %s%n", pluginId, status.getSeverity(), status.getMessage());
//            	});
//
//                System.out.println("\nGetting all open views...");
//                IViewReference[] viewReferences = page.getViewReferences();
//                for (IViewReference viewRef : viewReferences) {
//                    System.out.println("  View: " + viewRef.getPartName());
//                    if (viewRef != null) {
//	                    if (viewRef.getPartName() == "Problems" || viewRef.getPartName() == "Error Log") {
//	                    	    IViewPart viewPart = viewRef.getView(true);
//	                    	    System.out.println(viewPart.getClass());
////	                    	    if (viewPart instanceof YourSpecificViewClass) {
////	                    	        YourSpecificViewClass yourView = (YourSpecificViewClass) viewPart;
////	                    	        yourView.someMethod();
////	                    	    }
//                    	}
//                    }
//                }
//            }
        }
    }

    public IWorkbenchWindow[] getAllWorkbenchWindows() {
        IWorkbench workbench = PlatformUI.getWorkbench();
        return workbench.getWorkbenchWindows();
    }

    public void printAllWindowTitles() {
        IWorkbenchWindow[] windows = getAllWorkbenchWindows();
        if (windows.length == 0) {
            System.out.println("No workbench windows are currently open.");
            return;
        }

        System.out.println("Found " + windows.length + " open workbench window(s):");
        for (IWorkbenchWindow window : windows) {
            System.out.println("  - " + window.getShell().getText());
        }
    }
}