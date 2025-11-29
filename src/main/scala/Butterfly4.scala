import chisel3._
import chisel3.util._

class Butterfly4(val width: Int, val binaryPoint: Int, val pipeline: PipelineConfig, architecture: String = "GS") extends Module {
    val io = IO(new Bundle {
        // Input complex numbers
        val in = Input(Vec(4, new ComplexFixedPoint.Complex(width, binaryPoint)))
        // Output complex numbers
        val out = Output(Vec(4, new ComplexFixedPoint.Complex(width, binaryPoint)))
    })
    if (architecture == "GS") {
        val butterfly10 = Module(new Butterfly(width, binaryPoint, pipeline, architecture))
        val butterfly11 = Module(new Butterfly(width, binaryPoint, pipeline, architecture))

        butterfly10.io.in0 := io.in(0)
        butterfly10.io.in1 := io.in(2)
        butterfly10.io.twiddle.real := (1 << binaryPoint).S  // W_4^0 = 1
        butterfly10.io.twiddle.imag := 0.S

        butterfly11.io.in0 := io.in(1)
        butterfly11.io.in1 := io.in(3)
        butterfly11.io.twiddle.real := 0.S                   // W_4^1 = -j
        butterfly11.io.twiddle.imag := (-1 * (1 << binaryPoint)).S

        val butterfly00 = Module(new Butterfly2(width, binaryPoint, pipeline))
        val butterfly01 = Module(new Butterfly2(width, binaryPoint, pipeline))

        butterfly00.io.in0 := butterfly10.io.out0
        butterfly00.io.in1 := butterfly11.io.out0

        butterfly01.io.in0 := butterfly10.io.out1
        butterfly01.io.in1 := butterfly11.io.out1

        // Connect outputs
        io.out(0) := butterfly00.io.out0  
        io.out(2) := butterfly00.io.out1
        io.out(1) := butterfly01.io.out0
        io.out(3) := butterfly01.io.out1
        
    } else if (architecture == "CT") {
        val butterfly00 = Module(new Butterfly2(width, binaryPoint, pipeline))
        val butterfly01 = Module(new Butterfly2(width, binaryPoint, pipeline))

        butterfly00.io.in0 := io.in(0)
        butterfly00.io.in1 := io.in(2)

        butterfly01.io.in0 := io.in(1)
        butterfly01.io.in1 := io.in(3)

        val butterfly10 = Module(new Butterfly(width, binaryPoint, pipeline, architecture))
        val butterfly11 = Module(new Butterfly(width, binaryPoint, pipeline, architecture))

        butterfly10.io.in0 := butterfly00.io.out0
        butterfly10.io.in1 := butterfly01.io.out0
        // W_4^0 = 1
        butterfly10.io.twiddle.real := (1 << binaryPoint).S
        butterfly10.io.twiddle.imag := 0.S

        butterfly11.io.in0 := butterfly00.io.out1
        butterfly11.io.in1 := butterfly01.io.out1
        // W_4^1 = -j
        butterfly11.io.twiddle.real := 0.S
        butterfly11.io.twiddle.imag := (-1 * (1 << binaryPoint)).S

        // Connect outputs
        io.out(0) := butterfly10.io.out0
        io.out(1) := butterfly11.io.out0
        io.out(2) := butterfly10.io.out1
        io.out(3) := butterfly11.io.out1
    } else {
        throw new Exception("Unsupported architecture type. Use 'CT' for Cooley-Tukey or 'GS' for Gentleman-Sande.")
    }
    
}



