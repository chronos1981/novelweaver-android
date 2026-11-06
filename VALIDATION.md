# Completed validation

Version 2.3.2 changes Details into an in-scene book inspection. The original
selected DOM book moves from its reserved shelf slot, enlarges to viewport-fit
geometry and turns 180 degrees to show the full back cover. Return animates the
same book back and restores its action, styles, focus and shelf position.
Background controls are inert during inspection. Android Back, Escape, the
return button and tapping outside return the book. Tap zoom supports dragging
the enlarged image within the back face. Missing artwork and read errors appear
on that face. Closed/view-replaced inspections ignore late native responses.
Reduced-motion settings remove transitions. Native save-conflict prompts first
return the inspection so their controls remain accessible.

Validation: 13 JavaScript test files and the complete Chromium UI flow, including
same-element identity, no cover dialog, viewport-fit enlargement, full image,
zoom/pan, native Back, Escape, focus/shelf cleanup, missing artwork, late replies,
reduced motion, and all prior imports/cover operations. The back-cover capture
shows the actual bundled UI. All 80 native tests, Android assembly and lint pass. Physical-device touch
verification remains manual.

Version 2.3.1 corrects the difference between the library concept and the
previous implementation. The actual scene now uses locally bundled shelf and
ivy artwork, with carved walnut detail, integrated lighting and brass decor;
book volumes and controls remain interactive. The header is smaller, books
fill the shelf, empty bays offer imports, and the mobile Quill launcher sits
above the selected-book card instead of covering its buttons.

Validation: all 13 JavaScript test files and the complete Chromium import,
cover/spine and Details flow. Additional checks cover the reported single-series
case, three books fitting across a phone shelf, a heading below 150 CSS pixels,
Quill/button separation, and no horizontal page overflow at 320px and 393px.
Single-series, multi-series and desktop captures were visually inspected.
Android assembly, 80 native tests and lint pass. The two artwork assets are
bundled WebP resources; no network access is required. Native playback and
manuscript serialization are unchanged in this release.

Version 2.3.0 replaces the flat Bookcase with an offline CSS 3D library:
wood-grain shelves, lit series labels, cover/spine rotation, selected-book
pull-forward animation and an Open book / Details card. Details reads the full
back cover without opening or changing the project, supports zoom, and ignores
late replies after its dialog closes. Missing back covers have an empty state.
Manage covers now accepts SpineCover alongside FrontCover and BackCover.
Optional spine artwork survives cleaning, JSON import, saving and shelf reload;
older projects need no migration. EPUB/Word keep their front/back export layout.

Validation: all 13 JavaScript test files; 80 native unit tests; Android lint,
assembly and matching signing-certificate verification. The bundled UI browser
flow exercised selection without opening, cover/spine modes, imported spine
artwork, full-resolution back cover and zoom, absent back artwork, closed-dialog
reply suppression, spine upload/removal/re-addition, normalized JSON retention,
and all existing multi-file imports and safe project switches. Phone and desktop
screenshots were inspected. No HTTP requests or runtime 3D dependencies were
needed. Physical-device touch/performance checks remain manual.

Version 2.2.0 adds a persistent device-local Bookcase beside Edit chapter in Read.
Multiple JSON series and individual EPUB/DOCX/TXT/JSON books can be imported.
Each shelf stores a complete independent project; imports retain source files,
existing shelf updates require a revision match, and malformed stored files are
retained with a visible warning. Normal save/open-last and narration use the
selected shelf file.

Validation: 13 JavaScript test files, 79 native unit tests, APK assembly and
Android lint. A phone-sized Chromium flow exercised multiple series, duplicate
imports, cover/text EPUB round trips, DOCX/TXT, partial failures, active-shelf
updates, revision conflicts, cancelled switching, and empty Read navigation.
No external network requests occurred. Phone and desktop screenshots were
visually inspected. Physical-device import and playback checks remain manual.

EPUB output now contains valid standalone XHTML rather than accidentally
embedded application styles/CSP; import also accepts legacy HTML chapter pages.

Version 2.1.3 adds Create new voice… directly to the per-paragraph dialogue
picker. The picker remains enabled with no saved profiles. Creation reuses the
installed-voice editor and commits the preset and paragraph assignment in one
save. Cancellation restores the prior selection, while validation retries keep
the draft fields and the paragraph target. Stale paragraph/project targets are
rejected before mutation. The generic saved-voice editor retains its existing
save-and-return workflow.

All 11 JavaScript suites pass. A 393×852 Chromium headless UI check exercised
the actual bundled change handler, created and auto-applied a female preset,
verified narrator retention and one save, then cancelled a second creation with
no changes. The mobile form and applied-picker screenshots were visually checked.
No native playback code changed in this release.

Version 2.1.2 replaces direct TextToSpeech.speak playback with per-unit private
file synthesis and an app-owned MediaPlayer using USAGE_MEDIA/CONTENT_TYPE_SPEECH.
This addresses the audio-UID/session association that the 2.1.1 button handlers
alone did not resolve on the user's headphones. It remains a proposed fix until
the user verifies the physical Bluetooth route. Synthesis completion prepares
the audio; only audio playback completion advances the native queue. The media
session remains buffering until audio starts. Pause retains exact player
position and also prevents pending synthesis from automatically starting audio.

Validation: all 70 native tests and the Android debug build/lint pass. Fourteen
new Narrator cases across Android 9/15 exercise local media attributes, audio
completion versus synthesis completion, pause/resume without new synthesis,
pause while synthesizing, replaced/late callbacks, synthesis rejection,
playback errors, destruction and temporary clip cleanup. The service tests now
fail immediately if any code calls direct TextToSpeech.speak and drive actual
MediaPlayer completion callbacks through Robolectric. These are simulated
framework tests; real-device Bluetooth button delivery still needs verification.

Version 2.1.1 adds an explicit media-button restart receiver, links the visible
editor Activity to the narration controller, declares local speech playback and
the Play/Pause action, and handles headset toggle, Play, Pause, Stop and chapter
skip events. Key-up and held-button repeats do not trigger duplicate actions.
The restart receiver posts a foreground notification before waiting for file
loading. Android 8–11 retain a mutable media-button PendingIntent so the system
can supply the key event; Android 12+ use the explicit receiver component API.

Validation: all 10 JavaScript suites and 56 native tests pass, including 30
Robolectric playback-service cases across Android 9 and Android 15. New cases
exercise paired key events, repeats, dedicated transport keys, chapter changes,
unrelated keys, receiver forwarding, foreground controller attachment and
resuming from the saved project without an Activity. The 2.1.1 APK signature
matches the existing 2.1.0 personal-development certificate. Real Bluetooth
headphone routing and physical Android Auto operation still need device tests.

Version 2.1.0 adds a native MediaBrowserService and MediaSession for Android Auto,
notification/lock-screen playback controls, background narration, audio focus and
headphone-disconnect handling. The editor connects as a client; its lifecycle no
longer pauses or destroys chapter narration. The service reads the last actual
project file on cold start and reports unavailable files without recovery fallback.

New native speech-planning tests cover narrator/dialogue and preset inheritance,
imported defaults, quoted text and apostrophes, pronunciation rules, safe long-text
chunking, paragraph bookmarks, empty/missing chapters, chapter boundaries and
same-book isolation. Service tests exercise native queue progression without an
Activity, pause/resume and stale callbacks, chapter controls, audio interruptions,
listener disconnect, metadata, caller validation and cold-file reads. A bridge test
covers full native playback requests, settings, bookmarks and UI state events.

Automated validation: 10 JavaScript suites and 40 native tests (including 14
Robolectric service cases across Android 9 and Android 15) pass.

Physical Android Auto head-unit, Bluetooth steering-wheel and real installed TTS
engine testing still require a phone/car. This is a personal development APK, not
a Google Play-certified release.

Version 2.0.10 removes the quick-picker Apply button. Its change event immediately
updates and autosaves the dialogue preset while retaining narrator, text, history,
scroll and focus. Automated tests exercise the actual change listener and verify
that the Apply action is absent from the generated editor. A phone-sized simulated
host confirmed that selecting Livit updates the speech badge and starts saving
without a click, while keeping focus on the dropdown.

Version 2.0.9 adds a saved dialogue preset combo box and Apply button to every
paragraph card. Tests cover custom/legacy metadata, narrator preservation,
missing/empty presets, defaults, no-op applications, unchanged text/history and
scroll/focus retention. A phone-sized simulated host selected Livit, applied it
and verified the speech badge and selected preset, with zero text edits. DOM
bounds confirmed the picker remains inside the card at the lower right. This
was a browser check; no physical Android device was used.

Version 2.0.8 adds shared device-local language and connection voice filters.
JavaScript checks cover canonical locales, local/online/unknown metadata,
empty matches, selected and missing voice retention, late voice discovery and
filter persistence. A simulated Android host at 393 × 820 verified en-US + Local
results and retaining a selected local voice when switching to Online. Native
voice connection metadata uses isNetworkConnectionRequired. No real device
voice/network test was performed.

Version 2.0.7 provides independent paragraph narrator and dialogue settings.
Tests cover legacy-tag compatibility, storing both roles, resolving each preset,
per-role speed/pitch, mixed playback, resetting narrator to defaults and importing
both roles. The phone-sized simulated-host check saved Torill as the narrator
while retaining the legacy Niels dialogue preset; the resulting paragraph badge
showed both names. The later browser reopen observation timed out. No real
Android device audio test was performed.

Version 2.0.6 adds named Male/Female/Other voice presets, preview and reuse
in paragraph tags and role defaults. Mixed paragraph playback separates matched
straight/curly quoted dialogue from unquoted narration. Automated checks cover
JSON persistence, presets and overrides, segment voice/rate/pitch resets,
contractions, unmatched quotes, pause/resume, Stop, missing voices, import/reimport
and unchanged manuscript/history. A 393 × 820 simulated-host browser check
created a Female · Torill preset, previewed it, reused it on the next paragraph
and verified the saved selection. No real Android voice audio was tested.

Version 2.0.5 adds project speech defaults and paragraph voice tags. JavaScript
checks cover save/load, role and paragraph precedence, switching voice/rate/pitch,
missing voices, bounds, split inheritance, merge protection and unchanged text
and revision counts. A phone-sized browser check with a simulated Android host
verified saving and reopening a Dialogue tag, speaker label, voice, rate and pitch.
Real voice quality and engine support still require an Android device check.

Version 2.0.4 adds an explicit startup chooser. New empty project is unlinked;
Open last project reads the document provider afresh. Cached manuscript loading
and automatic recovery on startup are disabled. Explicit recovery remains
available separately. Tests cover unavailable files, canceled picking, migration
of the last-file shortcut, no startup autosave, preservation of unsaved recovery,
and no recovery offer for already saved content. A 393 × 820 browser check
with a simulated Android host verified the startup chooser, fresh-file choice,
and blank startup with zero words and no linked file. This is not a device test.

Version 2.0.3 adds the same chapter navigation below the final paragraph's
Add a paragraph control, retaining the header controls and shared navigation
behavior. Footer spacing keeps the controls clear of the floating Quill button.

Version 2.0.2 adds Previous chapter / Next chapter below the existing Write
toolbar. Automated navigation checks passed for chapter order within one book,
first/last boundaries, single-chapter books, pending text and save snapshots,
unchanged revision counts, stopped narration, heading focus and Quill context
refresh. A phone-sized browser check with the simulated host verified Next from
Front Matter to Chapter 1 and Previous back to Front Matter, including the
disabled first-chapter control and wrapping toolbar layout.

Version 2.0.1 adds a visible **More → Project file → New empty project** button.
The phone-sized browser check confirmed it opens an Untitled Series containing
Book One, Chapter 1, one empty paragraph, zero words and zero tracked edits. It
uses the existing save-before-replace flow and native new-project file isolation.

- Built a signed APK with Android SDK 35, Android Gradle Plugin 8.7.3,
  Gradle 8.9 and JDK 17. Android 8.0 (API 26) is the minimum supported OS.
- Android lint passed without errors. Remaining warnings include the intentional
  JavaScript-enabled bundled editor, private-network HTTP support, user-installed
  certificate trust, dependency updates and platform compatibility advisories.
  No TLS certificate error is bypassed and remote HTTP hosts are rejected by the
  native request policy.
- All 18 Java unit tests passed: initial recovery, first save, autosave, previous
  backups, unchanged-save backup retention, external conflicts, stale decisions,
  continued recovery during conflict, restart conflicts, missing documents,
  metadata/cover/history preservation, new-project isolation, invalid opens,
  URL policy, typing during a slow document write, and an interrupted provider
  write leaving recovery and the previous version available.
- JavaScript adapter checks passed: one prompt per external conflict, pending
  and paused states, native request routing and cancellation signals, API errors,
  key-storage adapter, no Drive OAuth, and thinking-tag filtering.
- Android bridge checks passed: RPC success/errors, Stop cancellation, late
  reply handling, file-status and voice events, origin/main-frame restriction,
  TLS rejection, and chunked export protocol.
- Shared editor tests passed for durable paragraph edit counts, saved history,
  cover validation, EPUB ZIP/navigation/cover metadata, DOCX image relationships,
  chapter content, and no-cover exports.
- Inspected the 393 × 820 layout in Chromium using a simulated Android host:
  writing layout, navigation, file controls and current filename, backup/export
  tools, Quill connection settings, and a mock reply with thinking tags hidden.
  This was a browser layout check, not an Android device/emulator run.

The APK signature, package metadata and bundled asset contents were verified.
The device-specific Storage Access Framework picker, Google Drive provider,
Android Keystore, real Open WebUI connection, Android TTS, soft keyboard and
native fullscreen still require a first-run check on an Android device. No real
API key or private manuscript was used for testing. This is a personal/development
build and has not been submitted to the Play Store.
