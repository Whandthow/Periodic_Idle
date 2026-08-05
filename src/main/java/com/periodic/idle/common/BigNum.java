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
        if(number==0 || Double.isNaN(number)){
            this.number=0;
            this.exponent=0;
            return;
        }
        if(Double.isInfinite(number)){
            // Захист від зависання в while-лупі нормалізації.
            throw new IllegalArgumentException("BigNum received Infinity");
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
        double finalNumber = getNumber();
        long finalExponent = getExponent();

        if (subtractNum.exponent > finalExponent) {
            throw new canNotSubtractBigNumException(subtractNum);
        }
        long gap = finalExponent - subtractNum.exponent;
        if (gap == 0) {
            if (finalNumber < subtractNum.number) throw new canNotSubtractBigNumException(subtractNum);
            return new BigNum(finalNumber - subtractNum.number, finalExponent);
        }
        if (gap > 308) {
            // subtractNum більше ніж на 308 порядків менший за this — віднімання
            // не змінює значення (дзеркалить те саме "гейт" в add(), розділ вище).
            // Без цієї гілки код падав у "else" нижче й напряму порівнював мантиси
            // при НЕОДНАКОВИХ (лише "не оброблених явно") показниках степеня —
            // напр. 2.5e2010 - 3.8e10 хибно кидало canNotSubtractBigNumException,
            // бо 2.5 < 3.8 порівнювались як мантиси однієї шкали.
            return new BigNum(this.number, this.exponent);
        }
        double tmp = subtractNum.number * Math.pow(10, subtractNum.exponent - finalExponent);
        return new BigNum(finalNumber - tmp, finalExponent);
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

    /**
     * {@code base * 10^baseExponent * mult^level}, безпечно для великих {@code level}.
     * Рахує в log10-просторі замість {@code Math.pow(mult, level)} напряму — той вираз
     * переповнює double (Infinity) вже при помірних level (напр. mult=3.2 переповнює
     * на рівні ~610, задовго до типового max_level=999 в upgrades), через що виклики на
     * кшталт {@code new BigNum(base*Math.pow(mult,level), baseExponent)} мовчки ламали
     * подорожчання ціни (guard на !isFinite зупиняв купівлю навічно, а не рахував ціну).
     */
    public static BigNum scaledByLevel(double base, long baseExponent, double mult, long level) {
        if (level <= 0) return new BigNum(base, baseExponent);
        double logValue = Math.log10(base) + baseExponent + level * Math.log10(mult);
        if (!Double.isFinite(logValue)) {
            // base<=0 чи mult<=0 — невалідний контент; величезна ціна замість "безкоштовно"
            // (BigNum(NaN,_) нормалізується в 0, що зробило б купівлю нескінченною).
            return new BigNum(1.0, Long.MAX_VALUE / 2);
        }
        long exp = (long) Math.floor(logValue);
        double mantissa = Math.pow(10, logValue - exp);
        return new BigNum(mantissa, exp);
    }
}
