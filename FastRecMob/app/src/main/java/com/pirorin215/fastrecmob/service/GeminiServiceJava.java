package com.pirorin215.fastrecmob.service;

import android.content.Context;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GoogleSearchRetrieval;
import com.google.genai.types.Tool;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Candidate;
import com.google.genai.types.GroundingMetadata;
import com.google.genai.types.GroundingChunk;
import com.google.genai.types.GroundingChunkWeb;
import com.google.genai.types.GroundingChunkRetrievedContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class GeminiServiceJava {
    private final Context context;
    private final String apiKey;
    private final String modelName;
    private Client client;

    public GeminiServiceJava(Context context, String apiKey, String modelName) {
        this.context = context;
        this.apiKey = apiKey;
        this.modelName = modelName != null ? modelName : "gemini-1.5-flash";
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            try {
                this.client = Client.builder()
                        .apiKey(apiKey)
                        .build();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public String generateResponseSync(String transcription) throws Exception {
        if (client == null) {
            throw new IllegalStateException("Gemini API key not set");
        }

        String prompt = "以下の発言内容に対して、役立つ応答を簡潔に生成してください：\n\n" +
                transcription +
                "\n\n応答は100文字以内で実的にしてください。";

        GenerateContentConfig config = GenerateContentConfig.builder()
                .tools(Collections.singletonList(
                        Tool.builder()
                                .googleSearchRetrieval(GoogleSearchRetrieval.builder().build())
                                .build()
                ))
                .build();

        // Use the field 'models' as discovered in reflection
        GenerateContentResponse response = client.models.generateContent(modelName, prompt, config);

        String baseText = response.text();
        if (baseText == null) baseText = "応答が生成されませんでした";

        List<String> sourceLinks = new ArrayList<>();
        Optional<List<Candidate>> candidatesOpt = response.candidates();
        if (candidatesOpt.isPresent()) {
            for (Candidate candidate : candidatesOpt.get()) {
                Optional<GroundingMetadata> metadataOpt = candidate.groundingMetadata();
                if (metadataOpt.isPresent()) {
                    Optional<List<GroundingChunk>> chunksOpt = metadataOpt.get().groundingChunks();
                    if (chunksOpt.isPresent()) {
                        for (GroundingChunk chunk : chunksOpt.get()) {
                            Optional<GroundingChunkWeb> webOpt = chunk.web();
                            if (webOpt.isPresent()) {
                                Optional<String> titleOpt = webOpt.get().title();
                                String title = titleOpt.isPresent() ? titleOpt.get() : "Source";
                                Optional<String> uriOpt = webOpt.get().uri();
                                String uri = uriOpt.isPresent() ? uriOpt.get() : "";
                                if (!uri.trim().isEmpty()) {
                                    sourceLinks.add(title + ": " + uri);
                                }
                            }
                            Optional<GroundingChunkRetrievedContext> contextOpt = chunk.retrievedContext();
                            if (contextOpt.isPresent()) {
                                Optional<String> titleOpt = contextOpt.get().title();
                                String title = titleOpt.isPresent() ? titleOpt.get() : "Context";
                                Optional<String> uriOpt = contextOpt.get().uri();
                                String uri = uriOpt.isPresent() ? uriOpt.get() : "";
                                if (!uri.trim().isEmpty()) {
                                    sourceLinks.add(title + ": " + uri);
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!sourceLinks.isEmpty()) {
            StringBuilder sb = new StringBuilder(baseText);
            sb.append("\n\n【出典】\n");
            List<String> distinctLinks = sourceLinks.stream().distinct().collect(Collectors.toList());
            for (String link : distinctLinks) {
                sb.append("- ").append(link).append("\n");
            }
            return sb.toString().trim();
        } else {
            return baseText;
        }
    }

    public void verifyApiKeySync() throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key is empty");
        }
        if (client == null) {
            throw new IllegalStateException("Model not initialized");
        }
        client.models.generateContent(modelName, "test", null);
    }

    public void verifyModelSync(String modelName) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key is empty");
        }
        if (modelName == null || modelName.trim().isEmpty()) {
            throw new IllegalArgumentException("Model name is empty");
        }
        Client testClient = Client.builder()
                .apiKey(apiKey)
                .build();
        testClient.models.generateContent(modelName, "test", null);
    }
}
