package pronze.hypixelify.api.events;

import lombok.Getter;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import pronze.hypixelify.api.party.IParty;
import pronze.hypixelify.api.wrapper.PlayerWrapper;

@Getter
public class SBAPlayerPartyCreatedEvent extends Event implements Cancellable {
    private static final HandlerList handlerList = new HandlerList();

    private final PlayerWrapper player;
    private final IParty party;
    private boolean cancelled;

    public SBAPlayerPartyCreatedEvent(PlayerWrapper player,
                                      IParty party) {
        super(false);
        this.player = player;
        this.party = party;
    }
    @Override
    public @NotNull HandlerList getHandlers() {
        return handlerList;
    }

    public static HandlerList getHandlerList() {
        return SBAPlayerPartyCreatedEvent.handlerList;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        cancelled = cancel;
    }
}
