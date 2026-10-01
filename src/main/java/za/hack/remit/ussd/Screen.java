package za.hack.remit.ussd;

public interface Screen {
    ScreenId id();

    /** What the user sees. Must not change state. */
    UssdResponse render(UssdSession s);

    /**
     * Process input and return the next screen. To re-show the same screen with an
     * error, call s.fail(key, args) and return id().
     */
    ScreenId handle(UssdSession s, String input);
}
