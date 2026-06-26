package ac.jester.anticheat.platform.api;

public interface PlatformPlugin {
    boolean isEnabled();

    String getName();

    String getVersion();
}
