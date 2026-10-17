const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read or write signals", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("signals").get()
  );
});

test("Authenticated user: can create and read own valid signal", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("signals").doc("sig_1").set({
      userId: ALICE_UID,
      ticker: "SBER",
      stockName: "Сбербанк",
      signalType: "LONG",
      entryPrice: 285.5,
      takeProfit: 292.1,
      stopLoss: 282.2,
      confidence: 86,
      reason: "Пробой EMA 20 вверх",
      status: "ACTIVE",
      pnlPercent: 0.0,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("signals").doc("sig_1").get()
  );
});

test("Authenticated user: cannot read another user's signals", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const now = new Date();
    await context.firestore().collection("users").doc(BOB_UID).collection("signals").doc("bob_sig").set({
      userId: BOB_UID,
      ticker: "GAZP",
      stockName: "Газпром",
      signalType: "SHORT",
      entryPrice: 140.0,
      takeProfit: 134.0,
      stopLoss: 143.0,
      confidence: 78,
      reason: "Пробой EMA 20 вниз",
      status: "ACTIVE",
      pnlPercent: 0.0,
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("signals").doc("bob_sig").get()
  );
});

test("Authenticated user: rejects shadow fields or invalid enum on signals", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("signals").doc("sig_bad").set({
      userId: ALICE_UID,
      ticker: "SBER",
      stockName: "Сбербанк",
      signalType: "INVALID_DIRECTION",
      entryPrice: 285.5,
      takeProfit: 292.1,
      stopLoss: 282.2,
      confidence: 86,
      reason: "Пробой EMA 20 вверх",
      status: "ACTIVE",
      pnlPercent: 0.0,
      isAdmin: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Authenticated user: can create and read own chat message", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chat_messages").doc("msg_1").set({
      userId: ALICE_UID,
      role: "user",
      text: "Проанализируй уровни по SBER и LKOH",
      modelMode: "PRO_ARGON",
      sourcesSummary: "",
      createdAt: now,
      updatedAt: now,
    })
  );
});
