package snw.treasurehunter;

public class Utilities {

    private Utilities() {
    }

    // WARN: you should add System.currentTimeMillis() to the result of this method manually.
    // supported formats: Xh, Xm, Xs
    public static long strToTimeMillis(String str) {
        char[] chars = str.toCharArray();
        char latest = chars[chars.length - 1];
        long unit = 1000L;
        switch (latest) {
            case 'd':
                unit = unit * 60 * 60 * 24;
                break;
            case 'h':
                unit = unit * 60 * 60;
                break;
            case 'm':
                unit = unit * 60;
                break;
            case 's':
                break;
            default:
                return -1; // unsupported format
        }
        try {
            return unit * Integer.parseInt(str.substring(0, str.length() - 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
