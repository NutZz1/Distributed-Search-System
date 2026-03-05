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

        Set<Integer> result = index.get(term.toLowerCase());

        if (result == null) {
            return new ArrayList<>();
        }

        return new ArrayList<>(result);
    }
}