package thunder.hack.features.modules.misc;

import io.netty.util.internal.ConcurrentSet;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.ExperienceBottleEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import thunder.hack.events.impl.EventEntitySpawn;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.math.MathUtility;
import thunder.hack.core.Managers;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static thunder.hack.features.modules.client.ClientSettings.isRu;

//~pasted~ Ported from 3arthh4ack
public class Tracker extends Module {
    public static Tracker INSTANCE;

    public Tracker() {
        super("Tracker", Category.MISC);
        INSTANCE = this;
    }

    protected final Setting<Boolean> only1v1 = new Setting<>("1v1-Only", true);
    protected final Setting<Boolean> notify = new Setting<>("Notify", true);

    protected final Set<BlockPos> placed = new ConcurrentSet<>();
    protected final AtomicInteger awaitingExp = new AtomicInteger();
    protected static final AtomicInteger crystals = new AtomicInteger();
    protected static final AtomicInteger exp = new AtomicInteger();
    protected static PlayerEntity trackedPlayer;
    protected boolean awaiting;
    protected int crystalStacks;
    protected int expStacks;

    private final List<UUID> players = new ArrayList<>();
    private final List<UUID> spawnedPlayers = new ArrayList<>();

    @Override
    public void onEnable() {
        awaiting = false;
        trackedPlayer = null;
        awaitingExp.set(0);
        crystals.set(0);
        exp.set(0);
        crystalStacks = 0;
        expStacks = 0;
    }

    @Override
    public String getDisplayInfo() {
        return trackedPlayer == null ? null : trackedPlayer.getName().getString();
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive e) {
        if (shouldEnable)
            shouldEnable = false;

        if (mc.player == null || mc.world == null)
            return;

        if (trackedPlayer == null && only1v1.getValue() && mc.world.getPlayers().size() == 2 && e.getPacket() instanceof PlaySoundS2CPacket && ((PlaySoundS2CPacket) e.getPacket()).getSound() == SoundEvents.ENTITY_ITEM_PICKUP) {
            for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
                if (player != null && player != mc.player && !player.isCreative() && !isFakePlayer(player)) {
                    trackedPlayer = player;

                    usedExp = 0;
                    usedExpStacks = 0;
                    usedCrystals = 0;
                    usedCrystalStacks = 0;

                    shouldEnable = true;

                    sendMessageWhenToggled("Şu an izlenen oyuncu: " + trackedPlayer.getName().getString() + "!");
                }
            }
        }

        if (e.getPacket() instanceof EntitySpawnS2CPacket && ((EntitySpawnS2CPacket) e.getPacket()).getEntityType() == EntityType.EXPERIENCE_ORB && trackedPlayer != null) {
            checkExp();
        }

        if (e.getPacket() instanceof EntitySpawnS2CPacket && ((EntitySpawnS2CPacket) e.getPacket()).getEntityType() == EntityType.END_CRYSTAL) {
            EntitySpawnS2CPacket packet = (EntitySpawnS2CPacket) e.getPacket();
            List<Entity> entities = new ArrayList<>();
            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof PlayerEntity) {
                    entities.add(entity);
                }
            }

            getEntitiesAsync(entities, entity -> {
                if (entity instanceof PlayerEntity && entity != null) {
                    Box box = new Box(packet.getX() - 1, packet.getY() - 1, packet.getZ() - 1, packet.getX() + 1, packet.getY() + 1, packet.getZ() + 1);
                    if (entity != mc.player && entity.getBoundingBox().intersects(box)) {
                        if (trackedPlayer != null && entity.getName().getString().equals(trackedPlayer.getName().getString())) {
                            if (notify.getValue() && message) {
                                String text = isRu() ? trackedPlayer.getName().getString() + " использовал кристалл!" :
                                        trackedPlayer.getName().getString() + " kristal kullandı!";
                                sendMessage(text);
                            }
                        }
                    }
                }
            });
        }
    }

    @Override
    public void onUpdate() {
        boolean found = false;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == null || player.equals(mc.player)) continue;

            if (found && only1v1.getValue()) {
                disable(isRu() ? "Ты не в дуели! Отключаю.." : "Disabled, you are not in a 1v1! Disabling...");
                return;
            }
            if (trackedPlayer == null)
                sendMessage(Formatting.LIGHT_PURPLE + (isRu() ? "Следим за " : "Now tracking ") + Formatting.DARK_PURPLE + player.getName().getString() + Formatting.LIGHT_PURPLE + "!");
            trackedPlayer = player;
            found = true;
        }

        if (trackedPlayer == null) return;

        int exp = this.exp.get() / 64;
        if (expStacks != exp) {
            expStacks = exp;
            if (isRu()) sendMessage(Formatting.DARK_PURPLE + trackedPlayer.getName().getString() + Formatting.LIGHT_PURPLE + " использовал " + Formatting.WHITE + exp + Formatting.LIGHT_PURPLE + (exp == 1 ? " стак" : " стаков") + " Пузырьков опыта!");
            else sendMessage(Formatting.DARK_PURPLE + trackedPlayer.getName().getString() + Formatting.LIGHT_PURPLE + " used " + Formatting.WHITE + exp + Formatting.LIGHT_PURPLE + (exp == 1 ? " stack" : " stacks") + " of XP Bottles!");
        }

        int crystals = this.crystals.get() / 64;
        if (crystalStacks != crystals) {
            crystalStacks = crystals;
            if (!isRu()) sendMessage(Formatting.DARK_PURPLE + trackedPlayer.getName().getString() + Formatting.LIGHT_PURPLE + " used " + Formatting.WHITE + crystals + Formatting.LIGHT_PURPLE + (crystals == 1 ? " stack" : " stacks") + " of Crystals!");
            else sendMessage(Formatting.DARK_PURPLE + trackedPlayer.getName().getString() + Formatting.LIGHT_PURPLE + " использовал " + Formatting.WHITE + crystals + Formatting.LIGHT_PURPLE + (crystals == 1 ? " стак" : " стаков") + " Кристаллов!");
        }
    }

    public void sendTrack() {
        if (trackedPlayer != null) {
            int c = crystals.get();
            int e = exp.get();
            StringBuilder builder;

            if (isRu()) builder = new StringBuilder().append(trackedPlayer.getName().getString()).append(Formatting.LIGHT_PURPLE).append(" использовал ").append(Formatting.WHITE).append(c).append(Formatting.LIGHT_PURPLE).append(" (").append(Formatting.WHITE);
            else builder = new StringBuilder().append(trackedPlayer.getName().getString()).append(Formatting.LIGHT_PURPLE).append(" has used ").append(Formatting.WHITE).append(c).append(Formatting.LIGHT_PURPLE).append(" (").append(Formatting.WHITE);

            if (c % 64 == 0) builder.append(c / 64);
            else builder.append(MathUtility.round(c / 64.0, 1));

            if (isRu()) builder.append(Formatting.LIGHT_PURPLE).append(") кристаллов и ").append(Formatting.WHITE).append(e).append(Formatting.LIGHT_PURPLE).append(" (").append(Formatting.WHITE);
            else builder.append(Formatting.LIGHT_PURPLE).append(") crystals and ").append(Formatting.WHITE).append(e).append(Formatting.LIGHT_PURPLE).append(" (").append(Formatting.WHITE);

            if (e % 64 == 0) builder.append(e / 64);
            else builder.append(MathUtility.round(e / 64.0, 1));

            if (isRu()) builder.append(Formatting.LIGHT_PURPLE).append(") пузырьков опыта.");
            else builder.append(Formatting.LIGHT_PURPLE).append(") bottles of experience.");

            sendMessage(builder.toString());
        }
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send e) {
        if (shouldEnable)
            shouldEnable = false;

        if (mc.player == null || mc.world == null)
            return;

        if (e.getPacket() instanceof PlayerInteractItemC2SPacket && !shouldEnable) {
            if (mc.player.getMainHandStack().getItem() == Items.EXPERIENCE_BOTTLE || mc.player.getOffHandStack().getItem() == Items.EXPERIENCE_BOTTLE) {
                usedExp++;
                if (usedExp == 64) {
                    usedExp = 0;
                    usedExpStacks++;
                }
            } else if (mc.player.getMainHandStack().getItem() == Items.END_CRYSTAL || mc.player.getOffHandStack().getItem() == Items.END_CRYSTAL) {
                usedCrystals++;
                if (usedCrystals == 64) {
                    usedCrystals = 0;
                    usedCrystalStacks++;
                }
            }
        }
    }

    private boolean message = false;

    public void checkExp() {
        List<Entity> entities = new ArrayList<>();
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof PlayerEntity) {
                entities.add(entity);
            }
        }

        getEntitiesAsync(entities, entity -> {
            if (entity instanceof PlayerEntity && entity != null) {
                if (entity != mc.player && trackedPlayer != null && entity.getName().getString().equals(trackedPlayer.getName().getString())) {
                    if (notify.getValue() && message) {
                        String text = isRu() ? trackedPlayer.getName().getString() + " выпил эксп бутылку!" :
                                trackedPlayer.getName().getString() + " deneyim şişesi kullandı!";
                        sendMessage(text);
                    }
                }
            }
        });
    }
    
    // Eksik değişkenler ve yardımcı metotlar
    private boolean shouldEnable = false;
    private int usedExp = 0;
    private int usedExpStacks = 0;
    private int usedCrystals = 0;
    private int usedCrystalStacks = 0;
    
    private void sendMessageWhenToggled(String message) {
        sendMessage(message);
    }
    
    private boolean isFakePlayer(PlayerEntity player) {
        return Managers.FRIEND.isFriend(player.getName().getString());
    }
    
    private void getEntitiesAsync(List<Entity> entities, java.util.function.Consumer<Entity> consumer) {
        for (Entity entity : entities) {
            consumer.accept(entity);
        }
    }
}
