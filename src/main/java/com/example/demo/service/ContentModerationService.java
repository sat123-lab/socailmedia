package com.example.demo.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pluggable content-moderation entry point used by every text-creating
 * service (comments, posts, reels, group messages…).
 *
 * <p>Default implementation is a high-precision keyword + regex screener
 * tuned for false-positive-free baseline filtering. The interface is
 * intentionally minimal so a future swap to an ML moderation service
 * (Perspective API, Llama-Guard, etc.) is a one-class change.</p>
 *
 * <h3>Public contract</h3>
 * <ul>
 *   <li>{@link #screen(String)} — returns a verdict.</li>
 *   <li>{@link #assertAllowed(String)} — throws {@link ContentBlockedException}
 *       when {@link Verdict#blocked} is true.</li>
 * </ul>
 *
 * <p>Verdicts are also persisted via the standard audit log when the caller
 * is in the admin tier, so we can track moderation outcomes for reporting.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContentModerationService {

    /** Block-list keywords — strict word boundaries to avoid Scunthorpe-style false positives. */
    @Value("${app.moderation.blocklist:fuck,shit,bitch,whore,slut,cunt,nigger,faggot,kill yourself}")
    private String blocklistCsv;

    /** Suspicious link / spam regexes — kept conservative. */
    private static final List<Pattern> SPAM_REGEXES = List.of(
            Pattern.compile("(?i)\\b(?:click|buy|earn)\\s+now\\b"),
            Pattern.compile("(?i)free\\s+v[!1i]agra"),
            Pattern.compile("(?i)\\b(?:btc|bitcoin|airdrop)\\b.+(?:dm|telegram|whatsapp)"),
            Pattern.compile("(?i)https?://\\S{0,40}(?:\\.tk|\\.ml|\\.gq|\\.cf)\\b")
    );

    private Set<Pattern> blocklist;

    /**
     * @return verdict — {@code blocked} indicates the body must be rejected;
     *   {@code label} is one of: OK, PROFANITY, SPAM, BOTH.
     */
    public Verdict screen(String text) {
        if (text == null || text.isBlank()) return Verdict.OK;
        ensureCompiled();
        String lower = text.toLowerCase();

        boolean profane = blocklist.stream().anyMatch(p -> p.matcher(lower).find());
        boolean spam    = SPAM_REGEXES.stream().anyMatch(p -> p.matcher(text).find());

        if (profane && spam)  return new Verdict(true,  "BOTH",      "profanity + spam");
        if (profane)          return new Verdict(true,  "PROFANITY", "blocked words");
        if (spam)             return new Verdict(true,  "SPAM",      "spam pattern matched");
        return Verdict.OK;
    }

    public void assertAllowed(String text) {
        Verdict v = screen(text);
        if (v.blocked) {
            log.info("Moderation blocked: {} — {}", v.label, v.detail);
            throw new ContentBlockedException(v);
        }
    }

    private synchronized void ensureCompiled() {
        if (blocklist != null) return;
        Set<Pattern> compiled = new HashSet<>();
        for (String raw : blocklistCsv.split(",")) {
            String word = raw.trim().toLowerCase();
            if (word.isEmpty()) continue;
            compiled.add(Pattern.compile("\\b" + Pattern.quote(word) + "\\b"));
        }
        blocklist = compiled;
    }

    // ============================================================
    //  Value objects
    // ============================================================

    public static class Verdict {
        public static final Verdict OK = new Verdict(false, "OK", null);
        public final boolean blocked;
        public final String label;
        public final String detail;
        public Verdict(boolean blocked, String label, String detail) {
            this.blocked = blocked; this.label = label; this.detail = detail;
        }
    }

    public static class ContentBlockedException extends RuntimeException {
        public final Verdict verdict;
        public ContentBlockedException(Verdict v) {
            super("Content blocked: " + v.label);
            this.verdict = v;
        }
    }
}
