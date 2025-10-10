# Cline Prompt Flow to AI Models (e.g., Claude via Anthropic)

## Complete Flow Documentation

When you invoke Cline and it sends a prompt to an AI model like Claude, here's the complete flow of what gets sent:

## 1. **System Prompt** (via `attemptApiRequest`)
**Location:** `src/core/task/index.ts` → `attemptApiRequest()` method

```typescript
const systemPrompt = await getSystemPrompt(promptContext)
```

**What goes into it:**
- Base system instructions (role, capabilities, rules)
- Tool definitions (read_file, write_to_file, etc.)
- Browser tool settings
- MCP tool definitions
- Focus chain settings
- Cline rules (global and local .clinerules files)
- Cursor rules (.cursorrules files)
- Windsurf rules (.windsurfrules files)
- .clineignore instructions
- Preferred language instructions
- Workspace roots (multi-root workspaces)
- Current mode instructions (PLAN MODE or ACT MODE)

**Built from:** `src/core/prompts/system-prompt/index.ts`

---

## 2. **User Content** (Conversation Messages)
**Location:** `src/core/task/index.ts` → `recursivelyMakeClineRequests()` method

### 2a. Initial User Message
The user's task/question with any images or files attached.

### 2b. Parsed & Processed Content (via `loadContext`)
```typescript
const [parsedUserContent, environmentDetails, clinerulesError] = await this.loadContext(
    userContent,
    includeFileDetails
)
```

**Processing includes:**
- **Mention parsing**: Converts `@file` mentions to actual file contents
- **Slash command parsing**: Processes `/newrule`, `/workflow`, etc.
- **File content injection**: Adds content from mentioned files
- **Focus chain instructions**: If focus chain is enabled and should be included

---

## 3. **Environment Details** (CRITICAL - This is likely what you're seeing!)
**Location:** `src/core/task/index.ts` → `getEnvironmentDetails()` method

**This is added AFTER user content:**
```typescript
userContent.push({ type: "text", text: environmentDetails })
```

### What's in Environment Details:

#### a. **Workspace Roots** (Multi-root workspaces)
```
# Workspace Roots
- Project1: /path/to/project1 (git)
- Project2: /path/to/project2
Primary workspace: Project1
```

#### b. **Visible Files**
```
# macOS Visible Files
src/file1.ts
src/file2.ts
```

#### c. **Open Tabs**
```
# macOS Open Tabs
README.md
src/index.ts
```

#### d. **Terminal Output** (Active & Inactive)
```
# Actively Running Terminals
## Original command: `npm run dev`
### New Output
Server started on port 3000...

# Inactive Terminals
## npm install
### New Output
added 245 packages
```

#### e. **Recently Modified Files**
```
# Recently Modified Files
These files have been modified since you last accessed them:
src/config.ts
```

#### f. **Current Time**
```
# Current Time
10/8/2025, 10:15:27 PM (America/New_York, UTC-4:00)
```

#### g. **File Details** (if `includeFileDetails=true`)
```
# Current Working Directory (/path/to/project) Files
src/
  index.ts
  config.ts
  ...
```

#### h. **Workspace Configuration** (JSON)
```json
# Workspace Configuration
{
  "workspaces": {
    "/path/to/project": {
      "hint": "project-name",
      ...
    }
  }
}
```

#### i. **Detected CLI Tools**
```
# Detected CLI Tools
These are some of the tools on the user's machine: git, curl, npm, node...
```

#### j. **Context Window Usage**
```
# Context Window Usage
23,702 / 200K tokens used (12%)
```

#### k. **Current Mode**
```
# Current Mode
ACT MODE
```
or
```
# Current Mode
PLAN MODE
[Plan mode specific instructions]
```

---

## 4. **Conversation History** (via Context Manager)
**Location:** `src/core/context/context-management/ContextManager.ts`

Before sending to the API, the conversation history is processed:

```typescript
const contextManagementMetadata = await this.contextManager.getNewContextMessagesAndMetadata(
    this.messageStateHandler.getApiConversationHistory(),
    this.messageStateHandler.getClineMessages(),
    this.api,
    this.taskState.conversationHistoryDeletedRange,
    previousApiReqIndex,
    await ensureTaskDirectoryExists(this.taskId),
    this.stateManager.getGlobalSettingsKey("useAutoCondense"),
)
```

**What it does:**
- **Truncates old messages** to fit context window
- **Removes duplicate file reads** (file content deduplication)
- **Adds truncation notices** to first assistant message
- **Manages context window limits** automatically

The truncated history is then used:
```typescript
stream = this.api.createMessage(systemPrompt, contextManagementMetadata.truncatedConversationHistory)
```

---

## 5. **Final API Call**
**Location:** `src/core/api/providers/anthropic.ts` (or other providers)

The actual API request structure:
```typescript
{
  system: systemPrompt,           // From step 1
  messages: [                      // From steps 2-4
    {
      role: "user",
      content: [
        { type: "text", text: "..." },          // User's task
        { type: "text", text: "..." },          // Parsed content
        { type: "text", text: environmentDetails }, // Environment details!
      ]
    },
    {
      role: "assistant",
      content: [...]
    },
    // ... conversation history with truncation/deduplication applied
  ],
  // ... other parameters
}
```

---

## Key Insight: The "Hidden" Context

The **Environment Details** section is likely what you're seeing that's not in the system prompt! This is dynamically generated for each request and includes:

1. Current state of visible files and tabs
2. Terminal output
3. Recently modified files
4. Current time
5. Context window usage
6. File directory structure (on first request or when `includeFileDetails=true`)

This context is added **to the user message**, not the system prompt, which is why it might not be obvious where it's coming from.

---

## When Environment Details is Included

- **Every API request** gets basic environment details (visible files, tabs, terminals, time, etc.)
- **First request** in a task gets `includeFileDetails=true`, which adds the full directory structure
- **Subsequent requests** use `includeFileDetails=false` by default

---

## Flow Summary

```
User Input
    ↓
Parse mentions & slash commands (loadContext)
    ↓
Add environment details (getEnvironmentDetails)
    ↓
Build system prompt (getSystemPrompt)
    ↓
Get & truncate conversation history (ContextManager)
    ↓
Combine all into API request structure
    ↓
Send to AI model via provider (anthropic.ts, etc.)
```

## Finding Specific Context Injection Points

To see exactly what's being sent:

1. **System Prompt**: `src/core/prompts/system-prompt/` directory
2. **Environment Details**: `src/core/task/index.ts` → `getEnvironmentDetails()` method (line ~1700+)
3. **Context Processing**: `src/core/task/index.ts` → `loadContext()` method (line ~1600+)
4. **Conversation History**: `src/core/context/context-management/ContextManager.ts`
5. **Final API Call**: `src/core/api/providers/anthropic.ts` → `createMessage()` method

The `getEnvironmentDetails()` method is the most comprehensive source of "invisible" context that gets added to every request!
