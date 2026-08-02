package com.airgap.web;

import com.airgap.dao.UserDao;
import com.airgap.model.User;
import com.airgap.util.JsonUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@WebServlet("/preference")
public class PreferenceServlet extends HttpServlet {

    private final UserDao userDao = new UserDao();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        User user = (User) session.getAttribute("user");

        String defaultDirection = request.getParameter("defaultDirection");

        if (defaultDirection == null || defaultDirection.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Default learning direction cannot be empty.\"}");
            return;
        }

        try {
            userDao.updateDefaultDirection(user.getId(), defaultDirection.trim());
            user.setDefaultDirection(defaultDirection.trim());
            session.setAttribute("user", user);

            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("defaultDirection", user.getDefaultDirection());
            result.put("message", "Learning direction preference updated successfully.");

            response.getWriter().write(JsonUtil.toJson(result));

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Failed to update preference: " + e.getMessage() + "\"}");
        }
    }
}
