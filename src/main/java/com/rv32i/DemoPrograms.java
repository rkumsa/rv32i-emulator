package com.rv32i;

import com.rv32i.loader.Assembler;
import com.rv32i.loader.Assembler.Reg;
import com.rv32i.memory.SystemBus;
import com.rv32i.peripheral.GpioPeripheral;
import com.rv32i.peripheral.TimerPeripheral;
import com.rv32i.peripheral.UartPeripheral;

/**
 * Built-in demo programs assembled at runtime with the Java assembler.
 * These programs run without any external toolchain.
 */
public class DemoPrograms {

    // Demo 1: Hello World via UART MMIO
    public static Emulator helloWorld() {
        Assembler asm = new Assembler();
        asm.lui(Reg.T0, UartPeripheral.BASE >>> 12);
        for (int ch : "Hello, World!\n".chars().toArray()) {
            asm.li(Reg.T1, ch);
            asm.sw(Reg.T0, Reg.T1, 0);
        }
        asm.li(Reg.A0, 0).li(Reg.A7, 93).ecall();
        Emulator emu = new Emulator();
        emu.getROM().loadBinary(asm.assemble(SystemBus.ROM_BASE));
        return emu;
    }

    // Demo 2: Fibonacci
    public static Emulator fibonacci(int count) {
        Assembler asm = new Assembler();
        asm.li(Reg.A0, 0).li(Reg.A1, 1).li(Reg.T0, count)
           .lui(Reg.T1, UartPeripheral.BASE >>> 12)
           .label("fib_loop")
           .beq(Reg.T0, Reg.ZERO, "done")
           .li(Reg.A7, 1).ecall()
           .li(Reg.T2, ' ').sw(Reg.T1, Reg.T2, 0)
           .add(Reg.T2, Reg.A0, Reg.A1)
           .mv(Reg.A0, Reg.A1).mv(Reg.A1, Reg.T2)
           .addi(Reg.T0, Reg.T0, -1)
           .j("fib_loop")
           .label("done")
           .li(Reg.T2, '\n').sw(Reg.T1, Reg.T2, 0)
           .li(Reg.A0, 0).li(Reg.A7, 93).ecall();
        Emulator emu = new Emulator();
        emu.getROM().loadBinary(asm.assemble(SystemBus.ROM_BASE));
        return emu;
    }

    // Demo 3: Blinky — GPIO LED toggle
    public static Emulator blinky(int blinks) {
        Assembler asm = new Assembler();
        asm.lui(Reg.T0, GpioPeripheral.BASE >>> 12)
           .li(Reg.T1, 1).sw(Reg.T0, Reg.T1, 0x04)   // OUTPUT_EN
           .li(Reg.S0, blinks * 2)
           .label("blink_loop")
           .beq(Reg.S0, Reg.ZERO, "blink_done")
           .lw(Reg.T1, Reg.T0, 0x00)
           .li(Reg.T2, 1).xor(Reg.T1, Reg.T1, Reg.T2)
           .sw(Reg.T0, Reg.T1, 0x00)
           .li(Reg.T3, 500)
           .label("delay").addi(Reg.T3, Reg.T3, -1).bne(Reg.T3, Reg.ZERO, "delay")
           .addi(Reg.S0, Reg.S0, -1).j("blink_loop")
           .label("blink_done")
           .li(Reg.A0, 0).li(Reg.A7, 93).ecall();
        Emulator emu = new Emulator();
        emu.getROM().loadBinary(asm.assemble(SystemBus.ROM_BASE));
        return emu;
    }

    // Demo 4: Timer interrupt demo
    public static Emulator timerInterruptDemo(int targetInterrupts) {
        int timerBase = TimerPeripheral.BASE;
        int uartBase  = UartPeripheral.BASE;
        int interval  = 100;

        Assembler asm = new Assembler();
        asm.j("main_entry")

           // ISR at offset +4
           .label("isr_handler")
           .addi(Reg.SP, Reg.SP, -8)
           .sw(Reg.SP, Reg.RA, 4).sw(Reg.SP, Reg.T0, 0)
           .addi(Reg.S0, Reg.S0, 1)
           .lui(Reg.T0, uartBase >>> 12).li(Reg.RA, '!').sw(Reg.T0, Reg.RA, 0)
           .lui(Reg.T0, timerBase >>> 12)
           .lw(Reg.RA, Reg.T0, 0x00).addi(Reg.RA, Reg.RA, interval)
           .sw(Reg.T0, Reg.RA, 0x08).li(Reg.RA, 0).sw(Reg.T0, Reg.RA, 0x0C)
           .lw(Reg.T0, Reg.SP, 0).lw(Reg.RA, Reg.SP, 4)
           .addi(Reg.SP, Reg.SP, 8).mret()

           // Main
           .label("main_entry")
           .li(Reg.S0, 0)
           .lui(Reg.T0, SystemBus.ROM_BASE >>> 12).addi(Reg.T0, Reg.T0, 4)
           .csrrw(Reg.ZERO, 0x305, Reg.T0)
           .li(Reg.T0, 1 << 3).csrrs(Reg.ZERO, 0x300, Reg.T0)
           .li(Reg.T0, 1 << 7).csrrs(Reg.ZERO, 0x304, Reg.T0)
           .lui(Reg.T0, timerBase >>> 12)
           .lw(Reg.T1, Reg.T0, 0x00).addi(Reg.T1, Reg.T1, interval)
           .sw(Reg.T0, Reg.T1, 0x08).li(Reg.T1, 0).sw(Reg.T0, Reg.T1, 0x0C)
           .li(Reg.S1, targetInterrupts)
           .label("wait_loop").blt(Reg.S0, Reg.S1, "wait_loop")
           .lui(Reg.T0, uartBase >>> 12).li(Reg.T1, '\n').sw(Reg.T0, Reg.T1, 0)
           .li(Reg.A0, 0).li(Reg.A7, 93).ecall();

        Emulator emu = new Emulator();
        emu.getROM().loadBinary(asm.assemble(SystemBus.ROM_BASE));
        return emu;
    }

    // Demo 5: Factorial (iterative, multiply via repeated addition — no MUL in RV32I base)
    public static Emulator factorial(int n) {
        Assembler asm = new Assembler();
        asm.li(Reg.S0, n).li(Reg.S1, 1)
           .label("fact_iter")
           .beq(Reg.S0, Reg.ZERO, "fact_done")
           .li(Reg.T0, 0).mv(Reg.T1, Reg.S0)
           .label("mul_loop")
           .beq(Reg.T1, Reg.ZERO, "mul_done")
           .add(Reg.T0, Reg.T0, Reg.S1).addi(Reg.T1, Reg.T1, -1).j("mul_loop")
           .label("mul_done")
           .mv(Reg.S1, Reg.T0).addi(Reg.S0, Reg.S0, -1).j("fact_iter")
           .label("fact_done")
           .mv(Reg.A0, Reg.S1).li(Reg.A7, 1).ecall()
           .lui(Reg.T0, UartPeripheral.BASE >>> 12).li(Reg.T1, '\n').sw(Reg.T0, Reg.T1, 0)
           .li(Reg.A0, 0).li(Reg.A7, 93).ecall();
        Emulator emu = new Emulator();
        emu.getROM().loadBinary(asm.assemble(SystemBus.ROM_BASE));
        return emu;
    }
}
