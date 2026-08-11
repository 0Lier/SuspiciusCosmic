package com.cosmicraft.suspiciuscosmic;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class DiscordWebhook {
    private final String url;

    public DiscordWebhook(String url) {
        this.url = url;
    }

    public void sendEmbed(String title, String description, int color) {
        if (url == null || url.isEmpty() || url.equals("YOUR_WEBHOOK_URL_HERE")) {
            return;
        }

        try {
            URL urlObj = new URL(this.url);
            HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            String jsonPayload = buildJsonEmbed(title, description, color);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                System.err.println("[SuspiciusCosmic] Error sending Discord Webhook: HTTP " + responseCode);
            }
            connection.disconnect();
        } catch (Exception e) {
            System.err.println("[SuspiciusCosmic] Exception while sending Discord Webhook: " + e.getMessage());
        }
    }

    private String buildJsonEmbed(String title, String description, int color) {
        // Simple JSON builder for Discord embed
        String safeTitle = escapeJson(title);
        String safeDesc = escapeJson(description);
        return "{\n" +
               "  \"embeds\": [\n" +
               "    {\n" +
               "      \"title\": \"" + safeTitle + "\",\n" +
               "      \"description\": \"" + safeDesc + "\",\n" +
               "      \"color\": " + color + "\n" +
               "    }\n" +
               "  ]\n" +
               "}";
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\b", "\\b")
                   .replace("\f", "\\f")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}
