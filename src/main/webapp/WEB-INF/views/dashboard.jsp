<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">
    <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/assets/images/AirGap-Space-Logo.svg">
    <title>AirGap Study</title>
    <script>window.CURRENT_USER_ID = parseInt("${sessionScope.user.id}", 10);</script>
    <script src="${pageContext.request.contextPath}/assets/js/theme.js"></script>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/style.css">
    <script src="https://unpkg.com/lucide@latest"></script>
</head>
<body>
    <div class="app-container">
        <!-- Navigation -->
        <header class="navbar">
            <div class="brand-container">
                <img class="brand-logo" src="${pageContext.request.contextPath}/assets/images/AirGap-Space-Logo.svg" alt="AirGap Study logo">
                <div>
                    <div class="brand-title">AirGap Study</div>
                    <div style="font-size: 12px; color: var(--text-dim);">For every "I'll learn this later." moment.</div>
                </div>
            </div>

            <div class="user-profile">
                <div id="network-status" class="status-badge online">
                    <span class="dot"></span>
                    <span>Online</span>
                </div>

                <div style="display: flex; align-items: center; gap: 6px;">
                    <span style="font-weight: 500; font-size: 13px; color: var(--text-muted);">${sessionScope.user.username}</span>
                    <button onclick="document.getElementById('help-modal').classList.add('active')" class="btn-icon" title="Help" aria-label="Help"><i data-lucide="circle-help" aria-hidden="true"></i></button>
                    <button onclick="document.getElementById('settings-modal').classList.add('active')" class="btn-icon" title="Settings" aria-label="Settings"><i data-lucide="settings" aria-hidden="true"></i></button>
                    <a href="${pageContext.request.contextPath}/login?action=logout" class="btn-icon" title="Sign Out" aria-label="Sign Out"><i data-lucide="log-out" aria-hidden="true"></i></a>
                </div>
            </div>
        </header>

        <!-- Quick Capture -->
        <div class="capture-card">
            <form id="quick-capture-form">
                <div style="display: flex; gap: 10px; align-items: center;">
                    <div style="flex: 1; min-width: 180px;">
                        <input type="text" id="topic-title" class="form-control"
                            placeholder="What's on your mind?" required autocomplete="off">
                    </div>
                    <div style="flex: 1; min-width: 180px;">
                        <input type="text" id="topic-direction" class="form-control"
                            placeholder="Optional learning direction..." autocomplete="off" aria-label="Optional learning direction">
                    </div>
                    <button type="submit" id="capture-btn" class="btn btn-primary" style="height: 44px; padding: 0 20px; white-space: nowrap;">
                        <i data-lucide="plus" aria-hidden="true"></i>
                        Capture
                    </button>
                </div>
            </form>
        </div>

        <!-- Filters & Search -->
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; flex-wrap: wrap; gap: 12px;">
            <div style="display: flex; gap: 6px; align-items: center; flex-wrap: wrap;">
                <span class="inbox-filter-pill active" data-filter="ALL">All Concepts</span>
                <span class="inbox-filter-pill" data-filter="READY_OFFLINE">Ready to Study</span>
                <span class="inbox-filter-pill" data-filter="PREPARING">Not Ready Yet</span>
                <span class="inbox-filter-pill" data-filter="NEEDS_ATTENTION">Generation Issues</span>
            </div>

            <div class="search-field" style="width: 240px; min-width: 180px;">
                <i data-lucide="search" aria-hidden="true"></i>
                <input type="text" id="topic-search-input" class="form-control"
                    placeholder="Search concepts..." style="font-size: 13px;">
            </div>
        </div>

        <!-- Concept Grid -->
        <div id="topic-grid-container" class="topic-grid">
            <c:choose>
                <c:when test="${not empty topics}">
                    <c:forEach var="topic" items="${topics}">
                        <div id="topic-card-${topic.id}" class="topic-card ${topic.pinned ? 'pinned' : ''}">
                            <div>
                                <div class="topic-card-header">
                                    <div class="topic-card-title">${topic.title}</div>
                                    <button onclick="togglePin(${topic.id})" class="btn-icon" title="${topic.pinned ? 'Unpin' : 'Pin'}" aria-label="${topic.pinned ? 'Unpin' : 'Pin'}">
                                        <i data-lucide="pin" aria-hidden="true"></i>
                                    </button>
                                </div>

                                <div style="display:flex; align-items:center; gap:6px; flex-wrap:wrap;">
                                    <c:choose>
                                        <c:when test="${topic.status == 'WAITING_FOR_NETWORK'}">
                                            <span class="badge badge-warning"><i data-lucide="cloud-off" aria-hidden="true"></i>Captured Offline</span>
                                        </c:when>
                                        <c:when test="${topic.status == 'CAPTURED'}">
                                            <span class="badge badge-warning"><i data-lucide="clock-3" aria-hidden="true"></i>Waiting to Start</span>
                                        </c:when>
                                        <c:when test="${topic.status == 'GENERATING'}">
                                            <span class="badge badge-info"><i data-lucide="clock-3" aria-hidden="true"></i>Preparing Guide...</span>
                                        </c:when>
                                        <c:when test="${topic.status == 'READY_OFFLINE'}">
                                            <span class="badge badge-success"><i data-lucide="check-circle" aria-hidden="true"></i>Ready Offline</span>
                                        </c:when>
                                        <c:otherwise>
                                            <button onclick="retryTopic(${topic.id})" class="badge badge-danger" style="cursor:pointer; border:none;">
                                                <i data-lucide="triangle-alert" aria-hidden="true"></i>
                                                Try Again
                                            </button>
                                        </c:otherwise>
                                    </c:choose>
                                </div>
                            </div>

                            <div class="topic-card-actions">
                                <a href="${pageContext.request.contextPath}/topic?id=${topic.id}" class="btn btn-secondary" style="flex:1; text-align:center; font-size:13px; padding: 8px 14px;">
                                    <c:choose>
                                        <c:when test="${topic.status == 'READY_OFFLINE'}">Study</c:when>
                                        <c:otherwise>View Status</c:otherwise>
                                    </c:choose>
                                </a>
                                <button onclick="deleteTopic(${topic.id})" class="btn-icon" style="color: var(--danger);" title="Delete" aria-label="Delete"><i data-lucide="trash-2" aria-hidden="true"></i></button>
                            </div>
                        </div>
                    </c:forEach>
                </c:when>
                <c:otherwise>
                    <div style="grid-column: 1 / -1; text-align: center; padding: 64px 24px; background: var(--bg-card); border-radius: var(--radius-lg); border: 1px dashed var(--bg-card-border);">
                        <div class="empty-state-icon" aria-hidden="true"><i data-lucide="inbox"></i></div>
                        <h3 style="font-size: 18px; margin-bottom: 8px; font-weight: 600; color: var(--text-main);">Every great idea starts with curiosity.</h3>
                        <p style="color: var(--text-muted); font-size: 14px; max-width: 400px; margin: 0 auto; line-height: 1.6;">
                            Capture the first concept that comes to mind.
                        </p>
                    </div>
                </c:otherwise>
            </c:choose>
        </div>

    </div>

    <!-- Onboarding Wizard -->
    <div id="onboarding-wizard" class="modal-overlay" style="display: none;" data-user-direction="${sessionScope.user.defaultDirection}">
        <div class="modal-card">
            <h2 style="font-size: 22px; font-weight: 700; margin-bottom: 4px;">Welcome to AirGap Study</h2>
            <p style="font-size: 14px; color: var(--text-muted); margin-bottom: 20px; line-height: 1.5;">
                How would you like your concepts explained? Choose a style or write your own.
            </p>

            <div class="onboarding-option selected" data-value="I want to understand why something came into existence, what problem it solved, build intuition first, then learn its formal definition, important concepts, practical uses, and the critical ideas I should know to think deeply about it.">
                <div class="onboarding-option-icon" style="background: rgba(79, 143, 247, 0.1);"><i data-lucide="lightbulb" aria-hidden="true"></i></div>
                <div>
                    <div style="font-weight: 600; font-size: 14px;">Intuition First</div>
                    <div style="font-size: 12px; color: var(--text-muted);">Why it exists, motivation, then formal definition</div>
                </div>
            </div>

            <div class="onboarding-option" data-value="College Syllabus — Exam-focused definitions, core theory, and academic rigor.">
                <div class="onboarding-option-icon" style="background: rgba(16, 185, 129, 0.1);"><i data-lucide="graduation-cap" aria-hidden="true"></i></div>
                <div>
                    <div style="font-weight: 600; font-size: 14px;">Academic</div>
                    <div style="font-size: 12px; color: var(--text-muted);">Exam-focused definitions and core theory</div>
                </div>
            </div>

            <div class="onboarding-option" data-value="Placement & Coding Interviews — Trade-offs, complexity analysis, system design patterns.">
                <div class="onboarding-option-icon" style="background: rgba(245, 158, 11, 0.1);"><i data-lucide="briefcase" aria-hidden="true"></i></div>
                <div>
                    <div style="font-weight: 600; font-size: 14px;">Interview Prep</div>
                    <div style="font-size: 12px; color: var(--text-muted);">Trade-offs, complexity, system design Q&A</div>
                </div>
            </div>

            <div class="onboarding-option" data-value="custom">
                <div class="onboarding-option-icon" style="background: rgba(168, 85, 247, 0.1);"><i data-lucide="pencil" aria-hidden="true"></i></div>
                <div>
                    <div style="font-weight: 600; font-size: 14px;">Custom</div>
                    <div style="font-size: 12px; color: var(--text-muted);">Write your own learning direction</div>
                </div>
            </div>

            <div id="custom-direction-input" style="display: none; margin-top: 10px;">
                <textarea id="custom-direction-text" class="form-control" rows="2" placeholder="Describe how you learn best..."></textarea>
            </div>

            <button id="save-onboarding-btn" class="btn btn-primary" style="width: 100%; margin-top: 16px; padding: 12px;">
                Continue
            </button>
        </div>
    </div>

    <!-- Settings Modal -->
    <div id="settings-modal" class="modal-overlay">
        <div class="modal-card">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h2 style="font-size: 18px; font-weight: 700;">Settings</h2>
                <button onclick="document.getElementById('settings-modal').classList.remove('active')" class="btn-icon" aria-label="Close Settings"><i data-lucide="x" aria-hidden="true"></i></button>
            </div>

            <div style="margin-bottom: 20px;">
                <label style="display: block; font-size: 13px; font-weight: 600; margin-bottom: 8px; color: var(--text-muted);">Theme</label>
                <div style="display: flex; gap: 6px;">
                    <button onclick="setTheme('system')" class="btn btn-secondary" style="flex:1; font-size: 13px;">System</button>
                    <button onclick="setTheme('dark')" class="btn btn-secondary" style="flex:1; font-size: 13px;">Dark</button>
                    <button onclick="setTheme('light')" class="btn btn-secondary" style="flex:1; font-size: 13px;">Light</button>
                </div>
            </div>

            <div style="margin-bottom: 20px;">
                <label style="display: block; font-size: 13px; font-weight: 600; margin-bottom: 8px; color: var(--text-muted);">Default Learning Direction</label>
                <textarea id="settings-direction" class="form-control" rows="3" style="font-size: 13px;">${sessionScope.user.defaultDirection}</textarea>
            </div>

            <div style="margin-bottom: 20px;">
                <label style="display: block; font-size: 13px; font-weight: 600; margin-bottom: 8px; color: var(--text-muted);">Storage</label>
                <div style="font-size: 12px; color: var(--text-dim); background: var(--bg-primary); padding: 12px 14px; border-radius: 8px; border: 1px solid var(--bg-card-border); line-height: 1.5;">
                    AirGapDB IndexedDB engine active. Concepts are cached locally for offline access.
                </div>
            </div>

            <button onclick="document.getElementById('settings-modal').classList.remove('active')" class="btn btn-primary" style="width: 100%;">
                Done
            </button>
        </div>
    </div>

    <!-- Help Center Modal -->
    <div id="help-modal" class="modal-overlay">
        <div class="modal-card" style="max-width: 560px;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h2 style="font-size: 18px; font-weight: 700;">How AirGap Study works</h2>
                <button onclick="document.getElementById('help-modal').classList.remove('active')" class="btn-icon" aria-label="Close Help"><i data-lucide="x" aria-hidden="true"></i></button>
            </div>

            <div style="display: flex; flex-direction: column; gap: 10px; font-size: 13px; color: var(--text-main); max-height: 420px; overflow-y: auto; line-height: 1.5;">
                <div style="background: var(--bg-primary); padding: 14px 16px; border-radius: var(--radius-md); border: 1px solid var(--bg-card-border);">
                    <div style="font-weight: 600; margin-bottom: 3px;">Capture</div>
                    <div style="color: var(--text-muted); font-size: 12px;">Type any concept, term, or idea into the capture bar. AirGap enriches it automatically using AI.</div>
                </div>
                <div style="background: var(--bg-primary); padding: 14px 16px; border-radius: var(--radius-md); border: 1px solid var(--bg-card-border);">
                    <div style="font-weight: 600; margin-bottom: 3px;">Direction</div>
                    <div style="color: var(--text-muted); font-size: 12px;">Settings provide your default direction. You can override it for an individual concept from the optional capture field.</div>
                </div>
                <div style="background: var(--bg-primary); padding: 14px 16px; border-radius: var(--radius-md); border: 1px solid var(--bg-card-border);">
                    <div style="font-weight: 600; margin-bottom: 3px;">Ready Offline</div>
                    <div style="color: var(--text-muted); font-size: 12px;">Once AI enrichment completes, the full knowledge pack is stored in your browser's IndexedDB for 100% offline access.</div>
                </div>
                <div style="background: var(--bg-primary); padding: 14px 16px; border-radius: var(--radius-md); border: 1px solid var(--bg-card-border);">
                    <div style="font-weight: 600; margin-bottom: 3px;">Captured Offline</div>
                    <div style="color: var(--text-muted); font-size: 12px;">The concept name is captured on this device without internet. When you reconnect, it is sent for AI enrichment automatically.</div>
                </div>
                <div style="background: var(--bg-primary); padding: 14px 16px; border-radius: var(--radius-md); border: 1px solid var(--bg-card-border);">
                    <div style="font-weight: 600; margin-bottom: 3px;">Offline Follow-ups</div>
                    <div style="color: var(--text-muted); font-size: 12px;">Chrome's built-in Gemini Nano allows you to ask follow-up questions even without internet.</div>
                </div>
                <div style="background: var(--bg-primary); padding: 14px 16px; border-radius: var(--radius-md); border: 1px solid var(--bg-card-border);">
                    <div style="font-weight: 600; margin-bottom: 3px;">Sync & Background Retry</div>
                    <div style="color: var(--text-muted); font-size: 12px;">The app automatically syncs with the server and retries failed generations in the background every 30 seconds.</div>
                </div>
                <div style="background: var(--bg-primary); padding: 14px 16px; border-radius: var(--radius-md); border: 1px solid var(--bg-card-border);">
                    <div style="font-weight: 600; margin-bottom: 3px;">Pinning</div>
                    <div style="color: var(--text-muted); font-size: 12px;">Pin important concepts to keep them at the top of your inbox. Pinned concepts always appear first.</div>
                </div>
                <div style="background: var(--bg-primary); padding: 14px 16px; border-radius: var(--radius-md); border: 1px solid var(--bg-card-border);">
                    <div style="font-weight: 600; margin-bottom: 3px;">Auto-Cleanup</div>
                    <div style="color: var(--text-muted); font-size: 12px;">Concepts expire after 30 days unless extended. A warning appears 3 days before expiry. You can extend or delete at any time.</div>
                </div>
                <div style="background: var(--bg-primary); padding: 14px 16px; border-radius: var(--radius-md); border: 1px solid var(--bg-card-border);">
                    <div style="font-weight: 600; margin-bottom: 3px;">Search</div>
                    <div style="color: var(--text-muted); font-size: 12px;">Fuzzy search finds concepts even with partial or misspelled queries. Results are ranked by relevance.</div>
                </div>
                <div class="about-airgap-panel">
                    <img class="about-airgap-logo" src="${pageContext.request.contextPath}/assets/images/AirGap-Space-Logo.svg" alt="AirGap Study logo">
                    <div>
                        <div style="font-weight: 600; margin-bottom: 5px;">About AirGap Study</div>
                        <div style="color: var(--text-muted); font-size: 12px; line-height: 1.6;">
                            The name AirGap reflects the space created when a device is separated from the internet: learning should still continue across that gap. The logo carries the creator's signature in the clock hands at 19:37. Its broken circular boundary reflects disconnected classrooms and dead internet zones, while the upward trajectory turns small unused moments into long-term growth. The book-shaped vessel carries knowledge forward, and the person steering it represents intentional, self-directed learning instead of passive waiting.
                        </div>
                    </div>
                </div>
            </div>

            <button onclick="document.getElementById('help-modal').classList.remove('active')" class="btn btn-primary" style="width: 100%; margin-top: 16px;">
                Got it
            </button>
        </div>
    </div>

    <!-- Delete Confirmation Modal -->
    <div id="delete-confirm-modal" class="modal-overlay" role="dialog" aria-modal="true" aria-labelledby="delete-confirm-title">
        <div class="modal-card" style="max-width: 420px;">
            <h2 id="delete-confirm-title" style="font-size: 18px; font-weight: 700; margin-bottom: 10px;">Delete this concept?</h2>
            <p style="color: var(--text-muted); font-size: 14px; line-height: 1.5; margin-bottom: 24px;">This will remove it from your offline library.</p>
            <div style="display: flex; gap: 10px; justify-content: flex-end;">
                <button id="delete-cancel-btn" class="btn btn-secondary">Cancel</button>
                <button id="delete-confirm-btn" class="btn btn-primary" style="background: var(--danger);">Delete</button>
            </div>
        </div>
    </div>

    <!-- Queue Processing Pill -->
    <div id="queue-processing-pill" class="queue-pill" style="display: none;">
        <span id="queue-pill-text">Processing in background...</span>
    </div>

    <!-- Scripts -->
    <script src="${pageContext.request.contextPath}/assets/js/db.js"></script>
    <script src="${pageContext.request.contextPath}/assets/js/ai.js"></script>
    <script src="${pageContext.request.contextPath}/assets/js/app.js"></script>
    <script>if (window.lucide) lucide.createIcons();</script>
</body>
</html>
