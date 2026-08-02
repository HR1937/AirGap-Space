package com.airgap.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class GeminiApiService {

    private static final String API_KEYS_ENV = "GEMINI_API_KEYS";
    private static final String API_KEY_ENV = "GEMINI_API_KEY";
    private static final String[] CANDIDATE_MODELS = {
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-flash-latest"
    };

    private static final long COOLDOWN_MILLIS = 24 * 60 * 60 * 1000L; // 24 hours

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<String> apiKeys;
    private final AtomicInteger currentKeyIndex = new AtomicInteger(0);
    private final ConcurrentHashMap<String, Long> disabledUntilMap = new ConcurrentHashMap<>();

    public GeminiApiService() {
        List<String> loadedKeys = loadKeysWithEmpiricalDiagnostics();
        if (loadedKeys.isEmpty()) {
            System.err.println("[Gemini] WARNING: No Gemini API keys found in env, system properties, classpath, or .env files!");
            this.apiKeys = Collections.emptyList();
        } else {
            this.apiKeys = Collections.unmodifiableList(loadedKeys);
            System.out.println("[Gemini] Active key pool initialized with " + loadedKeys.size() + " key(s). Active index = 1/" + loadedKeys.size());
        }
    }

    private List<String> loadKeysWithEmpiricalDiagnostics() {
        System.out.println("================================================================================");
        System.out.println("[GEMINI KEY RESOLUTION DIAGNOSTIC TRACE]");
        System.out.println("================================================================================");
        
        String userDir = System.getProperty("user.dir");
        String catalinaBase = System.getProperty("catalina.base", System.getenv("CATALINA_BASE"));
        String catalinaHome = System.getProperty("catalina.home", System.getenv("CATALINA_HOME"));
        
        System.out.println("1. Runtime Environment:");
        System.out.println("   - user.dir = " + userDir);
        System.out.println("   - CATALINA_BASE = " + catalinaBase);
        System.out.println("   - CATALINA_HOME = " + catalinaHome);

        List<String> rawLines = new ArrayList<>();
        String matchedSource = null;

        // Strategy A: Classpath Resource (Packages inside WAR as WEB-INF/classes/.env)
        try (InputStream is = getClass().getResourceAsStream("/.env")) {
            if (is != null) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        rawLines.add(line);
                    }
                }
                matchedSource = "Classpath Resource (/.env)";
            }
        } catch (Exception e) {
            System.out.println("   - Classpath /.env read error: " + e.getMessage());
        }

        if (rawLines.isEmpty()) {
            try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(".env")) {
                if (is != null) {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            rawLines.add(line);
                        }
                    }
                    matchedSource = "ContextClassLoader (.env)";
                }
            } catch (Exception e) {}
        }

        // Strategy B: File System Paths relative to runtime working dirs
        if (rawLines.isEmpty()) {
            List<File> candidateFiles = new ArrayList<>();
            candidateFiles.add(new File(".env"));
            if (userDir != null) {
                candidateFiles.add(new File(userDir, ".env"));
                candidateFiles.add(new File(userDir, "../.env"));
            }
            if (catalinaBase != null) {
                candidateFiles.add(new File(catalinaBase, ".env"));
                candidateFiles.add(new File(catalinaBase, "webapps/airgap-study/.env"));
            }

            for (File f : candidateFiles) {
                System.out.println("   - Checking file path: " + f.getAbsolutePath() + " (exists=" + f.exists() + ", readable=" + f.canRead() + ")");
                if (f.exists() && f.canRead()) {
                    try {
                        rawLines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
                        matchedSource = "File (" + f.getAbsolutePath() + ")";
                        break;
                    } catch (Exception e) {
                        System.out.println("   - Failed to read " + f.getAbsolutePath() + ": " + e.getMessage());
                    }
                }
            }
        }

        System.out.println("2. .env Resolution Source: " + (matchedSource != null ? matchedSource : "NONE FOUND"));

        List<String> keys = new ArrayList<>();

        // Check OS env / JVM property GEMINI_API_KEYS
        String multiKeysEnv = System.getenv(API_KEYS_ENV);
        if (multiKeysEnv == null || multiKeysEnv.isBlank()) {
            multiKeysEnv = System.getProperty(API_KEYS_ENV);
        }

        // Parse .env lines if OS env is empty
        if ((multiKeysEnv == null || multiKeysEnv.isBlank()) && !rawLines.isEmpty()) {
            for (String line : rawLines) {
                line = line.trim();
                if (line.startsWith(API_KEYS_ENV + "=")) {
                    multiKeysEnv = line.substring((API_KEYS_ENV + "=").length()).trim();
                    break;
                }
            }
        }

        if (multiKeysEnv != null && !multiKeysEnv.isBlank()) {
            System.out.println("3. Parsed GEMINI_API_KEYS raw string length: " + multiKeysEnv.length() + " chars");
            String[] parts = multiKeysEnv.split(",");
            for (String p : parts) {
                String k = p.trim();
                if (isKeyValid(k) && !keys.contains(k)) {
                    keys.add(k);
                }
            }
        } else {
            System.out.println("3. GEMINI_API_KEYS property is empty or absent.");
        }

        // Single key fallback if multiKeys yielded 0 keys
        if (keys.isEmpty()) {
            String singleKey = System.getenv(API_KEY_ENV);
            if (singleKey == null || singleKey.isBlank()) {
                singleKey = System.getProperty(API_KEY_ENV);
            }
            if ((singleKey == null || singleKey.isBlank()) && !rawLines.isEmpty()) {
                for (String line : rawLines) {
                    line = line.trim();
                    if (line.startsWith(API_KEY_ENV + "=")) {
                        singleKey = line.substring((API_KEY_ENV + "=").length()).trim();
                        break;
                    }
                }
            }
            if (singleKey != null && !singleKey.isBlank()) {
                String k = singleKey.trim();
                if (isKeyValid(k)) {
                    keys.add(k);
                }
            }
        }

        System.out.println("4. Key Loading Diagnostic Result:");
        System.out.println("   - Total Valid Keys Loaded: " + keys.size());
        if (keys.isEmpty()) {
            System.err.println("   [EXACT DIAGNOSTIC REASON FOR 0 KEYS]:");
            System.err.println("   No valid GEMINI_API_KEYS or GEMINI_API_KEY found in System.getenv(), System.getProperty(), classpath (/.env), or file paths relative to user.dir/CATALINA_BASE.");
        } else {
            for (int i = 0; i < keys.size(); i++) {
                String k = keys.get(i);
                String masked = (k.length() >= 8) ? k.substring(0, 8) + "..." : k;
                System.out.println("   - Key " + (i + 1) + ": " + masked + " (length=" + k.length() + ")");
            }
        }
        System.out.println("================================================================================");

        return keys;
    }

    public boolean isConfigured() {
        if (apiKeys.isEmpty()) return false;
        long now = System.currentTimeMillis();
        for (String k : apiKeys) {
            Long disabledUntil = disabledUntilMap.get(k);
            if (disabledUntil == null || now >= disabledUntil) {
                return true;
            }
        }
        return false;
    }

    private boolean isKeyValid(String key) {
        return key != null && !key.isBlank() && !key.startsWith("YOUR_");
    }

    public static class EnrichedTopicResult {
        public final String summaryContent;
        public final String knowledgePackJson;
        public final String teachingPlanJson;
        public final String curiosityPathsJson;
        public final String relatedConceptsJson;
        public final int estimatedReadingTime;

        public EnrichedTopicResult(String summaryContent, String knowledgePackJson, 
                                    String teachingPlanJson, String curiosityPathsJson, 
                                    String relatedConceptsJson, int estimatedReadingTime) {
            this.summaryContent = summaryContent;
            this.knowledgePackJson = knowledgePackJson;
            this.teachingPlanJson = teachingPlanJson;
            this.curiosityPathsJson = curiosityPathsJson;
            this.relatedConceptsJson = relatedConceptsJson;
            this.estimatedReadingTime = estimatedReadingTime;
        }
    }

    public EnrichedTopicResult generateEnrichedTopic(String topicTitle, String learningDirection) {
        return generateEnrichedTopic(topicTitle, learningDirection, new ArrayList<>());
    }

    public EnrichedTopicResult generateEnrichedTopic(String topicTitle, String learningDirection, List<String> existingUserTopics) {
        if (apiKeys.isEmpty()) {
            System.err.println("[Gemini]\nAll configured API keys exhausted.\nTopic marked AI_UNAVAILABLE.");
            return null;
        }

        String effectiveDirection = (learningDirection != null && !learningDirection.isBlank())
                ? learningDirection.trim()
                : "Explain why this exists, how it works intuitively, and key takeaways.";

        String existingTopicsStr = (existingUserTopics != null && !existingUserTopics.isEmpty())
                ? String.join(", ", existingUserTopics)
                : "None";

        String prompt = String.format(
                "You are the world's best 1-on-1 personal teacher explaining '%s' to a student.\n" +
                "Learning Focus / Motivation: '%s'.\n" +
                "User's Previously Learned Concepts: [%s].\n\n" +
                "CRITICAL QUALITY RULES:\n" +
                "1. EVERY SENTENCE MUST BE 100%% SPECIFIC TO '%s'. NEVER use generic reusable filler sentences.\n" +
                "2. NO EMOJIS OR TEXTBOOK DECORATIONS IN MARKDOWN.\n" +
                "3. COMPANY EXAMPLES: Use a real company example ONLY if you are reasonably confident it is widely documented (e.g. Twitter, GitHub). Otherwise describe the use case generically (e.g. 'large social media platforms cache timelines') instead of inventing a company.\n" +
                "4. ACTIONABLE EXAMPLES: Prefer examples and programs that students can realistically pursue or observe (e.g. GSoC, LFX Mentorship, Outreachy).\n" +
                "5. CONTEXT BRIDGE: Look at User's Previously Learned Concepts: [%s]. If any previously learned concept has a direct, meaningful relationship to '%s', generate AT MOST one short 2-3 sentence context bridge. If NO meaningful relationship exists, return an empty string \"\".\n\n" +
                "INSTRUCTIONS:\n" +
                "You MUST return ONLY a single, raw, valid JSON object without markdown code block backticks (like ```json), or explanatory text before/after.\n\n" +
                "JSON OBJECT SCHEMA:\n" +
                "{\n" +
                "  \"summary\": \"A concise, engaging reading summary (approx 400-600 words) in Markdown with clear ## H2 Section Headings for progressive reading. 1) Problem 2) Core Idea 3) Mental Model 4) Real-world Uses 5) Common Mistake. NO EMOJIS.\",\n" +
                "  \"knowledgePack\": {\n" +
                "    \"mentalModel\": \"A 1-2 sentence vivid mental model specific to '%s'\",\n" +
                "    \"keyIdeas\": [\"Topic-specific key idea 1\", \"Topic-specific key idea 2\", \"Topic-specific key idea 3\"],\n" +
                "    \"examples\": [\"Specific real-world company/use-case 1\", \"Specific use-case 2\"],\n" +
                "    \"comparisons\": [{\"vs\": \"Real Alternative Name\", \"difference\": \"Specific technical distinction\"}],\n" +
                "    \"misconceptions\": [\"Topic-specific common mistake 1\", \"Topic-specific common mistake 2\"]\n" +
                "  },\n" +
                "  \"quickCheck\": {\n" +
                "    \"question\": \"One conceptual question testing understanding of '%s' (e.g., 'Why isn't PostgreSQL alone enough for sub-millisecond session caching?')\",\n" +
                "    \"options\": [\"Option 0\", \"Option 1\", \"Option 2\", \"Option 3\"],\n" +
                "    \"correctIndex\": 1,\n" +
                "    \"explanation\": \"1-sentence clear explanation of why the correct option is right\"\n" +
                "  },\n" +
                "  \"relatedConcepts\": [\n" +
                "    {\"name\": \"ConceptName\", \"category\": \"Alternative|Peer|Complement|Prerequisite|Advanced\", \"reason\": \"Short 1-line reason why the student may naturally reach this next\"}\n" +
                "  ],\n" +
                "  \"explorationQuestions\": [\n" +
                "    {\"title\": \"Natural conversational question (e.g., 'Why do people choose Redis instead of Memcached?')\", \"prompt\": \"Complete curiosity question prompt for local AI follow-up\"}\n" +
                "  ],\n" +
                "  \"contextBridge\": \"2-3 sentence bridge if a previously learned topic connects to '%s', else empty string \\\"\\\".\"\n" +
                "}\n\n" +
                "STRICT CONSTRAINTS ON RELATED CONCEPTS:\n" +
                "- relatedConcepts MUST contain ONLY specific, real, learnable concepts or tools (e.g. for Redis: Memcached, Valkey, PostgreSQL, RabbitMQ, Kafka; for Docker: Podman, Containerd, OCI, Docker Compose, Kubernetes; for Online Internship: Resume, LinkedIn, ATS, Open Source, GSoC).\n" +
                "- relatedConcepts MUST NEVER contain generic headings or section titles (FORBIDDEN: Overview, Architecture, Internals, Performance, Workflow, Examples, Applications, Core Principles).\n\n" +
                "Return JSON ONLY.",
                topicTitle,
                effectiveDirection,
                existingTopicsStr,
                topicTitle,
                existingTopicsStr,
                topicTitle,
                topicTitle,
                topicTitle,
                topicTitle
        );

        System.out.println("[QUEUE TRACE STEP 3] Prompt Length: " + prompt.length() + " chars | Topic Title: '" + topicTitle + "'");

        ObjectNode textNode = objectMapper.createObjectNode().put("text", prompt);
        ObjectNode partsNode = objectMapper.createObjectNode();
        partsNode.set("parts", objectMapper.createArrayNode().add(textNode));
        ObjectNode rootPayload = objectMapper.createObjectNode();
        rootPayload.set("contents", objectMapper.createArrayNode().add(partsNode));

        String jsonPayload;
        byte[] payloadBytes;
        try {
            jsonPayload = objectMapper.writeValueAsString(rootPayload);
            payloadBytes = jsonPayload.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[Gemini BUG] Prompt JSON construction failed: " + e.getMessage());
            return null; // Application bug: do not retry another key
        }

        int totalKeys = apiKeys.size();
        int startIndex = currentKeyIndex.get() % totalKeys;

        for (int i = 0; i < totalKeys; i++) {
            int candidateIdx = (startIndex + i) % totalKeys;
            String currentKey = apiKeys.get(candidateIdx);

            Long disabledUntil = disabledUntilMap.get(currentKey);
            long now = System.currentTimeMillis();
            if (disabledUntil != null && now < disabledUntil) {
                System.out.println("[Gemini] Key " + (candidateIdx + 1) + "/" + totalKeys + " on cooldown until " + new Date(disabledUntil) + ". Skipping.");
                continue;
            }

            System.out.println("[Gemini] Trying key " + (candidateIdx + 1) + "/" + totalKeys);

            boolean keyFailedWithQuotaOrTransient = false;
            boolean keyFailedWithAuth = false;

            for (String modelName : CANDIDATE_MODELS) {
                String fullUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + currentKey;
                HttpURLConnection conn = null;

                try {
                    URL url = new URL(fullUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(35000);
                    conn.setDoOutput(true);

                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(payloadBytes);
                        os.flush();
                    }

                    int responseCode = conn.getResponseCode();
                    System.out.println("[Gemini] Model: " + modelName + " -> HTTP Status: " + responseCode);

                    if (responseCode != 200) {
                        InputStream es = conn.getErrorStream();
                        if (es != null) {
                            try {
                                String errBody = new String(es.readAllBytes(), StandardCharsets.UTF_8);
                                System.out.println("==== Gemini Error Body ====");
                                System.out.println(errBody);
                                System.out.println("===========================");
                            } catch (Exception e) {}
                        }
                    }

                    if (responseCode == 200) {
                        StringBuilder sb = new StringBuilder();
                        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = br.readLine()) != null) {
                                sb.append(line);
                            }
                        }
                        String responseBody = sb.toString();
                        JsonNode root = objectMapper.readTree(responseBody);
                        JsonNode textCandidate = root.path("candidates").get(0).path("content").path("parts").get(0).path("text");
                        if (textCandidate != null && !textCandidate.asText().isBlank()) {
                            EnrichedTopicResult result = parseSingleJsonResponse(textCandidate.asText(), topicTitle);
                            if (result != null) {
                                currentKeyIndex.set(candidateIdx);
                                System.out.println("[Gemini] Request succeeded using key " + (candidateIdx + 1) + "/" + totalKeys + ". Current active key = " + (candidateIdx + 1));
                                return result;
                            } else {
                                System.err.println("[Gemini BUG] Structured response parsing returned null. Application bug — stopping key retries.");
                                return null; // Application bug: do not retry another key
                            }
                        }
                    } else if (responseCode == 429) {
                        System.err.println("[Gemini] HTTP 429 (Rate Limit / Quota Exceeded) on key " + (candidateIdx + 1) + "/" + totalKeys + ". Switching to next key...");
                        keyFailedWithQuotaOrTransient = true;
                        break; // Stop model loop for this key, move to next key
                    } else if (responseCode == 401 || responseCode == 403) {
                        System.err.println("[Gemini] HTTP " + responseCode + " (Invalid/Unauthorized) on key " + (candidateIdx + 1) + "/" + totalKeys + ". Skipping key...");
                        keyFailedWithAuth = true;
                        break;
                    } else if (responseCode >= 500 && responseCode <= 599) {
                        System.err.println("[Gemini] HTTP " + responseCode + " (Server Error) on key " + (candidateIdx + 1) + "/" + totalKeys + ". Trying fallback model/key...");
                        keyFailedWithQuotaOrTransient = true;
                    } else if (responseCode == 400) {
                        System.err.println("[Gemini BUG] HTTP 400 Bad Request. Prompt construction error or invalid payload format. Application bug — stopping key retries.");
                        return null;
                    } else {
                        System.err.println("[Gemini] Model " + modelName + " returned HTTP " + responseCode);
                    }
                } catch (java.net.SocketTimeoutException | java.net.ConnectException netEx) {
                    System.err.println("[Gemini] Network error on key " + (candidateIdx + 1) + "/" + totalKeys + " (" + netEx.getMessage() + "). Trying next key...");
                    keyFailedWithQuotaOrTransient = true;
                    break;
                } catch (Exception ex) {
                    System.err.println("[Gemini BUG] Unexpected exception during request on key " + (candidateIdx + 1) + "/" + totalKeys + ": " + ex.getMessage());
                    ex.printStackTrace();
                    return null; // Application bug: do not retry another key
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }

            if (keyFailedWithQuotaOrTransient || keyFailedWithAuth) {
                long cooldownUntil = System.currentTimeMillis() + COOLDOWN_MILLIS;
                disabledUntilMap.put(currentKey, cooldownUntil);
                currentKeyIndex.compareAndSet(candidateIdx, (candidateIdx + 1) % totalKeys);
            }
        }

        System.err.println("[Gemini]\nAll configured API keys exhausted.\nTopic marked AI_UNAVAILABLE.");
        return null;
    }

    private EnrichedTopicResult parseSingleJsonResponse(String rawResponseText, String topicTitle) {
        String cleanJson = rawResponseText.trim();

        if (cleanJson.startsWith("```")) {
            int firstNewline = cleanJson.indexOf("\n");
            int lastBackticks = cleanJson.lastIndexOf("```");
            if (firstNewline != -1 && lastBackticks != -1 && lastBackticks > firstNewline) {
                cleanJson = cleanJson.substring(firstNewline + 1, lastBackticks).trim();
            }
        }

        try {
            JsonNode rootNode = objectMapper.readTree(cleanJson);

            String summary = rootNode.path("summary").asText("");
            String contextBridge = rootNode.path("contextBridge").asText("");

            if (!contextBridge.isBlank() && !summary.startsWith("> **Context Bridge**")) {
                summary = "> **Context Bridge**: " + contextBridge + "\n\n" + summary;
            }

            JsonNode knowledgePackNode = rootNode.path("knowledgePack");
            String knowledgePackJson = knowledgePackNode.isMissingNode() ? "{}" : objectMapper.writeValueAsString(knowledgePackNode);

            JsonNode relatedConceptsNode = rootNode.path("relatedConcepts");
            String relatedConceptsJson = relatedConceptsNode.isMissingNode() ? "[]" : objectMapper.writeValueAsString(relatedConceptsNode);

            JsonNode explorationQuestionsNode = rootNode.path("explorationQuestions");
            String curiosityPathsJson = explorationQuestionsNode.isMissingNode() ? "[]" : objectMapper.writeValueAsString(explorationQuestionsNode);

            JsonNode quickCheckNode = rootNode.path("quickCheck");
            ObjectNode teachingObj = objectMapper.createObjectNode();
            if (!quickCheckNode.isMissingNode()) {
                teachingObj.set("quickCheck", quickCheckNode);
            }
            String teachingPlanJson = objectMapper.writeValueAsString(teachingObj);

            int readingTime = Math.max(1, summary.split("\\s+").length / 200);

            System.out.println("[Gemini] JSON Parsing Successful.");
            System.out.println("[Gemini] Summary Length: " + summary.length() + " chars | Key Ideas: " + (knowledgePackNode.path("keyIdeas").isArray() ? knowledgePackNode.path("keyIdeas").size() : 0));

            return new EnrichedTopicResult(
                    summary,
                    knowledgePackJson,
                    teachingPlanJson,
                    curiosityPathsJson,
                    relatedConceptsJson,
                    readingTime
            );
        } catch (Exception parseEx) {
            System.err.println("[Gemini BUG] Failed to parse JSON response for '" + topicTitle + "': " + parseEx.getMessage());
            System.err.println("RAW RESPONSE:\n" + cleanJson);
            return null; // Return null so application bug is handled safely without retrying another key
        }
    }
}
