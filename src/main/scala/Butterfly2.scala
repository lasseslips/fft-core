import chisel3._
import chisel3.util._

// Butterfly2 module for radix-2 FFT
class Butterfly2(val width: Int, val binaryPoint: Int) extends Module {
    val io = IO(new Bundle {
        // Input complex numbers
        val in0 = Input(new ComplexFixedPoint.Complex(width, binaryPoint))
        val in1 = Input(new ComplexFixedPoint.Complex(width, binaryPoint))
        
        // Twiddle factor (complex exponential)
        val twiddle = Input(new ComplexFixedPoint.Complex(width, binaryPoint))
        
        // Output complex numbers
        val out0 = Output(new ComplexFixedPoint.Complex(width, binaryPoint))
        val out1 = Output(new ComplexFixedPoint.Complex(width, binaryPoint))
    })

    // Butterfly computation (DIT radix-2):
    // out0 = in0 + in1
    // out1 = (in0 - in1) * twiddle
    
    // First perform addition and subtraction
    val sum = ComplexFixedPoint.add(io.in0, io.in1)
    val diff = ComplexFixedPoint.sub(io.in0, io.in1)
    
    // Apply twiddle factor to the difference
    io.out0 := sum
    io.out1 := ComplexFixedPoint.mul(diff, io.twiddle)
}

// Companion object for easy instantiation
object Butterfly2 {
    def apply(width: Int, binaryPoint: Int): Butterfly2 = {
        Module(new Butterfly2(width, binaryPoint))
    }
}
