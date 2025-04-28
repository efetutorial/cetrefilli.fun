package thunder.hack.features.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import thunder.hack.core.Managers;
import thunder.hack.core.manager.client.ModuleManager;
import thunder.hack.events.impl.EventSync;
import thunder.hack.events.impl.PlayerUpdateEvent;
import thunder.hack.events.impl.ChatEvent;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;
import thunder.hack.utility.math.MathUtility;
import thunder.hack.utility.render.Render2DEngine;
import thunder.hack.utility.render.Render3DEngine;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static net.minecraft.util.math.MathHelper.wrapDegrees;

public final class CSGOAimbot extends Module {
    private final Setting<Float> aimRange = new Setting<>("Range", 80f, 20f, 200f);
    private final Setting<Integer> aimStrength = new Setting<>("AimStrength", 5, 1, 50);
    private final Setting<Integer> aimSmooth = new Setting<>("AimSmooth", 8, 1, 30);
    private final Setting<Integer> aimLockTime = new Setting<>("AimLockTime", 2, 1, 10);
    private final Setting<Boolean> ignoreWalls = new Setting<>("IgnoreWalls", false);
    private final Setting<Boolean> silentAim = new Setting<>("SilentAim", false);
    private final Setting<Integer> fov = new Setting<>("FOV", 40, 10, 180);
    private final Setting<Float> randomYaw = new Setting<>("YawRandom", 0.3f, 0f, 3f);
    private final Setting<Float> randomPitch = new Setting<>("PitchRandom", 0.15f, 0f, 3f);
    private final Setting<Float> predict = new Setting<>("AimPredict", 0.75f, 0.5f, 5f);
    private final Setting<Bone> targetBone = new Setting<>("TargetBone", Bone.Head);
    private final Setting<Boolean> onlyTargetWithGun = new Setting<>("OnlyGunTarget", true);
    private final Setting<Boolean> humanizeCurve = new Setting<>("HumanizeCurve", true);
    private final Setting<Float> curveRandomness = new Setting<>("CurveRandomness", 0.5f, 0.1f, 2f);
    private final Setting<Integer> reactionDelay = new Setting<>("ReactionDelay", 150, 0, 500);
    private final Setting<Boolean> slowStartup = new Setting<>("SlowStartup", true);
    private final Setting<Boolean> randomBreaks = new Setting<>("RandomBreaks", true);
    private final Setting<Boolean> chatCommand = new Setting<>("ChatCommand", true);

    private Entity target;
    private float rotationYaw, rotationPitch, assistAcceleration;
    private int aimTicks = 0;
    private Timer visibleTime = new Timer();
    private Timer reactionTimer = new Timer();
    private Timer breakTimer = new Timer();
    private boolean isBreaking = false;
    private float curveOffsetX = 0f;
    private float curveOffsetY = 0f;
    private float prevDeltaYaw = 0f;
    private float prevDeltaPitch = 0f;

    public CSGOAimbot() {
        super("CSGOAimbot", Category.COMBAT);
    }


    @EventHandler
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        // Kontrol et - silahla hedefleme
        if (onlyTargetWithGun.getValue() && !isHoldingGun()) {
            if (target != null) {
                target = null;
                aimTicks = 0;
                assistAcceleration = 0;
                resetCurveOffsets();
            }
            return;
        }

        // Hedef bulma veya güncelleme
        updateTarget();

        // Rastgele duraklamalar ekle
        if (randomBreaks.getValue() && target != null) {
            if (!isBreaking && Math.random() < 0.01) { // %1 olasılıkla durakla
                isBreaking = true;
                breakTimer.reset();
            }
            
            if (isBreaking && breakTimer.passedMs((long) (150 + Math.random() * 350))) {
                isBreaking = false;
            }
            
            if (isBreaking) return;
        }

        // Hedefi yönlendirme
        if (target != null) {
            // Reaksiyon gecikmesi
            if (target != null && aimTicks == 0 && reactionDelay.getValue() > 0) {
                if (!reactionTimer.passedMs(reactionDelay.getValue() + (long) (Math.random() * 100))) {
                    return;
                }
            }
            
            aimTicks++;
            
            // Kademeli hızlanma
            if (slowStartup.getValue()) {
                assistAcceleration = Math.min(1.0f, assistAcceleration + (aimStrength.getValue() / 2000f));
            } else {
                assistAcceleration = Math.min(1.0f, assistAcceleration + (aimStrength.getValue() / 1000f));
            }

            Vec3d targetVec = getResolvedPos(target).add(0, targetBone.getValue().getH(), 0);
            
            float delta_yaw = wrapDegrees((float) wrapDegrees(Math.toDegrees(Math.atan2(targetVec.z - mc.player.getZ(), (targetVec.x - mc.player.getX()))) - 90) - rotationYaw);
            float delta_pitch = ((float) (-Math.toDegrees(Math.atan2(targetVec.y - (mc.player.getPos().y + mc.player.getEyeHeight(mc.player.getPose())), Math.sqrt(Math.pow((targetVec.x - mc.player.getX()), 2) + Math.pow(targetVec.z - mc.player.getZ(), 2))))) - rotationPitch);

            if (delta_yaw > 180)
                delta_yaw = delta_yaw - 180;

            // Humanize Curve - daha doğal eğimli hareket
            if (humanizeCurve.getValue()) {
                // Her 10 tick'te bir eğri ofsetlerini güncelle
                if (aimTicks % 10 == 0 || curveOffsetX == 0f) {
                    curveOffsetX = (float) (Math.random() * 2 - 1) * curveRandomness.getValue();
                    curveOffsetY = (float) (Math.random() * 2 - 1) * curveRandomness.getValue();
                }
                
                // Eğri üzerinde hareket için ofset uygula
                delta_yaw += curveOffsetX * (1 - Math.abs(delta_yaw) / 180f);
                delta_pitch += curveOffsetY * (1 - Math.abs(delta_pitch) / 90f);
            }

            // Doğallaştırılmış hareket - ani değişimleri azalt
            float smoothFactorYaw = aimSmooth.getValue() * (1 + (float) Math.random() * 0.3f);
            float smoothFactorPitch = aimSmooth.getValue() * (1 + (float) Math.random() * 0.3f);
            
            // Önceki delta ile şimdiki arasında yumuşak geçiş
            delta_yaw = prevDeltaYaw + (delta_yaw - prevDeltaYaw) / smoothFactorYaw;
            delta_pitch = prevDeltaPitch + (delta_pitch - prevDeltaPitch) / smoothFactorPitch;
            
            prevDeltaYaw = delta_yaw;
            prevDeltaPitch = delta_pitch;

            // Hedef kaymayı ayarla ve rastgelelik ekle
            float deltaYaw = MathHelper.clamp(delta_yaw, -aimSmooth.getValue(), aimSmooth.getValue());
            float deltaPitch = MathHelper.clamp(delta_pitch, -aimSmooth.getValue(), aimSmooth.getValue());

            // Hareket hızında rastgelelik
            float randomFactor = (float) (0.7 + Math.random() * 0.6);
            deltaYaw *= randomFactor;
            deltaPitch *= randomFactor;

            float newYaw = rotationYaw + deltaYaw + MathUtility.random(-randomYaw.getValue(), randomYaw.getValue());
            float newPitch = MathHelper.clamp(rotationPitch + deltaPitch + MathUtility.random(-randomPitch.getValue(), randomPitch.getValue()), -90.0F, 90.0F);

            // GCD düzeltmesi (fare hassasiyeti kaymalarını simüle eder)
            double gcdFix = (Math.pow(mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2, 3.0)) * 1.2;
            rotationYaw = (float) (newYaw - (newYaw - rotationYaw) % gcdFix);
            rotationPitch = (float) (newPitch - (newPitch - rotationPitch) % gcdFix);

            // Hedef süre sonu kontrol
            if (aimTicks >= aimLockTime.getValue() * 20) {
                target = null;
                aimTicks = 0;
                assistAcceleration = 0;
                resetCurveOffsets();
                reactionTimer.reset();
            }
        } else {
            // Hedef yoksa oyuncunun mevcut rotasyonunu kullan
            rotationYaw = mc.player.getYaw();
            rotationPitch = mc.player.getPitch();
            aimTicks = 0;
            assistAcceleration = 0;
            resetCurveOffsets();
        }

        // Rotasyon düzeltmesi (opsiyonel)
        if (silentAim.getValue() && target != null) {
            ModuleManager.rotations.fixRotation = rotationYaw;
        }
    }

    private void resetCurveOffsets() {
        curveOffsetX = 0f;
        curveOffsetY = 0f;
        prevDeltaYaw = 0f;
        prevDeltaPitch = 0f;
    }

    @EventHandler
    public void onSync(EventSync event) {
        if (target != null && silentAim.getValue()) {
            mc.player.setYaw(rotationYaw);
            mc.player.setPitch(rotationPitch);
        }
    }

    @Override
    public void onEnable() {
        target = null;
        rotationYaw = mc.player.getYaw();
        rotationPitch = mc.player.getPitch();
        aimTicks = 0;
        assistAcceleration = 0;
        resetCurveOffsets();
        reactionTimer.reset();
        breakTimer.reset();
        isBreaking = false;
    }

    public void onRender3D(MatrixStack stack) {
        if (target != null && !silentAim.getValue() && !isBreaking) {
            // Daha yumuşak ve doğal interpolasyon
            float interpolationFactor = (float) (assistAcceleration * (0.8 + Math.random() * 0.4));
            mc.player.setYaw((float) Render2DEngine.interpolate(mc.player.getYaw(), rotationYaw, interpolationFactor));
            mc.player.setPitch((float) Render2DEngine.interpolate(mc.player.getPitch(), rotationPitch, interpolationFactor));
        }
    }

    private void updateTarget() {
        if (target == null || skipEntity(target)) {
            findTarget();
        }
    }

    private void findTarget() {
        List<Entity> potentialTargets = new CopyOnWriteArrayList<>();
        
        for (Entity entity : mc.world.getEntities()) {
            if (skipEntity(entity)) continue;
            potentialTargets.add(entity);
        }

        float best_fov = fov.getValue();
        Entity best_entity = null;

        for (Entity ent : potentialTargets) {
            float temp_fov = Math.abs(((float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(ent.getZ() - mc.player.getZ(), ent.getX() - mc.player.getX())) - 90.0)) - MathHelper.wrapDegrees(mc.player.getYaw()));
            if (temp_fov < best_fov) {
                best_entity = ent;
                best_fov = temp_fov;
            }
        }
        
        // Hedef değiştiğinde reaksiyon zamanlayıcısını sıfırla
        if (best_entity != target) {
            reactionTimer.reset();
            assistAcceleration = 0;
        }
        
        target = best_entity;
    }

    private boolean skipEntity(Entity entity) {
        // Genel kontroller
        if (!(entity instanceof LivingEntity ent)) return true;
        if (ent.isDead()) return true;
        if (!entity.isAlive()) return true;
        if (entity instanceof ArmorStandEntity) return true;
        if (ModuleManager.antiBot.isEnabled() && AntiBot.bots.contains(entity)) return true;
        
        // Sadece oyuncu hedefleri al
        if (!(entity instanceof PlayerEntity pl)) return true;
        
        // Kendini hedefleme
        if (entity == mc.player) return true;
        
        // Arkadaşları hedefleme
        if (Managers.FRIEND.isFriend(pl)) return true;
        
        // FOV kontrolü
        if (Math.abs(getYawToEntity(entity)) > fov.getValue()) return true;
        
        // Takım kontrolü
        if (pl.getTeamColorValue() == mc.player.getTeamColorValue() && mc.player.getTeamColorValue() != 16777215)
            return true;
        
        // Duvarlardan görüş kontrolü
        if (!ignoreWalls.getValue() && !mc.player.canSee(entity)) return true;
        
        // Menzil kontrolü
        return mc.player.squaredDistanceTo(getResolvedPos(entity)) > aimRange.getPow2Value();
    }

    private boolean isHoldingGun() {
        Item item = mc.player.getMainHandStack().getItem();
        return item == Items.BOW || 
               item == Items.CROSSBOW || 
               item == Items.TRIDENT;
    }

    private float getYawToEntity(@NotNull Entity entity) {
        double xDist = entity.getX() - mc.player.getX();
        double zDist = entity.getZ() - mc.player.getZ();
        float yaw1 = (float) (StrictMath.atan2(zDist, xDist) * 180.0 / Math.PI) - 90.0f;
        return wrapDegrees(yaw1 - mc.player.getYaw());
    }

    private Vec3d getResolvedPos(@NotNull Entity pl) {
        return new Vec3d(
            pl.getX() + (pl.getX() - pl.prevX) * predict.getValue(), 
            pl.getY(), 
            pl.getZ() + (pl.getZ() - pl.prevZ) * predict.getValue()
        );
    }

    private enum Bone {
        Head(1.7f),
        Neck(1.5f),
        Torso(1.0f),
        Legs(0.5f);

        private final float h;

        Bone(float h) {
            this.h = h;
        }

        public float getH() {
            return h;
        }
    }
} 