package co.xenastudios.mostwanted.backend.support;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

/**
 * Who's staff on the website: their linked Minecraft player's rank in game, read from
 * LuckPerms' own table (it shares our database), like the supporter badge. Nothing
 * to set up twice: giving someone a rank in game gives them the panel.
 */
public final class Staff {

    /** Lowest first. Helpers and up handle tickets; moderators and up decide applications. */
    public enum Level { HELPER, MODERATOR, ADMIN, OWNER;

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public boolean decides() {
            return compareTo(MODERATOR) >= 0;
        }
    }

    private final DSLContext sql;
    private final Clock clock;

    public Staff(DSLContext sql, Clock clock) {
        this.sql = sql;
        this.clock = clock;
    }

    /** Their highest staff rank, or null: not staff, no Minecraft account, or no LuckPerms. */
    public Level level(UUID minecraft) {
        if (minecraft == null) {
            return null;
        }
        Field<String> permission = DSL.field("permission", String.class);
        List<String> groups;
        try {
            groups = sql.select(permission)
                    .from(DSL.table("luckperms_user_permissions"))
                    .where(DSL.field("uuid", String.class).eq(minecraft.toString()))
                    .and(DSL.field("value", Boolean.class).isTrue())
                    .and(permission.in("group.helper", "group.moderator", "group.admin", "group.owner"))
                    .and(DSL.field("server", String.class).in("global", "mostwanted"))
                    .and(DSL.field("expiry", Long.class).eq(0L)
                            .or(DSL.field("expiry", Long.class).gt(clock.instant().getEpochSecond())))
                    .fetch(permission);
        } catch (DataAccessException e) {
            return null;
        }
        Level highest = null;
        for (Level level : Level.values()) {
            if (groups.contains("group." + level.key())) {
                highest = level;
            }
        }
        return highest;
    }
}
