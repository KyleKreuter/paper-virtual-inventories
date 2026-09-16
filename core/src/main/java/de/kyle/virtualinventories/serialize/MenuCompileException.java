package de.kyle.virtualinventories.serialize;

/**
 * Thrown when a menu definition cannot be parsed, compiled, encoded or
 * decoded. Messages always carry the menu id and the exact location
 * (slot, field) so broken menus fail fast with an actionable error.
 */
public class MenuCompileException extends RuntimeException {

    public MenuCompileException(String message) {
        super(message);
    }

    public MenuCompileException(String message, Throwable cause) {
        super(message, cause);
    }

    public static MenuCompileException at(String menuId, String where, String message) {
        return new MenuCompileException("[" + menuId + "] " + where + ": " + message);
    }
}
