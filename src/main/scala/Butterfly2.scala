import chisel3._
import chisel3.util._

class Butterfly2(val width: Int, val binaryPoint: Int, val pipeline: PipelineConfig, architecture: String = "GS") extends Module {
    val io = IO(new Bundle {
        // Input complex numbers
        val in0 = Input(new ComplexFixedPoint.Complex(width, binaryPoint))
        val in1 = Input(new ComplexFixedPoint.Complex(width, binaryPoint))

        // Output complex numbers
        val out0 = Output(new ComplexFixedPoint.Complex(width, binaryPoint))
        val out1 = Output(new ComplexFixedPoint.Complex(width, binaryPoint))
    })
    
    val butterfly = Module(new Butterfly(width, binaryPoint, pipeline, architecture))
    butterfly.io.in0 := io.in0
    butterfly.io.in1 := io.in1
    butterfly.io.twiddle.real := (1 << binaryPoint).S  // W_2^0 = 1
    butterfly.io.twiddle.imag := 0.S
    io.out0 := butterfly.io.out0
    io.out1 := butterfly.io.out1
}

