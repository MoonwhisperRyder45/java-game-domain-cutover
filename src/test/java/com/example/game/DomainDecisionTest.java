package com.example.game;

public final class DomainDecisionTest {
    public static void main(String[] args) {
        SendingDomainCutover.PlayerAsset asset = new SendingDomainCutover.PlayerAsset(
                "asset-1", "student-player", SendingDomainCutover.ModerationDecision.HOLD_FOR_REVIEW);
        if (asset.decision() != SendingDomainCutover.ModerationDecision.HOLD_FOR_REVIEW) {
            throw new AssertionError("blocked player asset must enter the moderation queue");
        }
        System.out.println("DomainDecisionTest: HOLD_FOR_REVIEW");
    }
}
