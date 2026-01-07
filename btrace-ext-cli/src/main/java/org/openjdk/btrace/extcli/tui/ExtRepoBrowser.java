package org.openjdk.btrace.extcli.tui;

// Copied from client.tui and adapted to ext-cli package

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.table.DefaultTableRenderer;
import com.googlecode.lanterna.gui2.table.Table;
import com.googlecode.lanterna.gui2.table.TableCellRenderer;
import com.googlecode.lanterna.gui2.table.TableHeaderRenderer;
import com.googlecode.lanterna.gui2.dialogs.ListSelectDialogBuilder;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.gui2.dialogs.TextInputDialogBuilder;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.openjdk.btrace.core.extensions.Permission;
import java.util.stream.Stream;

public final class ExtRepoBrowser {
  private ExtRepoBrowser() {}

  private enum SortKey { STATE, ID, VERSION, PATH }
  private static final boolean DEBUG_STATE_RENDER = false;

  public static void launch(String[] args) throws Exception {
    DefaultTerminalFactory factory = new DefaultTerminalFactory();
    Screen screen = factory.createScreen();
    screen.startScreen();
    MultiWindowTextGUI gui = new MultiWindowTextGUI(screen, new DefaultWindowManager(), new EmptySpace());

    final Window window = new BasicWindow("BTrace Extensions");
    window.setHints(List.of(Window.Hint.FULL_SCREEN));

    Panel root = new Panel(new BorderLayout());
    Panel headerPanel = new Panel(new LinearLayout(Direction.VERTICAL));
    headerPanel.setLayoutData(BorderLayout.Location.TOP);
    Label headerPolicy = new Label("");
    headerPolicy.setForegroundColor(TextColor.ANSI.WHITE);
    headerPolicy.setBackgroundColor(TextColor.ANSI.BLUE);
    Label headerRepos = new Label("");
    headerRepos.setForegroundColor(TextColor.ANSI.WHITE);
    headerRepos.setBackgroundColor(TextColor.ANSI.BLUE);
    headerPanel.addComponent(headerPolicy);
    headerPanel.addComponent(headerRepos);
    root.addComponent(headerPanel);

    Panel center = new Panel(new LinearLayout(Direction.VERTICAL));

    PolicyFileLite policy = PolicyFileLite.homeDefault();
    List<Item> all = new ArrayList<>();
    TuiState state = TuiState.load();
    final String[] filterText = new String[] { state.filter == null ? "" : state.filter };
    final SortKey[] sortKey = new SortKey[] { parseSortKey(state.sortKey) };
    final boolean[] sortAsc = new boolean[] { state.sortAsc };
    final boolean[] scanning = new boolean[] { false };
    final double[] splitRatio = new double[] { clampRatio(state.splitRatio) };

    final PolicyFileLite[] polRef = new PolicyFileLite[] { policy };
    final Label[] detailsRef = new Label[1];
    final Label[] footerRef = new Label[1];
    final Runnable[] refreshRef = new Runnable[1];
    final boolean[] splitAdjustMode = new boolean[] { false };

    Table<String> table = new Table<String>("State", "Id", "Version") {
      @Override public Interactable.Result handleKeyStroke(KeyStroke keyStroke) {
        if (splitAdjustMode[0]) {
          if (keyStroke.getKeyType() == KeyType.ArrowUp) {
            splitRatio[0] = Math.max(0.3, splitRatio[0] - 0.05);
            saveState(filterText[0], sortKey[0], sortAsc[0], splitRatio[0]);
            recomputeSizes(window.getTextGUI().getScreen(), this, detailsRef[0], splitRatio);
            try { this.invalidate(); if (detailsRef[0] != null) detailsRef[0].invalidate(); window.invalidate(); } catch (Throwable ignore) {}
            return Interactable.Result.HANDLED;
          } else if (keyStroke.getKeyType() == KeyType.ArrowDown) {
            splitRatio[0] = Math.min(0.9, splitRatio[0] + 0.05);
            saveState(filterText[0], sortKey[0], sortAsc[0], splitRatio[0]);
            recomputeSizes(window.getTextGUI().getScreen(), this, detailsRef[0], splitRatio);
            try { this.invalidate(); if (detailsRef[0] != null) detailsRef[0].invalidate(); window.invalidate(); } catch (Throwable ignore) {}
            return Interactable.Result.HANDLED;
          }
        }
        if (keyStroke.isShiftDown()) {
          if (keyStroke.getKeyType() == KeyType.ArrowUp) {
            splitRatio[0] = Math.max(0.3, splitRatio[0] - 0.05);
            saveState(filterText[0], sortKey[0], sortAsc[0], splitRatio[0]);
            recomputeSizes(window.getTextGUI().getScreen(), this, detailsRef[0], splitRatio);
            return Interactable.Result.HANDLED;
          } else if (keyStroke.getKeyType() == KeyType.ArrowDown) {
            splitRatio[0] = Math.min(0.9, splitRatio[0] + 0.05);
            saveState(filterText[0], sortKey[0], sortAsc[0], splitRatio[0]);
            recomputeSizes(window.getTextGUI().getScreen(), this, detailsRef[0], splitRatio);
            return Interactable.Result.HANDLED;
          }
        }
        if (!splitAdjustMode[0] && (keyStroke.getKeyType() == KeyType.ArrowUp ||
            keyStroke.getKeyType() == KeyType.ArrowDown ||
            keyStroke.getKeyType() == KeyType.PageUp ||
            keyStroke.getKeyType() == KeyType.PageDown ||
            keyStroke.getKeyType() == KeyType.Home ||
            keyStroke.getKeyType() == KeyType.End)) {
          Interactable.Result r = super.handleKeyStroke(keyStroke);
          Item it = findItemByRow(all, this, getSelectedRow());
          if (it != null && detailsRef[0] != null) {
            detailsRef[0].setText(describe(it, polRef[0]));
            detailsRef[0].setForegroundColor(it.privileged ? TextColor.ANSI.YELLOW : TextColor.ANSI.DEFAULT);
          }
          return r;
        }
        if (keyStroke.getKeyType() == KeyType.Character) {
          char c = keyStroke.getCharacter();
          if (c == ' ' || c == 'c' || c == 'C') {
            Item it = findItemByRow(all, this, getSelectedRow());
            if (it != null) {
              try {
                if (c == ' ') {
                  if (polRef[0].isAllowed(it.id)) polRef[0].deny(it.id);
                  else if (polRef[0].isDenied(it.id)) polRef[0].allow(it.id);
                  else {
                    if (!confirmAllow((WindowBasedTextGUI)window.getTextGUI(), it)) return Interactable.Result.HANDLED;
                    polRef[0].allow(it.id);
                  }
                } else {
                  polRef[0].clear(it.id);
                }
                polRef[0].save();
              } catch (Exception e) {
                MessageDialog.showMessageDialog((WindowBasedTextGUI)window.getTextGUI(), "Error", "Failed to update policy: " + e.getMessage());
              }
              if (refreshRef[0] != null) refreshRef[0].run();
              if (footerRef[0] != null) showToast((WindowBasedTextGUI)window.getTextGUI(), footerRef[0], scanning,
                  () -> "Arrows navigate • space toggle • c clear • e explain • / filter • s sort • m adjust-split • ? help • q quit",
                  "Saved", 900);
            }
            return Interactable.Result.HANDLED;
          }
        }
        return super.handleKeyStroke(keyStroke);
      }
    };
    Border tableBox = Borders.singleLine("Extensions");
    ((AbstractBorder)tableBox).setComponent(table);
    center.addComponent(tableBox);

    Label legend = new Label("State: ?=default, +=allowed, -=denied");
    center.addComponent(legend);

    center.addComponent(new Separator(Direction.HORIZONTAL));

    Label details = new Label("");
    Border detailsBox = Borders.singleLine("Details");
    ((AbstractBorder)detailsBox).setComponent(details);
    center.addComponent(detailsBox);
    detailsRef[0] = details;

    root.addComponent(center, BorderLayout.Location.CENTER);

    Label footer = new Label("Arrows navigate • space toggle • c clear • e explain • / filter • s sort • m adjust-split • ? help • q quit");
    footer.setForegroundColor(TextColor.ANSI.BLACK);
    footer.setBackgroundColor(TextColor.ANSI.WHITE);
    root.addComponent(footer, BorderLayout.Location.BOTTOM);
    footerRef[0] = footer;

    window.setComponent(root);

    Runnable refresh = () -> {
      table.getTableModel().clear();
      String f = filterText[0].toLowerCase(Locale.ROOT);
      Comparator<Item> cmp = comparator(sortKey[0], sortAsc[0], policy);
      new ArrayList<>(all).stream()
          .filter(it -> f.isEmpty() || it.id.toLowerCase(Locale.ROOT).contains(f) || it.dir.toString().toLowerCase(Locale.ROOT).contains(f))
          .sorted(cmp)
          .forEach(it -> table.getTableModel().addRow(stateSymbol(policy, it), it.id, it.version == null ? "" : it.version));
      headerPolicy.setText(buildPolicyHeader(policy));
      headerRepos.setText(buildReposHeader());
      footer.setText(scanning[0]
          ? ("Scanning extensions… (" + all.size() + ")  •  Press q to quit")
          : (splitAdjustMode[0]
              ? "Adjust split: Up/Down to resize • Esc to exit"
              : "Arrows navigate • space toggle • c clear • e explain • / filter • s sort • m adjust-split • ? help • q quit"));
      int sel = table.getSelectedRow();
      if (sel >= 0 && sel < table.getTableModel().getRowCount()) {
        Item it = findItemByRow(all, table, sel);
        if (it != null) {
          details.setText(describe(it, policy));
          details.setForegroundColor(it.privileged ? TextColor.ANSI.YELLOW : TextColor.ANSI.DEFAULT);
        }
      } else {
        details.setText("");
      }
    };

    refreshRef[0] = refresh;

    try {
      DefaultTableRenderer<String> dr = (DefaultTableRenderer<String>) table.getRenderer();
      dr.setAllowPartialColumn(false);
      Set<Integer> expand = new HashSet<>();
      expand.add(1);
      dr.setExpandableColumns(expand);
    } catch (Throwable ignored) {}

    table.setTableHeaderRenderer(new TableHeaderRenderer<String>() {
      @Override public TerminalSize getPreferredSize(Table<String> table, String label, int columnIndex) {
        int pad = 2;
        if (columnIndex == 0) return new TerminalSize(Math.max(3 + pad * 2, (label == null ? 0 : label.length()) + pad * 2), 1);
        return new TerminalSize((label == null ? 0 : label.length()) + pad * 2, 1);
      }
      @Override public void drawHeader(Table<String> table, String label, int columnIndex, TextGUIGraphics g) {
        String base = label == null ? "" : label;
        g.setBackgroundColor(TextColor.ANSI.WHITE);
        g.setForegroundColor(TextColor.ANSI.BLACK);
        g.fill(' ');
        TerminalSize sz = g.getSize();
        int width = sz != null ? sz.getColumns() : base.length();
        int x = Math.max(0, (width - base.length()) / 2);
        g.enableModifiers(SGR.BOLD);
        g.putString(x, 0, base);
        g.disableModifiers(SGR.BOLD);
      }
    });

    table.setTableCellRenderer(new TableCellRenderer<String>() {
      @Override public TerminalSize getPreferredSize(Table<String> table, String cell, int column, int row) {
        int pad = 2;
        if (column == 0) return new TerminalSize(3 + pad * 2, 1);
        int len = (cell == null ? 0 : cell.length()) + pad * 2;
        return new TerminalSize(len, 1);
      }
      @Override public void drawCell(Table<String> table, String cell, int column, int row, TextGUIGraphics graphics) {
        boolean isSelected = row == table.getSelectedRow();
        if (isSelected) {
          graphics.setBackgroundColor(TextColor.ANSI.CYAN);
        }
        String text = cell == null ? "" : cell;
        if (column == 0) {
          try {
            String id = table.getTableModel().getRow(row).get(1);
            boolean allowed = polRef[0].isAllowed(id);
            boolean denied = polRef[0].isDenied(id);
            String sym = allowed ? "+" : (denied ? "-" : "?");
            if (DEBUG_STATE_RENDER) {
              text = "[" + sym + "]";
              try { System.err.println("[TUI-STATE] row=" + row + " id=" + id + " allowed=" + allowed + " denied=" + denied + " sym=" + sym); } catch (Throwable ignore) {}
            } else {
              text = sym;
            }
          } catch (Exception ignored) {}
          if (text == null || text.isEmpty() || " ".equals(text)) text = "?";
        }
        graphics.setForegroundColor(TextColor.ANSI.BLACK);
        TerminalSize sz = graphics.getSize();
        graphics.fill(' ');
        String padded = "  " + text + "  ";
        graphics.putString(0, 0, padded);
      }
    });

    window.addWindowListener(new WindowListenerAdapter() {
      @Override public void onUnhandledInput(Window basePane, KeyStroke keyStroke, AtomicBoolean handled) {
        switch (keyStroke.getKeyType()) {
          case Character: {
            char c = keyStroke.getCharacter();
            if (c == 'q' || c == 'Q') { saveState(filterText[0], sortKey[0], sortAsc[0], splitRatio[0]); window.close(); handled.set(true); return; }
            if (c == '?') { showHelp(gui); handled.set(true); return; }
            if (c == 'm' || c == 'M') {
              if (splitAdjustMode[0]) { splitAdjustMode[0] = false; footer.setText("Arrows navigate • space toggle • c clear • e explain • / filter • s sort • m adjust-split • ? help • q quit"); }
              else { splitAdjustMode[0] = true; footer.setText("Adjust split: Up/Down to resize • Esc to exit"); }
              handled.set(true); return;
            }
            if (c == 'e' || c == 'E') { Item it = findItemByRow(all, table, table.getSelectedRow()); if (it != null) showPrivileges(gui, it); handled.set(true); return; }
            if (c == '/') { String ft = new TextInputDialogBuilder().setTitle("Filter").setDescription("Enter filter (id/path contains):").build().showDialog(gui); if (ft != null) { filterText[0] = ft; saveState(filterText[0], sortKey[0], sortAsc[0], splitRatio[0]); refresh.run(); } handled.set(true); return; }
            if (c == 's' || c == 'S') { SortKey chosen = chooseSort(gui, sortKey[0]); if (chosen != null) { if (chosen == sortKey[0]) sortAsc[0] = !sortAsc[0]; else { sortKey[0] = chosen; sortAsc[0] = true; } saveState(filterText[0], sortKey[0], sortAsc[0], splitRatio[0]); refresh.run(); } handled.set(true); return; }
            if (c == 'r' || c == 'R') { scanAsync(gui, all, refresh, scanning); handled.set(true); return; }
            break;
          }
          case Escape: {
            if (splitAdjustMode[0]) { splitAdjustMode[0] = false; footer.setText("Arrows navigate • space toggle • c clear • e explain • / filter • s sort • m adjust-split • ? help • q quit"); handled.set(true); return; }
            break;
          }
          case ArrowUp:
          case ArrowDown: {
            if (splitAdjustMode[0]) {
              if (keyStroke.getKeyType() == KeyType.ArrowUp) splitRatio[0] = Math.max(0.3, splitRatio[0] - 0.05);
              else splitRatio[0] = Math.min(0.9, splitRatio[0] + 0.05);
              saveState(filterText[0], sortKey[0], sortAsc[0], splitRatio[0]);
              recomputeSizes(window.getTextGUI().getScreen(), table, details, splitRatio);
              try { table.invalidate(); details.invalidate(); window.invalidate(); } catch (Throwable ignore) {}
              handled.set(true); return;
            }
            break;
          }
          default: break;
        }
      }
      @Override public void onResized(Window window, TerminalSize oldSize, TerminalSize newSize) {
        recomputeSizes(window.getTextGUI().getScreen(), table, details, splitRatio);
      }
    });

    recomputeSizes(screen, table, details, splitRatio);
    scanAsync(gui, all, refresh, scanning);
    window.setComponent(root);
    gui.addWindowAndWait(window);
    screen.stopScreen();
  }

  private static String stateSymbol(PolicyFileLite policy, Item it) {
    if (policy.isAllowed(it.id)) return "+";
    if (policy.isDenied(it.id)) return "-";
    return "?";
  }

  private static Comparator<Item> comparator(SortKey k, boolean asc, PolicyFileLite policy) {
    Comparator<Item> c;
    switch (k) {
      case STATE: c = Comparator.comparing((Item i) -> stateOrder(policy, i)); break;
      case VERSION: c = Comparator.comparing(i -> i.version == null ? "" : i.version, String::compareToIgnoreCase); break;
      case PATH: c = Comparator.comparing(i -> i.dir.toString(), String::compareToIgnoreCase); break;
      case ID:
      default: c = Comparator.comparing(i -> i.id, String::compareToIgnoreCase); break;
    }
    return asc ? c : c.reversed();
  }

  private static int stateOrder(PolicyFileLite policy, Item it) {
    if (policy.isAllowed(it.id)) return 0;
    if (policy.isDenied(it.id)) return 2;
    return 1;
  }

  private static SortKey parseSortKey(String key) {
    if (key == null) return SortKey.ID;
    try { return SortKey.valueOf(key.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException ex) { return SortKey.ID; }
  }

  private static double clampRatio(double r) {
    if (Double.isNaN(r) || Double.isInfinite(r)) return 0.65d;
    if (r < 0.3d) return 0.3d;
    if (r > 0.9d) return 0.9d;
    return r;
  }

  private static void saveState(String filter, SortKey sortKey, boolean sortAsc, double ratio) {
    TuiState s = new TuiState();
    s.filter = filter == null ? "" : filter;
    s.sortKey = sortKey == null ? "ID" : sortKey.name();
    s.sortAsc = sortAsc;
    s.splitRatio = clampRatio(ratio);
    TuiState.save(s);
  }

  private static SortKey chooseSort(MultiWindowTextGUI gui, SortKey current) {
    List<SortKey> opts = Arrays.asList(SortKey.STATE, SortKey.ID, SortKey.VERSION, SortKey.PATH);
    return new ListSelectDialogBuilder<SortKey>()
        .setTitle("Sort")
        .setDescription("Sort by (repeat to toggle asc/desc):")
        .addListItems(opts.toArray(new SortKey[0]))
        .build()
        .showDialog(gui);
  }

  private static void showHelp(MultiWindowTextGUI gui) {
    MessageDialog.showMessageDialog(gui, "Help",
        "Keys:\n" +
        "  Arrows/PageUp/PageDown/Home/End: navigate (details update automatically)\n" +
        "  space: Toggle state in list (+/-/?)\n" +
        "  c: Clear to default (removes from allow/deny)\n" +
        "  e: Explain privileges (dialog with risk descriptions)\n" +
        "  /: Filter   s: Sort    m: Adjust split (then Up/Down, Esc to exit)\n" +
        "\n" +
        "Details panel:\n" +
        "  Shows full-word state: default / allowed / denied\n" +
        "  ?: Help     q: Quit\n");
  }

  private static boolean confirmAllow(WindowBasedTextGUI gui, Item it) {
    StringBuilder sb = new StringBuilder();
    sb.append("Allow extension '" + it.id + "'?");
    List<String> names = it.requiredPerms;
    if (names != null && !names.isEmpty()) {
      sb.append("\n\nRequests permissions:\n");
      for (String n : names) {
        String up = n.trim().toUpperCase(Locale.ROOT);
        String desc;
        try {
          Permission p = Permission.valueOf(up);
          desc = p.getRiskDescription();
        } catch (IllegalArgumentException ex) {
          desc = "(unknown)";
        }
        sb.append("  - ").append(up).append(": ").append(desc).append('\n');
      }
    } else {
      sb.append("\n\nThis extension does not declare specific permissions.");
    }
    MessageDialogButton res = MessageDialog.showMessageDialog(gui, "Confirm Allow", sb.toString(), MessageDialogButton.Yes, MessageDialogButton.No);
    return res == MessageDialogButton.Yes;
  }

  private static void showToast(WindowBasedTextGUI gui, Label footer, boolean[] scanning, Supplier<String> base, String text, int millis) {
    String old = footer.getText();
    footer.setText(text);
    new Thread(() -> {
      try { Thread.sleep(millis); } catch (InterruptedException ignored) {}
      gui.getGUIThread().invokeLater(() -> footer.setText(scanning[0] ? ("Scanning extensions…  •  Press q to quit") : base.get()));
    }, "btracex-toast").start();
  }

  private static void showPrivileges(MultiWindowTextGUI gui, Item it) {
    StringBuilder sb = new StringBuilder();
    sb.append("Extension: ").append(it.id).append('\n');
    List<String> names = it.requiredPerms;
    if (names != null && !names.isEmpty()) {
      sb.append("\nRequested permissions:\n");
      for (String n : names) {
        String up = n.trim().toUpperCase(Locale.ROOT);
        String desc;
        try {
          Permission p = Permission.valueOf(up);
          desc = p.getRiskDescription();
        } catch (IllegalArgumentException ex) {
          desc = "(unknown)";
        }
        sb.append("  - ").append(up).append(": ").append(desc).append('\n');
      }
    } else {
      sb.append("\nNo explicit permissions declared by this extension.");
    }
    MessageDialog.showMessageDialog(gui, "Privileges", sb.toString(), MessageDialogButton.OK);
  }

  private static String describe(Item it, PolicyFileLite policy) {
    String state = policy.isAllowed(it.id) ? "allowed" : (policy.isDenied(it.id) ? "denied" : "default");
    StringBuilder sb = new StringBuilder();
    sb.append("Extension: ").append(it.id)
      .append("\nVersion  : ").append(it.version == null ? "" : it.version)
      .append("\nPrivileged: ").append(it.privileged)
      .append("\nState    : ").append(state)
      .append("\nPath     : ").append(it.dir);
    if (it.requiredPerms != null && !it.requiredPerms.isEmpty()) {
      sb.append("\nRequires : ");
      for (int i = 0; i < it.requiredPerms.size(); i++) { if (i > 0) sb.append(','); sb.append(it.requiredPerms.get(i)); }
    }
    return sb.toString();
  }

  private static Item findItemByRow(List<Item> all, Table<String> table, int row) {
    if (row < 0 || row >= table.getTableModel().getRowCount()) return null;
    String id = table.getTableModel().getRow(row).get(1);
    for (Item it : all) if (it.id.equals(id)) return it; return null;
  }

  private static void recomputeSizes(Screen screen, Table<String> table, Label details, double[] splitRatio) {
    TerminalSize ts = screen.getTerminalSize();
    int totalCols = Math.max(80, ts.getColumns());
    int totalRows = Math.max(24, ts.getRows());
    int headerFooter = 2;
    int centerRows = Math.max(10, totalRows - headerFooter);
    int reserved = 2;
    int tableRows = Math.max(6, (int)Math.round(centerRows * splitRatio[0]) - reserved);
    int detailsRows = Math.max(4, centerRows - tableRows - reserved);
    table.setPreferredSize(new TerminalSize(totalCols, tableRows));
    details.setPreferredSize(new TerminalSize(totalCols, detailsRows));
  }

  private static String buildPolicyHeader(PolicyFileLite policy) { return "Policy: " + String.valueOf(policy.getTarget()); }
  private static String buildReposHeader() {
    StringBuilder sb = new StringBuilder(); sb.append("Repos: ");
    List<Path> roots = RepoScannerLite.roots(); boolean first = true;
    for (Path r : roots) { if (r == null || !Files.isDirectory(r)) continue; if (!first) sb.append(", "); sb.append(r.toString()); first = false; }
    if (first) sb.append("(none)"); return sb.toString();
  }

  private static void scanAsync(MultiWindowTextGUI gui, List<Item> all, Runnable refresh, boolean[] scanning) {
    if (scanning[0]) return; scanning[0] = true; all.clear(); refresh.run();
    new Thread(() -> { try { for (Path root : RepoScannerLite.roots()) { if (root == null || !Files.isDirectory(root)) continue; try (Stream<Path> s = Files.list(root)) { for (Path p : (Iterable<Path>) s::iterator) { if (!Files.isDirectory(p)) continue; try { ExtensionInspectorLite.Report r = ExtensionInspectorLite.inspectDirectory(p); synchronized (all) { all.add(new Item(r.id, r.version, r.privileged, p, r.requiredPermNames)); } gui.getGUIThread().invokeLater(refresh); } catch (Exception ignored) {} } } catch (Exception ignored) {} } } finally { scanning[0] = false; gui.getGUIThread().invokeLater(refresh); } }, "btracex-scan").start();
  }

  private static final class Item {
    final String id; final String version; final boolean privileged; final Path dir; final List<String> requiredPerms;
    Item(String id, String version, boolean privileged, Path dir) { this(id, version, privileged, dir, Collections.emptyList()); }
    Item(String id, String version, boolean privileged, Path dir, List<String> requiredPerms) { this.id = id; this.version = version; this.privileged = privileged; this.dir = dir; this.requiredPerms = requiredPerms; }
  }
}
