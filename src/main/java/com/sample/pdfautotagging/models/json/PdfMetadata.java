package com.sample.pdfautotagging.models.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PdfMetadata {
//    private String format;
    private String title;
    private String author;
    private String subject;
//    private String keywords;
    private String creator;
    private String producer;
//    private String creationDate;
//    private String modDate;
//    private String trapped;
//    private Object encryption;
}
