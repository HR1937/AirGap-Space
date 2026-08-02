import com.airgap.dao.TopicDao;
import com.airgap.dao.UserDao;
import com.airgap.model.Topic;
import com.airgap.model.User;
import com.airgap.service.GeminiApiService;

import java.util.ArrayList;
import java.util.List;

public class TestQueueTracingPipeline {
    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("[STANDALONE BACKEND QUEUE TRACER]");
        System.out.println("================================================================================");

        try {
            UserDao userDao = new UserDao();
            TopicDao topicDao = new TopicDao();
            GeminiApiService geminiApiService = new GeminiApiService();

            User user = userDao.findByUsername("testuser");
            if (user == null) {
                user = new User("testuser", "password123");
                userDao.save(user);
            }

            // Create a queued topic "kafka in modern tech"
            Topic testTopic = new Topic(user, "kafka in modern tech", "Mastercard prep", Topic.CaptureSource.MANUAL);
            topicDao.save(testTopic);
            Long topicId = testTopic.getId();

            System.out.println("\n--- STEP 1: Entry & Queue Query ---");
            List<Topic> queuedTopics = topicDao.findQueuedTopics(user.getId());
            System.out.println("[STEP 1] Found " + queuedTopics.size() + " queued topics for User ID: " + user.getId());

            System.out.println("\n--- STEP 2: Inspect Queued Topic ---");
            for (Topic t : queuedTopics) {
                System.out.println("[STEP 2] Topic ID: " + t.getId() + " | Title: '" + t.getTitle() + "' | Status: " + t.getStatus());
            }

            System.out.println("\n--- STEP 6a: Change status to GENERATING ---");
            topicDao.updateStatus(topicId, Topic.Status.GENERATING);
            Topic afterGen = topicDao.findById(topicId);
            System.out.println("[STEP 6a] Status is now: " + (afterGen != null ? afterGen.getStatus() : "null"));

            System.out.println("\n--- STEP 3, 4, 5: Gemini API Call & JSON Parsing ---");
            GeminiApiService.EnrichedTopicResult result = geminiApiService.generateEnrichedTopic(
                    testTopic.getTitle(), testTopic.getDirection(), new ArrayList<>()
            );

            System.out.println("\n--- STEP 6b & 7: DB Update & Commit ---");
            if (result != null) {
                System.out.println("[STEP 6b] AI Generation Succeeded -> Updating DB row #" + topicId + " -> READY_OFFLINE...");
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
                System.out.println("[STEP 6b] AI Generation Failed / Unfulfilled -> Updating DB row #" + topicId + " -> AI_UNAVAILABLE...");
                topicDao.updateStatus(topicId, Topic.Status.AI_UNAVAILABLE);
            }

            Topic finalTopic = topicDao.findById(topicId);
            System.out.println("[STEP 7] After Commit -> ID: " + (finalTopic != null ? finalTopic.getId() : "null") 
                    + " | Saved Status: " + (finalTopic != null ? finalTopic.getStatus() : "null"));

            System.out.println("================================================================================");
            System.out.println("[QUEUE TRACE COMPLETE SUCCESS]");
            System.out.println("================================================================================");

        } catch (Exception e) {
            System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            System.err.println("[QUEUE TRACE EXCEPTION CAUGHT]");
            System.err.println("Exception Type: " + e.getClass().getName());
            System.err.println("Message: " + e.getMessage());
            System.err.println("File: " + (e.getStackTrace().length > 0 ? e.getStackTrace()[0].getFileName() : "Unknown"));
            System.err.println("Line: " + (e.getStackTrace().length > 0 ? e.getStackTrace()[0].getLineNumber() : "Unknown"));
            e.printStackTrace();
            System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        }
    }
}
