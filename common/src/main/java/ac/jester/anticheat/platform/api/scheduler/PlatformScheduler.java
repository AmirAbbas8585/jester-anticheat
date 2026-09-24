package ac.jester.anticheat.platform.api.scheduler;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public interface PlatformScheduler {
    static long convertTimeToTicks(long time, TimeUnit timeUnit) {
        return timeUnit.toMillis(time) / 50;
    }

    static long convertTicksToTime(long ticks, TimeUnit timeUnit) {
        long millis = ticks * 50L;
        return timeUnit.convert(millis, TimeUnit.MILLISECONDS);
    }

    @NotNull AsyncScheduler getAsyncScheduler();

    @NotNull GlobalRegionScheduler getGlobalRegionScheduler();

    @NotNull EntityScheduler getEntityScheduler();

    @NotNull RegionScheduler getRegionScheduler();
}
