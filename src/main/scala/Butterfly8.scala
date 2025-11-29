import chisel3._
import chisel3.util._

class Butterfly8(val width: Int, val binaryPoint: Int, val pipeline: PipelineConfig, architecture: String = "GS") extends Module {
    val io = IO(new Bundle {
        // Input complex numbers
        val in = Input(Vec(8, new ComplexFixedPoint.Complex(width, binaryPoint)))
        // Output complex numbers
        val out = Output(Vec(8, new ComplexFixedPoint.Complex(width, binaryPoint)))
    })

    if (architecture == "GS") {
        // First stage: 4 butterflies with twiddle factors W_8^0, W_8^1, W_8^2, W_8^3
        val sqrt2_over_2 = (0.7071 * (1 << binaryPoint)).toInt

        val butterfly10 = Module(new Butterfly(width, binaryPoint, pipeline, architecture))
        val butterfly11 = Module(new Butterfly(width, binaryPoint, pipeline, architecture))
        val butterfly12 = Module(new Butterfly(width, binaryPoint, pipeline, architecture))
        val butterfly13 = Module(new Butterfly(width, binaryPoint, pipeline, architecture))

        butterfly10.io.in0 := io.in(0)
        butterfly10.io.in1 := io.in(4)
        butterfly10.io.twiddle.real := (1 << binaryPoint).S  // W_8^0 = 1
        butterfly10.io.twiddle.imag := 0.S

        butterfly11.io.in0 := io.in(1)
        butterfly11.io.in1 := io.in(5)
        butterfly11.io.twiddle.real := sqrt2_over_2.S         // W_8^1 = sqrt(2)/2 - j*sqrt(2)/2
        butterfly11.io.twiddle.imag := (-sqrt2_over_2).S

        butterfly12.io.in0 := io.in(2)
        butterfly12.io.in1 := io.in(6)
        butterfly12.io.twiddle.real := 0.S                   // W_8^2 = -j
        butterfly12.io.twiddle.imag := (-1 * (1 << binaryPoint)).S

        butterfly13.io.in0 := io.in(3)
        butterfly13.io.in1 := io.in(7)
        butterfly13.io.twiddle.real := (-sqrt2_over_2).S     // W_8^3 = -sqrt(2)/2 - j*sqrt(2)/2
        butterfly13.io.twiddle.imag := (-sqrt2_over_2).S

        // Second stage: two 4-point FFTs
        val butterfly00 = Module(new Butterfly4(width, binaryPoint, pipeline, architecture))
        val butterfly01 = Module(new Butterfly4(width, binaryPoint, pipeline, architecture))

        // Connect the first 4-point FFT (even outputs)
        butterfly00.io.in(0) := butterfly10.io.out0
        butterfly00.io.in(1) := butterfly11.io.out0
        butterfly00.io.in(2) := butterfly12.io.out0
        butterfly00.io.in(3) := butterfly13.io.out0

        // Connect the second 4-point FFT (odd outputs)
        butterfly01.io.in(0) := butterfly10.io.out1
        butterfly01.io.in(1) := butterfly11.io.out1
        butterfly01.io.in(2) := butterfly12.io.out1
        butterfly01.io.in(3) := butterfly13.io.out1

        // Connect outputs 
        io.out(0) := butterfly00.io.out(0)
        io.out(1) := butterfly01.io.out(0)
        io.out(2) := butterfly00.io.out(1)
        io.out(3) := butterfly01.io.out(1)
        io.out(4) := butterfly00.io.out(2)
        io.out(5) := butterfly01.io.out(2)
        io.out(6) := butterfly00.io.out(3)
        io.out(7) := butterfly01.io.out(3)
    } else if (architecture == "CT") {
        // First stage: two 4-point FFTs
        val butterfly00 = Module(new Butterfly4(width, binaryPoint, pipeline, architecture))
        val butterfly01 = Module(new Butterfly4(width, binaryPoint, pipeline, architecture))

        // Connect the first 4-point FFT (even inputs)
        butterfly00.io.in(0) := io.in(0)
        butterfly00.io.in(1) := io.in(2)
        butterfly00.io.in(2) := io.in(4)
        butterfly00.io.in(3) := io.in(6)

        // Connect the second 4-point FFT (odd inputs)
        butterfly01.io.in(0) := io.in(1)
        butterfly01.io.in(1) := io.in(3)
        butterfly01.io.in(2) := io.in(5)
        butterfly01.io.in(3) := io.in(7)

        // Second stage: 4 butterflies with twiddle factors W_8^0, W_8^1, W_8^2, W_8^3
        val sqrt2_over_2 = (0.7071 * (1 << binaryPoint)).toInt

        val butterfly10 = Module(new Butterfly(width, binaryPoint, pipeline, architecture))
        val butterfly11 = Module(new Butterfly(width, binaryPoint, pipeline, architecture))
        val butterfly12 = Module(new Butterfly(width, binaryPoint, pipeline, architecture))
        val butterfly13 = Module(new Butterfly(width, binaryPoint, pipeline, architecture))

        butterfly10.io.in0 := butterfly00.io.out(0)
        butterfly10.io.in1 := butterfly01.io.out(0)
        butterfly10.io.twiddle.real := (1 << binaryPoint).S  // W_8^0 = 1
        butterfly10.io.twiddle.imag := 0.S

        butterfly11.io.in0 := butterfly00.io.out(1)
        butterfly11.io.in1 := butterfly01.io.out(1)
        butterfly11.io.twiddle.real := sqrt2_over_2.S         // W_8^1 = sqrt(2)/2 - j*sqrt(2)/2
        butterfly11.io.twiddle.imag := (-sqrt2_over_2).S

        butterfly12.io.in0 := butterfly00.io.out(2)
        butterfly12.io.in1 := butterfly01.io.out(2)
        butterfly12.io.twiddle.real := 0.S                   // W_8^2 = -j
        butterfly12.io.twiddle.imag := (-1 * (1 << binaryPoint)).S
        butterfly13.io.in0 := butterfly00.io.out(3)
        butterfly13.io.in1 := butterfly01.io.out(3)
        butterfly13.io.twiddle.real := (-sqrt2_over_2).S     // W_8^3 = -sqrt(2)/2 - j*sqrt(2)/2
        butterfly13.io.twiddle.imag := (-sqrt2_over_2).S

        // Connect outputs
        io.out(0) := butterfly10.io.out0
        io.out(1) := butterfly11.io.out0
        io.out(2) := butterfly12.io.out0
        io.out(3) := butterfly13.io.out0
        io.out(4) := butterfly10.io.out1
        io.out(5) := butterfly11.io.out1
        io.out(6) := butterfly12.io.out1
        io.out(7) := butterfly13.io.out1
    } else {
        throw new Exception("Unsupported architecture type. Use 'CT' for Cooley-Tukey or 'GS' for Gentleman-Sande.")
    }
}



