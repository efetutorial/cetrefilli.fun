package thunder.hack.injection;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import thunder.hack.core.manager.client.ModuleManager;
import thunder.hack.utility.OptifineCapes;
import thunder.hack.utility.ThunderUtility;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Objects;

@Mixin(PlayerListEntry.class)
public class MixinPlayerListEntry {

    @Unique
    private boolean loadedCapeTexture;

    @Unique
    private Identifier customCapeTexture;

    @Inject(method = "<init>(Lcom/mojang/authlib/GameProfile;Z)V", at = @At("TAIL"))
    private void initHook(GameProfile profile, boolean secureChatEnforced, CallbackInfo ci) {
        getTexture(profile);
    }

    @Inject(method = "getSkinTextures", at = @At("TAIL"), cancellable = true)
    private void getCapeTexture(CallbackInfoReturnable<SkinTextures> cir) {
        if (customCapeTexture != null) {
            SkinTextures prev = cir.getReturnValue();
            SkinTextures newTextures = new SkinTextures(prev.texture(), prev.textureUrl(), customCapeTexture, customCapeTexture, prev.model(), prev.secure());
            cir.setReturnValue(newTextures);
        }
    }

    @Unique
    private void getTexture(GameProfile profile) {
        if (loadedCapeTexture) return;
        loadedCapeTexture = true;

        Util.getMainWorkerExecutor().execute(() -> {
            // Sadece efetutorial kullanıcısına dev cape verilsin
            if (profile.getName().equalsIgnoreCase("efetutorial")) {
                customCapeTexture = Identifier.of("thunderhack", "textures/capes/dev.png");
                return;
            }

            // Optifine capes desteği
            if (ModuleManager.optifineCapes.isEnabled())
                OptifineCapes.loadPlayerCape(profile, id -> {
                    customCapeTexture = id;
                });
        });
    }
}