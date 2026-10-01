package za.hack.remit.ussd.screens;

import za.hack.remit.i18n.Messages;
import za.hack.remit.transfer.TransferService;
import za.hack.remit.ussd.*;

public class TrackScreen extends BaseScreen {
    private final TransferService transfers;
    public TrackScreen(Messages m, TransferService transfers) { super(m); this.transfers = transfers; }
    @Override public ScreenId id() { return ScreenId.TRACK; }
    @Override protected String body(UssdSession s) { return msg(s, "track.prompt"); }

    @Override public ScreenId handle(UssdSession s, String input) {
        var ref = InputValidator.reference(input);
        var status = ref.flatMap(transfers::status);          // e.g. "IN_TRANSIT"
        if (status.isEmpty()) { s.fail("err.ref_not_found"); return id(); }
        s.put("exitKey", "track.result");
        s.put("exitArg1", ref.get());
        s.put("exitArg2", msg(s, "status." + status.get().toLowerCase()));
        return ScreenId.EXIT;
    }
}
