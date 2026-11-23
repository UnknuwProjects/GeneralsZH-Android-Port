# Publishing an update without a new APK

The launcher checks `updates/` on the `custom-driver` branch -- from **Home → Updates** (on start and
with **Check for updates**) and when the **GeneralsOnline account** screen opens -- and takes two
kinds of update from it. The settings are applied by either check and shown on the account
screen; the engine is downloaded only by the Home check.

- **Settings** (`update/config.json` in the main tree): values the engine reads at startup. Today
  the STUN and TURN server lists (`stun_servers`, `turn_servers`), the PC client checksum for
  cross-play (`pc_exe_crc`, computed with `scripts/update/pc-exe-crc.py`) and the community data
  patch manifest address (`datapack_manifest_url`), and whether the current PC release ends its
  logic checksum with the GeneralsOnline revision tag (`logic_crc_revision`, `1`/`0`; 100126 does
  not -- used only when the launcher has not read it from the data package's PC executable).
  A missing key keeps the value built in.
  The community data patch itself comes from that manifest and is updated on the multiplayer
  screen (GeneralsOnline account → Online game data), which checks it by itself and installs a
  newer one on Wi-Fi; the Updates card only says when a newer one is out. It also computes the PC checksum
  from the PC executable inside that patch, and that number wins over `pc_exe_crc`, so a new PC
  release normally needs nothing published at all; `pc_exe_crc` covers players without the patch.
- **Feature switches** (engines from 3278, launchers after 1.4.0): `name=1` / `name=0` in the
  settings turns a feature on or off for everyone, missing keeps the build's default. Each one is
  listed in `update/features.json` -- what it does, its default, which builds read it and which do
  not (those need an engine update to follow it), and companion keys. Set them with
  `scripts/update/features.py`, then publish settings as usual:
  `features.py list` (value here and in the published manifest), `features.py off pc_crossplay`,
  `features.py on pc_crossplay pc_exe_crc=<PC number>`. `publish-update.py` refuses a switch that
  is not `0` or `1`. A new switch: an entry in `features.json`, and
  `GXRemoteConfig::enabled("name", default)` in the engine or
  `UpdateManager.featureEnabled(ctx, "name", default)` in the launcher where it applies. Today:
  `pc_crossplay` (Android-PC cross-play, off since 18/10/2026 at GeneralsOnline's request).
  A switch that guards something other players depend on is read in the engine with
  `GXSignedSettings::enabled("name")` instead: the engine checks the manifest's signature itself
  (the launcher saves `manifest.json.sig` next to `manifest.json`), so a patched launcher or a
  hand-edited `remote_config.ini` cannot turn it on. Only a rebuilt engine can -- the source is
  open, so the server remains the final word. Note for later: once such a switch has been
  published as `1` and then back to `0`, the older signed manifest could be put back by hand;
  if that matters, give the engine a minimum serial in the same engine update.
- **Data package manifest mirror**: while `datapack_manifest_url` points at the updates directory
  (`.../custom-driver/updates/datapack-manifest.json`), `publish-update.py` copies the GeneralsOnline CDN manifest
  there with its fields trimmed -- the CDN's `sha256` starts with a space, and launchers up to 1.3.0
  compare it untrimmed, failing every install with "checksum mismatch". The script downloads the
  package and refuses to publish unless the trimmed digest and size match it. Newer launchers ignore
  a mirror address and read the CDN themselves. The mirror is as current as the last publish: after
  a new GeneralsOnline release, publish settings again so 1.3.0 players see it. Point the key back at
  `https://cdn.playgenerals.online/manifest.json` once the CDN fixes the digest.
- **Support card** (launchers built from 03/10/2026 on): the Help page's "Support the project"
  card comes entirely from `update/support.json` -- its text in every language, and the
  addresses/links. `publish-update.py` copies the file to `support/<digest>.json` (a new name for every content, so
  GitHub's five-minute per-file cache cannot pair a new manifest with the old file) and writes its
  SHA-256 into the signed manifest, so it is as trusted as the manifest. Nothing of it is in the
  APK, so a new or retired address, a reworded text, or a language added or dropped is just a
  settings publish. Format: `text` maps a language tag (`en`, `ru`, `pt-BR`, `isv`, ...) to
  `title`, `body`, `warning`, `copy_hint`, `copied` (`%s` = the entry's label); `entries` is a list
  of `{ "label", "value" }`, where `label` is a string or a per-language object. A value starting
  with `https://` opens in the browser, anything else is copied. The player's language falls back
  to `en`, which the script requires. Deleting `update/support.json` before a publish withdraws
  the card everywhere.
- **Engine**: a newer `libmain.so` / `libmain60.so`. It is downloaded into the app's private
  storage and used from the next game start, instead of the engine inside the APK.

Nothing is used unless it is signed with the update key.

## Two channels: releases and test builds

Releases read `updates/` on the `custom-driver` branch. Test builds (packaged with
`GX_UPDATE_CHANNEL=test`, which writes `assets/update_channel.txt`) read `updates-test/` on the
same branch instead, so a test engine reaches testers over the air and is never offered to
players. Each channel keeps its own serial on the device, and an engine downloaded from one
channel does not run after an APK of the other channel is installed. Same key, same signature
check.

This is this fork's layout. Upstream keeps a separate `updates` branch for each channel; this
fork serves the manifest from `custom-driver` instead, like the language packs, so everything
the launcher fetches comes from one branch.

A test engine is published from a later test build than the one testers have (higher commit
count) with the same libraries; `requires_libs` must match the test APK they installed.

## How it is protected

- `manifest.json` is signed with an ECDSA P-256 key. The launcher carries only the public key
  (`UpdateManager.PUBLIC_KEY_B64`) and refuses a manifest whose signature does not verify.
- The engine files are checked against the SHA-256 and size written in the signed manifest.
- Every manifest has a `serial`; the launcher never accepts a lower one than it has seen, so an
  old signed manifest cannot be replayed to roll players back.
- A downloaded engine runs only if its build number is higher than the APK's own engine
  (`assets/engine_build.txt`, the commit count it was built at) and only on an install whose
  other native libraries are byte-for-byte the ones it was built with (`requires_libs`). A change
  to SDL, OpenAL, DXVK or anything else in `lib/` therefore still needs a new APK; the launcher
  says so.
- An updated engine that twice fails to reach the main menu is dropped, and the APK's own
  engine runs again.

Nothing in the manifest is secret -- the service addresses are public anyway -- so it is signed,
not encrypted. Encryption would need the key inside the APK, where anyone can take it out.

## The key

`unknuw-generalszh-update-signing-key.pem` is the fork's private key (ECDSA P-256, made
23/11/2026). It replaces upstream's key: upstream's private key is not ours, so a manifest we
publish could never verify against upstream's public key. Keep the private key out of the
repository -- it lives only as a file outside the repo, and anyone holding it can publish an
update every player trusts. Signings are done locally with it (see Publishing below); there is
no Actions secret for it yet. If the key is ever lost or leaked, a new one means a new APK
with its public key.

## Publishing

The manifest and its signature are plain files on `custom-driver`, under `updates/` (releases)
or `updates-test/` (test builds). To publish a new one: bump `serial`, write the manifest, sign
it with the fork's key, and commit both files on `custom-driver`:

```bash
openssl dgst -sha256 -sign unknuw-generalszh-update-signing-key.pem \
    -out /tmp/manifest.json.sig.bin updates/manifest.json
base64 -w0 /tmp/manifest.json.sig.bin > updates/manifest.json.sig
# then commit updates/manifest.json and updates/manifest.json.sig
```

The signature is over the exact bytes of `manifest.json`, so sign after the last edit and do
not touch the file afterwards. Verify before committing -- a bad signature makes every device
report a signature error:

```bash
openssl pkey -in unknuw-generalszh-update-signing-key.pem -pubout -out /tmp/upd-pub.pem
openssl dgst -sha256 -verify /tmp/upd-pub.pem \
    -signature /tmp/manifest.json.sig.bin updates/manifest.json
```

Settings only (after editing `update/config.json`). The engine already published is kept, files
and all -- without it a player who reinstalls the APK would run the APK's own engine again.

Note: `scripts/update/publish-update.py`, `push-updates-branch.sh` and the Actions "Sign update"
workflow still target upstream's separate `updates` / `updates-test` branches. Do not use them
until they are updated for this layout; publish by hand as above.

### Monthly heroes (support card)

The card shows the current month only: a "heroes" `rows` block with the month's supporters
(amounts in US dollars) and a `progress` block for the month's total against the subscription. At
the end of a month the rows go to the README's "Heroes of the project" history, the card's rows
block is removed and the progress goes back to 0 (a block with no rows is not shown). The
"thanks" announcement (`popup`, shown once per id) is only for a moment worth announcing.
