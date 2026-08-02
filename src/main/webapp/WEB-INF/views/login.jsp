<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">
    <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/assets/images/AirGap-Space-Logo.svg">
    <title>AirGap Study — Sign In</title>
    <meta name="description" content="AirGap Study: Capture concepts instantly, learn them offline with AI-enriched knowledge packs.">
    <script src="${pageContext.request.contextPath}/assets/js/theme.js"></script>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/style.css">
</head>
<body>
    <div class="auth-wrapper">
        <div class="auth-card">
            <div style="text-align: center; margin-bottom: 28px;">
                <img class="auth-logo" src="${pageContext.request.contextPath}/assets/images/AirGap-Space-Logo.svg" alt="AirGap Study logo">
                <h1 style="font-size: 24px; font-weight: 700; margin-bottom: 6px; color: var(--text-main);">AirGap Study</h1>
                <p style="font-size: 14px; color: var(--text-muted); line-height: 1.5;">
                    Capture concepts now. Learn them offline later.
                </p>
            </div>

            <c:if test="${not empty error}">
                <div class="alert-box alert-danger">
                    ${error}
                </div>
            </c:if>
            <c:if test="${param.msg eq 'logged_out'}">
                <div class="alert-box alert-success">
                    Signed out successfully.
                </div>
            </c:if>

            <form action="${pageContext.request.contextPath}/login" method="post">
                <div style="margin-bottom: 16px;">
                    <label style="display: block; font-size: 13px; font-weight: 600; margin-bottom: 6px; color: var(--text-muted);">
                        Username
                    </label>
                    <input type="text" name="username" class="form-control" placeholder="Enter username" required autofocus>
                </div>

                <div style="margin-bottom: 24px;">
                    <label style="display: block; font-size: 13px; font-weight: 600; margin-bottom: 6px; color: var(--text-muted);">
                        Password
                    </label>
                    <input type="password" name="password" class="form-control" placeholder="Enter password" required>
                </div>

                <button type="submit" class="btn btn-primary" style="width: 100%; padding: 12px; font-size: 15px;">
                    Sign In
                </button>
            </form>

            <div style="text-align: center; margin-top: 24px; font-size: 13px; color: var(--text-muted);">
                New here?
                <a href="${pageContext.request.contextPath}/register" style="font-weight: 600; color: var(--accent);">Create account</a>
            </div>
        </div>
    </div>
</body>
</html>
