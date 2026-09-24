package ac.jester.anticheat.manager.violationdatabase;

public interface DatabaseDialect {
    String getUuidColumnType();

    String getAutoIncrementPrimaryKeySyntax();

    String getInsertOrIgnoreSyntax(String tableName, String columnNames);

    String getUniqueConstraintViolationSQLState();
    int getUniqueConstraintViolationErrorCode();
}
