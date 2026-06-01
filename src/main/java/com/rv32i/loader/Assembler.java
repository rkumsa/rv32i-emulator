package com.rv32i.loader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mini RV32I Assembler — assembles RISC-V instructions directly in Java.
 *
 * This lets us write test programs without a cross-compiler toolchain.
 * It's a tiny two-pass assembler: first pass resolves labels, second encodes.
 *
 * Supports all RV32I instructions plus pseudo-instructions (NOP, LI, MV, RET, CALL).
 *
 * Usage:
 *   Assembler asm = new Assembler();
 *   asm.li(Reg.A0, 42);
 *   asm.ecall();
 *   byte[] binary = asm.assemble(ROM_BASE);
 */
public class Assembler {

    /** RISC-V register numbers */
    public static final class Reg {
        public static final int ZERO=0, RA=1, SP=2, GP=3, TP=4;
        public static final int T0=5, T1=6, T2=7;
        public static final int S0=8, FP=8, S1=9;
        public static final int A0=10, A1=11, A2=12, A3=13, A4=14, A5=15, A6=16, A7=17;
        public static final int S2=18, S3=19, S4=20, S5=21, S6=22, S7=23;
        public static final int S8=24, S9=25, S10=26, S11=27;
        public static final int T3=28, T4=29, T5=30, T6=31;
    }

    private final List<Object> items = new ArrayList<>(); // Integer (encoded) or String (label ref)
    private final Map<String, Integer> labels = new HashMap<>();
    private int baseAddress;

    // -------------------------------------------------------------------------
    // Label management
    // -------------------------------------------------------------------------

    /** Define a label at the current position. */
public Assembler label(String name) {
    labels.put(name, items.size() * 4);
    return this;
}
    // -------------------------------------------------------------------------
    // R-type instructions
    // -------------------------------------------------------------------------

    public Assembler add(int rd, int rs1, int rs2) { return r(rd, rs1, rs2, 0x0, 0x00); }
    public Assembler sub(int rd, int rs1, int rs2) { return r(rd, rs1, rs2, 0x0, 0x20); }
    public Assembler sll(int rd, int rs1, int rs2) { return r(rd, rs1, rs2, 0x1, 0x00); }
    public Assembler slt(int rd, int rs1, int rs2) { return r(rd, rs1, rs2, 0x2, 0x00); }
    public Assembler sltu(int rd, int rs1, int rs2){ return r(rd, rs1, rs2, 0x3, 0x00); }
    public Assembler xor(int rd, int rs1, int rs2) { return r(rd, rs1, rs2, 0x4, 0x00); }
    public Assembler srl(int rd, int rs1, int rs2) { return r(rd, rs1, rs2, 0x5, 0x00); }
    public Assembler sra(int rd, int rs1, int rs2) { return r(rd, rs1, rs2, 0x5, 0x20); }
    public Assembler or(int rd, int rs1, int rs2)  { return r(rd, rs1, rs2, 0x6, 0x00); }
    public Assembler and(int rd, int rs1, int rs2) { return r(rd, rs1, rs2, 0x7, 0x00); }

    // -------------------------------------------------------------------------
    // I-type ALU
    // -------------------------------------------------------------------------

    public Assembler addi(int rd, int rs1, int imm) { return i(rd, rs1, imm, 0x0, 0x13); }
    public Assembler slti(int rd, int rs1, int imm) { return i(rd, rs1, imm, 0x2, 0x13); }
    public Assembler sltiu(int rd, int rs1, int imm){ return i(rd, rs1, imm, 0x3, 0x13); }
    public Assembler xori(int rd, int rs1, int imm) { return i(rd, rs1, imm, 0x4, 0x13); }
    public Assembler ori(int rd, int rs1, int imm)  { return i(rd, rs1, imm, 0x6, 0x13); }
    public Assembler andi(int rd, int rs1, int imm) { return i(rd, rs1, imm, 0x7, 0x13); }
    public Assembler slli(int rd, int rs1, int shamt){ return emit(encodeShift(rd, rs1, shamt, 0x1, 0x00)); }
    public Assembler srli(int rd, int rs1, int shamt){ return emit(encodeShift(rd, rs1, shamt, 0x5, 0x00)); }
    public Assembler srai(int rd, int rs1, int shamt){ return emit(encodeShift(rd, rs1, shamt, 0x5, 0x20)); }

    // -------------------------------------------------------------------------
    // Load / Store
    // -------------------------------------------------------------------------

    public Assembler lb(int rd, int rs1, int off)  { return i(rd, rs1, off, 0x0, 0x03); }
    public Assembler lh(int rd, int rs1, int off)  { return i(rd, rs1, off, 0x1, 0x03); }
    public Assembler lw(int rd, int rs1, int off)  { return i(rd, rs1, off, 0x2, 0x03); }
    public Assembler lbu(int rd, int rs1, int off) { return i(rd, rs1, off, 0x4, 0x03); }
    public Assembler lhu(int rd, int rs1, int off) { return i(rd, rs1, off, 0x5, 0x03); }
    public Assembler sb(int rs1, int rs2, int off) { return s(rs1, rs2, off, 0x0); }
    public Assembler sh(int rs1, int rs2, int off) { return s(rs1, rs2, off, 0x1); }
    public Assembler sw(int rs1, int rs2, int off) { return s(rs1, rs2, off, 0x2); }

    // -------------------------------------------------------------------------
    // Branch (label-resolved in second pass)
    // -------------------------------------------------------------------------

    public Assembler beq(int rs1, int rs2, String label)  { return bLabel(rs1, rs2, label, 0x0); }
    public Assembler bne(int rs1, int rs2, String label)  { return bLabel(rs1, rs2, label, 0x1); }
    public Assembler blt(int rs1, int rs2, String label)  { return bLabel(rs1, rs2, label, 0x4); }
    public Assembler bge(int rs1, int rs2, String label)  { return bLabel(rs1, rs2, label, 0x5); }
    public Assembler bltu(int rs1, int rs2, String label) { return bLabel(rs1, rs2, label, 0x6); }
    public Assembler bgeu(int rs1, int rs2, String label) { return bLabel(rs1, rs2, label, 0x7); }

    public Assembler beq(int rs1, int rs2, int off)  { return emit(encodeBranch(rs1, rs2, off, 0x0)); }
    public Assembler bne(int rs1, int rs2, int off)  { return emit(encodeBranch(rs1, rs2, off, 0x1)); }
    public Assembler blt(int rs1, int rs2, int off)  { return emit(encodeBranch(rs1, rs2, off, 0x4)); }
    public Assembler bge(int rs1, int rs2, int off)  { return emit(encodeBranch(rs1, rs2, off, 0x5)); }

    // -------------------------------------------------------------------------
    // Jump
    // -------------------------------------------------------------------------

public Assembler jal(int rd, String label) {
    items.add("JAL:" + rd + ":" + label);
    return this;
}
    public Assembler jal(int rd, int off) {
        return emit(encodeJal(rd, off));
    }

    public Assembler jalr(int rd, int rs1, int off) {
        return emit((off & 0xFFF) << 20 | (rs1 << 15) | (0 << 12) | (rd << 7) | 0x67);
    }

    // -------------------------------------------------------------------------
    // U-type
    // -------------------------------------------------------------------------

    public Assembler lui(int rd, int imm20) {
        return emit((imm20 & 0xFFFFF) << 12 | (rd << 7) | 0x37);
    }

    public Assembler auipc(int rd, int imm20) {
        return emit((imm20 & 0xFFFFF) << 12 | (rd << 7) | 0x17);
    }

    // -------------------------------------------------------------------------
    // System
    // -------------------------------------------------------------------------

    public Assembler ecall()  { return emit(0x00000073); }
    public Assembler ebreak() { return emit(0x00100073); }
    public Assembler mret()   { return emit(0x30200073); }

    public Assembler csrrw(int rd, int csr, int rs1) { return csr_(rd, rs1, csr, 0x1); }
    public Assembler csrrs(int rd, int csr, int rs1) { return csr_(rd, rs1, csr, 0x2); }
    public Assembler csrrc(int rd, int csr, int rs1) { return csr_(rd, rs1, csr, 0x3); }
    public Assembler csrrwi(int rd, int csr, int uimm){ return csr_(rd, uimm, csr, 0x5); }
    public Assembler csrrsi(int rd, int csr, int uimm){ return csr_(rd, uimm, csr, 0x6); }
    public Assembler csrrci(int rd, int csr, int uimm){ return csr_(rd, uimm, csr, 0x7); }

    // -------------------------------------------------------------------------
    // Pseudo-instructions (expand to real instructions)
    // -------------------------------------------------------------------------

    /** NOP — ADDI x0, x0, 0 */
    public Assembler nop() { return addi(Reg.ZERO, Reg.ZERO, 0); }

    /** MV rd, rs — ADDI rd, rs, 0 */
    public Assembler mv(int rd, int rs) { return addi(rd, rs, 0); }

    /** NOT rd, rs — XORI rd, rs, -1 */
    public Assembler not(int rd, int rs) { return xori(rd, rs, -1); }

    /** NEG rd, rs — SUB rd, x0, rs */
    public Assembler neg(int rd, int rs) { return sub(rd, Reg.ZERO, rs); }

    /** RET — JALR x0, x1, 0 */
    public Assembler ret() { return jalr(Reg.ZERO, Reg.RA, 0); }

    /** J offset — JAL x0, offset */
    public Assembler j(String label) { return jal(Reg.ZERO, label); }

    /**
     * LI rd, imm — load a 32-bit immediate.
     * Expands to LUI+ADDI if needed.
     */
    public Assembler li(int rd, int imm) {
        if (imm >= -2048 && imm <= 2047) {
            return addi(rd, Reg.ZERO, imm);
        } else {
            int hi = (imm + 0x800) >>> 12; // round for sign extension
            int lo = imm - (hi << 12);
            lui(rd, hi);
            return addi(rd, rd, lo);
        }
    }

    /** Raw 32-bit word (for data or hand-crafted instructions). */
    public Assembler word(int value) { return emit(value); }

    // -------------------------------------------------------------------------
    // Second-pass assembly → binary
    // -------------------------------------------------------------------------

    /** Assemble all instructions into a byte array. */
    public byte[] assemble(int baseAddr) {
        this.baseAddress = baseAddr;
        // Remove placeholder slots from jal+label pairs
        List<Object> resolved = resolveLabels();
        byte[] out = new byte[resolved.size() * 4];
        for (int i = 0; i < resolved.size(); i++) {
            int instr = (Integer) resolved.get(i);
            out[i*4]   = (byte)(instr & 0xFF);
            out[i*4+1] = (byte)((instr >> 8) & 0xFF);
            out[i*4+2] = (byte)((instr >> 16) & 0xFF);
            out[i*4+3] = (byte)((instr >> 24) & 0xFF);
        }
        return out;
    }

    private List<Object> resolveLabels() {
        // First: expand JAL+label into encoded instruction
        List<Integer> instrs = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (item instanceof String s && s.startsWith("JAL:")) {
                String[] parts = s.split(":");
                int rd2  = Integer.parseInt(parts[1]);
                String lbl = parts[2];
                Integer labelOffset = labels.get(lbl);
                if (labelOffset == null) throw new RuntimeException("Undefined label: " + lbl);
                int off = labelOffset - (instrs.size() * 4);
                instrs.add(encodeJal(rd2, off));
            } else if (item instanceof String s && s.startsWith("B:")) {
                String[] parts = s.split(":");
                int rs1b  = Integer.parseInt(parts[1]);
                int rs2b  = Integer.parseInt(parts[2]);
                String lbl = parts[3];
                int f3   = Integer.parseInt(parts[4]);
                Integer labelOffset = labels.get(lbl);
                if (labelOffset == null) throw new RuntimeException("Undefined label: " + lbl);
                int off = labelOffset - (instrs.size() * 4);
                instrs.add(encodeBranch(rs1b, rs2b, off, f3));
            } else {
                instrs.add((Integer) item);
            }
        }
        return new ArrayList<>(instrs);
    }

    public int size() { return items.size() * 4; }

    // -------------------------------------------------------------------------
    // Private encoders
    // -------------------------------------------------------------------------

    private Assembler emit(int instr) { items.add(instr); return this; }

    private Assembler r(int rd, int rs1, int rs2, int f3, int f7) {
        return emit((f7 << 25) | (rs2 << 20) | (rs1 << 15) | (f3 << 12) | (rd << 7) | 0x33);
    }

    private Assembler i(int rd, int rs1, int imm, int f3, int op) {
        return emit(((imm & 0xFFF) << 20) | (rs1 << 15) | (f3 << 12) | (rd << 7) | op);
    }

    private Assembler s(int rs1, int rs2, int off, int f3) {
        int hi = (off >> 5) & 0x7F;
        int lo = off & 0x1F;
        return emit((hi << 25) | (rs2 << 20) | (rs1 << 15) | (f3 << 12) | (lo << 7) | 0x23);
    }

    private Assembler bLabel(int rs1, int rs2, String label, int f3) {
        items.add("B:" + rs1 + ":" + rs2 + ":" + label + ":" + f3);
        return this;
    }

    private int encodeBranch(int rs1, int rs2, int off, int f3) {
        int b12  = (off >> 12) & 1;
        int b11  = (off >> 11) & 1;
        int b10_5 = (off >> 5) & 0x3F;
        int b4_1  = (off >> 1) & 0xF;
        return (b12 << 31) | (b10_5 << 25) | (rs2 << 20) | (rs1 << 15)
             | (f3 << 12) | (b4_1 << 8) | (b11 << 7) | 0x63;
    }

    private int encodeJal(int rd, int off) {
        int b20    = (off >> 20) & 1;
        int b19_12 = (off >> 12) & 0xFF;
        int b11    = (off >> 11) & 1;
        int b10_1  = (off >> 1)  & 0x3FF;
        return (b20 << 31) | (b10_1 << 21) | (b11 << 20) | (b19_12 << 12) | (rd << 7) | 0x6F;
    }

    private int encodeShift(int rd, int rs1, int shamt, int f3, int f7) {
        return (f7 << 25) | ((shamt & 0x1F) << 20) | (rs1 << 15) | (f3 << 12) | (rd << 7) | 0x13;
    }

    private Assembler csr_(int rd, int rs1_uimm, int csrAddr, int f3) {
        return emit((csrAddr << 20) | (rs1_uimm << 15) | (f3 << 12) | (rd << 7) | 0x73);
    }
}
