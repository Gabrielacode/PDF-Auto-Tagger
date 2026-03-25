package com.sample.pdfautotagging;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Iterator;

public class ImageCOmpress {
    public static void main(String[] args) throws Exception {

        //We would list all the files in that directory
        File boluDirectory = new File("/home/garbi/Documents/Bolu Gabriel");


        var subFiles = boluDirectory.listFiles();
        for(File subFile : subFiles){
            System.out.println("Compressing File "+subFile.getName());
            ImageCompressor.compressImage(subFile,new File(subFile.getAbsolutePath()+"_compressed"),0.1f);
        }



    }
}

 class ImageCompressor {

    public static void compressImage(File input, File output, float quality) throws Exception {
        BufferedImage image = ImageIO.read(input);
        try (OutputStream os = new FileOutputStream(output)) {
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
            if (!writers.hasNext()) throw new IllegalStateException("No JPEG writers found");
            ImageWriter writer = writers.next();

            try (ImageOutputStream ios = ImageIO.createImageOutputStream(os)) {
                writer.setOutput(ios);
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality); // Quality: 0.0 (low) to 1.0 (high)

                writer.write(null, new IIOImage(image, null, null), param);
            } finally {
                writer.dispose();
            }
        }
    }
}
