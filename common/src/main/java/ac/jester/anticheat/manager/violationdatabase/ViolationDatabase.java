package ac.jester.anticheat.manager.violationdatabase;

import ac.jester.anticheat.player.GrimPlayer;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public interface ViolationDatabase {

    void connect() throws SQLException;

    void logAlert(GrimPlayer player, String SkyVersion, String verbose, String checkName, int vls);

    int getLogCount(UUID player);

    List<Violation> getViolations(UUID player, int page, int limit);

    void disconnect();

}
