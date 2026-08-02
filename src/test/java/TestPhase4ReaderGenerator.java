import com.airgap.service.GeminiApiService;
import java.util.ArrayList;
import java.util.List;

public class TestPhase4ReaderGenerator {
    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("TESTING PHASE 4 READER & QUICK CHECK (Redis, Docker, Online Internship)");
        System.out.println("=================================================\n");

        GeminiApiService apiService = new GeminiApiService();

        String[] topics = {"Redis", "Docker", "Online Internship"};
        List<String> existingUserTopics = new ArrayList<>();
        existingUserTopics.add("Docker");

        for (String topic : topics) {
            System.out.println("-------------------------------------------------");
            System.out.println("TOPIC: " + topic);
            System.out.println("-------------------------------------------------");

            GeminiApiService.EnrichedTopicResult result = apiService.generateEnrichedTopic(topic, "Intuition first", existingUserTopics);

            System.out.println("\n[1. PROGRESSIVE SUMMARY & CONTEXT BRIDGE (" + result.estimatedReadingTime + " min read)]:");
            System.out.println(result.summaryContent);

            System.out.println("\n[2. STORED ACTIVE RECALL QUICK CHECK (in teachingPlanJson)]:");
            System.out.println(result.teachingPlanJson);

            System.out.println("\n[3. STORED EXPLORATION QUESTIONS]:");
            System.out.println(result.curiosityPathsJson);

            System.out.println("\n[4. STORED REAL RELATED CONCEPTS]:");
            System.out.println(result.relatedConceptsJson);

            System.out.println("\n[5. STORED KNOWLEDGE PACK FOR OFFLINE NANO]:");
            System.out.println(result.knowledgePackJson);
            System.out.println("\n");
        }
    }
}
