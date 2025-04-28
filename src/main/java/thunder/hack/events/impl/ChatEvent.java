package thunder.hack.events.impl;

import thunder.hack.events.Event;

/**
 * Event that is triggered when chat messages are sent or received.
 * Can be used to modify or intercept chat messages.
 */
public class ChatEvent extends Event {
    private String message;
    private boolean cancelled;

    /**
     * Constructor for the chat event
     *
     * @param message The chat message
     */
    public ChatEvent(String message) {
        this.message = message;
        this.cancelled = false;
    }

    /**
     * Gets the message associated with this event
     *
     * @return The chat message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the message for this event
     *
     * @param message The new message
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Checks if the event is cancelled
     *
     * @return True if cancelled, false otherwise
     */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Sets whether the event is cancelled
     *
     * @param cancelled True to cancel the event
     */
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
