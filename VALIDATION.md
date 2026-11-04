# Completed validation

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
