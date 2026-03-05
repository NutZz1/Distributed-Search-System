public class TestSearch {

    public static void main(String[] args) {

        SearchService service = new SearchService();

        Document d1 = new Document(
            1,
            "https://helix.apache.org",
            "Apache Helix",
            "Helix manages distributed clusters"
        );

        Document d2 = new Document(
            2,
            "https://zookeeper.apache.org",
            "Apache ZooKeeper",
            "ZooKeeper coordinates distributed systems"
        );

        service.addDocument(d1);
        service.addDocument(d2);

        System.out.println(service.search("helix"));
    }
}