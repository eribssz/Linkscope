# Linkscope

A USB link inspector for Android. Plug in a drive, cable or gadget and it shows what the
connection is really doing:

- **Link speed right now** on a log-scale dial (1.5 Mb/s → 20 Gb/s)
- **What the device could do** on a perfect cable, read from its BOS capability descriptor
- **Bottleneck verdict** — "held back to USB 2.0" when a USB 3 device sits behind a USB 2 cable, adapter or port
- **UASP vs BOT** for storage, with each alternate setting shown
- Power draw, spec version, full interface and endpoint tree, one-tap text report

No root, no ads, no network permission.

## How the cable test works

Android doesn't expose the negotiated link speed through its public API, so Linkscope reads it three ways, best first:

1. `/sys/bus/usb/devices/*/speed` — the kernel's own value, when the phone allows reading it.
2. SuperSpeed endpoint companion descriptors — only present when the link is USB 3.
3. Bulk endpoint packet size — 1024 B at SuperSpeed, 512 B at High Speed, 64 B at Full Speed.
   The kernel reports descriptors for the speed actually negotiated, so a USB 3 drive on a
   USB 2 cable shows 512 B.

The deep scan (after you tap **Allow access**) reads the BOS descriptor, which a USB 3
device includes even when it's stuck on a USB 2 link. That's how it tells a genuine USB 2
device from a held-back USB 3 one.

Limits: a USB 2-only device (mouse, keyboard) can't test a cable's USB 3 lanes, and nothing
on Android can read a cable's e-marker chip.

## Building

Push this folder to a GitHub repository. The included workflow builds a signed APK and
publishes it under **Releases**. Or open it in Android Studio and run **Build → Build APK**.

The signing key in `app/sideload.keystore` is fixed on purpose so new builds install as
updates. It's for personal sideloading only — don't use it to publish.
