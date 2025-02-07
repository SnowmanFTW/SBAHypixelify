package pronze.hypixelify.listener;
import net.jitse.npclib.api.NPC;
import net.jitse.npclib.api.events.NPCInteractEvent;
import net.jitse.npclib.api.skin.MineSkinFetcher;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.screamingsandals.bedwars.api.game.GameStatus;
import org.screamingsandals.bedwars.config.MainConfig;
import org.screamingsandals.bedwars.events.GameStartedEventImpl;
import org.screamingsandals.bedwars.events.OpenShopEventImpl;
import org.screamingsandals.bedwars.events.PlayerJoinedEventImpl;
import org.screamingsandals.bedwars.events.PostRebuildingEventImpl;
import org.screamingsandals.bedwars.game.Game;
import org.screamingsandals.bedwars.game.GameStore;
import org.screamingsandals.bedwars.lib.entity.EntityMapper;
import org.screamingsandals.bedwars.lib.event.EventManager;
import org.screamingsandals.bedwars.player.BedWarsPlayer;
import org.screamingsandals.bedwars.player.PlayerManager;
import pronze.hypixelify.SBAHypixelify;
import pronze.hypixelify.api.MessageKeys;
import pronze.hypixelify.config.SBAConfig;
import pronze.hypixelify.game.StoreWrapper;
import pronze.hypixelify.lib.lang.LanguageService;
import pronze.hypixelify.service.NPCProviderService;
import pronze.lib.core.annotations.AutoInitialize;
import pronze.lib.core.annotations.OnInit;
import pronze.lib.core.utils.Logger;
import java.util.List;

//TODO: find a solution to skins not displaying correctly
//@AutoInitialize(listener = true)
public class NPCListener implements Listener {

    @OnInit
    public void registerSLibEvents() {
        EventManager.getDefaultEventManager().register(GameStartedEventImpl.class, this::onBedWarsGameStarted);
        EventManager.getDefaultEventManager().register(PlayerJoinedEventImpl.class, this::onBWPlayerJoin);
        EventManager.getDefaultEventManager().register(PostRebuildingEventImpl.class, this::onBWRebuild);
    }

    public void onBedWarsGameStarted(GameStartedEventImpl event) {
        final var game = event.getGame();
        if (SBAConfig.getInstance().node("npc", "enabled").getBoolean(true)) {
            var shopDisplayName = LanguageService
                    .getInstance()
                    .get(MessageKeys.NPC_SHOP_DISPLAY_NAME)
                    .toStringList();
            var upgradeShopDisplayName = LanguageService
                    .getInstance()
                    .get(MessageKeys.NPC_UPGRADE_SHOP_DISPLAY_NAME)
                    .toStringList();

            game.getGameStoreList().forEach(gameStore -> {
                final var shop = gameStore.getShopFile();
                if (shop == null) return;

                Logger.trace("Found Game Store: {} replacing it with NPCLib npc's!", gameStore);
                if (shop.equalsIgnoreCase("shop.yml") || shop.equalsIgnoreCase("upgradeShop.yml")) {
                    final var storeEntity = gameStore.getEntity();
                    gameStore.kill();

                    try {
                        var field = gameStore.getClass().getDeclaredField("entity");
                        field.setAccessible(true);
                        field.set(gameStore, null);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }

                    var storeType = StoreWrapper.Type.of(shop);
                    int skinId;
                    List<String> title;
                    switch (storeType) {
                        case NORMAL:
                            skinId = MainConfig.getInstance().node("npc", "shop-skin").getInt(1);
                            title = shopDisplayName;
                            break;
                        case UPGRADES:
                            skinId = MainConfig.getInstance().node("npc", "upgrade-shop-skin").getInt(1);
                            title = upgradeShopDisplayName;
                            break;
                        default:
                            throw new IllegalStateException("Unexpected value: " + storeType);
                    }

                    Logger.trace("Store type: {}", storeType.name());

                    MineSkinFetcher.fetchSkinFromIdAsync(skinId, skin -> Bukkit.getScheduler().runTask(SBAHypixelify.getInstance(), () -> {
                        NPC npc = NPCProviderService
                                .getInstance()
                                .getLibrary()
                                .createNPC(title);
                        npc.setLocation(gameStore.getStoreLocation());
                        npc.setSkin(skin);
                        npc.create();
                        game.getConnectedPlayers().forEach(npc::show);
                        NPCProviderService
                                .getInstance()
                                .getRegistry()
                                .register(game.getName(), StoreWrapper.of(storeEntity, npc, gameStore, storeType));
                    }));
                }
            });
        }
    }


    public void onBWPlayerJoin(PlayerJoinedEventImpl event) {
        final var game = event.getGame();
        // spectator has joined, let's show him the npc's
        if (game.getStatus() == GameStatus.RUNNING) {
            var npcs = NPCProviderService
                    .getInstance()
                    .getRegistry()
                    .get(game.getName());
            if (npcs != null) {
                npcs.forEach(npc -> npc.getNpc().show(event.getPlayer().as(Player.class)));
            }
        }
    }

    public void onBWRebuild(PostRebuildingEventImpl event) {
        final var game = event.getGame();
        final var npcs = NPCProviderService
                .getInstance()
                .getRegistry()
                .get(game.getName());

        if (npcs != null) {
            npcs.stream().map(StoreWrapper::getNpc).forEach(NPC::destroy);
        }
        NPCProviderService
                .getInstance()
                .getRegistry()
                .unregister(game.getName());
    }

    @EventHandler
    public void onNPCInteract(NPCInteractEvent event) {
        final var player = event.getWhoClicked();
        if (PlayerManager.getInstance().isPlayerInGame(player.getUniqueId())) {
            BedWarsPlayer gPlayer = PlayerManager.getInstance().getPlayer(player.getUniqueId()).orElseThrow();
            final var npc = event.getNPC();
            if (!gPlayer.isSpectator && gPlayer.getGame().getStatus() == GameStatus.RUNNING) {
                var wrappers = NPCProviderService
                        .getInstance()
                        .getRegistry()
                        .get(gPlayer.getGame().getName());

                if (wrappers != null) {
                    wrappers
                            .stream()
                            .filter(wrapper -> wrapper.getNpc().equals(npc))
                            .findFirst()
                            .ifPresent(wrapper -> open(player, wrapper.getStore(), wrapper.getEntity(), gPlayer.getGame()));
                }
            }
        }
    }

    public void open(Player player, GameStore store, Entity entity, Game game) {
        var openShopEvent = new OpenShopEventImpl(game, EntityMapper.wrapEntity(entity).orElseThrow(), PlayerManager.getInstance().getPlayer(player.getUniqueId()).orElseThrow(), store);
        EventManager.fire(openShopEvent);
    }
}
