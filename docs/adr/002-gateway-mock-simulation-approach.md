# ADR-002: Gateway Mock Simulation Approach


## Context
Section B4.1 confirms the test VM has no external internet access. Section
B4.3 requires each gateway to simulate 5 response types via X-Mock-Response /
X-Mock-Delay-Ms / X-Mock-Gateway-Down signals, deterministically.

## Decisions

1. **In-process simulation, not standalone mock HTTP servers.** Adapters
   implement PaymentGateway directly; there is no loopback network call to
   simulate inside a sealed, no-internet test VM.

2. **TIMEOUT returns promptly — it does not block for the gateway's real
   30–60-second timeout window (Section A1.3).** Section B3's failover
   benchmark measures time from *failure detection* to *alternate gateway
   response*, target < 2 seconds. If our mock genuinely slept for 30+ real
   seconds before signaling failure, sub-2-second failover would be
   structurally impossible to demonstrate. What we test is the router's
   reaction to a timeout signal (Day 7–8), not OS socket-timeout mechanics.

3. **X-Mock-Delay-Ms IS honored as a real delay** — distinct from #2, this
   simulates realistic latency variance for the P95 benchmarks.

4. **Shared simulation logic lives in AbstractMockPaymentGateway** (Template
   Method pattern) — each concrete adapter supplies only gateway identity
   and its A1.3 timeout constant.

5. **MockInstruction is an explicit parameter today**, wired from real
   X-Mock-* HTTP headers starting Day 11–12 (API layer translates headers
   into a MockInstruction, passed Controller → Service → Router → Adapter).

## Consequences
- `PaymentGatewayContractTest` guarantees all four adapters stay behaviorally
  in sync — a bug fixed in one adapter's test can't silently persist in another.
- (To be expanded Day 6): PayU's "Limited" auth+capture support and UPI's
  instant-settlement / collect-flow mandate window (FS-12) are gateway —
  specific quirks layered on top of this shared engine, not baked into it.