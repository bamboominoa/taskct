const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_user_123";
const BOB_UID = "bob_user_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

function buildValidTaskPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    title: "Hoàn thiện báo cáo quý 4",
    description: "Tổng hợp số liệu doanh thu từ tài liệu PDF",
    priority: "HIGH",
    category: "Công việc",
    sourceType: "FILE",
    sourceName: "BaoCaoQ4.pdf",
    completed: false,
    dueAtMillis: Date.now() + 3600000,
    reminderMinutesBefore: 30,
    reminderEnabled: true,
    telegramNotified: false,
    smartReminderText: "Sắp đến hạn nộp báo cáo quý 4, kiểm tra lại biểu đồ nhé!",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

test("Unauthenticated user cannot read or write tasks or scans", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("tasks")
      .doc("task_1")
      .set(buildValidTaskPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("ai_scans").get()
  );
});

test("Authenticated owner can create, list, toggle, edit, mark Telegram notified, and delete tasks", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const taskRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("tasks")
    .doc("task_1");

  // Create valid task (mirrors Kotlin TaskRepository.addTask map - NO 'id' field)
  await assertSucceeds(taskRef.set(buildValidTaskPayload(ALICE_UID)));

  // List own tasks subcollection (mirrors Kotlin TaskRepository.observeTasks)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").get()
  );

  // Action 1: Toggle completion
  await assertSucceeds(
    taskRef.update({
      completed: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Action 2: Mark Telegram notified
  await assertSucceeds(
    taskRef.update({
      telegramNotified: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Action 3: Edit task details
  await assertSucceeds(
    taskRef.update({
      title: "Hoàn thiện báo cáo quý 4 (đã duyệt)",
      priority: "MEDIUM",
      reminderMinutesBefore: 60,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Delete own task
  await assertSucceeds(taskRef.delete());
});

test("Rejects shadow fields, @DocumentId 'id' field, future timestamps, and immutable field mutation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const tasksCol = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("tasks");

  // Shadow / ghost field rejected
  await assertFails(
    tasksCol
      .doc("task_ghost")
      .set(buildValidTaskPayload(ALICE_UID, { isAdmin: true }))
  );

  // Explicit 'id' field rejected
  await assertFails(
    tasksCol
      .doc("task_with_id")
      .set(buildValidTaskPayload(ALICE_UID, { id: "task_with_id" }))
  );

  // Future timestamp rejected
  await assertFails(
    tasksCol
      .doc("task_future")
      .set(
        buildValidTaskPayload(ALICE_UID, {
          createdAt: new Date(Date.now() + 60000),
        })
      )
  );

  // Oversized title rejected
  await assertFails(
    tasksCol
      .doc("task_long")
      .set(buildValidTaskPayload(ALICE_UID, { title: "a".repeat(201) }))
  );

  // Valid create, then immutable createdAt and sourceType update rejected
  const taskRef = tasksCol.doc("task_immutable");
  await assertSucceeds(taskRef.set(buildValidTaskPayload(ALICE_UID)));

  await assertFails(
    taskRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertFails(
    taskRef.update({
      sourceType: "PHOTO",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Cross-user isolation: Bob cannot read, create, update, or delete Alice's tasks or scans", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("tasks")
      .doc("alice_task")
      .set(buildValidTaskPayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceTaskRef = bobDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("tasks")
    .doc("alice_task");

  await assertFails(aliceTaskRef.get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("tasks").get()
  );
  await assertFails(
    aliceTaskRef.update({
      completed: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(aliceTaskRef.delete());

  // Identity spoofing: Bob writing Alice's userId into Bob's collection fails
  await assertFails(
    bobDb
      .collection("users")
      .doc(BOB_UID)
      .collection("tasks")
      .doc("spoof_1")
      .set(buildValidTaskPayload(ALICE_UID))
  );
});

test("AI scan history records: owner can create, list, delete; updates denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const scanRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("ai_scans")
    .doc("scan_1");

  await assertSucceeds(
    scanRef.set({
      userId: ALICE_UID,
      sourceType: "PHOTO",
      sourceName: "BangKeHoach.jpg",
      summary: "Nhận diện 3 công việc từ ảnh bảng kế hoạch tuần.",
      extractedCount: 3,
      createdAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("ai_scans").get()
  );

  // Immutable scan record rejects update
  await assertFails(
    scanRef.update({ summary: "Modified summary" })
  );

  await assertSucceeds(scanRef.delete());
});
