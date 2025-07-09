package com.volcengine.docanalysis;

import java.io.File;
import java.util.List;
import com.github.jaiimageio.jpeg2000.impl.Box;

public class Entry {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("argument is not properly provided, required arguments are model and file");
        }

        String modelName = args[0];
        String fullFileName = args[1];
        if (!fullFileName.endsWith(".pdf") && !fullFileName.endsWith(".PDF")) {
            throw new IllegalArgumentException("provided file is not PDF file");
        }
        File file = new File(fullFileName);
        if (!file.exists()) {
            log("target file does not exist - " + fullFileName);
            return;
        }
        String apiKey = null;
        if (args.length == 3) {
            apiKey = args[2];
        } else {
            apiKey = System.getenv("VOLC_APIKEY");
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API Key needs to be provided as the third argument or VOLC_APIKEY environment variable");
        }

        log("start to process file " + fullFileName + " with model " + modelName);

        PDFConverter converter = new PDFConverter();
        List<String> imageFiles = converter.convertToImages(fullFileName);

        ImagesAnalyzer imagesAnalyzer = new ImagesAnalyzer();
        imagesAnalyzer.analyze(imageFiles, generateResultFileName(fullFileName), modelName, apiKey);
    }

    private static String generateResultFileName(String fullFileName) {
        if (fullFileName.endsWith(".pdf")) {
            return fullFileName.replace(".pdf", ".md");
        } else {
            return fullFileName.replace(".PDF", ".md");
        }
    }

    private static void log(String content) {
        Logger.log(Entry.class.getSimpleName(), content);
    }
}
