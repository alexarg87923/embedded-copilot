# Message Filtering Implementation

## Summary

Implemented comprehensive message filtering in the Eclipse Copilot plugin to control which Cline messages are displayed in the UI and which are hidden.

## Changes Made

### 1. **ChatUIManager.java** - Core Filtering Logic

Added new filtering system with these components:

#### **DisplayAction Enum**
- `SHOW` - Display as regular message
- `SHOW_ASK` - Display with approve/deny buttons
- `HIDE` - Don't display at all
- `DEBUG_ONLY` - Only show when debug mode is enabled

#### **FilteredMessage Class**
Holds filtering results with display text and original JSON

#### **Key Methods**

**`filterClineMessage(String jsonLine)`**
- Main entry point for filtering
- Parses JSON and routes to appropriate handler

**`filterSayMessage(JsonObject json)`**
- Handles all "say" type messages
- **SHOW**: `text`, `error`, `completion_result`, `task_progress`, `command`
- **HIDE**: `user_feedback`, `user_feedback_diff`, `api_req_started`, `api_req_finished`, `tool`, `browser_action`, `checkpoint_created`, `reasoning`, `command_output`, etc.
- **DEBUG_ONLY**: `api_req_failed`, `api_req_canceled`, unknown types

**`filterAskMessage(JsonObject json)`**
- Handles all "ask" type messages
- **SHOW_ASK** (with buttons): `tool`, `command`, `completion_result`, `api_req_failed`, `resume_task`, `resume_completed_task`
- **SHOW** (no buttons): `command_output` (streaming output visible to user)
- **HIDE**: `request_limit_reached`, `followup`

**`processClineMessage(Composite chatComposite, String jsonLine, onApprove, onDeny)`**
- Main method to call from your code
- Filters and displays messages based on filtering rules
- Automatically handles approve/deny callbacks

**`formatAskMessageForDisplay(JsonObject json)`**
- Formats ask messages in user-friendly way with emojis
- Shows tool name, path, command details
- Structured display for different ask types

#### **Debug Mode**
Toggle with `chatUIManager.setDebugMode(true)` to see internal messages

### 2. **TaskPollingService.java** - Simplified Message Flow

**Changed**: Now sends ALL messages (including ASK_REQUIRES_APPROVAL) through the main `onMessage` callback

**Before**:
```java
if (msg.type == Message.Type.ASK_REQUIRES_APPROVAL) {
    onAskRequiresApproval.accept(msg.text);
} else {
    onMessage.accept(msg);
}
```

**After**:
```java
// Send all messages to main callback
// ChatUIManager filtering handles display logic
if (onMessage != null) {
    onMessage.accept(msg);
}
```

This consolidates all message handling in one place.

### 3. **SampleView.java** - Updated Callback

**Changed**: Updated `startPolling()` to use new filtering logic

**Before**:
```java
(msg) -> display.asyncExec(() -> {
    if (msg.type == Message.Type.USER) {
        chatUIManager.addMessage(chatComposite, msg.text, true);
    } else if (msg.type == Message.Type.AI) {
        chatUIManager.addMessage(chatComposite, MessageProcessor.formatMessage(msg), false);
    }
})
```

**After**:
```java
(msg) -> display.asyncExec(() -> {
    // Use the new filtering logic for all messages
    if (msg.rawJson != null) {
        String jsonLine = msg.rawJson.toString();
        chatUIManager.processClineMessage(
            chatComposite, 
            jsonLine,
            (askContainer) -> handleApprove(chatComposite, askContainer),
            (askContainer) -> handleDeny(chatComposite, askContainer)
        );
    }
})
```

## Message Types Reference

### Say Messages (from AI)

| Type | Action | Display |
|------|--------|---------|
| `text` | SHOW | Regular text message |
| `user_feedback` | SHOW | 💬 + message |
| `user_feedback_diff` | SHOW | 💬 + message |
| `error` | SHOW | ❌ Error: + message |
| `completion_result` | SHOW | ✅ + message |
| `command` | SHOW | ⚡ Running: + command |
| `api_req_started` | HIDE | Internal state |
| `api_req_finished` | HIDE | Internal state |
| `api_req_retried` | HIDE | Internal state |
| `command_output` | HIDE | Internal state |
| `tool` | HIDE | Internal state |
| `browser_action` | HIDE | Internal state |
| `browser_action_launch` | HIDE | Internal state |
| `shell_integration_warning` | HIDE | Internal state |
| `inspect_site_result` | HIDE | Internal state |
| `mcp_server_request_started` | HIDE | Internal state |
| `checkpoint_created` | HIDE | Internal checkpoint tracking |
| `reasoning` | HIDE (special) | Tracked for duration display |
| `task_progress` | SHOW | 📋 Task Progress (planning) |
| `api_req_failed` | DEBUG_ONLY | ⚠️ API Request Issue |
| `api_req_canceled` | DEBUG_ONLY | ⚠️ API Request Issue |
| (unknown) | DEBUG_ONLY | For safety |

### Ask Messages (requiring approval)

| Type | Action | Display |
|------|--------|---------|
| `tool` | SHOW_ASK | 🔧 Tool Request + details |
| `command` | SHOW_ASK | ⚡ Command Execution + command |
| `completion_result` | SHOW_ASK | ✅ Done (simple acknowledgment) |
| `api_req_failed` | SHOW_ASK | ⚠️ API Request Failed |
| `resume_task` | SHOW_ASK | 🔄 Resume Task |
| `resume_completed_task` | SHOW_ASK | 🔄 Resume Completed Task |
| `command_output` | SHOW | Streaming output (no buttons) |
| `request_limit_reached` | HIDE | Handled internally |
| `followup` | HIDE | Handled internally |
| (unknown) | SHOW_ASK | For safety (better to ask) |

## How It Works

### Message Flow

1. **TaskPollingService** polls `clineService.getTaskViewJson()`
2. **MessageProcessor** parses JSON and creates `Message` objects with `rawJson`
3. **SampleView** receives messages via `onMessage` callback
4. **ChatUIManager.processClineMessage()** is called with raw JSON
5. **Filtering logic** determines display action:
   - `SHOW` → `addMessage()` with formatted text
   - `SHOW_ASK` → `addAskMessage()` with approve/deny buttons
   - `HIDE` → Nothing displayed
   - `DEBUG_ONLY` → Only if `debugMode` is enabled
6. **User interaction** (approve/deny) triggers callbacks that call `handleApprove()` or `handleDeny()`

### Example JSON Processing

**Input JSON**:
```json
{
  "type": "ask",
  "ask": "tool",
  "tool": "write_to_file",
  "path": "/path/to/file.java",
  "text": "Create new file"
}
```

**Processing**:
1. Parsed by `filterClineMessage()`
2. Routed to `filterAskMessage()` because `type="ask"`
3. Matched `ask="tool"` case
4. Formatted by `formatAskMessageForDisplay()`:
   ```
   🔧 Tool Request

   Tool: Write To File
   Path: /path/to/file.java

   Create new file
   ```
5. Returns `DisplayAction.SHOW_ASK`
6. Displayed with Approve/Deny buttons

## Important Distinctions

### `command` vs `command_output`

- **`command`** (ask type): Approval request BEFORE executing a command → Shows approve/deny buttons
- **`command_output`** (ask type): Streaming output WHILE command is running → Shows as regular message (no buttons)

Example flow:
1. AI wants to run `cargo install rust` → `ask: "command"` → User sees approve/deny buttons
2. User approves → Command starts executing
3. Output streams in: "Downloading...", "Installing..." → `ask: "command_output"` → Displayed as regular messages
   ```
   info: downloading installer
   info: downloading component 'cargo'
   info: installing component 'cargo'
   ```
4. Command completes → Final result shown

This prevents the UI from being saturated with approve/deny buttons for every line of output while still keeping the user informed of progress.

### User Message Echo Prevention

When a user sends a message, the flow is:
1. User types and sends message → Displayed immediately in UI as "You"
2. Message sent to Cline
3. Cline echoes the message back as `{"say": "text", "text": "user's message"}`

To prevent showing the user's message twice, `SampleView` checks if a message is of type `Message.Type.USER` (detected by `MessageProcessor`) and skips displaying it, since it was already shown when the user sent it.

```java
// Skip USER type messages - they're already displayed when the user sent them
if (msg.type == Message.Type.USER) {
    return;
}
```

### Reasoning Duration Display

When Cline is thinking (internal reasoning), we track the duration and show it to the user:

**Flow:**
1. `reasoning` message arrives with timestamp → Stored in `lastReasoningTimestamp`
2. Next visible message arrives → Calculate duration: `currentTimestamp - lastReasoningTimestamp`
3. Display: `"🤔 Thought for 3s"` (rounded to nearest second)
4. Display the actual message
5. Reset `lastReasoningTimestamp`

**Example:**
```
🤔 Thought for 3s
I'll create a merge sort algorithm in Rust for you. Let me first check the existing file.
```

This gives users visibility into Cline's thinking process without flooding them with the actual reasoning text, which is typically very technical and verbose.

## Benefits

1. **Clean UI**: Only relevant messages shown to users
2. **Hidden Internal State**: API requests, tool execution details hidden
3. **No Button Flooding**: Command output shown without approve/deny buttons
4. **User Visibility**: Users can see command progress in real-time
5. **Task Progress Tracking**: Users see Cline's planning with task progress updates
6. **Reasoning Duration**: Shows "Thought for Xs" to indicate thinking time without verbose text
7. **No Message Duplication**: User echoes are filtered to prevent showing messages twice
8. **User-Friendly Formatting**: Emojis and structured display for ask messages
9. **Centralized Logic**: All filtering in one place (`ChatUIManager`)
10. **Extensible**: Easy to add new message types or change display rules
11. **Debug Mode**: Developers can enable debug mode to see all messages

## Testing

To test the implementation:

1. **Build the plugin in Eclipse**:
   - Project → Clean...
   - Build Automatically should be enabled

2. **Run the plugin**:
   - Right-click project → Run As → Eclipse Application

3. **Test scenarios**:
   - Create a new chat and send a message
   - Verify only user-facing messages are displayed
   - Check that tool requests show approve/deny buttons
   - Confirm internal messages (api_req_started, etc.) are hidden

4. **Enable debug mode** (in code):
   ```java
   chatUIManager.setDebugMode(true);
   ```
   Then verify that DEBUG_ONLY messages appear

## Future Enhancements

1. **User Preference**: Add UI setting to toggle debug mode
2. **Message History**: Store filtered messages for later review
3. **Custom Filters**: Allow users to configure which messages to show/hide
4. **Notification System**: Show toast notifications for important hidden messages
5. **Message Search**: Search through all messages including hidden ones

