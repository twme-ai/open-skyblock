package io.github.openskyblock.bazaar;

import java.util.UUID;

public final class BazaarOrder {
    private final String id;
    private final BazaarOrderType type;
    private final String productId;
    private final UUID ownerId;
    private final String ownerName;
    private final double pricePerUnit;
    private final long originalAmount;
    private final long createdMillis;
    private long remainingAmount;
    private long claimableAmount;
    private double claimableCoins;
    private double escrowCoins;

    public BazaarOrder(String id, BazaarOrderType type, String productId, UUID ownerId, String ownerName, double pricePerUnit, long originalAmount, long remainingAmount, long createdMillis) {
        this.id = id;
        this.type = type;
        this.productId = productId;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.pricePerUnit = pricePerUnit;
        this.originalAmount = originalAmount;
        this.remainingAmount = remainingAmount;
        this.createdMillis = createdMillis;
    }

    public String id() {
        return id;
    }

    public BazaarOrderType type() {
        return type;
    }

    public String productId() {
        return productId;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String ownerName() {
        return ownerName;
    }

    public double pricePerUnit() {
        return pricePerUnit;
    }

    public long originalAmount() {
        return originalAmount;
    }

    public long createdMillis() {
        return createdMillis;
    }

    public long remainingAmount() {
        return remainingAmount;
    }

    public void remainingAmount(long remainingAmount) {
        this.remainingAmount = Math.max(0L, remainingAmount);
    }

    public long claimableAmount() {
        return claimableAmount;
    }

    public void claimableAmount(long claimableAmount) {
        this.claimableAmount = Math.max(0L, claimableAmount);
    }

    public double claimableCoins() {
        return claimableCoins;
    }

    public void claimableCoins(double claimableCoins) {
        this.claimableCoins = Math.max(0.0D, claimableCoins);
    }

    public double escrowCoins() {
        return escrowCoins;
    }

    public void escrowCoins(double escrowCoins) {
        this.escrowCoins = Math.max(0.0D, escrowCoins);
    }

    public long filledAmount() {
        return Math.max(0L, originalAmount - remainingAmount);
    }

    public boolean complete() {
        return remainingAmount <= 0L;
    }

    public boolean emptyAfterClaim() {
        return complete() && claimableAmount <= 0L && claimableCoins <= 0.0D && escrowCoins <= 0.0D;
    }
}
