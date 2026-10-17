# Security Specification: MOEX Pulse AI

## 1. Data Invariants
1. All Firestore documents live under `/users/{userId}/signals/{signalId}` or `/users/{userId}/chat_messages/{messageId}` and strictly require `request.auth != null` and `request.auth.uid == userId`.
2. Every document must store `userId == request.auth.uid` and immutable `createdAt <= request.time`.
3. `SignalLogCloud` documents validate `ticker` (1..16 chars), `stockName` (1..64 chars), `signalType` in `['LONG', 'SHORT']`, `status` in `['ACTIVE', 'HIT_TP', 'HIT_SL']`, `confidence` in `0..100`, and bounded numeric prices.
4. `ChatMessageCloud` documents validate `role` in `['user', 'model']`, `text` (1..8000 chars), `modelMode` (1..64 chars), and `sourcesSummary` (0..2000 chars).

## 2. The "Dirty Dozen" Payloads
1. Unauthenticated read/write to `/users/alice/signals/sig1`.
2. Cross-user read by `bob_456` on `/users/alice_123/signals/sig1`.
3. Identity spoofing: `alice_123` writing `userId: "bob_456"` inside `/users/alice_123/signals/sig1`.
4. Shadow update: injecting an unauthorized field `isAdmin: true` into a signal document.
5. Invalid enum value: `signalType: "HOLD"` or `status: "UNKNOWN"`.
6. Timestamp tampering: modifying `createdAt` during an `update` operation.
7. Future timestamp injection: `createdAt` > `request.time`.
8. Oversized string DoS: `reason` > 300 chars or `text` > 8000 chars.
9. Type poisoning: passing a string `"100"` for `entryPrice` or `confidence`.
10. ID poisoning: document ID exceeding 128 characters or containing special characters.
11. Unauthenticated list query on `/users/alice_123/chat_messages`.
12. Deleting required fields on update via partial write gap.
