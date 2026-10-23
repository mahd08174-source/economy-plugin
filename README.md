# EconomyPlugin (DerekSMP)

Paper 26.2 economy plugin (Java 25).

- `/bal [player]` - balance
- `/pay <player> <amount>` - amounts accept k, m, b, t (e.g. `100m`, `1.5k`)
- `/shop` - paged GUI with 8 categories: Blocks, Decoration, Farming & Food, Combat & Tools, Redstone, Potions, Spawners, Miscellaneous (left-click buy 1, shift-click buy 16, right-click buy 64; Back / Previous / Next buttons)
- `/sell` - sell the item in your hand (50% of shop price); `/sell all` sells everything sellable except gear
- `/tpa <player>`, `/tpahere <player>`, `/tpaccept`, `/tpdeny`
- `/rtp` - random teleport inside the world border (overworld only, safety-checked, max 10,000 blocks from centre)
- `/sethome <name>`, `/home <name>`, `/delhome <name>` - up to 10 homes per player (`/home` alone lists them)
- Admin only (player Arv1oishated, console excluded): `/give <username> <money>`, `/remove <username> <money>`, `/kick <player> [reason]`, `/ban <player> [reason]`, `/wipe <player>` (reset balance + homes), `/removeall <player>` (set balance to $0)
- Sidebar shows your username and cash. New players start with $200,000 (all shop prices and the starting balance are 200x the original).
