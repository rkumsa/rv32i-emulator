package com.rv32i;

import com.rv32i.cpu.CSRFile;
import com.rv32i.cpu.InterruptController;
import com.rv32i.cpu.RV32ICore;
import com.rv32i.memory.RAM;
import com.rv32i.memory.ROM;
import com.rv32i.memory.SystemBus;
import com.rv32i.peripheral.GpioPeripheral;
import com.rv32i.peripheral.TimerPeripheral;
import com.rv32i.peripheral.UartPeripheral;
import com.rv32i.trace.ExecutionTracer;

import java.io.PrintStream;

/**
 * Emulator — the top-level facade that wires all components together.
 *
 * Component hierarchy:
 *
 *   Emulator
 *   ├── SystemBus
 *   │   ├── ROM (128 KB, 0x00000000)
 *   │   ├── RAM (128 KB, 0x20000000)
 *   │   ├── UartPeripheral (0x40000000)
 *   │   ├── GpioPeripheral (0x50000000)
 *   │   └── TimerPeripheral (0x60000000)
 *   ├── CSRFile
 *   ├── InterruptController
 *   ├── ExecutionTracer
 *   └── RV32ICore
 *
 * This mirrors how a real SoC is structured: shared bus, memory-mapped
 * peripherals, a CPU core, and a platform-level interrupt controller.
 */
public class Emulator {

    private final SystemBus          bus;
    private final ROM                rom;
    private final RAM                ram;
    private final UartPeripheral     uart;
    private final GpioPeripheral     gpio;
    private final TimerPeripheral    timer;
    private final CSRFile            csr;
    private final InterruptController irqCtrl;
    private final ExecutionTracer    tracer;
    private final RV32ICore          cpu;

    // Stats
    private long runStartTime;

    public Emulator() {
        this(System.out);
    }

    public Emulator(PrintStream uartOut) {
        rom   = new ROM(SystemBus.ROM_BASE, SystemBus.ROM_SIZE);
        ram   = new RAM(SystemBus.RAM_BASE, SystemBus.RAM_SIZE);
        uart  = new UartPeripheral(uartOut);
        gpio  = new GpioPeripheral();
        timer = new TimerPeripheral();

        bus = new SystemBus(rom, ram);
        bus.addPeripheral(uart);
        bus.addPeripheral(gpio);
        bus.addPeripheral(timer);

        csr     = new CSRFile();
        irqCtrl = new InterruptController(timer, uart, gpio);
        tracer  = new ExecutionTracer(1024); // ring buffer of 1024 instructions

        cpu = new RV32ICore(bus, csr, irqCtrl, tracer, SystemBus.ROM_BASE);

        // Default ECALL handler: Linux-style ABI (a7 = syscall number)
        cpu.setEcallHandler((a0, a1, a2, a7, core) -> {
            return switch (a7) {
                case 1  -> { // print int
                    uartOut.print(a0);
                    yield false;
                }
                case 4  -> { // print string from RAM
                    StringBuilder sb = new StringBuilder();
                    int addr = a0;
                    int b;
                    while ((b = bus.readByte(addr++)) != 0) sb.append((char) b);
                    uartOut.print(sb);
                    yield false;
                }
                case 10 -> true; // exit
                case 93 -> {     // exit with code (Linux ABI)
                    uartOut.println("[EMU] Program exited with code " + a0);
                    yield true;
                }
                default -> {
                    uartOut.println("[EMU] Unknown ECALL a7=" + a7);
                    yield false;
                }
            };
        });

        // Wire up GPIO pin-change listener for blinky demo
        gpio.addPinChangeListener((pin, value) -> {
            if (pin == 0) { // LED pin
                uartOut.println("[GPIO] LED pin 0 → " + (value ? "HIGH (ON)" : "LOW (OFF)"));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Execution control
    // -------------------------------------------------------------------------

    /**
     * Run until halted or maxCycles exceeded.
     * @return number of cycles executed
     */
    public long run(long maxCycles) {
        runStartTime = System.nanoTime();
        long start = cpu.getCycle();
        while (!cpu.isHalted() && (cpu.getCycle() - start) < maxCycles) {
            cpu.step();
        }
        return cpu.getCycle() - start;
    }

    /**
     * Step exactly one instruction.
     */
    public void step() {
        cpu.step();
    }

    /**
     * Reset the CPU and RAM, keeping ROM contents.
     */
    public void reset(int resetVector) {
        ram.zero();
        tracer.clear();
        // Re-initialize CPU (new instance is cleanest)
    }

    // -------------------------------------------------------------------------
    // Diagnostics
    // -------------------------------------------------------------------------

    public void printStats(PrintStream out) {
        long elapsedNs = System.nanoTime() - runStartTime;
        double elapsedMs = elapsedNs / 1_000_000.0;
        long cycles = cpu.getCycle();
        double mips = cycles / (elapsedNs / 1000.0);

        out.println();
        out.println("╔══════════════════════════════════════════╗");
        out.println("║          Emulator Statistics              ║");
        out.println("╠══════════════════════════════════════════╣");
        out.printf( "║  Cycles executed : %,20d  ║%n", cycles);
        out.printf( "║  Wall-clock time : %18.2f ms  ║%n", elapsedMs);
        out.printf( "║  Throughput      : %17.2f MIPS  ║%n", mips);
        out.printf( "║  ROM used        : %,20d  ║%n", rom.getLoadedBytes());
        out.printf( "║  Stack depth     : %,20d  ║%n", ram.getStackUsage());
        out.printf( "║  Trace records   : %,20d  ║%n", tracer.getCount());
        out.println("╚══════════════════════════════════════════╝");

        if (ram.isStackOverflowDetected()) {
            out.println("⚠️  WARNING: Stack overflow may have occurred!");
        }
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public RV32ICore          getCPU()    { return cpu; }
    public SystemBus          getBus()    { return bus; }
    public ROM                getROM()    { return rom; }
    public RAM                getRAM()    { return ram; }
    public UartPeripheral     getUART()   { return uart; }
    public GpioPeripheral     getGPIO()   { return gpio; }
    public TimerPeripheral    getTimer()  { return timer; }
    public CSRFile            getCSR()    { return csr; }
    public ExecutionTracer    getTracer() { return tracer; }
}
