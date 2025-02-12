package pronze.hypixelify.specials.listener;
import org.screamingsandals.bedwars.player.PlayerManager;
import pronze.hypixelify.specials.Dragon;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.screamingsandals.bedwars.Main;
import org.screamingsandals.bedwars.api.config.ConfigurationContainer;
import org.screamingsandals.bedwars.api.game.GameStatus;
import org.screamingsandals.bedwars.api.special.SpecialItem;
import org.screamingsandals.bedwars.utils.MiscUtils;

import java.util.List;

public class DragonListener implements Listener {

    @EventHandler
    public void onDragonDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof EnderDragon)) {
            return;
        }

        final var eventDragon = (EnderDragon) event.getEntity();
        Main.getInstance().getGameManager().getGameNames().forEach(name-> {
            final var game = Main.getInstance().getGameManager().getGame(name).get();
            if (game.getStatus() == GameStatus.RUNNING && eventDragon.getWorld().equals(game.getGameWorld())) {
                List<SpecialItem> dragons = game.getActivedSpecialItems(Dragon.class);
                for (var item : dragons) {
                    if (item instanceof Dragon) {
                        final var dragon = (Dragon) item;
                        if (dragon.getEntity().equals(eventDragon)) {
                            if (event.getDamager() instanceof Player) {
                                Player player = (Player) event.getDamager();
                                if (PlayerManager.getInstance().isPlayerInGame(player.getUniqueId())) {
                                    if (dragon.getTeam() != game.getTeamOfPlayer(player)) {
                                        return;
                                    }
                                }
                            } else if (event.getDamager() instanceof Projectile) {
                                ProjectileSource shooter = ((Projectile) event.getDamager()).getShooter();
                                if (shooter instanceof Player) {
                                    Player player = (Player) shooter;
                                    if (PlayerManager.getInstance().isPlayerInGame(player.getUniqueId())) {
                                        if (dragon.getTeam() != game.getTeamOfPlayer(player)) {
                                            return;
                                        }
                                    }
                                }
                            }

                            event.setCancelled(game.getConfigurationContainer().getOrDefault(ConfigurationContainer.FRIENDLY_FIRE, Boolean.class, false));
                            return;
                        }
                        return;
                    }
                }
            }
        });
    }



    @EventHandler
    public void onEnderDragonTarget(EntityTargetEvent event) {
        if (!(event.getEntity() instanceof EnderDragon)) {
            return;
        }

        final var enderDragon = (EnderDragon) event.getEntity();


        Main.getInstance().getGameManager().getGameNames().forEach(gameName-> {
            final var game = Main.getInstance().getGameManager().getGame(gameName).get();
            if ((game.getStatus() == GameStatus.RUNNING || game.getStatus() == GameStatus.GAME_END_CELEBRATING) && enderDragon.getWorld().equals(game.getGameWorld())) {
                List<SpecialItem> dragons = game.getActivedSpecialItems(Dragon.class);
                dragons.forEach(item-> {
                    if (item instanceof Dragon) {
                        final var dragon = (Dragon) item;
                        if (dragon.getEntity().equals(enderDragon)) {
                            if (event.getTarget() instanceof Player) {
                                final var player = (Player) event.getTarget();
                                if (game.isProtectionActive(player)) {
                                    event.setCancelled(true);
                                    return;
                                }

                                if (PlayerManager.getInstance().isPlayerInGame(player.getUniqueId())) {
                                    if (dragon.getTeam() == game.getTeamOfPlayer(player)) {
                                        event.setCancelled(true);

                                        final var enemyTarget = MiscUtils.findTarget(game, player, 40);
                                        if (enemyTarget != null) {
                                            enderDragon.setTarget(enemyTarget);
                                        }
                                    }
                                }
                            }
                        }
                    }
                });
            }
        });

    }

    @EventHandler
    public void onDragonTargetDeath(PlayerDeathEvent event) {
        if (PlayerManager.getInstance().isPlayerInGame(event.getEntity().getUniqueId())) {
            final var game = PlayerManager
                    .getInstance()
                    .getGameOfPlayer(event.getEntity().getUniqueId())
                    .orElseThrow();

            final var dragons = game.getActivedSpecialItems(Dragon.class);
            for (var item : dragons) {
                final var dragon = (Dragon) item;
                final var enderDragon = (EnderDragon) dragon.getEntity();
                if (enderDragon.getTarget() != null && enderDragon.getTarget().equals(event.getEntity())) {
                    enderDragon.setTarget(null);
                }
            }
        }
    }

    @EventHandler
    public void onDragonDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon)) {
            return;
        }

        EnderDragon eventDragon = (EnderDragon) event.getEntity();
        Main.getInstance().getGameManager().getGameNames().forEach(name-> {
            final var game = Main.getInstance().getGameManager().getGame(name).get();
            if ((game.getStatus() == GameStatus.RUNNING || game.getStatus() == GameStatus.GAME_END_CELEBRATING) && eventDragon.getWorld().equals(game.getGameWorld())) {
                List<SpecialItem> dragons = game.getActivedSpecialItems(Dragon.class);
                for (SpecialItem item : dragons) {
                    if (item instanceof EnderDragon) {
                        Dragon dragon = (Dragon) item;
                        if (dragon.getEntity().equals(eventDragon)) {
                            event.getDrops().clear();
                        }
                    }
                }
            }
        });
    }

}
