const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_landlord_1";
const BOB_UID = "bob_landlord_2";

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

function validTenantPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    name: "Ramesh Kumar",
    roomNumber: "Room 101",
    phone: "9876543210",
    monthlyRent: 8500,
    initialMeterReading: 1240,
    openingBalance: 0,
    active: true,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validLedgerPayload(uid, tenantId, monthKey, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    tenantId,
    monthKey,
    rentAmount: 8500,
    prevReading: 1240,
    currReading: 1320,
    unitsConsumed: 80,
    electricityMode: "FIXED_RATE",
    ratePerUnit: 8.5,
    electricityAmount: 680,
    previousBalance: 0,
    amountPaid: 9000,
    paymentMode: "UPI",
    paymentNote: "Paid via GPay on 5th",
    closingBalance: 180,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validMainMeterPayload(uid, monthKey, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    monthKey,
    consumerNumber: "CA-10293847",
    prevReading: 10500,
    currReading: 10950,
    unitsConsumed: 450,
    govtBillAmount: 3825,
    billPaid: true,
    notes: "BSES November cycle",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("tenants").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("tenants").doc("t1").set(validTenantPayload(ALICE_UID))
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("monthlyLedgers").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("mainMeterLogs").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("settings").doc("preferences").get());
});

test("Owner can create, list, partial-update, and delete tenants; invalid payloads rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const tenantsRef = aliceDb.collection("users").doc(ALICE_UID).collection("tenants");

  await assertSucceeds(tenantsRef.doc("t1").set(validTenantPayload(ALICE_UID)));
  await assertSucceeds(tenantsRef.get());

  await assertSucceeds(
    tenantsRef.doc("t1").update({
      monthlyRent: 9000,
      active: false,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject immutable createdAt modification
  await assertFails(
    tenantsRef.doc("t1").update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Reject ghost field & @DocumentId `id` field
  await assertFails(
    tenantsRef.doc("t2").set(validTenantPayload(ALICE_UID, { id: "t2" }))
  );
  await assertFails(
    tenantsRef.doc("t2").set(validTenantPayload(ALICE_UID, { isVerified: true }))
  );

  // Reject future timestamp and negative rent
  await assertFails(
    tenantsRef.doc("t2").set(validTenantPayload(ALICE_UID, { createdAt: new Date(Date.now() + 60000) }))
  );
  await assertFails(
    tenantsRef.doc("t2").set(validTenantPayload(ALICE_UID, { monthlyRent: -500 }))
  );

  await assertSucceeds(tenantsRef.doc("t1").delete());
});

test("Cross-user isolation blocks Bob from reading or modifying Alice records", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb.collection("users").doc(ALICE_UID).collection("tenants").doc("t1").set(validTenantPayload(ALICE_UID));
    await adminDb.collection("users").doc(ALICE_UID).collection("monthlyLedgers").doc("t1_2026-11").set(validLedgerPayload(ALICE_UID, "t1", "2026-11"));
    await adminDb.collection("users").doc(ALICE_UID).collection("mainMeterLogs").doc("2026-11").set(validMainMeterPayload(ALICE_UID, "2026-11"));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("tenants").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("tenants").doc("t1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("tenants").doc("t1").update({ name: "Hijack" }));
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("monthlyLedgers").doc("t1_2026-11").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("mainMeterLogs").doc("2026-11").get());
});

test("MonthlyLedger enforces tenant existence, reading order, enum validity, and immutable keys", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const tenantsRef = aliceDb.collection("users").doc(ALICE_UID).collection("tenants");
  const ledgersRef = aliceDb.collection("users").doc(ALICE_UID).collection("monthlyLedgers");

  // Orphaned ledger before tenant exists must fail
  await assertFails(ledgersRef.doc("t1_2026-11").set(validLedgerPayload(ALICE_UID, "t1", "2026-11")));

  // Create tenant first
  await assertSucceeds(tenantsRef.doc("t1").set(validTenantPayload(ALICE_UID)));

  // Invalid reading order (curr < prev) must fail
  await assertFails(
    ledgersRef.doc("t1_2026-11").set(validLedgerPayload(ALICE_UID, "t1", "2026-11", { prevReading: 500, currReading: 400 }))
  );

  // Invalid electricityMode / paymentMode must fail
  await assertFails(
    ledgersRef.doc("t1_2026-11").set(validLedgerPayload(ALICE_UID, "t1", "2026-11", { electricityMode: "FREE" }))
  );
  await assertFails(
    ledgersRef.doc("t1_2026-11").set(validLedgerPayload(ALICE_UID, "t1", "2026-11", { paymentMode: "CRYPTO" }))
  );

  // Valid create + query + partial update
  await assertSucceeds(ledgersRef.doc("t1_2026-11").set(validLedgerPayload(ALICE_UID, "t1", "2026-11")));
  await assertSucceeds(ledgersRef.get());
  await assertSucceeds(
    ledgersRef.doc("t1_2026-11").update({
      amountPaid: 9180,
      closingBalance: 0,
      paymentMode: "CASH",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject mutating tenantId on update
  await assertFails(
    ledgersRef.doc("t1_2026-11").update({
      tenantId: "t2",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("MainMeterLog and BillingSettings enforce schema, monthKey parity, and docId constraints", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const mainRef = aliceDb.collection("users").doc(ALICE_UID).collection("mainMeterLogs");
  const settingsRef = aliceDb.collection("users").doc(ALICE_UID).collection("settings");

  await assertSucceeds(mainRef.doc("2026-11").set(validMainMeterPayload(ALICE_UID, "2026-11")));
  await assertSucceeds(mainRef.get());
  await assertSucceeds(
    mainRef.doc("2026-11").update({
      govtBillAmount: 4000,
      billPaid: false,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Mismatched monthKey docId vs field must fail
  await assertFails(mainRef.doc("2026-10").set(validMainMeterPayload(ALICE_UID, "2026-11")));

  // Settings must use doc ID "preferences"
  const now = new Date(Date.now() - 2000);
  await assertFails(
    settingsRef.doc("other").set({
      userId: ALICE_UID,
      defaultRatePerUnit: 8,
      defaultElectricityMode: "FIXED_RATE",
      consumerNumber: "CA-123",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    settingsRef.doc("preferences").set({
      userId: ALICE_UID,
      defaultRatePerUnit: 8,
      defaultElectricityMode: "MAIN_BILL_SPLIT",
      consumerNumber: "CA-123",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    settingsRef.doc("preferences").update({
      defaultRatePerUnit: 9.5,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});
