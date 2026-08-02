import com.airgap.service.GeminiApiService;

public class TestGeminiKeyDiagnostic {
    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("[GEMINI API KEY RESOLUTION DIAGNOSTIC]");
        System.out.println("================================================================================");

        String envKey = System.getenv("GEMINI_API_KEY");
        String propKey = System.getProperty("GEMINI_API_KEY");

        System.out.println("1. System.getenv(\"GEMINI_API_KEY\") != null ? " + (envKey != null));
        System.out.println("   Environment Key Length: " + (envKey != null ? envKey.length() : 0));

        System.out.println("2. System.getProperty(\"GEMINI_API_KEY\") != null ? " + (propKey != null));
        System.out.println("   System Property Key Length: " + (propKey != null ? propKey.length() : 0));

        GeminiApiService service = new GeminiApiService();
        System.out.println("3. GeminiApiService.isConfigured() ? " + service.isConfigured());

        System.out.println("================================================================================");
    }
}
