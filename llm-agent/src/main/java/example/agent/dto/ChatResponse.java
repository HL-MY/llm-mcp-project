package example.agent.dto;

/**
 * @author hull
 * @since 2025/9/25 10:27
 */
public class ChatResponse {
    private final String reply;
    private final UiState uiState;

    public ChatResponse(String reply, UiState uiState) {
        this.reply = reply;
        this.uiState = uiState;
    }

    public String getReply() {
        return reply;
    }

    public UiState getUiState() {
        return uiState;
    }
}
