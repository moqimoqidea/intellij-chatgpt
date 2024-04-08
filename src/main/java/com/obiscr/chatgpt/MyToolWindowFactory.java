package com.obiscr.chatgpt;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import com.obiscr.chatgpt.message.ChatGPTBundle;
import com.obiscr.chatgpt.settings.OpenAISettingsState;
import com.obiscr.chatgpt.ui.action.SettingAction;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Wuzi
 */
public class MyToolWindowFactory implements ToolWindowFactory {

    public static final Key<Object> ACTIVE_CONTENT = Key.create("ActiveContent");

    public static final String CHATGPT_CONTENT_NAME = "ChatGPT";

    /**
     * Create the tool window content.
     *
     * @param project    current project
     * @param toolWindow current tool window
     */
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        ContentFactory contentFactory = ContentFactory.getInstance();

        ChatGPTToolWindow chatGPTToolWindow = new ChatGPTToolWindow(project);
        Content chatGpt = contentFactory.createContent(chatGPTToolWindow.getContent(), CHATGPT_CONTENT_NAME, false);
        chatGpt.setCloseable(false);

        OpenAISettingsState settingsState = OpenAISettingsState.getInstance();
        Map<Integer, String> contentSort = settingsState.contentOrder;

        toolWindow.getContentManager().addContent(chatGpt);

        // Set the default component. It require the 1st container
        String firstContentName = contentSort.get(1);
        if (firstContentName.equals(CHATGPT_CONTENT_NAME)) {
            project.putUserData(ACTIVE_CONTENT, chatGPTToolWindow.getPanel());
        } else {
            throw new RuntimeException("Error content name, content name must be one of ChatGPT, GPT-3.5-Turbo, Online ChatGPT");
        }

        // Add the selection listener
        toolWindow.addContentManagerListener(new ContentManagerListener() {
            @Override
            public void selectionChanged(@NotNull ContentManagerEvent event) {
                String displayName = event.getContent().getDisplayName();
                if (CHATGPT_CONTENT_NAME.equals(displayName)) {
                    project.putUserData(ACTIVE_CONTENT,chatGPTToolWindow.getPanel());
                }
            }
        });

        List<AnAction> actionList = new ArrayList<>();
        actionList.add(new SettingAction(ChatGPTBundle.message("action.settings")));
        toolWindow.setTitleActions(actionList);
    }

}
