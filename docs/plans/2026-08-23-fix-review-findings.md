# Fix Review Findings REV-002 ~ REV-008

**Goal:** Fix all remaining code-review findings from `docs/code-review-post-0.1.0.md`.
**Scope:** REV-002, REV-003, REV-004, REV-005, REV-006, REV-007, REV-008.
**Approach:** Targeted code/resource fixes; no feature changes.

---

### Task 1: Fix REV-002 — placement replaceability check

**Files:**
- Modify: `src/main/java/com/zonlong/iceandfiredreadland/item/DreadQueenPortraitItem.java`

**Steps:**
1. Replace the air/water check:
   ```java
   if (!current.isAir() && !current.getFluidState().is(Fluids.WATER)) {
   ```
   with:
   ```java
   if (!current.canBeReplaced()) {
   ```
2. Keep the `Fluids` import for waterlogged placement logic.

**Verification:**
```powershell
Select-String -Path src/main/java/com/zonlong/iceandfiredreadland/item/DreadQueenPortraitItem.java -Pattern 'canBeReplaced'
```

---

### Task 2: Fix REV-003 + REV-004 + REV-006 — standard drop path and remove static state

**Files:**
- Modify: `src/main/java/com/zonlong/iceandfiredreadland/block/DreadQueenPortraitBlock.java`

**Steps:**
1. Remove the static `CREATIVE_SUPPRESSED_DROPS` map and its imports (`Map`, `Set`, `HashSet` no longer needed; keep `List`).
2. Replace `playerWillDestroy`:
   - In Creative mode, remove the whole 3×3 structure with `removeBlock(..., false)` **master first**, so no standard drop is triggered.
   - Non-creative mode keeps vanilla behavior.
3. Remove the manual drop from `onRemove` (no longer override it, or keep only `super.onRemove`).
4. Change `getDrops`:
   ```java
   @Override
   protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
       if (isMaster(state)) {
           return List.of(new ItemStack(ModItems.DREAD_QUEEN_PORTRAIT.get()));
       }
       return List.of();
   }
   ```
5. Change `neighborChanged`:
   - If the structure is invalid and the master block is still present, call:
     ```java
     level.destroyBlock(masterPos, true);
     ```
   - Then remove remaining painting blocks with `removeBlock(..., false)`.

**Verification:**
```powershell
Select-String -Path src/main/java/com/zonlong/iceandfiredreadland/block/DreadQueenPortraitBlock.java -Pattern 'destroyBlock|getDrops|CREATIVE_SUPPRESSED'
```

---

### Task 3: Fix REV-005 — post-placement neighbor/game updates

**Files:**
- Modify: `src/main/java/com/zonlong/iceandfiredreadland/item/DreadQueenPortraitItem.java`

**Steps:**
1. After the 9-block placement loop, add:
   ```java
   level.gameEvent(GameEvent.BLOCK_PLACE, center, GameEvent.Context.of(player, base));
   level.updateNeighborsAt(center, ModBlocks.DREAD_QUEEN_PORTRAIT.get());
   ```
2. Add import:
   ```java
   import net.minecraft.world.level.gameevent.GameEvent;
   ```
3. Replace `stack.shrink(1)` with `stack.consume(1, player)` to match vanilla block item behavior (creative-safe).

**Verification:**
```powershell
Select-String -Path src/main/java/com/zonlong/iceandfiredreadland/item/DreadQueenPortraitItem.java -Pattern 'gameEvent|updateNeighborsAt|consume'
```

---

### Task 4: Fix REV-007 — JSON BOM / formatting cleanup

**Files:**
- Modify: `src/main/resources/assets/iceandfire_dreadland/blockstates/dread_queen_portrait.json`
- Modify: `src/main/resources/assets/iceandfire_dreadland/models/block/dread_queen_portrait_0_0.json` … `dread_queen_portrait_2_2.json`

**Steps:**
1. Strip UTF-8 BOM from all these files.
2. Re-serialize with a clean 2-space indented JSON writer (e.g. .NET `System.Text.Json` with `WriteIndented = true`), writing UTF-8 without BOM.

**Verification:**
```powershell
$files = @("src/main/resources/assets/iceandfire_dreadland/blockstates/dread_queen_portrait.json") + (Get-ChildItem src/main/resources/assets/iceandfire_dreadland/models/block/dread_queen_portrait_*.json | ForEach-Object { $_.FullName })
foreach ($f in $files) { $b = [System.IO.File]::ReadAllBytes($f); if ($b.Length -ge 3 -and $b[0] -eq 0xEF -and $b[1] -eq 0xBB -and $b[2] -eq 0xBF) { Write-Output "BOM: $f" } }
```

---

### Task 5: Fix REV-008 — improve step/hit/fall sounds

**Files:**
- Modify: `src/main/java/com/zonlong/iceandfiredreadland/block/DreadQueenPortraitBlock.java`

**Steps:**
1. Change the custom `SoundType` to use wool-like sounds for step/hit/fall while keeping painting break/place:
   ```java
   new SoundType(
       1.0F, 1.0F,
       SoundEvents.PAINTING_BREAK,
       SoundEvents.WOOL_STEP,
       SoundEvents.PAINTING_PLACE,
       SoundEvents.WOOL_HIT,
       SoundEvents.WOOL_FALL
   )
   ```

**Verification:**
```powershell
Select-String -Path src/main/java/com/zonlong/iceandfiredreadland/block/DreadQueenPortraitBlock.java -Pattern 'WOOL_STEP|WOOL_HIT|WOOL_FALL'
```

---

### Task 6: Build and verify

**Files:**
- No new files.

**Steps:**
1. Run `.\gradlew.bat build`.
2. Verify no compile errors.
3. Verify JAR still contains the painting assets and no BOM in packaged JSON (build copies resources as-is).

**Verification:**
```powershell
.\gradlew.bat build
jar tf build/libs/iceandfire_dreadland-0.1.1.jar | Select-String -Pattern 'dread_queen_portrait'
```

---

## Notes

- REV-001, REV-009 ~ REV-013 are already closed/non-issues and are not touched.
- This plan does not change gameplay behavior (floating placement, waterlogging, creative no-drop remain intact).
