package io.github.openskyblock.quest;

import java.util.Locale;

public enum QuestObjectiveType {
    ISLAND_CREATED,
    SKYBLOCK_LEVEL,
    TOTAL_SKILL_LEVEL,
    SKILL_LEVEL,
    SKILL_XP,
    COLLECTION_AMOUNT,
    COLLECTION_TIER,
    UNLOCKED_COLLECTIONS,
    SLAYER_LEVEL,
    SLAYER_XP,
    BESTIARY_KILLS,
    BESTIARY_TIER,
    FAIRY_SOULS,
    PETS_OWNED,
    PET_SCORE,
    MINIONS_PLACED,
    MUSEUM_DONATIONS,
    PURSE,
    BANK_BALANCE;

    public static QuestObjectiveType parse(String value) {
        if (value == null || value.isBlank()) {
            return SKYBLOCK_LEVEL;
        }
        try {
            return valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ignored) {
            return SKYBLOCK_LEVEL;
        }
    }
}
