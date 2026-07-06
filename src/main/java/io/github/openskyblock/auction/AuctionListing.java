package io.github.openskyblock.auction;

import java.util.UUID;
import org.bukkit.inventory.ItemStack;

public final class AuctionListing {
    private final String id;
    private final UUID sellerId;
    private final String sellerName;
    private final ItemStack itemStack;
    private final double price;
    private final long createdMillis;
    private final long expiresMillis;
    private UUID buyerId;
    private String buyerName;
    private long soldMillis;
    private boolean sellerClaimed;
    private boolean cancelled;

    public AuctionListing(String id, UUID sellerId, String sellerName, ItemStack itemStack, double price, long createdMillis, long expiresMillis) {
        this.id = id;
        this.sellerId = sellerId;
        this.sellerName = sellerName;
        this.itemStack = itemStack;
        this.price = price;
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

    public long createdMillis() {
        return createdMillis;
    }

    public long expiresMillis() {
        return expiresMillis;
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

    public boolean cancelled() {
        return cancelled;
    }

    public void cancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public boolean sold() {
        return buyerId != null;
    }

    public boolean active(long nowMillis) {
        return !cancelled && !sold() && nowMillis < expiresMillis;
    }

    public boolean expired(long nowMillis) {
        return !cancelled && !sold() && nowMillis >= expiresMillis;
    }
}
