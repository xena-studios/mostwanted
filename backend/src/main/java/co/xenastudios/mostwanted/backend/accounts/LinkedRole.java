package co.xenastudios.mostwanted.backend.accounts;

/** Keeps the Linked role on Discord in step with accounts. The bot does it; a no-op without the bot. */
@FunctionalInterface
public interface LinkedRole {

    LinkedRole NONE = (discordId, linked) -> { };

    void update(String discordId, boolean linked);
}
