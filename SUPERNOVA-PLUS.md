# Supernova++ — what this fork adds

A list of everything this fork adds on top of upstream Supernova, kept up to date
as features land. It is not a PR description: it is the map of the fork.

- **Upstream base:** v0.2.9 (`de54a7f`, 2026-11-24).
- **Combined test branch:** `test-all`
- **Full commit list vs upstream:** `git log --oneline origin/main..test-all`
- **Release build for testers:** the `test-2026-11-22` pre-release on this fork.

Legend: **stable** = used daily, believed solid · **experimental** = usable, rough
edges known · **dropped** = considered and deliberately not done.

---

## Folders

- **Folder item swipes** — *stable*. A folder icon's own Swipe up / Swipe down
  (Edit → Swipe) can launch the folder's first or second app, by the folder's
  order. The folder is the icon being swiped, so there is no folder picker and no
  ambiguity. The position rides in the action payload (0-based), so an nth item
  is just another payload. A folder with nothing at that position opens instead.
  The action is only offered on folder icons; home gestures and standalone action
  icons cannot select it.

## Search

- **Not a second drawer** — *stable*. The empty search window shows recent
  searches, suggested-shortcut pills and **one row** of recently opened apps. It
  no longer lists the drawer's folders and every app: search should not replicate
  the app drawer, which is one swipe away. Hidden apps stay out of the row and are
  still found when typing (where *Search hidden apps* allows).
- **Window style, tightened** — *stable*. No status-bar inset twice (the floating
  card already sits below the status bar); the card is sized by its list and
  stops above the keyboard instead of being a screen-tall box. The immersive
  style keeps upstream's navigation-button strip.

## Page transitions

- **Four more effects** — *stable*. Fade, Push, Parallax, Swing, on top of
  upstream's sixteen. Push shrinks and dims its outgoing page; Parallax keeps it
  whole and grows it while both planes trail the finger.
- **Custom transitions (Labs)** — *stable*. A versioned JSON spec of chips on an
  incoming and an outgoing lane: each chip moves a channel (move, scale, fade,
  roll, pitch, yaw) between two points of the swipe, with an easing; chips overlap
  and stack (`add`). The lanes are **roles**, not sides: the arriving page reads
  the incoming lane whichever way the finger goes, X is mirrored by the page's own
  side, and the departing page always fades out by the neighbour slot. The Labs
  row pastes, saves and shares a spec.
- **Timeline builder (Labs)** — *experimental*. Cards-based screen with a live
  mini-home preview, a **zoomable, side-scrolling** two-lane timeline (pinch or − /
  +, Fit), a playhead fed by the preview, draggable/resizable chips with edge
  grips, lane washes, ruler ticks and a selected-chip inspector (channel, from,
  value, at, to, easing, additive). Add / duplicate / delete. Seven presets
  (Nod, Crossfade, Drift, Pop, Lift, Spin, Curtain), each tested to be identity at
  rest and gone by the neighbour slot. Save / Import JSON / Share / Reset.
  No undo yet; the preview uses coloured tiles.

## Look

- **Liquid glass dock** — *stable*. The dock can use real optics instead of a stack
  of gradients: a rounded slab's signed-distance field gives the edge and a bevel
  normal; that normal refracts the backdrop, splits it slightly by wavelength at
  the rim, catches a Blinn-Phong highlight and brightens at grazing angles
  (Fresnel); colour comes from absorption through the slab. One AGSL pass, with a
  Canvas fallback below Android 13 or if a driver rejects the program. On
  Android 13+ the wallpaper bitmap is not readable, so the backdrop is a soft
  field of the wallpaper's reported colours; where it is readable, the blurred
  wallpaper. Applies to the dock only. Its optics are settings, under
  Dock → Glass: tone (Auto/Clear/Frosted/Smoked), backdrop (Automatic/Wallpaper
  colours/None), refraction, blur, tint depth, highlight, rim light, colour
  split and light angle. The dock and its preview resolve the same look, so they
  cannot drift apart.
- **Custom label font** — *stable*. Pick a `.ttf`/`.otf`; it is validated before it
  is stored, then used by every label surface. The bytes ride in backups (4 MB
  cap) and a Remove row restores a system family.

## Widgets

- **Widget stacks** — *experimental*. A workspace cell can hold up to **12**
  widgets. Swipe to switch (direction-aware slide, dots with a counter past seven,
  a light haptic), long-press to add another, and a **Manage stack** sheet listing
  every member in order (move up/down, remove with the widget id released, add,
  front marked). A member that scrolls sideways itself keeps the touch stream.
  Stored as the primary widget plus `widgetStack` members; restore, orphan sweep,
  page signature and backups all know about them.
- **Pop-up widget** — *stable*. A home icon can carry a widget it peeks at on a
  swipe: a card opens at the icon — above it, below when there is no room above,
  beside it in landscape — holding the widget itself, so its taps and scrolling
  work. Bind it in **Edit → Popup widget**, or pick **Popup widget** in the
  icon's swipe picker with nothing bound, which asks for the widget and then
  binds that swipe. One host view per widget, so a second peek does not reload
  it; rebound from backups and released with the icon. Tap outside the card or
  press Back to put it away. Home icons only: dock icons have no swipes, and a
  folder's window has no room for a card.

- **Dock suggestions** — *stable*. Off by default (Dock → Suggestions): the main
  page's empty slots fill with apps used recently, excluding the dock's own
  items, hidden apps and private space. A suggestion is an ordinary dock cell;
  tap opens, long-press arms the drawer's drag to keep it.

## Daily use

- **Usage feed card** — *stable*. Top apps by launch counts the launcher already
  keeps locally, with proportional bars; tap to launch. No permission, no polling,
  nothing running while the feed is hidden.
- **Notification glance card** — *stable*. The newest **badged** notifications,
  read-only, newest first; a tap sends the notification's own content intent.
  Uses only the notification access already granted for badges, nothing is read
  while the card is off screen, and without access the card says so instead of
  looking empty. Off by default (Feed settings → Notifications).
- **Reachability** — *stable*. A bindable action (Gestures → any gesture →
  Reachability): the home's grid comes down a third with a dimmed band above it;
  tap the band, repeat the action, or leave the launcher to put it back. Ignored
  over the drawer, search, feed and edit mode.
- **Drawer that starts lower** — *stable*. Drawer → *Start the list lower*: 28% of
  the sheet's height above the list, so the grid and its scroller sit in the
  thumb's half.
- **Low-spec mode** — *stable*. Labs → Low-spec: the launcher's animations stop,
  wallpaper zoom stops, and the dock glass falls back to the painted pane (no
  shader, no wallpaper sampling).
- **Left-handed drawer** — *stable*. Drawer → Left-handed: the fast scroller and
  its letter bubble move to the left, with the scroller's geometry mirrored.

## Feel

- **Swipe-launch nudge** — *stable*. A swipe-launched item (folder first/second,
  swipe-bound intent) rises a little in the swipe's direction, grows, fades, and
  the app scales up out of it, instead of the icon flying to the screen centre.
- **Badges settle** — *stable*. A badge arriving or clearing gives its item a short
  spring instead of a silent redraw (home and dock).
- **Press feedback** — *stable*. Icons settle under a finger and spring back, via a
  state list animator (home, dock, folder members).
- **Wiggle eases in** — *stable*. Edit mode's rock ramps up over the first frames
  instead of starting at full swing.
- **Top hit rises** — *stable*. A new search top hit rises 6 dp with a fade, once
  per answer - a slower provider re-submitting the same hit does not re-animate.
- **Stack tick** — *stable*. One light haptic per stack switch.

## Correctness and hardening

- **Backups are a known state** — *stable*. The Labs custom transition and the
  custom font ride in the payload (blank means none and clears; absent means the
  backup predates the field and leaves the current one alone; the user's saved
  transition presets ride in the payload the same way). Export and
  serialization move off the main thread; automatic snapshots are written aside
  and renamed when whole; the pre-import snapshot is taken under the lock.
- **Page drag ends with the launcher** — *stable*. Stopping cancels the page drag
  (it owns the pager's alpha) and a deferred page move checks it still lives.
- **Accessibility** — *stable*. Recycled search rows drop the previous result's
  content description.
- **Glass cannot crash the launcher** — *stable*. A shader rejected at creation or
  draw falls back to the Canvas reading.

---

## Dropped on purpose

- **Drag a widget onto another to make a stack** — with overlapping widgets it
  fights the layout; stacking stays a menu action.
- **Swipe-indicator fade** — the indicator is the icon's foreground drawable, so a
  fade would replay on every page bind; it would draw attention rather than remove
  a jump.

## Known limits

- Widget stacks: removing an app's last widget removes the whole stack; cards and
  drag-to-stack are not in; the Manage sheet uses arrows, not drag.
- Liquid glass: an approximation on Android 13+ (no wallpaper pixels), dock only.
- Custom transitions: the builder's timeline is not zoomable per lane; there is no
  cross-lane drag (move a chip by retyping its lane's chip list).
- Reachability moves the workspace pager only; the page indicator stays.
- Folder badges are upstream (Badges → Folder badges), not a fork addition.
- Wallpaper-adaptive theming is upstream (`PaletteSource.WALLPAPER`), not a fork
  addition.

## Branches

| Branch | Contents |
|---|---|
| `test-all` | everything below, on top of v0.2.9 |
| `search-and-folder-swipes` / `-v28` | folder item swipes + search window |
| `fix-misc` | page-drag stop, TalkBack description, backup IO/atomicity |
| `feature-page-transitions` | Fade, Push, Parallax, Swing |
| `feature-liquid-glass` | glass optics + dock style |
| `feature-widget-stacks` | stack model, view, indicator, arbitration |
| `feature-stack-manage` | Manage stack sheet |
| `feature-custom-transitions` | custom spec, evaluator, builder |
| `feature-custom-font` | font picking, store, backup |
| `feature-usage-card` | usage feed card |
