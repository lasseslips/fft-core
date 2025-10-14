import scala.sys.process._
import java.io.{File, PrintWriter}
import scala.io.Source

object PythonFFTVerifier {
  
  // Verify N-point FFT with Python NumPy
  def verifyNPointFFTWithPython(inputSequence: Seq[(Double, Double)]): Option[Seq[(Double, Double)]] = {
    
    if (inputSequence.isEmpty) return None
    
    val N = inputSequence.length
    
    try {
      // Generate Python array initialization
      val pythonInputs = inputSequence.map { case (real, imag) =>
        s"$real + ${imag}j"
      }.mkString(", ")
      
      val scriptContent = s"""
        |import numpy as np
        |
        |# Input sequence (N = $N)
        |x = np.array([$pythonInputs])
        |
        |# Compute N-point FFT
        |X = np.fft.fft(x)
        |
        |# Output results
        |for k in range(len(X)):
        |    print(f'X{k}_real={X[k].real:.15f}')
        |    print(f'X{k}_imag={X[k].imag:.15f}')
        |""".stripMargin
      
      val pythonCommand = s"""python -c "$scriptContent""""
      val result = pythonCommand.!!
      
      val lines = result.split('\n')
      
      // Parse results for all N points
      val fftResults = (0 until N).map { k =>
        val real = lines.find(_.startsWith(s"X${k}_real=")).map(_.split("=")(1).toDouble)
        val imag = lines.find(_.startsWith(s"X${k}_imag=")).map(_.split("=")(1).toDouble)
        
        for {
          r <- real
          i <- imag
        } yield (r, i)
      }
      
      // Return results if all values found
      if (fftResults.forall(_.isDefined)) {
        Some(fftResults.map(_.get))
      } else {
        println("Could not parse all FFT results from Python")
        None
      }
      
    } catch {
      case e: Exception =>
        println(s"Python N-point FFT verification failed: ${e.getMessage}")
        None
    }
  }
    
  // Check if Python with NumPy is available
  def isPythonAvailable: Boolean = {
    try {
      "python -c \"import numpy; print('Python NumPy OK')\"".!!
      true
    } catch {
      case _: Exception => false
    }
  }
}