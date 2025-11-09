import breeze.linalg._
import breeze.signal._
import breeze.math.Complex

object ScalaFFTVerifier {

  // Verify N-point FFT using Breeze
  def verifyNPointFFT(inputSequence: Seq[(Double, Double)]): Option[Seq[(Double, Double)]] = {
    if (inputSequence.isEmpty) return None

    try {
      val complexInput = DenseVector(inputSequence.map { case (r, i) => Complex(r, i) }.toArray)
      val fftResult: DenseVector[Complex] = fourierTr(complexInput)

      // Convert Breeze Complex -> (real, imag)
      val output = fftResult.data.map(c => (c.real, c.imag))
      Some(output)
    } catch {
      case e: Exception =>
        println(s"Breeze FFT failed: ${e.getMessage}")
        None
    }
  }

  // Verify N-point IFFT using Breeze directly via iFourierTr
  def verifyNPointIFFT(inputSequence: Seq[(Double, Double)]): Option[Seq[(Double, Double)]] = {
    if (inputSequence.isEmpty) return None

    try {
      val complexInput = DenseVector(inputSequence.map { case (r, i) => Complex(r, i) }.toArray)
      val ifftResult: DenseVector[Complex] = iFourierTr(complexInput)

      // Convert Breeze Complex -> (real, imag)
      val output = ifftResult.data.map(c => (c.real, c.imag))
      Some(output)
    } catch {
      case e: Exception =>
        println(s"Breeze IFFT failed: ${e.getMessage}")
        None
    }
  }

  // Check if Breeze FFT functionality is available.
  // (Basically just tries a small transform to confirm the library is working.)
  def isBreezeAvailable: Boolean = {
    try {
      val test = DenseVector(Complex(1.0, 0.0), Complex(0.0, 0.0))
      val fft = fourierTr(test)
      fft.length == 2
    } catch {
      case _: Throwable => false
    }
  }
}
