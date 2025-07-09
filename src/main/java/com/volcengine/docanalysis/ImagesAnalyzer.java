package com.volcengine.docanalysis;

import com.volcengine.ark.runtime.model.Usage;
import com.volcengine.ark.runtime.model.completion.chat.*;
import com.volcengine.ark.runtime.service.ArkService;
import io.reactivex.Flowable;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ImagesAnalyzer {

    private static final String baseUrl = "https://ark.cn-beijing.volces.com/api/v3";

    public void analyze(List<String> fileNames, String resultFileName, String model, String apiKey) throws RuntimeException, IOException {
        log( "----- start to analyze images -----");

        ConnectionPool connectionPool = new ConnectionPool(5, 1, TimeUnit.SECONDS);
        Dispatcher dispatcher = new Dispatcher();
        ArkService service = ArkService.builder().dispatcher(dispatcher).connectionPool(connectionPool).baseUrl(baseUrl).apiKey(apiKey).build();

        List<String> base64Images = encodeToBase64(fileNames);
        List<ChatMessage> messages = constructMessages(base64Images);

        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                // 您可以前往 在线推理页 创建接入点后进行使用
                .model(model)
                .messages(messages)
                .temperature(0.01)
                .topP(0.7)
                .maxTokens(16384)
                .streamOptions(ChatCompletionRequest.ChatCompletionRequestStreamOptions.of(true))
                .thinking(new ChatCompletionRequest.ChatCompletionRequestThinking("disabled"))
                .build();


        StringBuilder stringBuilder = new StringBuilder();
        List<Usage> usages = new ArrayList<>();
        Flowable<ChatCompletionChunk> flowable = service.streamChatCompletion(chatCompletionRequest);
        flowable.doOnError(Throwable::printStackTrace)
                .blockingForEach(
                        choice -> {
                            if (!choice.getChoices().isEmpty()) {
                                if (choice.getChoices().getFirst().getFinishReason() != null) {
                                    String requestId = choice.getId();
                                    System.out.println("\nrequestId:" + requestId);
                                } else {
                                    stringBuilder.append(choice.getChoices().getFirst().getMessage().getContent().toString());
                                }
                            } else {
                                usages.add(choice.getUsage());
                            }
                        }
                );
        Usage usage = usages.getFirst();
        String usageInfo = String.format("Usage information  prompt tokens: %d; completion tokens: %d total tokens: %d", usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
        log(usageInfo);
        service.shutdownExecutor();
        File textFile = new File(resultFileName);
        textFile.createNewFile();

        IOUtils.write(stringBuilder.toString(), FileUtils.openOutputStream(textFile), StandardCharsets.UTF_8);
        log("Result is saved to " + textFile);
    }


    private List<String> encodeToBase64(List<String> fileNames) {
        ExecutorService executor = Executors.newFixedThreadPool(20);
        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (String fileName : fileNames) {

            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return Base64.getEncoder().encodeToString(FileUtils.readFileToByteArray(new File(fileName)));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, executor);
            futures.add(future);
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        // 使用自定义的线程池
        executor.shutdown();
        List<String> base64Images = new ArrayList<>();
        futures.forEach(f -> {
            try {
                base64Images.add(f.get());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return base64Images;
    }

    private List<ChatMessage> constructMessages(List<String> base64Images) {
        final List<ChatMessage> messages = new ArrayList<>();
        //add system prompt
        final ChatMessage sysMessage = ChatMessage.builder().role(ChatMessageRole.SYSTEM).content("你是豆包AI助手，善于按用户的要求解析文档").build();
        messages.add(sysMessage);

        //add user message
        final List<ChatCompletionContentPart> multiParts = new ArrayList<>();
        multiParts.add(ChatCompletionContentPart.builder().type("text").text(
                """
                        请提取出图片中的所有文字内容，并根据原图中各项内容的结构尽量保持输出类似的结构，可以使用Markdown、html等标签来体现结构
                        1. 注意段落的准确性
                        2. 文档中可能存在合并单元格的表格，要正确解析并用Markdown或html恰当表示
                        """
        ).build());

        for (String base64Image : base64Images) {
            multiParts.add(ChatCompletionContentPart.builder().type("image_url").imageUrl(
                    new ChatCompletionContentPart.ChatCompletionContentPartImageURL(
                            "data:image/jpeg;base64,"+base64Image
                    )
            ).build());
        }

        final ChatMessage userMessage = ChatMessage.builder().role(ChatMessageRole.USER)
                .multiContent(multiParts).build();
        messages.add(userMessage);
        return messages;
    }

    private void log(String content) {
        Logger.log(this.getClass().getSimpleName(), content);
    }

}
