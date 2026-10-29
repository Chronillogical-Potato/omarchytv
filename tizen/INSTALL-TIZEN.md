# OmarchyTV for Samsung Tizen: install guide (`2.13.0-beta`, untested on hardware)

The Tizen build reuses the LG webOS web app in `webos/app/`. Only three files are Tizen-specific:
`config.xml` (the widget manifest), `tizen-shim.js` (remote keys and Samsung app IDs) and
`build-wgt.sh`. Every feature of the webOS build is available, with the same limits. It is a normal
app, not a replacement for Samsung's home screen.

> Status: the package builds and its structure is checked by script. It has **not** been installed
> on a real Samsung TV. The Samsung app IDs in `tizen-shim.js` (YouTube, Netflix) are the commonly
> published store IDs and have not been verified.

## 1. Build the package

```bash
cd tizen
./build-wgt.sh            # -> tizen/out/OmarchyTV-tizen-2.13.0-beta-unsigned.wgt + SHA-256
```

Samsung TVs only install signed packages. To sign the package, install Tizen Studio with the
**TV Extensions** and create a **Samsung** certificate profile in Certificate Manager. This needs a
free Samsung account; the distributor certificate is tied to your TV's DUID. Then run:

```bash
TIZEN_PROFILE=<your-profile-name> ./build-wgt.sh   # -> OmarchyTV-tizen-2.13.0-beta.wgt (signed)
```

## 2. Put the TV in Developer Mode

1. On the TV, open **Apps**, then press `1` `2` `3` `4` `5` on the remote.
2. Turn **Developer mode** on and enter your PC's LAN IP, then restart the TV.

## 3. Install

```bash
sdb connect 192.168.1.60            # TV IP
tizen install -n OmarchyTV-tizen-2.13.0-beta.wgt -t <device-name-from-sdb-devices>
tizen run -p OmarchyTV0.OmarchyTV -t <device-name>
```

## 4. Pair with your Omarchy PC

This works the same as on webOS: run `omarchy-tv webos serve` on the PC, then pair from
**Settings > PC** on the TV. See `webos/INSTALL-WEBOS.md`, section "Connecting Your Omarchy Linux PC".

## Remote keys

| Key | Action |
|---|---|
| Arrows / OK | navigate / open |
| Return (10009) | back (same as webOS Back 461) |
| 0-9, colour keys, CH+/-, media keys | registered via `tizen.tvinputdevice` (same mapping as webOS) |

## `config.xml` permissions

Due to a tooling limit, three manifest lines could not be written automatically. Before signing,
add them to `tizen/config.xml`, just before `<tizen:profile ...>`:

```xml
<tizen:privilege name="http://tizen.org/privilege/application.launch"/>
<tizen:privilege name="http://tizen.org/privilege/tv.inputdevice"/>
<tizen:privilege name="http://tizen.org/privilege/internet"/>
<tizen:setting screen-orientation="landscape" context-menu="disable" background-support="disable" encryption="disable" install-location="auto" hwkey-event="enable"/>
<access origin="*" subdomains="true"/>
```

If these lines are missing, the TV cannot launch other apps or register the remote keys, and LAN
requests to the PC are blocked.

## Uninstall

```bash
tizen uninstall -p OmarchyTV0.OmarchyTV -t <device-name>
```

Or uninstall it from the TV's Apps settings.
