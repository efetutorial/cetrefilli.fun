package thunder.hack.features.modules.client;

import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.AddServerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import thunder.hack.ThunderHack;
import thunder.hack.core.Managers;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;
import thunder.hack.utility.discord.DiscordEventHandlers;
import thunder.hack.utility.discord.DiscordRPC;
import thunder.hack.utility.discord.DiscordRichPresence;

import java.io.*;
import java.util.Objects;

import static thunder.hack.features.modules.client.ClientSettings.isRu;

public final class RPC extends Module {
    private static final DiscordRPC rpc = DiscordRPC.INSTANCE;
    public static Setting<Mode> mode = new Setting<>("Picture", Mode.Recode);
    public static Setting<Boolean> showIP = new Setting<>("ShowIP", true);
    public static Setting<sMode> smode = new Setting<>("StateMode", sMode.Stats);
    public static Setting<String> state = new Setting<>("State", "Beta? Recode? NextGen?");
    public static Setting<Boolean> nickname = new Setting<>("Nickname", true);
    public static DiscordRichPresence presence = new DiscordRichPresence();
    public static boolean started;
    static String String1 = "none";
    private final Timer timer_delay = new Timer();
    private static Thread thread;
    String slov;
    String[] rpc_perebor_en = {"Parkour", "Reporting cheaters", "Touching grass", "Asks how to bind", "Reporting bugs", "Watching Kilab"};
    String[] rpc_perebor_ru = {"Паркурит", "Репортит читеров", "Трогает траву", "Спрашивает как забиндить", "Репортит баги", "Смотрит Флюгера"};
    int randomInt;

    public RPC() {
        super("DiscordRPC", Category.CLIENT);
    }

    public String read() {
        try {
            File file = new File("cetrefilli.fun/misc/RPC.txt");
            if (!file.exists()) return null;
            FileReader fileReader = new FileReader(file);
            StringBuilder sb = new StringBuilder();
            int i;
            while ((i = fileReader.read()) != -1)
                sb.append((char) i);
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public void write(String text) {
        try {
            File file = new File("cetrefilli.fun/misc/RPC.txt");
            if (!file.exists())
                file.createNewFile();
            FileWriter fileWriter = new FileWriter(file);
            fileWriter.write(text);
            fileWriter.flush();
            fileWriter.close();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDisable() {
        started = false;
        if (thread != null && !thread.isInterrupted()) {
            thread.interrupt();
        }
        rpc.Discord_Shutdown();
    }

    @Override
    public void onUpdate() {
        startRpc();
    }

    public void startRpc() {
        if (isDisabled()) return;
        if (!started) {
            started = true;
            DiscordEventHandlers handlers = new DiscordEventHandlers();
            rpc.Discord_Initialize("1345427056065908826", handlers, true, "");
            presence.startTimestamp = (System.currentTimeMillis() / 1000L);
            presence.largeImageText = "v" + ThunderHack.VERSION + " [" + ThunderHack.GITHUB_HASH + "]";
            rpc.Discord_UpdatePresence(presence);

            thread = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    rpc.Discord_RunCallbacks();
                    presence.details = getDetails();

                    switch (smode.getValue()) {
                        case Stats ->
                                presence.state = "Hacks: " + Managers.MODULE.getEnabledModules().size() + " / " + Managers.MODULE.modules.size();
                        case Custom -> presence.state = state.getValue();
                        case Version -> presence.state = "v" + ThunderHack.VERSION +" for mc 1.21";
                    }

                    if (nickname.getValue()) {
                        presence.smallImageText = "logged as - " + mc.getSession().getUsername();
                        presence.smallImageKey = "https://minotar.net/helm/" + mc.getSession().getUsername() + "/100.png";
                    } else {
                        presence.smallImageText = "";
                        presence.smallImageKey = "";
                    }

                    presence.button_label_1 = "Download";
                    presence.button_url_1 = "https://cetrefilli.fun";

                    switch (mode.getValue()) {
                        case Recode -> presence.largeImageKey = "https://i.imgur.com/l9dnzaU.png";
                        case MegaCute ->
                                presence.largeImageKey = "https://i.imgur.com/l9dnzaU.png";
                        case Custom -> {
                            String data = read();
                            if (data != null) {
                                String[] parts = data.split("SEPARATOR");
                                presence.largeImageKey = parts[0];
                                if (parts.length > 1 && !Objects.equals(parts[1], "none")) {
                                    presence.smallImageKey = parts[1];
                                }
                            }
                        }
                    }
                    rpc.Discord_UpdatePresence(presence);
                    try {
                        Thread.sleep(2000L);
                    } catch (InterruptedException ignored) {
                    }
                }
            }, "TH-RPC-Handler");
            thread.start();
        }
    }

    private String getDetails() {
        String result = "";

        if (mc.currentScreen instanceof MultiplayerScreen || mc.currentScreen instanceof AddServerScreen || mc.currentScreen instanceof TitleScreen) {
            if(timer_delay.passedMs(60 * 1000)){
                randomInt = (int)(Math.random() * (5 - 0 + 1) + 0);
                slov = isRu() ? rpc_perebor_ru[randomInt] : rpc_perebor_en[randomInt];
                timer_delay.reset();
            }
            result = slov;
        } else if (mc.getCurrentServerEntry() != null) {
            result = isRu() ? (showIP.getValue() ? "Играет на " + mc.getCurrentServerEntry().address : "Играет на сервере") : (showIP.getValue() ? "Playing on " + mc.getCurrentServerEntry().address : "Playing on server");
        } else if (mc.isInSingleplayer()) {
            result = isRu() ? "Читерит в одиночке" : "SinglePlayer hacker";
        }
        return result;
    }

    public enum Mode {Custom, MegaCute, Recode}

    public enum sMode {Custom, Stats, Version}
}
