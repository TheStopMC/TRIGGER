/*
 *     This file is part of cat.TRIGGER by @catkillsreality.
 *
 *     cat.TRIGGER is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 *     cat.TRIGGER is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License along with cat.TRIGGER. If not, see <https://www.gnu.org/licenses/>.
 */

package cat.TRIGGER;

import cat.TRIGGER.config.TriggerDefinition;
import cat.TRIGGER.config.VecTypeSerializer;
import cat.TRIGGER.dynamic.DynamicConsumerWrapper;
import com.jamieswhiteshirt.rtree3i.Box;
import com.jamieswhiteshirt.rtree3i.ConfigurationBuilder;
import com.jamieswhiteshirt.rtree3i.RTreeMap;
import com.jamieswhiteshirt.rtree3i.Selection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntitySpawnEvent;
import net.minestom.server.event.entity.EntityTeleportEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.timer.TaskSchedule;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.objectmapping.DefaultObjectMapperFactory;
import ninja.leaping.configurate.objectmapping.ObjectMapper;
import ninja.leaping.configurate.objectmapping.ObjectMapperFactory;
import ninja.leaping.configurate.objectmapping.serialize.TypeSerializerCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;


/**
 * A utility class for managing a collection of triggers.
 * Standalone use of the {@link Trigger} class is not recommended since it does not contain any event hooks.
 */
public class TriggerManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TriggerManager.class);
    private static final TypeSerializerCollection serializers = TypeSerializerCollection.create().register(new VecTypeSerializer());
    private RTreeMap<Box, Trigger> triggers = RTreeMap.create(new ConfigurationBuilder().star().build());

    private int totalTriangles = 0;
    private final boolean debug;

    /**
     * The default constructor
     * @param debug Debug mode, enables rendering. <p> DEBUG RENDERING CAN CAUSE BIG LAG.
     */
    public TriggerManager(boolean debug) {
        this.debug = debug;

        if (debug) {
            MinecraftServer.getSchedulerManager().buildTask(() -> MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(player -> triggers.values()
                            .forEach(trigger -> trigger.render(player)))).repeat(TaskSchedule.nextTick()).schedule();
        }
    }

    public Trigger create(List<Vec> anchors, Vec position, UUID uuid, Component name, TextColor color, DynamicConsumerWrapper triggeredCallback) {
        if (!Trigger.validatePoints(anchors)) LOGGER.warn("Detected very close points for {}, collision and/or rendering may break due to numerical instability, use at your own risk", PlainTextComponentSerializer.plainText().serialize(name));

        /*---------------< EXTRUDE 2D INTO 3D >---------------*/
        // since 2D shapes projected onto 3D directly would be infinitely thin, extruding them slightly allows for good collision detection.
        if (Trigger.arePointsCoplanar(anchors)) {


            double thickness = 0.1;
            List<Vec> extrudedPoints = new ArrayList<>();
            Vec origin = anchors.get(0);

            // Calculate normal of the plane
            Vec edge1 = anchors.get(1).sub(origin);
            Vec edge2 = anchors.get(2).sub(origin);
            Vec normal = edge1.cross(edge2).normalize();

            for (Vec point : anchors) {
                extrudedPoints.add(point.add(normal.mul(thickness / 2)));
                extrudedPoints.add(point.sub(normal.mul(thickness / 2)));
            }

            anchors = List.copyOf(extrudedPoints);
        }

        final Trigger trigger = new Trigger(anchors, position, uuid, name, color, triggeredCallback);

        if (debug) {
            DecimalFormat df = new DecimalFormat("###.###");
            LOGGER.info("Hull computation of {} took {}ms", PlainTextComponentSerializer.plainText().serialize(trigger.getName()), df.format(trigger.getLastComputationTime()));
        }

        add(trigger);
        totalTriangles += trigger.getTriangles().size();
        return trigger;
    }

    /**
     * The main movement event hook that glues the underlying collision logic together.
     * @param event The {@link PlayerMoveEvent}.
     */
    public void playerMoveEvent(PlayerMoveEvent event) {
        final Player player = event.getPlayer();
        final Pos oldPos = player.getPosition();
        final Pos newPos = event.getNewPosition();

        List<Vec> previousPoints = Trigger.getHitboxPoints(oldPos, player);
        List<Vec> currentPoints = Trigger.getHitboxPoints(newPos, player);
        Box queryBox = Box.create(
                (int) Math.floor(Math.min(oldPos.x(), newPos.x()) - 1.5),
                (int) Math.floor(Math.min(oldPos.y(), newPos.y())),
                (int) Math.floor(Math.min(oldPos.z(), newPos.z()) - 1.5),
                (int) Math.ceil(Math.max(oldPos.x(), newPos.x()) + 1.5),
                (int) Math.ceil(Math.max(oldPos.y(), newPos.y()) + 3),
                (int) Math.ceil(Math.max(oldPos.z(), newPos.z()) + 1.5)
        );
        handleTriggers(player, previousPoints, currentPoints, queryBox);
    }

    public void entityTeleportEvent(EntityTeleportEvent event) {
        if (event.getEntity() instanceof Player player) {
            final Pos oldPos = player.getPosition();
            final Pos newPos = event.getNewPosition();

            List<Vec> previousPoints = Trigger.getHitboxPoints(oldPos, player);
            List<Vec> currentPoints = Trigger.getHitboxPoints(newPos, player);
            Box queryBox = Box.create(
                    (int) Math.floor(Math.min(oldPos.x(), newPos.x()) - 1.5),
                    (int) Math.floor(Math.min(oldPos.y(), newPos.y()) - 1.5),
                    (int) Math.floor(Math.min(oldPos.z(), newPos.z()) - 1.5),
                    (int) Math.ceil(Math.max(oldPos.x(), newPos.x()) + 1.5),
                    (int) Math.ceil(Math.max(oldPos.y(), newPos.y()) + 1.5),
                    (int) Math.ceil(Math.max(oldPos.z(), newPos.z()) + 1.5)
            );

            handleTriggers(player, previousPoints, currentPoints, queryBox);
        }
    }

    public void entitySpawnEvent(EntitySpawnEvent event) {
        if (event.getEntity() instanceof Player player) {
            final Pos spawnPos = player.getPosition();

            List<Vec> currentPoints = Trigger.getHitboxPoints(spawnPos, player);

            Box queryBox = Box.create(spawnPos.blockX(), spawnPos.blockY(), spawnPos.blockZ(), spawnPos.blockX() + 1, spawnPos.blockY() + 1, spawnPos.blockZ() + 1);

            triggers.values(box -> box.intersectsOpen(queryBox)).forEach(trigger -> {
                boolean isInside = trigger.contains(currentPoints);

                // The player either spawns inside or not inside
                if (isInside) {
                    trigger.getTriggeredCallback().accept(new TriggeredCallback(player, trigger, TriggeredCallback.Type.TICK));
                    trigger.getTriggeredCallback().accept(new TriggeredCallback(player, trigger, TriggeredCallback.Type.ENTERED));
                }
            });
        }
    }

    private void handleTriggers(Player player, List<Vec> previousPoints, List<Vec> currentPoints, Box queryBox) {
        triggers.values(box -> box.intersectsOpen(queryBox)).forEach(trigger -> {
            boolean wasInside = trigger.contains(previousPoints);
            boolean isInside = trigger.contains(currentPoints);

            if (isInside) {
                trigger.getTriggeredCallback().accept(new TriggeredCallback(player, trigger, TriggeredCallback.Type.TICK));
            }
            if (!wasInside && isInside) {
                trigger.getTriggeredCallback().accept(new TriggeredCallback(player, trigger, TriggeredCallback.Type.ENTERED));
            } else if (wasInside && !isInside) {
                trigger.getTriggeredCallback().accept(new TriggeredCallback(player, trigger, TriggeredCallback.Type.EXITED));
            }
        });
    }

    /**
     * Remove a trigger from {@link TriggerManager#triggers}.
     * @param trigger The trigger to remove.
     * @return True if the trigger was removed, false if it does not exist.
     */
    public boolean remove(Trigger trigger) {
        Box key = trigger.getBoundingBox();
        triggers = triggers.remove(key, trigger);
        boolean existed = triggers.containsKey(key);
        if (existed) totalTriangles -= trigger.getTriangles().size();
        return existed;
    }
    /**
     * Add an existing trigger to {@link TriggerManager#triggers}.
     * @param trigger The trigger to add.
     */
    public void add(Trigger trigger) {
        triggers = triggers.put(trigger.getBoundingBox(), trigger);
    }

    /**
     * Check if the list of {@link TriggerManager#triggers triggers} contains a trigger.
     * @param trigger The trigger to check containment for.
     * @return true if {@link TriggerManager#triggers triggers} contains the trigger, false if not.
     */
//    public boolean contains(Trigger trigger) {
//        return triggers.contains;
//    }

    /**
     * Iterate over all registered triggers.
     * @param trigger The consumer.
     */
    public void forEach(Consumer<Trigger> trigger) {
        triggers.values().forEach(trigger);
    }

    /**
     * Get an unmodifiable copy of the list containing all triggers of this manager.
     * @return An unmodifiable copy of the list containing all triggers of this manager.
     */
    public Selection<Trigger> getTriggers() {
        return triggers.values();
    }

    /**
     * Get the combined total triangle count of all registered triggers.
     * @return Combined total triangle count of all registered triggers.
     */
    public int getTotalTriangles() {
        return totalTriangles;
    }

    /**
     * Get if this instance is in debug mode.
     * @return true if in debug mode, false if not.
     */
    public boolean isDebug() {
        return debug;
    }

    public void registerEvents(EventNode<Event> handler) {
        handler.addListener(PlayerMoveEvent.class, this::playerMoveEvent)
                .addListener(EntityTeleportEvent.class, this::entityTeleportEvent)
                .addListener(EntitySpawnEvent.class, this::entitySpawnEvent);
    }

    public static TriggerDefinition load(Path path) throws Exception {
        HoconConfigurationLoader loader = HoconConfigurationLoader.builder()
                .setPath(path)
                .setDefaultOptions(opts -> opts.withSerializers(serializers))
                .build();

        ConfigurationNode root = loader.load();

        ObjectMapperFactory factory = new DefaultObjectMapperFactory();
        ObjectMapper<TriggerDefinition> mapper = factory.getMapper(TriggerDefinition.class);
        return mapper.bindToNew().populate(root);
    }
}



