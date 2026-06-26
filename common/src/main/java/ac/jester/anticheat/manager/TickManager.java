package ac.jester.anticheat.manager;

import ac.jester.anticheat.manager.tick.Tickable;
import ac.jester.anticheat.manager.tick.impl.ClearRecentlyUpdatedBlocks;
import ac.jester.anticheat.manager.tick.impl.ClientVersionSetter;
import ac.jester.anticheat.manager.tick.impl.ResetTick;
import ac.jester.anticheat.manager.tick.impl.TickInventory;
import ac.jester.anticheat.manager.tick.impl.ViolationDecayTick;
import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.ImmutableClassToInstanceMap;

public class TickManager {
    // Overflows after 4 years of uptime
    public int currentTick;
    private final ClassToInstanceMap<Tickable> syncTick;
    private final ClassToInstanceMap<Tickable> asyncTick;

    public TickManager() {
        syncTick = new ImmutableClassToInstanceMap.Builder<Tickable>()
                .put(ResetTick.class, new ResetTick())
                .build();

        asyncTick = new ImmutableClassToInstanceMap.Builder<Tickable>()
                .put(ClientVersionSetter.class, new ClientVersionSetter()) // Async because permission lookups might take a while, depending on the plugin
                .put(TickInventory.class, new TickInventory()) // Async because I've never gotten an exception from this.  It's probably safe.
                .put(ClearRecentlyUpdatedBlocks.class, new ClearRecentlyUpdatedBlocks())
                .put(ViolationDecayTick.class, new ViolationDecayTick())
                .build();
    }

    public void tickSync() {
        currentTick++;
        syncTick.values().forEach(Tickable::tick);
    }

    public void tickAsync() {
        asyncTick.values().forEach(Tickable::tick);
    }
}
