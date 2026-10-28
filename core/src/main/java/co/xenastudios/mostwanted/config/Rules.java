package co.xenastudios.mostwanted.config;

import java.time.Duration;
import org.bukkit.configuration.ConfigurationSection;

/**
 * The numbers the core loop runs on, from config.yml's rules section, so they can be
 * balanced without a new build. The defaults are the decided rules (docs/02-gameplay.md).
 *
 * @param sentenceBase every sentence is at least this long
 * @param sentenceStep added for every {@code sentenceBountyStep} of bounty
 * @param sentenceBountyStep how much bounty adds one {@code sentenceStep}
 * @param sentenceMax no sentence is longer
 * @param killShare the share of the bounty a guard gets for killing rather than cuffing, in percent
 * @param guardKillBounty added to a criminal's bounty for killing an on-duty guard
 * @param cuffRange how close, in blocks, a guard must be to cuff someone
 * @param combatTag how long after a fight a guard can't go off duty
 */
public record Rules(Duration sentenceBase, Duration sentenceStep, long sentenceBountyStep, Duration sentenceMax,
        int killShare, long guardKillBounty, double cuffRange, Duration combatTag) {

    public static final Rules DEFAULTS = new Rules(Duration.ofMinutes(1), Duration.ofMinutes(1), 2500,
            Duration.ofMinutes(5), 50, 500, 3, Duration.ofSeconds(15));

    static Rules read(ConfigurationSection rules) {
        if (rules == null) {
            return DEFAULTS;
        }
        Rules read = new Rules(
                Duration.ofSeconds(rules.getLong("sentence.base-seconds", DEFAULTS.sentenceBase.toSeconds())),
                Duration.ofSeconds(rules.getLong("sentence.seconds-per-step", DEFAULTS.sentenceStep.toSeconds())),
                rules.getLong("sentence.bounty-per-step", DEFAULTS.sentenceBountyStep),
                Duration.ofSeconds(rules.getLong("sentence.max-seconds", DEFAULTS.sentenceMax.toSeconds())),
                rules.getInt("kill-share-percent", DEFAULTS.killShare),
                rules.getLong("guard-kill-bounty", DEFAULTS.guardKillBounty),
                rules.getDouble("cuff-range", DEFAULTS.cuffRange),
                Duration.ofSeconds(rules.getLong("combat-tag-seconds", DEFAULTS.combatTag.toSeconds())));
        if (read.sentenceBountyStep < 1 || read.killShare < 0 || read.killShare > 100 || read.guardKillBounty < 0
                || read.cuffRange <= 0 || read.sentenceBase.isNegative() || read.sentenceMax.isNegative()) {
            throw new IllegalArgumentException("a value in rules is out of range");
        }
        return read;
    }

    /** 1 minute, plus 1 for every $2,500 of bounty, up to 5 (with the defaults). */
    public Duration sentence(long bounty) {
        Duration sentence = sentenceBase.plus(sentenceStep.multipliedBy(bounty / sentenceBountyStep));
        return sentence.compareTo(sentenceMax) > 0 ? sentenceMax : sentence;
    }

    /** What a guard is paid for an arrest. */
    public long payout(long bounty, boolean killed) {
        return killed ? bounty * killShare / 100 : bounty;
    }
}
