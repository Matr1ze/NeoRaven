# NeoRaven

A Minecraft **1.8.9** Forge client with a Material 3 ("Material You") interface: a fully custom
ClickGUI, a Material HUD suite and a large module set, rebuilt from the Raven B4 / Onyx lineage.

> Forge `1.8.9-11.15.1.2318`, Java 8, single mod jar. Client-side only.

---

## Features

**Material 3 interface**

- **Onyx ClickGUI** — open with **Right Shift** (or the `Onyx ClickGUI` module in the `client`
  category). Sidebar categories, search field, animated cards, per-module detail pages with
  toggle / mode / slider / colour / keybind editors, breadcrumbs and page transitions.
- Animated main menu with a wallpaper picker (Rainy Day, Mica, Onyx clouds, Android gradients and
  a parallax image), dark/light theme and a **frosted glass** backdrop; the Android-style menus
  (singleplayer, multiplayer, settings, accounts, proxy) share the same look.
- Custom `GuiScreen` background, screen-open animation and a Google Sans Flex / Material Icons
  typeface stack with CJK fallback.

**HUD**

Dynamic Island watermark (with music / compass / bed counters), array list, keybinds, scoreboard,
tab list, target HUD, potion HUD, nametags, info bar, hotbar — all Material 3, all draggable through
their own *Edit position* screens, all with an optional frosted-glass background (`.blur on|off`).

**Modules** — 129 modules in 11 categories (combat, movement, player, world, render, network,
minigames, other, client, profiles, scripts), including

- combat: KillAura, AimAssist, AutoBlock, AutoGapple, **AutoRod**, Reach, Velocity, WTap, Hit Select…
- movement: Scaffold, Speed, Flight, LongJump, NoSlow, InvMove, Stasis…
- player: InvManager, **InvManager2**, **ChestStealer**, AutoTool, AutoPlace, BedAura, WaterBucket…
- render: ESP/nametags/tracers, Skybox, Wings, JumpCircles, HandOutline, BlockOverlay, Zoom, Freelook,
  ArrayList, Watermark, TargetHud, Keybinds, PotionHud…
- minigames: Bed Wars, **Teams**, Sky Wars, Duels Stats, Murder Mystery, Speed Builders, Sumo Fences,
  AutoRequeue, AutoWho
- and a script API (`scripts` category) for user-written modules.

**Other systems**

- **Teams** — one configurable teammate rule (scoreboard colour / dyed armour colour / `[A]` name tag /
  vanilla) that every target module honours.
- **Proxy** menu: SOCKS5, HTTP and **FisProxy** (API token + service id, start/stop, change IP,
  one-click join the session).
- **Account manager**: Microsoft OAuth (device-style flow with PKCE), refresh-token and offline
  accounts, per-account proxy.
- Profiles, friends/enemies, config persistence in `.minecraft/keystrokes/`.

---

## Requirements

| | |
| --- | --- |
| Minecraft | 1.8.9 |
| Forge | 11.15.1.2318 or newer for 1.8.9 |
| Java | 8 (the build targets Java 8; any newer JDK works as the toolchain host) |
| Gradle | 7.6.4 (the wrapper is included) |

## Build

```bash
./gradlew build          # Windows: gradlew.bat build
```

The remapped mod jar lands in `build/libs/NeoRaven.jar`. Install it by dropping the jar into
`.minecraft/mods/` and launching the Forge 1.8.9 profile.

A first build downloads the Minecraft 1.8.9 assets, mappings and Forge through Loom, so it needs
network access; later builds work offline.

## Usage

- **Right Shift** — ClickGUI. Every module can be bound with `.bind <module> <key>`.
- **`.help`** — command list in chat: `.toggle`, `.bind`, `.find`, `.hide`, `.hidekey`, `.keysize`,
  `.blur`, `.friend`, `.enemy`, `.cname`, `.panic`, `.resetgui`, `.renameprofile`, `.fps`, `.ping`,
  `.play`, `.requeue`, `.time`, `.track`, `.rotate`, `.limbo`, `.clear`, `.ign`, `.fakechat`, `.sb`.
- Settings, profiles, accounts and the friends/enemies list live in `.minecraft/keystrokes/`
  (`config.txt`, `profiles/`, `accounts.json`, `menu_theme.txt`, `menu_background.txt`, …).
  Deleting those files restores the shipped defaults (Rainy Day wallpaper, dark theme, frosted glass).

## Project layout

```
src/main/java/xyz/daikirai/
  gui/onyx/          Material 3 UI: ClickGUI, menus, HUD widgets, render primitives
  module/            module framework, settings, 130 modules by category
  mixin/             Forge/Mixin hooks into vanilla 1.8.9
  client/            config, chat commands, Hypixel state, bot checks
  account/           Microsoft auth, alt accounts, SOCKS5/HTTP/FisProxy transport
  script/            script API
  render/            ‎3D/2D renderers (skybox, blur, hand outline, jump circles)
src/main/resources/
  assets/clickgui/   module defaults (modules.json), menu textures
  assets/onyx/       fonts, shaders, effect textures, font licences
  clickgui.mixins.json
```

## Credits

Built on the **Raven B4** client and the **OpenOnyx** Material You interface. The module defaults
(`assets/clickgui/modules.json`) are the single source of truth for module names and settings.
Bundled fonts and shaders keep their original licences (`assets/onyx/licenses`).

## Disclaimer

This project is published for educational and research purposes only. It is **not** affiliated with
Mojang, Microsoft or Hypixel, and using a client modification may violate the rules of the server you
play on. You are responsible for how you use it — no warranty of any kind is provided.

## License

NeoRaven is free software: you can redistribute it and/or modify it under the terms of the
**GNU General Public License, version 3**, as published by the Free Software Foundation.

Because the GPL is a copyleft license, any redistributed version (modified or not) has to stay under
the GPLv3 and come with its corresponding source. There is **no warranty**, to the extent permitted by
law. The full text is in [LICENSE](LICENSE).

```
NeoRaven — Material 3 Minecraft 1.8.9 client
Copyright (C) 2026 NeoRaven contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, version 3 of the License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```

Bundled third-party assets (Google Sans Flex, Material Icons, the Onyx shaders) keep their own
licenses, listed under `src/main/resources/assets/onyx/licenses`.

---

## 中文速览

NeoRaven 是一个 **Minecraft 1.8.9 Forge** 客户端，界面是 Material 3 / Material You 风格。

- **构建**：`gradlew build` → `build/libs/NeoRaven.jar`，丢进 `.minecraft/mods/` 即可。
- **打开 ClickGUI**：右 Shift（也可以在 `client` 分类里点 `Onyx ClickGUI` 模块）。
- **配置目录**：`.minecraft/keystrokes/`；删掉里面的文件就恢复出厂设置
  （Rainy Day 壁纸 + 暗色主题 + Frosted Glass 模糊）。
- **命令**：游戏里输入 `.help` 看全部。
- **许可证**：GNU GPL v3（见 `LICENSE`）—— 可自由使用、修改、再分发，但再分发时必须继续用 GPLv3
  并提供对应源码；无任何担保。
- 仅用于学习研究，与 Mojang / Microsoft / Hypixel 无关，使用风险自负。
