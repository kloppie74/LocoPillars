# LuckyBlocks — Documentation Moved

The LuckyBlocks system has been completely rewritten.  
The full documentation is now located at:

**`plugins/LocoPillars/Luckyblocks/README.md`**

This file is auto-extracted to your server's `Luckyblocks/` folder every time the server starts,
so it always reflects the latest version.

---

## Quick Reference

Each lucky block lives in its own folder:

```
plugins/LocoPillars/Luckyblocks/
└── my_luckyblock/
    ├── Settings.json   <- id, name, icon, rarity
    └── Luckyblock.kts  <- Kotlin script (player + game API available)
```

Reload without restarting: `/luckyblocks reload`
