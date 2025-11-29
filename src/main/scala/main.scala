import chisel3._
import verifier.FFTTestData

object Main extends App {
  println("I will now generate the Verilog file!")
  val fftSize = 16
  val width = 16
  val binaryPoint = 8
  val pipeline = PipelineConfig(
      pipelineComplexMultiplication = false,
      pipelineButterflyFirstPart = true,
      pipelineButterflySecondPart = true
  )
  val architecture = "GS"

  val baudRate = 115200
  val clockFreq = 100_000_000 // 100 MHz
  val testCases = Seq(
      FFTTestData.generateTestCase(fftSize, "impulse", width, binaryPoint),
      FFTTestData.generateTestCase(fftSize, "sinusoid", width, binaryPoint),
      FFTTestData.generateTestCase(fftSize, "real_sin", width, binaryPoint),
      FFTTestData.generateTestCase(fftSize, "dc", width, binaryPoint),
      FFTTestData.generateTestCase(fftSize, "random", width, binaryPoint)
  )
  //emitVerilog(new FPGATestTop(fftSize, width, binaryPoint, pipeline, testCases, architecture), Array("--target-dir", "verilog"))
  emitVerilog(new UartedFFT(baudRate, clockFreq, width, binaryPoint, fftSize, pipeline, architecture), Array("--target-dir", "verilog"))
}
