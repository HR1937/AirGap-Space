/**
 * ai.js - Native Chrome On-Device Gemini Nano Engine with Structured Knowledge Grounding
 */

function getChromeAiNamespace() {
    if (window.ai?.languageModel) return window.ai.languageModel;
    if (window.LanguageModel) return window.LanguageModel;
    if (window.ai?.assistant) return window.ai.assistant;
    return null;
}

async function checkChromeAiCapabilities() {
    console.log('[OfflineAI] Checking Chrome window.ai / window.LanguageModel availability...');
    const aiEngine = getChromeAiNamespace();
    if (!aiEngine) {
        return { available: false, reason: 'NOT_FOUND' };
    }

    try {
        if (typeof aiEngine.capabilities === 'function') {
            const caps = await aiEngine.capabilities();
            if (caps && caps.available === 'no') {
                return { available: false, reason: 'MODEL_NOT_READY' };
            }
        }
        return { available: true, engine: aiEngine };
    } catch (err) {
        return { available: false, reason: 'ERROR', error: err.message };
    }
}

async function resolveDoubtOffline(topicTitle, knowledgePackJson, userQuestion, onStageChange) {
    if (typeof onStageChange === 'function') {
        onStageChange("Reading offline knowledge...");
    }

    const aiCheck = await checkChromeAiCapabilities();
    if (!aiCheck.available) {
        throw new Error(getAiFallbackGuidanceHtml(aiCheck.reason));
    }

    const aiEngine = aiCheck.engine;

    // Safety truncation
    let safeContext = knowledgePackJson || '{}';
    if (safeContext.length > 15000) {
        safeContext = safeContext.substring(0, 15000) + '...}';
    }

    let session = null;
    try {
        if (typeof onStageChange === 'function') {
            onStageChange("Reasoning locally...");
        }

        session = await aiEngine.create({
            systemPrompt: "You are an encouraging learning companion operating offline. First answer accurately using the stored structured knowledge object below. If the answer is incomplete, reason from first principles. Do not hallucinate. Never simply repeat the stored text — teach the student with clarity.",
            outputLanguage: "en",
            expectedOutputs: [{ type: "text", languages: ["en"] }]
        });

        const fullPrompt = `Topic: ${topicTitle}\n\nStored Structured Knowledge:\n${safeContext}\n\nStudent Question / Follow-up: ${userQuestion}\n\nTeacher Explanation:`;

        const rawResult = await session.prompt(fullPrompt);

        if (typeof onStageChange === 'function') {
            onStageChange("Formatting explanation...");
        }

        let textResult = '';
        if (typeof rawResult === 'string') {
            textResult = rawResult;
        } else if (rawResult && typeof rawResult.text === 'string') {
            textResult = rawResult.text;
        } else if (rawResult) {
            textResult = String(rawResult);
        }

        return textResult;

    } catch (err) {
        console.error('[OfflineAI] Session error:', err);
        throw new Error(`Offline Error: ${err.message || 'Model execution failed'}. Ensure chrome://flags/#prompt-api-for-gemini-nano is enabled.`);
    } finally {
        if (session && typeof session.destroy === 'function') {
            try { session.destroy(); } catch (e) {}
        }
    }
}

function getAiFallbackGuidanceHtml(reason) {
    return `
        <div class="alert-box alert-warning" style="background: rgba(245,158,11,0.06); border: 1px solid rgba(245,158,11,0.2); border-radius: 8px; padding: 14px;">
            <h4 style="margin-bottom:6px; color: var(--text-main); font-size: 13px; font-weight: 600;">
                Offline Feature Setup Required
            </h4>
            <p style="font-size:13px; margin-bottom:8px; color: var(--text-muted);">
                ${reason === 'NOT_FOUND' ? 'Chrome Built-in AI is not enabled in your browser.' : 'Model is downloading or initializing.'}
            </p>
            <div style="background:var(--bg-primary); padding:10px; border-radius:6px; font-size:12px; line-height: 1.5; color: var(--text-muted);">
                <strong>Steps to activate Chrome Gemini Nano:</strong>
                <ol style="margin-left:16px; margin-top:4px;">
                    <li>Open <code>chrome://flags/#prompt-api-for-gemini-nano</code> and set to <strong>Enabled</strong>.</li>
                    <li>Open <code>chrome://flags/#optimization-guide-on-device-model</code> and set to <strong>Enabled BypassPerfRequirement</strong>.</li>
                    <li>Open <code>chrome://components</code> and verify <strong>Optimization Guide On Device Model</strong> is updated.</li>
                </ol>
            </div>
        </div>
    `;
}
