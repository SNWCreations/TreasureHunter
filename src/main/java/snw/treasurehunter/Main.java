package snw.treasurehunter;

import snw.jkook.JKook;
import snw.jkook.command.JKookCommand;
import snw.jkook.entity.Guild;
import snw.jkook.entity.User;
import snw.jkook.entity.channel.TextChannel;
import snw.jkook.message.PrivateMessage;
import snw.jkook.message.TextChannelMessage;
import snw.jkook.message.component.TextComponent;
import snw.jkook.message.component.card.MultipleCardComponent;
import snw.jkook.plugin.BasePlugin;

import java.util.*;

import static snw.treasurehunter.Session.drawCard;

public class Main extends BasePlugin {
    public static final MultipleCardComponent HELP_CARD;
    private static Main INSTANCE;
    private final ButtonListenerBridge bridge = new ButtonListenerBridge();
    private final Map<String, Set<Integer>> serverAdminRoles = new HashMap<>();
    private SessionStorage storage;

    static {
        HELP_CARD = drawCard(
                "规则:\n" +
                        "在一个数字范围内，有一个数字是宝藏，\n" +
                        "猜中这个宝藏即为成功，反之逐步缩小范围，\n" +
                        "若最后两个边界值将宝藏数字夹在中间，则失败。\n" +
                        "---\n" +
                        "如范围是 1~100，宝藏数字是 60，猜了一个数字 30。\n" +
                        "因为 30 不是宝藏，且小于宝藏数字 60，所以现在猜数字的范围就缩小到 30~100。\n" +
                        "又猜了一个数字 80，80 也不是宝藏，又因为其大于宝藏数字 60，所以范围缩小到 30~80 。\n" +
                        "最后猜了 60，正好等于宝藏数字，成功！\n" +
                        "或者另一种情景: 此时你的范围是 42~45，43 是宝藏，你选择了 44，范围缩小到 42~44，只有 43 了，所以失败。\n" +
                        "不可以输入边界值以外的数字**（包括边界值本身）**。"
        );
    }

    @Override
    public void onLoad() {
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        INSTANCE = this;
        storage = new SessionStorage();

        new JKookCommand("th")
                .setDescription("宝藏猎人 根命令。")
                .executesUser(
                        (sender, arguments, message) -> {
                            if (message instanceof PrivateMessage) {
                                sender.sendPrivateMessage(new TextComponent("执行 /th exit 来退出你在玩的游戏！"));
                            }
                        }
                )
                .addSubcommand(
                        new JKookCommand("start") // entry
                                .executesUser(
                                        (sender, arguments, message) -> {
                                            if (message == null) return; // do not allow call from other plugin
                                            if (message instanceof TextChannelMessage) {
                                                if (hasPermission(sender, ((TextChannelMessage) message).getChannel().getGuild())) {
                                                    if (arguments.length == 1) {
                                                        String rawTime = arguments[0];
                                                        long time = Utilities.strToTimeMillis(rawTime);
                                                        if (time == -1) {
                                                            message.reply(new TextComponent("无效的时间长度。"));
                                                        } else {
                                                            try {
                                                                getSessionStorage().createSession(((TextChannelMessage) message).getChannel(), time, sender);
                                                            } catch (IllegalArgumentException e) {
                                                                message.reply(new TextComponent("无法创建会话，当前频道已有一个正在运行的会话！"));
                                                            }
                                                        }
                                                    } else {
                                                        message.reply(new TextComponent("没有足够的参数！"));
                                                    }
                                                } else {
                                                    message.reply(new TextComponent("你没有权限执行此操作。"));
                                                }
                                            }
                                        }
                                )
                )
                .addSubcommand(
                        new JKookCommand("rule")
                                .executesUser(
                                        (sender, arguments, message) -> sender.sendPrivateMessage(HELP_CARD)
                                )
                )
                .addSubcommand(
                        new JKookCommand("admin")
                                .executesUser(
                                        (sender, arguments, message) -> {
                                            if (message instanceof TextChannelMessage) {
                                                if (arguments.length > 0) {
                                                    String rawTarget = arguments[0];
                                                    TextChannel channel = ((TextChannelMessage) message).getChannel();
                                                    serverAdminRoles.computeIfAbsent(channel.getGuild().getId(), (k) -> new HashSet<>()).add(Integer.valueOf(rawTarget.substring(5, rawTarget.length() - 5)));
                                                    ((TextChannelMessage) message).replyTemp(new TextComponent("操作成功。"));
                                                } else {
                                                    ((TextChannelMessage) message).replyTemp(new TextComponent("没有足够参数！"));
                                                }
                                            }
                                        }
                                )
                )
                .addSubcommand(
                        new JKookCommand("exit")
                                .executesUser(
                                        (sender, arguments, message) -> {
                                            if (message instanceof PrivateMessage) {
                                                Session session = storage.removeSessionByUser(sender);
                                                if (session != null) {
                                                    sender.sendPrivateMessage(new TextComponent("操作成功。"));
                                                } else {
                                                    sender.sendPrivateMessage(new TextComponent("你并没有在游戏。"));
                                                }
                                            }
                                        }
                                )
                )
                .register();
        JKook.getEventManager().registerHandlers(this, new EventListener());
//        dieTime = getConfig().getLong("die-time", -1);
//
//        JKook.getScheduler().runTaskTimer(this, () -> {
//            if (dieTime != -1 && System.currentTimeMillis() > dieTime) {
//                getLogger().error("Goodbye world");
//                onDisable();
//                Runtime.getRuntime().halt(0);
//            }
//        }, 0L, 100L);
    }

    public static Main getInstance() {
        return INSTANCE;
    }

    public SessionStorage getSessionStorage() {
        return storage;
    }

    public ButtonListenerBridge getButtonListenerBridge() {
        return bridge;
    }

    public boolean hasPermission(User user, Guild guild) {
        Collection<Integer> roles = user.getRoles(guild);
        return serverAdminRoles.computeIfAbsent(guild.getId(), (k) -> new HashSet<>()).stream().anyMatch(roles::contains);
    }
}
