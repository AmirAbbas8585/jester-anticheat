package ac.jester.anticheat.platform.api.manager;

import ac.jester.anticheat.platform.api.permissions.PermissionDefaultValue;

public interface PermissionRegistrationManager {
    void registerPermission(String name, PermissionDefaultValue defaultValue);
}
