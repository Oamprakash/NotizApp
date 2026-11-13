# App icon

`notiz-icon.svg` is the master: a white house with notice lines and a marigold notification dot on Notiz teal. It uses the 108×108 Android adaptive-icon canvas; the visible icon is the middle 72×72.

- Android: `res/drawable/ic_launcher_{background,foreground,monochrome}.xml` (adaptive icon, Android 8+, themed icon on Android 13+) and PNGs in `res/mipmap-*` for Android 7.
- iOS: `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png` (Xcode makes the other sizes).
- Play Store listing: `play-store-512.png`.

To change it, edit the SVG (and the matching paths in the three drawables), then run `python3 render.py notiz-icon.svg` (needs Playwright) and copy the PNGs into place.
