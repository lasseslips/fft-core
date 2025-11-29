import chisel3._
import chisel3.util._
import scala.math._

class InverseFFT(
    val fftSize: Int = 8, 
    val width: Int = 16, 
    val binaryPoint: Int = 8, 
    val pipeline: PipelineConfig,
    val architecture: String = "GS"
    ) extends Module {
    def isPow2(x: Int): Boolean = (x & (x - 1)) == 0
    require(fftSize >= 2 && isPow2(fftSize), "FFT size must be a power of 2 and >= 2")
    val totalTwiddleCount = ButterflyNUtils.calcTwiddleCount(fftSize)

    val io = IO(new Bundle {
        val in = Input(Vec(fftSize, new ComplexFixedPoint.Complex(width, binaryPoint)))
        val twiddles = Input(Vec(totalTwiddleCount, new ComplexFixedPoint.Complex(width, binaryPoint)))
        val out = Output(Vec(fftSize, new ComplexFixedPoint.Complex(width, binaryPoint)))
    })

    // Instantiate FFT core
    val fftCore = Module(new ButterflyN(fftSize, width, binaryPoint, pipeline, architecture))

    // Forward twiddles from IO to internal FFT core, flipping imaginary sign for IFFT
    for (i <- 0 until totalTwiddleCount) {
        fftCore.io.twiddles(i).real := io.twiddles(i).real
        // Flip the imaginary sign for inverse transform
        fftCore.io.twiddles(i).imag := -io.twiddles(i).imag
    }

    fftCore.io.in := io.in

    // Apply normalization (divide by N) at the output since IFFT = (1/N) * inverse-twiddle FFT
    val shiftAmount = log2Ceil(fftSize)
    for (i <- 0 until fftSize) {
        val normalized = Wire(new ComplexFixedPoint.Complex(width, binaryPoint))
        normalized.real := (fftCore.io.out(i).real >> shiftAmount)
        normalized.imag := (fftCore.io.out(i).imag >> shiftAmount)
        io.out(i) := normalized
    }
}
