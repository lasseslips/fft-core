package utils

object FixedPointUtils {
  import scala.math._

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

  // Helper function specifically for ROM initialization (returns unsigned two's complement representation)
  def doubleToFixedPointUnsigned(value: Double, width: Int, binaryPoint: Int): BigInt = {
    val scaleFactor = math.pow(2, binaryPoint)
    val scaledValue = (value * scaleFactor).round
    // Handle two's complement for negative values
    val maxVal = (1L << (width - 1)) - 1
    val minVal = -(1L << (width - 1))
    val clampedValue = math.max(minVal, math.min(maxVal, scaledValue))

    // Convert to unsigned two's complement representation for ROM initialization
    if (clampedValue < 0) {
      BigInt((1L << width) + clampedValue) // Two's complement conversion
    } else {
      BigInt(clampedValue)
    }
  }

  def fixedPointToDouble(value: BigInt, width: Int, binaryPoint: Int): Double = {
    val scaleFactor = math.pow(2, binaryPoint)
    // Convert from unsigned back to signed for floating point conversion if needed
    val signedValue = if (value >= (BigInt(1) << (width - 1))) {
      value - (BigInt(1) << width) // Convert back from two's complement
    } else {
      value
    }
    signedValue.toDouble / scaleFactor
  }

  // Calculate appropriate tolerance based on FFT characteristics
  def calculateTolerance(fftSize: Int, width: Int, binaryPoint: Int): Int = {
    // Base quantization error
    val lsb = 1

    // Error accumulation through FFT stages
    val numStages = (math.log(fftSize) / math.log(2)).toInt
    val stageError = lsb * numStages

    // Conservative safety margin
    val safetyFactor = 4

    stageError * safetyFactor
  }

  def calculateToleranceDouble(fftSize: Int, width: Int, binaryPoint: Int): Double = {
    val intTolerance = calculateTolerance(fftSize, width, binaryPoint)
    intTolerance.toDouble / math.pow(2, binaryPoint)
  }
}
