package service;

import javafx.concurrent.Task;
import model.Message;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ChatSearchEngine {
    private final Map<String, Set<String>> invertedIndex = new HashMap<>();
    private final Map<String, Integer> messageOrder = new HashMap<>();

    public Task<Void> createBuildIndexTask(List<Message> history) {
        List<Message> snapshot = history == null ? List.of() : new ArrayList<>(history);
        return new Task<>() {
            @Override
            protected Void call() {
                buildIndex(snapshot);
                return null;
            }
        };
    }

    public synchronized void buildIndex(List<Message> history) {
        Map<String, Set<String>> nextIndex = new HashMap<>();
        Map<String, Integer> nextOrder = new HashMap<>();

        if (history != null) {
            int order = 0;
            for (Message message : history) {
                if (message == null || message.getId() == null || message.getId().isBlank()) {
                    continue;
                }
                nextOrder.put(message.getId(), order++);
                for (String token : tokenize(message.getContent())) {
                    nextIndex.computeIfAbsent(token, ignored -> new LinkedHashSet<>()).add(message.getId());
                }
            }
        }

        invertedIndex.clear();
        invertedIndex.putAll(nextIndex);
        messageOrder.clear();
        messageOrder.putAll(nextOrder);
    }

    public Task<List<String>> createSearchTask(String keyword) {
        String query = keyword == null ? "" : keyword;
        return new Task<>() {
            @Override
            protected List<String> call() {
                return search(query);
            }
        };
    }

    public synchronized List<String> search(String keyword) {
        List<String> tokens = tokenize(keyword);
        if (tokens.isEmpty()) {
            return List.of();
        }

        Set<String> intersection = null;
        for (String token : tokens) {
            Set<String> matches = invertedIndex.get(token);
            if (matches == null || matches.isEmpty()) {
                return List.of();
            }
            if (intersection == null) {
                intersection = new LinkedHashSet<>(matches);
            } else {
                intersection.retainAll(matches);
                if (intersection.isEmpty()) {
                    return List.of();
                }
            }
        }

        if (intersection.isEmpty()) {
            return List.of();
        }

        List<String> orderedIds = new ArrayList<>(intersection);
        orderedIds.sort(Comparator.comparingInt(id -> messageOrder.getOrDefault(id, Integer.MAX_VALUE)));
        return orderedIds;
    }

    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        StringBuilder latinBuffer = new StringBuilder();
        StringBuilder cjkBuffer = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char current = Character.toLowerCase(text.charAt(i));
            if (Character.isLetterOrDigit(current) && current < 128) {
                flushCjkBuffer(cjkBuffer, tokens);
                latinBuffer.append(current);
                continue;
            }
            flushLatinBuffer(latinBuffer, tokens);
            if (isCjk(current)) {
                cjkBuffer.append(current);
            } else {
                flushCjkBuffer(cjkBuffer, tokens);
            }
        }

        flushLatinBuffer(latinBuffer, tokens);
        flushCjkBuffer(cjkBuffer, tokens);
        return new ArrayList<>(tokens);
    }

    private static void flushLatinBuffer(StringBuilder buffer, Collection<String> tokens) {
        if (buffer.isEmpty()) {
            return;
        }
        String token = buffer.toString().toLowerCase(Locale.ROOT);
        if (!token.isBlank()) {
            tokens.add(token);
        }
        buffer.setLength(0);
    }

    private static void flushCjkBuffer(StringBuilder buffer, Collection<String> tokens) {
        if (buffer.isEmpty()) {
            return;
        }
        String chunk = buffer.toString();
        for (int i = 0; i < chunk.length(); i++) {
            tokens.add(String.valueOf(chunk.charAt(i)));
            if (i + 1 < chunk.length()) {
                tokens.add(chunk.substring(i, i + 2));
            }
        }
        buffer.setLength(0);
    }

    private static boolean isCjk(char value) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(value);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
                || block == Character.UnicodeBlock.GENERAL_PUNCTUATION;
    }
}
