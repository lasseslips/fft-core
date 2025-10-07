import chisel3._
import chisel3.util._

// Butterfly4 module for radix-4 FFT
class Butterfly4(val width: Int, val binaryPoint: Int) extends Module {
    val io = IO(new Bundle {
        // Input complex numbers
        val in = Input(Vec(4, new ComplexFixedPoint.Complex(width, binaryPoint)))
        // Output complex numbers
        val out = Output(Vec(4, new ComplexFixedPoint.Complex(width, binaryPoint)))
    })

    // Implement proper 4-point FFT using decimation-in-time
    // Stage 1: Even/odd decomposition into 2-point FFTs
    val evenFFT = Module(new Butterfly2(width, binaryPoint))
    val oddFFT = Module(new Butterfly2(width, binaryPoint))
    
    // Even indices: x[0], x[2]
    evenFFT.io.in0 := io.in(0)
    evenFFT.io.in1 := io.in(2)
    evenFFT.io.twiddle.real := (1 << binaryPoint).S  // W_2^0 = 1
    evenFFT.io.twiddle.imag := 0.S
    println("Twiddle for even FFT: real=" + (1 << binaryPoint).toString + ", imag=0")
    
    // Odd indices: x[1], x[3]  
    oddFFT.io.in0 := io.in(1)
    oddFFT.io.in1 := io.in(3)
    oddFFT.io.twiddle.real := (1 << binaryPoint).S   // W_2^0 = 1
    oddFFT.io.twiddle.imag := 0.S
    println("Twiddle for odd FFT: real=" + (1 << binaryPoint).toString + ", imag=0")
    
    // Stage 2: Combine with proper twiddle factors
    val combine0 = Module(new Butterfly2(width, binaryPoint))
    val combine1 = Module(new Butterfly2(width, binaryPoint))
    
    // First combination: k=0
    combine0.io.in0 := evenFFT.io.out0
    combine0.io.in1 := oddFFT.io.out0
    combine0.io.twiddle.real := (1 << binaryPoint).S  // W_4^0 = 1
    combine0.io.twiddle.imag := 0.S
    println("Twiddle for k=0: real=" + (1 << binaryPoint).toString + ", imag=0")
    
    // Second combination: k=1
    combine1.io.in0 := evenFFT.io.out1
    combine1.io.in1 := oddFFT.io.out1
    combine1.io.twiddle.real := 0.S                   // W_4^1 = -j
    combine1.io.twiddle.imag := (-1 * (1 << binaryPoint)).S
    println("Twiddle for k=1: real=0, imag=" + (-1 * (1 << binaryPoint)).toString)
    
    // Connect outputs
    io.out(0) := combine0.io.out0  // X[0] = Even[0] + W_4^0 * Odd[0]
    io.out(2) := combine0.io.out1  // X[2] = Even[0] - W_4^0 * Odd[0]
    io.out(1) := combine1.io.out0  // X[1] = Even[1] + W_4^1 * Odd[1]
    io.out(3) := combine1.io.out1  // X[3] = Even[1] - W_4^1 * Odd[1]
}



