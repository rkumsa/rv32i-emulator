package com.rv32i.cpu;

import com.rv32i.exception.BusFaultException;
import com.rv32i.exception.CpuException;
import com.rv32i.memory.SystemBus;
import com.rv32i.trace.ExecutionTracer;
import com.rv32i.trace.TraceRecord;

/**
 * RV32I CPU Core — implements the full RISC-V 32-bit base integer ISA.
 *
 * All 47 instructions are implemented. Instruction decode follows the
 * RISC-V ISA specification (Volume I, Unprivileged ISA).
 *
 * Key design decisions matching real firmware engineering:
 *  - x0 is hardwired to zero and enforced after every instruction
 *  - All immediates are sign-extended as per spec
 *  - Misaligned access detection (configurable: trap or emulate)
 *  - Full CSR support (mstatus, mtvec, mepc, mcause, mip, mie, mcycle)
 *  - Interrupt handling via MRET/ECALL/EBREAK
 *
 * Instruction format reference:
 *   31      25 24   20 19  15 14  12 11    7 6      0
 *   [ funct7 ][ rs2  ][ rs1 ][funct3][  rd  ][opcode]  R-type
 *   [    imm[11:0]   ][ rs1 ][funct3][  rd  ][opcode]  I-type
 *   [imm[11:5]][ rs2 ][ rs1 ][funct3][imm4:0][opcode]  S-type
 *   [imm[12|10:5]][rs2][rs1][f3][imm[4:1|11]][opcode]  B-type
 *   [         imm[31:12]         ][  rd  ][opcode]      U-type
 *   [   imm[20|10:1|11|19:12]   ][  rd  ][opcode]      J-type
 */
public class RV32ICore {

    // Register file: x0–x31
    // ABI names: zero, ra, sp, gp, tp, t0-t2, s0/fp, s1, a0-a7, s2-s11, t3-t6
    private final int[] x = new int[32];
    private int pc;

    private final SystemBus        bus;
    private final CSRFile          csr;
    private final InterruptController irqCtrl;
    private final ExecutionTracer  tracer;

    private boolean halted = false;
    private long    cycle  = 0;

    // ECALL handler — programs use this to request emulator services (print, exit, etc.)
    private EcallHandler ecallHandler;

    public interface EcallHandler {
        /** Return true to halt the CPU, false to continue. */
        boolean handle(int a0, int a1, int a2, int a7, RV32ICore cpu);
    }

    // -------------------------------------------------------------------------
    // Opcodes (bits [6:0])
    // -------------------------------------------------------------------------
    private static final int OP_LOAD    = 0x03;
    private static final int OP_MISC_MEM= 0x0F; // FENCE
    private static final int OP_OP_IMM  = 0x13; // ADDI, SLTI, etc.
    private static final int OP_AUIPC   = 0x17;
    private static final int OP_STORE   = 0x23;
    private static final int OP_OP      = 0x33; // ADD, SUB, SLL, etc.
    private static final int OP_LUI     = 0x37;
    private static final int OP_BRANCH  = 0x63;
    private static final int OP_JALR    = 0x67;
    private static final int OP_JAL     = 0x6F;
    private static final int OP_SYSTEM  = 0x73; // ECALL, EBREAK, CSR*

    public RV32ICore(SystemBus bus, CSRFile csr, InterruptController irqCtrl,
                     ExecutionTracer tracer, int resetVector) {
        this.bus     = bus;
        this.csr     = csr;
        this.irqCtrl = irqCtrl;
        this.tracer  = tracer;
        this.pc      = resetVector;

        // Initialize stack pointer to top of RAM (like a reset vector)
        x[2] = SystemBus.RAM_BASE + SystemBus.RAM_SIZE;
    }

    public void setEcallHandler(EcallHandler h) { this.ecallHandler = h; }

    // -------------------------------------------------------------------------
    // Main step — fetch → decode → execute → writeback
    // -------------------------------------------------------------------------

    public void step() {
        if (halted) return;

        // --- Tick peripherals ---
        bus.getPeripherals().forEach(p -> p.tick(cycle));
        csr.incrementCycle();

        // --- Check for pending interrupts ---
        if (csr.isMachineInterruptEnabled()) {
            int irq = irqCtrl.poll();
            if (irq >= 0) {
                handleInterrupt(irq);
                cycle++;
                return;
            }
        }

        // --- Fetch ---
        if ((pc & 3) != 0) {
            trap(CpuException.Cause.INSTRUCTION_ADDRESS_MISALIGNED.code, pc, pc);
            return;
        }

        int instr;
        try {
            instr = bus.readWord(pc);
        } catch (BusFaultException e) {
            trap(CpuException.Cause.INSTRUCTION_ACCESS_FAULT.code, pc, e.getAddress());
            return;
        }

        int opcode = instr & 0x7F;
        int nextPc = pc + 4;

        // Snapshot registers for trace (before writeback)
        int rd  = (instr >> 7)  & 0x1F;
        int rs1 = (instr >> 15) & 0x1F;
        int rs2 = (instr >> 20) & 0x1F;

        String mnemonic = "?";

        try {
            switch (opcode) {

                // ---- R-type: ADD SUB SLL SLT SLTU XOR SRL SRA OR AND ----
                case OP_OP -> {
                    int funct3 = (instr >> 12) & 0x7;
                    int funct7 = (instr >> 25) & 0x7F;
                    int a = x[rs1], b = x[rs2];
                    int result;
                    mnemonic = switch (funct3) {
                        case 0x0 -> funct7 == 0x20 ? "SUB" : "ADD";
                        case 0x1 -> "SLL";
                        case 0x2 -> "SLT";
                        case 0x3 -> "SLTU";
                        case 0x4 -> "XOR";
                        case 0x5 -> funct7 == 0x20 ? "SRA" : "SRL";
                        case 0x6 -> "OR";
                        case 0x7 -> "AND";
                        default  -> "?OP?";
                    };
                    result = switch (funct3) {
                        case 0x0 -> (funct7 == 0x20) ? (a - b) : (a + b);
                        case 0x1 -> a << (b & 0x1F);
                        case 0x2 -> (a < b) ? 1 : 0;                        // signed
                        case 0x3 -> (Integer.compareUnsigned(a, b) < 0) ? 1 : 0; // unsigned
                        case 0x4 -> a ^ b;
                        case 0x5 -> (funct7 == 0x20) ? (a >> (b & 0x1F))   // SRA (arithmetic)
                                                      : (a >>> (b & 0x1F)); // SRL (logical)
                        case 0x6 -> a | b;
                        case 0x7 -> a & b;
                        default  -> throw new CpuException(CpuException.Cause.ILLEGAL_INSTRUCTION, pc,
                                "Unknown funct3 in OP: " + funct3);
                    };
                    writeRd(rd, result);
                }

                // ---- I-type ALU: ADDI SLTI SLTIU XORI ORI ANDI SLLI SRLI SRAI ----
                case OP_OP_IMM -> {
                    int funct3 = (instr >> 12) & 0x7;
                    int imm    = instr >> 20;            // sign-extended
                    int shamt  = (instr >> 20) & 0x1F;  // shift amount
                    int funct7 = (instr >> 25) & 0x7F;
                    int a = x[rs1];
                    int result;
                    mnemonic = switch (funct3) {
                        case 0x0 -> "ADDI";
                        case 0x1 -> "SLLI";
                        case 0x2 -> "SLTI";
                        case 0x3 -> "SLTIU";
                        case 0x4 -> "XORI";
                        case 0x5 -> funct7 == 0x20 ? "SRAI" : "SRLI";
                        case 0x6 -> "ORI";
                        case 0x7 -> "ANDI";
                        default  -> "?IMM?";
                    };
                    result = switch (funct3) {
                        case 0x0 -> a + imm;
                        case 0x1 -> a << shamt;
                        case 0x2 -> (a < imm) ? 1 : 0;
                        case 0x3 -> (Integer.compareUnsigned(a, imm) < 0) ? 1 : 0;
                        case 0x4 -> a ^ imm;
                        case 0x5 -> (funct7 == 0x20) ? (a >> shamt) : (a >>> shamt);
                        case 0x6 -> a | imm;
                        case 0x7 -> a & imm;
                        default  -> throw new CpuException(CpuException.Cause.ILLEGAL_INSTRUCTION, pc,
                                "Bad funct3 in OP_IMM: " + funct3);
                    };
                    writeRd(rd, result);
                }

                // ---- Load: LB LH LW LBU LHU ----
                case OP_LOAD -> {
                    int funct3 = (instr >> 12) & 0x7;
                    int imm    = instr >> 20; // sign-extended offset
                    int addr   = x[rs1] + imm;
                    int result;
                    mnemonic = switch (funct3) {
                        case 0x0 -> "LB"; case 0x1 -> "LH"; case 0x2 -> "LW";
                        case 0x4 -> "LBU"; case 0x5 -> "LHU"; default -> "?LD?";
                    };
                    result = switch (funct3) {
                        case 0x0 -> bus.readByteSigned(addr);
                        case 0x1 -> bus.readHalfSigned(addr);
                        case 0x2 -> bus.readWord(addr);
                        case 0x4 -> bus.readByte(addr);
                        case 0x5 -> bus.readHalf(addr);
                        default  -> throw new CpuException(CpuException.Cause.ILLEGAL_INSTRUCTION, pc,
                                "Bad funct3 in LOAD: " + funct3);
                    };
                    writeRd(rd, result);
                }

                // ---- Store: SB SH SW ----
                case OP_STORE -> {
                    int funct3 = (instr >> 12) & 0x7;
                    // S-type immediate: imm[11:5] = instr[31:25], imm[4:0] = instr[11:7], sign-extended
                    int imm    = (instr >> 20 & ~0x1F) | ((instr >> 7) & 0x1F);
                    imm        = (imm << 20) >> 20; // sign-extend from bit 11
                    int addr   = x[rs1] + imm;
                    int val    = x[rs2];
                    mnemonic = switch (funct3) {
                        case 0x0 -> "SB"; case 0x1 -> "SH"; case 0x2 -> "SW"; default -> "?ST?";
                    };
                    switch (funct3) {
                        case 0x0 -> bus.writeByte(addr, val);
                        case 0x1 -> bus.writeHalf(addr, val);
                        case 0x2 -> bus.writeWord(addr, val);
                        default  -> throw new CpuException(CpuException.Cause.ILLEGAL_INSTRUCTION, pc,
                                "Bad funct3 in STORE: " + funct3);
                    }
                    rd = 0; // stores don't write a destination
                }

                // ---- Branch: BEQ BNE BLT BGE BLTU BGEU ----
                case OP_BRANCH -> {
                    int funct3 = (instr >> 12) & 0x7;
                    // B-type immediate decode (the trickiest one in RV32I)
                    int imm = ((instr >> 31) & 1) << 12
                            | ((instr >>  7) & 1) << 11
                            | ((instr >> 25) & 0x3F) << 5
                            | ((instr >>  8) & 0xF)  << 1;
                    imm = (imm << 19) >> 19; // sign-extend from bit 12
                    int a = x[rs1], b = x[rs2];
                    boolean taken;
                    mnemonic = switch (funct3) {
                        case 0x0 -> "BEQ";  case 0x1 -> "BNE";
                        case 0x4 -> "BLT";  case 0x5 -> "BGE";
                        case 0x6 -> "BLTU"; case 0x7 -> "BGEU";
                        default  -> "?BR?";
                    };
                    taken = switch (funct3) {
                        case 0x0 -> a == b;
                        case 0x1 -> a != b;
                        case 0x4 -> a < b;
                        case 0x5 -> a >= b;
                        case 0x6 -> Integer.compareUnsigned(a, b) < 0;
                        case 0x7 -> Integer.compareUnsigned(a, b) >= 0;
                        default  -> throw new CpuException(CpuException.Cause.ILLEGAL_INSTRUCTION, pc,
                                "Bad funct3 in BRANCH: " + funct3);
                    };
                    if (taken) {
                        nextPc = pc + imm;
                        if ((nextPc & 3) != 0)
                            throw new CpuException(CpuException.Cause.INSTRUCTION_ADDRESS_MISALIGNED,
                                    pc, nextPc, "Branch target misaligned");
                    }
                    rd = 0;
                }

                // ---- JAL: Jump and Link ----
                case OP_JAL -> {
                    // J-type immediate decode
                    int imm = ((instr >> 31) & 1)    << 20
                            | ((instr >> 12) & 0xFF)  << 12
                            | ((instr >> 20) & 1)     << 11
                            | ((instr >> 21) & 0x3FF) << 1;
                    imm = (imm << 11) >> 11; // sign-extend from bit 20
                    mnemonic = "JAL";
                    writeRd(rd, nextPc);     // rd = PC+4 (return address)
                    nextPc = pc + imm;
                    if ((nextPc & 3) != 0)
                        throw new CpuException(CpuException.Cause.INSTRUCTION_ADDRESS_MISALIGNED,
                                pc, nextPc, "JAL target misaligned");
                }

                // ---- JALR: Jump and Link Register ----
                case OP_JALR -> {
                    int imm = instr >> 20; // sign-extended
                    mnemonic = "JALR";
                    int target = (x[rs1] + imm) & ~1; // clear bit 0 per spec
                    writeRd(rd, nextPc);
                    nextPc = target;
                }

                // ---- LUI: Load Upper Immediate ----
                case OP_LUI -> {
                    int imm = instr & 0xFFFFF000;
                    mnemonic = "LUI";
                    writeRd(rd, imm);
                }

                // ---- AUIPC: Add Upper Immediate to PC ----
                case OP_AUIPC -> {
                    int imm = instr & 0xFFFFF000;
                    mnemonic = "AUIPC";
                    writeRd(rd, pc + imm);
                }

                // ---- FENCE: no-op in emulation ----
                case OP_MISC_MEM -> {
                    mnemonic = "FENCE";
                    // Memory ordering hint — no-op in a single-core emulator
                }

                // ---- SYSTEM: ECALL, EBREAK, CSR* ----
                case OP_SYSTEM -> {
                    int funct3 = (instr >> 12) & 0x7;
                    int funct12 = (instr >> 20) & 0xFFF;
                    int csrAddr = funct12;

                    if (funct3 == 0) {
                        // Non-CSR system instructions
                        switch (funct12) {
                            case 0x000 -> { // ECALL
                                mnemonic = "ECALL";
                                if (ecallHandler != null) {
                                    boolean halt = ecallHandler.handle(x[10], x[11], x[12], x[17], this);
                                    if (halt) { halted = true; return; }
                                } else {
                                    trap(CpuException.Cause.ECALL.code, pc, 0);
                                    return;
                                }
                            }
                            case 0x001 -> { // EBREAK
                                mnemonic = "EBREAK";
                                System.err.println("[EBREAK] PC=0x" + Integer.toHexString(pc));
                                if (tracer.isEnabled()) tracer.dump(System.err);
                                halted = true;
                                return;
                            }
                            case 0x302 -> { // MRET — return from machine-mode trap
                                mnemonic = "MRET";
                                nextPc = csr.exitTrap();
                            }
                            default -> throw new CpuException(CpuException.Cause.ILLEGAL_INSTRUCTION,
                                    pc, "Unknown SYSTEM funct12: 0x" + Integer.toHexString(funct12));
                        }
                        rd = 0;
                    } else {
                        // CSR instructions: CSRRW, CSRRS, CSRRC, CSRRWI, CSRRSI, CSRRCI
                        int csrVal = csr.read(csrAddr);
                        int uimm   = rs1; // for immediate variants, rs1 field is zero-extended uimm[4:0]
                        mnemonic = switch (funct3) {
                            case 1 -> "CSRRW"; case 2 -> "CSRRS"; case 3 -> "CSRRC";
                            case 5 -> "CSRRWI"; case 6 -> "CSRRSI"; case 7 -> "CSRRCI";
                            default -> "?CSR?";
                        };
                        int newVal = switch (funct3) {
                            case 1 -> x[rs1];                // CSRRW
                            case 2 -> csrVal |  x[rs1];     // CSRRS
                            case 3 -> csrVal & ~x[rs1];     // CSRRC
                            case 5 -> uimm;                  // CSRRWI
                            case 6 -> csrVal |  uimm;       // CSRRSI
                            case 7 -> csrVal & ~uimm;       // CSRRCI
                            default -> csrVal;
                        };
                        writeRd(rd, csrVal);                 // read old value into rd
                        // CSRRW/CSRRWI always write.
                        // CSRRS/CSRRC: only write if rs1 != 0 (per spec — avoids side effects on read)
                        // CSRRSI/CSRRCI: only write if uimm != 0
                        boolean doWrite = switch (funct3) {
                            case 1, 5 -> true;                 // CSRRW, CSRRWI — always write
                            case 2, 3 -> rs1 != 0;             // CSRRS, CSRRC
                            case 6, 7 -> uimm != 0;            // CSRRSI, CSRRCI
                            default -> false;
                        };
                        if (doWrite) csr.write(csrAddr, newVal);
                    }
                }

                default -> throw new CpuException(CpuException.Cause.ILLEGAL_INSTRUCTION, pc,
                        "Unknown opcode: 0x" + Integer.toHexString(opcode));
            }

        } catch (BusFaultException e) {
            boolean isStore = (opcode == OP_STORE);
            trap(isStore ? CpuException.Cause.STORE_ACCESS_FAULT.code
                         : CpuException.Cause.LOAD_ACCESS_FAULT.code, pc, e.getAddress());
            return;
        } catch (CpuException e) {
            trap(e.getExceptionCause().code, e.getPC(), e.getBadAddress());
            return;
        }

        // --- Trace ---
        if (tracer.isEnabled()) {
            tracer.record(new TraceRecord(cycle, pc, instr, rd, x[rd],
                    rs1, x[rs1], rs2, x[rs2], mnemonic));
        }

        pc = nextPc;
        x[0] = 0; // x0 always zero — enforce the invariant
        cycle++;
    }

    // -------------------------------------------------------------------------
    // Interrupt / Trap entry
    // -------------------------------------------------------------------------

    private void handleInterrupt(int irq) {
        int cause;
        if (irq == InterruptController.IRQ_TIMER) {
            cause = CSRFile.MCAUSE_MACHINE_TIMER_IRQ;
        } else {
            cause = CSRFile.MCAUSE_EXTERNAL_IRQ;
        }
        csr.enterTrap(pc, cause, irq);
        irqCtrl.acknowledge(irq);
        pc = csr.getMtvec();
    }

    private void trap(int causeCode, int trapPc, int badAddr) {
        csr.enterTrap(trapPc, causeCode, badAddr);
        int mtvec = csr.getMtvec();
        if (mtvec == 0) {
            // No trap handler installed — halt with diagnostic
            System.err.printf("[FAULT] cause=%d PC=0x%08X addr=0x%08X — no trap vector, halting%n",
                    causeCode, trapPc, badAddr);
            if (tracer.isEnabled()) tracer.dump(System.err);
            halted = true;
        } else {
            pc = mtvec;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void writeRd(int rd, int value) {
        if (rd != 0) x[rd] = value;
    }

    // -------------------------------------------------------------------------
    // Public accessors (for debugger, tests, ECALL handler)
    // -------------------------------------------------------------------------

    public int  getReg(int n)       { return x[n]; }
    public void setReg(int n, int v){ if (n != 0) x[n] = v; }
    public int  getPC()             { return pc; }
    public void setPC(int pc)       { this.pc = pc; }
    public long getCycle()          { return cycle; }
    public boolean isHalted()       { return halted; }
    public void halt()              { halted = true; }
    public CSRFile getCSR()         { return csr; }
    public SystemBus getBus()       { return bus; }

    /** ABI register names for diagnostics. */
    public static final String[] ABI_NAMES = {
        "zero","ra","sp","gp","tp","t0","t1","t2",
        "s0","s1","a0","a1","a2","a3","a4","a5",
        "a6","a7","s2","s3","s4","s5","s6","s7",
        "s8","s9","s10","s11","t3","t4","t5","t6"
    };

    /** Dump all registers (for post-mortem debugging). */
    public void dumpRegisters(java.io.PrintStream out) {
        out.printf("PC = 0x%08X  cycle = %d%n", pc, cycle);
        for (int i = 0; i < 32; i++) {
            out.printf("  x%-2d (%-4s) = 0x%08X  (%d)%n", i, ABI_NAMES[i], x[i], x[i]);
        }
    }
}
