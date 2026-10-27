# Historical forum UI demonstrations

These 22 files are **historical demonstrations**, not a new automated Before/After run and not
performance measurements. They are copied byte-for-byte from the accepted C4 delivery set.
Only cropped/redacted playback files are included; raw recordings, session data, private captures
and contact sheets are excluded. Previous privacy review was agent-based, not an independent
human audit of every full-resolution frame. The author authorized publication of this accepted set
on 27 October 2026; the manifest retains its original preparation-time status.

Before: stock Telegram X `eaef48a2a3603bac39bd7fd09a57a23acd24fbb2`.
After: Validation Release `3817e684b79d8e61ea504630fb8549d687290e5b`.
All recordings were made on Pixel 6. The [manifest](forum-media/manifest.json) records exact file
digests, source/APK hashes, dimensions and individual limitations. It contains 5 Before and
17 After files, with reused Before references: not 17 matched pairs.

| Area | Before | After | Limitation |
|---|---|---|---|
| Chat-row topic preview | [Stock row](forum-media/before-chat-preview-v2.mp4) | [Topic preview](forum-media/after-chat-preview.mp4) | One row; footer/draft states differ. |
| Rail and navigation | [Stock entry/Back](forum-media/before-forum-entry-v2.mp4) | [Whole workflow](forum-media/after-rail-workflow-v2.mp4), [entry](forum-media/rail-entry.mp4), [topic/Back](forum-media/topic-transition.mp4) | Three neutral chat icons, not a long-rail scroll; private areas masked. |
| Selector controls | [No selector](forum-media/before-selector.mp4) | [Whole workflow](forum-media/after-selector-control-v2.mp4), [top](forum-media/selector-top-v2.mp4), [side](forum-media/selector-side-v2.mp4), [bottom](forum-media/selector-bottom-v2.mp4) | Reused Before; no equivalent topic controls in stock. |
| Topic list and selection | New screen; no equivalent stock screen | [List](forum-media/topic-list.mp4), [toolbar](forum-media/selection-toolbar.mp4) | Neutral rows; commands not applied. |
| Editor | New screen | [Form](forum-media/editor-full.mp4), [preview](forum-media/editor-preview.mp4), [search](forum-media/editor-search.mp4) | Unsaved form; not a server-save demonstration. |
| Topic profile | New screen | [Profile](forum-media/profile-overview.mp4) | Link identity hidden; static overview, not every action. |
| Materials | [Group materials](forum-media/before-materials.mp4) | [Topic materials](forum-media/after-materials.mp4) | Different group/topic scope; Files/Media/Links, not every filter. |
| Message geometry | [Stock](forum-media/before-geometry.mp4) | [Topic layout](forum-media/after-geometry.mp4) | Same synthetic incoming image/caption; author hidden; vertical offsets differ. |
| Notification options | New composition | [Options](forum-media/notification-options.mp4) | Default/silent choices, not delivery proof. |

## New upstream workflow still to perform

Run appropriate UI checks against the current base and feature candidate in an approved isolated
environment. Record current rail transitions and selector controls with the same check on both
sides; use screenshots for static screens. For new screens that do not exist before, label Before
as not available. `describeCheckResults` should supply the current comparison table and media
links. Do not relabel these older recordings as the output of that workflow.

`clearCheckResults` removes upstream's `tests/ui/checks/media` output directory. The historical
assets deliberately live in `forum-media`, separate from generated future recordings. Keep any
new captures private until their contents and final hashes have been reviewed.
