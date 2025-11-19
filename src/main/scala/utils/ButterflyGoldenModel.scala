package utils

object ButterflyGoldenModel {

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
