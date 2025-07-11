package com.volcengine.docanalysis;

import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.volcengine.docanalysis.Logger.log;

public class PDFConverter {

    public List<String> convertToImages(String filename) throws Exception {
        File file = new File(filename);
        String folderName = prepareFolder(file);
        PDDocument document = PDDocument.load(file);
        PDFRenderer pdfRenderer = new PDFRenderer(document);
        String filePath = file.getParentFile().getAbsolutePath() + File.separator + folderName + File.separator;
        int DPI = 150;

        List<String> imageFiles = new ArrayList<>();
        for (int page = 0; page < document.getNumberOfPages(); ++page) {
            BufferedImage bim = pdfRenderer.renderImageWithDPI(
                    page, DPI, ImageType.RGB);
            String imageFileName = String.format(filePath + "image-%d.%s", page + 1, "jpg");
            log(imageFileName);
            imageFiles.add(imageFileName);
            ImageIOUtil.writeImage(
                    bim, imageFileName, DPI);
        }

        document.close();
        return imageFiles;
    }

    private String prepareFolder(File file) throws IOException {
        String fileName = file.getName();
        String folderName = fileName.substring(0, fileName.lastIndexOf("."));
        File file1 = new File(file.getParentFile(), folderName + File.separator + ".tmp");
        FileUtils.createParentDirectories(file1);
        return folderName;
    }

}
