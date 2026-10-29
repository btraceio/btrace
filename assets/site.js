import morphdom from './morphdom.js';
import { LandingPage } from './landing-page.js';
import { render } from './render.js';

const page = new LandingPage();
const root = document.getElementById('landing');
let handlers = [];
page.update = () => {
  const nextHandlers = [];
  const html = render(page.renderVals(), nextHandlers);
  morphdom(root, `<div id="landing">${html}</div>`, {
    onBeforeElUpdated(from, to) {
      // Scroll progress is maintained separately; updating other controls must
      // not reset it. Avoid touching unchanged subtrees (including the GIF).
      if (from.dataset.btRef === 'progRef') return false;
      return !from.isEqualNode(to);
    }
  });
  handlers = nextHandlers;
  for (const el of root.querySelectorAll('[data-bt-ref]')) {
    page[el.dataset.btRef].current = el;
  }
};
for (const type of ['click', 'change', 'input']) {
  root.addEventListener(type, event => {
    // Text and range controls update as you type; radios use change.
    if (type === 'change' && event.target.matches('input:not([type="radio"])')) return;
    if (type === 'input' && event.target.matches('[type="radio"]')) return;
    const attr = 'data-bt-' + (type === 'input' ? 'change' : type);
    const control = event.target.closest(`[${attr}]`);
    if (!control || control.disabled) return;
    handlers[Number(control.getAttribute(attr))]?.(event);
  });
}
page.update();
page.mount();
addEventListener('pagehide', () => page.destroy(), { once: true });
