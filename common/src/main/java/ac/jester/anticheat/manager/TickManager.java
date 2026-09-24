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
    public int currentTick;
    private final ClassToInstanceMap<Tickable> syncTick;
    private final ClassToInstanceMap<Tickable> asyncTick;

    public TickManager() {
        syncTick = new ImmutableClassToInstanceMap.Builder<Tickable>()
                .put(ResetTick.class, new ResetTick())
                .build();

        asyncTick = new ImmutableClassToInstanceMap.Builder<Tickable>()
                .put(ClientVersionSetter.class, new ClientVersionSetter())
                .put(TickInventory.class, new TickInventory())
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
