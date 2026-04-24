package com.periodic.idle;

import com.periodic.idle.common.BigNum;
import com.periodic.idle.exception.canNotSubtractBigNumException;
import com.periodic.idle.exception.dividedByZeroException;
import com.periodic.idle.exception.negativeNumberInBigNumException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BigNumTest {

    // === Конструктор + нормалізація ===

    @Test
    @DisplayName("12345 нормалізується в 1.2345e4")
    void constructorNormalizesLargeNumber() {
        BigNum n = new BigNum(12345, 0);
        assertEquals(1.2345, n.getNumber(), 0.0001);
        assertEquals(4, n.getExponent());
    }

    @Test
    @DisplayName("0.005 нормалізується в 5.0e-3")
    void constructorNormalizesSmallNumber(){
        BigNum n = new BigNum(0.005,0);
        assertEquals(5.0,n.getNumber(),0.0001);
        assertEquals(-3,n.getExponent());
    }

    @Test
    @DisplayName("нуль залишається нулем")
    void constructorZero(){
        BigNum n = new BigNum(0,0);
        assertEquals(0,n.getNumber(),0.0001);
        assertEquals(0,n.getExponent());
    }

    @Test
    @DisplayName("від'ємне — exception")
    void constructorNegativeThrows() {
        assertThrows(negativeNumberInBigNumException.class,
                ()->new BigNum(-5,0));
    }

    // === Додавання ===

    @Test
    @DisplayName("1e100 + 2e100 = 3e100")
    void addSameExponent() {
        BigNum n = new BigNum(1,100);
        BigNum m = new BigNum(2,100);
        BigNum result = n.add(m);
        assertEquals(3,result.getNumber(),0.001);
        assertEquals(100,result.getExponent());
    }

    @Test
    @DisplayName("1e100 + 1e50 = 1e100 (маленьке поглинається)")
    void addTinyIgnored(){
        BigNum n = new BigNum(1,100);
        BigNum m = new BigNum(1,50);
        BigNum result = n.add(m);
        assertEquals(1,result.getNumber(),0.001);
        assertEquals(100,result.getExponent());
    }

    @Test
    @DisplayName("1e50 + 1e120 = 1e120(Велике замінює значення)")
    void addBigReplace(){
        BigNum n = new BigNum(1,50);
        BigNum m = new BigNum(1,120);
        BigNum result = n.add(m);
        assertEquals(1,result.getNumber(),0.001);
        assertEquals(120,result.getExponent());
    }

    @Test
    @DisplayName("1e120 + 1e122 = 1.01e122(Додавання більшого)")
    void addBig(){
        BigNum n = new BigNum(1,120);
        BigNum m = new BigNum(1,122);
        BigNum result = n.add(m);
        assertEquals(1.01,result.getNumber(),0.001);
        assertEquals(122,result.getExponent());
    }

    @Test
    @DisplayName("1e80 + 2e79 = 1.2e80(додавання меншого)")
    void addSmall(){
        BigNum n = new BigNum(1,80);
        BigNum m = new BigNum(2,79);
        BigNum result = n.add(m);
        assertEquals(1.2,result.getNumber(),0.001);
        assertEquals(80,result.getExponent());
    }

    // === Віднімання ===

    @Test
    @DisplayName("1e90 - 1e100 = Exception значення більше доступного")
    void tooBigSubtractExponent(){
        BigNum n = new BigNum(1,90);
        BigNum m = new BigNum(1,100);
        assertThrows(canNotSubtractBigNumException.class, () -> n.subtract(m));
    }

    @Test
    @DisplayName("1e55 - 1.4e55 = Exception ")
    void tooBigSubtractNumber(){
        BigNum n = new BigNum(1,55);
        BigNum m = new BigNum(1.4,55);
        assertThrows(canNotSubtractBigNumException.class, () -> n.subtract(m));
    }

    @Test
    @DisplayName("1e20 - 8e18 = 9.2e19")
    void exponentSubtract(){
        BigNum n = new BigNum(1,20);
        BigNum m = new BigNum(8,18);
        BigNum result = n.subtract(m);
        assertEquals(9.2,result.getNumber(),0.001);
        assertEquals(19,result.getExponent());
    }

    @Test
    @DisplayName("7e90 - 4e90 = 3e90")
    void numberSubtract(){
        BigNum n = new BigNum(7,90);
        BigNum m = new BigNum(4,90);
        BigNum result = n.subtract(m);
        assertEquals(3,result.getNumber(),0.001);
        assertEquals(90,result.getExponent());
    }

    @Test
    @DisplayName("6e-2 - 2e-4 = 5.98e-2")
    void negativeNumberSubtract(){
        BigNum n = new BigNum(6,-2);
        BigNum m = new BigNum(2,-4);
        BigNum result = n.subtract(m);
        assertEquals(5.98,result.getNumber(),0.001);
        assertEquals(-2,result.getExponent());
    }

    // === Множення ===

    @Test
    @DisplayName("1e30 * 70 = 7e31")
    void correctMultDouble(){
        BigNum result = new BigNum(1,30).multiply(70);
        assertEquals(7,result.getNumber(),0.001);
        assertEquals(31,result.getExponent());
    }

    @Test
    @DisplayName("1e12 * 0 = 0")
    void zeroMult(){
        BigNum result = new BigNum(1,30).multiply(0);
        assertEquals(0,result.getNumber(),0.001);
        assertEquals(0,result.getExponent());
    }

    @Test
    @DisplayName("2e13 * -10 = exception")
    void negativeMultException(){
        BigNum result = new BigNum(2,13);
        assertThrows(negativeNumberInBigNumException.class,()->result.multiply(-10));
    }

    // === Множення на BigNum===

    @Test
    @DisplayName("1e40 * 4e20 = 4e60")
    void correctBigNumMult() {
        BigNum n = new BigNum(1, 40);
        BigNum m = new BigNum(4, 20);
        BigNum result = n.multiply(m);
        assertEquals(4, result.getNumber(), 0.001);
        assertEquals(60, result.getExponent());
    }

    @Test
    @DisplayName("2e20 * 0e0 = 0")
    void zeroBigNumMult(){
        BigNum n = new BigNum(2, 20);
        BigNum m = new BigNum(0, 0);
        BigNum result = n.multiply(m);
        assertEquals(0, result.getNumber(), 0.001);
        assertEquals(0, result.getExponent());
    }

    // === Ділення на double ===

    @Test
    @DisplayName("1e30 / 5 = 2e29")
    void correctDivDouble() {
        BigNum result = new BigNum(1, 30).divide(5);
        assertEquals(2, result.getNumber(), 0.001);
        assertEquals(29, result.getExponent());
    }

    @Test
    @DisplayName("1e10 / 0 = exception")
    void divByZeroDouble() {
        assertThrows(dividedByZeroException.class,
                () -> new BigNum(1, 10).divide(0));
    }

    // === Ділення на BigNum ===

    @Test
    @DisplayName("6e80 / 3e30 = 2e50")
    void correctBigNumDiv() {
        BigNum result = new BigNum(6, 80).divide(new BigNum(3, 30));
        assertEquals(2, result.getNumber(), 0.001);
        assertEquals(50, result.getExponent());
    }

    @Test
    @DisplayName("1e50 / 0 = exception")
    void divByZeroBigNum() {
        assertThrows(dividedByZeroException.class,
                () -> new BigNum(1, 50).divide(new BigNum(0, 0)));
    }

    @Test
    @DisplayName("1e10 / 3e10 = ~3.333e-1")
    void divSameExponent() {
        BigNum result = new BigNum(1, 10).divide(new BigNum(3, 10));
        assertEquals(3.3333, result.getNumber(), 0.001);
        assertEquals(-1, result.getExponent());
    }

    // === Степінь double ===

    @Test
    @DisplayName("1e10 ^ 2 = 1e20")
    void powSimple() {
        BigNum result = new BigNum(1, 10).pow(2);
        assertEquals(1, result.getNumber(), 0.001);
        assertEquals(20, result.getExponent());
    }

    @Test
    @DisplayName("2e5 ^ 3 = 8e15")
    void powWithMantissa() {
        BigNum result = new BigNum(2, 5).pow(3);
        assertEquals(8, result.getNumber(), 0.01);
        assertEquals(15, result.getExponent());
    }

    @Test
    @DisplayName("1e100 ^ 0 = 1e0")
    void powZero() {
        BigNum result = new BigNum(1, 100).pow(0);
        assertEquals(1, result.getNumber(), 0.001);
        assertEquals(0, result.getExponent());
    }

    @Test
    @DisplayName("4e10 ^ 0.5 = 2e5 (корінь)")
    void powFractional() {
        BigNum result = new BigNum(4, 10).pow(0.5);
        assertEquals(2, result.getNumber(), 0.01);
        assertEquals(5, result.getExponent());
    }

    // === compareTo ===

    @Test
    @DisplayName("1e50 > 9e49")
    void compareGreaterExponent() {
        assertEquals(1, new BigNum(1, 50).compareTo(new BigNum(9, 49)));
    }

    @Test
    @DisplayName("5e30 > 3e30")
    void compareGreaterNumber() {
        assertEquals(1, new BigNum(5, 30).compareTo(new BigNum(3, 30)));
    }

    @Test
    @DisplayName("1e10 == 1e10")
    void compareEqual() {
        assertEquals(0, new BigNum(1, 10).compareTo(new BigNum(1, 10)));
    }

    @Test
    @DisplayName("3e20 < 5e20")
    void compareLess() {
        assertEquals(-1, new BigNum(3, 20).compareTo(new BigNum(5, 20)));
    }

    @Test
    @DisplayName("1e5 < 1e50")
    void compareLessExponent() {
        assertEquals(-1, new BigNum(1, 5).compareTo(new BigNum(1, 50)));
    }

    // === toString ===

    @Test
    @DisplayName("1.5e10 toString")
    void toStringBasic() {
        assertEquals("1,5000e10", new BigNum(1.5, 10).toString());
    }

    @Test
    @DisplayName("0 toString")
    void toStringZero() {
        assertEquals("0,0000e0", new BigNum(0, 0).toString());
    }

    // === Крайні випадки ===

    @Test
    @DisplayName("Дуже великі експоненти: 1e999999 * 1e999999 = 1e1999998")
    void hugeExponentMultiply() {
        BigNum result = new BigNum(1, 999999).multiply(new BigNum(1, 999999));
        assertEquals(1, result.getNumber(), 0.001);
        assertEquals(1999998, result.getExponent());
    }

    @Test
    @DisplayName("Immutability: add не змінює оригінал")
    void addDoesNotMutate() {
        BigNum a = new BigNum(1, 10);
        BigNum b = new BigNum(2, 10);
        a.add(b);
        assertEquals(1, a.getNumber(), 0.001);
        assertEquals(10, a.getExponent());
    }

    @Test
    @DisplayName("Immutability: multiply не змінює оригінал")
    void multiplyDoesNotMutate() {
        BigNum a = new BigNum(3, 20);
        a.multiply(new BigNum(2, 5));
        assertEquals(3, a.getNumber(), 0.001);
        assertEquals(20, a.getExponent());
    }

    @Test
    @DisplayName("0 + 5e10 = 5e10")
    void addToZero() {
        BigNum result = new BigNum(0, 0).add(new BigNum(5, 10));
        assertEquals(5, result.getNumber(), 0.001);
        assertEquals(10, result.getExponent());
    }

    @Test
    @DisplayName("5e10 - 5e10 = 0")
    void subtractToZero() {
        BigNum result = new BigNum(5, 10).subtract(new BigNum(5, 10));
        assertEquals(0, result.getNumber(), 0.001);
        assertEquals(0, result.getExponent());
    }

    // === Захист від NaN / Infinity ===

    @Test
    @DisplayName("Infinity у конструкторі кидає IllegalArgumentException (без зависання)")
    void constructorInfinityThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new BigNum(Double.POSITIVE_INFINITY, 0));
    }

    @Test
    @DisplayName("NaN у конструкторі трактується як 0")
    void constructorNanAsZero() {
        BigNum n = new BigNum(Double.NaN, 10);
        assertEquals(0, n.getNumber(), 0.001);
        assertEquals(0, n.getExponent());
    }
}
