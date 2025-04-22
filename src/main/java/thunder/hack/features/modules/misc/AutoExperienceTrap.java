package thunder.hack.features.modules.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;
import thunder.hack.events.impl.EventSync;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;
import thunder.hack.utility.player.InventoryUtility;
import thunder.hack.utility.player.InteractionUtility;
import thunder.hack.utility.player.SearchInvResult;

import static thunder.hack.features.modules.client.ClientSettings.isRu;

/**
 * AutoExperienceTrap modülü, otomatik olarak XP şişelerini kendine doğru kullanır.
 * Aşağı bakarak XP şişesi kullanımını otomatikleştirir.
 */
public class AutoExperienceTrap extends Module {
    
    public AutoExperienceTrap() {
        super("AutoXP", Category.MISC);
    }

    // Ana Ayarlar
    private final Setting<Integer> delay = new Setting<>("Delay", 50, 0, 1000);
    private final Setting<Float> pitch = new Setting<>("Pitch", 90.0f, -90.0f, 90.0f);
    private final Setting<Integer> minXpCount = new Setting<>("MinXpCount", 1, 1, 64);
    private final Setting<Integer> maxUsage = new Setting<>("MaxUsage", 64, 1, 512);
    
    // Kullanım Ayarları
    private final Setting<UseMode> useMode = new Setting<>("UseMode", UseMode.MainHand);
    private final Setting<Boolean> keepCurrentItem = new Setting<>("KeepCurrentItem", true);
    
    // Diğer Ayarlar
    private final Setting<Boolean> autoDisable = new Setting<>("AutoDisable", false);
    private final Setting<Boolean> notifications = new Setting<>("Notifications", true);

    private final Timer useTimer = new Timer();
    private int totalUsage = 0;
    private float originalPitch = 0f;
    private boolean isUsing = false;

    private enum UseMode {
        MainHand,   // Ana ele XP şişesi alır
        OffHand,    // İkinci elde XP şişesi varsa kullanır
        BothHands   // Her iki elde de XP şişesi varsa kullanır
    }

    @Override
    public void onEnable() {
        totalUsage = 0;
        isUsing = false;
        
        if (notifications.getValue()) {
            sendMessage(isRu() ? "Модуль включен!" : "Modül etkinleştirildi!");
        }
    }
    
    @Override
    public void onDisable() {
        // Rotasyonu geri yükle
        if (mc.player != null && isUsing) {
            mc.player.setPitch(originalPitch);
            isUsing = false;
        }
        
        if (notifications.getValue()) {
            sendMessage(isRu() ? "Модуль выключен!" : "Modül devre dışı bırakıldı!");
        }
    }

    @Override
    public String getDisplayInfo() {
        return totalUsage + "";
    }

    @EventHandler
    public void onSync(EventSync event) {
        if (mc.player == null || mc.world == null) return;
        
        // Maksimum kullanım sayısını aştıysak devre dışı bırak
        if (autoDisable.getValue() && totalUsage >= maxUsage.getValue()) {
            toggle();
            return;
        }
        
        // XP şişesi kullanma zamanlaması
        if (useTimer.passedMs(delay.getValue())) {
            // XP şişesi var mı kontrol et
            SearchInvResult result = null;
            
            if (useMode.getValue() == UseMode.OffHand) {
                // İkinci el kontrolü
                if (mc.player.getOffHandStack().getItem() == Items.EXPERIENCE_BOTTLE) {
                    useXpBottleOffHand();
                    useTimer.reset();
                    return;
                }
            } else if (useMode.getValue() == UseMode.BothHands) {
                // Önce ikinci eli kontrol et, varsa kullan
                if (mc.player.getOffHandStack().getItem() == Items.EXPERIENCE_BOTTLE) {
                    useXpBottleOffHand();
                    useTimer.reset();
                    return;
                }
                // İkinci elde yoksa ana eli kontrol et
                result = InventoryUtility.findItemInHotBar(Items.EXPERIENCE_BOTTLE);
            } else {
                // Sadece ana elden kullan
                result = InventoryUtility.findItemInHotBar(Items.EXPERIENCE_BOTTLE);
            }
            
            // Ana eldeki XP şişesini kontrol et
            if (result != null && result.found() && result.stack() != null) {
                int xpCount = result.stack().getCount();
                if (xpCount >= minXpCount.getValue()) {
                    useXpBottleMainHand(result);
                    useTimer.reset();
                } else if (notifications.getValue() && totalUsage == 0) {
                    sendMessage(isRu() ? "Недостаточно опыта! Требуется: " + minXpCount.getValue() : "Yetersiz XP şişesi! Gerekli: " + minXpCount.getValue());
                    if (autoDisable.getValue()) {
                        toggle();
                    }
                }
            } else if (notifications.getValue() && totalUsage == 0) {
                sendMessage(isRu() ? "Недостаточно опыта! Требуется XP бутылки в хотбаре." : "Yetersiz XP şişesi! Hotbar'da XP şişesi gerekli.");
                if (autoDisable.getValue()) {
                    toggle();
                }
            }
        }
    }
    
    private void useXpBottleMainHand(SearchInvResult result) {
        // Mevcut slotu kaydet
        int prevSlot = mc.player.getInventory().selectedSlot;
        
        // Orijinal bakış açısını kaydet
        if (!isUsing) {
            originalPitch = mc.player.getPitch();
            isUsing = true;
        }
        
        // Aşağı bak
        mc.player.setPitch(pitch.getValue());
        
        // XP şişesine geçiş yap
        if (keepCurrentItem.getValue()) {
            // Geçici olarak XP şişesine geç
            int xpSlot = result.slot();
            
            if (xpSlot != prevSlot) {
                InventoryUtility.switchTo(xpSlot);
            }
            
            // XP şişesini kullan
            InteractionUtility.sendSequencedPacket(id -> 
                new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, mc.player.getYaw(), mc.player.getPitch())
            );
            
            // Önceki slota geri dön
            if (xpSlot != prevSlot) {
                InventoryUtility.switchTo(prevSlot);
            }
        } else {
            // Direkt olarak XP şişesine geçiş yap ve kullan
            InventoryUtility.switchTo(result.slot());
            
            // XP şişesini kullan
            InteractionUtility.sendSequencedPacket(id -> 
                new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, mc.player.getYaw(), mc.player.getPitch())
            );
        }
        
        // Kullanım sayısını artır
        totalUsage++;
    }
    
    private void useXpBottleOffHand() {
        // Orijinal bakış açısını kaydet
        if (!isUsing) {
            originalPitch = mc.player.getPitch();
            isUsing = true;
        }
        
        // Aşağı bak
        mc.player.setPitch(pitch.getValue());
        
        // İkinci eldeki XP şişesini kullan
        InteractionUtility.sendSequencedPacket(id -> 
            new PlayerInteractItemC2SPacket(Hand.OFF_HAND, id, mc.player.getYaw(), mc.player.getPitch())
        );
        
        // Kullanım sayısını artır
        totalUsage++;
    }
} 