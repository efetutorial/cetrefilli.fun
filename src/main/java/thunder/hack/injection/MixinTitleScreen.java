package thunder.hack.injection;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import thunder.hack.ThunderHack;
import thunder.hack.gui.misc.DialogScreen;
import thunder.hack.gui.mainmenu.MainMenuScreen;
import thunder.hack.utility.render.TextureStorage;
import thunder.hack.core.manager.client.ModuleManager;
import thunder.hack.features.modules.client.ClientSettings;

import java.net.URI;

import static thunder.hack.features.modules.Module.mc;
import static thunder.hack.features.modules.client.ClientSettings.isRu;
import static thunder.hack.features.modules.client.ClientSettings.isTr;

@Mixin(TitleScreen.class)
public class MixinTitleScreen extends Screen {
    
    protected MixinTitleScreen(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    public void postInitHook(CallbackInfo ci) {
        if (ModuleManager.clickGui.getBind().getKey() == -1) {
            DialogScreen dialogScreen = new DialogScreen(
                    TextureStorage.cutie,
                    "Cetrefilli.fun'u indirdiğiniz için teşekkürler!",
                    "Hile menüsü P tuşu ile açılabilir",
                    "Minecraft'a gir",
                    "Minecraft'tan çık",
                    () -> {
                        ModuleManager.clickGui.setBind(InputUtil.fromTranslationKey("key.keyboard.p").getCode(), false, false);
                        // mc.setScreen(MainMenuScreen.getInstance());
                    },
                    () -> {
                        ModuleManager.clickGui.setBind(InputUtil.fromTranslationKey("key.keyboard.p").getCode(), false, false);
                        mc.stop();
                    }
            );
            ClientSettings.language.setValue(ClientSettings.Language.TR);
            mc.setScreen(dialogScreen);
        }

        if (ThunderHack.isOutdated && !FabricLoader.getInstance().isDevelopmentEnvironment()) {
            mc.setScreen(new ConfirmScreen(
                    confirm -> {
                        if (confirm) Util.getOperatingSystem().open(URI.create("https://github.com/Pan4ur/ThunderHack-Recode/releases/download/latest/thunderhack-1.7.jar/"));
                        else mc.stop();
                    },
                    Text.of(Formatting.RED + "You are using an outdated version of Cetrefilli.fun"), Text.of("Please update to the latest release"), Text.of("Download"), Text.of("Quit Game")));
        }
    }
    
    // 1.21'de splashText alanı değiştiği için bu şekilde kullanamıyoruz
    // Alternatif olarak, bir render metodu ekleyerek kendi mesajımızı gösterebiliriz
    @Inject(method = "render", at = @At("RETURN"))
    public void renderSplashText(net.minecraft.client.gui.DrawContext drawContext, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        drawContext.drawTextWithShadow(
            mc.textRenderer,
            Formatting.GOLD + "We love cetrefilli.fun!",
            8,
            8,
            0xFFFFFF
        );
    }
}
