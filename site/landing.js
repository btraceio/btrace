
const GH = 'https://github.com/btraceio/btrace/blob/develop/';
const TUT = GH + 'docs/tutorials/';
const DEMO = 'https://github.com/btraceio/btrace/tree/develop/demos/';
const AP = 'https://github.com/btraceio/agent-plugins/tree/main/plugins/';
const NAV = [['agent','Agent'],['start','Start here'],['plugins','Plugins'],['oneliner','Oneliner'],['ai','AI workloads'],['production','Production'],['demos','Demos'],['tutorials','Tutorials'],['install','Install'],['docs','Docs']];
const STEPS = [
  { at: 1, tool: 'list_jvms', text: 'Found CheckoutServiceApp' },
  { at: 2, tool: 'Read', text: 'PaymentClient and FraudScreen come from acme-sdk.jar, which has no source' },
  { at: 4, tool: 'deploy_script', text: 'Measure FraudScreen.screen (return, error) and PaymentClient.authorize. Report every 5 s.' },
  { at: 6, tool: 'read_probe_output', text: 'wait_seconds: 12' },
  { at: 11, tool: 'exit_probe', text: 'Probe removed' },
  { at: 12, tool: 'list_probes', text: 'No probes running' }
];
const OUT = [
  { at: 1, k: 'cmd', text: '$ list_jvms' },
  { at: 1, k: 'n', text: '48211  CheckoutServiceApp' },
  { at: 4, k: 'cmd', text: '$ deploy_script CheckoutProbe.java → 48211' },
  { at: 5, k: 'dim', text: 'probe attached, @OnTimer(5000)' },
  { at: 6, k: 'cmd', text: '$ read_probe_output --wait 12' },
  { at: 7, k: 'n', text: '=== checkout probe ===' },
  { at: 8, k: 'hl', text: 'FraudScreen.screen        104 calls  22 errors (21%)' },
  { at: 8, k: 'n', text: '  RiskServiceException, swallowed by checkout' },
  { at: 9, k: 'n', text: '  avg 15 ms   max 41 ms' },
  { at: 10, k: 'hl', text: 'PaymentClient.authorize    76 calls' },
  { at: 10, k: 'n', text: '  avg 129 ms  max 449 ms' },
  { at: 11, k: 'cmd', text: '$ exit_probe' },
  { at: 11, k: 'dim', text: 'probe removed; no overhead remains' },
  { at: 12, k: 'cmd', text: '$ list_probes' },
  { at: 12, k: 'dim', text: '(none)' }
];
const END = 13, LOOP = 20;
const TONE = { cmd: 'var(--color-bg)', n: 'var(--color-neutral-300)', dim: 'var(--color-neutral-500)', hl: 'var(--color-accent-400)' };
const PLUGIN = {
  claude: { label: 'Claude Code', p: '', lines: ['/plugin marketplace add btraceio/agent-plugins', '/plugin install btrace-observability@btraceio-agent-plugins'] },
  codex: { label: 'Codex', p: '$ ', lines: ['git clone https://github.com/btraceio/agent-plugins.git', 'codex plugin marketplace add ./agent-plugins'] },
  pi: { label: 'Pi', p: '$ ', lines: ['pi install git:github.com/btraceio/agent-plugins'] }
};
const AUD = {
  sre: { label: 'SRE / ops', goal: 'production', lead: 'Find the cause on a live JVM. Then remove all probes.', routes: [
    { kind: 'Tutorial 01 · ~5 min', title: 'Your First Trace in 2 Minutes', desc: 'Find a latency bug and a swallowed exception in a live JVM with oneliners.', href: TUT + '01-first-trace-in-2-minutes.md' },
    { kind: 'Tutorial 12 · ~10 min', title: 'Attach to Production Without Fear', desc: 'Prepare the JVM, check it with btrace doctor, attach, and make sure that no probe remains.', href: TUT + '12-production-safe-attach.md' },
    { kind: 'Tutorial 09 · ~15 min', title: 'BTrace in Kubernetes, the Sidecar Way', desc: 'Three container variants; attach across a container boundary.', href: TUT + '09-kubernetes-sidecar.md' } ] },
  ai: { label: 'AI / LLM builders', goal: 'ai', lead: 'See tokens, cost, retrieval quality and inference latency from inside the JVM.', routes: [
    { kind: 'Tutorial 07 · ~10 min', title: 'Watch Your LLM App Think', desc: 'Set a latency budget on an LLM call and read tokens, cost and violations on one dashboard.', href: TUT + '07-llm-observability.md' },
    { kind: 'Tutorial 11 · ~10 min', title: 'Watch Your RAG Pipeline and Your Models', desc: 'Empty retrievals, similarity scores and embedding latency from a live RAG answerer.', href: TUT + '11-ai-workload-observability.md' },
    { kind: 'Reference', title: 'AI/LLM observability lessons', desc: 'BTrace Tutorial lesson 12: llm-trace, rag-quality and gpu-bridge in depth.', href: GH + 'docs/BTraceTutorial.md#121-llm-inference-tracing-btrace-llm-trace' } ] },
  agent: { label: 'Coding-agent users', goal: 'agents', lead: 'Your coding agent probes a live JVM. Then it removes the probe.', routes: [
    { kind: 'Tutorial 05 · ~10 min', title: 'Let an AI Assistant Debug Your JVM', desc: 'Install the plugin; the assistant finds a slow method and a swallowed exception.', href: TUT + '05-mcp-server.md' },
    { kind: 'Repository', title: 'btraceio/agent-plugins', desc: 'The marketplace for Claude Code, Codex and Pi, with install steps for each host.', href: 'https://github.com/btraceio/agent-plugins' },
    { kind: 'Demo 06', title: 'Diagnose a checkout service', desc: 'A recorded session. The vendor SDK of the service has no source.', href: DEMO + '06-ai-assistant' } ] }
};
const PLUGINS = [
  { name: 'btrace-observability', desc: 'Skills that find faults in Java applications with BTrace probes.', href: AP + 'btrace-observability', mark: 'var(--color-accent)' },
  { name: 'jfr-analyzer', desc: 'Analyzes JFR profiles. Can compare the results with live BTrace probes.', href: AP + 'jfr-analyzer' },
  { name: 'jafar-perf', desc: 'Procedures for JVM performance analysis on the Jafar MCP server: CPU, latency, GC and memory.', href: AP + 'jafar-perf' },
  { name: 'perf-engineer', desc: 'Finds and verifies optimizations with JFR, BTrace and JMH.', href: AP + 'perf-engineer' },
  { name: 'btrace-development', desc: 'Rules and build instructions for work on BTrace itself.', href: AP + 'btrace-development' }
].map(p => ({ ...p, mark: p.mark || 'var(--color-neutral-600)' }));
const SKILLS = ['latency-analysis','failure-analysis','oneliner-triage','endpoint-diagnostics','probe-lifecycle','data-safety','runtime-access','startup-and-packaging','mcp-operations','legacy-libs-migration'].map(s => ({ name: 'btrace-' + s }));
const EXTS = [
  { name: 'btrace-metrics', desc: 'Latency histograms and stats: p50, p95, p99.' },
  { name: 'btrace-contracts', desc: 'Latency budgets, call-rate limits, assertions and tagged path profiles.' },
  { name: 'btrace-otel', desc: 'Sends spans, counters and gauges to an OTLP/HTTP collector.' },
  { name: 'btrace-statsd', desc: 'Sends metrics to a StatsD endpoint.' },
  { name: 'btrace-utils', desc: 'Common helper functions for probes.' }
];
const PRESETS = [
  { id: 'every', label: 'How long does each call take?', s: { cls: 'OrderService', meth: 'processOrder', loc: 'return', thr: 0, fields: { method: true, duration: true } } },
  { id: 'slow', label: 'Which calls are slower than 200 ms?', s: { cls: 'OrderService', meth: '*', loc: 'return', thr: 200, fields: { method: true, duration: true } } },
  { id: 'err', label: 'Which method throws an exception, and where?', s: { cls: 'OrderService', meth: 'validateOrder', loc: 'error', thr: 0, fields: { method: true, stack: true } } }
];
const FIELDS = ['method', 'args', 'duration', 'return', 'stack'];
const EVENTS = [
  { m: 'processOrder', d: 54, a: '[ORD-1041]', r: 'OK' },
  { m: 'chargeCard', d: 345, a: '[ORD-1042, 129.90]', r: 'AUTHORIZED' },
  { m: 'processOrder', d: 394, a: '[ORD-1042]', r: 'OK' },
  { m: 'validateOrder', d: 3, a: '[ORD-1043]', r: 'true' },
  { m: 'processOrder', d: 61, a: '[ORD-1044]', r: 'OK' }
];
const STACK = ['OrderService.processOrder(OrderServiceApp.java:81)', 'OrderServiceApp.lambda$main$0(OrderServiceApp.java:47)', 'java.base/java.lang.Thread.run(Thread.java:842)'];
const DOCTOR = {
  ready: [['$ btrace doctor 39936','cmd'],['Status: READY (exit 0)','hl'],['BTrace endpoint: ready at 127.0.0.1:58103','n'],['Authentication: required; credential file readable','n']],
  plain: [['$ btrace doctor 40319','cmd'],['Status: PREPARATION_REQUIRED (exit 2)','hl'],['BTrace endpoint: not ready','n'],['Remediation:','n'],['- Relaunch with -javaagent:/path/to/btrace.jar=port=0 for reviewed prepared mode.','n']]
};
const DEMOS = [
  { n: '01', title: 'Your first trace in one line', story: 'Three oneliners find a latency bug and a swallowed exception in a live order service.', src: 'media/01-oneliner.gif', href: DEMO + '01-oneliner' },
  { n: '02', title: 'Upgrade from 2.x', story: 'A probe compiled with BTrace 2.2.6 runs without changes. The migration script updates a source file.', src: 'media/02-upgrade-from-2x.gif', href: DEMO + '02-upgrade-from-2x' },
  { n: '03', title: 'Metrics and permissions', story: 'btrace-metrics gives p50, p95 and p99. The -le option explains a blocked extension. A policy then allows it.', src: 'media/03-metrics-and-permissions.gif', href: DEMO + '03-metrics-and-permissions' },
  { n: '04', title: 'LLM trace', story: 'Tokens, cost and a 500 ms latency budget on a chat-completion client. Slow calls appear as violations.', src: 'media/04-llm-trace.gif', href: DEMO + '04-llm-trace' },
  { n: '05', title: 'RAG and inference', story: 'Empty retrievals and embedding latency from btrace-rag-quality and btrace-gpu-bridge in one probe.', src: 'media/05-rag-and-inference.gif', href: DEMO + '05-rag-and-inference' },
  { n: '06', title: 'AI assistant', story: 'A coding agent finds why a checkout service drops orders and stalls inside a vendor SDK with no source.', src: 'media/06-ai-assistant.gif', href: DEMO + '06-ai-assistant' },
  { n: '07', title: 'Production-safe attach', story: 'Prepare, check, attach and detach. Throughput returns to baseline after the client detaches.', src: 'media/07-production-safe-attach.gif', href: DEMO + '07-production-safe-attach' }
];
const GOALS = [['all','All'],['start','First steps'],['production','Production'],['ai','AI workloads'],['agents','Agents'],['extend','Extend'],['upgrade','Upgrade']];
const TUTS = [
  ['01','Your First Trace in 2 Minutes','Find a latency bug and a swallowed exception in a live JVM with oneliners.','~5 min','01-first-trace-in-2-minutes.md',['start']],
  ['02','From Oneliner to Script: The Flat DSL','Turn a oneliner into a version-controllable script and add @TLS state.','~10 min','02-oneliner-to-script.md',['start']],
  ['03','Upgrade from 2.x in 10 Minutes','Migrate scripts to the new package; see why compiled probes need no changes.','~10 min','03-upgrade-from-2x.md',['upgrade']],
  ['04','Extensions and Permissions Without Tears','Grant, deny and inspect extension permissions against a live JVM.','~10 min','04-extensions-and-permissions.md',['extend']],
  ['05','Let an AI Assistant Debug Your JVM','Install btrace-observability and have an assistant find the slow method, then check it cleaned up.','~10 min','05-mcp-server.md',['agents','start']],
  ['06','Write Your Own Extension in 30 Minutes','Scaffold, build, install and grant permissions for your own extension.','~25 min','06-write-your-own-extension.md',['extend']],
  ['07','Watch Your LLM App Think','Put a latency budget on an LLM call; read tokens, latency, cost and violations.','~10 min','07-llm-observability.md',['ai']],
  ['08','One JAR to Rule Them All','Package BTrace plus an extension into a single -javaagent JAR.','~15 min','08-fat-agent.md',['production','extend']],
  ['09','BTrace in Kubernetes, the Sidecar Way','Three container variants; attach across a container boundary.','~15 min','09-kubernetes-sidecar.md',['production']],
  ['10','What Changed for 2.x Users, in 15 Minutes','Oneliners, an untouched 2.2.6 probe, one-command migration, Aggregations to btrace-metrics.','~15 min','10-whats-new-for-2x-users.md',['upgrade']],
  ['11','Watch Your RAG Pipeline and Your Models','Empty retrievals, similarity scores and embedding latency in one probe.','~10 min','11-ai-workload-observability.md',['ai']],
  ['12','Attach to Production Without Fear','Prepared mode, btrace doctor, no-flag attach, and nothing left behind.','~10 min','12-production-safe-attach.md',['production']]
].map(([n, title, desc, time, f, tags]) => ({ n, title, desc, time, href: TUT + f, tags }));
const INST = { sdk: { label: 'SDKMAN!', cmd: 'sdk install btrace' }, jbang: { label: 'JBang', cmd: 'jbang io.btrace:btrace:3.0.0 <pid> ...' }, docker: { label: 'Docker', cmd: 'docker pull ghcr.io/btraceio/btrace:3.0.0' } };
const D = GH + 'docs/';
const DOCS = [
  { title: 'Learn', links: [['Getting Started', D + 'GettingStarted.md'], ['Oneliner Guide', D + 'OnelinerGuide.md'], ['BTrace Tutorial', D + 'BTraceTutorial.md'], ['Quick Reference', D + 'QuickReference.md']] },
  { title: 'Operate', links: [['Troubleshooting', D + 'Troubleshooting.md'], ['FAQ', D + 'FAQ.md'], ['JDK Compatibility', D + 'JDKCompatibility.md'], ['Permission Policy', D + 'PermissionPolicy.md']] },
  { title: 'Build on it', links: [['Extension Development Guide', D + 'BTraceExtensionDevelopmentGuide.md'], ['MCP Server', D + 'MCPServer.md'], ['Migration 2.x → 3.0', D + 'Migration-2.x-to-3.0.md'], ['50+ sample scripts', 'https://github.com/btraceio/btrace/tree/develop/btrace-dist/src/main/resources/samples']] },
  { title: 'Community', links: [['GitHub Discussions', 'https://github.com/btraceio/btrace/discussions'], ['Issues', 'https://github.com/btraceio/btrace/issues'], ['Slack', 'http://btrace.slack.com/'], ['Gitter', 'https://gitter.im/btraceio/btrace']] }
].map(c => ({ title: c.title, links: c.links.map(([label, href]) => ({ label, href })) }));

class LandingPage {
  state = { t: 13, plugin: 'claude', aud: null, goal: null, cls: 'OrderService', meth: '*', loc: 'return', thr: 200, fields: { method: true, duration: true }, preset: 'slow', inst: 'sdk', doctor: 'ready', demo: 5, copied: null, active: 'agent' };
  props = { animateHero: true, defaultAudience: 'sre' };
  setState(patch) {
    this.state = { ...this.state, ...(typeof patch === 'function' ? patch(this.state) : patch) };
    this.update?.();
  }
  progRef = ({ current: null });

  mount() {
    this.startHero();
    this.onScroll = () => {
      const el = document.scrollingElement || document.documentElement;
      const max = el.scrollHeight - innerHeight;
      if (this.progRef.current) this.progRef.current.style.width = (max > 0 ? (el.scrollTop / max) * 100 : 0) + '%';
    };
    addEventListener('scroll', this.onScroll, { passive: true });
    this.onScroll();
    this.spy = new IntersectionObserver(es => es.forEach(e => e.isIntersecting && this.setState({ active: e.target.id })), { rootMargin: '-35% 0px -60% 0px' });
    document.querySelectorAll('[data-spy]').forEach(s => this.spy.observe(s));
  }

  destroy() {
    clearInterval(this.timer); clearTimeout(this.copyT);
    removeEventListener('scroll', this.onScroll);
    this.spy && this.spy.disconnect();
  }
  startHero() {
    clearInterval(this.timer);
    if (matchMedia('(prefers-reduced-motion: reduce)').matches || this.props.animateHero === false) { this.setState({ t: END }); return; }
    this.setState({ t: 0 });
    this.timer = setInterval(() => this.setState(s => ({ t: s.t >= LOOP ? 0 : s.t + 1 })), 850);
  }
  async copy(key, text) {
    clearTimeout(this.copyT);
    try {
      await navigator.clipboard.writeText(text);
      this.setState({ copied: key });
    } catch {
      this.setState({ copied: key + '-failed' });
    }
    this.copyT = setTimeout(() => this.setState({ copied: null }), 2000);
  }
  tabs(map, cur, key) {
    return Object.keys(map).map(k => ({
      label: map[k].label, pick: () => this.setState({ [key]: k }),
      bg: k === cur ? 'var(--color-accent)' : 'transparent',
      fg: k === cur ? 'var(--color-bg)' : 'var(--color-neutral-300)'
    }));
  }

  renderVals() {
    const s = this.state;
    const t = Math.min(s.t, END);
    const aud = s.aud ?? this.props.defaultAudience ?? 'sre';
    const goal = s.goal ?? AUD[aud].goal;

    const steps = STEPS.filter(x => t >= x.at).map(x => ({ ...x, dot: (t === x.at && t < END) ? 'var(--color-accent)' : 'var(--color-neutral-500)' }));
    const outLines = OUT.filter(l => t >= l.at).map(l => ({ text: l.text, color: TONE[l.k] }));
    const done = t >= END;

    const pl = PLUGIN[s.plugin];
    const pluginLines = pl.lines.map(text => ({ text, prompt: pl.p || '› ' }));

    // oneliner builder
    const loc = s.loc;
    const allowed = { method: true, args: true, duration: loc !== 'entry', return: loc === 'return', stack: true };
    const active = FIELDS.filter(f => s.fields[f] && allowed[f]);
    const useThr = loc !== 'entry' && s.thr > 0;
    const cmdMatcher = `${s.cls || 'MyClass'}::${s.meth || '*'}`;
    const cmdFilter = useThr ? ` if duration>${s.thr}ms` : '';
    const cmdAction = active.length ? 'print ' + active.map(f => f === 'stack' ? 'stack' : f).join(', ') : 'count';
    const cmd = `btrace -n '${cmdMatcher} @${loc}${cmdFilter} { ${cmdAction} }' <PID>`;
    const mm = s.meth || '*';
    const matchM = e => mm === '*' || (mm.endsWith('*') ? e.m.startsWith(mm.slice(0, -1)) : e.m === mm);
    let evs;
    if (loc === 'error') evs = [{ m: mm === '*' ? 'validateOrder' : mm.replace('*', ''), d: 2, a: '[ORD-1043]' }];
    else {
      evs = EVENTS.filter(matchM).filter(e => !useThr || e.d > s.thr);
      if (!evs.length && !EVENTS.some(matchM)) evs = [{ m: mm.replace('*', '') || 'call', d: 87, a: '[...]', r: '…' }];
    }
    let sampleOut = [];
    if (!active.length) {
      sampleOut = [{ text: `${cmdMatcher}  count: ${loc === 'error' ? 38 : 1284}`, color: TONE.n }];
    } else if (!evs.length) {
      sampleOut = [{ text: `(no calls over ${s.thr} ms yet)`, color: TONE.dim }];
    } else {
      evs.slice(0, 4).forEach((e, i) => {
        if (i) sampleOut.push({ text: ' ', color: TONE.n });
        active.forEach(f => {
          if (f === 'method') sampleOut.push({ text: e.m, color: TONE.cmd });
          if (f === 'args') sampleOut.push({ text: 'args: ' + e.a, color: TONE.n });
          if (f === 'duration') sampleOut.push({ text: `execution time: ${e.d} ms`, color: e.d > 200 ? TONE.hl : TONE.n });
          if (f === 'return') sampleOut.push({ text: 'return: ' + e.r, color: TONE.n });
          if (f === 'stack') STACK.forEach(l => sampleOut.push({ text: l, color: TONE.dim }));
        });
      });
    }
    const setB = patch => this.setState({ ...patch, preset: null });

    const dl = DOCTOR[s.doctor];
    const demoSel = DEMOS[s.demo];
    const tutList = goal === 'all' ? TUTS : TUTS.filter(x => x.tags.includes(goal));

    return {
      nav: NAV.map(([id, label]) => ({ href: '#' + id, label, cur: s.active === id ? 'page' : undefined })),
      progRef: this.progRef,
      steps, outLines, showAnswer: done,
      stageLabel: done ? 'done // probe removed' : (t < 4 ? 'step 1 // read source' : t < 11 ? 'step 2 // probe attached' : 'step 3 // remove probe'),
      pulseAnim: done ? 'none' : 'bt-pulse 1s steps(1) infinite',
      replay: () => this.startHero(),
      pluginTabs: this.tabs(PLUGIN, s.plugin, 'plugin'),
      pluginLines,
      copyPlugin: () => this.copy('plugin', pl.lines.join('\n')),
      copyPluginLabel: s.copied === 'plugin' ? 'Copied' : s.copied === 'plugin-failed' ? 'Select and copy' : 'Copy',

      audTabs: Object.keys(AUD).map((k, i) => ({
        num: '0' + (i + 1), label: AUD[k].label,
        pick: () => this.setState({ aud: k, goal: AUD[k].goal }),
        bar: k === aud ? 'var(--color-accent)' : 'var(--color-neutral-800)',
        bg: k === aud ? 'color-mix(in srgb, var(--color-accent) 14%, transparent)' : 'transparent'
      })),
      audLead: AUD[aud].lead,
      audRoutes: AUD[aud].routes.map(r => ({ ...r, key: aud + r.title })),

      plugins: PLUGINS, skills: SKILLS, exts: EXTS,

      presets: PRESETS.map(p => ({
        label: p.label, pick: () => this.setState({ ...p.s, preset: p.id }),
        bg: s.preset === p.id ? 'color-mix(in srgb, var(--color-accent) 16%, transparent)' : 'transparent',
        fg: 'var(--color-bg)',
        border: s.preset === p.id ? 'var(--color-accent)' : 'var(--color-neutral-700)'
      })),
      cls: s.cls, meth: s.meth, thr: s.thr,
      setCls: e => setB({ cls: e.target.value.trim() }),
      setMeth: e => setB({ meth: e.target.value.trim() }),
      setThr: e => setB({ thr: +e.target.value }),
      locOpts: ['entry', 'return', 'error'].map(l => ({ label: '@' + l, on: loc === l, pick: () => setB({ loc: l }) })),
      thrDisabled: loc === 'entry',
      thrOpacity: loc === 'entry' ? 0.45 : 1,
      thrLabel: loc === 'entry' ? 'n/a on @entry' : (s.thr > 0 ? s.thr + ' ms' : 'off'),
      fieldChips: FIELDS.map(f => {
        const on = !!s.fields[f] && allowed[f];
        return {
          key: f, disabled: !allowed[f], opacity: allowed[f] ? 1 : 0.4,
          toggle: () => setB({ fields: { ...s.fields, [f]: !s.fields[f] } }),
          bg: on ? 'var(--color-accent)' : 'transparent',
          fg: on ? 'var(--color-bg)' : 'var(--color-neutral-300)',
          border: on ? 'var(--color-accent)' : 'var(--color-neutral-700)'
        };
      }),
      cmdMatcher, cmdLoc: '@' + loc, cmdFilter, cmdAction,
      copyCmd: () => this.copy('cmd', cmd),
      copyCmdLabel: s.copied === 'cmd' ? 'Copied' : s.copied === 'cmd-failed' ? 'Select and copy' : 'Copy',
      sampleOut,

      doctorTabs: [['ready', 'Prepared JVM'], ['plain', 'Plain JVM']].map(([k, label]) => ({
        label, pick: () => this.setState({ doctor: k }),
        bg: s.doctor === k ? 'var(--color-accent)' : 'transparent',
        fg: s.doctor === k ? 'var(--color-bg)' : 'var(--color-neutral-300)',
        border: s.doctor === k ? 'var(--color-accent)' : 'var(--color-neutral-700)'
      })),
      doctorLines: dl.map(([text, k]) => ({ text, color: k === 'hl' ? (s.doctor === 'ready' ? 'var(--color-bg)' : 'var(--color-accent-400)') : TONE[k] })),

      demoItems: DEMOS.map((d, i) => ({
        ...d, pick: () => this.setState({ demo: i }),
        bg: i === s.demo ? 'color-mix(in srgb, var(--color-accent) 12%, transparent)' : 'transparent',
        bar: i === s.demo ? 'var(--color-accent)' : 'transparent',
        numColor: i === s.demo ? 'var(--color-accent)' : 'var(--color-neutral-500)'
      })),
      demoSel,

      goalChips: GOALS.map(([k, label]) => ({
        label, count: k === 'all' ? TUTS.length : TUTS.filter(x => x.tags.includes(k)).length,
        pick: () => this.setState({ goal: k }),
        bg: goal === k ? 'var(--color-accent)' : 'transparent',
        fg: goal === k ? 'var(--color-bg)' : 'var(--color-neutral-300)',
        border: goal === k ? 'var(--color-accent)' : 'var(--color-neutral-700)'
      })),
      tutList,

      instTabs: this.tabs(INST, s.inst, 'inst'),
      instCmd: INST[s.inst].cmd,
      copyInst: () => this.copy('inst', INST[s.inst].cmd),
      copyInstLabel: s.copied === 'inst' ? 'Copied' : s.copied === 'inst-failed' ? 'Select and copy' : 'Copy',

      docCols: DOCS
    };
  }
}

export { LandingPage };
