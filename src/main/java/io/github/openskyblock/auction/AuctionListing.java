package io.github.openskyblock.auction;

import java.util.UUID;
import org.bukkit.inventory.ItemStack;

public final class AuctionListing {
    private final String id;
    private final UUID sellerId;
    private final String sellerName;
    private final ItemStack itemStack;
    private final double price;
    private final boolean bin;
    private final long createdMillis;
    private final long expiresMillis;
    private UUID highBidderId;
    private String highBidderName;
    private double highBid;
    private UUID buyerId;
    private String buyerName;
    private long soldMillis;
    private boolean sellerClaimed;
    private boolean buyerClaimed;
    private boolean cancelled;

    public AuctionListing(String id, UUID sellerId, String sellerName, ItemStack itemStack, double price, boolean bin, long createdMillis, long expiresMillis) {
        this.id = id;
        this.sellerId = sellerId;
        this.sellerName = sellerName;
        this.itemStack = itemStack;
        this.price = price;
        this.bin = bin;
        this.createdMillis = createdMillis;
        this.expiresMillis = expiresMillis;
    }

    public String id() {
        return id;
    }

    public UUID sellerId() {
        return sellerId;
    }

    public String sellerName() {
        return sellerName;
    }

    public ItemStack itemStack() {
        return itemStack;
    }

    public double price() {
        return price;
    }

    public boolean bin() {
        return bin;
    }

    public long createdMillis() {
        return createdMillis;
    }

    public long expiresMillis() {
        return expiresMillis;
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

    public UUID buyerId() {
        return buyerId;
    }

    public void buyerId(UUID buyerId) {
        this.buyerId = buyerId;
    }

    public String buyerName() {
        return buyerName;
    }

    public void buyerName(String buyerName) {
        this.buyerName = buyerName;
    }

    public long soldMillis() {
        return soldMillis;
    }

    public void soldMillis(long soldMillis) {
        this.soldMillis = soldMillis;
    }

    public boolean sellerClaimed() {
        return sellerClaimed;
    }

    public void sellerClaimed(boolean sellerClaimed) {
        this.sellerClaimed = sellerClaimed;
    }

    public boolean buyerClaimed() {
        return buyerClaimed;
    }

    public void buyerClaimed(boolean buyerClaimed) {
        this.buyerClaimed = buyerClaimed;
    }

    public boolean cancelled() {
        return cancelled;
    }

    public void cancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public boolean sold() {
        return buyerId != null;
    }

    public boolean hasBid() {
        return highBidderId != null && highBid > 0.0D;
    }

    public boolean active(long nowMillis) {
        return !cancelled && (bin ? !sold() : true) && nowMillis < expiresMillis;
    }

    public boolean expired(long nowMillis) {
        return !cancelled && (bin ? !sold() : true) && nowMillis >= expiresMillis;
    }

    public boolean fullyClaimed(long nowMillis) {
        if (cancelled) {
            return true;
        }
        if (bin) {
            return sellerClaimed;
        }
        if (nowMillis < expiresMillis) {
            return false;
        }
        return hasBid() ? sellerClaimed && buyerClaimed : sellerClaimed;
    }
}
