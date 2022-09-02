package snw.treasurehunter;

import snw.jkook.JKook;
import snw.jkook.config.file.YamlConfiguration;
import snw.jkook.entity.User;
import snw.jkook.entity.channel.TextChannel;
import snw.jkook.message.TextChannelMessage;
import snw.jkook.message.component.FileComponent;
import snw.jkook.message.component.TextComponent;
import snw.jkook.message.component.card.CardBuilder;
import snw.jkook.message.component.card.MultipleCardComponent;
import snw.jkook.message.component.card.Size;
import snw.jkook.message.component.card.Theme;
import snw.jkook.message.component.card.element.ButtonElement;
import snw.jkook.message.component.card.element.MarkdownElement;
import snw.jkook.message.component.card.element.PlainTextElement;
import snw.jkook.message.component.card.module.*;
import snw.jkook.scheduler.JKookRunnable;
import snw.jkook.scheduler.Task;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;

public class ChannelSession {
    private static final SimpleDateFormat SDF;

    private final SessionStorage storage;
    private final String channelId;
    private final long endTime;
    private final ChannelBoard board;
    private final String starterId;
    private final Set<User> players = new HashSet<>();
    private final String startButtonId;
    private final String deleteButtonId;
    private final String exportButtonId;
    private TextChannelMessage starterMsg;
    private TextChannelMessage boardMsg;
    private boolean init = false;
    private boolean dead = false;
    private Task naturalDieTask;
    private File sessionFile;
    private final UUID uuid;

    static {
        SDF = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
        SDF.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
    }

    public ChannelSession(SessionStorage storage, String channelId, long sessionAliveLength, User sender) {
        this.storage = storage;
        this.channelId = channelId;
        this.uuid = UUID.randomUUID();
        this.board = new ChannelBoard(uuid);
        this.startButtonId = UUID.randomUUID().toString();
        this.deleteButtonId = UUID.randomUUID().toString();
        this.exportButtonId = UUID.randomUUID().toString();
        this.starterId = sender.getId();
        this.endTime = System.currentTimeMillis() + sessionAliveLength;
        init(sessionAliveLength);
        createSessionFile();
    }

    private void createSessionFile() {
        File sessionFolder = Main.getInstance().getSessionFolder();
        File file;
        do {
            file = new File(sessionFolder, this.uuid+ ".yml");
        } while (file.exists());
        try {
				    file.createNewFile();
				} catch (Exception e) {
				    throw new RuntimeException(e);
				}
        YamlConfiguration data = new YamlConfiguration();
        data.set("uuid", uuid.toString());
        data.set("channelId", channelId);
        data.set("boardMsgId", boardMsg.getId());
        data.set("starterMsgId", starterMsg.getId());
        data.set("starterId", starterId);
        data.set("startButtonId", startButtonId);
        data.set("deleteButtonId", deleteButtonId);
        data.set("exportButtonId", exportButtonId);
        data.set("endTime", endTime);
        try {
            data.save(file);
        } catch (IOException e) {
            throw new RuntimeException("无法写入会话数据！", e);
        }
        sessionFile = file;
    }

    public ChannelSession(SessionStorage storage, YamlConfiguration data, File file) {
        this.storage = storage;
        this.channelId = data.getString("channelId", "");
        String boardMsgId = data.getString("boardMsgId", "");
        this.uuid = UUID.fromString(data.getString("uuid", ""));
        this.board = new ChannelBoard(uuid);
        board.setBoardMessageId(boardMsgId);
        this.starterMsg = JKook.getCore().getUnsafe().getTextChannelMessage(data.getString("starterMsgId", ""));
        this.boardMsg = JKook.getCore().getUnsafe().getTextChannelMessage(boardMsgId);
        this.starterId = data.getString("starterId", "");
        this.startButtonId = data.getString("startButtonId", "");
        this.deleteButtonId = data.getString("deleteButtonId", "");
        this.exportButtonId = data.getString("exportButtonId", "");
        this.endTime = data.getLong("endTime");
        registerButtonEvents();
        init = true;
        sessionFile = file;
        File dataFile = new File(file.getParentFile(), uuid + ".txt");
        if (dataFile.exists()) {
            board.load(dataFile);
        }
    }

    public void init(long sessionAliveLength) {
        if (init) {
            throw new IllegalStateException();
        }
        TextChannel channel = (TextChannel) JKook.getHttpAPI().getChannel(channelId);
        starterMsg = JKook.getCore().getUnsafe().getTextChannelMessage(channel.sendComponent(renderStartCard(), null, null));
        String boardMsgId = channel.sendComponent(board.drawCard(), null, null);
        boardMsg = JKook.getCore().getUnsafe().getTextChannelMessage(boardMsgId);
        board.setBoardMessageId(boardMsgId);
        naturalDieTask = new JKookRunnable() {
            @Override
            public void run() {
                markDie0();
                upload(JKook.getHttpAPI().getUser(starterId));
                new JKookRunnable() {
                    @Override
                    public void run() {
                        Main.getInstance().getButtonListenerBridge().remove(exportButtonId);
                    }
                }.runTaskLater(Main.getInstance(), 1000L * 60 * 60 * 3);
            }
        }.runTaskLater(Main.getInstance(), sessionAliveLength);
        init = true; // here we go!
    }

    public void addPlayer(User user) {
        players.add(user);
    }

    // Return value - if this session was completed successfully
    public boolean completeSession(Session session) {
        if (dead) {
            return false;
        }
        board.addRecord(session);
        Main.getInstance().getSessionStorage().removeSessionByUser(session.getUser());
        return true;
    }

    public void markDie() {
        markDie0();
        Main.getInstance().getButtonListenerBridge().remove(exportButtonId);
        if (naturalDieTask != null) {
            if (!naturalDieTask.isCancelled() && !naturalDieTask.isExecuted()) {
                naturalDieTask.cancel();
            }
        }
    }

    private void markDie0() {
        dead = true;
        for (User player : players) {
            Main.getInstance().getSessionStorage().removeSessionByUser(player);
        }
        Main.getInstance().getButtonListenerBridge().remove(startButtonId);
        Main.getInstance().getButtonListenerBridge().remove(deleteButtonId);
        storage.removeSession(channelId);
        new File(sessionFile.getParentFile(), sessionFile.getName().replace(".yml", ".txt")).delete();
        if (sessionFile != null) {
            //noinspection ResultOfMethodCallIgnored
            sessionFile.delete();
        }
    }

    private void upload(User target) {
        String filename = SDF.format(new Date(System.currentTimeMillis())).replace(':', '_') + " - " + JKook.getHttpAPI().getChannel(channelId).getName() + ".txt";
        File file = new File(Main.getInstance().getDataFolder(), filename);
        String export = board.export();
        try (FileWriter fileWriter = new FileWriter(file)) {
            fileWriter.write(export);
        } catch (IOException e) {
            Main.getInstance().getLogger().error("Unable to write data.");
            target.sendPrivateMessage(new TextComponent("抱歉！数据写入失败！我们会尝试将日志发送给您，您可以从日志中获取数据！"));
            Main.getInstance().getLogger().error(export); // make sure the data is still available through the log
            File logsDir = new File(new File("."), "logs");
            File logfile = new File(logsDir, "latest.log");
            String logUrl = JKook.getHttpAPI().uploadFile(logfile);
            target.sendPrivateMessage(new FileComponent(logUrl, filename, -1, FileComponent.Type.FILE));
            return;
        }
        String s = JKook.getHttpAPI().uploadFile(file);
        target.sendPrivateMessage(new FileComponent(s, filename, -1, FileComponent.Type.FILE));
        file.delete();
    }

    private MultipleCardComponent renderStartCard() {
        MultipleCardComponent card = new CardBuilder()
                .setTheme(Theme.NONE)
                .setSize(Size.LG)
                .addModule(
                        new HeaderModule("活动开始！")
                )
                .addModule(DividerModule.INSTANCE)
                .addModule(
                        new SectionModule(
                                new MarkdownElement(String.format("发起人: (met)%s(met)", starterId)), null, null
                        )
                )
                .addModule(
                        new CountdownModule(CountdownModule.Type.DAY, endTime)
                )
                .addModule(
                        new ContextModule(
                                Collections.singletonList(
                                        new PlainTextElement(
                                                String.format("本次活动将在 %s 结束", SDF.format(new Date(endTime)))
                                        )
                                )
                        )
                )
                .addModule(DividerModule.INSTANCE)
                .addModule(
                        new ActionGroupModule.Builder()
                                .add(
                                        new ButtonElement(
                                                Theme.SUCCESS, startButtonId, ButtonElement.EventType.RETURN_VAL, new PlainTextElement("参与")
                                        )
                                )
                                .add(
                                        new ButtonElement(
                                                Theme.DANGER, deleteButtonId, ButtonElement.EventType.RETURN_VAL, new PlainTextElement("删除")
                                        )
                                )
                                .add(
                                        new ButtonElement(
                                                Theme.DANGER, exportButtonId, ButtonElement.EventType.RETURN_VAL, new PlainTextElement("导出")
                                        )
                                )
                                .build()
                )
                .build();
        registerButtonEvents();
        return card;
    }

    public void registerButtonEvents() {
        Main.getInstance().getButtonListenerBridge().add(
                startButtonId,
                wrap(user -> {
//                    if (board.hasPlayed(user)) {
//                        ((TextChannel) JKook.getHttpAPI().getChannel(channelId)).sendComponent(
//                                new TextComponent("你已经参与过本次活动！"), null, user
//                        );
//                    } else {
                        Main.getInstance().getSessionStorage().createSession(user, channelId);
//                    }
                })
        );
        Main.getInstance().getButtonListenerBridge().add(
                deleteButtonId,
                wrap(user -> {
                    if (Main.getInstance().hasPermission(user, JKook.getHttpAPI().getChannel(channelId).getGuild())) {

                        markDie();
                        starterMsg.delete();
                        boardMsg.delete();
                        ((TextChannel) JKook.getHttpAPI().getChannel(channelId)).sendComponent(
                                new TextComponent("操作成功。"), null, user
                        );
                    } else {
                        ((TextChannel) JKook.getHttpAPI().getChannel(channelId)).sendComponent(
                                new TextComponent("你没有权限执行此操作。"), null, user
                        );
                    }
                })

        );
        Main.getInstance().getButtonListenerBridge().add(
                exportButtonId,
                wrap(user -> {
                    if (Main.getInstance().hasPermission(user, JKook.getHttpAPI().getChannel(channelId).getGuild())) {
                        upload(user);
//                        String filename = SDF.format(new Date(System.currentTimeMillis() - aliveLength)).replace(':', '_') + ".txt";
//                        File file = new File(Main.getInstance().getDataFolder(), filename);
//                        try (FileWriter fileWriter = new FileWriter(file)) {
//                            fileWriter.write(board.export());
//                        } catch (IOException e) {
//                            ((TextChannel) JKook.getHttpAPI().getChannel(channelId)).sendComponent(
//                                    new TextComponent("操作失败，写入文件时发生异常。"), null, user
//                            );
//                        }
//                        String s = JKook.getHttpAPI().uploadFile(file);
//                        // //noinspection ResultOfMethodCallIgnored
//                        // file.delete();
//                        user.sendPrivateMessage(new FileComponent(s, filename, -1, FileComponent.Type.FILE));
                    } else {
                        ((TextChannel) JKook.getHttpAPI().getChannel(channelId)).sendComponent(
                                new TextComponent("你没有权限执行此操作。"), null, user
                        );
                    }
                })
        );
    }

    private Consumer<User> wrap(Consumer<User> beWrapped) {
        return user -> {
            if (dead) {
                ((TextChannel) JKook.getHttpAPI().getChannel(channelId)).sendComponent(
                        new TextComponent("此活动已经失效。"), null, user
                );
            } else {
                beWrapped.accept(user);
            }
        };
    }
}
