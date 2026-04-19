package com.periodic.idle.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class dividedByZeroException extends RuntimeException{
    public dividedByZeroException (){
        super("Ви не можете виконати ділення на 0 !!!");
    }
}
