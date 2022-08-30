package snw.treasurehunter;

public class SessionRecord implements Comparable<SessionRecord> {
    private final String userId;
    private final String userName;
    private final Integer steps;

    public SessionRecord(String userId, String userName, int steps) {
        this.userId = userId;
        this.userName = userName;
        this.steps = steps;
    }

    public String getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public int getSteps() {
        return steps;
    }

    @Override
    public int compareTo(SessionRecord o) {
        return this.steps.compareTo(o.steps);
    }

}
