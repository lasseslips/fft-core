import scala.math._

object Butterfly2GoldenModel {

  // Golden model for butterfly computation
  def butterflyGoldenModel(in0_real: Double, in0_imag: Double, 
                          in1_real: Double, in1_imag: Double,
                          tw_real: Double, tw_imag: Double): 
                          ((Double, Double), (Double, Double)) = {
    // out0 = in0 + in1
    val out0_real = in0_real + in1_real
    val out0_imag = in0_imag + in1_imag
    
    // out1 = (in0 - in1) * twiddle
    val diff_real = in0_real - in1_real
    val diff_imag = in0_imag - in1_imag
    
    // Complex multiplication: (a + jb) * (c + jd) = (ac - bd) + j(ad + bc)
    val out1_real = diff_real * tw_real - diff_imag * tw_imag
    val out1_imag = diff_real * tw_imag + diff_imag * tw_real
    
    ((out0_real, out0_imag), (out1_real, out1_imag))
  }
}

object FixedPointUtils {
  
  // Helper function to convert double to fixed point value (returns signed representation)
  def doubleToFixedPoint(value: Double, width: Int, binaryPoint: Int): BigInt = {
    val scaleFactor = math.pow(2, binaryPoint)
    val scaledValue = (value * scaleFactor).round
    // Handle two's complement for negative values
    val maxVal = (1L << (width - 1)) - 1
    val minVal = -(1L << (width - 1))
    val clampedValue = math.max(minVal, math.min(maxVal, scaledValue))
    // Return the signed value directly
    clampedValue
  }
  
  // Helper function to convert fixed point to double (for SInt values)
  def fixedPointToDouble(value: BigInt, width: Int, binaryPoint: Int): Double = {
    val scaleFactor = math.pow(2, binaryPoint)
    // For SInt, peekInt() already returns the signed value
    value.toDouble / scaleFactor
  }
}
