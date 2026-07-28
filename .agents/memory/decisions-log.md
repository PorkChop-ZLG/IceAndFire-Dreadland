# Decisions Log

## 2026-07-28: Ice Dragon Trial Boss

- Use one trial spawner with normal and ominous configurations for the repeatable ice-dragon encounter.
- Spawn one blue ice dragon fixed at 50 days (Stage 3), with no multiplayer mob-count scaling.
- Use strict `active_effects`-only Approach B. Do not mirror effect modifiers in entity attributes.
- Normal theoretical target is 1000 maximum health and 50 attack damage.
- Ominous theoretical result is 1236 maximum health and 80 attack damage because effect amplifier is capped at 255; the user accepted this instead of adding a 264-point modifier.
- Attribute Fix is a runtime prerequisite for health above the vanilla attribute bound.
- Use a 32-block player detection range, range-1 spawning, a 30-minute cooldown, and the vanilla hard-coded 40-tick first-spawn buffer.
- Disable ominous combat item spawning and use the existing normal/ominous ice-dragon completion loot tables.
- Arena design must keep the dragon within the trial spawner's 47-block tracking radius.
