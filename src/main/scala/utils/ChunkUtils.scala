package utils
  
object ChunkUtils {
  // Helper: build byte chunks from complex numbers (real, imag) using fixed point utils
  def getChunksForComplexNumbers(complexNumbers: Seq[(Double, Double)], communicationWidth: Int, width: Int, binaryPoint: Int): Seq[Int] = {
    val chunkCnt = width / communicationWidth
    complexNumbers.flatMap { case (real, imag) =>
      val realFixed = FixedPointUtils.doubleToFixedPointUnsigned(real, width, binaryPoint)
      val imagFixed = FixedPointUtils.doubleToFixedPointUnsigned(imag, width, binaryPoint)

      val realChunks = (0 until chunkCnt).map { i =>
        ((realFixed >> (i * communicationWidth)) & ((BigInt(1) << communicationWidth) - 1)).toInt
      }
      val imagChunks = (0 until chunkCnt).map { i =>
        ((imagFixed >> (i * communicationWidth)) & ((BigInt(1) << communicationWidth) - 1)).toInt
      }

      realChunks ++ imagChunks
    }
  }

  def getComplexNumbersFromChunks(chunks: Seq[Int], communicationWidth: Int, width: Int, binaryPoint: Int): Seq[(Double, Double)] = {
    val chunkCnt = width / communicationWidth
    chunks.grouped(chunkCnt * 2).map { chunkGroup =>
      val realChunks = chunkGroup.take(chunkCnt)
      val imagChunks = chunkGroup.drop(chunkCnt)

      val realFixed = realChunks.zipWithIndex.map { case (chunk, i) => BigInt(chunk) << (i * communicationWidth) }.sum
      val imagFixed = imagChunks.zipWithIndex.map { case (chunk, i) => BigInt(chunk) << (i * communicationWidth) }.sum

      val realDouble = FixedPointUtils.fixedPointToDouble(realFixed, width, binaryPoint)
      val imagDouble = FixedPointUtils.fixedPointToDouble(imagFixed, width, binaryPoint)

      (realDouble, imagDouble)
    }.toSeq
  }
}
