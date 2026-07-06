package io.github.openskyblock.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;

public final class TradeSession {
    private final UUID firstId;
    private final String firstName;
    private final UUID secondId;
    private final String secondName;
    private final long createdMillis;
    private final List<ItemStack> firstItems = new ArrayList<>();
    private final List<ItemStack> secondItems = new ArrayList<>();
    private double firstCoins;
    private double secondCoins;
    private boolean firstReady;
    private boolean secondReady;
    private boolean firstConfirmed;
    private boolean secondConfirmed;

    public TradeSession(UUID firstId, String firstName, UUID secondId, String secondName, long createdMillis) {
        this.firstId = firstId;
        this.firstName = firstName;
        this.secondId = secondId;
        this.secondName = secondName;
        this.createdMillis = createdMillis;
    }

    public UUID firstId() {
        return firstId;
    }

    public String firstName() {
        return firstName;
    }

    public UUID secondId() {
        return secondId;
    }

    public String secondName() {
        return secondName;
    }

    public long createdMillis() {
        return createdMillis;
    }

    public boolean contains(UUID playerId) {
        return firstId.equals(playerId) || secondId.equals(playerId);
    }

    public UUID partnerId(UUID playerId) {
        return firstId.equals(playerId) ? secondId : firstId;
    }

    public String partnerName(UUID playerId) {
        return firstId.equals(playerId) ? secondName : firstName;
    }

    public List<ItemStack> items(UUID playerId) {
        return firstId.equals(playerId) ? firstItems : secondItems;
    }

    public double coins(UUID playerId) {
        return firstId.equals(playerId) ? firstCoins : secondCoins;
    }

    public void addCoins(UUID playerId, double amount) {
        if (firstId.equals(playerId)) {
            firstCoins += amount;
        } else {
            secondCoins += amount;
        }
    }

    public void ready(UUID playerId, boolean ready) {
        if (firstId.equals(playerId)) {
            firstReady = ready;
        } else {
            secondReady = ready;
        }
    }

    public boolean ready(UUID playerId) {
        return firstId.equals(playerId) ? firstReady : secondReady;
    }

    public boolean bothReady() {
        return firstReady && secondReady;
    }

    public void confirmed(UUID playerId, boolean confirmed) {
        if (firstId.equals(playerId)) {
            firstConfirmed = confirmed;
        } else {
            secondConfirmed = confirmed;
        }
    }

    public boolean confirmed(UUID playerId) {
        return firstId.equals(playerId) ? firstConfirmed : secondConfirmed;
    }

    public boolean bothConfirmed() {
        return firstConfirmed && secondConfirmed;
    }

    public void resetReview() {
        firstReady = false;
        secondReady = false;
        firstConfirmed = false;
        secondConfirmed = false;
    }
}
