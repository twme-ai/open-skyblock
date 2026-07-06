package io.github.openskyblock.service;

public record ActionReward(SkillType skillType, double skillXp, String collectionId, long collectionAmount) {
}
