# Website maintenance

The `gh-pages` branch serves the BTrace project website. `index.html` and all runtime assets are checked in, so GitHub Pages serves the branch root as static files. The `.nojekyll` marker disables Jekyll processing; hosting does not require Node.js, Ruby or a custom deployment workflow. Blog posts belong on the personal `jbachorik.github.io` site and are not included here.

## Edit and build

Use Node.js 18 or newer:

```bash
npm ci
npm run build
```

- `site/landing.html` contains the page markup, adapted from the supplied Landing v2 design. Its `sc-for`, `sc-if` and `{{ expression }}` syntax is compiled during the build; it is not shipped as an in-browser template engine.
- `site/landing.js` contains page content, example data and interactive control state.
- `assets/design.css` and `assets/landing.css` contain the supplied design styles. `assets/site.css` contains responsive layout and accessibility adjustments.
- `assets/site.js` connects controls to the rendered page. The locally vendored `morphdom.js` preserves inputs and GIF playback during updates; its MIT license is beside it.
- `icon/` and `media/` contain the supplied icons and recordings. Demo images load on demand.

Rebuild after changing either source file. Commit the generated `index.html`, `assets/render.js`, `assets/landing-page.js` and `assets/hover.css` with the source changes. These assets require no React, Babel or external script CDN at runtime. Fonts use Google Fonts with local system fallbacks.

## Preview

Serve the branch root with any static server:

```bash
python3 -m http.server 8000
```

Open `http://localhost:8000/`. This serves the same checked-in files that GitHub Pages publishes.

The landing page uses relative asset paths so it works under `/btrace/` and at a domain root. In GitHub Pages settings, publish from the `gh-pages` branch and the `/ (root)` folder. Rebuild and include the generated files whenever the page source changes.

## Verify changes

Rebuild and inspect desktop and phone layouts. Check the plugin and installation tabs, audience routes, tutorial filters, command presets, editable matcher, probe locations, duration slider, field toggles, clipboard controls, doctor examples and all seven demo images. The command output is simulated; the builder does not connect to a JVM.

Check that `@entry` disables duration and return values and that `@error` disables return values. Confirm there is no horizontal page overflow at narrow widths, keyboard focus is visible, reduced-motion preferences are honored, and installation/documentation content is available with JavaScript disabled. Clipboard feedback must report success only after the browser accepts the copy operation.
