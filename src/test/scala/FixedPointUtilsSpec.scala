import org.scalatest.flatspec.AnyFlatSpec

class FixedPointUtilsSpec extends AnyFlatSpec {

  val width = 16
  val binaryPoint = 8

  behavior of "FixedPoint"

  "doubleToFixedPoint" should "correctly convert positive numbers" in {
    val value = 1.5
    val fixed = FixedPointUtils.doubleToFixedPoint(value, width, binaryPoint)
    val expected = (1.5 * (1 << binaryPoint)).round
    assert(fixed == expected)
  }

  it should "correctly convert negative numbers" in {
    val value = -2.25
    val fixed = FixedPointUtils.doubleToFixedPoint(value, width, binaryPoint)
    val expected = (-2.25 * (1 << binaryPoint)).round
    assert(fixed == expected)
  }

  it should "clamp to max positive representable value" in {
    val tooLarge = 1000.0
    val fixed = FixedPointUtils.doubleToFixedPoint(tooLarge, width, binaryPoint)
    val maxVal = (1L << (width - 1)) - 1
    assert(fixed == maxVal)
  }

  it should "clamp to max negative representable value" in {
    val tooNegative = -1000.0
    val fixed = FixedPointUtils.doubleToFixedPoint(tooNegative, width, binaryPoint)
    val minVal = -(1L << (width - 1))
    assert(fixed == minVal)
  }

  "fixedPointToDouble" should "correctly convert fixed point back to double" in {
    val fixed = (1.25 * (1 << binaryPoint)).toInt
    val result = FixedPointUtils.fixedPointToDouble(fixed, width, binaryPoint)
    assert(math.abs(result - 1.25) < 1e-6)
  }

  it should "handle negative fixed point values correctly" in {
    val fixed = (-0.75 * (1 << binaryPoint)).toInt
    val result = FixedPointUtils.fixedPointToDouble(fixed, width, binaryPoint)
    assert(math.abs(result - (-0.75)) < 1e-6)
  }

  it should "round-trip correctly within quantization error" in {
    val testValues = Seq(-5.125, -1.5, -0.25, 0.0, 0.5, 2.75, 10.0)
    for (v <- testValues) {
      val fixed = FixedPointUtils.doubleToFixedPoint(v, width, binaryPoint)
      val back = FixedPointUtils.fixedPointToDouble(fixed, width, binaryPoint)
      val diff = math.abs(v - back)
      assert(diff <= 1.0 / (1 << binaryPoint), s"Round-trip error too large for $v: $diff")
    }
  }
}
