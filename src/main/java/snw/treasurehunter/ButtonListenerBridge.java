package snw.treasurehunter;

import snw.jkook.entity.User;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class ButtonListenerBridge {
    private final Map<String, Set<Consumer<User>>> map = new HashMap<>();

    public void add(String buttonId, Consumer<User> consumer) {
        get(buttonId).add(consumer);
    }

    public Set<Consumer<User>> get(String buttonId) {
        return map.computeIfAbsent(buttonId, k -> new HashSet<>());
    }

    public void remove(String buttonId) {
        map.remove(buttonId);
    }
}
