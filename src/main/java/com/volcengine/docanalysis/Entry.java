package com.volcengine.docanalysis;

import java.io.File;
import java.util.Date;
import java.util.List;

public class Entry {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            throw new IllegalArgumentException("argument is not properly provided, required arguments are model、file and batchSize");
        }

        String modelName = args[0];
        String fullFileName = args[1];
        int batchSize = Integer.parseInt(args[2]);
        if (!fullFileName.endsWith(".pdf") && !fullFileName.endsWith(".PDF")) {
            throw new IllegalArgumentException("provided file is not PDF file");
        }
        File file = new File(fullFileName);
        if (!file.exists()) {
            log("target file does not exist - " + fullFileName);
            return;
        }
        String apiKey;
        if (args.length == 4) {
            apiKey = args[3];
        } else {
            apiKey = System.getenv("VOLC_APIKEY");
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API Key needs to be provided either in argument or in environment variable");
        }

        start(modelName, fullFileName, batchSize, apiKey);
    }

    private static void start(String model, String fullFileName, int batchSize, String apiKey) throws Exception {
        log("start to process file " + fullFileName + " with model " + model + " batchSize " + batchSize);
        Date startTime = new Date();
        PDFConverter converter = new PDFConverter();
        List<String> imageFiles = converter.convertToImages(fullFileName);

        ImagesAnalyzer imagesAnalyzer = new ImagesAnalyzer();
        imagesAnalyzer.analyze(imageFiles, generateResultFileName(fullFileName), model, apiKey, batchSize);
        
        String executionTime = Logger.getExecutionTime(startTime);
        log("File process done, total execution time: " + executionTime);
    
    }

    private static String generateResultFileName(String fullFileName) {
        if (fullFileName.endsWith(".pdf")) {
            return fullFileName.replace(".pdf", ".md");
        } else {
            return fullFileName.replace(".PDF", ".md");
        }
    }

    static void log(String content) {
        Logger.log(Entry.class.getSimpleName(), content);
    }
}
