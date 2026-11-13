const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const serverTimestamp = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_rajesh";
const MEMBER_UID = "member_suresh";
const OUTSIDER_UID = "outsider_vikram";
const COMMITTEE_ID = "comm_hamari_bc_1";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validCommitteePayload(adminUid = ADMIN_UID, memberUids = [ADMIN_UID]) {
  return {
    name: "हमारी BC कमेटी",
    adminUid,
    adminEmail: "admin@test.com",
    inviteCode: "BC2026",
    memberUids,
    totalBcAmount: 100000,
    totalMembers: 10,
    timerMinutes: 7,
    scheduledAtMillis: 0,
    isLive: false,
    biddingStartedAtMillis: 0,
    biddingEndsAtMillis: 0,
    currentHighestBid: 0,
    currentHighestBidderUid: "",
    currentHighestBidderName: "",
    currentHighestBidderMobile: "",
    roundNumber: 1,
    winnerDeclared: false,
    winnerName: "",
    winnerUid: "",
    winnerMobile: "",
    winningBid: 0,
    netPayableToWinner: 0,
    perMemberInstallment: 0,
    winnerSloganTemplate: "🏆 बधाई हो [Winner Name]! YOU ARE GREAT! 🎉",
    apkDownloadUrl: "https://example.com/app.apk",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

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

test("Unauthenticated user: cannot read users or committees", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").get());
  await assertFails(unauthDb.collection("committees").get());
});

test("User profile PII isolation: owner can write/read, other user cannot", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  await assertSucceeds(
    adminDb.collection("users").doc(ADMIN_UID).set({
      userId: ADMIN_UID,
      displayName: "Rajesh",
      mobileNumber: "9876543210",
      email: "rajesh@test.com",
      activeCommitteeId: "",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();
  await assertFails(outsiderDb.collection("users").doc(ADMIN_UID).get());
});

test("Shadow update & value poisoning: committee rejects ghost field and oversized strings", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const payload = validCommitteePayload();
  await assertSucceeds(adminDb.collection("committees").doc(COMMITTEE_ID).set(payload));

  // Ghost field attack
  await assertFails(
    adminDb.collection("committees").doc(COMMITTEE_ID).update({
      name: "New Name",
      isVerifiedGhost: true,
      updatedAt: serverTimestamp(),
    })
  );

  // Value poisoning (timerMinutes out of bounds)
  await assertFails(
    adminDb.collection("committees").doc(COMMITTEE_ID).update({
      timerMinutes: 9999,
      updatedAt: serverTimestamp(),
    })
  );
});

test("Email spoofing test: unverified admin email cannot override another committee", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  await assertSucceeds(
    adminDb.collection("committees").doc(COMMITTEE_ID).set(validCommitteePayload())
  );

  const spoofedDb = testEnv
    .authenticatedContext(OUTSIDER_UID, {
      email: "VishwanathYadav127@gmail.com",
      email_verified: false,
    })
    .firestore();

  await assertFails(
    spoofedDb.collection("committees").doc(COMMITTEE_ID).update({
      name: "Hijacked",
      updatedAt: serverTimestamp(),
    })
  );
});

test("Committee query alignment & live bidding flow: join, bid, and subcollections", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  await assertSucceeds(
    adminDb.collection("committees").doc(COMMITTEE_ID).set(validCommitteePayload())
  );

  // Query without memberUids filter must fail
  await assertFails(adminDb.collection("committees").get());

  // Query with memberUids filter succeeds
  await assertSucceeds(
    adminDb.collection("committees").where("memberUids", "array-contains", ADMIN_UID).get()
  );

  // Outsider cannot read committee before joining
  const memberDb = testEnv.authenticatedContext(MEMBER_UID).firestore();
  await assertFails(memberDb.collection("committees").doc(COMMITTEE_ID).get());

  // Member joins via memberUids append
  await assertSucceeds(
    memberDb.collection("committees").doc(COMMITTEE_ID).update({
      memberUids: [ADMIN_UID, MEMBER_UID],
      updatedAt: serverTimestamp(),
    })
  );

  // Member cannot place bid while room is locked
  await assertFails(
    memberDb.collection("committees").doc(COMMITTEE_ID).update({
      currentHighestBid: 5000,
      currentHighestBidderUid: MEMBER_UID,
      currentHighestBidderName: "Suresh",
      currentHighestBidderMobile: "9988776655",
      updatedAt: serverTimestamp(),
    })
  );

  // Admin unlocks live bidding
  await assertSucceeds(
    adminDb.collection("committees").doc(COMMITTEE_ID).update({
      isLive: true,
      biddingStartedAtMillis: Date.now(),
      biddingEndsAtMillis: Date.now() + 7 * 60 * 1000,
      updatedAt: serverTimestamp(),
    })
  );

  // Member places valid higher bid
  await assertSucceeds(
    memberDb.collection("committees").doc(COMMITTEE_ID).update({
      currentHighestBid: 15000,
      currentHighestBidderUid: MEMBER_UID,
      currentHighestBidderName: "Suresh",
      currentHighestBidderMobile: "9988776655",
      updatedAt: serverTimestamp(),
    })
  );

  // Member cannot place lower bid
  await assertFails(
    memberDb.collection("committees").doc(COMMITTEE_ID).update({
      currentHighestBid: 12000,
      currentHighestBidderUid: MEMBER_UID,
      currentHighestBidderName: "Suresh",
      currentHighestBidderMobile: "9988776655",
      updatedAt: serverTimestamp(),
    })
  );

  // Member posts bid bubble in subcollection
  await assertSucceeds(
    memberDb
      .collection("committees")
      .doc(COMMITTEE_ID)
      .collection("bids")
      .doc("bid_1")
      .set({
        committeeId: COMMITTEE_ID,
        roundNumber: 1,
        senderUid: MEMBER_UID,
        senderName: "Suresh",
        senderMobile: "9988776655",
        type: "bid",
        amount: 15000,
        message: "₹15,000 बोली",
        memberUids: [ADMIN_UID, MEMBER_UID],
        createdAt: serverTimestamp(),
      })
  );

  // Outsider cannot read bids subcollection (direct get, unfiltered list, or querying another UID)
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();
  await assertFails(
    outsiderDb
      .collection("committees")
      .doc(COMMITTEE_ID)
      .collection("bids")
      .doc("bid_1")
      .get()
  );
  await assertFails(
    outsiderDb
      .collection("committees")
      .doc(COMMITTEE_ID)
      .collection("bids")
      .get()
  );
  await assertFails(
    outsiderDb
      .collection("committees")
      .doc(COMMITTEE_ID)
      .collection("bids")
      .where("memberUids", "array-contains", ADMIN_UID)
      .get()
  );
});
