package snw.treasurehunter;

import snw.jkook.JKook;
import snw.jkook.entity.User;
import snw.jkook.entity.channel.TextChannel;
import snw.jkook.util.Validate;

import java.util.HashMap;
import java.util.Map;

public class SessionStorage {
    // key - channel ID, value - Session object
    private final Map<String, ChannelSession> sessionMap = new HashMap<>();
    private final Map<String, Session> userSession = new HashMap<>();

    public void createSession(TextChannel channel, long aliveLength, User sender) throws IllegalArgumentException {
        Validate.isFalse(hasSession(channel), "This channel has a session that not terminated!");
        ChannelSession channelSession = new ChannelSession(this, channel.getId(), aliveLength, sender);
        sessionMap.put(channel.getId(), channelSession);
    }

    // IllegalArgumentException -> already a session bound to the provided user
    // IllegalStateException -> no activity on the channel
    public void createSession(User user, String channelId) throws IllegalArgumentException, IllegalStateException {
        if (getSessionByUser(user) != null) {
            throw new IllegalArgumentException();
        }
        ChannelSession channelSession = getSession(((TextChannel) JKook.getHttpAPI().getChannel(channelId)));
        if (channelSession == null) {
            throw new IllegalStateException();
        }
        channelSession.addPlayer(user);
        Session session = new Session(user, channelSession);
        userSession.put(user.getId(), session);
    }

    public ChannelSession getSession(TextChannel channel) {
        return sessionMap.get(channel.getId());
    }

    public Session getSessionByUser(User user) {
        return userSession.get(user.getId());
    }

    public Session removeSessionByUser(User user) {
        return userSession.remove(user.getId());
    }

    public void removeSession(String channelId) {
        sessionMap.remove(channelId);
    }

    public boolean hasSession(TextChannel channel) {
        return sessionMap.containsKey(channel.getId());
    }

}
