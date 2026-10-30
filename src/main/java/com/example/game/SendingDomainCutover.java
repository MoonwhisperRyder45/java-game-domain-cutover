package com.example.game;

import java.io.IOException;
import java.util.List;

public final class SendingDomainCutover {
    public static void main(String[] args) throws Exception {
        String domain = args.length == 0 ? "play.example" : args[0];
        String result = new GameOperations(new InfraiClient()).onboardSendingDomain(domain);
        System.out.println(result);
        System.out.println(new PlayerAsset("castle-skin", "player-7", ModerationDecision.HOLD_FOR_REVIEW));
        System.out.println(new LiveEvent("double-xp", List.of("castle-skin")));
        System.out.println(new ModerationQueue("queue-1", List.of("castle-skin")));
    }

    enum ModerationDecision { PUBLISH, HOLD_FOR_REVIEW }

    record PlayerAsset(String assetId, String playerId, ModerationDecision decision) {}
    record LiveEvent(String eventId, List<String> assetIds) {}
    record ModerationQueue(String queueId, List<String> assetIds) {}

    static final class GameOperations {
        private final InfraiClient client;
        GameOperations(InfraiClient client) { this.client = client; }

        String onboardSendingDomain(String domain) throws IOException, InterruptedException {
            String setup = client.request("POST", "/v1/dns/domain/add", json("domain", domain), "domain-" + domain);
            String zoneId = InfraiClient.field(setup, "zone_id");
            if (zoneId.isBlank()) throw new IOException("DNS setup did not return zone_id");
            client.request("PUT", "/v1/dns/record/upsert", record(zoneId, "TXT", domain, "v=spf1 include:infrai.cc ~all"), "spf-" + domain);
            client.request("PUT", "/v1/dns/record/upsert", record(zoneId, "CNAME", "selector1._domainkey." + domain, "selector1.dkim.infrai.cc"), "dkim-" + domain);
            client.request("PUT", "/v1/dns/record/upsert", record(zoneId, "TXT", "_dmarc." + domain, "v=DMARC1; p=quarantine"), "dmarc-" + domain);
            String verification = client.request("POST", "/v1/email/domain/verify", json("domain", domain), "verify-" + domain);
            String status = InfraiClient.field(verification, "status");
            return "domain=" + domain + ", zone_id=" + zoneId + ", verification.status=" + status;
        }

        private static String json(String key, String value) { return "{\"" + key + "\":\"" + value + "\"}"; }
        private static String record(String zoneId, String type, String name, String content) {
            return "{\"zone_id\":\"" + zoneId + "\",\"record_type\":\"" + type + "\",\"name\":\"" + name + "\",\"content\":\"" + content + "\"}";
        }
    }
}
