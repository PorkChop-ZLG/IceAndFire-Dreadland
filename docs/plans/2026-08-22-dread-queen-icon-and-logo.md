# Add Dread Queen Icon Item and Mod Logo Implementation Plan

**Goal:** Add the 128×128 image as the mod logo, add a new item `dread_queen_icon` (“悚怖女王图标” / “Dread Queen Icon”) using the same image as its item texture, and set the creative mode tab icon to this item.

**Architecture:** Manual resource files + NeoForge `DeferredRegister` item registration, following the existing pattern in `ModItems` and `ModCreativeModeTabs`. No datagen is needed for this small change.

**Approach:** Approach A — direct resource copy and manual registration (recommended). It matches the project’s current manual JSON + registry style, has the fewest moving parts, and avoids introducing a datagen pipeline.

---

### Task 1: Copy the 128×128 image into the mod resources

**Files:**
- Create: `src/main/resources/logo.png`
- Create: `src/main/resources/assets/iceandfire_dreadland/textures/item/dread_queen_icon.png`
- Source: `D:\Minecraft\IceAndFire-Dreadland\lib\悚怖女王image2初稿128.png`

**Steps:**
1. Copy the 128×128 PNG to `src/main/resources/logo.png` (mod logo).
2. Copy the same PNG to `src/main/resources/assets/iceandfire_dreadland/textures/item/dread_queen_icon.png` (item texture).
3. Confirm both destination files exist and are 128×128.

**Verification:**
```powershell
Test-Path src/main/resources/logo.png
Test-Path src/main/resources/assets/iceandfire_dreadland/textures/item/dread_queen_icon.png
```

---

### Task 2: Register the mod logo in `neoforge.mods.toml`

**Files:**
- Modify: `src/main/resources/META-INF/neoforge.mods.toml`

**Steps:**
1. Inside the existing `[[mods]]` block, replace the commented placeholder:
   ```toml
   #logoFile="examplemod.png" #optional
   ```
   with:
   ```toml
   logoFile="logo.png" #optional
   ```
2. Keep the comment/order consistent with the current file.

**Verification:**
```powershell
Select-String -Path src/main/resources/META-INF/neoforge.mods.toml -Pattern 'logoFile="logo.png"'
```

---

### Task 3: Register the `dread_queen_icon` item

**Files:**
- Modify: `src/main/java/com/zonlong/iceandfiredreadland/registry/ModItems.java`

**Steps:**
1. Add a new `DeferredItem<Item>` field after the existing key items:
   ```java
   public static final DeferredItem<Item> DREAD_QUEEN_ICON = REGISTRY.registerItem("dread_queen_icon", Item::new, new Item.Properties());
   ```
2. `Item` is already imported in this file, so no new import is needed.

**Verification:**
```powershell
Select-String -Path src/main/java/com/zonlong/iceandfiredreadland/registry/ModItems.java -Pattern 'DREAD_QUEEN_ICON'
```

---

### Task 4: Create the item model JSON

**Files:**
- Create: `src/main/resources/assets/iceandfire_dreadland/models/item/dread_queen_icon.json`

**Steps:**
1. Create the JSON with the same pattern as existing key item models:
   ```json
   {
     "parent": "minecraft:item/generated",
     "textures": {
       "layer0": "iceandfire_dreadland:item/dread_queen_icon"
     }
   }
   ```

**Verification:**
```powershell
Get-Content src/main/resources/assets/iceandfire_dreadland/models/item/dread_queen_icon.json
```

---

### Task 5: Add language entries

**Files:**
- Modify: `src/main/resources/assets/iceandfire_dreadland/lang/en_us.json`
- Modify: `src/main/resources/assets/iceandfire_dreadland/lang/zh_cn.json`

**Steps:**
1. Add to `en_us.json`:
   ```json
   "item.iceandfire_dreadland.dread_queen_icon": "Dread Queen Icon"
   ```
2. Add to `zh_cn.json`:
   ```json
   "item.iceandfire_dreadland.dread_queen_icon": "悚怖女王图标"
   ```
3. Keep JSON valid (commas after previous entries where needed).

**Verification:**
```powershell
Select-String -Path src/main/resources/assets/iceandfire_dreadland/lang/en_us.json -Pattern 'dread_queen_icon'
Select-String -Path src/main/resources/assets/iceandfire_dreadland/lang/zh_cn.json -Pattern 'dread_queen_icon'
```

---

### Task 6: Change the creative mode tab icon

**Files:**
- Modify: `src/main/java/com/zonlong/iceandfiredreadland/registry/ModCreativeModeTabs.java`

**Steps:**
1. Change the tab icon from the portal item to the new item:
   ```java
   .icon(() -> new ItemStack(ModItems.DREAD_QUEEN_ICON.get()))
   ```

**Verification:**
```powershell
Select-String -Path src/main/java/com/zonlong/iceandfiredreadland/registry/ModCreativeModeTabs.java -Pattern 'DREAD_QUEEN_ICON'
```

---

### Task 7: Build and verify the jar contents

**Files:**
- No new files.

**Steps:**
1. Run the Gradle build:
   ```powershell
   .\gradlew.bat build
   ```
2. Verify the built jar contains the logo, item model, item texture, and updated metadata:
   ```powershell
   jar tf build/libs/iceandfire_dreadland-0.1.1.jar | Select-String -Pattern 'logo.png|dread_queen_icon'
   ```

**Verification:** Build succeeds; jar contains `logo.png`, `assets/iceandfire_dreadland/models/item/dread_queen_icon.json`, and `assets/iceandfire_dreadland/textures/item/dread_queen_icon.png`.

---

## Notes

- Per clarification, the item ID and English name are `dread_queen_icon` / “Dread Queen Icon”, not `dread_queen_logo`.
- The new item will appear in the creative mode tab because the existing tab already outputs every item in `ModItems.REGISTRY`.
- No gameplay behavior, tooltip, recipe, or loot table is added; this is a display/icon item only.
