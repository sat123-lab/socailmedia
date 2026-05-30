package com.example.demo.messaging;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class MessagingAIService {

    private final AtomicReference<MessagingAIProviders.SmartReplyProvider> smartReply =
            new AtomicReference<>();
    private final AtomicReference<MessagingAIProviders.ChatSummaryProvider> summary =
            new AtomicReference<>();
    private final AtomicReference<MessagingAIProviders.MessageCategorizationProvider> categorize =
            new AtomicReference<>();
    private final AtomicReference<MessagingAIProviders.SpamDetectionProvider> spam =
            new AtomicReference<>();
    private final AtomicReference<MessagingAIProviders.TranslationProvider> translate =
            new AtomicReference<>();
    private final AtomicReference<MessagingAIProviders.ModerationProvider> moderation =
            new AtomicReference<>();
    private final AtomicReference<MessagingAIProviders.ConversationInsightsProvider> insights =
            new AtomicReference<>();

    public void register(MessagingAIProviders.SmartReplyProvider p) { smartReply.set(p); }
    public void register(MessagingAIProviders.ChatSummaryProvider p) { summary.set(p); }
    public void register(MessagingAIProviders.MessageCategorizationProvider p) { categorize.set(p); }
    public void register(MessagingAIProviders.SpamDetectionProvider p) { spam.set(p); }
    public void register(MessagingAIProviders.TranslationProvider p) { translate.set(p); }
    public void register(MessagingAIProviders.ModerationProvider p) { moderation.set(p); }
    public void register(MessagingAIProviders.ConversationInsightsProvider p) { insights.set(p); }

    public Optional<MessagingAIProviders.SmartReplyProvider> smartReply() { return Optional.ofNullable(smartReply.get()); }
    public Optional<MessagingAIProviders.ChatSummaryProvider> summary() { return Optional.ofNullable(summary.get()); }
    public Optional<MessagingAIProviders.MessageCategorizationProvider> categorize() { return Optional.ofNullable(categorize.get()); }
    public Optional<MessagingAIProviders.SpamDetectionProvider> spam() { return Optional.ofNullable(spam.get()); }
    public Optional<MessagingAIProviders.TranslationProvider> translate() { return Optional.ofNullable(translate.get()); }
    public Optional<MessagingAIProviders.ModerationProvider> moderation() { return Optional.ofNullable(moderation.get()); }
    public Optional<MessagingAIProviders.ConversationInsightsProvider> insights() { return Optional.ofNullable(insights.get()); }
}
