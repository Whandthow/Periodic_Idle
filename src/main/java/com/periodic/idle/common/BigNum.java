package com.periodic.idle.common;

import com.periodic.idle.exception.canNotSubtractBigNumException;
import com.periodic.idle.exception.dividedByZeroException;
import com.periodic.idle.exception.negativeNumberInBigNumException;
import lombok.*;

@Getter
@EqualsAndHashCode
public class BigNum implements Comparable<BigNum> {
    @Setter(AccessLevel.NONE)
    private double number;
    private long exponent;
    //private int doubleExponent;

    public BigNum(double number,long exponent){
        if(number<0) throw new negativeNumberInBigNumException();
        if(number==0){
            this.number=0;
            this.exponent=0;
            return;
        }
        this.exponent=exponent;
        while (number >= 10) {
            this.exponent++;
            number /= 10;
        }
        while (number < 1) {
            this.exponent--;
            number *= 10;
        }
        this.number=number;
    }

    public double bigNumToDouble (BigNum newNumber){
        if(Math.abs(newNumber.exponent)>=308)throw new IllegalArgumentException();
        return newNumber.number * Math.pow(10, newNumber.exponent);
    }

    @Override
    public String toString() {
        return //doubleExponent > 0 ? String.format("%.4fe%de%d",number,exponent,doubleExponent)
                String.format("%.4fe%d",number,exponent);
    }

    public int compareTo (BigNum comparingNumber){
        if(comparingNumber.exponent<this.exponent) return 1;
        if(comparingNumber.exponent==this.exponent&&
        comparingNumber.number<this.number)return 1;
        if(this.equals(comparingNumber))return 0;
        return -1;
    }

    public BigNum add(BigNum additionNum){
        double finalNumber=getNumber();
        long finalExponent= getExponent();
        if(Math.abs(finalExponent-additionNum.exponent)<=380){
            if(additionNum.exponent<finalExponent){
                double tmp = additionNum.number * Math.pow(10,(additionNum.exponent-finalExponent));
                return new BigNum((finalNumber+tmp),finalExponent);
            }else if(additionNum.exponent>finalExponent){
                double tmp = finalNumber * Math.pow(10,finalExponent-additionNum.exponent);
                return new BigNum(additionNum.number+tmp, additionNum.exponent);
            }else{
                return new BigNum(finalNumber+ additionNum.number,finalExponent);
            }
        }else if(additionNum.exponent-finalExponent>308){
            return new BigNum(additionNum.number, additionNum.exponent);
        }else{
            return new BigNum(this.number, this.exponent);
        }
    }

    public BigNum subtract(BigNum subtractNum) {
        double finalNumber=getNumber();
        long finalExponent= getExponent();
        if (subtractNum.exponent < finalExponent && Math.abs(finalExponent - subtractNum.exponent) <= 308) {
            double tmp = subtractNum.number * Math.pow(10, (subtractNum.exponent - finalExponent));
            return new BigNum(finalNumber - tmp, finalExponent);
        } else if (subtractNum.exponent > finalExponent) {
            throw new canNotSubtractBigNumException(subtractNum);
        } else {
            if (finalNumber < subtractNum.number) throw new canNotSubtractBigNumException(subtractNum);
            return new BigNum(finalNumber-subtractNum.number,finalExponent);
        }
    }

    public BigNum multiply(double multNum){
        return new BigNum(getNumber()*multNum,getExponent());
    }

    public BigNum multiply(BigNum multiplyNum){
        return new BigNum(getNumber()*multiplyNum.number,getExponent()+ multiplyNum.exponent);
    }

    public BigNum divide(double divNum){
        if(divNum==0)throw new dividedByZeroException();
        return new BigNum(getNumber()/divNum,getExponent());
    }

    public BigNum divide(BigNum divideNum){
        if(divideNum.number==0)throw new dividedByZeroException();
        return new BigNum(getNumber()/divideNum.number,getExponent()-divideNum.exponent);
    }

    public BigNum pow (double powNum){
        return new BigNum(Math.pow(getNumber(),powNum),(long)(getExponent()*powNum));
    }

    public BigNum pow (BigNum powNum){
        double postLog = Math.log10(getNumber())+getExponent();
        double finalExponent = bigNumToDouble(pow(postLog));
        return new BigNum(Math.pow(10,finalExponent%1),(long)finalExponent);
    }
}
