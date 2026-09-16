# virtual-inventories

Dupe-proof, packet-based virtual inventories for Paper 1.21. No `player.openInventory()`,
no backing `Inventory` instance — windows exist only as protocol state. There is nothing
to duplicate from, because there is nothing.

- **Core (`:core`)** — mechanism only: menu compilation, packet I/O, sessions, hooks.
  Deliberately contains **zero game logic** (no trade matching, no crafting rules).
  All outcomes belong to the integrating plugin via actions and hooks.
- **Demo (`:demo`)** — reference implementation: 16 menus, live furnace ticker,
  merchant trade simulation, paginated navigation. Copy patterns from here, not from the core.

`de.kyle.virtualinventories` · Java 21 · Paper 1.21.x · PacketEvents 2.13.0

## How the dupe-proofing works

1. **Fake windows.** `OpenWindow` / `WindowItems` / `SetSlot` / `CloseWindow` are sent
   directly via PacketEvents. The server never creates an `InventoryView`, so shift-click,
   drag, drop, number keys and creative pick-block have no vanilla handler to exploit.
2. **Cancel everything.** While a session is open, every `ClickWindow`, `CloseWindow`,
   `ClickWindowButton`, `CreativeInventoryAction` and (for anvils) `NameItem` packet for
   that window is cancelled on the Netty thread. No client packet ever mutates server state.
3. **Server-side truth, client mirrors it.** All mutations (deposit transfer, output take,
   cursor content) happen on Bukkit objects on the main thread; afterwards the client is
   forcibly resynced (`SetCursorItem` from `player.getItemOnCursor()`, full `WindowItems`
   refresh). Ghost items are deleted, real items survive.
4. **Per-player window IDs** (never 0, pooled 1–100), one session per player, cleanup on
   quit/kick/death. No shared mutable slot state: static slots are pre-built once and only
   ever sent as clones.

## Architecture

```
menus/*.yml ──compile──▶ *.vmenu.gz ──load──▶ CompiledMenu ──open──▶ MenuSession
  (human)      (validate,       (VMNU binary,   (static items     (packets only,
                parse ph.,       gzip, CRC)      materialized       hooks fire,
                collect keys)                     1x)               client resynced)
```

The provider **only ever loads compiled blobs** — including the in-memory path, which
compiles YAML → blob → loads the blob (dogfooding the codec). Stale blobs are detected
via embedded source SHA + CRC32; old format versions fail with an explicit recompile hint.

## Quickstart

```java
// onEnable
VirtualInventories.init(this);
var menus = VirtualInventories.api().menus();
menus.placeholders().register("balance", p -> String.valueOf(economy.get(p)));
menus.action("buy_diamond", ctx -> {
    Player p = ctx.player();
    if (economy.withdraw(p, 64)) {
        p.getInventory().addItem(new ItemStack(Material.DIAMOND));
        menus.refreshDynamic(p); // re-resolve placeholders, live-update open menu
    }
});
menus.loadDirectory(); // dataFolder/menus/*.yml → compiled/*.vmenu.gz

// open (extra map feeds %keys% not covered by resolvers)
menus.open(player, "shop");
```

```java
// onDisable
VirtualInventories.shutdown();
```

## Menu definitions (YAML)

```yaml
id: furnace_demo
type: FURNACE            # CHEST (default) | ANVIL | FURNACE | BLAST_FURNACE | SMOKER
                        # BREWING_STAND | MERCHANT | ENCHANTMENT | STONECUTTER | LOOM
                        # GENERIC_3X3 | CRAFTER_3X3 | HOPPER | SHULKER_BOX
rows: 1                 # CHEST only (1-6); fixed-size types omit it
title: "<gold>Furnace <gray>(%player%)"
slots:
  0: { material: AIR }  # deposit/output slots must be AIR templates
  2: { material: AIR }  # (filled at runtime, never from the template)
  12: { ref: demo_crown }  # full NBT stack from items.yml (see below)
deposit: [0, 1]         # player-placeable slots (type whitelist enforced)
output: [2]             # take-out slots: registered action fires, else default hand-over
data: { 0: 200, 1: 200, 2: 0, 3: 200 }   # container-data (furnace flame/arrow, ...)
buttons: { 0: enchant_1 }                # ClickWindowButton id → action id
trades:                                  # MERCHANT only
  - { buy_a: EMERALD, buy_a_count: 5, result: DIAMOND_SWORD, max_uses: 3 }
```

Items support `material`, `amount` (int or single `%key%`), `name` / `lore` (MiniMessage
with `%key%` placeholders, `%%` escapes), `flags`, `custom_model_data`. Slot `action:`
binds a click to a registered handler.

### Named items (`ref:`) — full NBT stacks

Templates (`material:` + MiniMessage) are portable and stay the default. When you need a
fully serialized stack — NBT included, exactly as built in-game — reference it by ID
instead of describing it. Nothing mixes: a `ref:` slot takes only an optional `amount`
override, no `material`/`name`/`lore`/`flags`, and placeholders do not apply inside refs.

Named items live in `items.yml` (flat `id → Base64` map) and are served by an
`ItemProvider`. The core ships `FileItemProvider` (`items.yml` in the plugin data
folder; the demo's `/vitem export` writes the cursor stack under a new ID):

```java
menus.setItemProvider(FileItemProvider.fromDataFolder(this));
```

Mechanics: the compiled blob stores **only the ID**, so items reload without
recompiling menus; unknown IDs fail fast at load; stacks are materialized once and
cloned per open (64 KB cap per blob).

⚠ Version binding: blobs use Bukkit's `serializeAsBytes` and decode **only on the
server version that wrote them** — menu blobs stay portable across 1.21.x, item blobs
deliberately don't. Regenerate `items.yml` after a version upgrade. The codec is public
API: `ItemBlobs.encode(stack)` / `ItemBlobs.decode(base64)`.

Supported window types and their vanilla registry IDs (1.21):

| Type | ID | Slots | Notes |
|---|---|---|---|
| `CHEST_9X1`…`CHEST_9X6` | 0–5 | 9–54 | rows 1–6 |
| `GENERIC_3X3` | 6 | 9 | dispenser/dropper look |
| `CRAFTER_3X3` | 7 | 9 | ⚠ not the anvil — the ID gap is real |
| `ANVIL` | 8 | 3 | rename pipeline + deposit slots |
| `BREWING_STAND` | 11 | 5 | container data 0–1 |
| `ENCHANTMENT` | 13 | 2 | levels via container data 0–2, buttons 0–2 |
| `FURNACE` | 14 | 3 | container data 0–3 |
| `HOPPER` | 16 | 5 | |
| `LOOM` | 18 | 4 | options-UI pattern (see demo) |
| `MERCHANT` | 19 | 3 | offers packet + trade simulation in demo |
| `SHULKER_BOX` | 20 | 27 | |
| `SMOKER`/`BLAST_FURNACE` | 22/10 | 3 | same data layout as furnace |
| `STONECUTTER` | 24 | 2 | options-UI pattern (see demo) |

Rule of thumb: `CHEST` for navigation, `ANVIL` for text input, furnace/brewing for
progress visuals, `MERCHANT` for offers, stonecutter/loom as clickable option grids.

## Placeholders, actions, hooks

- **Placeholders** (`%key%`): resolved once per open into a scope, then patched into
  dynamic slots by index. Precedence: `extra` map → registered resolvers →
  PlaceholderAPI bridge (optional, reflective, no hard dependency) → `""`.
  Unknown keys fail fast at load, never silently at runtime.
- **Actions** (`menus.action(id, handler)`): slot clicks, output takes, button presses.
  Handlers receive a `ClickContext` (player, session, slot, click type, item snapshot).
- **Hooks** (`menus.hooks(menuId, onOpen, onClose, onDeposit)`): `onDeposit` fires with
  the changed slot (`-1` = player-inventory side, e.g. shift-click). Use it for previews
  (merchant result, stonecutter options) and live state.

## Binary format (`menu-format: 4`)

`VMNU` magic · u8 version · sha256(source) · menuId · rows · window type · deposit/output/
data/buttons/trades sections · `ref` slots (ID only) · CRC32 — all gzip-compressed. DTO-level (material keys,
MiniMessage segments), never NMS bytes, so menu blobs stay portable across 1.21.x patch
versions (`ref` IDs resolve against the version-bound `items.yml` at load).
`MenuCodec` round-trips losslessly; tampered blobs are rejected.

## Build, test, run

```bash
./gradlew build          # core + demo, 71 unit tests
./gradlew :demo:shadowJar # fat jar → docker/plugins/
docker compose up -d     # Paper 1.21.8, PacketEvents/Via* via docker/plugins
```

E2E: `./gradlew :demo:plugwrightTest` — boots a real Paper 1.21.8, drives Mineflayer bots
through 22 specs (open/title, no-dupe click, lore refresh, pagination, anvil rename,
merchant offers, item refs, reload). Dev loop notes: test server uses game port **25566** and RCON
**25576** (the docker dev server owns 25565/25575); specs live in `demo/src/test/e2e/tests/`.

In-game demo commands: `/vmenu [id]` · `/vpaged` · `/vname` · `/vitem` · `/vreload`.
To join with any 1.21.x client, the docker server ships ViaVersion + ViaBackwards.

## Status

Working: all types above, rename pipeline, deposit/output/button primitives, merchant
offers + demo-side trade simulation, furnace ticker demo. Deliberately out of scope:
a database blob store (the `CompiledMenuStore` seam exists; not planned), game rules in
the core, pre-1.21 clients.

## License

MIT — see [LICENSE](LICENSE). Commercial use, forks, and closed-source derivatives allowed.
