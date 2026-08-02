<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">
    <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/assets/images/AirGap-Space-Logo.svg">
    <title>AirGap Study — Create Account</title>
    <script src="${pageContext.request.contextPath}/assets/js/theme.js"></script>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/style.css">
</head>
<body>
    <div class="auth-wrapper">
        <div class="auth-card">
            <div style="text-align: center; margin-bottom: 28px;">
                <img class="auth-logo" src="${pageContext.request.contextPath}/assets/images/AirGap-Space-Logo.svg" alt="AirGap Study logo">
                <h1 style="font-size: 24px; font-weight: 700; margin-bottom: 6px; color: var(--text-main);">Create Account</h1>
                <p style="font-size: 14px; color: var(--text-muted); line-height: 1.5;">
                    Start your personal concept inbox
                </p>
            </div>

            <c:if test="${not empty error}">
                <div class="alert-box alert-danger">
                    ${error}
                </div>
            </c:if>

            <form action="${pageContext.request.contextPath}/register" method="post">
                <div style="margin-bottom: 16px;">
                    <label style="display: block; font-size: 13px; font-weight: 600; margin-bottom: 6px; color: var(--text-muted);">
                        Username
                    </label>
                    <input type="text" name="username" class="form-control" placeholder="Choose a username" required autofocus>
                </div>

                <div style="margin-bottom: 24px;">
                    <label style="display: block; font-size: 13px; font-weight: 600; margin-bottom: 6px; color: var(--text-muted);">
                        Password
                    </label>
                    <input type="password" name="password" class="form-control" placeholder="Create a strong password" required>
                </div>

                <button type="submit" class="btn btn-primary" style="width: 100%; padding: 12px; font-size: 15px;">
                    Create Account
                </button>
            </form>

            <div style="text-align: center; margin-top: 24px; font-size: 13px; color: var(--text-muted);">
                Already have an account?
                <a href="${pageContext.request.contextPath}/login" style="font-weight: 600; color: var(--accent);">Sign in</a>
            </div>
        </div>
    </div>
</body>
</html>
