package ac.jester.anticheat.platform.bukkit.manager;

import ac.jester.anticheat.platform.api.manager.PermissionRegistrationManager;
import ac.jester.anticheat.platform.api.permissions.PermissionDefaultValue;
import ac.jester.anticheat.platform.bukkit.utils.convert.BukkitConversionUtils;
import org.bukkit.Bukkit;
import org.bukkit.permissions.Permission;

public class BukkitPermissionRegistrationManager implements PermissionRegistrationManager {
    @Override
    public void registerPermission(String name, PermissionDefaultValue defaultValue) {
        final Permission bukkitPermission = Bukkit.getPluginManager().getPermission(name);
        if (bukkitPermission == null) {
            Bukkit.getPluginManager().addPermission(new Permission(name, BukkitConversionUtils.toBukkitPermissionDefault(defaultValue)));
        } else {
            bukkitPermission.setDefault(BukkitConversionUtils.toBukkitPermissionDefault(defaultValue));
        }
    }
}
