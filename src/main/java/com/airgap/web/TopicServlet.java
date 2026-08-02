package com.airgap.web;

import com.airgap.dao.TopicDao;
import com.airgap.model.Topic;
import com.airgap.model.User;
import com.airgap.service.GeminiApiService;
import com.airgap.util.JsonUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/topic")
public class TopicServlet extends HttpServlet {

    private final TopicDao topicDao = new TopicDao();
    private final GeminiApiService geminiApiService = new GeminiApiService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        User user = (User) session.getAttribute("user");

        String action = request.getParameter("action");
        String idParam = request.getParameter("id");

        if ("delete".equals(action) && idParam != null) {
            try {
                Long topicId = Long.parseLong(idParam);
                System.out.println("[DELETE] Topic ID: " + topicId);
                System.out.println("[DELETE] Servlet entered");
                int affectedRows = topicDao.delete(topicId, user.getId());
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"success\": true, \"deletedId\": " + topicId + ", \"affectedRows\": " + affectedRows + "}");
                System.out.println("[DELETE] Finished server delete for topic ID: " + topicId);
                return;
            } catch (Exception e) {
                System.err.println("[DELETE] Servlet failure: " + e.getMessage());
                e.printStackTrace();
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"error\": \"" + e.getMessage() + "\"}");
                return;
            }
        }

        if ("pin".equals(action) && idParam != null) {
            try {
                Long topicId = Long.parseLong(idParam);
                String requestedPinState = request.getParameter("isPinned");
                Boolean desiredPinState = null;
                if (requestedPinState != null) {
                    if (!"true".equalsIgnoreCase(requestedPinState) && !"false".equalsIgnoreCase(requestedPinState)) {
                        throw new IllegalArgumentException("isPinned must be true or false.");
                    }
                    desiredPinState = Boolean.parseBoolean(requestedPinState);
                }
                System.out.println("[PIN] Topic ID: " + topicId);
                System.out.println("[PIN] Servlet entered; requested state: " + desiredPinState);
                boolean isPinned = topicDao.togglePin(topicId, user.getId(), desiredPinState);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"success\": true, \"isPinned\": " + isPinned + "}");
                System.out.println("[PIN] Finished; persisted state: " + isPinned);
                return;
            } catch (Exception e) {
                System.err.println("[PIN] Servlet failure. Exact reason: " + e.getMessage());
                e.printStackTrace();
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"error\": \"" + e.getMessage() + "\"}");
                return;
            }
        }

        if (idParam == null || idParam.isBlank()) {
            response.sendRedirect(request.getContextPath() + "/dashboard");
            return;
        }

        try {
            Long topicId = Long.parseLong(idParam);
            Topic topic = topicDao.findById(topicId);

            if (topic == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Topic not found");
                return;
            }

            String format = request.getParameter("format");
            if ("json".equalsIgnoreCase(format)) {
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(JsonUtil.toJson(buildTopicMap(topic)));
                return;
            }

            request.setAttribute("topic", topic);
            request.getRequestDispatcher("/WEB-INF/views/topic-view.jsp").forward(request, response);

        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid topic ID");
        } catch (Exception e) {
            System.err.println("[TopicServlet.doGet ERROR]: " + e.getMessage());
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error loading topic: " + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        User user = (User) session.getAttribute("user");

        String action = request.getParameter("action");

        // Action: Persist pin state
        if ("pin".equals(action)) {
            try {
                String idParam = request.getParameter("id");
                Long topicId = Long.parseLong(idParam);
                String requestedPinState = request.getParameter("isPinned");
                Boolean desiredPinState = null;
                if (requestedPinState != null) {
                    if (!"true".equalsIgnoreCase(requestedPinState) && !"false".equalsIgnoreCase(requestedPinState)) {
                        throw new IllegalArgumentException("isPinned must be true or false.");
                    }
                    desiredPinState = Boolean.parseBoolean(requestedPinState);
                }
                System.out.println("[PIN] Topic ID: " + topicId);
                System.out.println("[PIN] Servlet entered; requested state: " + desiredPinState);
                boolean isPinned = topicDao.togglePin(topicId, user.getId(), desiredPinState);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"success\": true, \"isPinned\": " + isPinned + "}");
                System.out.println("[PIN] Finished; persisted state: " + isPinned);
                return;
            } catch (Exception e) {
                System.err.println("[PIN] Servlet failure. Exact reason: " + e.getMessage());
                e.printStackTrace();
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"error\": \"" + e.getMessage() + "\"}");
                return;
            }
        }

        // Action: Retry Failed Topic Generation
        if ("retry".equals(action)) {
            String idParam = request.getParameter("id");
            if (idParam != null) {
                try {
                    Long topicId = Long.parseLong(idParam);
                    topicDao.updateStatus(topicId, Topic.Status.CAPTURED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"success\": true, \"id\": " + topicId + ", \"status\": \"CAPTURED\"}");
                    return;
                } catch (Exception e) {
                    System.err.println("[TopicServlet.doPost RETRY ERROR]: " + e.getMessage());
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    response.getWriter().write("{\"error\": \"" + e.getMessage() + "\"}");
                    return;
                }
            }
        }

        // Action: Background Queue Worker Endpoint (Never throws 500)
        if ("process_queue".equals(action)) {
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");

            List<Map<String, Object>> processedList = new ArrayList<>();

            try {
                List<Topic> queuedTopics = topicDao.findQueuedTopics(user.getId());
                List<String> recentTitles = topicDao.findRecentTopicTitlesByUserId(user.getId(), 10);

                System.out.println("================================================================================");
                System.out.println("[QUEUE TRACE STEP 1] Entry into TopicServlet.process_queue.");
                System.out.println("[QUEUE TRACE STEP 1] User ID: " + user.getId() + " | Queued topics found: " + queuedTopics.size());
                System.out.println("================================================================================");

                for (int i = 0; i < queuedTopics.size(); i++) {
                    Topic queuedTopic = queuedTopics.get(i);
                    System.out.println("[QUEUE TRACE STEP 2] Topic #" + (i + 1) + " -> ID: " + queuedTopic.getId() 
                            + " | Title: '" + queuedTopic.getTitle() + "' | Current Status: " + queuedTopic.getStatus());

                    try {
                        System.out.println("[QUEUE TRACE STEP 6a] Changing status to GENERATING...");
                        topicDao.updateStatus(queuedTopic.getId(), Topic.Status.GENERATING);

                        List<String> otherTitles = new ArrayList<>(recentTitles);
                        otherTitles.remove(queuedTopic.getTitle());

                        System.out.println("[QUEUE TRACE STEP 3] Triggering GeminiApiService for: '" + queuedTopic.getTitle() + "'");
                        GeminiApiService.EnrichedTopicResult result = geminiApiService.generateEnrichedTopic(
                                queuedTopic.getTitle(), queuedTopic.getDirection(), otherTitles);

                        if (result != null) {
                            System.out.println("[QUEUE TRACE STEP 6b] Updating database row #" + queuedTopic.getId() + " status -> READY_OFFLINE...");
                            topicDao.updateEnrichedContent(
                                    queuedTopic.getId(),
                                    result.summaryContent,
                                    result.knowledgePackJson,
                                    result.teachingPlanJson,
                                    result.curiosityPathsJson,
                                    result.relatedConceptsJson,
                                    result.estimatedReadingTime
                            );

                            Topic updated = topicDao.findById(queuedTopic.getId());
                            if (updated != null) {
                                System.out.println("[QUEUE TRACE STEP 7] After Commit -> ID: " + updated.getId() + " | Saved Status: " + updated.getStatus());
                                processedList.add(buildTopicMap(updated));
                            }
                        } else {
                            System.err.println("[QUEUE TRACE FAILURE] AI Service unfulfilled for topic #" + queuedTopic.getId() + " ('" + queuedTopic.getTitle() + "') -> Setting status to AI_UNAVAILABLE");
                            topicDao.updateStatus(queuedTopic.getId(), Topic.Status.AI_UNAVAILABLE);
                            Topic updated = topicDao.findById(queuedTopic.getId());
                            if (updated != null) {
                                processedList.add(buildTopicMap(updated));
                            }
                        }

                    } catch (Exception e) {
                        System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                        System.err.println("[QUEUE TRACE FAILURE] Exception processing queued topic #" + queuedTopic.getId() + " ('" + queuedTopic.getTitle() + "')");
                        System.err.println("Root Cause: " + e.getClass().getName() + ": " + e.getMessage());
                        e.printStackTrace();
                        System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                        
                        topicDao.updateStatus(queuedTopic.getId(), Topic.Status.AI_UNAVAILABLE);
                        Topic updated = topicDao.findById(queuedTopic.getId());
                        if (updated != null) {
                            processedList.add(buildTopicMap(updated));
                        }
                    }
                }
            } catch (Exception outerErr) {
                System.err.println("[QUEUE TRACE FATAL ERROR]: " + outerErr.getMessage());
                outerErr.printStackTrace();
            }

            response.getWriter().write(JsonUtil.toJson(processedList));
            return;
        }

        // Instant Non-Blocking Capture (<10ms)
        String title = request.getParameter("title");
        String directionOverride = request.getParameter("direction");
        String sourceParam = request.getParameter("source");

        if (title == null || title.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Topic title is required.\"}");
            return;
        }

        Topic.CaptureSource source = Topic.CaptureSource.MANUAL;
        if ("RELATED_CONCEPT".equalsIgnoreCase(sourceParam)) {
            source = Topic.CaptureSource.RELATED_CONCEPT;
        }

        String effectiveDirection = (directionOverride != null && !directionOverride.isBlank())
                ? directionOverride.trim()
                : user.getDefaultDirection();

        try {
            Topic newTopic = new Topic(user, title.trim(), effectiveDirection, source);
            Topic savedTopic = topicDao.save(newTopic);

            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(JsonUtil.toJson(buildTopicMap(savedTopic)));

        } catch (Exception e) {
            System.err.println("[TopicServlet.doPost CAPTURE ERROR]: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Failed to capture topic: " + e.getMessage() + "\"}");
        }
    }

    private Map<String, Object> buildTopicMap(Topic topic) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", topic.getId());
        map.put("userId", topic.getUser().getId());
        map.put("title", topic.getTitle());
        map.put("direction", topic.getDirection());
        map.put("status", topic.getStatus().name());
        map.put("captureSource", topic.getCaptureSource().name());
        map.put("summaryContent", topic.getSummaryContent());
        map.put("knowledgePackJson", topic.getKnowledgePackJson());
        map.put("teachingPlanJson", topic.getTeachingPlanJson());
        map.put("curiosityPathsJson", topic.getCuriosityPathsJson());
        map.put("relatedConceptsJson", topic.getRelatedConceptsJson());
        map.put("estimatedReadingTime", topic.getEstimatedReadingTime());
        map.put("timesRead", topic.getTimesRead());
        map.put("questionsAsked", topic.getQuestionsAsked());
        map.put("isPinned", topic.isPinned());
        map.put("createdAt", topic.getCreatedAt() != null ? topic.getCreatedAt().toString() : "");
        return map;
    }
}
