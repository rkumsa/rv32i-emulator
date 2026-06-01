package com.rv32i.peripheral;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * GPIO peripheral — 32 pins, each configurable as input or output.
 *
 * Register map (base = 0x50000000):
 *   +0x00  DATA       (RW) — pin levels (1 bit per pin)
 *   +0x04  OUTPUT_EN  (RW) — 1 = output, 0 = input (bit per pin)
 *   +0x08  IRQ_EN     (RW) — interrupt-on-change enable (bit per pin)
 *   +0x0C  IRQ_STATUS (RO) — pending IRQ bits; write 1 to clear
 *
 * Listeners (Java callbacks) are notified on every output pin change —
 * the Emulator uses this for the blinky LED demo.
 */
public class GpioPeripheral extends Peripheral {

    public static final int BASE = 0x50000000;
    private static final int SIZE = 0x100;

    private static final int REG_DATA       = 0x00;
    private static final int REG_OUTPUT_EN  = 0x04;
    private static final int REG_IRQ_EN     = 0x08;
    private static final int REG_IRQ_STATUS = 0x0C;

    private int dataReg      = 0;
    private int outputEn     = 0;
    private int irqEn        = 0;
    private int irqStatus    = 0;

    private final List<BiConsumer<Integer, Boolean>> listeners = new ArrayList<>();

    public GpioPeripheral() {
        super(BASE, SIZE);
    }

    /**
     * Register a pin-change callback.  Called on every write to DATA that
     * changes an output pin's level.
     *
     * @param listener (pinNumber, newLevel) — pinNumber 0–31
     */
    public void addPinChangeListener(BiConsumer<Integer, Boolean> listener) {
        listeners.add(listener);
    }

    @Override
    public int readByte(int addr) {
        int reg = (addr - base) & ~3;
        int word = switch (reg) {
            case REG_DATA       -> dataReg;
            case REG_OUTPUT_EN  -> outputEn;
            case REG_IRQ_EN     -> irqEn;
            case REG_IRQ_STATUS -> irqStatus;
            default             -> 0;
        };
        // Return the correct byte lane
        int byteOffset = (addr - base) & 3;
        return (word >>> (byteOffset * 8)) & 0xFF;
    }

    @Override
    public void writeByte(int addr, int value) {
        int byteOffset = (addr - base) & 3;
        int reg = (addr - base) & ~3;
        switch (reg) {
            case REG_DATA -> {
                int mask    = 0xFF << (byteOffset * 8);
                int newData = (dataReg & ~mask) | ((value & 0xFF) << (byteOffset * 8));
                int changed = (dataReg ^ newData) & outputEn; // only output pins
                dataReg = newData;
                // Notify listeners for each changed output pin
                if (changed != 0) notifyChanges(changed);
                // Raise IRQ for any enabled change
                int pending = changed & irqEn;
                if (pending != 0) {
                    irqStatus |= pending;
                    irqPending = true;
                }
            }
            case REG_OUTPUT_EN -> {
                int mask = 0xFF << (byteOffset * 8);
                outputEn = (outputEn & ~mask) | ((value & 0xFF) << (byteOffset * 8));
            }
            case REG_IRQ_EN -> {
                int mask = 0xFF << (byteOffset * 8);
                irqEn = (irqEn & ~mask) | ((value & 0xFF) << (byteOffset * 8));
            }
            case REG_IRQ_STATUS -> {
                // Write-1-to-clear
                int mask = 0xFF << (byteOffset * 8);
                irqStatus &= ~((value & 0xFF) << (byteOffset * 8));
                if (irqStatus == 0) irqPending = false;
            }
        }
    }

    private void notifyChanges(int changedMask) {
        for (int pin = 0; pin < 32; pin++) {
            if ((changedMask & (1 << pin)) != 0) {
                boolean level = (dataReg & (1 << pin)) != 0;
                for (BiConsumer<Integer, Boolean> l : listeners) l.accept(pin, level);
            }
        }
    }

    public int  getDataReg()   { return dataReg; }
    public int  getOutputEn()  { return outputEn; }
}
