package ac.jester.anticheat.platform.api.sender;

import ac.jester.anticheat.platform.api.player.PlatformPlayer;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface Sender {
    UUID CONSOLE_UUID = new UUID(0, 0);

    String CONSOLE_NAME = "Console";

    String getName();

    UUID getUniqueId();

    void sendMessage(String message);

    void sendMessage(Component message);

    boolean hasPermission(String permission);

    boolean hasPermission(String permission, boolean defaultIfUnset);

    void performCommand(String commandLine);

    boolean isConsole();

    boolean isPlayer();

    default boolean isValid() {
        return true;
    }

    @NotNull Object getNativeSender();

    @Nullable PlatformPlayer getPlatformPlayer();
}
