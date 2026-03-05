public class QueryService {

    public void search(String term) {

        System.out.println("Routing query: " + term);

        // later:
        // ask Helix which nodes are leaders
        // send query to them
    }
}