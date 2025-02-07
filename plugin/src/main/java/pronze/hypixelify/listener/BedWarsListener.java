package pronze.hypixelify.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.screamingsandals.bedwars.Main;
import org.screamingsandals.bedwars.api.RunningTeam;
import org.screamingsandals.bedwars.api.Team;
import org.screamingsandals.bedwars.api.events.*;
import org.screamingsandals.bedwars.api.game.GameStatus;
import org.screamingsandals.bedwars.events.*;
import org.screamingsandals.bedwars.game.Game;
import org.screamingsandals.bedwars.game.GameManager;
import org.screamingsandals.bedwars.lang.LangKeys;
import org.screamingsandals.bedwars.lib.event.EventManager;
import org.screamingsandals.bedwars.lib.event.EventPriority;
import org.screamingsandals.bedwars.lib.event.OnEvent;
import org.screamingsandals.bedwars.lib.lang.Message;
import org.screamingsandals.bedwars.lib.player.PlayerMapper;
import org.screamingsandals.bedwars.lib.player.PlayerWrapper;
import org.screamingsandals.bedwars.player.BedWarsPlayer;
import org.screamingsandals.bedwars.player.PlayerManager;
import pronze.hypixelify.SBAHypixelify;
import pronze.hypixelify.api.MessageKeys;
import pronze.hypixelify.config.SBAConfig;
import pronze.hypixelify.game.Arena;
import pronze.hypixelify.game.ArenaManager;
import pronze.hypixelify.lib.lang.LanguageService;
import pronze.hypixelify.utils.SBAUtil;
import pronze.hypixelify.utils.ShopUtil;
import pronze.lib.core.Core;
import pronze.lib.core.annotations.AutoInitialize;
import pronze.lib.core.annotations.OnInit;
import pronze.lib.core.utils.Logger;
import pronze.lib.scoreboards.Scoreboard;
import pronze.lib.scoreboards.ScoreboardManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@AutoInitialize(listener = true)
public class BedWarsListener implements Listener {
    private final Map<UUID, BukkitTask> runnableCache = new HashMap<>();

    @OnInit
    public void registerSLibEvents() {
        EventManager.getDefaultEventManager().register(GameStartedEventImpl.class, this::onStarted);
        EventManager.getDefaultEventManager().register(TargetBlockDestroyedEventImpl.class, this::onTargetBlockDestroyed);
        EventManager.getDefaultEventManager().register(PostRebuildingEventImpl.class, this::onPostRebuildingEvent, EventPriority.HIGHEST);
        EventManager.getDefaultEventManager().register(GameEndingEventImpl.class, this::onOver);
        EventManager.getDefaultEventManager().register(PlayerJoinedEventImpl.class, this::onBWLobbyJoin, EventPriority.HIGHEST);
        EventManager.getDefaultEventManager().register(PlayerLeaveEventImpl.class, this::onBedWarsPlayerLeave, EventPriority.LOWEST);
        EventManager.getDefaultEventManager().register(PlayerKilledEventImpl.class, this::onBedWarsPlayerKilledEvent);
    }

    public void onStarted(GameStartedEventImpl e) {
        final var game = e.getGame();
        ArenaManager
                .getInstance()
                .createArena(game);

        LanguageService
                .getInstance()
                .get(MessageKeys.GAME_START_MESSAGE)
                .send(game.getConnectedPlayers().stream().map(PlayerMapper::wrapPlayer).toArray(PlayerWrapper[]::new));
    }

    @EventHandler
    public void onBwReload(PluginEnableEvent event) {
        final var pluginName = event.getPlugin().getName();
        //Register listeners again
        if (pluginName.equalsIgnoreCase(Main.getInstance().as(JavaPlugin.class).getName())) {
            Logger.trace("Re-registering listeners");
            final var listeners = Core.getRegisteredListeners();
            listeners.forEach(Core::unregisterListener);
            listeners.forEach(Core::registerListener);
            Logger.trace("Registration complete");
        }
    }

    public void onTargetBlockDestroyed(TargetBlockDestroyedEventImpl e) {
        final var game = e.getGame();
        ArenaManager
                .getInstance()
                .get(game.getName())
                .ifPresent(arena -> ((Arena)arena).onTargetBlockDestroyed(e));
    }

    public void onPostRebuildingEvent(PostRebuildingEventImpl e) {
        final var game = e.getGame();
        ArenaManager
                .getInstance()
                .removeArena(game);
    }

    public void onOver(GameEndingEventImpl e) {
        final var game = e.getGame();
        ArenaManager
                .getInstance()
                .get(game.getName())
                .ifPresent(arena -> ((Arena)arena).onOver(e));
    }

    public void onBWLobbyJoin(PlayerJoinedEventImpl e) {
        final var player = e.getPlayer().as(Player.class);
        final var game = (Game) GameManager.getInstance().getGame(e.getGame().getName()).orElseThrow();
        final var task = runnableCache.get(player.getUniqueId());
        if (task != null) {
            SBAUtil.cancelTask(task);
        }

        switch (game.getStatus()) {
            case WAITING:
                var bukkitTask = new BukkitRunnable() {
                    int buffer = 1; //fixes the bug where it constantly shows will start in 1 second
                    @Override
                    public void run() {
                        if (game.getStatus() == GameStatus.WAITING) {
                            if (game.getConnectedPlayers().size() >= game.getMinPlayers()) {
                                String time = game.getFormattedTimeLeft();

                                if (!time.contains("0-1")) {
                                    String[] units = time.split(":");
                                    int seconds = Integer.parseInt(units[1]) + 1;
                                    if (buffer == seconds) return;
                                    buffer = seconds;
                                    if (seconds <= 10) {
                                        var message = LanguageService
                                                .getInstance()
                                                .get(MessageKeys.GAME_STARTS_IN_MESSAGE)
                                                .replace("%seconds%", String.valueOf(seconds))
                                                .toString();

                                        message = seconds == 1 ? message
                                                .replace("seconds", "second") : message;
                                        player.sendMessage(message);
                                        SBAUtil.sendTitle(PlayerMapper.wrapPlayer(player), ShopUtil.translateColors("&c" + seconds), "", 0, 20, 0);
                                    }
                                }
                            }
                        } else {
                            this.cancel();
                            runnableCache.remove(player.getUniqueId());
                        }
                    }
                }.runTaskTimer(SBAHypixelify.getInstance(), 3L, 20L);
                runnableCache.put(player.getUniqueId(), bukkitTask);
                break;
            case RUNNING:
                ArenaManager
                        .getInstance()
                        .get(game.getName())
                        .ifPresent(arena -> arena.getScoreboardManager().createBoard(player));
                break;
        }
    }

    public void onBedWarsPlayerLeave(PlayerLeaveEventImpl e) {
        final var player = e.getPlayer().as(Player.class);
        final var task = runnableCache.get(player.getUniqueId());
        final var game = e.getGame();
        ArenaManager
                .getInstance()
                .get(game.getName())
                .ifPresent(arena -> {
                    final var scoreboardManager = arena.getScoreboardManager();
                    if (scoreboardManager != null) {
                        scoreboardManager.removeBoard(player);
                    }
                });

        if (task != null) {
            SBAUtil.cancelTask(task);
        }
        runnableCache.remove(player.getUniqueId());

        ScoreboardManager
                .getInstance()
                .fromCache(player.getUniqueId())
                .ifPresent(Scoreboard::destroy);
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    public void onBedWarsPlayerKilledEvent(PlayerKilledEventImpl e) {
        final var game = e.getGame();
        // query arena instance for access to Victim/Killer data
        ArenaManager
                .getInstance()
                .get(game.getName())
                .ifPresent(arena -> {
                    final var victim = e.getPlayer();
                    // player has died, increment death counter
                    arena.getPlayerData(victim.getUuid())
                            .ifPresent(victimData -> victimData.setDeaths(victimData.getDeaths() + 1));

                    final var killer = e.getKiller();
                    //killer is present
                    if (killer != null) {
                        // get victim game profile
                        final var gVictim = PlayerManager
                                .getInstance()
                                .getPlayer(victim.getUuid())
                                .orElse(null);

                        if (gVictim == null || gVictim.isSpectator) return;

                        // get victim team to check if it was a final kill or not
                        final var victimTeam = game.getTeamOfPlayer(victim.as(Player.class));
                        if (victimTeam != null) {
                            arena.getPlayerData(killer.getUuid())
                                    .ifPresent(killerData -> {
                                        // increment kill counter for killer
                                        killerData.setKills(killerData.getKills() + 1);
                                        if (!victimTeam.isAlive()) {
                                            // increment final kill counter for killer
                                            killerData.setFinalKills(killerData.getFinalKills() + 1);
                                        }
                                    });
                        }
                    }
                });
    }

}
