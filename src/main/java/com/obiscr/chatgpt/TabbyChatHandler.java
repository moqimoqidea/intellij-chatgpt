package com.obiscr.chatgpt;

import com.intellij.openapi.project.Project;
import com.obiscr.chatgpt.core.parser.OfficialParser;
import com.obiscr.chatgpt.ui.MainPanel;
import com.obiscr.chatgpt.ui.MessageComponent;
import com.obiscr.chatgpt.util.StringUtil;
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

/**
 * @author wuzi
 */
public class TabbyChatHandler extends AbstractHandler {

    private Project myProject;

    private static final Logger LOG = LoggerFactory.getLogger(TabbyChatHandler.class);

    public EventSource handle(MainPanel mainPanel, MessageComponent component, String question) {
        myProject = mainPanel.getProject();
        try {
            String jsonRequestBody = "{\n" +
                    "  \"messages\": [\n" +
                    "    {\n" +
                    "      \"role\": \"user\",\n" +
                    "      \"content\": \"" + question + "\"\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}";

            String tabbyChatUrl = System.getenv("TABBY_CHAT_URL");
            LOG.info("TabbyChat: tabbyChatUrl = {}", tabbyChatUrl);

            Request request = new Request.Builder()
                    .url(tabbyChatUrl + "/v1beta/chat/completions")
                    // 这里直接使用提供的 JSON 数据
                    .post(RequestBody.create(jsonRequestBody.getBytes(StandardCharsets.UTF_8),
                            MediaType.parse("application/json")))
                    .build();

            OkHttpClient httpClient = new OkHttpClient();
            EventSourceListener listener = new EventSourceListener() {

                boolean handler = false;

                @Override
                public void onOpen(@NotNull EventSource eventSource, @NotNull Response response) {
                    LOG.info("TabbyChat: conversation open. Url = {}",eventSource.request().url());
                }

                @Override
                public void onClosed(@NotNull EventSource eventSource) {
                    LOG.info("TabbyChat: conversation close. Url = {}",eventSource.request().url());
                    if (!handler) {
                        component.setContent("Connection to remote server failed. There are usually several reasons for this:<br />1. Request too frequently, please try again later.<br />2. It may be necessary to set up a proxy to request.");
                    }
                    mainPanel.aroundRequest(false);
                    component.scrollToBottom();
                    mainPanel.getExecutorService().shutdown();
                }

                @Override
                public void onEvent(@NotNull EventSource eventSource, @Nullable String id, @Nullable String type, @NotNull String data) {
                    handler = true;
                    if (StringUtil.isEmpty(data)) {
                        return;
                    }
                    if (data.contains("\"finish_reason\":\"stop\"")) {
                        return;
                    }
                    try {
                        OfficialParser.ParseResult parseResult = OfficialParser.parseTabbyChat(myProject, component, data);
                        component.setSourceContent(parseResult.getSource());
                        component.setContent(parseResult.getHtml());
                    } catch (Exception e) {
                        LOG.error("TabbyChat: Parse response error, e={}, message={}", e, e.getMessage());
                        component.setContent(e.getMessage());
                    } finally {
                        mainPanel.getExecutorService().shutdown();
                    }
                }

                @Override
                public void onFailure(@NotNull EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
                    if (t != null) {
                        if (t instanceof StreamResetException) {
                            LOG.info("TabbyChat: Request failure, throwable StreamResetException, cause: {}", t.getMessage());
                            component.setContent("Request failure, cause: " + t.getMessage());
                            mainPanel.aroundRequest(false);
                            t.printStackTrace();
                            return;
                        }
                        LOG.info("TabbyChat: conversation failure. Url={}, response={}, errorMessage={}",eventSource.request().url(), response, t.getMessage());
                        component.setContent("Response failure, cause: " + t.getMessage() + ", please try again. <br><br> Tips: if proxy is enabled, please check if the proxy server is working.");
                        mainPanel.aroundRequest(false);
                        t.printStackTrace();
                    } else {
                        String responseString = "";
                        if (response != null) {
                            try {
                                responseString = response.body().string();
                            } catch (IOException e) {
                                mainPanel.aroundRequest(false);
                                LOG.error("TabbyChat: parse response error, cause: {}", e.getMessage());
                                component.setContent("Response failure, cause: " + e.getMessage());
                                throw new RuntimeException(e);
                            }
                        }
                        LOG.info("TabbyChat: conversation failure. Url={}, response={}",eventSource.request().url(), response);
                        component.setContent("Response failure, please try again. Error message: " + responseString);
                    }
                    mainPanel.aroundRequest(false);
                    component.scrollToBottom();
                    mainPanel.getExecutorService().shutdown();
                }
            };
            EventSource.Factory factory = EventSources.createFactory(httpClient);
            return factory.newEventSource(request, listener);
        } catch (Exception e) {
            LOG.error("TabbyChat handle Exception, error: {}", e.getMessage());
        } finally {
            mainPanel.getExecutorService().shutdown();
        }
        return null;
    }
}
