package com.rv32i;

import com.rv32i.loader.ProgramLoader;
import com.rv32i.memory.SystemBus;

import java.nio.file.Path;

/**
 * Main — command-line entry point.
 *
 * Usage:
 *   java -jar rv32i-emulator.jar [options] <program>
 *   java -jar rv32i-emulator.jar --demo hello
 *   java -jar rv32i-emulator.jar --demo fib 10
 *   java -jar rv32i-emulator.jar --demo blinky 3
 *   java -jar rv32i-emulator.jar --demo factorial 7
 *   java -jar rv32i-emulator.jar --demo timer 5
 *   java -jar rv32i-emulator.jar path/to/program.bin
 *   java -jar rv32i-emulator.jar path/to/program.elf
 *
 * Flags:
 *   --trace          Enable instruction-level tracing (dump on exit)
 *   --max-cycles N   Halt after N cycles (default 10,000,000)
 *   --stats          Print performance stats after execution
 */
public class Main {

    private static final long DEFAULT_MAX_CYCLES = 10_000_000L;

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printHelp();
            return;
        }

        boolean trace     = false;
        boolean stats     = false;
        long    maxCycles = DEFAULT_MAX_CYCLES;
        String  demoName  = null;
        int     demoArg   = 0;
        String  filePath  = null;

        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--trace"      -> trace = true;
                case "--stats"      -> stats = true;
                case "--max-cycles" -> maxCycles = Long.parseLong(args[++i]);
                case "--demo"       -> {
                    demoName = args[++i];
                    if (i + 1 < args.length && args[i + 1].matches("-?\\d+")) {
                        demoArg = Integer.parseInt(args[++i]);
                    }
                }
                default -> filePath = args[i];
            }
        }

        Emulator emu;

        if (demoName != null) {
            emu = switch (demoName.toLowerCase()) {
                case "hello"    -> DemoPrograms.helloWorld();
                case "fib"      -> DemoPrograms.fibonacci(demoArg > 0 ? demoArg : 10);
                case "blinky"   -> DemoPrograms.blinky(demoArg > 0 ? demoArg : 3);
                case "factorial" -> DemoPrograms.factorial(demoArg > 0 ? demoArg : 7);
                case "timer"    -> DemoPrograms.timerInterruptDemo(demoArg > 0 ? demoArg : 5);
                default -> {
                    System.err.println("Unknown demo: " + demoName);
                    System.err.println("Available: hello, fib, blinky, factorial, timer");
                    System.exit(1);
                    yield null;
                }
            };
        } else if (filePath != null) {
            emu = new Emulator();
            ProgramLoader loader = new ProgramLoader(emu.getBus());
            int entry = loader.load(Path.of(filePath));
            emu.getCPU().setPC(entry);
        } else {
            printHelp();
            return;
        }

        if (trace) {
            emu.getTracer().setEnabled(true);
        }

        System.out.printf("[EMU] Starting — max cycles: %,d%n", maxCycles);
        long cycles = emu.run(maxCycles);
        System.out.printf("[EMU] Finished after %,d cycles.%n", cycles);

        if (!emu.getCPU().isHalted()) {
            System.err.println("[EMU] WARNING: Hit cycle limit without halting.");
        }

        if (trace) {
            emu.getTracer().dump(System.out);
        }

        if (stats) {
            emu.printStats(System.out);
        }
    }

    private static void printHelp() {
        System.out.println("RV32I Emulator");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -jar rv32i-emulator.jar --demo <name> [arg]");
        System.out.println("  java -jar rv32i-emulator.jar [--trace] [--stats] <file.bin|file.elf>");
        System.out.println();
        System.out.println("Built-in demos:");
        System.out.println("  hello               Hello World via UART");
        System.out.println("  fib    <count>       First N Fibonacci numbers");
        System.out.println("  blinky <blinks>      GPIO LED toggle");
        System.out.println("  factorial <n>        n! via repeated addition");
        System.out.println("  timer  <interrupts>  Timer interrupt demo");
    }
}
