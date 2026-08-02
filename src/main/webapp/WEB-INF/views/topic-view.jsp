<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">
    <link rel="icon" type="image/svg+xml" href="${pageContext.request.contextPath}/assets/images/AirGap-Space-Logo.svg">
    <title>AirGap Study — ${not empty topic ? topic.title : 'Concept Guide'}</title>
    <script>window.CURRENT_USER_ID = parseInt("${sessionScope.user.id}", 10);</script>
    <script src="${pageContext.request.contextPath}/assets/js/theme.js"></script>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/style.css">
    <script src="https://unpkg.com/lucide@latest"></script>
    <script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
</head>
<body>
    <div class="app-container">
        <!-- Navigation -->
        <header class="navbar">
            <div class="brand-container">
                <img class="brand-logo" src="${pageContext.request.contextPath}/assets/images/AirGap-Space-Logo.svg" alt="AirGap Study logo">
                <a href="${pageContext.request.contextPath}/dashboard" class="btn btn-secondary" style="padding: 8px 16px; font-size: 13px;">
                    <i data-lucide="arrow-left" aria-hidden="true"></i>Back
                </a>
            </div>

            <div class="user-profile">
                <div id="network-status" class="status-badge online">
                    <span class="dot"></span>
                    <span>Online</span>
                </div>

            </div>
        </header>

        <!-- Topic Reader -->
        <article class="card" style="max-width: 820px; margin: 0 auto 24px auto;">
            <div style="border-bottom: 1px solid var(--bg-card-border); padding-bottom: 20px; margin-bottom: 24px;">
                <h1 id="topic-title-heading" style="font-size: 28px; font-weight: 700; letter-spacing: -0.3px; margin-bottom: 10px; line-height: 1.3; color: var(--text-main);">
                    ${topic.title}
                </h1>

                <div style="font-size: 13px; color: var(--text-dim); margin-bottom: 12px;">
                    Direction: <span id="topic-saved-reason" style="color: var(--text-muted);">${not empty topic.direction ? topic.direction : 'Intuition first'}</span>
                </div>

                <div style="display: flex; align-items: center; gap: 10px; font-size: 13px; color: var(--text-muted); flex-wrap: wrap;">
                    <span>Captured ${topic.createdAt}</span>
                    <c:choose>
                        <c:when test="${topic.status == 'READY_OFFLINE'}">
                            <span class="badge badge-success"><i data-lucide="check-circle" aria-hidden="true"></i>Ready Offline</span>
                        </c:when>
                        <c:when test="${topic.status == 'AI_UNAVAILABLE' || topic.status == 'FAILED'}">
                            <span class="badge badge-danger"><i data-lucide="triangle-alert" aria-hidden="true"></i>Generation Failed</span>
                        </c:when>
                        <c:otherwise>
                            <span class="badge badge-warning"><i data-lucide="clock-3" aria-hidden="true"></i>Queued</span>
                        </c:otherwise>
                    </c:choose>
                </div>
            </div>

            <!-- Raw Content Holders (stored JSON, 0 API calls on view) -->
            <div id="raw-summary-content" style="display: none;">${topic.summaryContent}</div>
            <div id="raw-knowledge-json" style="display: none;">${topic.knowledgePackJson}</div>
            <div id="raw-teaching-json" style="display: none;">${topic.teachingPlanJson}</div>
            <div id="raw-curiosity-json" style="display: none;">${topic.curiosityPathsJson}</div>
            <div id="raw-related-json" style="display: none;">${topic.relatedConceptsJson}</div>

            <c:choose>
                <c:when test="${topic.status == 'READY_OFFLINE'}">
                    <!-- Context Bridge -->
                    <div id="context-bridge-container" style="display: none; margin-bottom: 24px; padding: 14px 18px; background: rgba(79, 143, 247, 0.04); border: 1px solid rgba(79, 143, 247, 0.12); border-radius: var(--radius-md); font-size: 14px; color: var(--text-main); line-height: 1.6;">
                    </div>

                    <!-- Progressive Reader -->
                    <div id="progressive-sections-wrapper" class="topic-content">
                        <!-- Sections injected dynamically -->
                    </div>

                    <!-- Quick Check -->
                    <div id="quick-check-wrapper" style="display: none; margin-top: 36px; padding: 24px; background: var(--bg-primary); border: 1px solid var(--bg-card-border); border-radius: var(--radius-lg);">
                        <div style="font-size: 12px; font-weight: 700; letter-spacing: 0.5px; text-transform: uppercase; color: var(--text-muted); margin-bottom: 10px;">
                            Check your understanding
                        </div>
                        <h4 id="quick-check-question" style="font-size: 16px; font-weight: 600; margin-bottom: 16px; color: var(--text-main); line-height: 1.5;">
                        </h4>

                        <div id="think-pause-container" style="margin-bottom: 16px;">
                            <button onclick="revealQuickCheckOptions()" class="btn btn-secondary" style="padding: 10px 20px; font-size: 13px;">
                                Think first... then reveal <i data-lucide="arrow-right" aria-hidden="true"></i>
                            </button>
                        </div>

                        <div id="quick-check-options" style="display: none; flex-direction: column; gap: 8px; margin-bottom: 16px;">
                        </div>
                        <div id="quick-check-feedback" style="display: none; padding: 12px 16px; border-radius: var(--radius-md); font-size: 14px; line-height: 1.6;">
                        </div>
                    </div>
                </c:when>

                <c:otherwise>
                    <!-- Pending / Failed State -->
                    <div style="padding: 40px 24px; text-align: center; background: var(--bg-primary); border: 1px dashed var(--bg-card-border); border-radius: var(--radius-lg); margin-top: 12px;">
                        <c:choose>
                            <c:when test="${topic.status == 'AI_UNAVAILABLE' || topic.status == 'FAILED'}">
                                <div class="state-icon" aria-hidden="true"><i data-lucide="triangle-alert"></i></div>
                                <h3 style="font-size: 17px; font-weight: 600; margin-bottom: 8px; color: var(--text-main);">Generation Failed</h3>
                                <p style="font-size: 14px; color: var(--text-muted); max-width: 440px; margin: 0 auto 20px auto; line-height: 1.6;">
                                    The AI service was unavailable. This could be due to rate limits, network issues, or API configuration.
                                </p>
                                <button onclick="retryTopic(${topic.id})" class="btn btn-primary" style="padding: 10px 24px; font-size: 14px;">
                                    Retry Generation
                                </button>
                            </c:when>
                            <c:otherwise>
                                <div class="state-icon" aria-hidden="true"><i data-lucide="clock-3"></i></div>
                                <h3 style="font-size: 17px; font-weight: 600; margin-bottom: 8px; color: var(--text-main);">Preparing Guide...</h3>
                                <p style="font-size: 14px; color: var(--text-muted); max-width: 440px; margin: 0 auto 20px auto; line-height: 1.6;">
                                    This concept was captured successfully. The study guide will appear automatically once enrichment finishes.
                                </p>
                                <button onclick="retryTopic(${topic.id})" class="btn btn-secondary" style="padding: 10px 20px; font-size: 13px;">
                                    Retry Now
                                </button>
                            </c:otherwise>
                        </c:choose>
                    </div>
                </c:otherwise>
            </c:choose>
        </article>

        <c:if test="${topic.status == 'READY_OFFLINE'}">
            <!-- Continue Learning -->
            <section class="card" style="max-width: 820px; margin: 0 auto 24px auto;">
                <h3 style="font-size: 18px; font-weight: 700; margin-bottom: 4px;">Continue learning</h3>
                <p style="font-size: 13px; color: var(--text-muted); margin-bottom: 18px;">
                    Exploration questions and related concepts to study next.
                </p>

                <div id="curiosity-paths-container" class="exploration-grid" style="margin-bottom: 24px;">
                </div>

                <div style="border-top: 1px solid var(--bg-card-border); padding-top: 20px;">
                    <div style="font-size: 14px; font-weight: 600; margin-bottom: 12px; color: var(--text-main);">
                        Related concepts
                    </div>
                    <div id="related-concepts-container" style="display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 12px;">
                    </div>
                </div>
            </section>

            <!-- Follow-up Questions -->
            <section class="doubt-box card" style="max-width: 820px; margin: 0 auto 24px auto;">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; flex-wrap: wrap; gap: 8px;">
                    <h3 style="font-size: 18px; font-weight: 700;">Ask a follow-up</h3>
                    <span style="font-size: 12px; color: var(--text-dim);">Chrome Gemini Nano · Offline</span>
                </div>

                <form id="doubt-form">
                    <div style="margin-bottom: 12px;">
                        <input type="text" id="doubt-input" class="form-control"
                            placeholder="Ask anything about this concept..." required>
                    </div>
                    <button type="submit" id="ask-doubt-btn" class="btn btn-primary" style="padding: 10px 20px;">
                        Ask
                    </button>
                </form>

                <div id="doubt-response-container" class="doubt-response" style="display: none;">
                </div>
            </section>
        </c:if>
    </div>

    <!-- Scripts -->
    <script src="${pageContext.request.contextPath}/assets/js/db.js"></script>
    <script src="${pageContext.request.contextPath}/assets/js/ai.js"></script>
    <script src="${pageContext.request.contextPath}/assets/js/app.js"></script>
    <script>if (window.lucide) lucide.createIcons();</script>

    <script>
        let currentTopicTitle = "${topic.title}";
        let currentKnowledgePackJson = "{}";
        let currentSections = [];
        let visibleSectionIndex = 0;
        let quickCheckData = null;

        document.addEventListener('DOMContentLoaded', async () => {
            const rawSummaryEl = document.getElementById('raw-summary-content');
            const rawKnowledgeEl = document.getElementById('raw-knowledge-json');
            const rawTeachingEl = document.getElementById('raw-teaching-json');
            const rawCuriosityEl = document.getElementById('raw-curiosity-json');
            const rawRelatedEl = document.getElementById('raw-related-json');
            const titleHeading = document.getElementById('topic-title-heading');

            let currentSummaryContent = rawSummaryEl ? rawSummaryEl.innerText : "";
            currentKnowledgePackJson = rawKnowledgeEl ? rawKnowledgeEl.innerText : "{}";
            let rawTeachingJson = rawTeachingEl ? rawTeachingEl.innerText : "{}";
            let rawCuriosityJson = rawCuriosityEl ? rawCuriosityEl.innerText : "[]";
            let rawRelatedJson = rawRelatedEl ? rawRelatedEl.innerText : "[]";

            const urlParams = new URLSearchParams(window.location.search);
            const topicId = urlParams.get('id');

            // Load from IndexedDB if server data is empty (offline-first)
            if ((!currentSummaryContent || currentSummaryContent.trim() === '') && topicId) {
                const localTopic = await getLocalTopicById(topicId);
                if (localTopic) {
                    currentTopicTitle = localTopic.title;
                    currentSummaryContent = localTopic.summaryContent || localTopic.content || "";
                    currentKnowledgePackJson = localTopic.knowledgePackJson || "{}";
                    rawTeachingJson = localTopic.teachingPlanJson || "{}";
                    rawCuriosityJson = localTopic.curiosityPathsJson || "[]";
                    rawRelatedJson = localTopic.relatedConceptsJson || "[]";
                    if (titleHeading) titleHeading.innerText = localTopic.title;
                    const savedReasonEl = document.getElementById('topic-saved-reason');
                    if (savedReasonEl && localTopic.direction) savedReasonEl.innerText = localTopic.direction;
                }
            }

            // Extract Context Bridge
            if (currentSummaryContent && currentSummaryContent.includes('> **Context Bridge**:')) {
                const bridgeStart = currentSummaryContent.indexOf('> **Context Bridge**:');
                const doubleNL = currentSummaryContent.indexOf('\n\n', bridgeStart);
                if (doubleNL !== -1) {
                    const bridgeText = currentSummaryContent.substring(bridgeStart + '> **Context Bridge**:'.length, doubleNL).trim();
                    currentSummaryContent = currentSummaryContent.substring(doubleNL + 2).trim();
                    const bridgeContainer = document.getElementById('context-bridge-container');
                    if (bridgeContainer && bridgeText) {
                        bridgeContainer.style.display = 'block';
                        bridgeContainer.innerHTML = '<strong>Context Bridge:</strong> ' + escapeHtml(bridgeText);
                    }
                }
            }

            // Parse Quick Check
            try {
                if (rawTeachingJson && rawTeachingJson.trim()) {
                    const tObj = JSON.parse(rawTeachingJson);
                    if (tObj && tObj.quickCheck) quickCheckData = tObj.quickCheck;
                }
            } catch (e) {}

            // Render Progressive Sections
            setupProgressiveReader(currentSummaryContent);

            // Lazy Load Secondary Content
            if ('IntersectionObserver' in window) {
                const continueSection = document.querySelector('section.card');
                if (continueSection) {
                    const observer = new IntersectionObserver((entries) => {
                        entries.forEach(entry => {
                            if (entry.isIntersecting) {
                                renderRelatedConcepts(rawRelatedJson);
                                renderCuriosityPaths(rawCuriosityJson, currentTopicTitle);
                                observer.disconnect();
                            }
                        });
                    }, { rootMargin: '200px' });
                    observer.observe(continueSection);
                } else {
                    setTimeout(() => {
                        renderRelatedConcepts(rawRelatedJson);
                        renderCuriosityPaths(rawCuriosityJson, currentTopicTitle);
                    }, 50);
                }
            } else {
                setTimeout(() => {
                    renderRelatedConcepts(rawRelatedJson);
                    renderCuriosityPaths(rawCuriosityJson, currentTopicTitle);
                }, 50);
            }

            // Doubt Form
            const doubtForm = document.getElementById('doubt-form');
            if (doubtForm) {
                doubtForm.addEventListener('submit', async (e) => {
                    e.preventDefault();
                    const doubtInput = document.getElementById('doubt-input');
                    const question = doubtInput.value.trim();
                    if (!question) return;
                    await runOfflineQuery(question);
                });
            }
        });

        function setupProgressiveReader(markdownContent) {
            const wrapper = document.getElementById('progressive-sections-wrapper');
            if (!wrapper) return;
            if (!markdownContent || !markdownContent.trim()) {
                wrapper.innerHTML = '';
                return;
            }

            const rawParts = markdownContent.split(/(?=\n## )/);
            currentSections = rawParts.filter(p => p.trim().length > 0);
            if (currentSections.length === 0) currentSections = [markdownContent];

            visibleSectionIndex = 0;
            renderVisibleSections();
        }

        function renderVisibleSections() {
            const wrapper = document.getElementById('progressive-sections-wrapper');
            if (!wrapper) return;

            var html = '';
            for (var i = 0; i <= visibleSectionIndex && i < currentSections.length; i++) {
                var sectionMd = currentSections[i];
                var renderedHtml = '';
                try {
                    if (typeof marked !== 'undefined' && typeof marked.parse === 'function') {
                        renderedHtml = marked.parse(sectionMd);
                    } else {
                        renderedHtml = sectionMd.replace(/\n/g, '<br>');
                    }
                } catch (e) {
                    renderedHtml = sectionMd.replace(/\n/g, '<br>');
                }
                html += '<div class="progressive-section" style="margin-bottom:28px;">' + renderedHtml + '</div>';
            }

            if (visibleSectionIndex < currentSections.length - 1) {
                html += '<div style="text-align:center; margin-top:20px; margin-bottom:32px;">' +
                        '<button onclick="revealNextSection()" class="btn btn-secondary" style="padding:10px 24px; font-weight:600;">' +
                        'Continue reading <i data-lucide="arrow-right" aria-hidden="true"></i>' +
                        '</button>' +
                        '</div>';
            }

            wrapper.innerHTML = html;
            refreshLucideIcons();

            if (visibleSectionIndex >= currentSections.length - 1) {
                renderQuickCheck();
            }
        }

        function revealNextSection() {
            if (visibleSectionIndex < currentSections.length - 1) {
                visibleSectionIndex++;
                renderVisibleSections();
            }
        }

        function renderQuickCheck() {
            const wrapper = document.getElementById('quick-check-wrapper');
            const qEl = document.getElementById('quick-check-question');
            if (!wrapper || !quickCheckData || !quickCheckData.question) return;
            wrapper.style.display = 'block';
            qEl.innerText = quickCheckData.question;
        }

        function revealQuickCheckOptions() {
            const pauseContainer = document.getElementById('think-pause-container');
            const optsEl = document.getElementById('quick-check-options');
            if (!optsEl || !quickCheckData) return;
            if (pauseContainer) pauseContainer.style.display = 'none';
            optsEl.style.display = 'flex';

            var optsHtml = '';
            const opts = quickCheckData.options || [];
            for (var i = 0; i < opts.length; i++) {
                optsHtml += '<button onclick="selectQuickCheckOption(' + i + ')" class="btn btn-secondary" style="justify-content:flex-start; text-align:left; padding:12px 16px; font-size:14px; font-weight:400; width:100%; border-radius:var(--radius-md);">' +
                    '<span style="font-weight:600; margin-right:8px;">' + String.fromCharCode(65 + i) + '.</span> ' + escapeHtml(opts[i]) +
                    '</button>';
            }
            optsEl.innerHTML = optsHtml;
        }

        function selectQuickCheckOption(selectedIndex) {
            const feedbackEl = document.getElementById('quick-check-feedback');
            if (!feedbackEl || !quickCheckData) return;

            const isCorrect = selectedIndex === quickCheckData.correctIndex;
            feedbackEl.style.display = 'block';

            if (isCorrect) {
                feedbackEl.style.background = 'rgba(16, 185, 129, 0.08)';
                feedbackEl.style.border = '1px solid rgba(16, 185, 129, 0.25)';
                feedbackEl.style.color = 'var(--text-main)';
                feedbackEl.innerHTML = '<strong>Correct.</strong> ' + escapeHtml(quickCheckData.explanation || '');
            } else {
                feedbackEl.style.background = 'rgba(239, 68, 68, 0.08)';
                feedbackEl.style.border = '1px solid rgba(239, 68, 68, 0.25)';
                feedbackEl.style.color = 'var(--text-main)';
                const correctText = (quickCheckData.options && quickCheckData.options[quickCheckData.correctIndex]) ? quickCheckData.options[quickCheckData.correctIndex] : '';
                feedbackEl.innerHTML = '<strong>Incorrect.</strong> Correct answer: <em>' + escapeHtml(correctText) + '</em>. ' + escapeHtml(quickCheckData.explanation || '');
            }
        }

        function renderRelatedConcepts(jsonStr) {
            const container = document.getElementById('related-concepts-container');
            if (!container) return;

            let concepts = [];
            try {
                if (jsonStr && jsonStr.trim()) concepts = JSON.parse(jsonStr);
            } catch (e) {}

            var html = '';
            if (Array.isArray(concepts) && concepts.length > 0) {
                for (var i = 0; i < concepts.length; i++) {
                    var item = concepts[i];
                    var name = typeof item === 'string' ? item : (item.name || 'Concept');
                    var category = typeof item === 'object' && item.category ? item.category : 'Related';
                    var reason = typeof item === 'object' && item.reason ? item.reason : 'Explore next';

                    html += '<div style="background:var(--bg-primary); border:1px solid var(--bg-card-border); border-radius:var(--radius-md); padding:14px; display:flex; flex-direction:column; justify-content:space-between;">' +
                        '<div>' +
                        '<div style="font-size:11px; font-weight:700; text-transform:uppercase; color:var(--text-dim); margin-bottom:4px; letter-spacing:0.3px;">' + escapeHtml(category) + '</div>' +
                        '<div style="font-size:15px; font-weight:600; color:var(--text-main); margin-bottom:6px;">' + escapeHtml(name) + '</div>' +
                        '<div style="font-size:12px; color:var(--text-muted); line-height:1.5; margin-bottom:12px;">' + escapeHtml(reason) + '</div>' +
                        '</div>' +
                        '<button onclick="captureRelatedConcept(\'' + escapeJs(name) + '\')" class="btn btn-secondary" style="padding:7px 12px; font-size:12px; width:100%; justify-content:center;"><i data-lucide="plus" aria-hidden="true"></i> Capture</button>' +
                        '</div>';
                }
            } else {
                html = '<div style="font-size:13px; color:var(--text-muted);">No related concepts available.</div>';
            }
            container.innerHTML = html;
            refreshLucideIcons();
        }

        function renderCuriosityPaths(jsonStr, title) {
            const container = document.getElementById('curiosity-paths-container');
            if (!container) return;

            let paths = [];
            try {
                if (jsonStr && jsonStr.trim()) paths = JSON.parse(jsonStr);
            } catch (e) {}

            if (!Array.isArray(paths) || paths.length === 0) {
                paths = [
                    { title: "Why does " + title + " exist?", prompt: "Explain why " + title + " was created and what core problem it solved." },
                    { title: "How is " + title + " used in practice?", prompt: "Provide real world usage examples of " + title }
                ];
            }

            var html = '';
            for (var i = 0; i < paths.length; i++) {
                var p = paths[i];
                html += '<div class="exploration-card" onclick="triggerCuriosityPath(\'' + escapeJs(p.title) + '\', \'' + escapeJs(p.prompt) + '\')">' +
                        '<div class="exploration-card-title">' + escapeHtml(p.title) + '</div>' +
                        '</div>';
            }
            container.innerHTML = html;
            refreshLucideIcons();
        }

        async function triggerCuriosityPath(pathTitle, query) {
            const doubtInput = document.getElementById('doubt-input');
            if (doubtInput) doubtInput.value = '[' + pathTitle + '] ' + query;
            await runOfflineQuery('[Exploration: ' + pathTitle + '] ' + query);
        }

        async function runOfflineQuery(userQuestion) {
            var askBtn = document.getElementById('ask-doubt-btn');
            var responseContainer = document.getElementById('doubt-response-container');

            askBtn.disabled = true;
            var originalText = askBtn.innerHTML;

            responseContainer.style.display = 'block';
            responseContainer.innerHTML = '<div style="display:flex; align-items:center; gap:10px; color:var(--text-muted); font-size:13px;">' +
                '<div class="dot" style="animation: pulse 1s infinite;"></div>' +
                '<span id="ai-stage-text">Reading offline knowledge...</span>' +
                '</div>';

            var stageTextEl = document.getElementById('ai-stage-text');

            try {
                var answer = await resolveDoubtOffline(
                    currentTopicTitle,
                    currentKnowledgePackJson,
                    userQuestion,
                    function(stageMsg) {
                        if (stageTextEl) stageTextEl.innerText = stageMsg;
                    }
                );

                var renderedAnswer = '';
                if (answer && answer.trim().length > 0) {
                    try {
                        if (typeof marked !== 'undefined' && typeof marked.parse === 'function') {
                            renderedAnswer = marked.parse(answer);
                        } else {
                            renderedAnswer = answer.replace(/\n/g, '<br>');
                        }
                    } catch (mErr) {
                        renderedAnswer = answer.replace(/\n/g, '<br>');
                    }
                } else {
                    renderedAnswer = '<em>No response from local AI session.</em>';
                }

                responseContainer.innerHTML = '<div class="doubt-response" style="display:block;">' +
                    '<div style="font-weight:600; color:var(--text-main); margin-bottom:10px; font-size:14px;">Answer:</div>' +
                    '<div style="font-size:14px; line-height:1.7; color:var(--text-main);">' + renderedAnswer + '</div>' +
                    '</div>';

            } catch (err) {
                responseContainer.innerHTML = '<div style="color:var(--danger); font-size:13px;">' + (err.message || String(err)) + '</div>';
            } finally {
                askBtn.disabled = false;
                askBtn.innerHTML = originalText;
            }
        }

        function escapeJs(str) {
            if (!str) return '';
            return str.replace(/\\/g, "\\\\").replace(/'/g, "\\'").replace(/"/g, '\\"');
        }

        function escapeHtml(str) {
            if (!str) return '';
            return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#39;");
        }
    </script>
</body>
</html>
