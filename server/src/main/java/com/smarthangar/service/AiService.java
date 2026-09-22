package com.smarthangar.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AiService {

        private final ObjectMapper mapper = new ObjectMapper();

        private final HttpClient http = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();

        @Value("${smarthangar.ai.api-key:}")
        private String apiKey;

        @Value("${smarthangar.ai.model:openrouter/free}")
        private String model;

        @Value("${smarthangar.ai.base-url:https://openrouter.ai/api/v1/chat/completions}")
        private String baseUrl;

        @Value("${smarthangar.ai.allow-fallback:true}")
        private boolean allowFallback;

        public Map<String, Object> status() {
                Map<String, Object> status = new LinkedHashMap<>();

                boolean configured = apiKey != null && !apiKey.isBlank();

                status.put("configured", configured);
                status.put("provider", "OpenRouter");
                status.put("model", model);
                status.put("fallbackEnabled", allowFallback);
                status.put("mode", configured ? "LIVE" : "DEMO_FALLBACK");

                return status;
        }

        public Map<String, Object> generateMaintenanceDraft(
                        Map<String, Object> discrepancy,
                        Map<String, Object> aircraft,
                        String additionalPrompt) {

                if (apiKey == null || apiKey.isBlank()) {
                        return fallback(
                                        discrepancy,
                                        aircraft,
                                        "SMARTHANGAR_AI_API_KEY is not configured");
                }

                try {
                        String input = buildInput(
                                        discrepancy,
                                        aircraft,
                                        additionalPrompt);

                        String systemPrompt = """
                                        You are SmartHangar AI, an aircraft-maintenance documentation assistant.

                                        Your job is to help a qualified aircraft maintainer create a documentation and triage draft.

                                        Important safety rules:

                                        - Do not claim an aircraft is safe for flight.
                                        - Do not approve an aircraft for return to service.
                                        - Do not claim that maintenance is complete.
                                        - Do not invent technical-order references.
                                        - Do not invent maintenance limits.
                                        - Do not invent measurements.
                                        - Do not invent part numbers.
                                        - Do not invent inspection criteria.
                                        - Do not replace approved technical data.
                                        - Always remind the maintainer to verify the applicable approved technical data.
                                        - Final maintenance decisions must remain with qualified human maintainers.

                                        Return ONLY valid JSON.

                                        Do not include Markdown.
                                        Do not use code fences.
                                        Do not include commentary before or after the JSON.

                                        The JSON must contain exactly these fields:

                                        {
                                          "summary": "string",
                                          "suggestedAction": "string",
                                          "suggestedCodes": ["string"],
                                          "suggestedShop": "string",
                                          "riskLevel": "LOW | MEDIUM | HIGH",
                                          "verificationSteps": ["string"],
                                          "rationale": "string",
                                          "confidence": 0.0
                                        }

                                        confidence must be a number between 0 and 1.
                                        """;

                        Map<String, Object> requestBody = new LinkedHashMap<>();

                        requestBody.put("model", model);

                        requestBody.put(
                                        "messages",
                                        List.of(
                                                        Map.of(
                                                                        "role", "system",
                                                                        "content", systemPrompt),
                                                        Map.of(
                                                                        "role", "user",
                                                                        "content", input)));

                        /*
                         * Ask compatible OpenRouter models to return JSON.
                         *
                         * Some free models may not support this perfectly,
                         * which is why we also validate and parse the response below.
                         */
                        requestBody.put(
                                        "response_format",
                                        Map.of(
                                                        "type", "json_object"));

                        HttpRequest request = HttpRequest.newBuilder()
                                        .uri(URI.create(baseUrl))
                                        .timeout(Duration.ofSeconds(60))
                                        .header(
                                                        "Authorization",
                                                        "Bearer " + apiKey)
                                        .header(
                                                        "Content-Type",
                                                        "application/json")
                                        .header(
                                                        "X-Title",
                                                        "SmartHangar")
                                        .POST(
                                                        HttpRequest.BodyPublishers.ofString(
                                                                        mapper.writeValueAsString(requestBody)))
                                        .build();

                        HttpResponse<String> response = http.send(
                                        request,
                                        HttpResponse.BodyHandlers.ofString());

                        if (response.statusCode() < 200
                                        || response.statusCode() >= 300) {

                                String reason = "OpenRouter returned HTTP "
                                                + response.statusCode()
                                                + ": "
                                                + response.body();

                                if (allowFallback) {
                                        return fallback(
                                                        discrepancy,
                                                        aircraft,
                                                        reason);
                                }

                                throw new IllegalStateException(reason);
                        }

                        JsonNode root = mapper.readTree(response.body());

                        JsonNode choices = root.path("choices");

                        if (!choices.isArray()
                                        || choices.isEmpty()) {

                                throw new IllegalStateException(
                                                "OpenRouter response did not contain choices");
                        }

                        String outputText = choices
                                        .path(0)
                                        .path("message")
                                        .path("content")
                                        .asText();

                        if (outputText == null
                                        || outputText.isBlank()) {

                                throw new IllegalStateException(
                                                "OpenRouter returned an empty AI response");
                        }

                        outputText = stripCodeFence(outputText);

                        Map<String, Object> result = mapper.readValue(
                                        outputText,
                                        new TypeReference<Map<String, Object>>() {
                                        });

                        /*
                         * Add SmartHangar metadata after parsing the AI JSON.
                         */
                        result.put(
                                        "provider",
                                        "OPENROUTER");

                        result.put(
                                        "model",
                                        model);

                        result.put(
                                        "live",
                                        true);

                        return result;

                } catch (Exception ex) {

                        if (allowFallback) {
                                return fallback(
                                                discrepancy,
                                                aircraft,
                                                ex.getMessage());
                        }

                        throw new IllegalStateException(
                                        "AI request failed",
                                        ex);
                }
        }

        private String buildInput(
                        Map<String, Object> discrepancy,
                        Map<String, Object> aircraft,
                        String additionalPrompt)
                        throws Exception {

                Map<String, Object> context = new LinkedHashMap<>();

                context.put(
                                "aircraft",
                                aircraft);

                context.put(
                                "discrepancy",
                                discrepancy);

                context.put(
                                "maintainerPrompt",
                                additionalPrompt == null
                                                ? ""
                                                : additionalPrompt);

                return """
                                Review this SmartHangar maintenance record.

                                Create a documentation and triage draft for a qualified aircraft maintainer.

                                Do not invent approved technical data.

                                Do not declare the aircraft safe for flight.

                                Do not approve return to service.

                                Base your response only on the information provided.

                                SmartHangar context:
                                """
                                + mapper.writeValueAsString(context);
        }

        private String stripCodeFence(
                        String text) {

                String trimmed = text.trim();

                if (!trimmed.startsWith("```")) {
                        return trimmed;
                }

                int firstNewline = trimmed.indexOf('\n');

                int lastFence = trimmed.lastIndexOf("```");

                if (firstNewline >= 0
                                && lastFence > firstNewline) {

                        return trimmed
                                        .substring(
                                                        firstNewline + 1,
                                                        lastFence)
                                        .trim();
                }

                return trimmed;
        }

        private Map<String, Object> fallback(
                        Map<String, Object> discrepancy,
                        Map<String, Object> aircraft,
                        String reason) {

                String description = String.valueOf(
                                discrepancy.getOrDefault(
                                                "description",
                                                ""))
                                .toLowerCase();

                String shop = "CREW CHIEF";

                List<String> codes = List.of(
                                "GENERAL",
                                "TECH-DATA-VERIFY",
                                "OPS-CHECK");

                String risk = "MEDIUM";

                if (description.contains("hydraulic")) {

                        shop = "HYDRAULICS";

                        codes = List.of(
                                        "HYD",
                                        "TECH-DATA-VERIFY",
                                        "OPS-CHECK");

                } else if (description.contains("elect")
                                || description.contains("light")) {

                        shop = "ELECTRICAL";

                        codes = List.of(
                                        "ELEC",
                                        "TECH-DATA-VERIFY",
                                        "OPS-CHECK");

                } else if (description.contains("engine")
                                || description.contains("oil")) {

                        shop = "ENGINES";

                        codes = List.of(
                                        "ENG",
                                        "TECH-DATA-VERIFY",
                                        "OPS-CHECK");

                } else if (description.contains("radio")
                                || description.contains("avion")) {

                        shop = "AVIONICS";

                        codes = List.of(
                                        "AVIONICS",
                                        "TECH-DATA-VERIFY",
                                        "OPS-CHECK");
                }

                if ("RED X".equals(
                                String.valueOf(
                                                discrepancy.get("symbol")))) {

                        risk = "HIGH";
                }

                Map<String, Object> result = new LinkedHashMap<>();

                result.put(
                                "summary",
                                "Draft triage for "
                                                + discrepancy.getOrDefault(
                                                                "discrepancy_number",
                                                                "maintenance discrepancy"));

                result.put(
                                "suggestedAction",
                                """
                                                Confirm the reported condition, identify the applicable approved technical data, document the verified corrective action actually performed, and record any required inspection or operational check before a qualified maintainer closes the discrepancy.
                                                """
                                                .trim());

                result.put(
                                "suggestedCodes",
                                codes);

                result.put(
                                "suggestedShop",
                                shop);

                result.put(
                                "riskLevel",
                                risk);

                result.put(
                                "verificationSteps",
                                List.of(
                                                "Verify the discrepancy against the actual aircraft condition.",
                                                "Use the current approved technical data for troubleshooting and corrective action.",
                                                "Record actual findings, parts, actions, inspections, and operational-check results.",
                                                "Require qualified human review before closing the maintenance record."));

                result.put(
                                "rationale",
                                "SmartHangar demo fallback classifies the discrepancy by keywords while preserving human maintenance authority.");

                result.put(
                                "confidence",
                                0.55);

                result.put(
                                "provider",
                                "SMART_HANGAR_DEMO");

                result.put(
                                "model",
                                "deterministic-fallback");

                result.put(
                                "live",
                                false);

                result.put(
                                "fallbackReason",
                                reason == null
                                                ? "Unknown AI provider error"
                                                : reason);

                return result;
        }

        public Map<String, Object> generateCloseoutDraft(
                        Map<String, Object> discrepancy,
                        Map<String, Object> aircraft,
                        String workPerformed) {

                if (apiKey == null || apiKey.isBlank()) {
                        return closeoutFallback(
                                        discrepancy,
                                        aircraft,
                                        workPerformed,
                                        "SMARTHANGAR_AI_API_KEY is not configured");
                }

                try {

                        String systemPrompt = """

                                        You are SmartHangar AI, an aircraft-maintenance documentation assistant.

                                        Your job is to transform short maintainer notes into a clear,
                                        professional corrective-action documentation draft.

                                        The maintainer may use shorthand, abbreviations, incomplete
                                        sentences, or common maintenance terminology.

                                        Expand the shorthand into professional maintenance-record wording
                                        while preserving the meaning of what the maintainer actually stated.

                                        IMPORTANT RULES:

                                        - Do not invent maintenance actions that were not stated or reasonably implied by the maintainer's findings.
                                        - Do not invent inspections.
                                        - Do not invent operational checks.
                                        - Do not invent parts or part numbers.
                                        - Do not invent measurements.
                                        - Do not invent technical-order references.
                                        - Do not claim a discrepancy is corrected unless the input supports it.
                                        - Do not claim the aircraft is safe for flight.
                                        - Do not approve the aircraft for return to service.
                                        - If the maintainer states or clearly indicates that additional work is required,
                                          document that the original discrepancy may be closed as an evaluation/finding
                                          while the additional maintenance is carried forward as a follow-on discrepancy.
                                        - Expand understandable maintenance shorthand into complete sentences.
                                        - Keep the corrective action concise and suitable for a maintenance record.
                                        - Preserve uncertainty when the maintainer's statement is incomplete.

                                        Example:

                                        Maintainer input:
                                        "new panel fab req"

                                        Good output:
                                        "Inspection findings determined that replacement panel fabrication is required.
                                        Further maintenance is required to fabricate and install the replacement panel."

                                        Bad output:
                                        "Removed damaged panel, inspected surrounding structure, fabricated and installed
                                        replacement panel, and completed operational check."

                                        The bad example invents maintenance that the maintainer never stated.

                                        FOLLOW-ON DISCREPANCY LOGIC:

                                        A follow-on discrepancy is appropriate when the maintainer's findings identify
                                        additional maintenance that remains unresolved after the current evaluation,
                                        inspection, troubleshooting, or maintenance action.

                                        You SHOULD recommend a follow-on discrepancy when the maintainer's notes reasonably
                                        indicate that another maintenance action is required.

                                        Examples include:

                                        - troubleshooting determines a component requires replacement
                                        - an inspection identifies additional damage
                                        - evaluation determines further repair is required
                                        - a malfunction is isolated to another component or system
                                        - removal, replacement, repair, fabrication, adjustment, or additional troubleshooting is required
                                        - the current discrepancy documents a symptom or evaluation and the findings establish a more specific maintenance requirement

                                        The maintainer does NOT need to explicitly say "open another discrepancy."

                                        You may infer the need for a follow-on discrepancy from clear maintenance findings.

                                        Example:

                                        Original discrepancy:
                                        "High oil consumption ENG #3"

                                        Maintainer findings:
                                        "Checked oil consumption and determined engine requires replacement."

                                        Appropriate corrective-action draft:
                                        "Evaluated Engine #3 for reported high oil consumption. Findings indicate engine replacement is required."

                                        Appropriate follow-on discrepancy:
                                        "Engine #3 requires removal and replacement."

                                        Another example:

                                        Original discrepancy:
                                        "RH cargo door panel damaged"

                                        Maintainer findings:
                                        "Inspected panel. Damage beyond repair limits. new panel fab req"

                                        Appropriate follow-on discrepancy:
                                        "Fabricate and install replacement RH cargo door panel."

                                        Do NOT create a follow-on discrepancy based on speculation or information that does
                                        not appear in or reasonably follow from the maintainer's stated findings.

                                        Do NOT invent:
                                        - which component failed if it was not identified
                                        - a specific part number
                                        - a technical-order reference
                                        - a measurement or limit
                                        - maintenance that has already been completed
                                        - an operational check that was not stated

                                        When no unresolved follow-on maintenance requirement is identified:
                                        followOnDiscrepancyRecommended must be false
                                        and the other follow-on fields must be empty strings.

                                        When unresolved follow-on maintenance is identified:
                                        followOnDiscrepancyRecommended should be true.

                                        Then:
                                        - followOnDescription must clearly describe the remaining maintenance requirement
                                        - followOnShop should select the most reasonable shop based on the stated system or component
                                        - followOnSymbol should be RED X, RED DASH, or INFORMATIONAL
                                        - do not assign a more severe symbol unless the maintainer's stated findings support it

                                        The follow-on discrepancy is only a recommendation.
                                        A qualified maintainer must review, edit, and approve it before it is created.

                                        Return ONLY valid JSON.

                                        Do not include Markdown.
                                        Do not include code fences.
                                        Do not include commentary.

                                        Return exactly:

                                        {
                                          "correctiveAction": "string",
                                          "verificationReminder": "string",
                                          "confidence": 0.0,
                                          "followOnDiscrepancyRecommended": false,
                                          "followOnDescription": "",
                                          "followOnShop": "",
                                          "followOnSymbol": ""
                                        }

                                        confidence must be a number between 0 and 1.

                                                                                                                        """;

                        Map<String, Object> context = new LinkedHashMap<>();

                        context.put("aircraft", aircraft);
                        context.put("discrepancy", discrepancy);
                        context.put(
                                        "actualWorkPerformed",
                                        workPerformed == null ? "" : workPerformed);

                        Map<String, Object> requestBody = new LinkedHashMap<>();

                        requestBody.put("model", model);

                        requestBody.put(
                                        "messages",
                                        List.of(
                                                        Map.of(
                                                                        "role",
                                                                        "system",
                                                                        "content",
                                                                        systemPrompt),
                                                        Map.of(
                                                                        "role",
                                                                        "user",
                                                                        "content",
                                                                        """
                                                                                        Create the corrective-action draft from this
                                                                                        SmartHangar maintenance context:

                                                                                        %s
                                                                                        """
                                                                                        .formatted(
                                                                                                        mapper.writeValueAsString(
                                                                                                                        context)))));

                        requestBody.put(
                                        "response_format",
                                        Map.of(
                                                        "type",
                                                        "json_object"));

                        HttpRequest request = HttpRequest.newBuilder()
                                        .uri(URI.create(baseUrl))
                                        .timeout(Duration.ofSeconds(60))
                                        .header(
                                                        "Authorization",
                                                        "Bearer " + apiKey)
                                        .header(
                                                        "Content-Type",
                                                        "application/json")
                                        .header(
                                                        "X-Title",
                                                        "SmartHangar")
                                        .POST(
                                                        HttpRequest.BodyPublishers.ofString(
                                                                        mapper.writeValueAsString(requestBody)))
                                        .build();

                        HttpResponse<String> response = http.send(
                                        request,
                                        HttpResponse.BodyHandlers.ofString());

                        if (response.statusCode() < 200
                                        || response.statusCode() >= 300) {

                                String reason = "OpenRouter returned HTTP "
                                                + response.statusCode()
                                                + ": "
                                                + response.body();

                                if (allowFallback) {
                                        return closeoutFallback(
                                                        discrepancy,
                                                        aircraft,
                                                        workPerformed,
                                                        reason);
                                }

                                throw new IllegalStateException(reason);
                        }

                        JsonNode root = mapper.readTree(response.body());

                        JsonNode choices = root.path("choices");

                        if (!choices.isArray()
                                        || choices.isEmpty()) {

                                throw new IllegalStateException(
                                                "OpenRouter response did not contain choices");
                        }

                        String outputText = choices
                                        .path(0)
                                        .path("message")
                                        .path("content")
                                        .asText();

                        if (outputText == null
                                        || outputText.isBlank()) {

                                throw new IllegalStateException(
                                                "OpenRouter returned an empty AI response");
                        }

                        outputText = stripCodeFence(outputText);

                        Map<String, Object> result = mapper.readValue(
                                        outputText,
                                        new TypeReference<Map<String, Object>>() {
                                        });

                        result.put(
                                        "provider",
                                        "OPENROUTER");

                        result.put(
                                        "model",
                                        model);

                        result.put(
                                        "live",
                                        true);

                        return result;

                } catch (Exception ex) {

                        if (allowFallback) {
                                return closeoutFallback(
                                                discrepancy,
                                                aircraft,
                                                workPerformed,
                                                ex.getMessage());
                        }

                        throw new IllegalStateException(
                                        "AI closeout request failed",
                                        ex);
                }
        }

        private Map<String, Object> closeoutFallback(
                        Map<String, Object> discrepancy,
                        Map<String, Object> aircraft,
                        String workPerformed,
                        String reason) {

                String work = workPerformed == null
                                ? ""
                                : workPerformed.trim();

                String normalized = work.toLowerCase();

                String correctiveAction;

                if (work.isBlank()) {

                        correctiveAction = "No corrective-action details were provided. "
                                        + "Maintainer documentation is required before this discrepancy can be closed.";

                } else if (normalized.contains("fab req")
                                || normalized.contains("fabrication req")
                                || normalized.contains("fabrication required")) {

                        correctiveAction = "Replacement component fabrication required. "
                                        + "Discrepancy remains open pending fabrication "
                                        + "and completion of the required maintenance.";

                } else if (normalized.contains("panel")
                                && normalized.contains("replace")) {

                        correctiveAction = "Panel replacement required. "
                                        + "Discrepancy remains open pending completion "
                                        + "of the replacement action.";

                } else if (normalized.contains("ops check")
                                || normalized.contains("op check")) {

                        correctiveAction = "Operational check required following completion "
                                        + "of the documented maintenance action.";

                } else if (normalized.contains("part on order")
                                || normalized.contains("parts on order")
                                || normalized.contains("awaiting part")) {

                        correctiveAction = "Required replacement part is pending availability. "
                                        + "Discrepancy remains open awaiting parts "
                                        + "and completion of maintenance.";

                } else {

                        correctiveAction = "Maintainer reported the following corrective-action information: "
                                        + work
                                        + ". Verify and complete the maintenance documentation "
                                        + "using the applicable approved technical data.";
                }

                Map<String, Object> fallback = new LinkedHashMap<>();

                fallback.put(
                                "correctiveAction",
                                correctiveAction);

                fallback.put(
                                "verificationReminder",
                                "Verify the draft against the maintenance actually performed "
                                                + "and the applicable approved technical data before using it "
                                                + "as an official maintenance entry.");

                fallback.put(
                                "confidence",
                                0.55);

                fallback.put(
                                "provider",
                                "SMART_HANGAR_DEMO");

                fallback.put(
                                "model",
                                "deterministic-closeout-fallback");

                fallback.put(
                                "live",
                                false);

                fallback.put(
                                "fallbackReason",
                                reason == null
                                                ? "Unknown AI provider error"
                                                : reason);
                fallback.put("followOnDiscrepancyRecommended", false);
                fallback.put("followOnDescription", "");
                fallback.put("followOnShop", "");
                fallback.put("followOnSymbol", "");
                return fallback;
        }
}