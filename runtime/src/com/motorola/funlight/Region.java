package com.motorola.funlight;

/** A Motorola FunLight light region. */
public interface Region {
    int getColor();

    int getControl();

    int getID();

    void releaseControl();

    int setColor(byte red, byte green, byte blue);

    int setColor(int color);

    String toString();
}
