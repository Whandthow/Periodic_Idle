package com.periodic.idle.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class negativeNumberInBigNumException extends RuntimeException{
    public negativeNumberInBigNumException(){
        super("Ви не можете задати значення менше 0 в BigNum!");
    }
}
