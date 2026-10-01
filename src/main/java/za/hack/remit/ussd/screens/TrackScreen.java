package za.hack.remit.ussd.screens;

import za.hack.remit.i18n.Messages;
import za.hack.remit.transfer.TransferService;
import za.hack.remit.ussd.*;

/** Ends the session (END) with the status of the latest transfer sent from this phone. */
public class TrackScreen extends BaseScreen {
    private final TransferService transfers;
    public TrackScreen(Messages m, TransferService transfers) { super(m); this.transfers = transfers; }
    @Override public ScreenId id() { return ScreenId.TRACK; }
    @Override protected boolean terminal() { return true; }

    @Override protected String body(UssdSession s) {
        if (s.lastReference == null) return msg(s, "track.none");
        return transfers.find(s.lastReference, s.msisdn())
                .map(t -> msg(s, "track.result", t.getReference(),
                        msg(s, "status." + t.getStatus().name().toLowerCase())))
                .orElseGet(() -> msg(s, "track.none"));
    }

    @Override public ScreenId handle(UssdSession s, String input) { return ScreenId.MAIN_MENU; }
}
