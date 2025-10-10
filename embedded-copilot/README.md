# Embedded Copilot

An Eclipse plugin for AI-powered code assistance.

## Prerequisites

- Eclipse IDE for Java Developers
- Eclipse Plugin Development Environment (PDE)

## Setup Instructions

### 1. Install Eclipse IDE

Download **Eclipse IDE for Java Developers** from:
[https://www.eclipse.org/downloads/packages/](https://www.eclipse.org/downloads/packages/)

### 2. Install Eclipse PDE

You have two options to install Eclipse Plugin Development Tools:

#### Option A: Direct Download
Download Eclipse PDE from:
[https://download.eclipse.org/eclipse/downloads/](https://download.eclipse.org/eclipse/downloads/)

#### Option B: Install Through Eclipse
1. Open Eclipse
2. Go to **Help > Install New Software**
3. In the install window, select **The Eclipse Project Updates** from the "Work with" field
4. In the list, select **Eclipse Plugin Development Tools**
5. Proceed with the license terms and click **Finish**

**Reference:** [Creating Your First Eclipse Plugin](https://medium.com/@ravi_theja/creating-your-first-eclipse-plugin-9b1b5ba33b58)

### 3. Import the Project

#### Mac

1. Open Eclipse and select this repository as your working directory
2. If you don't see the embedded-copilot project:
   - Go to **File > Import**
   - Select **General > Projects from Folder or Archive**
   - Navigate to and select the `./embedded-copilot` directory
   - Click **Finish**

#### Windows

1. Open Eclipse and select this repository as your working directory
2. If you don't see the embedded-copilot project:
   - Go to **File > Import**
   - Select **General > Existing Projects into Workspace** or **Projects from Folder or Archive**
   - Navigate to and select the `./embedded-copilot` directory
   - Click **Finish**

### 4. Run the Plugin

#### Mac

1. Go to **Run > Run**
2. Select **Eclipse Application**
3. The plugin will launch in a new Eclipse instance

#### Windows

1. Go to **Run > Run** or **Run > Run As**
2. Select **Eclipse Application**
3. The plugin will launch in a new Eclipse instance

## Project Structure

- `src/` - Source code for the plugin
- `icons/` - Plugin icon resources
- `META-INF/` - Plugin metadata and manifest
- `plugin.xml` - Plugin configuration file
- `.classpath` - Eclipse classpath configuration
- `.project` - Eclipse project configuration

## Development

This is an Eclipse plugin development project. After importing, you can modify the plugin code and test changes by running a new Eclipse Application instance.
