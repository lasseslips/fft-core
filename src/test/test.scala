import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class HelloTest extends AnyFlatSpec with ChiselScalatestTester {

  "Hello accumulator" should "accumulate when enabled" in {
    test(new Hello()) { dut =>
      println("Testing accumulator functionality")
      
      // Initialize all signals
      dut.io.clear.poke(false.B)
      dut.io.enable.poke(false.B)
      dut.io.din.poke(0.U)
      dut.clock.step(1)
      dut.io.dout.expect(0.U)
      
      // Test accumulation with enable
      dut.io.enable.poke(true.B)
      dut.io.din.poke(5.U)
      dut.clock.step(1)
      dut.io.dout.expect(5.U)
      
      // Add more to accumulator
      dut.io.din.poke(3.U)
      dut.clock.step(1)
      dut.io.dout.expect(8.U)
      
      // Add another value
      dut.io.din.poke(7.U)
      dut.clock.step(1)
      dut.io.dout.expect(15.U)
    }
  }
  
  "Hello accumulator" should "not accumulate when disabled" in {
    test(new Hello()) { dut =>
      println("Testing enable functionality")
      
      // Start with accumulator at 0
      dut.io.clear.poke(true.B)
      dut.io.enable.poke(false.B)
      dut.io.din.poke(0.U)
      dut.clock.step(1)
      dut.io.dout.expect(0.U)
      
      // Release clear and accumulate a value
      dut.io.clear.poke(false.B)
      dut.io.enable.poke(true.B)
      dut.io.din.poke(10.U)
      dut.clock.step(1)
      dut.io.dout.expect(10.U)
      
      // Disable accumulator - value should not change
      dut.io.enable.poke(false.B)
      dut.io.din.poke(20.U)
      dut.clock.step(1)
      dut.io.dout.expect(10.U) // Should remain 10
      
      // Still disabled - value should not change
      dut.io.din.poke(30.U)
      dut.clock.step(1)
      dut.io.dout.expect(10.U) // Should still be 10
    }
  }
  
  "Hello accumulator" should "clear when clear signal is asserted" in {
    test(new Hello()) { dut =>
      println("Testing clear functionality")
      
      // Build up some value in accumulator
      dut.io.clear.poke(false.B)
      dut.io.enable.poke(true.B)
      dut.io.din.poke(15.U)
      dut.clock.step(1)
      dut.io.dout.expect(15.U)
      
      dut.io.din.poke(25.U)
      dut.clock.step(1)
      dut.io.dout.expect(40.U)
      
      // Clear the accumulator
      dut.io.clear.poke(true.B)
      dut.clock.step(1)
      dut.io.dout.expect(0.U)
      
      // Clear should override enable
      dut.io.clear.poke(true.B)
      dut.io.enable.poke(true.B)
      dut.io.din.poke(100.U)
      dut.clock.step(1)
      dut.io.dout.expect(0.U) // Should remain 0 due to clear
    }
  }
  
  "Hello accumulator" should "handle clear and enable priority correctly" in {
    test(new Hello()) { dut =>
      println("Testing clear/enable priority")
      
      // Start clean
      dut.io.clear.poke(true.B)
      dut.io.enable.poke(false.B)
      dut.io.din.poke(0.U)
      dut.clock.step(1)
      dut.io.dout.expect(0.U)
      
      // Release clear, enable accumulation
      dut.io.clear.poke(false.B)
      dut.io.enable.poke(true.B)
      dut.io.din.poke(50.U)
      dut.clock.step(1)
      dut.io.dout.expect(50.U)
      
      // Assert both clear and enable - clear should win
      dut.io.clear.poke(true.B)
      dut.io.enable.poke(true.B)
      dut.io.din.poke(25.U)
      dut.clock.step(1)
      dut.io.dout.expect(0.U)
      
      // Release clear with enable still high - should start accumulating again
      dut.io.clear.poke(false.B)
      dut.io.enable.poke(true.B)
      dut.io.din.poke(75.U)
      dut.clock.step(1)
      dut.io.dout.expect(75.U)
    }
  }
}
