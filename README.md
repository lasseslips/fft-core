## Fast Fourier Transform Generator Library
This repository contains a library for generating RTL for the Fast Fourier Transform (FFT) algorithm using Chisel, a hardware construction language embedded in Scala.

Created as part of the "[Agile Hardware Design](https://github.com/schoeberl/agile-hw)" ([02201](https://kurser.dtu.dk/course/02201)) course at DTU.

### Creators : Group 8
- Andreas Lildballe (s214387, [DreasL02](https://github.com/DreasL02))
- Lasse Slipsager (s224007, [lasseslips](https://github.com/lasseslips))
- Henrique Agostinho Loureiro dos Santos de Oliveira (s252981)

[Github commits](https://github.com/lasseslips/fft-core/commits/master/) accurately reflect individual contributions.

Elements of the UART implementation has previously been handed in and is not original work of the creators. See [the communication folder](./src/main/scala/communication/README.md) for more details.


### Features
- Parameterizable pipelined Butterfly FFT.
  - Supports both Decimation-In-Time (DIT) and Decimation-In-Frequency (DIF) architectures using respectively Cooley-Tukey and Gentleman-Sande butterflies.
  - Can be configured for different FFT sizes (powers of two).
  - Supports fixed-point number representation with customizable bit widths and fractional bits.
  - Uses a recursive architecture for easy scalability.
  - Supports both forward and inverse FFT operations.
- Modular design for easy integration into larger systems
  - Supports Ready/Valid handshaking for streaming data interfaces.
  - Possibility to supply inputs through a UART interface.
- Comprehensive testbench for functional verification using ChiselTest.
- FPGA test platform support for real-world performance evaluation.
- Optional scala and tcl building and synthesis scripts for evaluating performance on FPGA targets using Vivado.
  - Depends on having Vivado installed and accessible through WSL on Windows systems.

### Repository Structure

### Getting Started


## Motivation

## Algorithm Explanation
The discrete fourier transform is defined by the equation:

$ X(k) = \sum_{n=0}^{N-1} x(n) \cdot e^{-j \frac{2 \pi}{N} k n} \quad \text{for } k = 0, 1, \ldots, N-1 $

where:
- $X(k)$ is the output frequency component at index $k$,
- $x(n)$ is the input time-domain sample at index $n$,
- $N$ is the total number of samples.

This sum can be computed directly, but it has a time complexity of O(N^2), which is inefficient for large N. The Fast Fourier Transform (FFT) algorithm reduces this complexity to O(N log N) by exploiting symmetries and redundancies in the computation.
This is achieved through a divide-and-conquer approach, recursively breaking down the DFT into smaller DFTs.

By splitting the sum into different parts, we can reduce the number of computations needed. One common method is the Cooley-Tukey algorithm, which recursively divides the DFT into smaller DFTs of even and odd indexed samples:

$ X(k) = \sum_{m=0}^{N/2-1} x(2m) \cdot e^{-j \frac{2 \pi}{N} k (2m)} + \sum_{m=0}^{N/2-1} x(2m+1) \cdot e^{-j \frac{2 \pi}{N} k (2m+1)} $

The next step can perhaps easily be seen by factoring out the exponentials:

$ X(k) = \sum_{m=0}^{N/2-1} x(2m) \cdot e^{-j \frac{2 \pi}{N/2} k m} + e^{-j \frac{2 \pi}{N} k} \cdot \sum_{m=0}^{N/2-1} x(2m+1) \cdot e^{-j \frac{2 \pi}{N/2} k m} $

Giving us two smaller DFTs of size N/2:

$ E(k) = \sum_{m=0}^{N/2-1} x(2m) \cdot e^{-j \frac{2 \pi}{N/2} k m} $

$ O(k) = \sum_{m=0}^{N/2-1} x(2m+1) \cdot e^{-j \frac{2 \pi}{N/2} k m} $

This can be further simplified using the periodicity and symmetry properties of the complex exponential function, leading to the use of "twiddle factors" which are precomputed complex exponentials that help combine the results of the smaller DFTs efficiently.

$ W_N^k = e^{-j \frac{2 \pi}{N} k} $

$ X(k) = E(k) + W_N^k \cdot O(k) $



Something about butterflys

### Butterfly architectures
The butterfly components are defined as the basic computing units, operating on two input samples, a twiddle factor, and producing two output samples. Two common architectures are the Cooley-Tukey Butterfly and the Gentleman-Sande Butterfly.

The former operates on the inputs $x_0$ and $x_1$ as: 

$ X_0 = x_0 + W_N^k \cdot x_1 $

$ X_1 = x_0 - W_N^k \cdot x_1 $

while the Gentleman-Sande Butterfly operates as:

$ X_0 = x_0 + x_1 $

$ X_1 = (x_0 - x_1) \cdot W_N^k $

Expanding the complex operations lay bare the hardware requirements for multipliers and adders/subtractors. A complex addition is defined as:

$(a + jb) + (c + jd) = (a + c) + j(b + d)$

A complex multiplication is defined as:

$(a + jb) \cdot (c + jd) = (ac - bd) + j(ad + bc)$

Applying these to Cooley-Tukey gives:

$Re\{X_0\} = Re\{x_0\} + Re\{W_N^k\} \cdot Re\{x_1\} - Im\{W_N^k\} \cdot Im\{x_1\}$

$Im\{X_0\} = Im\{x_0\} + Re\{W_N^k\} \cdot Im\{x_1\} + Im\{W_N^k\} \cdot Re\{x_1\}$

$Re\{X_1\} = Re\{x_0\} - Re\{W_N^k\} \cdot Re\{x_1\} + Im\{W_N^k\} \cdot Im\{x_1\}$

$Im\{X_1\} = Im\{x_0\} - Re\{W_N^k\} \cdot Im\{x_1\} - Im\{W_N^k\} \cdot Re\{x_1\}$

This contains four unique additions/subtractions and four multiplications.

And for Gentleman-Sande:

$Re\{X_0\} = Re\{x_0\} + Re\{x_1\}$

$Im\{X_0\} = Im\{x_0\} + Im\{x_1\}$

$Re\{X_1\} = (Re\{x_0\} - Re\{x_1\}) \cdot Re\{W_N^k\} - (Im\{x_0\} - Im\{x_1\}) \cdot Im\{W_N^k\}$

$Im\{X_1\} = (Re\{x_0\} - Re\{x_1\}) \cdot Im\{W_N^k\} + (Im\{x_0\} - Im\{x_1\}) \cdot Re\{W_N^k\}$

This also contains four unique additions/subtractions and four multiplications.

The arithmetic cost of the two approaches is clearly the same. However, the data flow and ordering of inputs and outputs differ, which can impact the overall FFT architecture.


## Number Representation
As floating-point arithmetic is an expensive operation in hardware, this design opts for fixed-point representation of numbers. Fixed-point numbers are represented with a total bit width and a specified number of fractional bits, allowing for efficient arithmetic operations while maintaining a balance between range and precision. It can be represented in Q(m.n) format, where m is the number of integer bits (including the sign bit) and n is the number of fractional bits.


The use of fixed-point arithmetic introduces quantization errors, which can accumulate through the stages of the FFT. 

## Design and Implementation

## Testing

## Interfacing 

## Synthesis and Performance

## Project Agility
As the project was conducted as part of the Agile Hardware Design course, we adopted agile methodologies to manage our development process effectively. We utilized iterative development and continuous integration practices to ensure that our design evolved in response to the project requirements and time.

This is reflected in the current structure of the Butterfly modules. Files such as [Butterfly2.scala](./src/main/scala/Butterfly2.scala), [Butterfly4.scala](./src/main/scala/Butterfly4.scala), and [Butterfly8.scala](./src/main/scala/Butterfly8.scala) were developed iterating upon the previous and allowed for formalizing patterns and abstractions that could be reused in the final [ButterflyN.scala](./src/main/scala/ButterflyN.scala) implementation. The older patterns allowed for quick validation once the generative implementation was complete as it could be tested with known-good smaller instances.

In [our continuous integration pipeline](.github/workflows/scala.yml), we set up automated testing using ChiselTest to validate our FFT implementation on every commit. This would in theory allow us to catch mistakes early and ensure that new features did not break existing functionality, but it only actually caught something during a brief moment where Python was required for a test and not installed on the CI runners.

This is probabily due to the relatively small group size and project scope, which made communication and coordination easier without the need for extensive agile practices. However, the experience provided valuable insights into how agile methodologies can be applied in hardware design projects.

One aspect that was a downside of the continous integration setup was that it developed a very localized setup, where something was only commited once it fully worked (largely due to the tests being developed alongside, if not before, the actual implementation). Perhaps something like branching could be used in future projects, but that also adds overhead. 
## Conclusion and Future Work

## References

https://dsp-book.narod.ru/FFTBB/0270_PDF_C03.pdf