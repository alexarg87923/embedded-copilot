package embeddedcopilot.model;

import java.util.ArrayList;
import java.util.List;

public class ChatHistory {
    private String title;
    private List<ChatMessage> messages = new ArrayList<>();
    
    public ChatHistory(String title) {
        this.title = title;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public List<ChatMessage> getMessages() {
        return messages;
    }
    
    public void addMessage(ChatMessage message) {
        this.messages.add(message);
    }
}