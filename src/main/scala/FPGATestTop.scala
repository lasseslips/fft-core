import chisel3._
import chisel3.util._
import scala.math._

class FPGATestTop(val fftSize: Int = 8, val width: Int = 16, val binaryPoint: Int = 8, val pipeline: Boolean = true, val testCases: Seq[FFTTestCase] = Seq()) extends Module {
    def isPow2(x: Int): Boolean = (x & (x - 1)) == 0
    require(fftSize >= 2 && isPow2(fftSize), "FFT size must be a power of 2 and >= 2")
    require(testCases.nonEmpty, "At least one test case must be provided")
    require(testCases.forall(_.size == fftSize), s"All test cases must have FFT size $fftSize")

    val io = IO(new Bundle {
        // Clock and reset are implicit
        
        // Test control signals
        val startTest = Input(Bool())
        val testComplete = Output(Bool())
        
        // LED output for comparison result
        val ledPass = Output(Bool())
        val ledFail = Output(Bool())
        
        // Debug outputs
        val fftProcessing = Output(Bool())
        val comparisonDone = Output(Bool())

        val currentTestIndex = Output(UInt(log2Ceil(testCases.size).W))
    })

    // State machine for test control
    val sIdle :: sFFTProcess :: sCompare :: sDone :: Nil = Enum(4)
    val state = RegInit(sIdle)

    // Individual ROMs for each FFT inputs for each test (to enable parallel access)
    val inputROMs = Seq.tabulate(fftSize) { i =>
        val data = Seq.tabulate(testCases.size) { j => // Single input for each ROM
            testCases(j).inputData(i)
        }
        Module(new ComplexROM(testCases.size, width, binaryPoint, data))
    }

    // Individual ROMs for each expected result (to enable parallel access)
    val expectedROMs = Seq.tabulate(fftSize) { i =>
        val data = Seq.tabulate(testCases.size) { j =>
            testCases(j).expectedData(i)
        }
        Module(new ComplexROM(testCases.size, width, binaryPoint, data))
    }

    // Individual memories for each FFT output (to enable parallel access)
    val outputMems = Seq.tabulate(fftSize) { i =>
        Module(new ComplexMemory(testCases.size, width, binaryPoint))
    }
    
    // FFT core instance
    val fftCore = Module(new ButterflyN(fftSize, width, binaryPoint, pipeline))
    
    // Calculate expected latency based on pipeline configuration
    // For pipelined version, each butterfly stage adds 1 cycle delay
    val fftLatency = if (pipeline) {
        log2Ceil(fftSize).U  // log2(N) stages for radix-2 FFT
    } else {
        1.U  // Combinational, only 1 cycle for output registration
    }
    
    // Registers for managing the delay
    
    // Counter for managing data flow
    val counter = RegInit(0.U(log2Ceil(fftSize * 4).W))
    // Counter for indexing test cases
    val currentTestIndex = RegInit(0.U(log2Ceil(testCases.size).W))
    // Counter for FFT latency
    val delayCounter = RegInit(0.U(log2Ceil(32).W))  // Support up to 32 cycles delay

    // Flag to indicate when output data is valid
    val outputDataValid = RegInit(false.B)
    
    // Comparison logic
    val comparisonPass = RegInit(true.B)
    val allDataCompared = RegInit(false.B)
    
    // ROM and memory connections
    // Each input ROM only has address 0 since it contains single element
    for (i <- 0 until fftSize) {
        inputROMs(i).io.addr := currentTestIndex
        expectedROMs(i).io.addr := currentTestIndex

        // Initialize output memories
        outputMems(i).io.writeEnable := false.B
        outputMems(i).io.writeAddr := currentTestIndex
        outputMems(i).io.writeData := fftCore.io.out(i)
        outputMems(i).io.readAddr := currentTestIndex
    }
    
    // FFT core connections - connect all inputs from individual ROMs
    for (i <- 0 until fftSize) {
        fftCore.io.in(i) := inputROMs(i).io.data
    }
    
    // State machine implementation
    switch(state) {
        is(sIdle) {
            counter := 0.U
            delayCounter := 0.U
            comparisonPass := true.B
            allDataCompared := false.B
            outputDataValid := false.B
            
            when(io.startTest) {
                state := sFFTProcess
                currentTestIndex := currentTestIndex + 1.U
                when(currentTestIndex === (testCases.size - 1).U) {
                    currentTestIndex := 0.U // Wrap around to first test case
                }
            }
        }
        
        is(sFFTProcess) {
            // Wait for FFT latency, then store outputs
            when(delayCounter >= fftLatency) {
                // Store all FFT outputs to their respective memories in parallel
                for (i <- 0 until fftSize) {
                    outputMems(i).io.writeEnable := true.B
                    outputMems(i).io.writeAddr := currentTestIndex
                    outputMems(i).io.writeData := fftCore.io.out(i)
                }
                
                // Move to comparison after one cycle of storing
                when(counter >= 1.U) {
                    outputDataValid := true.B
                    state := sCompare
                    counter := 0.U
                }.otherwise {
                    counter := counter + 1.U
                }
            }
            
            delayCounter := delayCounter + 1.U
        }
        
        is(sCompare) {
            // Compare all FFT outputs with expected results in parallel
            val allSamplesPass = Wire(Vec(fftSize, Bool()))
            
            for (i <- 0 until fftSize) {
                // Get expected and actual results for each output
                val expectedReal = expectedROMs(i).io.data.real
                val expectedImag = expectedROMs(i).io.data.imag
                val actualReal = outputMems(i).io.readData.real
                val actualImag = outputMems(i).io.readData.imag
                
                // Calculate absolute differences
                val realDiff = Mux(actualReal >= expectedReal, 
                                 actualReal - expectedReal, 
                                 expectedReal - actualReal)
                val imagDiff = Mux(actualImag >= expectedImag, 
                                 actualImag - expectedImag, 
                                 expectedImag - actualImag)
                
                // Check if within tolerance (using tolerance from first test case as this should be constant across all testcases)
                val realWithinTolerance = realDiff <= testCases(0).tolerance.S
                val imagWithinTolerance = imagDiff <= testCases(0).tolerance.S
                allSamplesPass(i) := realWithinTolerance && imagWithinTolerance
            }
            
            // All comparisons done in one cycle
            comparisonPass := allSamplesPass.reduce(_ && _)
            allDataCompared := true.B
            state := sDone
        }
        
        is(sDone) {
            // Test complete, hold results until start test is deasserted
            when(!io.startTest) {
                state := sIdle
            }
        }
    }
    
    // Output assignments
    io.testComplete := (state === sDone)
    io.ledPass := (state === sDone) && comparisonPass && allDataCompared
    io.ledFail := (state === sDone) && (!comparisonPass || !allDataCompared)
    io.fftProcessing := (state === sFFTProcess)
    io.comparisonDone := allDataCompared
    io.currentTestIndex := currentTestIndex
}
