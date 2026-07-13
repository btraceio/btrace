(() => {
  const form = document.querySelector('#probe-form');
  const goal = document.querySelector('#probe-goal');
  const className = document.querySelector('#probe-class');
  const method = document.querySelector('#probe-method');
  const threshold = document.querySelector('#probe-threshold');
  const targetFields = document.querySelector('#custom-target-fields');
  const thresholdField = document.querySelector('#threshold-field');
  const note = document.querySelector('#probe-note');
  const command = document.querySelector('#generated-command');
  const script = document.querySelector('#generated-script');
  const title = document.querySelector('#probe-result-title');
  const explainer = document.querySelector('#probe-explainer');
  const docLink = document.querySelector('#probe-doc-link');
  const copyStatus = document.querySelector('#copy-status');
  const copyButtons = document.querySelectorAll('[data-copy]');
  const escapeJava = (value) => value.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
  const safeValue = (input, fallback) => input.trim() || fallback;

  const templates = {
    slow: {
      title: 'Slow method probe', explanation: 'Hooks method returns and prints only calls above the selected latency budget.',
      docs: 'https://github.com/btraceio/btrace/blob/develop/docs/OnelinerGuide.md#performance-debugging',
      note: 'Specific patterns and a duration threshold keep tracing overhead low.',
      command: (clazz, meth, ms) => `jbang btrace@btraceio -n '${clazz}::${meth} @return if duration>${ms}ms { print method, duration }' <PID>`,
      script: (clazz, meth, ms) => `@BTrace\npublic class SlowMethodProbe {\n    @OnMethod(clazz = "${escapeJava(clazz)}", method = "${escapeJava(meth)}", location = @Location(Kind.RETURN))\n    public static void onReturn(@ProbeMethodName String method, @Duration long duration) {\n        if (duration > ${ms}_000_000L) {\n            println(method + " took " + str(duration / 1_000_000L) + " ms");\n        }\n    }\n}`
    },
    calls: {
      title: 'Method call probe', explanation: 'Hooks method entry and prints the method name with the arguments BTrace receives.',
      docs: 'https://github.com/btraceio/btrace/blob/develop/docs/OnelinerGuide.md#monitoring-method-calls',
      note: 'Start with a specific class; printing arguments can be expensive on a hot path.',
      command: (clazz, meth) => `jbang btrace@btraceio -n '${clazz}::${meth} @entry { print method, args }' <PID>`,
      script: (clazz, meth) => `@BTrace\npublic class MethodCallProbe {\n    @OnMethod(clazz = "${escapeJava(clazz)}", method = "${escapeJava(meth)}")\n    public static void onEntry(@ProbeMethodName String method, AnyType[] args) {\n        println(method);\n        printArray(args);\n    }\n}`
    },
    errors: {
      title: 'Exception path probe', explanation: 'Runs only when the chosen method exits by throwing and prints a bounded stack trace.',
      docs: 'https://github.com/btraceio/btrace/blob/develop/docs/OnelinerGuide.md#exception-tracking',
      note: 'Keep stack depth bounded. Start with the method where the failure is observed.',
      command: (clazz, meth) => `jbang btrace@btraceio -n '${clazz}::${meth} @error { print method, stack(5) }' <PID>`,
      script: (clazz, meth) => `@BTrace\npublic class ExceptionPathProbe {\n    @OnMethod(clazz = "${escapeJava(clazz)}", method = "${escapeJava(meth)}", location = @Location(Kind.ERROR))\n    public static void onError(@ProbeMethodName String method) {\n        println(method);\n        jstack(5);\n    }\n}`
    },
    count: {
      title: 'Invocation counter', explanation: 'Counts matching calls and emits a running count without printing every invocation.',
      docs: 'https://github.com/btraceio/btrace/blob/develop/docs/OnelinerGuide.md#count---count-invocations',
      note: 'This counter is printed from the BTrace client menu. For periodic summaries or grouped metrics, move to a Java probe.',
      command: (clazz, meth) => `jbang btrace@btraceio -n '${clazz}::${meth} @entry { count }' <PID>`,
      script: (clazz, meth) => `@BTrace\npublic class InvocationCounterProbe {\n    private static long calls;\n\n    @OnMethod(clazz = "${escapeJava(clazz)}", method = "${escapeJava(meth)}")\n    public static void onEntry() {\n        calls++;\n    }\n\n    @OnTimer(5000)\n    public static void report() {\n        println("calls: " + str(calls));\n    }\n}`
    },
    sql: {
      title: 'Slow SQL probe', explanation: 'Times JDBC Statement execution and reports calls that exceed the selected threshold.',
      docs: 'https://github.com/btraceio/btrace/blob/develop/docs/OnelinerGuide.md#performance-debugging',
      note: 'This template targets JDBC Statement execution. Adjust the threshold to avoid noisy output.',
      command: (_clazz, _meth, ms) => `jbang btrace@btraceio -n 'java.sql.Statement::execute* @return if duration>${ms}ms { print method, duration }' <PID>`,
      script: (_clazz, _meth, ms) => `@BTrace\npublic class SlowSqlProbe {\n    @OnMethod(clazz = "java.sql.Statement", method = "execute*", location = @Location(Kind.RETURN))\n    public static void onReturn(@ProbeMethodName String method, @Duration long duration) {\n        if (duration > ${ms}_000_000L) {\n            println(method + " took " + str(duration / 1_000_000L) + " ms");\n        }\n    }\n}`
    }
  };

  function update() {
    const selected = templates[goal.value];
    const clazz = safeValue(className.value, 'com.myapp.OrderService');
    const meth = safeValue(method.value, '*');
    const ms = Math.max(1, Math.min(60000, Number.parseInt(threshold.value, 10) || 200));
    const sql = goal.value === 'sql';
    const timed = goal.value === 'slow' || sql;
    targetFields.hidden = sql;
    thresholdField.hidden = !timed;
    note.textContent = selected.note;
    title.textContent = selected.title;
    explainer.textContent = selected.explanation;
    docLink.href = selected.docs;
    command.textContent = selected.command(clazz, meth, ms);
    script.textContent = selected.script(clazz, meth, ms);
  }

  form.addEventListener('input', update);
  form.addEventListener('change', update);
  document.querySelectorAll('[data-goal]').forEach((link) => link.addEventListener('click', () => { goal.value = link.dataset.goal; update(); }));
  copyButtons.forEach((button) => button.addEventListener('click', async () => {
    const source = button.dataset.copy === 'script' ? script.textContent : command.textContent;
    try {
      await navigator.clipboard.writeText(source);
      const original = button.textContent;
      button.textContent = 'Copied';
      copyStatus.textContent = `${button.dataset.copy === 'script' ? 'Java probe' : 'Command'} copied to clipboard.`;
      window.setTimeout(() => { button.textContent = original; }, 1600);
    } catch (_) {
      button.textContent = 'Select text to copy';
      copyStatus.textContent = 'Clipboard access is unavailable. Select the text and copy it manually.';
    }
  }));
  update();
})();
