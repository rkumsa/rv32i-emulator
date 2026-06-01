package com.rv32i.memory;

import com.rv32i.exception.BusFaultException;
import com.rv32i.peripheral.Peripheral;

import java.util.ArrayList;
import java.util.List;

/**
 * SystemBus — address decoder and memory map.
 *
 * Memory map (fixed):
 *   0x00000000 – 0x0001FFFF   ROM  (128 KB)
 *   0x20000000 – 0x2001FFFF   RAM  (128 KB)
 *   0x40000000 – 0x400000FF   UART
 *   0x50000000 – 0x500000FF   GPIO
 *   0x60000000 – 0x600000FF   Timer
 *
 * All multi-byte accesses are little-endian, matching RV32I spec.
 */
public class SystemBus {

    // -------------------------------------------------------------------------
    // Address map constants (used by CPU, loader, and demo programs)
    // -------------------------------------------------------------------------
    public static final int ROM_BASE  = 0x00000000;
    public static final int ROM_SIZE  = 128 * 1024;
    public static final int RAM_BASE  = 0x20000000;
    public static final int RAM_SIZE  = 128 * 1024;

    private final ROM  rom;
    private final RAM  ram;
    private final List<Peripheral> peripherals = new ArrayList<>();

    public SystemBus(ROM rom, RAM ram) {
        this.rom = rom;
        this.ram = ram;
    }

    public void addPeripheral(Peripheral p) { peripherals.add(p); }

    // -------------------------------------------------------------------------
    // Byte-level routing
    // -------------------------------------------------------------------------

    public int readByte(int addr) {
        if (rom.contains(addr)) return rom.readByte(addr);
        if (ram.contains(addr)) return ram.readByte(addr);
        for (Peripheral p : peripherals)
            if (p.contains(addr)) return p.readByte(addr);
        throw new BusFaultException(addr, "read");
    }

    public void writeByte(int addr, int value) {
        if (rom.contains(addr)) { rom.writeByte(addr, value); return; }
        if (ram.contains(addr)) { ram.writeByte(addr, value); return; }
        for (Peripheral p : peripherals) {
            if (p.contains(addr)) { p.writeByte(addr, value); return; }
        }
        throw new BusFaultException(addr, "write");
    }

    // -------------------------------------------------------------------------
    // Word / half-word — little-endian, built from byte access
    // -------------------------------------------------------------------------

    public int readWord(int addr) {
        return  readByte(addr)
             | (readByte(addr + 1) << 8)
             | (readByte(addr + 2) << 16)
             | (readByte(addr + 3) << 24);
    }

    public void writeWord(int addr, int value) {
        writeByte(addr,     value        & 0xFF);
        writeByte(addr + 1, (value >> 8) & 0xFF);
        writeByte(addr + 2, (value >>16) & 0xFF);
        writeByte(addr + 3, (value >>24) & 0xFF);
    }

    /** Unsigned half-word (16-bit) read. */
    public int readHalf(int addr) {
        return readByte(addr) | (readByte(addr + 1) << 8);
    }

    /** Sign-extended half-word read. */
    public int readHalfSigned(int addr) {
        return (short) readHalf(addr);
    }

    public void writeHalf(int addr, int value) {
        writeByte(addr,     value       & 0xFF);
        writeByte(addr + 1, (value >> 8)& 0xFF);
    }

    /** Sign-extended byte read. */
    public int readByteSigned(int addr) {
        return (byte) readByte(addr);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public ROM              getRom()          { return rom; }
    public RAM              getRam()          { return ram; }
    public List<Peripheral> getPeripherals()  { return peripherals; }
}
