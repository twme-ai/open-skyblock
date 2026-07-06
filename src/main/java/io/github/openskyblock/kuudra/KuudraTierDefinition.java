package io.github.openskyblock.kuudra;

import java.util.List;

public record KuudraTierDefinition(
        String id,
        String displayName,
        int tierNumber,
        int requiredCombatLevel,
        String requiredTierId,
        int requiredTierCompletions,
        String keyItemId,
        double keyCost,
        double chestCost,
        int freeRewardRolls,
        int paidRewardRolls,
        double combatXp,
        double completionCoins,
        int freeTeeth,
        int paidTeeth,
        double crimsonEssence,
        double skyBlockXp,
        List<KuudraRewardDefinition> freeRewards,
        List<KuudraRewardDefinition> paidRewards
) {
}
