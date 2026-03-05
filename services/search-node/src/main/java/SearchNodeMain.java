import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.apache.helix.HelixManager;
import org.apache.helix.HelixManagerFactory;
import org.apache.helix.InstanceType;

@SpringBootApplication
public class SearchNodeMain {

    public static void main(String[] args) throws Exception {

        String instanceName = "Pandora_box-PARTICIPANT";

        HelixManager manager = HelixManagerFactory.getZKHelixManager(
                "search-cluster",
                instanceName,
                InstanceType.PARTICIPANT,
                "localhost:2181"
        );

        manager.connect();

        System.out.println("Search node started: " + instanceName);

        // START SPRING BOOT SERVER
        SpringApplication.run(SearchNodeMain.class, args);
    }
}