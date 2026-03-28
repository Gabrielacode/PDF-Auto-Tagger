package com.sample.pdfautotagging.controller;

import com.sample.pdfautotagging.error.ErrorResponse;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalErrorControllerAdvice {
    //This is where we will handle all miscellaneous errors
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e){
        //We will just convert it to simple error handler
        var errorMessage = e.getMessage();
        return  ResponseEntity.internalServerError().body( new ErrorResponse(errorMessage,500));
    }

}
