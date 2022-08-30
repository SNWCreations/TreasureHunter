package snw.treasurehunter;

import snw.jkook.entity.User;
import snw.jkook.event.EventHandler;
import snw.jkook.event.Listener;
import snw.jkook.event.pm.PrivateMessageReceivedEvent;
import snw.jkook.event.user.UserClickButtonEvent;
import snw.jkook.message.component.TextComponent;

import java.util.Set;
import java.util.function.Consumer;

public class EventListener implements Listener {

    // easy bridge implementation :D
    @EventHandler
    public void onButtonClick(UserClickButtonEvent event) {
        Set<Consumer<User>> consumers = Main.getInstance().getButtonListenerBridge().get(event.getValue());
        if (consumers == null) {
            return;
        }
        for (Consumer<User> consumer : consumers) {
            consumer.accept(event.getUser());
        }
    }

    @EventHandler
    public void onDM(PrivateMessageReceivedEvent event) {
        if (event.getMessage().getComponent() instanceof TextComponent) {
            Session session = Main.getInstance().getSessionStorage().getSessionByUser(event.getUser());
            if (session != null) {
                int num;
                try {
                    num = Integer.parseInt(event.getMessage().getComponent().toString());
                } catch (NumberFormatException e) {
                    return;
                }
                session.execute(num);
            }
        }
    }

}
