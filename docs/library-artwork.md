# Library artwork

Version 2.3.1 uses two locally bundled production assets generated with the built-in image tool from the approved library concept. They supply material detail and decoration; the books, titles, selection animation and controls are rendered by the app.

- `app/src/main/assets/www/library-shelf.webp`: one empty walnut shelf bay with carved cornice, two warm downlights, a visible shelf floor, dark rear corners, tiny decorative leather spines at the far left and a small brass armillary at the far right. The central area stays empty for the user’s interactive books. No UI or text is painted into the asset.
- `app/src/main/assets/www/library-ivy.webp`: a transparent English ivy cutout, clustered at the upper left and tapering into delicate tendrils, with warm rim light. No pot or background scene.

Production direction for the shelf: use the approved concept as a style reference; render exactly one empty bay, front-facing with slight downward perspective, in landscape 3:2. Match the rich dark amber figured walnut, beveled wood, cinematic warm light and architectural depth. Reserve the middle 80 percent for live books, keep decoration confined to the far edges, and omit all labels, buttons, text and front-facing books.

Production direction for the ivy: render one thin asymmetrical trailing English ivy vine as a photorealistic transparent cutout, portrait 1:3, dark green leaves with subtle golden rim light from the right, most foliage near the upper left, tapering toward the bottom. Keep the right half mostly transparent and omit all other objects.

Generated PNG originals are preserved outside the repository; the app includes WebP encodings with alpha retained for the ivy. Neither asset is downloaded at runtime. Browser tests inline the same bytes solely for their standalone test page.
