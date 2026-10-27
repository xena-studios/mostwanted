package co.xenastudios.mostwanted.hud;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

/**
 * One player's sidebar. Lines are score entries showing a custom name, with the
 * numbers hidden; only lines that changed are sent again.
 */
final class Sidebar {

    /** The sidebar shows at most 15 lines. */
    static final int MAX_LINES = 15;

    private final Scoreboard board;
    private final Objective objective;
    private final List<Component> shown = new ArrayList<>();

    Sidebar(ScoreboardManager manager, Component title) {
        board = manager.getNewScoreboard();
        objective = board.registerNewObjective("mostwanted", Criteria.DUMMY, title);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());
    }

    Scoreboard board() {
        return board;
    }

    void lines(List<Component> lines) {
        if (lines.size() > MAX_LINES) {
            lines = lines.subList(0, MAX_LINES);
        }
        if (lines.size() != shown.size()) {
            // The order comes from the scores, which depend on the line count: redo them all.
            for (int i = 0; i < shown.size(); i++) {
                board.resetScores(entry(i));
            }
            shown.clear();
        }
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            if (i < shown.size() && shown.get(i).equals(line)) {
                continue;
            }
            var score = objective.getScore(entry(i));
            score.setScore(lines.size() - i);
            score.customName(line);
            if (i < shown.size()) {
                shown.set(i, line);
            } else {
                shown.add(line);
            }
        }
    }

    /** Each line's hidden key; the custom name is what shows. */
    private static String entry(int line) {
        return "line" + line;
    }
}
