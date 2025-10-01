import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import scala.math._

class Butterfly2Spec extends AnyFlatSpec with ChiselScalatestTester {

  "Butterfly2" should "compute basic butterfly operation correctly" in {
    test(new Butterfly2(16, 8)) { dut =>
      println("Testing basic butterfly operation")
      
      // Test case 1: Simple real inputs
      val in0_r = 1.0; val in0_j = 0.0
      val in1_r = 0.5; val in1_j = 0.0
      val tw_r = 1.0; val tw_j = 0.0  // Twiddle = 1 (no rotation)
      
      // Set inputs
      dut.io.in0.real.poke(FixedPointUtils.doubleToFixedPoint(in0_r, 16, 8).S)
      dut.io.in0.imag.poke(FixedPointUtils.doubleToFixedPoint(in0_j, 16, 8).S)
      dut.io.in1.real.poke(FixedPointUtils.doubleToFixedPoint(in1_r, 16, 8).S)
      dut.io.in1.imag.poke(FixedPointUtils.doubleToFixedPoint(in1_j, 16, 8).S)
      dut.io.twiddle.real.poke(FixedPointUtils.doubleToFixedPoint(tw_r, 16, 8).S)
      dut.io.twiddle.imag.poke(FixedPointUtils.doubleToFixedPoint(tw_j, 16, 8).S)
      
      dut.clock.step(1)
      
      // Compute golden model
      val ((exp_out0_r, exp_out0_j), (exp_out1_r, exp_out1_j)) = 
        Butterfly2GoldenModel.butterflyGoldenModel(in0_r, in0_j, in1_r, in1_j, tw_r, tw_j)
      
      // Get actual outputs and convert back to double
      val act_out0_r = FixedPointUtils.fixedPointToDouble(dut.io.out0.real.peekInt(), 16, 8)
      val act_out0_j = FixedPointUtils.fixedPointToDouble(dut.io.out0.imag.peekInt(), 16, 8)
      val act_out1_r = FixedPointUtils.fixedPointToDouble(dut.io.out1.real.peekInt(), 16, 8)
      val act_out1_j = FixedPointUtils.fixedPointToDouble(dut.io.out1.imag.peekInt(), 16, 8)
      
      // Verify with tolerance for fixed-point precision
      val tolerance = 0.01
      assert(abs(act_out0_r - exp_out0_r) < tolerance, s"out0.real: expected $exp_out0_r, got $act_out0_r")
      assert(abs(act_out0_j - exp_out0_j) < tolerance, s"out0.imag: expected $exp_out0_j, got $act_out0_j")
      assert(abs(act_out1_r - exp_out1_r) < tolerance, s"out1.real: expected $exp_out1_r, got $act_out1_r")
      assert(abs(act_out1_j - exp_out1_j) < tolerance, s"out1.imag: expected $exp_out1_j, got $act_out1_j")
      
      println(f"Test 1 passed: out0=($act_out0_r%.3f, $act_out0_j%.3f), out1=($act_out1_r%.3f, $act_out1_j%.3f)")
    }
  }
  
  "Butterfly2" should "handle complex twiddle factors" in {
    test(new Butterfly2(16, 8)) { dut =>
      println("Testing with complex twiddle factors")
      
      // Test case 2: Complex inputs with -j twiddle (90° rotation)
      val in0_r = 1.0; val in0_j = 1.0
      val in1_r = 0.5; val in1_j = -0.5
      val tw_r = 0.0; val tw_j = -1.0  // Twiddle = -j
      
      // Set inputs
      dut.io.in0.real.poke(FixedPointUtils.doubleToFixedPoint(in0_r, 16, 8).S)
      dut.io.in0.imag.poke(FixedPointUtils.doubleToFixedPoint(in0_j, 16, 8).S)
      dut.io.in1.real.poke(FixedPointUtils.doubleToFixedPoint(in1_r, 16, 8).S)
      dut.io.in1.imag.poke(FixedPointUtils.doubleToFixedPoint(in1_j, 16, 8).S)
      dut.io.twiddle.real.poke(FixedPointUtils.doubleToFixedPoint(tw_r, 16, 8).S)
      dut.io.twiddle.imag.poke(FixedPointUtils.doubleToFixedPoint(tw_j, 16, 8).S)
      
      dut.clock.step(1)
      
      // Compute golden model
      val ((exp_out0_r, exp_out0_j), (exp_out1_r, exp_out1_j)) = 
        Butterfly2GoldenModel.butterflyGoldenModel(in0_r, in0_j, in1_r, in1_j, tw_r, tw_j)
      
      // Get actual outputs
      val act_out0_r = FixedPointUtils.fixedPointToDouble(dut.io.out0.real.peekInt(), 16, 8)
      val act_out0_j = FixedPointUtils.fixedPointToDouble(dut.io.out0.imag.peekInt(), 16, 8)
      val act_out1_r = FixedPointUtils.fixedPointToDouble(dut.io.out1.real.peekInt(), 16, 8)
      val act_out1_j = FixedPointUtils.fixedPointToDouble(dut.io.out1.imag.peekInt(), 16, 8)
      
      val tolerance = 0.01
      assert(abs(act_out0_r - exp_out0_r) < tolerance, s"out0.real: expected $exp_out0_r, got $act_out0_r")
      assert(abs(act_out0_j - exp_out0_j) < tolerance, s"out0.imag: expected $exp_out0_j, got $act_out0_j")
      assert(abs(act_out1_r - exp_out1_r) < tolerance, s"out1.real: expected $exp_out1_r, got $act_out1_r")
      assert(abs(act_out1_j - exp_out1_j) < tolerance, s"out1.imag: expected $exp_out1_j, got $act_out1_j")
      
      println(f"Test 2 passed: out0=($act_out0_r%.3f, $act_out0_j%.3f), out1=($act_out1_r%.3f, $act_out1_j%.3f)")
    }
  }
  
  "Butterfly2" should "handle edge cases" in {
    test(new Butterfly2(16, 8)) { dut =>
      println("Testing edge cases")
      
      // Test case 3: Zero inputs
      val in0_r = 0.0; val in0_j = 0.0
      val in1_r = 0.0; val in1_j = 0.0
      val tw_r = 0.707; val tw_j = 0.707  // 45° rotation
      
      dut.io.in0.real.poke(FixedPointUtils.doubleToFixedPoint(in0_r, 16, 8).S)
      dut.io.in0.imag.poke(FixedPointUtils.doubleToFixedPoint(in0_j, 16, 8).S)
      dut.io.in1.real.poke(FixedPointUtils.doubleToFixedPoint(in1_r, 16, 8).S)
      dut.io.in1.imag.poke(FixedPointUtils.doubleToFixedPoint(in1_j, 16, 8).S)
      dut.io.twiddle.real.poke(FixedPointUtils.doubleToFixedPoint(tw_r, 16, 8).S)
      dut.io.twiddle.imag.poke(FixedPointUtils.doubleToFixedPoint(tw_j, 16, 8).S)
      
      dut.clock.step(1)
      
      // Should get all zeros
      val act_out0_r = FixedPointUtils.fixedPointToDouble(dut.io.out0.real.peekInt(), 16, 8)
      val act_out0_j = FixedPointUtils.fixedPointToDouble(dut.io.out0.imag.peekInt(), 16, 8)
      val act_out1_r = FixedPointUtils.fixedPointToDouble(dut.io.out1.real.peekInt(), 16, 8)
      val act_out1_j = FixedPointUtils.fixedPointToDouble(dut.io.out1.imag.peekInt(), 16, 8)
      
      val tolerance = 0.01
      assert(abs(act_out0_r) < tolerance, s"out0.real should be ~0, got $act_out0_r")
      assert(abs(act_out0_j) < tolerance, s"out0.imag should be ~0, got $act_out0_j")
      assert(abs(act_out1_r) < tolerance, s"out1.real should be ~0, got $act_out1_r")
      assert(abs(act_out1_j) < tolerance, s"out1.imag should be ~0, got $act_out1_j")
      
      println("Test 3 passed: Zero input handling")
    }
  }
  
  "Butterfly2" should "verify multiple random test vectors" in {
    test(new Butterfly2(16, 8)) { dut =>
      println("Testing with random vectors")
      
      val random = new scala.util.Random(42) // Fixed seed for reproducibility
      val tolerance = 0.02
      val numTests = 10
      
      for (i <- 0 until numTests) {
        // Generate random test vectors (keeping values reasonable for fixed point)
        val in0_r = (random.nextDouble() - 0.5) * 2
        val in0_j = (random.nextDouble() - 0.5) * 2  
        val in1_r = (random.nextDouble() - 0.5) * 2
        val in1_j = (random.nextDouble() - 0.5) * 2
        val tw_r = (random.nextDouble() - 0.5) * 2
        val tw_j = (random.nextDouble() - 0.5) * 2
        
        // Set inputs
        dut.io.in0.real.poke(FixedPointUtils.doubleToFixedPoint(in0_r, 16, 8).S)
        dut.io.in0.imag.poke(FixedPointUtils.doubleToFixedPoint(in0_j, 16, 8).S)
        dut.io.in1.real.poke(FixedPointUtils.doubleToFixedPoint(in1_r, 16, 8).S)
        dut.io.in1.imag.poke(FixedPointUtils.doubleToFixedPoint(in1_j, 16, 8).S)
        dut.io.twiddle.real.poke(FixedPointUtils.doubleToFixedPoint(tw_r, 16, 8).S)
        dut.io.twiddle.imag.poke(FixedPointUtils.doubleToFixedPoint(tw_j, 16, 8).S)
        
        dut.clock.step(1)
        
        // Compute golden model
        val ((exp_out0_r, exp_out0_j), (exp_out1_r, exp_out1_j)) = 
          Butterfly2GoldenModel.butterflyGoldenModel(in0_r, in0_j, in1_r, in1_j, tw_r, tw_j)
        
        // Get actual outputs
        val act_out0_r = FixedPointUtils.fixedPointToDouble(dut.io.out0.real.peekInt(), 16, 8)
        val act_out0_j = FixedPointUtils.fixedPointToDouble(dut.io.out0.imag.peekInt(), 16, 8)
        val act_out1_r = FixedPointUtils.fixedPointToDouble(dut.io.out1.real.peekInt(), 16, 8)
        val act_out1_j = FixedPointUtils.fixedPointToDouble(dut.io.out1.imag.peekInt(), 16, 8)
        
        // Verify
        assert(abs(act_out0_r - exp_out0_r) < tolerance, 
               f"Vector $i: out0.real expected $exp_out0_r%.3f, got $act_out0_r%.3f")
        assert(abs(act_out0_j - exp_out0_j) < tolerance, 
               f"Vector $i: out0.imag expected $exp_out0_j%.3f, got $act_out0_j%.3f")
        assert(abs(act_out1_r - exp_out1_r) < tolerance, 
               f"Vector $i: out1.real expected $exp_out1_r%.3f, got $act_out1_r%.3f")
        assert(abs(act_out1_j - exp_out1_j) < tolerance, 
               f"Vector $i: out1.imag expected $exp_out1_j%.3f, got $act_out1_j%.3f")
      }
      
      println("Test 4: all %d random test vectors passed".format(numTests))
    }
  }
}
