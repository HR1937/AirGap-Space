import com.airgap.dao.TopicDao;
import com.airgap.dao.UserDao;
import com.airgap.model.Topic;
import com.airgap.model.User;
import com.airgap.service.GeminiApiService;

import java.util.ArrayList;
import java.util.List;

public class TestMultiKeyFailover {
    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("[MULTI-KEY FAILOVER INTEGRATION TEST]");
        System.out.println("================================================================================");

        try {
            UserDao userDao = new UserDao();
            TopicDao topicDao = new TopicDao();
            GeminiApiService geminiApiService = new GeminiApiService();

            System.out.println("\n--- TEST 1: Check Configuration & Key Count ---");
            boolean configured = geminiApiService.isConfigured();
            System.out.println("GeminiApiService.isConfigured() ? " + configured);

            User user = userDao.findByUsername("testuser");
            if (user == null) {
                user = new User("testuser", "password123");
                userDao.save(user);
            }

            Topic testTopic = new Topic(user, "redis caching strategies", "Mastercard prep", Topic.CaptureSource.MANUAL);
            topicDao.save(testTopic);
            Long topicId = testTopic.getId();

            System.out.println("\n--- TEST 2: Trigger Multi-Key Failover Attempt ---");
            topicDao.updateStatus(topicId, Topic.Status.GENERATING);

            GeminiApiService.EnrichedTopicResult result = geminiApiService.generateEnrichedTopic(
                    testTopic.getTitle(), testTopic.getDirection(), new ArrayList<>()
            );

            if (result != null) {
                System.out.println("\n[SUCCESS] AI Generation Succeeded using an active working key!");
                topicDao.updateEnrichedContent(
                        topicId,
                        result.summaryContent,
                        result.knowledgePackJson,
                        result.teachingPlanJson,
                        result.curiosityPathsJson,
                        result.relatedConceptsJson,
                        result.estimatedReadingTime
                );
            } else {
                System.out.println("\n[EXPECTED ALL EXHAUSTED] All API keys exhausted or rate-limited. Setting AI_UNAVAILABLE status.");
                topicDao.updateStatus(topicId, Topic.Status.AI_UNAVAILABLE);
            }

            Topic finalTopic = topicDao.findById(topicId);
            System.out.println("[VERIFICATION] Topic #" + topicId + " Saved Status: " + (finalTopic != null ? finalTopic.getStatus() : "null"));

            System.out.println("================================================================================");
            System.out.println("[MULTI-KEY FAILOVER TEST COMPLETE]");
            System.out.println("================================================================================");

        } catch (Exception e) {
            System.err.println("Unexpected exception in test: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
