package utils

object OtherUtils {
  def calculateNumberOfTwiddles(N : Int): Int = {
    N/2 * (math.log(N)/math.log(2)).toInt
  }
}
