/*
 * This file is part of packetevents - https://github.com/retrooper/packetevents
 * Copyright (C) 2024 retrooper and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ac.jester.anticheat.platform.api.scheduler;

import ac.grim.grimac.api.plugin.GrimPlugin;
import ac.jester.anticheat.platform.api.world.PlatformWorld;
import ac.jester.anticheat.utils.math.Location;
import org.jetbrains.annotations.NotNull;

public interface RegionScheduler {
    void execute(@NotNull GrimPlugin plugin, @NotNull PlatformWorld world, int chunkX, int chunkZ, @NotNull Runnable task);

    void execute(@NotNull GrimPlugin plugin, @NotNull Location location, @NotNull Runnable task);

    TaskHandle run(@NotNull GrimPlugin plugin, @NotNull PlatformWorld world, int chunkX, int chunkZ, @NotNull Runnable task);

    TaskHandle run(@NotNull GrimPlugin plugin, @NotNull Location location, @NotNull Runnable task);

    TaskHandle runDelayed(@NotNull GrimPlugin plugin, @NotNull PlatformWorld world, int chunkX, int chunkZ, @NotNull Runnable task, long delayTicks);

    TaskHandle runDelayed(@NotNull GrimPlugin plugin, @NotNull Location location, @NotNull Runnable task, long delayTicks);

    TaskHandle runAtFixedRate(@NotNull GrimPlugin plugin, @NotNull PlatformWorld world, int chunkX, int chunkZ, @NotNull Runnable task, long initialDelayTicks, long periodTicks);

    TaskHandle runAtFixedRate(@NotNull GrimPlugin plugin, @NotNull Location location, @NotNull Runnable task, long initialDelayTicks, long periodTicks);
}
