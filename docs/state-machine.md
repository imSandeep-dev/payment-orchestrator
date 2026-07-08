# Transaction State Machine Specification

## Overview
Twenty-four states, enforced in application code by `TransactionStateMachine`
. The database `state` column is `VARCHAR(30) + CHECK` — a
data-integrity backstop, not the enforcement mechanism itself.

## Why more than the required 12 states
Section A2.2 requires a minimum of 12 and explicitly lists 9 "additional
states you should consider" — all 9 are included below because each maps to
a concrete requirement elsewhere in the spec:

| Extra state                        | Required by                                               |
|------------------------------------|-----------------------------------------------------------|
| ABANDONED                          | A2.2 (user drop-off)                                      |
| VOID_INITIATED / VOIDED            | A1.2 (auth without capture must be voidable)              |
| AUTH_EXPIRED                       | FS-12 (UPI mandate timeout)                               |
| SETTLED                            | A5.5 reconciliation engine; FS-08 refund-after-settlement |
| PARTIALLY_REFUNDED / REFUND_FAILED | A2.2; FS-08                                               |
| DISPUTE_OPENED / DISPUTE_RESOLVED  | A2.2 (chargebacks)                                        |
| RECONCILIATION_MISMATCH            | FS-11, explicitly named in the scenario                   |
| ROUTE_FAILED / AUTH_TIMEOUT        | FS-01 (must distinguish timeout from decline)             |

## State Table

| State                   | Category            | Description                                                             |
|-------------------------|---------------------|-------------------------------------------------------------------------|
| CREATED                 | initial             | Record exists, no gateway contact yet                                   |
| ROUTE_SELECTED          | intermediate        | Router chose a gateway                                                  |
| ROUTE_FAILED            | intermediate        | No gateway available/eligible                                           |
| ABANDONED               | terminal            | Customer dropped off before auth                                        |
| AUTH_INITIATED          | intermediate        | Auth request sent                                                       |
| AUTHORISED              | intermediate        | Gateway placed a hold                                                   |
| AUTH_FAILED             | intermediate        | Gateway declined                                                        |
| AUTH_TIMEOUT            | intermediate        | No response within gateway's timeout (A1.3 table)                       |
| AUTH_EXPIRED            | terminal            | Hold period elapsed unused (FS-12)                                      |
| VOID_INITIATED          | intermediate        | Void request sent                                                       |
| VOIDED                  | terminal            | Hold released, no capture                                               |
| CAPTURE_INITIATED       | intermediate        | Capture request sent                                                    |
| CAPTURED                | intermediate        | Funds transferred                                                       |
| PARTIALLY_CAPTURED      | intermediate        | Less than full auth amount captured (FS-05)                             |
| CAPTURE_FAILED          | intermediate        | Capture attempt failed                                                  |
| SETTLED                 | intermediate        | Gateway confirms settlement                                             |
| REFUND_INITIATED        | intermediate        | Refund request sent                                                     |
| REFUNDED                | terminal            | Full refund complete                                                    |
| PARTIALLY_REFUNDED      | intermediate        | Partial refund complete                                                 |
| REFUND_FAILED           | intermediate        | Refund attempt failed                                                   |
| DISPUTE_OPENED          | intermediate        | Chargeback initiated                                                    |
| DISPUTE_RESOLVED        | terminal            | Chargeback resolved                                                     |
| RECONCILIATION_MISMATCH | manual-intervention | Reconciliation found a discrepancy (FS-11); exits only via admin action |
| FAILED                  | terminal            | Unrecoverable                                                           |

## Transition Table

| From                    | Event                   | To                                         |
|-------------------------|-------------------------|--------------------------------------------|
| CREATED                 | ROUTE_SELECTED          | ROUTE_SELECTED                             |
| CREATED                 | ABANDONED               | ABANDONED                                  |
| ROUTE_SELECTED          | AUTH_INITIATED          | AUTH_INITIATED                             |
| ROUTE_SELECTED          | ROUTE_FAILED            | ROUTE_FAILED                               |
| ROUTE_FAILED            | RETRY_ROUTE             | ROUTE_SELECTED                             |
| ROUTE_FAILED            | EXHAUSTED               | FAILED                                     |
| AUTH_INITIATED          | GATEWAY_AUTH_SUCCESS    | AUTHORISED                                 |
| AUTH_INITIATED          | GATEWAY_AUTH_DECLINE    | AUTH_FAILED                                |
| AUTH_INITIATED          | GATEWAY_TIMEOUT         | AUTH_TIMEOUT                               |
| AUTH_INITIATED          | MANDATE_EXPIRED         | AUTH_EXPIRED                               ||
| AUTH_TIMEOUT            | FAILOVER                | ROUTE_SELECTED                             |
| AUTH_TIMEOUT            | EXHAUSTED               | FAILED                                     |
| AUTH_FAILED             | RETRY_ROUTE             | ROUTE_SELECTED                             |
| AUTH_FAILED             | EXHAUSTED               | FAILED                                     |
| AUTHORISED              | CAPTURE_INITIATED       | CAPTURE_INITIATED                          |
| AUTHORISED              | VOID_INITIATED          | VOID_INITIATED                             |
| AUTHORISED              | HOLD_EXPIRED            | AUTH_EXPIRED                               |
| VOID_INITIATED          | GATEWAY_VOID_SUCCESS    | VOIDED                                     |
| CAPTURE_INITIATED       | GATEWAY_CAPTURE_SUCCESS | CAPTURED                                   |
| CAPTURE_INITIATED       | GATEWAY_PARTIAL_CAPTURE | PARTIALLY_CAPTURED                         |
| CAPTURE_INITIATED       | GATEWAY_CAPTURE_ERROR   | CAPTURE_FAILED                             |
| CAPTURE_FAILED          | RETRY_CAPTURE           | CAPTURE_INITIATED                          |
| CAPTURE_FAILED          | VOID_INITIATED          | VOID_INITIATED                             |
| PARTIALLY_CAPTURED      | CAPTURE_INITIATED       | CAPTURE_INITIATED                          |
| PARTIALLY_CAPTURED      | REFUND_INITIATED        | REFUND_INITIATED                           |
| PARTIALLY_CAPTURED      | VOID_INITIATED          | VOID_INITIATED                             |
| PARTIALLY_CAPTURED      | GATEWAY_SETTLED         | SETTLED                                    |
| CAPTURED                | REFUND_INITIATED        | REFUND_INITIATED                           |
| CAPTURED                | GATEWAY_SETTLED         | SETTLED                                    |
| CAPTURED                | DISPUTE_OPENED          | DISPUTE_OPENED                             |
| CAPTURED                | RECONCILIATION_OVERRIDE | RECONCILIATION_MISMATCH                    |
| SETTLED                 | REFUND_INITIATED        | REFUND_INITIATED                           |
| SETTLED                 | DISPUTE_OPENED          | DISPUTE_OPENED                             |
| SETTLED                 | RECONCILIATION_OVERRIDE | RECONCILIATION_MISMATCH                    |
| REFUND_INITIATED        | GATEWAY_REFUND_SUCCESS  | REFUNDED                                   |
| REFUND_INITIATED        | GATEWAY_PARTIAL_REFUND  | PARTIALLY_REFUNDED                         |
| REFUND_INITIATED        | GATEWAY_REFUND_ERROR    | REFUND_FAILED                              |
| REFUND_FAILED           | RETRY_REFUND            | REFUND_INITIATED                           |
| PARTIALLY_REFUNDED      | REFUND_INITIATED        | REFUND_INITIATED                           |
| DISPUTE_OPENED          | DISPUTE_RESOLVED        | DISPUTE_RESOLVED                           |
| RECONCILIATION_MISMATCH | ADMIN_OVERRIDE          | (manual — target state chosen by reviewer) |

Every row above becomes exactly one entry in the `TransactionStateMachine`'s
transition map. Any pair not listed here is illegal and must
throw `InvalidStateTransitionException` (see FS-15).

## Diagram

​```mermaid
stateDiagram-v2
    [*] --> CREATED
    CREATED --> ROUTE_SELECTED
    CREATED --> ABANDONED
    ROUTE_SELECTED --> AUTH_INITIATED
    ROUTE_SELECTED --> ROUTE_FAILED
    ROUTE_FAILED --> ROUTE_SELECTED : retry
    ROUTE_FAILED --> FAILED : exhausted
    AUTH_INITIATED --> AUTHORISED
    AUTH_INITIATED --> AUTH_FAILED
    AUTH_INITIATED --> AUTH_TIMEOUT
    AUTH_INITIATED --> AUTH_EXPIRED : mandate_expired (FS-12)
    AUTH_TIMEOUT --> ROUTE_SELECTED : failover
    AUTH_TIMEOUT --> FAILED : exhausted
    AUTH_FAILED --> ROUTE_SELECTED : retry
    AUTH_FAILED --> FAILED : exhausted
    AUTHORISED --> CAPTURE_INITIATED
    AUTHORISED --> VOID_INITIATED
    AUTHORISED --> AUTH_EXPIRED
    VOID_INITIATED --> VOIDED
    CAPTURE_INITIATED --> CAPTURED
    CAPTURE_INITIATED --> PARTIALLY_CAPTURED
    CAPTURE_INITIATED --> CAPTURE_FAILED
    CAPTURE_FAILED --> CAPTURE_INITIATED : retry
    CAPTURE_FAILED --> VOID_INITIATED
    PARTIALLY_CAPTURED --> CAPTURE_INITIATED : remainder
    PARTIALLY_CAPTURED --> REFUND_INITIATED
    PARTIALLY_CAPTURED --> VOID_INITIATED
    PARTIALLY_CAPTURED --> SETTLED
    CAPTURED --> REFUND_INITIATED
    CAPTURED --> SETTLED
    CAPTURED --> DISPUTE_OPENED
    CAPTURED --> RECONCILIATION_MISMATCH
    SETTLED --> REFUND_INITIATED
    SETTLED --> DISPUTE_OPENED
    SETTLED --> RECONCILIATION_MISMATCH
    REFUND_INITIATED --> REFUNDED
    REFUND_INITIATED --> PARTIALLY_REFUNDED
    REFUND_INITIATED --> REFUND_FAILED
    REFUND_FAILED --> REFUND_INITIATED : retry
    PARTIALLY_REFUNDED --> REFUND_INITIATED : remainder
    DISPUTE_OPENED --> DISPUTE_RESOLVED
    RECONCILIATION_MISMATCH --> [*] : admin override (manual)
    ABANDONED --> [*]
    VOIDED --> [*]
    AUTH_EXPIRED --> [*]
    REFUNDED --> [*]
    DISPUTE_RESOLVED --> [*]
    FAILED --> [*]
​```

## Amendments

###  Added AUTH_INITIATED → AUTH_EXPIRED (event: MANDATE_EXPIRED)

The original 39-transition table only modeled AUTH_EXPIRED as
reachable from AUTHORISED via HOLD_EXPIRED — representing a card/net-banking auth hold's window elapsing unused. Implementing
UPIAdapter against **FS-12** ("UPI Collect Flow Timeout") revealed this
was incomplete: a UPI collect request nobody approves within the 5-minute
mandate window never reaches AUTHORISED at all — it needs to go straight
from AUTH_INITIATED to AUTH_EXPIRED. Added a dedicated MANDATE_EXPIRED
event rather than overloading HOLD_EXPIRED, since the two represent
genuinely different real-world triggers (elapsed capture window vs.
customer non-response), even though they share a target state.