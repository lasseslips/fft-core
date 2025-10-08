import chisel3._
import chisel3.util._

class Butterfly(val width: Int, val binaryPoint: Int, val pipeline: Boolean = false) extends Module {
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
    if (pipeline) {
        io.out0 := RegNext(sum)
        io.out1 := RegNext(ComplexFixedPoint.mul(diff, io.twiddle))
    } else {
        io.out0 := sum
        io.out1 := ComplexFixedPoint.mul(diff, io.twiddle)
    }

}
