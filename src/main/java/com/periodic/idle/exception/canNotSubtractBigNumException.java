package com.periodic.idle.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class canNotSubtractBigNumException extends RuntimeException{
    public canNotSubtractBigNumException(Object o){
        super("Об'єкт "+o+" не може бути віднятий від числа BigNum томущо результат буде менше 0");
    }
}
