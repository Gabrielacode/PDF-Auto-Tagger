package com.sample.pdfautotagging.encryption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Component
public class HmacUtil {
    String secretKey;

    public HmacUtil( @Value("${MICROSERVICE_SECRET_KEY}")String secretKey) {
        this.secretKey = secretKey;
    }

    //This is our HMAC util class , for generating our hmac encryption from a token and a key
    public String  signToken(String payload){

        try{
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(), "HmacSHA256"));

            //Then we will create the signed token
          var signedTokenBytes =   mac.doFinal(payload.getBytes());
            return Base64.getEncoder().encodeToString(signedTokenBytes);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // We would have to also extract the token
    public String extractTokenFromUrl(String url){
        return  UriComponentsBuilder.fromUriString(url)
                .build()
                .getQueryParams()
                .getFirst("token");
    }
}
