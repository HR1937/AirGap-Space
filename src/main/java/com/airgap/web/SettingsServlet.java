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

@WebServlet("/settings")
public class SettingsServlet extends HttpServlet {

    private final UserDao userDao = new UserDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        User user = (User) session.getAttribute("user");

        Map<String, Object> settings = new HashMap<>();
        settings.put("username", user.getUsername());
        settings.put("email", user.getEmail());
        settings.put("defaultDirection", user.getDefaultDirection());

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(JsonUtil.toJson(settings));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        User user = (User) session.getAttribute("user");

        String defaultDirection = request.getParameter("defaultDirection");

        if (defaultDirection != null && !defaultDirection.isBlank()) {
            userDao.updateDefaultDirection(user.getId(), defaultDirection.trim());
            user.setDefaultDirection(defaultDirection.trim());
            session.setAttribute("user", user);
        }

        response.setContentType("application/json");
        response.getWriter().write("{\"success\": true, \"message\": \"Settings updated successfully.\"}");
    }
}
