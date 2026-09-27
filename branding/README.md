# TopicsGram X launcher icon

Fork-only branding, kept separate from forum functionality and the Windows/TDLib build fixes.

User brief: keep the current background and airplane, with two short topic tabs behind the airplane.

The tabs were edited with the built-in image-generation tool on 2026-09-27/28. Only the isolated tabs layer is used; the generated draft airplane is not shipped. The original airplane, adaptive background and border assets remain unchanged. `topics-tabs-large-source.png` is the active transparent source layer, enlarged and raised after the user found the first version too subtle. `topics-tabs-source.png` preserves the first small-tab source for comparison; `topicsgram-icon-preview.png` is the current composite preview with the original round adaptive background and airplane. The 48 px and 192 px legacy previews were inspected for visibility of both tabs.

`Update-LauncherIcons.ps1` uses ImageMagick to resize the tabs for each Android density and compose the legacy icons. Adaptive icons layer the tabs behind the original airplane at runtime, including the monochrome alpha silhouette. Legacy icons retain the original background and mask the tabs beneath the airplane. Notification and in-app artwork are unchanged.

Regenerate from the repository root:

```powershell
./branding/Update-LauncherIcons.ps1 -ImageMagick 'C:/Program Files/ImageMagick-7.0.8-Q16-HDRI/magick.exe'
```

The generated resources use the `topicsgram_` prefix. The manifest and account authenticator point at the new launcher names; upstream resources are retained for a clean separation from an eventual functionality-only PR.

## Original small-tabs prompt (built-in tool)

```text
Use case: precise-object-edit / background-extraction.
Input image 1 is the edit target, a transparent paper airplane icon with two pale blue-gray rounded topic tabs behind it. Create its separate TABS-ONLY layer for deterministic compositing in an Android app.
Remove the entire airplane. Keep only the TWO existing short rounded blue-gray tabs, in exactly the same sizes, angles, colors and upper-left-of-center positions on the same square transparent canvas. Complete just the small portions of those tabs that were hidden by the airplane, without lengthening them into large cards. Do not center or enlarge the tabs: preserve their absolute layout on the existing full canvas and all transparent margins. Keep the two tabs distinct, with the pale back tab a little higher and the darker front tab a little lower.
The output must have genuinely transparent pixels everywhere else. No airplane, no white shape, no background, no circle, no text, no border, no shadow, no texture, no extra objects. The app will keep its exact original airplane and background and composite these tabs behind it.
```

## Enlarged tabs prompt (built-in tool)

```text
Use case: precise-object-edit.
Asset type: transparent tabs-only layer for an Android app launcher icon.
Input image 1 is the EDIT TARGET: the existing two tiny pale blue-gray rounded topic tabs on a large transparent square canvas. Input image 2 is CONTEXT ONLY: it shows how those tabs currently sit behind the unchanged paper airplane on a blue-gray circular icon. Do not reproduce image 2 or its background.
Requested change: enlarge BOTH topic tabs substantially, to about TWICE their current width and height, and move the stack slightly upward so the upper parts remain clearly visible above and to the left of the airplane when composited. They must read as TWO distinct staggered topic/folder tabs at a small 48-pixel app-icon size, not as one tiny badge. Keep the existing pale blue-gray colors, crisp rounded corners and slight diagonal slant. Give the front tab enough offset from the back tab that their two stepped silhouettes are visible. The final tabs-only cluster should occupy roughly x=28%-54% and y=28%-47% of the unchanged square canvas, within the icon safe area. Keep the canvas and all surrounding transparent space.
Invariants: exactly two tabs, same simple style, no airplane, no circle, no background, no border, no text, no hashtag, no extra objects, no glow, no shadows, no texture. Output ONLY the two enlarged tabs on genuinely transparent background; the app will reuse its original airplane and background unchanged.
```

## Final positioning refinement (built-in tool)

```text
Use case: precise-object-edit.
Input image 1 is the edit target: two large rounded blue-gray topic tabs on a transparent square canvas.
Change only their position: translate the entire two-tab stack UPWARD by about 9 percent of the full canvas height, preserving their sizes, slant, proportions, colors and relative overlap. The front darker tab must be higher too. Keep the large square canvas and existing transparent left/right margins unchanged. In the final canvas the cluster should span approximately x=27%-59%, y=22%-48%. This repositioning is needed because an unchanged paper airplane will be composited across the center and lower-right, and currently hides the darker front tab. Both tabs must visibly protrude above the airplane's upper-left edge.
Output only the same TWO large tabs on genuinely transparent background. No airplane, no background, no circle, no text, no shadow, no added objects, no redesign. Do not recenter, rescale or crop the canvas.
```
