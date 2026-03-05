package com.distributedsearch.node;

import java.util.*;

public class InvertedIndex {

    private Map<String, Set<Integer>> index = new HashMap<>();

    public void addDocument(Document doc) {

        String text = doc.getTitle() + " " + doc.getContent();

        String[] words = text
                .toLowerCase()
                .replaceAll("[^a-z0-9 ]", "")
                .split("\\s+");

        for (String word : words) {

            index.computeIfAbsent(word, k -> new HashSet<>())
                    .add(doc.getId());
        }
    }

    public List<Integer> search(String term) {

        String cleanedTerm = term.toLowerCase().replaceAll("[^a-z0-9 ]", "").trim();
        if (cleanedTerm.isEmpty()) {
            return new ArrayList<>();
        }

        String[] words = cleanedTerm.split("\\s+");
        Set<Integer> result = null;

        for (String word : words) {
            Set<Integer> docIds = index.get(word);
            if (docIds == null) {
                return new ArrayList<>();
            }
            if (result == null) {
                result = new HashSet<>(docIds);
            } else {
                result.retainAll(docIds);
            }
        }

        return result == null ? new ArrayList<>() : new ArrayList<>(result);
    }
}