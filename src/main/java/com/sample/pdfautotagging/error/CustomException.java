package com.sample.pdfautotagging.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

public class CustomException  extends RuntimeException{
    //This exception will be used to details  to send an error message , and also for developers to look into it
    //We would have a status code
    //Then a message
    //We would also have to keep track of the job of the Job Id
    private final String jobId;
    @Getter
    private final HttpStatus statusCode;
    private final String supportMessage;


    //Then we would  a custom get message method will be called


    public CustomException(String message, String jobId, HttpStatus statusCode, String supportMessage, Throwable cause) {
        super(message,cause);
        this.jobId = jobId;
        this.statusCode = statusCode;
        this.supportMessage = supportMessage;
    }


    //In the message we would have to Have the structure
    //The main message to the user , then ( support message to the developer with the job id )

    @Override
    public String getMessage() {
        return super.getMessage() + "(# " +supportMessage+" - "+jobId+")";
    }
}
