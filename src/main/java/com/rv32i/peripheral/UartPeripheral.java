package com.rv32i.peripheral;

import java.io.PrintStream;

/**
 * Simple UART peripheral — TX only (sufficient for printf-style output).
 *
 * Register map (base = 0x40000000):
 *   +0x00  TX_DATA   (WO) — write a byte here to transmit it
 *   +0x04  TX_STATUS (RO) — bit 0 = TX_READY (always 1 in emulation)
 *   +0x08  RX_DATA   (RO) — received byte (0 if empty in this stub)
 *   +0x0C  RX_STATUS (RO) — bit 0 = RX_READY (always 0 — no RX source)
 *   +0x10  CTRL      (RW) — bit 0 = TX_IRQ_ENABLE, bit 1 = RX_IRQ_ENABLE
 *
 * Writing a byte to TX_DATA triggers the Java PrintStream immediately —
 * this models a UART whose TX FIFO is always empty.
 */
public class UartPeripheral extends Peripheral {

    public static final int BASE = 0x40000000;
    private static final int SIZE = 0x100;

    private static final int REG_TX_DATA   = 0x00;
    private static final int REG_TX_STATUS = 0x04;
    private static final int REG_RX_DATA   = 0x08;
    private static final int REG_RX_STATUS = 0x0C;
    private static final int REG_CTRL      = 0x10;

    private final PrintStream out;
    private int ctrl = 0;

    public UartPeripheral(PrintStream out) {
        super(BASE, SIZE);
        this.out = out;
    }

    @Override
    public int readByte(int addr) {
        int reg = addr - base;
        return switch (reg & ~3) {          // word-align for sub-byte access
            case REG_TX_STATUS -> 0x01;     // TX always ready
            case REG_RX_DATA   -> 0x00;     // no RX source
            case REG_RX_STATUS -> 0x00;     // no data available
            case REG_CTRL      -> ctrl & 0xFF;
            default            -> 0;
        };
    }

@Override
public void writeByte(int addr, int value) {
    int reg = (addr - base) & ~3;
    int byteOffset = (addr - base) & 3;
    switch (reg) {
        case REG_TX_DATA -> {
            // Only transmit on the lowest byte lane (byte 0 of the word).
            // SW writes all 4 bytes; bytes 1-3 are zero-padding, not characters.
            if (byteOffset == 0) {
                char c = (char)(value & 0xFF);
                if (c != 0) out.print(c);
            }
        }
        case REG_CTRL -> ctrl = value & 0xFF;
    }
}
}
