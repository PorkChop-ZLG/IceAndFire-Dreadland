# Dread Portal Frame Implementation Plan

**Goal:** Add tag-driven, vanilla-sized Dread Portal frames activated by the Dreadland Key while preserving existing standalone portal blocks and teleport behavior.
**Architecture:** Copy the structural portion of Minecraft 1.21.1 `PortalShape` into a mod-owned `DreadPortalShape`, replacing the vanilla frame predicate and portal block references. A custom `DreadlandKeyItem` invokes the detector from `useOn`; generated portal blocks carry an `AXIS` and `FRAME_BOUND` state so only generated portals depend on their frames.
**Approach:** Use a custom key item instead of a global interaction event. This keeps activation behavior localized and requires no vanilla Mixin.

---

### Task 1: Define the portal contract tests

**Files:**
- Modify: `scripts/verify-key-items.ps1`
- Create: `scripts/verify-dread-portal-frame.ps1`

**Steps:**
1. Update the key contract to require `DreadlandKeyItem` registration while retaining default stack size 64.
2. Add checks for the frame tag, vanilla dimensions, axis/bound states, custom shape class, activation, and success-only consumption.
3. Run both scripts and verify the new portal-frame contract fails because implementation is absent.

**Verification:** `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-dread-portal-frame.ps1`

### Task 2: Add the frame tag

**Files:**
- Create: `src/main/java/com/zonlong/iceandfiredreadland/registry/tag/ModBlockTags.java`
- Create: `src/main/resources/data/iceandfire_dreadland/tags/block/dreadland_portal_frame.json`

**Steps:**
1. Define `DREADLAND_PORTAL_FRAME` as a `TagKey<Block>` in the mod namespace.
2. Add `iceandfire:dread_stone` as the required default entry.
3. Run the portal contract and confirm the tag checks pass while shape checks remain red.

**Verification:** Parse the tag JSON and run `verify-dread-portal-frame.ps1`.

### Task 3: Implement vanilla-compatible shape detection

**Files:**
- Create: `src/main/java/com/zonlong/iceandfiredreadland/world/portal/DreadPortalShape.java`

**Steps:**
1. Copy the frame-search portion of Minecraft 1.21.1 `PortalShape`.
2. Replace the frame predicate with `BlockState#is(ModBlockTags.DREADLAND_PORTAL_FRAME)`.
3. Preserve vanilla interior dimensions: width 2-21 and height 3-21, optional corners, horizontal X/Z orientation, air/fire interior.
4. Count and create this mod's portal blocks, setting `AXIS` and `FRAME_BOUND=true`.
5. Run the portal contract.

**Verification:** `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-dread-portal-frame.ps1`

### Task 4: Add bound-frame lifecycle behavior

**Files:**
- Modify: `src/main/java/com/zonlong/iceandfiredreadland/block/DreadPortalBlock.java`

**Steps:**
1. Add vanilla-compatible `AXIS` and mod-owned `FRAME_BOUND` block-state properties.
2. Default direct placement and legacy structure states to `FRAME_BOUND=false`.
3. Reuse vanilla portal shape/update logic so bound portal blocks become air when their frame is incomplete.
4. Preserve block entity rendering, particles, collision behavior, and existing portal detection.
5. Add rotation and axis-aware outline behavior.

**Verification:** Run the portal contract and `gradlew compileJava`.

### Task 5: Activate frames with the Dreadland Key

**Files:**
- Create: `src/main/java/com/zonlong/iceandfiredreadland/item/DreadlandKeyItem.java`
- Modify: `src/main/java/com/zonlong/iceandfiredreadland/registry/ModItems.java`

**Steps:**
1. Override `useOn` and test the block adjacent to the clicked frame face in both horizontal axes.
2. Return `PASS` for invalid frames without consuming the key.
3. On a valid frame, create portal blocks server-side and consume one key unless the player has creative instabuild.
4. Return a sided success result so hand interaction is handled consistently.
5. Register the item through NeoForge `DeferredRegister.Items#registerItem` with default properties.

**Verification:** Run both contract scripts and `gradlew compileJava`.

### Task 6: Behavioral and build verification

**Files:**
- Create: `src/main/java/com/zonlong/iceandfiredreadland/gametest/DreadPortalGameTests.java`

**Steps:**
1. Add GameTests for minimum and mixed-tag frames, invalid dimensions, generated bound state, frame-break cleanup, and standalone portal persistence.
2. Reuse the existing `dread_exit_portal` structure as the test template and clear the test area before arranging each case.
3. Run the GameTest server when the local Ice And Fire runtime dependencies permit it.
4. Run the complete Gradle build, JSON validation, `git diff --check`, and focused code review.

**Verification:** `.\gradlew.bat runGameTestServer --no-daemon` and `.\gradlew.bat build --no-daemon`

