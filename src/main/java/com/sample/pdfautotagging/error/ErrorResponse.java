package com.sample.pdfautotagging.error;

public record ErrorResponse(
        String message,
        int statusCode
) {
}
