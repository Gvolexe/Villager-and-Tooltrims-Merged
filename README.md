# VillagerToolTrims

Merged Purpur/Paper plugin for Minecraft 1.21.11.

## Included functionality

- Villagers, zombie villagers, and wandering traders can be picked up and placed with buckets.
- Tool Trims support is installed from the official Tool Trims 3.0.1-beta data/resource pack.
- Players are prompted for the villager-bucket and Tool Trims resource packs on join.

## Build

```bash
JAVA_HOME=/var/lib/flatpak/app/com.google.AndroidStudio/x86_64/stable/facf2da2e53725c0ba92aaa4e9dc35dc736b3083f2fb72e127bc6717f723802e/files/extra/jbr ./gradlew build
```

The plugin jar is written to `build/libs/VillagerToolTrims-1.0.0.jar`.

## Notes

Tool Trims is downloaded from Modrinth at runtime instead of bundled in this repository. Public servers using Tool Trims should credit the original Tool Trims project: https://modrinth.com/datapack/tool-trims
