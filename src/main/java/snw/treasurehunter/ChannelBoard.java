package snw.treasurehunter;

import snw.jkook.JKook;
import snw.jkook.entity.User;
import snw.jkook.message.component.card.CardBuilder;
import snw.jkook.message.component.card.MultipleCardComponent;
import snw.jkook.message.component.card.Size;
import snw.jkook.message.component.card.Theme;
import snw.jkook.message.component.card.element.PlainTextElement;
import snw.jkook.message.component.card.module.ContextModule;
import snw.jkook.message.component.card.module.DividerModule;
import snw.jkook.message.component.card.module.HeaderModule;
import snw.jkook.message.component.card.module.SectionModule;
import snw.jkook.util.Validate;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class ChannelBoard {
    private final List<SessionRecord> records = new ArrayList<>();
    private final UUID uuid;
    private String boardMessageId;

    public ChannelBoard(UUID uuid) {
        this.uuid = uuid;
    }

    public synchronized void addRecord(Session session) {
        Iterator<SessionRecord> iterator = records.iterator();
        while (iterator.hasNext()) {
            SessionRecord sessionRecord = iterator.next();
            if (Objects.equals(sessionRecord.getUserId(), session.getUser().getId())) {
                if (session.getSteps() > sessionRecord.getSteps()) {
                    // if this record is not better than the record in this board, it is not necessary to create record for this session
                    return;
                } else if (session.getSteps() < sessionRecord.getSteps()) {
                    iterator.remove(); // remove the old record
                } else {
                    return; // it is not necessary to write the same result!
                }
            }
        }
        SessionRecord record = new SessionRecord(session.getUser().getId(), session.getUser().getName(), session.getSteps());
        records.add(record);
        update();
        File file = new File(Main.getInstance().getSessionFolder(), uuid + ".txt");
        try (FileWriter fileWriter = new FileWriter(file, true)) {
            fileWriter.write(
                    String.format(
                            "%s§%s§%s", session.getUser().getName(), session.getUser().getId(), session.getSteps()
                    )
            );
            fileWriter.write("\n");
        } catch (IOException e) {
            Main.getInstance().getLogger().error("Unable to write record. data: {}", String.format("%s,%s,%s", session.getUser().getName(), session.getUser().getId(), session.getSteps()), e);
        }
    }

    public void load(File file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file.toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        for (String line : lines) {
            addRecord(line);
        }
        update();
    }

    public void addRecord(String rawData) {
        if (rawData.isEmpty()) {
            return;
        }
        String[] split = rawData.split("§");
        SessionRecord record = new SessionRecord(split[1], split[0], Integer.parseInt(split[2]));
//        Iterator<SessionRecord> iterator = records.iterator();
//        while (iterator.hasNext()) {
//            SessionRecord sessionRecord = iterator.next();
//            if (Objects.equals(sessionRecord.getUserId(), record.getUserId())) {
//                if (record.getSteps() > sessionRecord.getSteps()) {
//                    // if this record is not better than the record in this board, it is not necessary to create record for this session
//                    return;
//                } else if (record.getSteps() < sessionRecord.getSteps()) {
//                    iterator.remove(); // remove the old record
//                } else {
//                    return; // it is not necessary to write the same result!
//                }
//            }
//        }
        records.add(record);
    }

    public void setBoardMessageId(String boardMessageId) {
        // Oh, I hate bug
        Validate.isTrue(this.boardMessageId == null, "This instance has already bound to a board.");
        this.boardMessageId = boardMessageId;
    }

    public void update() {
        JKook.getCore().getUnsafe().getTextChannelMessage(boardMessageId).setComponent(drawCard());
    }

    public boolean hasPlayed(User user) {
        for (SessionRecord record : records) {
            if (Objects.equals(user.getId(), record.getUserId())) {
                return true;
            }
        }
        return false;
    }

    // return the card that can be shown as the board.
    public MultipleCardComponent drawCard() {
        CardBuilder header = new CardBuilder()
                .setTheme(Theme.NONE)
                .setSize(Size.LG)
                .addModule(
                        new HeaderModule("排名榜")
                );
        if (records.isEmpty()) {
            header.addModule(
                    new SectionModule(new PlainTextElement("暂无"), null, null)
            );
        } else {
            Collections.sort(records);
            int rank = 1;
            for (SessionRecord record : records) {
                // cannot exceed 47
                if (rank > 45) {
                    break;
                }
                header.addModule(
                        new SectionModule(
                                new PlainTextElement(
                                        rank++ + ". " + record.getUserName() + " - " + record.getSteps()
                                ), null, null
                        )
                );
            }
        }
        header.addModule(DividerModule.INSTANCE);
        header.addModule(
                new ContextModule(Collections.singletonList(new PlainTextElement("格式: 排名. 用户名 - 步数")))
        );
        return header.build();
    }

    public String export() {
        StringBuilder builder = new StringBuilder();
        for (SessionRecord record : records) {
            builder.append(
                    String.format(
                            "%s,%s,%s", record.getUserName(), record.getUserId(), record.getSteps()
                    )
            );
            builder.append("\n");
        }
        return builder.toString();
    }

}
