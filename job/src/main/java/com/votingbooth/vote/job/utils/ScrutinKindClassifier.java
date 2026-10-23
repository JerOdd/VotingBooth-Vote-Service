package com.votingbooth.vote.job.utils;

import com.votingbooth.vote.core.model.entity.VoteKind;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

// Tells what a scrutin is about from its title ("l'amendement n° 80 de...", "l'ensemble du projet de loi..."). Titles mix
// ' and ’ and contain typos ("l'aticle", "sous-amendmeent"), so they are normalized first and the rules stay loose.
@Component
public class ScrutinKindClassifier {

    private static final Pattern APOSTROPHES = Pattern.compile("[’‘ʼ`´]");
    private static final Pattern SPACES = Pattern.compile("[\\s\\u00A0\\u202F]+");

    // First match wins: "l'article unique" is the whole text, before the article rule.
    private static final List<Rule> RULES = List.of(
            new Rule(VoteKind.AMENDMENT, "^(l'|les |le |la )(sous-)?ame"),
            new Rule(VoteKind.MOTION, "^(sur )?la motion"),
            new Rule(VoteKind.FINAL_TEXT, "^l'ensemble"),
            new Rule(VoteKind.FINAL_TEXT, "^l'article unique"),
            new Rule(VoteKind.FINAL_TEXT, "^(la proposition de résolution|la proposition de loi|le projet de loi)"),
            new Rule(VoteKind.PART, "^la (première|deuxième|seconde|troisième|quatrième) partie"),
            new Rule(VoteKind.ARTICLE, "^l'a[a-z]{0,2}ticle"),
            new Rule(VoteKind.PROCEDURAL, "^(la demande de suspension|la demande de seconde délibération|la proposition du gouvernement de prolonger)")
    );

    // Lower case, one kind of apostrophe and of space: what any title matching must work on.
    public String normalize(String title) {
        String text = Normalizer.normalize(title, Normalizer.Form.NFC);
        text = APOSTROPHES.matcher(text).replaceAll("'");
        text = SPACES.matcher(text).replaceAll(" ");
        return text.strip().toLowerCase(Locale.FRENCH);
    }

    public VoteKind classify(String normalizedTitle) {
        for (Rule rule : RULES) {
            if (rule.pattern().matcher(normalizedTitle).find()) {
                return rule.kind();
            }
        }
        return VoteKind.OTHER;
    }

    private record Rule(VoteKind kind, Pattern pattern) {

        Rule(VoteKind kind, String regex) {
            this(kind, Pattern.compile(regex));
        }
    }

}
