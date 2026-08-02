import com.airgap.service.GeminiApiService;
import java.util.ArrayList;
import java.util.List;

public class TestPhase3BridgeGenerator {
    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("TESTING PHASE 3 CONTEXT BRIDGE (Docker -> Kubernetes)");
        System.out.println("=================================================\n");

        GeminiApiService apiService = new GeminiApiService();

        List<String> userTopics = new ArrayList<>();
        userTopics.add("Docker");

        System.out.println("User's Existing Learned Topics: " + userTopics);
        System.out.println("Capturing New Topic: Kubernetes\n");

        GeminiApiService.EnrichedTopicResult result = apiService.generateEnrichedTopic("Kubernetes", "Core intuition first", userTopics);

        System.out.println("[1. GENERATED SUMMARY WITH CONTEXT BRIDGE (" + result.estimatedReadingTime + " min read)]:");
        System.out.println(result.summaryContent);

        System.out.println("\n[2. STORED KNOWLEDGE PACK JSON]:");
        System.out.println(result.knowledgePackJson);

        System.out.println("\n[3. STORED RELATED CONCEPTS JSON]:");
        System.out.println(result.relatedConceptsJson);

        System.out.println("\n[4. STORED EXPLORATION QUESTIONS JSON]:");
        System.out.println(result.curiosityPathsJson);
    }
}
