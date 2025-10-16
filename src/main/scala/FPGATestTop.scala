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
    val sIdle :: sLoadInput :: sFFTProcess :: sLoadResult :: sCompare :: sDone :: Nil = Enum(6)
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

    // Assign twiddle factors
    val totalTwiddleCount = ButterflyNUtils.calcTwiddleCount(fftSize)
    val twiddlesInt = ButterflyNUtils.generateTwiddleFactors(fftSize)
    val twiddles = ButterflyNUtils.twiddlesToFixedPoint(twiddlesInt, width, binaryPoint)
    for (i <- 0 until totalTwiddleCount) {
        fftCore.io.twiddles(i).real := twiddles(i)._1.S
        fftCore.io.twiddles(i).imag := twiddles(i)._2.S
    }

    val comparators = Seq.tabulate(fftSize) { i =>
        Module(new Comparator(width, binaryPoint, testCases(0).tolerance, pipeline)) // Use tolerance from first test case (should be same for all)
    }
    
    // Calculate expected latency based on pipeline configuration
    // For pipelined version, each butterfly stage adds 1 cycle delay
    val fftLatency = ButterflyNUtils.getLatency(fftSize, pipeline).U

    
    // Counter for indexing test cases
    val currentTestIndex = RegInit(0.U(log2Ceil(testCases.size).W))
    // Counter for FFT latency
    val delayCounter = RegInit(0.U(log2Ceil(32).W))  // Support up to 32 cycles delay

    val inputsRegs = RegInit(VecInit(Seq.fill(fftSize)(0.U.asTypeOf(new ComplexFixedPoint.Complex(width, binaryPoint)))))
    
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

        // Connect comparators
        comparators(i).io.in0 := expectedROMs(i).io.data
        comparators(i).io.in1 := outputMems(i).io.readData
    }
    
    
    // FFT core connections - connect all inputs from individual ROMs (using registers to break combinational path)
    for (i <- 0 until fftSize) {
        fftCore.io.in(i) := inputsRegs(i)
    }
    
    // State machine implementation
    switch(state) {
        is(sIdle) {
            delayCounter := 0.U
            comparisonPass := true.B
            allDataCompared := false.B
            
            when(io.startTest) {
                state := sLoadInput
                currentTestIndex := currentTestIndex + 1.U
                when(currentTestIndex === (testCases.size - 1).U) {
                    currentTestIndex := 0.U // Wrap around to first test case
                }
            }
        }

        is(sLoadInput) {
            state := sFFTProcess
            for (i <- 0 until fftSize) {
                inputsRegs(i) := inputROMs(i).io.data
            }
        }
        
        is(sFFTProcess) {
            // Wait for FFT latency, then store outputs
            when(delayCounter >= fftLatency) {
                // Store all FFT outputs to their respective memories in parallel
                for (i <- 0 until fftSize) {
                    outputMems(i).io.writeEnable := true.B
                }
                state := sLoadResult
            }
            delayCounter := delayCounter + 1.U
        }

        is(sLoadResult) {
            state := sCompare
        }

        is(sCompare) {
            // Compare all FFT outputs with expected results in parallel
            val allSamplesPass = Wire(Vec(fftSize, Bool()))
            
            for (i <- 0 until fftSize) {
                allSamplesPass(i) := comparators(i).io.equal
            }
            
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
