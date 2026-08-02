package com.airgap.web;

import com.airgap.dao.TopicDao;
import com.airgap.model.Topic;
import com.airgap.model.User;
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

@WebServlet("/sync")
public class SyncServlet extends HttpServlet {

    private final TopicDao topicDao = new TopicDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        User user = (User) session.getAttribute("user");

        List<Topic> topics = topicDao.findByUserId(user.getId());
        List<Map<String, Object>> topicDTOs = new ArrayList<>();

        for (Topic topic : topics) {
            Map<String, Object> dto = new HashMap<>();
            dto.put("id", topic.getId());
            dto.put("userId", user.getId());
            dto.put("title", topic.getTitle());
            dto.put("direction", topic.getDirection());
            dto.put("status", topic.getStatus().name());
            dto.put("captureSource", topic.getCaptureSource().name());
            dto.put("summaryContent", topic.getSummaryContent());
            dto.put("knowledgePackJson", topic.getKnowledgePackJson());
            dto.put("teachingPlanJson", topic.getTeachingPlanJson());
            dto.put("curiosityPathsJson", topic.getCuriosityPathsJson());
            dto.put("relatedConceptsJson", topic.getRelatedConceptsJson());
            dto.put("estimatedReadingTime", topic.getEstimatedReadingTime());
            dto.put("timesRead", topic.getTimesRead());
            dto.put("questionsAsked", topic.getQuestionsAsked());
            dto.put("isPinned", topic.isPinned());
            dto.put("lastOpenedAt", topic.getLastOpenedAt() != null ? topic.getLastOpenedAt().toString() : "");
            dto.put("createdAt", topic.getCreatedAt() != null ? topic.getCreatedAt().toString() : "");
            topicDTOs.add(dto);
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(JsonUtil.toJson(topicDTOs));
    }
}
