import chisel3._
import chisel3.util._

object ComplexFixedPoint {

    // Bundle for complex numbers
    class Complex(val w: Int, val binaryPoint: Int) extends Bundle {
        val real = SInt(w.W)
        val imag = SInt(w.W)
    }

    // Complex Adder
    def add(a: Complex, b: Complex): Complex = {
        val out = Wire(new Complex(a.w, a.binaryPoint))
        out.real := a.real + b.real
        out.imag := a.imag + b.imag
        out
    }

    // Complex Subtractor
    def sub(a: Complex, b: Complex): Complex = {
        val out = Wire(new Complex(a.w, a.binaryPoint))
        out.real := a.real - b.real
        out.imag := a.imag - b.imag
        out
    }

    // Complex Multiplier, TODO look at pipelining 
    def mul(a: Complex, b: Complex): Complex = {
        val out = Wire(new Complex(a.w, a.binaryPoint))
        // (a + jb) * (c + jd) = (ac - bd) + j(ad + bc)
        val ac = a.real * b.real
        val bd = a.imag * b.imag
        val ad = a.real * b.imag
        val bc = a.imag * b.real
        // Scale down for fixed point arithmetic
        out.real := (ac - bd) >> a.binaryPoint
        out.imag := (ad + bc) >> a.binaryPoint
        out
    }
}