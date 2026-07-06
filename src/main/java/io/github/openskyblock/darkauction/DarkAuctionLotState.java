package io.github.openskyblock.darkauction;

import java.util.UUID;

public final class DarkAuctionLotState {
    private final String sessionId;
    private final int lotIndex;
    private final String itemId;
    private final long endsMillis;
    private UUID highBidderId;
    private String highBidderName;
    private double highBid;
    private boolean awarded;

    public DarkAuctionLotState(String sessionId, int lotIndex, String itemId, long endsMillis) {
        this.sessionId = sessionId;
        this.lotIndex = lotIndex;
        this.itemId = itemId;
        this.endsMillis = endsMillis;
    }

    public String sessionId() {
        return sessionId;
    }

    public int lotIndex() {
        return lotIndex;
    }

    public String itemId() {
        return itemId;
    }

    public long endsMillis() {
        return endsMillis;
    }

    public UUID highBidderId() {
        return highBidderId;
    }

    public void highBidderId(UUID highBidderId) {
        this.highBidderId = highBidderId;
    }

    public String highBidderName() {
        return highBidderName;
    }

    public void highBidderName(String highBidderName) {
        this.highBidderName = highBidderName;
    }

    public double highBid() {
        return highBid;
    }

    public void highBid(double highBid) {
        this.highBid = Math.max(0.0D, highBid);
    }

    public boolean awarded() {
        return awarded;
    }

    public void awarded(boolean awarded) {
        this.awarded = awarded;
    }

    public boolean hasBid() {
        return highBidderId != null && highBid > 0.0D;
    }
}
