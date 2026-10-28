package co.xenastudios.mostwanted.zone;

import static co.xenastudios.mostwanted.common.db.Tables.ZONES;

import co.xenastudios.mostwanted.common.db.tables.records.ZonesRecord;
import co.xenastudios.mostwanted.storage.Storage;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Every zone, in memory, loaded once at startup. Changes apply straight away in memory
 * and are saved in the background. Main thread only, apart from {@link #index}.
 */
public final class ZoneStore {

    private final Storage storage;
    private final Map<String, Zone> zones = new TreeMap<>();
    private volatile ZoneIndex index = ZoneIndex.EMPTY;
    private Runnable onChange = () -> {};

    public ZoneStore(Storage storage) {
        this.storage = storage;
    }

    /** Reads every zone. Blocks: for startup only. */
    public void load() {
        zones.clear();
        for (ZonesRecord row : storage.sql().selectFrom(ZONES).fetch()) {
            Zone zone = fromRow(row);
            zones.put(zone.name(), zone);
        }
        rebuild();
    }

    /** Runs after any zone changes, on the main thread. */
    public void onChange(Runnable onChange) {
        this.onChange = onChange;
    }

    /** Safe from any thread. */
    public ZoneIndex index() {
        return index;
    }

    public Optional<Zone> get(String name) {
        return Optional.ofNullable(zones.get(name));
    }

    /** Sorted by name. */
    public Collection<Zone> all() {
        return List.copyOf(zones.values());
    }

    /** Adds a zone, or replaces the one with the same name. */
    public CompletableFuture<Void> put(Zone zone, UUID by) {
        zones.put(zone.name(), zone);
        rebuild();
        onChange.run();
        return storage.write("zone " + zone.name(), sql -> {
            ZonesRecord row = toRow(zone);
            row.setCreatedBy(by);
            sql.insertInto(ZONES).set(row)
                    .onConflict(ZONES.NAME).doUpdate().set(row)
                    .execute();
        });
    }

    /** False if there was no such zone. */
    public boolean delete(String name) {
        if (zones.remove(name) == null) {
            return false;
        }
        rebuild();
        onChange.run();
        storage.write("zone " + name, sql -> sql.deleteFrom(ZONES).where(ZONES.NAME.eq(name)).execute());
        return true;
    }

    private void rebuild() {
        index = new ZoneIndex(zones.values());
    }

    static Zone fromRow(ZonesRecord row) {
        Zone.Spawn spawn = row.getSpawnX() == null ? null : new Zone.Spawn(row.getSpawnX(), row.getSpawnY(),
                row.getSpawnZ(), row.getSpawnYaw(), row.getSpawnPitch());
        return new Zone(row.getName(), ZoneKind.fromKey(row.getKind()), row.getWorld(), row.getMinX(),
                row.getMinY(), row.getMinZ(), row.getMaxX(), row.getMaxY(), row.getMaxZ(), spawn);
    }

    static ZonesRecord toRow(Zone zone) {
        ZonesRecord row = new ZonesRecord();
        row.setName(zone.name());
        row.setKind(zone.kind().key());
        row.setWorld(zone.world());
        row.setMinX(zone.minX());
        row.setMinY(zone.minY());
        row.setMinZ(zone.minZ());
        row.setMaxX(zone.maxX());
        row.setMaxY(zone.maxY());
        row.setMaxZ(zone.maxZ());
        Zone.Spawn spawn = zone.spawn();
        row.setSpawnX(spawn == null ? null : spawn.x());
        row.setSpawnY(spawn == null ? null : spawn.y());
        row.setSpawnZ(spawn == null ? null : spawn.z());
        row.setSpawnYaw(spawn == null ? null : spawn.yaw());
        row.setSpawnPitch(spawn == null ? null : spawn.pitch());
        return row;
    }
}
