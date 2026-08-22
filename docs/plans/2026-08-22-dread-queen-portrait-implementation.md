# Dread Queen Portrait Implementation Plan (Block-Based)

**Goal:** Add a pure block-based 3×3 “悚怖女皇画像 / Dread Queen Portrait” that is placed by the `dread_queen_portrait` item and drops that item when broken.
**Architecture:** Multi-block structure (9 blocks) with static baked JSON models; no block entity, no Moonlight, no vanilla painting entity.
**Approach:** Static baked models + custom block/item classes.

---

### Task 1: Prepare textures

**Files:**
- Create: `src/main/resources/assets/iceandfire_dreadland/textures/painting/dread_queen_portrait.png`
- Create: `src/main/resources/assets/iceandfire_dreadland/textures/item/dread_queen_portrait.png`
- Create: `src/main/resources/assets/iceandfire_dreadland/textures/block/dread_queen_portrait_back.png`
- Delete: `src/main/resources/assets/iceandfire_dreadland/textures/item/dread_queen_icon.png`
- Delete: `src/main/resources/assets/iceandfire_dreadland/models/item/dread_queen_icon.json`

**Steps:**
1. Copy `D:\Minecraft\IceAndFire-Dreadland\lib\悚怖女王-image2-192x.png` to the painting texture path (192×192).
2. Resize the 192×192 image to 64×64 and save as the item icon.
3. Copy `D:\Minecraft\minecraft-1.21.1\assets\minecraft\textures\painting\back.png` to the block back texture path.
4. Delete the old `dread_queen_icon` item texture/model files.

**Verification:**
```powershell
Test-Path src/main/resources/assets/iceandfire_dreadland/textures/painting/dread_queen_portrait.png
Test-Path src/main/resources/assets/iceandfire_dreadland/textures/item/dread_queen_portrait.png
Test-Path src/main/resources/assets/iceandfire_dreadland/textures/block/dread_queen_portrait_back.png
Test-Path src/main/resources/assets/iceandfire_dreadland/textures/item/dread_queen_icon.png
```

---

### Task 2: Create the block class

**Files:**
- Create: `src/main/java/com/zonlong/iceandfiredreadland/block/DreadQueenPortraitBlock.java`

**Steps:**
1. Extend `HorizontalDirectionalBlock`.
2. Add block state properties:
   - `FACING` (horizontal)
   - `X_OFFSET` (0–2)
   - `Y_OFFSET` (0–2)
3. Implement:
   - Thin wall-panel `getShape` per facing.
   - `getCollisionShape` returns `Shapes.empty()`.
   - `onRemove` drops one `ModItems.DREAD_QUEEN_PORTRAIT` when the master block (`x_offset=0, y_offset=0`) is removed.
   - `getDrops` returns empty list.
   - `getCloneItemStack` returns `new ItemStack(ModItems.DREAD_QUEEN_PORTRAIT.get())`.
   - `canSurvive` validates all 9 blocks of the structure.
   - `neighborChanged` removes the whole structure when invalid.
   - `updateShape` breaks the block if the wall behind it is removed.
   - `skipRendering` avoids internal face rendering between painting blocks.
   - `getPistonPushReaction` returns `PushReaction.DESTROY`.

**Verification:**
```powershell
Test-Path src/main/java/com/zonlong/iceandfiredreadland/block/DreadQueenPortraitBlock.java
```

---

### Task 3: Register the block

**Files:**
- Modify: `src/main/java/com/zonlong/iceandfiredreadland/registry/ModBlocks.java`

**Steps:**
1. Add:
   ```java
   public static final DeferredBlock<Block> DREAD_QUEEN_PORTRAIT = register("dread_queen_portrait", DreadQueenPortraitBlock::new);
   ```
2. Add import for `DreadQueenPortraitBlock`.

**Verification:**
```powershell
Select-String -Path src/main/java/com/zonlong/iceandfiredreadland/registry/ModBlocks.java -Pattern 'DREAD_QUEEN_PORTRAIT'
```

---

### Task 4: Create the item class

**Files:**
- Create: `src/main/java/com/zonlong/iceandfiredreadland/item/DreadQueenPortraitItem.java`

**Steps:**
1. Extend `Item`.
2. Override `useOn(UseOnContext)`:
   - Reject vertical faces.
   - Compute the 3×3 grid centered on the clicked block face.
   - Validate all 9 cells are air and the wall behind each cell is solid.
   - On the server, place all 9 blocks with correct `facing`, `x_offset`, `y_offset`.
   - Play `SoundEvents.PAINTING_PLACE`.
   - Shrink the item stack.
3. Return `InteractionResult.sidedSuccess(level.isClientSide)`.

**Verification:**
```powershell
Test-Path src/main/java/com/zonlong/iceandfiredreadland/item/DreadQueenPortraitItem.java
```

---

### Task 5: Update item registration

**Files:**
- Modify: `src/main/java/com/zonlong/iceandfiredreadland/registry/ModItems.java`

**Steps:**
1. Remove the old `DREAD_QUEEN_ICON` registration (`dread_queen_icon`).
2. Add the new first registration:
   ```java
   public static final DeferredItem<Item> DREAD_QUEEN_PORTRAIT = REGISTRY.registerItem("dread_queen_portrait", DreadQueenPortraitItem::new, new Item.Properties());
   ```
3. Add import for `DreadQueenPortraitItem`.

**Verification:**
```powershell
Select-String -Path src/main/java/com/zonlong/iceandfiredreadland/registry/ModItems.java -Pattern 'dread_queen_portrait|DREAD_QUEEN_PORTRAIT'
```

---

### Task 6: Update creative tab icon

**Files:**
- Modify: `src/main/java/com/zonlong/iceandfiredreadland/registry/ModCreativeModeTabs.java`

**Steps:**
1. Change:
   ```java
   .icon(() -> new ItemStack(ModItems.DREAD_QUEEN_ICON.get()))
   ```
   to:
   ```java
   .icon(() -> new ItemStack(ModItems.DREAD_QUEEN_PORTRAIT.get()))
   ```

**Verification:**
```powershell
Select-String -Path src/main/java/com/zonlong/iceandfiredreadland/registry/ModCreativeModeTabs.java -Pattern 'DREAD_QUEEN_PORTRAIT'
```

---

### Task 7: Create blockstate JSON

**Files:**
- Create: `src/main/resources/assets/iceandfire_dreadland/blockstates/dread_queen_portrait.json`

**Steps:**
1. Generate 36 variants for `facing` × `x_offset` × `y_offset`.
2. Use model naming `iceandfire_dreadland:block/dread_queen_portrait_{x}_{y}`.
3. Use the same `y` rotations as FastPaintings:
   - `north`: `y=180`
   - `south`: no rotation
   - `east`: `y=270`
   - `west`: `y=90`

**Verification:**
```powershell
Get-Content src/main/resources/assets/iceandfire_dreadland/blockstates/dread_queen_portrait.json
```

---

### Task 8: Create 9 block models

**Files:**
- Create: `src/main/resources/assets/iceandfire_dreadland/models/block/dread_queen_portrait_0_0.json` … `dread_queen_portrait_2_2.json`

**Steps:**
1. For each cell `(x, y)`:
   - `u0 = x * 16 / 3`
   - `u1 = (x + 1) * 16 / 3`
   - `v0 = y * 16 / 3`
   - `v1 = (y + 1) * 16 / 3`
2. Model is a 1px-thin south-facing panel:
   - `from: [0, 0, 0]`, `to: [16, 16, 1]`
   - `south` face uses `#painting` with cropped UVs
   - `north`, edges use `#back`
   - Textures:
     - `#painting`: `iceandfire_dreadland:painting/dread_queen_portrait`
     - `#back`: `iceandfire_dreadland:block/dread_queen_portrait_back`
     - `particle`: `iceandfire_dreadland:painting/dread_queen_portrait`

**Verification:**
```powershell
Get-ChildItem src/main/resources/assets/iceandfire_dreadland/models/block/dread_queen_portrait_*.json | Measure-Object
```

---

### Task 9: Update language files

**Files:**
- Modify: `src/main/resources/assets/iceandfire_dreadland/lang/en_us.json`
- Modify: `src/main/resources/assets/iceandfire_dreadland/lang/zh_cn.json`

**Steps:**
1. Remove `item.iceandfire_dreadland.dread_queen_icon`.
2. Add:
   - `en_us.json`:
     - `item.iceandfire_dreadland.dread_queen_portrait`: `Dread Queen Portrait`
     - `block.iceandfire_dreadland.dread_queen_portrait`: `Dread Queen Portrait`
   - `zh_cn.json`:
     - `item.iceandfire_dreadland.dread_queen_portrait`: `悚怖女皇画像`
     - `block.iceandfire_dreadland.dread_queen_portrait`: `悚怖女皇画像`

**Verification:**
```powershell
Select-String -Path src/main/resources/assets/iceandfire_dreadland/lang/en_us.json -Pattern 'dread_queen_portrait'
Select-String -Path src/main/resources/assets/iceandfire_dreadland/lang/zh_cn.json -Pattern 'dread_queen_portrait'
```

---

### Task 10: Build and verify

**Files:**
- No new files.

**Steps:**
1. Run `.\gradlew.bat build`.
2. Verify the jar contains:
   - `assets/iceandfire_dreadland/blockstates/dread_queen_portrait.json`
   - `assets/iceandfire_dreadland/models/block/dread_queen_portrait_*.json`
   - `assets/iceandfire_dreadland/textures/painting/dread_queen_portrait.png`
   - `assets/iceandfire_dreadland/textures/item/dread_queen_portrait.png`
   - `assets/iceandfire_dreadland/textures/block/dread_queen_portrait_back.png`
   - no `dread_queen_icon` files

**Verification:**
```powershell
jar tf build/libs/iceandfire_dreadland-0.1.1.jar | Select-String -Pattern 'dread_queen_portrait|dread_queen_icon'
```

---

## Notes

- Item ID is `dread_queen_portrait`, not `dread_queen_icon`.
- The item remains first in `ModItems` and is the creative tab icon.
- No `painting_variant`, no vanilla painting entity, no Moonlight dependency.
