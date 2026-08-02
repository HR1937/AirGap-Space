import com.airgap.service.GeminiApiService;

public class TestPhase2Generator {
    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("TESTING PHASE 2 GENERATION (Redis, Docker, Online Internship)");
        System.out.println("=================================================\n");

        GeminiApiService apiService = new GeminiApiService();

        String[] topics = {"Redis", "Docker", "Online Internship"};

        for (String topic : topics) {
            System.out.println("-------------------------------------------------");
            System.out.println("TOPIC: " + topic);
            System.out.println("-------------------------------------------------");

            GeminiApiService.EnrichedTopicResult result = apiService.generateEnrichedTopic(topic, "Core intuition first");

            System.out.println("\n[1. GENERATED SUMMARY (" + result.estimatedReadingTime + " min read)]:");
            System.out.println(result.summaryContent);

            System.out.println("\n[2. STORED KNOWLEDGE PACK JSON]:");
            System.out.println(result.knowledgePackJson);

            System.out.println("\n[3. STORED RELATED CONCEPTS JSON]:");
            System.out.println(result.relatedConceptsJson);

            System.out.println("\n[4. STORED EXPLORATION QUESTIONS JSON]:");
            System.out.println(result.curiosityPathsJson);
            System.out.println("\n");
        }
    }
}
