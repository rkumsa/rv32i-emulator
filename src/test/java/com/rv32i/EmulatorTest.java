package com.rv32i;

import com.rv32i.cpu.CSRFile;
import com.rv32i.loader.Assembler;
import com.rv32i.loader.Assembler.Reg;
import com.rv32i.memory.SystemBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

class EmulatorTest {

    private static final long MAX_CYCLES = 100_000;

    private ByteArrayOutputStream uartBuf;
    private PrintStream uartOut;

    @BeforeEach
    void setUp() {
        uartBuf = new ByteArrayOutputStream();
        uartOut = new PrintStream(uartBuf);
    }

    private Emulator makeEmulator(Assembler asm) {
        Emulator emu = new Emulator(uartOut);
        emu.getROM().loadBinary(asm.assemble(SystemBus.ROM_BASE));
        return emu;
    }

    @Test
    @DisplayName("ADDI: a0 = 0 + 42")
    void testAddi() {
        Assembler asm = new Assembler();
        asm.addi(Reg.A0, Reg.ZERO, 42).li(Reg.A7, 93).ecall();
        Emulator emu = makeEmulator(asm);
        emu.run(MAX_CYCLES);
        assertEquals(42, emu.getCPU().getReg(Reg.A0));
    }

    @Test
    @DisplayName("ADD: 10 + 32 = 42")
    void testAdd() {
        Assembler asm = new Assembler();
        asm.li(Reg.T0, 10).li(Reg.T1, 32).add(Reg.A0, Reg.T0, Reg.T1).li(Reg.A7, 93).ecall();
        Emulator emu = makeEmulator(asm);
        emu.run(MAX_CYCLES);
        assertEquals(42, emu.getCPU().getReg(Reg.A0));
    }

    @Test
    @DisplayName("SUB: 100 - 58 = 42")
    void testSub() {
        Assembler asm = new Assembler();
        asm.li(Reg.T0, 100).li(Reg.T1, 58).sub(Reg.A0, Reg.T0, Reg.T1).li(Reg.A7, 93).ecall();
        Emulator emu = makeEmulator(asm);
        emu.run(MAX_CYCLES);
        assertEquals(42, emu.getCPU().getReg(Reg.A0));
    }

    @Test
    @DisplayName("AND / OR / XOR")
    void testLogical() {
        Assembler asm = new Assembler();
        asm.li(Reg.T0, 0xFF0).li(Reg.T1, 0x0FF)
           .and(Reg.S0, Reg.T0, Reg.T1)
           .or(Reg.S1, Reg.T0, Reg.T1)
           .xor(Reg.S2, Reg.T0, Reg.T1)
           .li(Reg.A7, 93).ecall();
        Emulator emu = makeEmulator(asm);
        emu.run(MAX_CYCLES);
        assertEquals(0x0F0, emu.getCPU().getReg(Reg.S0));
        assertEquals(0xFFF, emu.getCPU().getReg(Reg.S1));
        assertEquals(0xF0F, emu.getCPU().getReg(Reg.S2));
    }

    @Test
    @DisplayName("SLLI / SRLI / SRAI")
    void testShifts() {
        Assembler asm = new Assembler();
        asm.li(Reg.T0, 1).slli(Reg.S0, Reg.T0, 4)
           .li(Reg.T1, 0x80000010)
           .srli(Reg.S1, Reg.T1, 4)
           .srai(Reg.S2, Reg.T1, 4)
           .li(Reg.A7, 93).ecall();
        Emulator emu = makeEmulator(asm);
        emu.run(MAX_CYCLES);
        assertEquals(16,         emu.getCPU().getReg(Reg.S0));
        assertEquals(0x08000001, emu.getCPU().getReg(Reg.S1));
        assertEquals(0xF8000001, emu.getCPU().getReg(Reg.S2));
    }

    @Test
    @DisplayName("SW + LW roundtrip in RAM")
    void testLoadStore() {
        int value = 0xCAFEBABE;
        int hi = (value + 0x800) >>> 12;
        int lo = value - (hi << 12);
        Assembler asm = new Assembler();
        asm.lui(Reg.T0, hi).addi(Reg.T0, Reg.T0, lo)
           .lui(Reg.T1, SystemBus.RAM_BASE >>> 12)
           .sw(Reg.T1, Reg.T0, 0)
           .lw(Reg.A0, Reg.T1, 0)
           .li(Reg.A7, 93).ecall();
        Emulator emu = makeEmulator(asm);
        emu.run(MAX_CYCLES);
        assertEquals(value, emu.getCPU().getReg(Reg.A0));
    }

    @Test
    @DisplayName("SB + LBU + LB sign extension")
    void testByteLoadStore() {
        Assembler asm = new Assembler();
        asm.lui(Reg.T0, SystemBus.RAM_BASE >>> 12)
           .li(Reg.T1, 0xFF)
           .sb(Reg.T0, Reg.T1, 0)
           .lbu(Reg.S0, Reg.T0, 0)
           .lb(Reg.S1, Reg.T0, 0)
           .li(Reg.A7, 93).ecall();
        Emulator emu = makeEmulator(asm);
        emu.run(MAX_CYCLES);
        assertEquals(255, emu.getCPU().getReg(Reg.S0));
        assertEquals(-1,  emu.getCPU().getReg(Reg.S1));
    }

    @Test
    @DisplayName("BEQ taken: s0 should be 1")
    void testBranchBeq() {
        Assembler asm = new Assembler();
        asm.li(Reg.T0, 5).li(Reg.T1, 5)
           .beq(Reg.T0, Reg.T1, "taken")
           .li(Reg.S0, 2).j("end")
           .label("taken").li(Reg.S0, 1)
           .label("end").li(Reg.A7, 93).ecall();
        Emulator emu = makeEmulator(asm);
        emu.run(MAX_CYCLES);
        assertEquals(1, emu.getCPU().getReg(Reg.S0));
    }

    @Test
    @DisplayName("BLT signed: -1 < 0 should branch")
    void testBranchBlt() {
        Assembler asm = new Assembler();
        asm.li(Reg.T0, -1).li(Reg.T1, 0)
           .blt(Reg.T0, Reg.T1, "yes")
           .li(Reg.S0, 0).j("done")
           .label("yes").li(Reg.S0, 1)
           .label("done").li(Reg.A7, 93).ecall();
        Emulator emu = makeEmulator(asm);
        emu.run(MAX_CYCLES);
        assertEquals(1, emu.getCPU().getReg(Reg.S0));
    }

    @Test
    @DisplayName("JAL + JALR: square(5) = 25")
    void testCall() {
        Assembler asm = new Assembler();
        asm.li(Reg.A0, 5)
           .jal(Reg.RA, "square")
           .li(Reg.A7, 93).ecall()
           .label("square")
           .mv(Reg.T0, Reg.A0).li(Reg.T1, 0)
           .label("sqloop")
           .beq(Reg.T0, Reg.ZERO, "sqdone")
           .add(Reg.T1, Reg.T1, Reg.A0)
           .addi(Reg.T0, Reg.T0, -1)
           .j("sqloop")
           .label("sqdone")
           .mv(Reg.A0, Reg.T1).ret();
        Emulator emu = makeEmulator(asm);
        emu.run(MAX_CYCLES);
        assertEquals(25, emu.getCPU().getReg(Reg.A0));
    }

    @Test
    @DisplayName("LUI loads upper immediate")
    void testLui() {
        Assembler asm = new Assembler();
        asm.lui(Reg.A0, 0xABCDE).li(Reg.A7, 93).ecall();
        Emulator emu = makeEmulator(asm);
        emu.run(MAX_CYCLES);
        assertEquals(0xABCDE000, emu.getCPU().getReg(Reg.A0));
    }

    @Test
    @DisplayName("AUIPC equals ROM_BASE at first instruction")
    void testAuipc() {
        Assembler asm = new Assembler();
        asm.auipc(Reg.A0, 0).li(Reg.A7, 93).ecall();
        Emulator emu = makeEmulator(asm);
        emu.run(MAX_CYCLES);
        assertEquals(SystemBus.ROM_BASE, emu.getCPU().getReg(Reg.A0));
    }

    @Test
    @DisplayName("x0 is always zero")
    void testX0IsZero() {
        Assembler asm = new Assembler();
        asm.addi(Reg.ZERO, Reg.ZERO, 99)
           .add(Reg.A0, Reg.ZERO, Reg.ZERO)
           .li(Reg.A7, 93).ecall();
        Emulator emu = makeEmulator(asm);
        emu.run(MAX_CYCLES);
        assertEquals(0, emu.getCPU().getReg(0));
        assertEquals(0, emu.getCPU().getReg(Reg.A0));
    }

    @Test
    @DisplayName("Demo: Hello World halts cleanly")
    void testDemoHello() {
        Emulator emu = DemoPrograms.helloWorld();
        emu.run(MAX_CYCLES);
        assertTrue(emu.getCPU().isHalted());
    }

    @Test
    @DisplayName("Demo: Fibonacci contains correct values")
    void testDemoFib() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        Emulator emu = new Emulator(new PrintStream(buf));
        emu.getROM().loadBinary(
            new Assembler()
                .li(Reg.A0, 0).li(Reg.A1, 1).li(Reg.T0, 8)
                .lui(Reg.T1, com.rv32i.peripheral.UartPeripheral.BASE >>> 12)
                .label("fib_loop")
                .beq(Reg.T0, Reg.ZERO, "done")
                .li(Reg.A7, 1).ecall()
                .li(Reg.T2, ' ').sw(Reg.T1, Reg.T2, 0)
                .add(Reg.T2, Reg.A0, Reg.A1)
                .mv(Reg.A0, Reg.A1).mv(Reg.A1, Reg.T2)
                .addi(Reg.T0, Reg.T0, -1)
                .j("fib_loop")
                .label("done")
                .li(Reg.A0, 0).li(Reg.A7, 93).ecall()
                .assemble(SystemBus.ROM_BASE)
        );
        emu.run(MAX_CYCLES);
        assertTrue(emu.getCPU().isHalted());
        String out = buf.toString().trim();
        assertTrue(out.contains("13"), "missing 13, got: " + out);
        assertFalse(out.contains("21"), "printed too many, got: " + out);
    }

    @Test
    @DisplayName("Factorial(5) = 120")
    void testDemoFactorial() {
        Assembler asm = new Assembler();
        asm.li(Reg.S0, 5).li(Reg.S1, 1)
           .label("fact_iter")
           .beq(Reg.S0, Reg.ZERO, "fact_done")
           .li(Reg.T0, 0).mv(Reg.T1, Reg.S0)
           .label("mul_loop")
           .beq(Reg.T1, Reg.ZERO, "mul_done")
           .add(Reg.T0, Reg.T0, Reg.S1).addi(Reg.T1, Reg.T1, -1).j("mul_loop")
           .label("mul_done")
           .mv(Reg.S1, Reg.T0).addi(Reg.S0, Reg.S0, -1).j("fact_iter")
           .label("fact_done")
           .mv(Reg.A0, Reg.S1).li(Reg.A7, 93).ecall();
        Emulator emu = makeEmulator(asm);
        emu.run(MAX_CYCLES);
        assertTrue(emu.getCPU().isHalted());
        assertEquals(120, emu.getCPU().getReg(Reg.A0));
    }

    @Test
    @DisplayName("CSRRW: write and read back mtvec")
    void testCsrRw() {
        Assembler asm = new Assembler();
        asm.li(Reg.T0, 0x1000)
           .csrrw(Reg.ZERO, CSRFile.MTVEC, Reg.T0)
           .csrrw(Reg.A0, CSRFile.MTVEC, Reg.ZERO)
           .li(Reg.A7, 93).ecall();
        Emulator emu = makeEmulator(asm);
        emu.run(MAX_CYCLES);
        assertEquals(0x1000, emu.getCPU().getReg(Reg.A0));
    }
}
