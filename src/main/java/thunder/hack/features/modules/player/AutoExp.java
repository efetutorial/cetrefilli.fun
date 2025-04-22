package thunder.hack.features.modules.player;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;
import thunder.hack.core.Managers;
import thunder.hack.events.impl.EventSync;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.setting.impl.Bind;
import thunder.hack.setting.impl.BooleanSettingGroup;
import thunder.hack.utility.Timer;
import thunder.hack.utility.player.InventoryUtility;
import thunder.hack.utility.player.InteractionUtility;
import thunder.hack.utility.player.SearchInvResult;

import java.text.DecimalFormat;

import static thunder.hack.features.modules.client.ClientSettings.isRu;

/**
 * AutoExp modülü, otomatik olarak XP şişelerini kullanmayı sağlar.
 * Sessiz modda çalışabilir ve çeşitli kullanım ayarlarına sahiptir.
 */
public class AutoExp extends Module {
    
    public AutoExp() {
        super("AutoExp", Category.PLAYER);
    }

    // Ana Ayarlar
    private final Setting<Mode> mode = new Setting<>("Mode", Mode.Normal);
    private final Setting<Integer> frequency = new Setting<>("Frequency", 1, 1, 10);
    private final Setting<Integer> delay = new Setting<>("Delay", 50, 0, 500);
    private final Setting<Float> pitch = new Setting<>("Pitch", 90.0f, -90.0f, 90.0f, v -> mode.is(Mode.Normal));
    private final Setting<Integer> xpPerUse = new Setting<>("XpPerUse", 1, 1, 10, v -> mode.is(Mode.Count));
    
    // Hotkey Ayarları
    private final Setting<BooleanSettingGroup> bindSettings = new Setting<>("Bind Settings", new BooleanSettingGroup(false));
    private final Setting<Bind> useKey = new Setting<>("UseKey", new Bind(GLFW.GLFW_KEY_X, false, false)).addToGroup(bindSettings);
    
    // Kullanım Koşulları
    private final Setting<BooleanSettingGroup> useConditions = new Setting<>("Use Conditions", new BooleanSettingGroup(false));
    private final Setting<Boolean> onlyWhileStanding = new Setting<>("Only While Standing", false).addToGroup(useConditions);
    private final Setting<Boolean> checkForEnchanting = new Setting<>("Check For Enchanting", false).addToGroup(useConditions);
    private final Setting<Boolean> onlyAboveExp = new Setting<>("Only Above XP", false).addToGroup(useConditions);
    private final Setting<Integer> minExpLevel = new Setting<>("Min Exp Level", 0, 0, 30, v -> onlyAboveExp.getValue()).addToGroup(useConditions);
    
    // Zırh Durma Ayarı - StopAt
    private final Setting<BooleanSettingGroup> stopAtSettings = new Setting<>("StopAt Settings", new BooleanSettingGroup(false));
    private final Setting<Boolean> stopAtEnabled = new Setting<>("StopAt Enabled", true).addToGroup(stopAtSettings);
    private final Setting<Float> stopAtDurability = new Setting<>("StopAt Percent", 96.0f, 1.0f, 100.0f).addToGroup(stopAtSettings);
    private final Setting<StopArmorType> stopArmorType = new Setting<>("Armor Type", StopArmorType.AnyArmor).addToGroup(stopAtSettings);
    private final Setting<Boolean> showCurrentDura = new Setting<>("Show Current Dura", true).addToGroup(stopAtSettings);
    
    // Zaman Ayarları
    private final Setting<BooleanSettingGroup> timeSettings = new Setting<>("Time Settings", new BooleanSettingGroup(false));
    private final Setting<Boolean> autoDisable = new Setting<>("Auto Disable", false).addToGroup(timeSettings);
    private final Setting<Integer> disableDelay = new Setting<>("Disable Delay", 2000, 100, 10000, v -> autoDisable.getValue()).addToGroup(timeSettings);

    // Diğer Ayarlar
    private final Setting<Boolean> silentMode = new Setting<>("Silent Mode", true);
    private final Setting<Boolean> fakeSwitch = new Setting<>("Fake Switch", true); // Görünürde el değiştirsin ama aslında değiştirmesin
    private final Setting<Boolean> prioritizeHotbar = new Setting<>("Prioritize Hotbar", true); // Hotbardaki XP şişelerini önceliklendir
    private final Setting<Boolean> noHandChange = new Setting<>("No Hand Change", true);
    private final Setting<Boolean> silentSwitch = new Setting<>("Silent Switch", true, v -> !silentMode.getValue());
    private final Setting<Boolean> swingHand = new Setting<>("Swing Hand", false);
    private final Setting<Boolean> autoDisableEmpty = new Setting<>("Auto Disable Empty", true);
    private final Setting<Boolean> notifications = new Setting<>("Notifications", true);

    private final Timer useTimer = new Timer();
    private final Timer disableTimer = new Timer();
    private int expUseCount = 0;
    private boolean performingAction = false;
    private int lastSlot = -1;
    
    // Zırh dayanıklılık yüzdesini göstermek için
    private final DecimalFormat durabilityFormat = new DecimalFormat("0.0");
    private float highestDurability = 0f;

    private enum Mode {
        Normal, // Normal kullanım modu
        Count,  // Belirli bir sayıda XP şişesi kullanır
        BindOnly // Sadece belirtilen tuşa basılıyken çalışır
    }
    
    private enum StopArmorType {
        AnyArmor,       // Herhangi bir zırh parçası
        AllArmor,       // Tüm zırh parçaları
        Helmet,         // Sadece kask
        Chestplate,     // Sadece göğüslük
        Leggings,       // Sadece pantolon
        Boots           // Sadece botlar
    }

    @Override
    public void onEnable() {
        expUseCount = 0;
        performingAction = false;
        disableTimer.reset();
        lastSlot = -1;
        highestDurability = 0f;
    }
    
    @Override
    public void onDisable() {
        // Son slota geri dön (eğer gerekirse)
        if (lastSlot != -1 && mc.player != null && fakeSwitch.getValue()) {
            // Gerçek slot değişimi yerine paket ile görselini göndeririz
            InventoryUtility.switchTo(lastSlot);
            lastSlot = -1;
        }
    }

    @Override
    public String getDisplayInfo() {
        if (mode.is(Mode.Count)) {
            return xpPerUse.getValue() - expUseCount + "";
        }
        
        // StopAt durum bilgisini göster
        if (stopAtEnabled.getValue() && showCurrentDura.getValue() && highestDurability > 0) {
            return durabilityFormat.format(highestDurability) + "%";
        }
        
        SearchInvResult result = InventoryUtility.findItemInInventory(Items.EXPERIENCE_BOTTLE);
        return result.found() && result.stack() != null ? result.stack().getCount() + "" : "0";
    }

    @EventHandler
    public void onSync(EventSync event) {
        if (mc.player == null || mc.world == null) return;
        
        // Auto disable işlemi
        if (autoDisable.getValue() && disableTimer.passedMs(disableDelay.getValue())) {
            toggle();
            return;
        }
        
        // Mode BindOnly durumunda ve belirtilen tuş basılı değilse işlem yapma
        if (mode.is(Mode.BindOnly) && !useKey.getValue().isDown()) return;
        
        // Kullanım koşullarını kontrol et
        if (onlyWhileStanding.getValue() && !mc.player.isOnGround()) return;
        if (onlyAboveExp.getValue() && mc.player.experienceLevel < minExpLevel.getValue()) return;
        
        // Enchanting işlemi kontrolü
        if (checkForEnchanting.getValue() && (mc.currentScreen != null || performingAction)) return;
        
        // Zırh durabilite kontrolü - StopAt
        if (stopAtEnabled.getValue()) {
            CheckArmorResult armorCheck = checkArmorDurability();
            if (armorCheck.shouldStop) {
                highestDurability = armorCheck.highestDurability;
                if (notifications.getValue()) {
                    sendMessage(isRu() ? "Прочность брони достигла " + durabilityFormat.format(highestDurability) + "% (лимит: " + stopAtDurability.getValue() + "%)" : 
                            "Zırh dayanıklılığı " + durabilityFormat.format(highestDurability) + "% seviyesine ulaştı (limit: " + stopAtDurability.getValue() + "%)");
                }
                toggle();
                return;
            }
            highestDurability = armorCheck.highestDurability;
        }
        
        // Count modunda ve belirlenen sayıya ulaşıldıysa işlem yapma
        if (mode.is(Mode.Count) && expUseCount >= xpPerUse.getValue()) {
            if (autoDisable.getValue()) {
                toggle();
            }
            return;
        }
        
        // XP şişesi kullanma zamanlaması
        if (useTimer.passedMs(delay.getValue())) {
            // XP şişesi ara, önce hotbar'da ara
            SearchInvResult result;
            
            if (prioritizeHotbar.getValue()) {
                result = InventoryUtility.findItemInHotBar(Items.EXPERIENCE_BOTTLE);
                if (!result.found()) {
                    // Hotbar'da bulunamadıysa envanterde ara
                    result = InventoryUtility.findItemInInventory(Items.EXPERIENCE_BOTTLE);
                }
            } else {
                // Öncelik vermeden tüm envanterde ara
                result = InventoryUtility.findItemInInventory(Items.EXPERIENCE_BOTTLE);
            }
            
            if (!result.found()) {
                if (notifications.getValue()) {
                    sendMessage(isRu() ? "Опыт не найден!" : "XP Şişesi bulunamadı!");
                }
                if (autoDisable.getValue() || autoDisableEmpty.getValue()) {
                    toggle();
                }
                return;
            }
            
            // Ana işlemi gerçekleştir
            for (int i = 0; i < frequency.getValue(); i++) {
                if (silentMode.getValue()) {
                    if (fakeSwitch.getValue() && result.slot() < 9) {
                        // Fake switch özelliği aktifse ve XP hotbarda
                        useExpBottleFakeSwitch(result);
                    } else {
                        useExpBottleSilent(result);
                    }
                } else {
                    useExpBottle(result);
                }
            }
            
            useTimer.reset();
        }
    }
    
    private static class CheckArmorResult {
        boolean shouldStop;
        float highestDurability;
        
        CheckArmorResult(boolean shouldStop, float highestDurability) {
            this.shouldStop = shouldStop;
            this.highestDurability = highestDurability;
        }
    }
    
    private CheckArmorResult checkArmorDurability() {
        boolean shouldStop = false;
        float highestDurabilityFound = 0f;
        
        // Zırh parçalarının durabilite değerlerini takip etmek için
        float[] durabilities = new float[4]; // 0: kask, 1: göğüslük, 2: pantolon, 3: botlar
        int validArmorCount = 0;
        
        // Tüm zırh parçalarını kontrol et
        for (int i = 0; i < 4; i++) {
            ItemStack armorPiece = mc.player.getInventory().getArmorStack(i);
            if (armorPiece.isEmpty() || armorPiece.getMaxDamage() == 0) {
                durabilities[i] = 0f;
                continue;
            }
            
            validArmorCount++;
            
            // Zırh dayanıklılık yüzdesi
            float durabilityPercent = 100f * (1f - (float) armorPiece.getDamage() / armorPiece.getMaxDamage());
            durabilities[i] = durabilityPercent;
            
            // En yüksek dayanıklılık değerini güncelle
            if (durabilityPercent > highestDurabilityFound) {
                highestDurabilityFound = durabilityPercent;
            }
            
            // Zırh tipine göre kontrol
            switch (stopArmorType.getValue()) {
                case AnyArmor:
                    // Herhangi bir zırh parçası limitin üzerindeyse dur
                    if (durabilityPercent >= stopAtDurability.getValue()) {
                        shouldStop = true;
                    }
                    break;
                case Helmet:
                    // Sadece kask için kontrol et (i == 3 kask)
                    if (i == 3 && durabilityPercent >= stopAtDurability.getValue()) {
                        shouldStop = true;
                    }
                    break;
                case Chestplate:
                    // Sadece göğüslük için kontrol et (i == 2 göğüslük)
                    if (i == 2 && durabilityPercent >= stopAtDurability.getValue()) {
                        shouldStop = true;
                    }
                    break;
                case Leggings:
                    // Sadece pantolon için kontrol et (i == 1 pantolon)
                    if (i == 1 && durabilityPercent >= stopAtDurability.getValue()) {
                        shouldStop = true;
                    }
                    break;
                case Boots:
                    // Sadece botlar için kontrol et (i == 0 botlar)
                    if (i == 0 && durabilityPercent >= stopAtDurability.getValue()) {
                        shouldStop = true;
                    }
                    break;
                case AllArmor:
                    // Bu durumda, all armor durumunu tüm kontroller bittikten sonra değerlendirilecek
                    break;
            }
        }
        
        // AllArmor modu için kontrol - tüm zırh parçaları limiti geçmişse dur
        if (stopArmorType.getValue() == StopArmorType.AllArmor && validArmorCount > 0) {
            boolean allAboveLimit = true;
            for (int i = 0; i < 4; i++) {
                if (durabilities[i] == 0f) continue; // Boş zırh slotlarını atla
                if (durabilities[i] < stopAtDurability.getValue()) {
                    allAboveLimit = false;
                    break;
                }
            }
            shouldStop = allAboveLimit;
        }
        
        return new CheckArmorResult(shouldStop, highestDurabilityFound);
    }
    
    /**
     * Görünürde el değiştirip, aslında değiştirmeden XP şişesi kullanır.
     * Bu metot, ekranda eline XP şişesi aldığı görünür ama aslında slot değişmez.
     */
    private void useExpBottleFakeSwitch(SearchInvResult result) {
        if (result.slot() >= 9) return; // Sadece hotbardaki XP şişeleri için
        
        float originalPitch = mc.player.getPitch();
        mc.player.setPitch(pitch.getValue());
        
        performingAction = true;
        
        // Mevcut slotu kaydet (ilk kez yapıyorsak)
        if (lastSlot == -1) {
            lastSlot = mc.player.getInventory().selectedSlot;
        }
        
        // Fake slot değişimi - sadece paket gönderir, gerçekte değişmez
        // Görünürde slot değişimi için paket gönder
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(result.slot()));
        
        // XP şişesini kullan
        InteractionUtility.sendSequencedPacket(id -> 
            new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, mc.player.getYaw(), mc.player.getPitch())
        );
        
        // El sallamayı gerçekleştir (eğer ayar açıksa)
        if (swingHand.getValue()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
        
        // Count modunda kullanım sayısını artır
        if (mode.is(Mode.Count)) {
            expUseCount++;
        }
        
        // Görünürde orijinal slota geri dön
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(lastSlot));
        
        // İşlem bitti
        mc.player.setPitch(originalPitch);
        Managers.ASYNC.run(() -> performingAction = false, 100);
    }
    
    private void useExpBottleSilent(SearchInvResult result) {
        // Silently kullanım - hiç el değiştirmeden
        float originalPitch = mc.player.getPitch();
        mc.player.setPitch(pitch.getValue());
        
        performingAction = true;
        
        int originalSlot = -1;
        int xpSlot = result.slot();
        
        // Mevcut slotu kaydet (eğer gerekirse)
        if (!noHandChange.getValue() && result.slot() < 9) {
            originalSlot = mc.player.getInventory().selectedSlot;
        }
        
        // Gerekirse slot değiştir
        if (!noHandChange.getValue() && result.slot() < 9 && result.slot() != mc.player.getInventory().selectedSlot) {
            InventoryUtility.switchToSilent(result.slot());
        }
        
        // XP şişesini kullan
        InteractionUtility.sendSequencedPacket(id -> 
            new PlayerInteractItemC2SPacket(
                (!noHandChange.getValue() && result.slot() < 9) ? Hand.MAIN_HAND : 
                (result.slot() == 999 ? Hand.OFF_HAND : Hand.MAIN_HAND),
                id, mc.player.getYaw(), mc.player.getPitch())
        );
        
        // El sallamayı gerçekleştir (eğer ayar açıksa)
        if (swingHand.getValue()) {
            mc.player.swingHand((!noHandChange.getValue() && result.slot() < 9) ? Hand.MAIN_HAND : 
                (result.slot() == 999 ? Hand.OFF_HAND : Hand.MAIN_HAND));
        }
        
        // Count modunda kullanım sayısını artır
        if (mode.is(Mode.Count)) {
            expUseCount++;
        }
        
        // Orijinal slota geri dön (eğer gerekirse)
        if (!noHandChange.getValue() && originalSlot != -1) {
            InventoryUtility.switchToSilent(originalSlot);
        }
        
        // İşlem bitti
        mc.player.setPitch(originalPitch);
        Managers.ASYNC.run(() -> performingAction = false, 100);
    }
    
    private void useExpBottle(SearchInvResult result) {
        // Normal modda oyuncunun bakış açısını değiştir
        if (mode.is(Mode.Normal)) {
            float originalPitch = mc.player.getPitch();
            mc.player.setPitch(pitch.getValue());
            
            // İşlem yapılıyor
            performingAction = true;
            
            // Slot değiştirme
            int prevSlot = mc.player.getInventory().selectedSlot;
            if (silentSwitch.getValue()) {
                if (result.slot() < 9) {
                    InventoryUtility.switchToSilent(result.slot());
                } else {
                    int nearestSlot = findNearestEmptySlot();
                    if (nearestSlot != -1) {
                        // Silent slot swap
                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, result.slot(), 
                                nearestSlot, net.minecraft.screen.slot.SlotActionType.SWAP, mc.player);
                        
                        // Use XP bottle
                        InteractionUtility.sendSequencedPacket(id -> 
                            new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, mc.player.getYaw(), mc.player.getPitch())
                        );
                        
                        // Slot geri al
                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, result.slot(), 
                                nearestSlot, net.minecraft.screen.slot.SlotActionType.SWAP, mc.player);
                    }
                }
            } else {
                if (result.slot() < 9) {
                    InventoryUtility.switchTo(result.slot());
                    
                    // Use XP bottle
                    InteractionUtility.sendSequencedPacket(id -> 
                        new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, mc.player.getYaw(), mc.player.getPitch())
                    );
                    
                    // Restore original slot
                    InventoryUtility.switchTo(prevSlot);
                } else {
                    // Non-silent inventory usage
                    int nearestSlot = findNearestEmptySlot();
                    if (nearestSlot != -1) {
                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, result.slot(), 
                                nearestSlot, net.minecraft.screen.slot.SlotActionType.SWAP, mc.player);
                        
                        InteractionUtility.sendSequencedPacket(id -> 
                            new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, mc.player.getYaw(), mc.player.getPitch())
                        );
                        
                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, result.slot(), 
                                nearestSlot, net.minecraft.screen.slot.SlotActionType.SWAP, mc.player);
                    }
                }
            }
            
            // El sallamayı gerçekleştir (eğer ayar açıksa)
            if (swingHand.getValue()) {
                mc.player.swingHand(Hand.MAIN_HAND);
            }
            
            // Count modunda kullanım sayısını artır
            if (mode.is(Mode.Count)) {
                expUseCount++;
            }
            
            // İşlem bitti
            mc.player.setPitch(originalPitch);
            Managers.ASYNC.run(() -> performingAction = false, 100);
        }
    }
    
    private int findNearestEmptySlot() {
        // Hotbarda boş slot ara
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                return i;
            }
        }
        
        // Boş slot bulunamadı, oyuncunun mevcut slotunu kullan
        return mc.player.getInventory().selectedSlot;
    }
} 