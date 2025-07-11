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
import java.util.*;
import java.util.concurrent.*;

public class ImagesAnalyzer {

    private static final String baseUrl = "https://ark.cn-beijing.volces.com/api/v3";
    private final List<Usage> usageList = new ArrayList<>();


    public void analyze(List<String> fileNames, String resultFileName, String model, String apiKey, int batchSize) throws RuntimeException, IOException, ExecutionException, InterruptedException {
        log("----- start to analyze images -----");

        ConnectionPool connectionPool = new ConnectionPool(5, 1, TimeUnit.SECONDS);
        Dispatcher dispatcher = new Dispatcher();
        ArkService service = ArkService.builder().dispatcher(dispatcher).connectionPool(connectionPool).baseUrl(baseUrl).apiKey(apiKey).build();

        List<String> base64Images = encodeToBase64(fileNames);

        int batchNum = base64Images.size() / batchSize;

        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i <= batchNum; i++) {
            if ((i + 1) * batchSize > base64Images.size()) {
                if (i * batchSize == base64Images.size()) {
                    break;
                }
                List<String> subList = base64Images.subList(i * batchSize, base64Images.size());
                batches.add(subList);
            } else {
                List<String> subList = base64Images.subList(i * batchSize, (i + 1) * batchSize);
                batches.add(subList);
            }
        }

        ExecutorService executor = Executors.newFixedThreadPool(batchNum+1);
        List<Future<String>> futures = new ArrayList<>();
        batches.forEach(batch -> {
            Future<String> f = executor.submit(() -> {
                List<ChatMessage> messages = constructMessages(batch);
                return analyzeBatch(model, messages, service);
            });
            futures.add(f);
        });
        executor.shutdown();
        StringBuilder resultBuffer = new StringBuilder();
        for (Future<String> future : futures) {
            String result = future.get();
            resultBuffer.append(result);
            resultBuffer.append("\n");
        }

        service.shutdownExecutor();
        File textFile = new File(resultFileName);
        textFile.createNewFile();

        IOUtils.write(resultBuffer.toString(), FileUtils.openOutputStream(textFile), StandardCharsets.UTF_8);
        log("Result is saved to " + textFile);

        long promptTokens = usageList.stream().mapToLong(Usage::getPromptTokens).reduce(Long::sum).getAsLong();
        long completionTokens = usageList.stream().mapToLong(Usage::getCompletionTokens).reduce(Long::sum).getAsLong();
        long totalTokens = usageList.stream().mapToLong(Usage::getTotalTokens).reduce(Long::sum).getAsLong();
        String usageInfo = String.format("Total usage information for the file: prompt tokens %d; completion tokens %d total tokens %d", promptTokens, completionTokens, totalTokens);
        log(usageInfo);
    }

    private String analyzeBatch(String model, List<ChatMessage> messages, ArkService service) {
        int maxTokens = model.startsWith("doubao-seed-1.6") ? 32768 : 16384;
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                // 您可以前往 在线推理页 创建接入点后进行使用
                .model(model)
                .messages(messages)
                .temperature(0.01)
                .topP(0.7)
                .maxTokens(maxTokens)
                .streamOptions(ChatCompletionRequest.ChatCompletionRequestStreamOptions.of(true))
                .thinking(new ChatCompletionRequest.ChatCompletionRequestThinking("disabled"))
                .build();


        StringBuilder result = new StringBuilder();
        List<Usage> usages = new ArrayList<>();
        Flowable<ChatCompletionChunk> flowable = service.streamChatCompletion(chatCompletionRequest);
        flowable.doOnError(Throwable::printStackTrace)
                .blockingForEach(
                        choice -> {
                            if (!choice.getChoices().isEmpty()) {
                                if (choice.getChoices().getFirst().getFinishReason() != null) {
                                    String requestId = choice.getId();
                                    log("requestId:" + requestId);
                                } else {
                                    result.append(choice.getChoices().getFirst().getMessage().getContent().toString());
                                }
                            } else {
                                usages.add(choice.getUsage());
                            }
                        }
                );
        Usage usage = usages.getFirst();
        usageList.add(usage);
        String usageInfo = String.format("Usage information of this batch: prompt tokens %d; completion tokens %d total tokens %d", usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
        log(usageInfo);
        return result.toString();
    }


    private List<String> encodeToBase64(List<String> fileNames) {
        ExecutorService executor = Executors.newFixedThreadPool(20);
        List<Future<String>> futures = new ArrayList<>();

        try {
            // 提交所有文件处理任务
            for (String fileName : fileNames) {
                futures.add(executor.submit(() -> 
                    Base64.getEncoder().encodeToString(
                        FileUtils.readFileToByteArray(new File(fileName)))));
            }

            // 收集处理结果
            List<String> base64Images = new ArrayList<>(futures.size());
            for (Future<String> future : futures) {
                base64Images.add(future.get());
            }
            return base64Images;

        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("图片转base64失败", e);
        } finally {
            executor.shutdown();
        }
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
请严格按输入图片的顺序提取出图片中的所有文字内容，并根据原图中各项内容的结构尽量保持输出类似的结构，可以使用Markdown、html等标签来体现结构
1. 注意段落的准确性
2. 遇到空白的图片就继续解析下一张图片
3. 文档中可能存在合并单元格的表格，要正确解析并用Markdown或html恰当表示
4. 不提取页码
                        """
        ).build());

        for (String base64Image : base64Images) {
            multiParts.add(ChatCompletionContentPart.builder().type("image_url").imageUrl(
                    new ChatCompletionContentPart.ChatCompletionContentPartImageURL(
                            "data:image/jpeg;base64," + base64Image
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
