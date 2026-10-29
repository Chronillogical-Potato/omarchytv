# Adding a remote

The "Remote buttons" feature (see `OMARCHYTV.md`) only touches buttons that are listed for a known remote in
`app/src/main/assets/remotes.json`. Adding your remote is a data change: one entry in that file, no code.

A key is intercepted only if all three hold: it has a binding in `"keys"`, it comes from an input device whose
USB/Bluetooth vendor and product id match a profile, and that profile lists the button. Everything else passes
through untouched.

## 1. Find the vendor and product id

```
adb shell dumpsys input
```

Find your remote under the input devices; its `Identifier:` line shows `vendor=0x0955, product=0x7217`. Use the
four-digit hex values without `0x` in `match` (`"0955"`, `"7217"`). A remote can list several pairs (hardware
revisions, or USB vs Bluetooth).

## 2. Find the keycode of a button

1. `adb shell getevent -lt /dev/input/eventN` (N from the `Devices:` list in dumpsys input), press the button
   and note the scancode (`KEY_...` or `scancode`; the number in `getevent` without `-l` is hex).
2. The dumpsys entry for your remote shows `KeyLayoutFile:`, for example
   `/system/usr/keylayout/Vendor_0955_Product_7217.kl`. `adb shell cat` it: the line for your scancode names the
   Android keycode (scancode 393 maps to `BUTTON_12` on the SHIELD remote).
3. Alternatively press the button while watching `adb logcat` for the key event (`keyCode=`), or look the number
   up in the `KeyEvent.KEYCODE_*` constants.

In `remotes.json` the key is the keycode name without `KEYCODE_`: `BUTTON_12`, `PROG_RED`, ...

## 3. Check that the key reaches the accessibility filter

Not every button can be remapped from an app. Some are consumed by the system before accessibility services see
them.

1. Enable the service (Settings, Accessibility, "OmarchyTV remote buttons").
2. Add your entry to `remotes.json` (or test with a debug build), and add a binding for the key in
   `omarchytv.json`, e.g. `"keys": {"BUTTON_12": {"single": {"action": "walker"}}}`.
3. Press the button. If the binding runs and the vendor behaviour does not, the button works.
4. If the vendor action still happens (or the launcher binding never runs), the system handles that key in
   `interceptKeyBeforeQueueing`, before the accessibility filter, and the button cannot be supported this way.
   Example: the SHIELD mic key (`ASSIST`). For an assistant key the route is the Assistant role (Settings, Apps,
   Default apps, Assist app), which is outside this launcher.

## 4. Add the entry

```json
{
  "id": "nvidia-shield-2019",
  "name": "SHIELD remote (2019)",
  "match": [{"vendor": "0955", "product": "7217"}],
  "buttons": [
    {"key": "BUTTON_12", "name": "Netflix button", "default": "Opens Netflix (NVIDIA)"}
  ],
  "notes": "Verified on SHIELD TV Pro 2019, Android 11, SHIELD Experience 9.2.4: ..."
}
```

- `id`: unique, lower case, with dashes.
- `name`: what the Remote buttons page shows for the remote; `buttons[].name` and `default` (what the button does
  without a binding) likewise.
- `notes`: say which TV or box, Android version and firmware you verified it on, and anything surprising (which
  hook handles the key, what does not work). Only list buttons you actually tested.

`RemoteProfilesTest` loads the file, so a mistyped hex id, an empty button list or a duplicate `id` fails
`./gradlew :app:testReleaseUnitTest`. Mention your remote and TV in the pull request.
