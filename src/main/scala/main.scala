import chisel3._

/**
 * An object extending App to generate the Verilog code.
 */
object Main extends App {
  println("I will now generate the Verilog file!")
  val fftSize = 8
  val width = 16
  val binaryPoint = 8
  val pipeline = true
  val testCases = Seq(
      FFTTestData.generateTestCase(fftSize, "impulse", width, binaryPoint),
      FFTTestData.generateTestCase(fftSize, "sinusoid", width, binaryPoint),
      FFTTestData.generateTestCase(fftSize, "real_sin", width, binaryPoint),
      FFTTestData.generateTestCase(fftSize, "dc", width, binaryPoint),
      FFTTestData.generateTestCase(fftSize, "random", width, binaryPoint)
  )
  emitVerilog(new FPGATestTop(fftSize, width, binaryPoint, pipeline, testCases), Array("--target-dir", "verilog"))
}
