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

    // Complex Multiplier
    // TODO: Look at https://docs.amd.com/v/u/en-US/ug479_7Series_DSP48E1 and see how we can best map to DSP slices
    // 
    def mul(a: Complex, b: Complex): Complex = {
        val out = Wire(new Complex(a.w, a.binaryPoint))
        // (a + jb) * (c + jd) = (ac - bd) + j(ad + bc)
        val doubleWidth = a.w * 2
        // Intermediate values to avoid overflow
        val ac = Wire(SInt(doubleWidth.W))
        val bd = Wire(SInt(doubleWidth.W))
        val ad = Wire(SInt(doubleWidth.W))
        val bc = Wire(SInt(doubleWidth.W))

        ac := a.real.asSInt * b.real.asSInt
        bd := a.imag.asSInt * b.imag.asSInt
        ad := a.real.asSInt * b.imag.asSInt
        bc := a.imag.asSInt * b.real.asSInt

        // Scale down for fixed point arithmetic
        out.real := (ac - bd) >> a.binaryPoint
        out.imag := (ad + bc) >> a.binaryPoint
        out
    }
}