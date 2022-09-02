package snw.treasurehunter;

import snw.jkook.entity.User;
import snw.jkook.message.component.TextComponent;
import snw.jkook.message.component.card.CardBuilder;
import snw.jkook.message.component.card.MultipleCardComponent;
import snw.jkook.message.component.card.Size;
import snw.jkook.message.component.card.Theme;
import snw.jkook.message.component.card.element.MarkdownElement;
import snw.jkook.message.component.card.element.PlainTextElement;
import snw.jkook.message.component.card.module.ContextModule;
import snw.jkook.message.component.card.module.DividerModule;
import snw.jkook.message.component.card.module.HeaderModule;
import snw.jkook.message.component.card.module.SectionModule;
import snw.jkook.util.Validate;

import java.util.Collections;
import java.util.Random;

public class Session {
    private final ChannelSession parent;
    private final User user;
    private final Object lock = new Object();
    private int min;
    private int max;
    private int target;
    private int steps;


    public Session(User user, ChannelSession parent) {
        this(user, Main.getInstance().getConfig().getInt("min", 0), Main.getInstance().getConfig().getInt("max", 100), parent);
    }

    public Session(User user, int min, int max, ChannelSession parent) {
        Validate.isTrue(min < max, "Minimum number should less than maximum number");
        Validate.isFalse(Math.abs(max - min) == 2, "Always win?"); // if max - min == 2, the player won
        this.user = user;
        this.min = min;
        this.max = max;
        this.parent = parent;
        do {
            this.target = (new Random().nextInt(max - min) + min);
        } while (this.target <= this.min); // make sure the target is in the range (excluding minimum and maximum)
        user.sendPrivateMessage(Main.HELP_CARD); // send help
        user.sendPrivateMessage(drawCard());
    }

    public User getUser() {
        return user;
    }

    public void execute(int answer) {
        synchronized (lock) {
            if (answer <= min || answer >= max) {
                user.sendPrivateMessage(new TextComponent("无效的参数 - 超出有效范围。"));
                return;
            }
            if (answer != target) {
                steps++; // add step
                if (answer > target) {
                    max = answer;
                } else {
                    min = answer;
                }
                if (Math.abs(this.max - this.min) == 2) {
                    user.sendPrivateMessage(
                            drawCard("你失败了！宝藏数字: " + target)
                    );
                    Main.getInstance().getSessionStorage().removeSessionByUser(user);
                } else {
                    user.sendPrivateMessage(drawCard());
                }
            } else {
                boolean b = parent.completeSession(this);
                if (!b) {
                    user.sendPrivateMessage(new TextComponent("提交记录时出现错误: 活动已过期。"));
                } else {
                    user.sendPrivateMessage(
                            drawCard("你成功了！你用了 " + steps + " 步。")
                    );
                }
            }
        }
    }

    public MultipleCardComponent drawCard() {
        return drawCard("请在 " + min + " 和 " + max + " 之间选择一个数字。\n直接发送您的目标数字即可。");
    }

    public static MultipleCardComponent drawCard(String infoMsg) {
        return new CardBuilder()
                .setTheme(Theme.DANGER)
                .setSize(Size.LG)
                .addModule(
                        new HeaderModule("宝藏猎人")
                )
                .addModule(DividerModule.INSTANCE)
                .addModule(
                        new SectionModule(new MarkdownElement(infoMsg), null, null)
                )
                .addModule(DividerModule.INSTANCE)
                .addModule(
                        new ContextModule(Collections.singletonList(new PlainTextElement("由 ZX夏夜之风 开发")))
                )
                .build();
    }

    public int getSteps() {
        return steps;
    }
}
