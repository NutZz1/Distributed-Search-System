package com.distributedsearch.node;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe inverted index for full-text search.
 */
public class InvertedIndex {

    // Thread-safe index: word -> set of document IDs
    private final Map<String, Set<Integer>> index = new ConcurrentHashMap<>();

    /**
     * Add a document to the index.
     * Thread-safe for concurrent indexing.
     */
    public synchronized void addDocument(Document doc) {

        String text = doc.getTitle() + " " + doc.getContent();

        String[] words = text
                .toLowerCase()
                .replaceAll("[^a-z0-9 ]", "")
                .split("\\s+");

        for (String word : words) {
            if (word.isEmpty()) continue;
            
            // ConcurrentHashMap handles concurrent access to the map itself,
            // but we need synchronization when modifying the Set
            index.computeIfAbsent(word, k -> ConcurrentHashMap.newKeySet())
                    .add(doc.getId());
        }
    }

    /**
     * Search for documents containing all the search terms (AND query).
     * Thread-safe for concurrent searches.
     */
    public List<Integer> search(String term) {

        String cleanedTerm = term.toLowerCase().replaceAll("[^a-z0-9 ]", "").trim();
        if (cleanedTerm.isEmpty()) {
            return new ArrayList<>();
        }

        String[] words = cleanedTerm.split("\\s+");
        Set<Integer> result = null;

        for (String word : words) {
            if (word.isEmpty()) continue;
            
            Set<Integer> docIds = index.get(word);
            if (docIds == null) {
                // Word not found in index - no results
                return new ArrayList<>();
            }
            if (result == null) {
                // First word - copy the set
                result = new HashSet<>(docIds);
            } else {
                // Subsequent words - intersect (AND operation)
                result.retainAll(docIds);
            }
            
            // Early exit if no documents match
            if (result.isEmpty()) {
                return new ArrayList<>();
            }
        }

        return result == null ? new ArrayList<>() : new ArrayList<>(result);
    }
}