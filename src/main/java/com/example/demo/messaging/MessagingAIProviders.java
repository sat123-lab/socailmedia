package com.example.demo.messaging;

import java.util.List;
import java.util.Map;

/**
 * Pluggable AI hooks for the messaging ecosystem. No implementations ship
 * in this build — register concrete providers at startup via
 * {@link MessagingAIService}.
 */
public final class MessagingAIProviders {
    private MessagingAIProviders() {}

    public interface SmartReplyProvider {
        List<String> suggestReplies(long userId, long conversationId, int max);
    }

    public interface ChatSummaryProvider {
        String summarize(long userId, long conversationId, int maxMessages);
    }

    public interface MessageCategorizationProvider {
        String categorize(String plaintextPreview);
    }

    public interface SpamDetectionProvider {
        boolean isSpam(long senderId, String preview);
    }

    public interface TranslationProvider {
        String translate(String text, String targetLang);
    }

    public interface ModerationProvider {
        double riskScore(String preview);
    }

    public interface ConversationInsightsProvider {
        Map<String, Object> insights(long userId, long conversationId);
    }
}
