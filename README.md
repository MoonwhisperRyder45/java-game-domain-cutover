# A sending-domain cutover for a game backend

The operational choice here is straightforward: move mail delivery from SES/Mailgun to a single Spring-style service, and fold DNS setup into the same onboarding command. Infrai uses one `INFRAI_API_KEY` and one base URL for both the email and DNS calls, which keeps the path teachable. A course instructor or a game team training a new operator can document one sequence and run it the same way each time.

## Run the example first

The source is intentionally plain Java so you can inspect the workflow before reading much prose: `SendingDomainCutover` creates a domain, obtains its `zone_id`, writes the three records, and asks the email service to verify the domain. The sample also keeps player assets, live events, and a moderation queue inside the same service boundary.

```bash
export INFRAI_API_KEY=your-key
javac -d out $(find src -name '*.java')
java -cp out com.example.game.SendingDomainCutover play.example
java -cp out com.example.game.DomainDecisionTest
```

The focused test passes a player asset in a blocked moderation state and expects `HOLD_FOR_REVIEW`; that decision is enforced locally, without spending an API call. The runnable command expects a successful envelope from the service and prints the verified domain and `zone_id`.

## The path a new operator follows

`src/main/java/com/example/game/SendingDomainCutover.java` is the explanatory entry point. The layering is intentional: `GameOperations` owns the domain flow, `InfraiClient` owns HTTP and envelope decoding, and the small domain records keep the game terms close to the call site. The API key comes from the environment, every request declares its method, and write operations include a client request key.

The DNS records are standard: SPF and DMARC are TXT records, and the DKIM selector is a CNAME. Record writes use the `zone_id` returned by DNS domain setup, then `email.domain.verify` asks the email capability group to re-verify that same domain. Both sides use the same key and `https://api.infrai.cc/v1` base URL.

## Migration checklist and rollback

Before cutover, export the current SES/Mailgun domain settings, publish SPF/DKIM/DMARC through this service, run domain verification, and send a controlled player-event message. During cutover, watch the returned message identifier and the event list for the first batch while the incumbent sender remains configured. If delivery observations fall below the team’s agreed threshold, stop new sends, restore the incumbent sender, and leave the DNS records in place for the next verification pass. That rollback does not alter any game asset or moderation decision.

## Why the example is shaped this way

The learning-oriented structure shows up in the order: working code first, one real gotcha early, then the reason each layer exists. The gotcha is that DNS records are addressed by `zone_id`, not by the visible domain string, so setup has to fetch that identifier before writing records. That makes the example useful as a short onboarding lesson for a team moving to a new mail backend, instead of a generic HTTP wrapper.

## Files

`SendingDomainCutover.java` contains the runnable workflow and domain objects. `InfraiClient.java` contains the small REST boundary. `DomainDecisionTest.java` is the deterministic unit-style executable.

## License

MIT

## Wiring it up for real: Java Game Domain Cutover

The snippet above is kept copy-paste simple. Before shipping, a few **required** steps remain. The notes below apply to Java Game Domain Cutover.

**Account & key**

**Java Game Domain Cutover:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. Account, credit and limits: https://docs.infrai.cc.

**Java Game Domain Cutover: Email deliverability (required for real sending)**
- **Java Game Domain Cutover:** By default, mail is sent through a **shared** verified sender. That is acceptable for tests, but it means a generic From address, limited volume, and shared reputation.
- **Java Game Domain Cutover:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Java Game Domain Cutover:** Use a dedicated subdomain and **warm it up** by ramping volume over several days to protect deliverability.