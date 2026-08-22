# Dread Queen Portrait Design Document

**Date:** 2026-08-22
**Status:** Approved
**Approach:** Pure block-based 3×3 painting with static baked JSON models; reuse `dread_queen_portrait` item as placement/drop item.

## Problem Statement

Add a 3×3 painting named “悚怖女皇画像 / Dread Queen Portrait” to the mod. It must be a standalone block (not a vanilla painting entity), rendered with baked models for performance. Breaking the painting block drops the `dread_queen_portrait` item, which can be used to place it again.

## Design

### Architecture

- A new block `iceandfire_dreadland:dread_queen_portrait` represents the painting.
- One painting is made of 9 blocks in a 3×3 grid (master + 8 offset blocks).
- Each block uses a static JSON baked model with UVs cropped from a 192×192 texture.
- No block entity, no block entity renderer, no Moonlight dependency.
- The `dread_queen_portrait` item (renamed from `dread_queen_icon`) right-clicks a wall to place the whole 3×3 structure.

### Components

1. **Painting texture**
   - Source: `D:\Minecraft\IceAndFire-Dreadland\lib\悚怖女王-image2-192x.png`
   - Destination: `src/main/resources/assets/iceandfire_dreadland/textures/painting/dread_queen_portrait.png`
   - Resolution: 192×192 (64×64 per block cell).

2. **Item icon texture**
   - Scaled from the 192×192 source to 64×64.
   - Destination: `src/main/resources/assets/iceandfire_dreadland/textures/item/dread_queen_portrait.png`

3. **Block**
   - `src/main/java/com/zonlong/iceandfiredreadland/block/DreadQueenPortraitBlock.java`
   - Extends `HorizontalDirectionalBlock`.
   - Block state properties: `facing`, `x_offset` (0–2), `y_offset` (0–2).
   - Manages multi-block structure validation and removal.
   - Drops `dread_queen_portrait` item when the master block is removed.

4. **Item**
   - `src/main/java/com/zonlong/iceandfiredreadland/item/DreadQueenPortraitItem.java`
   - Extends `Item`, overrides `useOn` to place the 3×3 block structure.
   - Registered in `ModItems` as `DREAD_QUEEN_PORTRAIT` with id `dread_queen_portrait`.
   - Remains the first item and creative tab icon.

5. **Blockstate & models**
   - `assets/iceandfire_dreadland/blockstates/dread_queen_portrait.json`
   - 9 model files:
     - `assets/iceandfire_dreadland/models/block/dread_queen_portrait_0_0.json` … `dread_queen_portrait_2_2.json`
   - Each model renders a 1px-thin wall panel and uses UV crop for its 64×64 cell.

6. **Language entries**
   - `item.iceandfire_dreadland.dread_queen_portrait`: `Dread Queen Portrait` / `悚怖女皇画像`
   - `block.iceandfire_dreadland.dread_queen_portrait`: `Dread Queen Portrait` / `悚怖女皇画像`

### Data Flow

1. Player right-clicks a wall with `dread_queen_portrait`.
2. The item computes the 3×3 grid centered on the clicked block face.
3. It validates all 9 cells are air and the wall behind them is solid.
4. It places 9 blocks with correct `facing`, `x_offset`, `y_offset`.
5. When any block is removed, the block class detects the structure is broken and removes the remaining blocks; the master drop spawns one `dread_queen_portrait` item.

### Error Handling

- Vertical faces are rejected.
- If any cell is obstructed or wall support is missing, placement fails without consuming the item.
- If the painting structure becomes invalid, all remaining painting blocks are removed and one item is dropped.

## Decisions Made

- Use pure block implementation, no `painting_variant`, no vanilla painting entity.
- Use static JSON baked models instead of dynamic custom model loader or Moonlight.
- Use 192×192 painting texture (64×64 per cell).
- Use 64×64 item icon.
- Rename item ID from `dread_queen_icon` to `dread_queen_portrait`.
- Keep the item as the creative tab icon and first registered item.
- Do not add the painting to `#minecraft:placeable`.

## Non-Goals

- No vanilla painting entity compatibility.
- No Moonlight dependency.
- No block entity.
- No recipe or structure generation.

## Next Steps

Invoke the planning skill to create an implementation plan.
