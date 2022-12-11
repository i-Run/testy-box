package fr.ght1pc9kc.testy.jooq.model;

/**
 * Trace level for H2 database.
 */
public enum DatabaseTraceLevel {

    /**
     * No trace.
     */
    OFF(0),

    /**
     * Trace errors.
     */
    ERROR(1),

    /**
     * Trace infos.
     */
    INFO(2),

    /**
     * Trace debug.
     */
    DEBUG(3);

    public final int levelValue;

    DatabaseTraceLevel(int levelValue) {
        this.levelValue = levelValue;
    }
}
