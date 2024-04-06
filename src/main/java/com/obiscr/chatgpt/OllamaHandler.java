package com.obiscr.chatgpt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.project.Project;
import com.obiscr.chatgpt.core.builder.OfficialBuilder;
import com.obiscr.chatgpt.ui.MainPanel;
import com.obiscr.chatgpt.ui.MessageComponent;
import okhttp3.*;
import okhttp3.internal.http2.StreamResetException;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Stack;
import java.util.concurrent.TimeUnit;

/**
 * @author wuzi
 */
public class OllamaHandler extends AbstractHandler {

    private Project myProject;

    private static final Logger LOG = LoggerFactory.getLogger(OllamaHandler.class);

    private final Stack<String> gpt35Stack = new Stack<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public EventSource handle(MainPanel mainPanel, MessageComponent component, String question) {
        myProject = mainPanel.getProject();
        try {
            String jsonRequestBody = "{\n" +
                    "    \"model\": \"qwen:0.5b\",\n" +
                    "    \"messages\": [\n" +
                    "        {\n" +
                    "            \"role\": \"user\",\n" +
                    "            \"content\": \"" + question + "\"\n" +
                    "        }\n" +
                    "    ]\n" +
                    "}";

            Request request = new Request.Builder()
                    .url("http://localhost:11434/v1/chat/completions")
                    .header("Accept", "text/event-stream")
                    // 这里直接使用提供的 JSON 数据
                    .post(RequestBody.create(jsonRequestBody.getBytes(StandardCharsets.UTF_8),
                            MediaType.parse("application/json")))
                    .build();
            OkHttpClient httpClient = new OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String string = response.body().string();
                    LOG.info("response: {}", string);
                    mainPanel.getContentPanel().getMessages().add(OfficialBuilder.assistantMessage(string));
                    component.setContent(string);
                }

                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    LOG.error("error: {}", e.getMessage());
                }
            });

            return null;
        } catch (Exception e) {
            LOG.error("ChatGPT handle Exception, error: {}", e.getMessage());
        } finally {
            mainPanel.getExecutorService().shutdown();
        }
        return null;
    }
}
