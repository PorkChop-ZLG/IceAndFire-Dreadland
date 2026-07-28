# Ice Dragon Trial Boss Design

**Date:** 2026-07-28
**Status:** Approved
**Approach:** Strict active-effects-only configuration (Approach B)

## Problem Statement

Create a repeatable Minecraft 1.21.1 NeoForge trial-spawner boss encounter that spawns exactly one Stage 3 blue Ice and Fire ice dragon, uses separate normal and ominous rewards, and scales the dragon with Strength and Health Boost effects.

## Design

### Architecture

- Use one `minecraft:trial_spawner` item with both `normal_config` and `ominous_config`.
- Spawn exactly one `iceandfire:ice_dragon` in either mode.
- Keep the solution data-only: no Java event handler or Mixin is included.
- Use strict Approach B: declare effects only in `active_effects`; do not mirror effect attribute modifiers in `attributes`.
- Treat the external Attribute Fix mod, which removes the maximum-health attribute bound, as a runtime prerequisite.

### Dragon Identity

Both variants use these fields:

```snbt
AgeTicks:1200000,AgingDisabled:1b,Variant:"blue",Gender:0b,Hunger:50,AttackDecision:1b,Health:20.0f
```

- `AgeTicks:1200000` is exactly 50 days, the first day of Stage 3.
- `AgingDisabled:1b` prevents the boss from aging beyond Stage 3.
- `Variant:"blue"` selects the blue ice-dragon variant.
- Explicit gender, hunger, and attack decision compensate for the skipped `finalizeSpawn` path caused by custom entity NBT.
- A one-tick Instant Health effect is used to fill current health after maximum health is established.

### Effects

Normal mode:

```snbt
active_effects:[{id:"minecraft:health_boost",amplifier:196,duration:-1,show_particles:0b,show_icon:0b},{id:"minecraft:strength",amplifier:13,duration:-1,show_particles:0b,show_icon:0b},{id:"minecraft:instant_health",amplifier:10,duration:1,show_particles:0b,show_icon:0b}]
```

Expected theoretical attributes from the default IAF configuration:

- Stage 3 base maximum health: 212.
- Health Boost: `4 * (196 + 1) = 788`.
- Theoretical maximum health: `212 + 788 = 1000`.
- Stage 3 base attack damage: 8.
- Strength: `3 * (13 + 1) = 42`.
- Theoretical attack damage: `8 + 42 = 50`.

Ominous mode:

```snbt
active_effects:[{id:"minecraft:health_boost",amplifier:255,duration:-1,show_particles:0b,show_icon:0b},{id:"minecraft:strength",amplifier:23,duration:-1,show_particles:0b,show_icon:0b},{id:"minecraft:instant_health",amplifier:10,duration:1,show_particles:0b,show_icon:0b}]
```

Expected theoretical attributes:

- Health Boost is limited by the effect amplifier Codec to 255: `4 * 256 = 1024`.
- Theoretical maximum health: `212 + 1024 = 1236`.
- Strength: `3 * (23 + 1) = 72`.
- Theoretical attack damage: `8 + 72 = 80`.

The originally requested ominous maximum health of 1500 is explicitly not met under strict Approach B. The user approved the 1236 theoretical result rather than adding the missing 264 through an attribute modifier.

### Trial Spawner Configuration

Top-level settings:

```snbt
required_player_range:32,target_cooldown_length:36000
```

Shared settings in both configs:

```snbt
spawn_range:1,total_mobs:1.0f,simultaneous_mobs:1.0f,total_mobs_added_per_player:0.0f,simultaneous_mobs_added_per_player:0.0f,ticks_between_spawn:20
```

- The player scan runs every 20 ticks.
- The first spawn delay after player registration remains the vanilla hard-coded 40 ticks.
- `ticks_between_spawn:20` does not change that initial delay and has no practical impact when only one mob is spawned.
- Player scaling is disabled, so multiplayer never increases the dragon count.
- `spawn_range:1` keeps the entrance position close to the spawner.
- The repeat-challenge cooldown is the vanilla default of 36000 ticks (30 minutes).

### Rewards

Normal completion loot table:

```text
iceandfire_dreadland:spawners/trial_chamber/ice_dragon
```

Ominous completion loot table:

```text
iceandfire_dreadland:spawners/ominous/trial_chamber/ice_dragon
```

The ominous combat item-spawner mechanic is disabled with:

```snbt
items_to_drop_when_ominous:"minecraft:empty"
```

Each registered player receives one reward-generation pass from the applicable completion loot table.

### Data Flow

1. The spawner scans for eligible players within 32 blocks.
2. A normal player selects `normal_config`; Trial Omen or Bad Omen selects `ominous_config`.
3. After the hard-coded 40-tick registration buffer, the spawner attempts to place the single dragon within range 1.
4. The dragon is registered in `current_mobs` and no second dragon can spawn.
5. When its health reaches zero, `Entity.isAlive()` becomes false even if the IAF lootable corpse remains.
6. The spawner untracks the UUID, waits 40 ticks, and begins reward ejection.
7. After all registered players receive rewards, the spawner enters a 36000-tick cooldown.
8. The encounter resets and can be challenged again after cooldown.

## Error Handling

- The arena must provide enough collision-free space and a clear line of sight from the spawner to the spawn point.
- Trial spawners stop tracking mobs farther than 47 blocks away. The arena should keep the flying dragon within about 40 blocks of the spawner to prevent premature completion.
- If an ominous player is detected during a normal fight, vanilla conversion discards the normal dragon and resets the encounter into ominous mode.
- Strict Approach B has a source-level risk: direct NBT loading inserts `active_effects` without invoking the normal `addEffect/onEffectAdded` path. Effects may exist in NBT without installing their attribute modifiers.
- Attribute Fix must be present wherever the encounter runs. It does not raise the effect amplifier limit beyond 255.
- IAF configuration changes to `dragon.maxHealth` or `dragon.attackDamage` change the Stage 3 base values and invalidate the theoretical totals.

## Testing Strategy

After spawning each variant, inspect its attributes:

```mcfunction
/attribute @e[type=iceandfire:ice_dragon,sort=nearest,limit=1] minecraft:generic.max_health get
/attribute @e[type=iceandfire:ice_dragon,sort=nearest,limit=1] minecraft:generic.attack_damage get
```

Expected values under strict Approach B:

| Mode | Maximum health | Attack damage |
|---|---:|---:|
| Normal | 1000 | 50 |
| Ominous | 1236 | 80 |

Inspect identity and effects:

```mcfunction
/data get entity @e[type=iceandfire:ice_dragon,sort=nearest,limit=1] AgeTicks
/data get entity @e[type=iceandfire:ice_dragon,sort=nearest,limit=1] Variant
/data get entity @e[type=iceandfire:ice_dragon,sort=nearest,limit=1] active_effects
```

Expected identity is `AgeTicks=1200000` and `Variant="blue"`. If effect records exist but attributes remain at the Stage 3 base values of 212 and 8, Approach B has failed in the runtime environment and must be replaced with mirrored modifiers or code-driven `addEffect` calls.

Complete both variants with two or more registered players to verify per-player reward ejection, ominous loot selection, corpse-independent completion, and the 30-minute cooldown state.

## Decisions Made

- Use strict Approach B despite the direct-NBT effect application risk.
- Accept ominous theoretical maximum health 1236 instead of 1500.
- Keep the encounter purely data-driven.
- Fix the dragon at exactly Stage 3 and blue color.
- Disable multiplayer mob-count scaling.
- Detect players within 32 blocks.
- Accept the hard-coded 40-tick first-spawn buffer while retaining `ticks_between_spawn:20`.
- Disable ominous combat item spawning.
- Use the normal 30-minute trial-spawner cooldown.

## Non-Goals

- No Mixin to reduce the initial 40-tick spawn delay.
- No Java event handler to apply effects after spawning.
- No attribute modifier to fill the missing 264 ominous health.
- No changes to the two existing ice-dragon loot tables.
- No attempt to keep the dragon tracked outside the vanilla 47-block limit.

## Next Steps

Create the final `/give` command from this design, validate its SNBT structure, and test both trial states in a development world before embedding it in a structure or data pack.
