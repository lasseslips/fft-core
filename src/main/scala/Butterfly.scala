import chisel3._
import chisel3.util._

class Butterfly(val width: Int, val binaryPoint: Int, val pipeline: Boolean = false, architecture : String = "GS") extends Module {
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

    if (architecture == "CT") {  // Cooley-Tukey Butterfly (DIT)
        // out0 = in0 + in1*twiddle
        // out1 = in0 - in1*twiddle
        if (pipeline) {
            val prod = RegNext(ComplexFixedPoint.mul(io.in1, io.twiddle, false))
            val sum = RegNext(ComplexFixedPoint.add(RegNext(io.in0), prod))
            val diff = RegNext(ComplexFixedPoint.sub(RegNext(io.in0), prod))
            io.out0 := sum
            io.out1 := diff
        } else {
            val prod = ComplexFixedPoint.mul(io.in1, io.twiddle, pipeline)
            val sum = ComplexFixedPoint.add(io.in0, prod)
            val diff = ComplexFixedPoint.sub(io.in0, prod)
            io.out0 := sum
            io.out1 := diff
        }
    } else if (architecture == "GS") {  // Gentleman-Sande Butterfly (DIF)
        // out0 = in0 + in1
        // out1 = (in0 - in1) * twiddle
        if (pipeline) {
            val sum = RegNext(ComplexFixedPoint.add(io.in0, io.in1))
            val diff = RegNext(ComplexFixedPoint.sub(io.in0, io.in1))
            io.out0 := RegNext(sum)
            io.out1 := RegNext(ComplexFixedPoint.mul(diff, io.twiddle, false))
        } else {
            val sum = ComplexFixedPoint.add(io.in0, io.in1)
            val diff = ComplexFixedPoint.sub(io.in0, io.in1)
            io.out0 := sum
            io.out1 := ComplexFixedPoint.mul(diff, io.twiddle, pipeline)
        }
    } else {
        throw new Exception("Unsupported architecture type. Use 'CT' for Cooley-Tukey or 'GS' for Gentleman-Sande.")
    }
}
