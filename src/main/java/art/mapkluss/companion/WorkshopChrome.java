package art.mapkluss.companion;

/** Shared pixel chrome; each compatibility bridge supplies its rectangle painter. */
final class WorkshopChrome {
    interface Painter { void fill(int x, int y, int right, int bottom, int argb); }
    record State(boolean enabled, boolean hovered, boolean focused, boolean pressed, boolean selected, boolean loading, UiAction.Kind kind) {}
    record Appearance(int face, int edgeLight, int edgeDark, int text, int marker, int contentOffset) {}

    static Appearance appearance(WorkshopTheme t, State s) {
        int accent = switch (s.kind == null ? UiAction.Kind.DEFAULT : s.kind) {
            case WARNING -> t.color("warning");
            case DANGER -> t.color("error");
            case TECHNICAL -> t.color("info");
            default -> t.color("accent");
        };
        boolean down = s.enabled && s.pressed;
        boolean primary = s.enabled && s.kind == UiAction.Kind.PRIMARY;
        int face = !s.enabled ? t.color("surface-primary") : primary
            ? t.color(down ? "accent-active" : s.hovered ? "accent-hover" : "accent")
            : t.color(down ? "field-bg" : s.hovered ? "surface-elevated" : "surface-secondary");
        int text = !s.enabled ? t.color("text-disabled") : primary ? t.color("on-accent") : t.color("text-primary");
        return new Appearance(face, t.color(down ? "field-bg" : "border-strong"), t.color(down ? "border-strong" : "field-bg"), text,
            s.focused && s.enabled ? t.color("focus") : s.loading ? t.color("info") : accent, down ? 1 : 0);
    }

    static void button(Painter p, WorkshopLayout.Rect r, WorkshopTheme theme, State state) {
        if (r.width() < 6 || r.height() < 6) return;
        Appearance a = appearance(theme, state);
        int x = r.x(), y = r.y(), right = r.right(), bottom = r.bottom();
        p.fill(x + 1, y + 1, right - 1, bottom - 1, a.face);
        p.fill(x + 1, y, right - 1, y + 1, a.edgeLight);
        p.fill(x, y + 1, x + 1, bottom - 1, a.edgeLight);
        p.fill(x + 1, bottom - 1, right - 1, bottom, a.edgeDark);
        p.fill(right - 1, y + 1, right, bottom - 1, a.edgeDark);
        if (state.selected) p.fill(x + 3, bottom - 3, right - 3, bottom - 1, a.marker);
        if (state.focused && state.enabled) {
            p.fill(x + 2, y + 2, right - 2, y + 3, a.marker);
            p.fill(x + 2, bottom - 3, right - 2, bottom - 2, a.marker);
            p.fill(x + 2, y + 3, x + 3, bottom - 3, a.marker);
            p.fill(right - 3, y + 3, right - 2, bottom - 3, a.marker);
        }
    }

    static void frame(Painter p, WorkshopLayout.Rect r, WorkshopTheme t) {
        p.fill(r.x() + 2, r.y(), r.right() - 2, r.bottom(), t.color("border"));
        p.fill(r.x(), r.y() + 2, r.right(), r.bottom() - 2, t.color("border"));
        p.fill(r.x() + 2, r.y() + 2, r.right() - 2, r.bottom() - 2, t.color("border-strong"));
        p.fill(r.x() + 4, r.y() + 4, r.right() - 4, r.bottom() - 4, t.color("surface-primary"));
        p.fill(r.x() + 4, r.bottom() - 4, r.right() - 4, r.bottom() - 2, t.color("field-bg"));
    }

    static void librarySections(Painter p, WorkshopLayout.Shell shell, WorkshopTheme theme) {
        int left = shell.navigation().x(), right = shell.navigation().right();
        int dark = theme.color("field-bg"), edge = theme.color("border-subtle");
        for (int y : new int[]{shell.navigation().bottom() + 1, shell.tabs().bottom() + 1, shell.footer().y() - 3}) {
            p.fill(left, y, right, y + 1, dark);
            p.fill(left, y + 1, right, y + 2, edge);
        }
        if (shell.split()) {
            int x = shell.list().right() + 1;
            p.fill(x, shell.list().y(), x + 1, shell.list().bottom(), dark);
            p.fill(x + 1, shell.list().y(), x + 2, shell.list().bottom(), edge);
        }
    }
}
