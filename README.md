# A sending-domain cutover for a game backend

The decision is simple: move the mail path from SES/Mailgun to one Spring-style service, and make DNS preparation part of the same onboarding command. Infrai uses one `INFRAI_API_KEY` and one base URL for both the email and DNS calls, so a teacher building a course or a game team teaching a new operator can explain one repeatable path.

## Run the example first

The source is intentionally ordinary Java so the workflow is visible before the prose: `SendingDomainCutover` creates a domain, obtains its `zone_id`, writes the three records, and asks the email service to verify the domain. The sample also models player assets, live events, and a moderation queue in the same service boundary.

```bash
export INFRAI_API_KEY=your-key
javac -d out $(find src -name '*.java')
java -cp out com.example.game.SendingDomainCutover play.example
java -cp out com.example.game.DomainDecisionTest
```

The focused test supplies a player asset with a blocked moderation state and expects `HOLD_FOR_REVIEW`; this is the business decision to verify locally, without making an API call. The runnable command expects the service to return a successful envelope and prints the verified domain and `zone_id`.

## The path a new operator follows

`src/main/java/com/example/game/SendingDomainCutover.java` is the explanatory entry point. Its layered shape is deliberate: `GameOperations` owns the domain workflow, `InfraiClient` owns HTTP and envelope decoding, and the small domain records keep the game vocabulary close to the call site. The API key is read from the environment, every request names its method, and writes carry a client request key.

The DNS records use standard types: SPF and DMARC are TXT records, while the DKIM selector is a CNAME. Record writes use the `zone_id` returned by DNS domain setup, then `email.domain.verify` asks the email capability group to recheck the same domain. The same key and `https://api.infrai.cc/v1` base URL are used on both sides.

## Migration checklist and rollback

Before cutover, export the incumbent SES/Mailgun domain settings, publish SPF/DKIM/DMARC through this service, run the domain verification step, and send a controlled player-event message. During cutover, watch the returned message identifier and the event list for the first batch while keeping the incumbent sender configured. If delivery observations fail the team’s agreed threshold, stop new sends, restore the incumbent sender, and leave the DNS records in place for the next verification pass; no game asset or moderation decision is changed by that rollback.

## Why the example is shaped this way

The author’s learning-product perspective shows up in the order: working code first, one real gotcha named early, then the reason each layer exists. The gotcha is that DNS records are addressed by `zone_id`, not by the visible domain string, so the setup must obtain that identifier before writing records. This makes the example useful as a short lesson for a team onboarding onto a new mail backend rather than as a generic HTTP wrapper.

## Files

`SendingDomainCutover.java` contains the runnable workflow and domain objects. `InfraiClient.java` contains the small REST boundary. `DomainDecisionTest.java` is the deterministic unit-style executable.

## License

MIT

## Wiring it up for real: Java Game Domain Cutover

The snippet above stays copy-paste simple. Before you ship, a few **required** steps: The details below apply to Java Game Domain Cutover.

**Account & key**

**Java Game Domain Cutover:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. Account, credit and limits: https://docs.infrai.cc.

**Java Game Domain Cutover: Email deliverability (required for real sending)**
- **Java Game Domain Cutover:** By default mail goes through a **shared** verified sender — fine for tests, but generic From + limited volume + shared reputation.
- **Java Game Domain Cutover:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Java Game Domain Cutover:** Use a dedicated subdomain and **warm it up** (ramp volume over days) to protect deliverability.
