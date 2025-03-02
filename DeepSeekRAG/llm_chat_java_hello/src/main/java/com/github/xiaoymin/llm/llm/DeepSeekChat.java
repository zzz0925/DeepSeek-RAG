package com.github.xiaoymin.llm.llm;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.xiaoymin.llm.config.LLmProperties;
import com.google.api.Usage;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
/**
 * @author Peiyao Zhao>
 * 2025/03/01 18:35
 * @since llm_chat_java_hello
 */
@Component
@AllArgsConstructor
@Slf4j
public class DeepSeekChat {
    //private final String apiKey;
    private final ObjectMapper mapper = new ObjectMapper();
    final LLmProperties lLmProperties;
    private final List<Message> messageHistory = new ArrayList<>();
    // 最大保留对话轮次（按 user+assistant 为一轮）
    private static final int MAX_HISTORY = 5;

    public List<Message> getMessageHistory() {
        return messageHistory;
    }
    public Message getNewMessage(String role, String content){
        return new Message(role, content);
    }

    public String getApiKey(){
        String apiKey= lLmProperties.getDSKey();
        if (StrUtil.isBlank(apiKey)){
            apiKey=System.getenv("CHAT2CMD_KEY_ZP");
        }
        return apiKey;
    }
    public void chat(String prompt) {
        try {
            // 自动清理历史记录（保留最近的N轮对话）
            trimHistory();
            // 1. 配置 OkHttpClient
            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(20000, TimeUnit.MILLISECONDS)
                    .readTimeout(20000, TimeUnit.MILLISECONDS)
                    .writeTimeout(20000, TimeUnit.MILLISECONDS)
                    .addInterceptor(chain -> {
                        Request original = chain.request();
                        Request request = original.newBuilder()
                                .header("Authorization", "Bearer " + this.getApiKey())
                                .build();
                        return chain.proceed(request);
                    })
                    .build();
            // 2. 构建请求体
            /*ChatRequest requestBody = new ChatRequest(
                    "deepseek-r1",
                    new Message[] { new Message("user", prompt) },
                    true
            );*/
            ChatRequest requestBody = new ChatRequest(
                    "deepseek-r1",
                    new ArrayList<>(messageHistory),
                    true
            );

            String jsonBody = mapper.writeValueAsString(requestBody);

            // 3. 创建 SSE 请求
            Request request = new Request.Builder()
                    .url("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions")
                    .post(RequestBody.create(jsonBody, MediaType.get("application/json")))
                    .build();


            // 4. 创建事件监听器
            CountDownLatch latch = new CountDownLatch(1);
            EventSource.Factory factory = EventSources.createFactory(okHttpClient);
            EventSource eventSource = factory.newEventSource(request, new EventSourceListener() {
                private final StringBuilder reasoningContent = new StringBuilder();
                private final StringBuilder answerContent = new StringBuilder();
                private boolean isAnswering = false;

                @Override
                public void onOpen(EventSource eventSource, Response response) {
                    System.out.println("\n" + "=".repeat(20) + "思考过程" + "=".repeat(20) + "\n");
                }

                @Override
                public void onEvent(EventSource eventSource, String id, String type, String data) {
                    try {
                        if ("[DONE]".equals(data)) {
                            // 将完整回复添加到历史记录
                            messageHistory.add(new Message("assistant", answerContent.toString()));
                            System.out.println("\n" + "=".repeat(20) + "完整思考过程" + "=".repeat(20));
                            System.out.println(reasoningContent);
                            System.out.println("=".repeat(20) + "完整回复" + "=".repeat(20));
                            System.out.println(answerContent);
                            return;
                        }

                        ChatChunk chunk = mapper.readValue(data, ChatChunk.class);
                        Delta delta = chunk.choices[0].delta;

                        // 处理 reasoning_content
                        if (delta.reasoning_content != null && !delta.reasoning_content.isEmpty()) {
                            System.out.print(delta.reasoning_content);
                            reasoningContent.append(delta.reasoning_content);
                        }

                        // 处理 content
                        if (delta.content != null && !delta.content.isEmpty()) {
                            if (!isAnswering) {
                                System.out.println("\n" + "=".repeat(20) + "完整回复" + "=".repeat(20) + "\n");
                                isAnswering = true;
                            }
                            System.out.print(delta.content);
                            answerContent.append(delta.content);
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(EventSource eventSource, Throwable t, Response response) {
                    t.printStackTrace();
                    latch.countDown();
                }

                @Override
                public void onClosed(EventSource eventSource) {
                    latch.countDown();
                }
            });

            latch.await(); // 等待流结束
        } catch (Exception e) {
            System.err.println("llm-chat异常：" + e.getMessage());
        }
    }


    // 清理历史记录（保留最近N轮对话）
    private void trimHistory() {
        int totalMessages = messageHistory.size();
        int messagesToKeep = Math.min(totalMessages, MAX_HISTORY * 2); // 每轮含user+assistant
        if (totalMessages > messagesToKeep) {
            messageHistory.subList(0, totalMessages - messagesToKeep).clear();
        }
    }

    /*// 请求体结构
    static class ChatRequest {
        public String model;
        public Message[] messages;
        public boolean stream;

        public ChatRequest(String model, Message[] messages, boolean stream) {
            this.model = model;
            this.messages = messages;
            this.stream = stream;
        }
    }*/
    //支持多轮对话
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ChatRequest {
        public String model;
        public List<Message> messages;
        public boolean stream;

        public ChatRequest(String model, List<Message> messages, boolean stream) {
            this.model = model;
            this.messages = messages;
            this.stream = stream;
        }
    }

    static class Message {
        public String role;
        public String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    // 修改后的数据类定义
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ChatChunk {
        public String id;
        public String object;
        public long created;
        public String model;
        public String system_fingerprint;
        public Choice[] choices;
        public Usage usage;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Choice {
        public Delta delta;
        public String finish_reason;
        public int index;
        public Object logprobs;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Delta {
        public String reasoning_content;
        public String content;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Usage {
        public int prompt_tokens;
        public int completion_tokens;
        public int total_tokens;
    }
}
