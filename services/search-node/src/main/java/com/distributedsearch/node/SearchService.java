
package com.distributedsearch.node;
import java.util.*;

public class SearchService {

    private Map<Integer, Document> documents = new HashMap<>();
    private InvertedIndex index = new InvertedIndex();

    public void addDocument(Document doc) {

        documents.put(doc.getId(), doc);
        index.addDocument(doc);
    }

    public List<Document> search(String term) {

        List<Integer> ids = index.search(term);
        List<Document> results = new ArrayList<>();

        for(Integer id : ids) {
            results.add(documents.get(id));
        }

        return results;
    }
}