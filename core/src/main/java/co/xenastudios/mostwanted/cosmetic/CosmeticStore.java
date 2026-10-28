package co.xenastudios.mostwanted.cosmetic;

import static co.xenastudios.mostwanted.common.db.Tables.COSMETIC_CHOICES;

import co.xenastudios.mostwanted.storage.Storage;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Each online player's cosmetic choices, read as they join and saved as they change. */
public final class CosmeticStore {

    private final Storage storage;
    private final Map<UUID, Map<String, String>> choices = new ConcurrentHashMap<>();

    public CosmeticStore(Storage storage) {
        this.storage = storage;
    }

    /** Reads a player's choices. Blocks: call it off the main thread. */
    public void load(UUID uuid) {
        Map<String, String> loaded = new HashMap<>();
        storage.sql().select(COSMETIC_CHOICES.SLOT, COSMETIC_CHOICES.CHOICE)
                .from(COSMETIC_CHOICES)
                .where(COSMETIC_CHOICES.UUID.eq(uuid))
                .forEach(r -> loaded.put(r.value1(), r.value2()));
        choices.put(uuid, new ConcurrentHashMap<>(loaded));
    }

    public void forget(UUID uuid) {
        choices.remove(uuid);
    }

    /** What's in the slot, or null. */
    public String get(UUID uuid, String slot) {
        Map<String, String> mine = choices.get(uuid);
        return mine == null ? null : mine.get(slot);
    }

    /** Sets a slot; null clears it. Saved in the background. */
    public void set(UUID uuid, String slot, String choice) {
        Map<String, String> mine = choices.computeIfAbsent(uuid, key -> new ConcurrentHashMap<>());
        if (choice == null) {
            mine.remove(slot);
            storage.write("a cosmetic choice", sql -> sql.deleteFrom(COSMETIC_CHOICES)
                    .where(COSMETIC_CHOICES.UUID.eq(uuid)).and(COSMETIC_CHOICES.SLOT.eq(slot)).execute());
        } else {
            mine.put(slot, choice);
            storage.write("a cosmetic choice", sql -> sql.insertInto(COSMETIC_CHOICES)
                    .set(COSMETIC_CHOICES.UUID, uuid)
                    .set(COSMETIC_CHOICES.SLOT, slot)
                    .set(COSMETIC_CHOICES.CHOICE, choice)
                    .onConflict(COSMETIC_CHOICES.UUID, COSMETIC_CHOICES.SLOT)
                    .doUpdate().set(COSMETIC_CHOICES.CHOICE, choice)
                    .execute());
        }
    }
}
