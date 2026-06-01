<!-- Back to top -->
<a id="readme-top"></a>
<!-- SHIELDS -->
￼ ￼
<!-- PROJECT HEADER --> <br /> <div align="center"> <h1 align="center">RV32I Emulator</h1> <p align="center"> A software emulator of the RISC-V RV32I base ISA, written in Java — built to bridge the gap between high-level programming and bare-metal embedded systems. <br /> <br /> <a href="#about-the-project">About</a> &middot; <a href="#getting-started">Getting Started</a> &middot; <a href="#usage">Usage</a> &middot; <a href="#roadmap">Roadmap</a> </p> </div>

<!-- TABLE OF CONTENTS --> <details> <summary>Table of Contents</summary> <ol> <li><a href="#about-the-project">About The Project</a></li> <li><a href="#what-i-learned">What I Learned</a></li> <li><a href="#architecture">Architecture</a></li> <li><a href="#built-with">Built With</a></li> <li><a href="#getting-started">Getting Started</a></li> <li><a href="#usage">Usage</a></li> <li><a href="#roadmap">Roadmap</a></li> <li><a href="#license">License</a></li> <li><a href="#contact">Contact</a></li> <li><a href="#acknowledgments">Acknowledgments</a></li> </ol> </details>

<!-- ABOUT -->
About The Project
I'm a UC Berkeley student who came into this project knowing Java from high school but wanting to break into embedded systems. After starting to learn C and taking computer architecture through self study, I wanted a project that forced me to actually understand what happens at the hardware level.
So I built a complete software emulator of the RISC-V RV32I base instruction set architecture from scratch in Java.
RISC-V is a free, open-source ISA used in real embedded chips and is increasingly taught in university computer architecture courses. RV32I is its 32-bit integer base
What this emulator does:
	•	Fetches, decodes, and executes all 47 RV32I instructions with cycle-accurate simulation
	•	Implements a memory-mapped peripheral bus with UART, GPIO, and a timer — the same pattern used in real microcontrollers like STM32 and SiFive boards
	•	Handles machine-mode interrupts and the full M-mode CSR file (mstatus, mtvec, mepc, mcause, mie, mip, mcycle)
	•	Includes a built-in two-pass assembler so programs can be written and run without any external toolchain
	•	Loads real ELF32 binaries compiled with riscv32-unknown-elf-gcc — meaning actual C programs run on it
	•	Ships with five built-in demo programs: Hello World, Fibonacci, Factorial, GPIO blinky, and a timer interrupt demo
Building this taught me more about how CPUs, memory buses, and peripherals actually work than any textbook did so I’d recommend trying to build this yourself if you also want to get started with computer arcitecure.
<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- WHAT I LEARNED -->
What I Learned
This project was as much about learning as it was about building. Some things that genuinely surprised me along the way:
	•	Instruction encoding is intricate. The RV32I B-type and J-type immediates have their bits scrambled across the instruction word in a non-obvious order. Implementing the decode made me understand why — it minimizes the hardware needed to route wires to the sign-extend unit.
	•	Memory-mapped I/O.
	•	Interrupts require careful state management. Getting MRET, mstatus.MIE, and MPIE right — saving and restoring interrupt enable state atomically — took the most debugging of anything in the project.
	•	A two-pass assembler is surprisingly approachable. Forward label resolution (a branch to a label that hasn't been defined yet) requires two passes: one to collect label positions, one to encode. Implementing this made assemblers feel much less like magic.
	•	Sign extension is everywhere. Almost every immediate in RV32I is sign-extended. Getting this wrong produces bugs that are very hard to spot — several of my early test failures came down to a missing >> 19 arithmetic shift.
<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- ARCHITECTURE -->
Architecture
Emulator
├── SystemBus                  — address decoder, little-endian word access
│   ├── ROM (128 KB @ 0x00000000)   — program flash
│   ├── RAM (128 KB @ 0x20000000)   — stack, heap, data
│   ├── UartPeripheral  @ 0x40000000
│   ├── GpioPeripheral  @ 0x50000000
│   └── TimerPeripheral @ 0x60000000  — mtime/mtimecmp compare-match
├── CSRFile                    — mstatus, mtvec, mepc, mcause, mip, mie, mcycle
├── InterruptController        — polls peripherals, routes IRQs to CPU
├── ExecutionTracer            — ring-buffer of last N instructions (post-mortem debug)
└── RV32ICore                  — fetch → decode → execute → writeback
Memory map:
Address
Region
Size
0x00000000
ROM
128 KB
0x20000000
RAM
128 KB
0x40000000
UART
256 B
0x50000000
GPIO
256 B
0x60000000
Timer
256 B
<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- BUILT WITH -->
Built With
	•	￼ — core language (Java 17+)
	•	￼ — build system and dependency management
	•	￼ — 17 integration tests covering every major instruction group
	•	RISC-V RV32I ISA — spec here
<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- GETTING STARTED -->
Getting Started
Prerequisites
	•	Java 17 or higher
	•	Maven 3.6 or higher
java -version   # should say 17+
mvn -version    # should say Apache Maven 3.x
Don't have them? Install via:
# Mac
brew install openjdk@21 maven

# Ubuntu/Debian
sudo apt install openjdk-21-jdk maven
Installation
	1	Clone the repogit clone https://github.com/your_username/rv32i-emulator.git
	2	cd rv32i-emulator
	3	
	4	Build the fat jarmvn package -q
	5	
	6	Run the tests to confirm everything worksmvn test
	7	You should see 17 tests passing.
	8	Run a demojava -jar target/rv32i-emulator-1.0.0-jar-with-dependencies.jar --demo hello
	9	
<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- USAGE -->
Usage
Built-in demos
No external toolchain needed — these are assembled at runtime by the built-in Java assembler.
# Hello World via UART
java -jar target/rv32i-emulator-1.0.0-jar-with-dependencies.jar --demo hello

# First N Fibonacci numbers
java -jar target/rv32i-emulator-1.0.0-jar-with-dependencies.jar --demo fib 12

# N factorial (computed via repeated addition — no MUL in RV32I base)
java -jar target/rv32i-emulator-1.0.0-jar-with-dependencies.jar --demo factorial 7

# GPIO LED blink — watch pin 0 toggle
java -jar target/rv32i-emulator-1.0.0-jar-with-dependencies.jar --demo blinky 5

# Timer interrupt demo — fires N interrupts, prints ! for each
java -jar target/rv32i-emulator-1.0.0-jar-with-dependencies.jar --demo timer 5
Load a real compiled C program
Requires riscv32-unknown-elf-gcc:
# Compile for bare-metal RV32I
riscv32-unknown-elf-gcc -march=rv32i -mabi=ilp32 -nostdlib -o program.elf program.c

# Load and run
java -jar target/rv32i-emulator-1.0.0-jar-with-dependencies.jar program.elf
The loader auto-detects ELF32 vs raw .bin and handles .data, .bss, and multi-segment programs.
Useful flags
Flag
Effect
--stats
Print cycle count, wall time, and MIPS after execution
--trace
Dump the last 1024 instructions executed (great for debugging)
--max-cycles N
Hard cycle limit — prevents infinite loops hanging forever
# Example: factorial with stats
java -jar target/rv32i-emulator-1.0.0-jar-with-dependencies.jar --demo factorial 10 --stats
<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- ROADMAP -->
Roadmap
	•	[x] All 47 RV32I base instructions
	•	[x] Memory-mapped UART, GPIO, Timer peripherals
	•	[x] Machine-mode interrupts and full CSR file
	•	[x] Built-in two-pass assembler
	•	[x] ELF32 binary loader
	•	[x] Execution tracer and post-mortem debug
	•	[x] 17 integration tests
	•	[ ] RV32M extension (MUL, DIV, REM) — need to add in oredr to run more complex C programs
	•	[ ] UART RX — keyboard input support
	•	[ ] Interactive debugger (step, breakpoints, register watch)
	•	[ ] Simple framebuffer peripheral for graphics output (working on this currently)
<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- LICENSE -->
License
Distributed under the MIT License. See LICENSE for more information.
<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- CONTACT -->
Contact
Robel Kumsa — LinkedIn
Project Link: https://github.com/your_username/rv32i-emulator
<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- ACKNOWLEDGMENTS -->
Acknowledgments
Resources that helped me out while building this:
	•	RISC-V ISA Specification 
	•	Computer Organization and Design RISC-V Edition
	•	CS61C at UC Berkeley
	•	RISC-V from scratch
	•	Shields.io 
	•	Best-README-Template
<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- MARKDOWN LINKS -->