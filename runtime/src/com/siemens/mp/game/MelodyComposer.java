package com.siemens.mp.game;

import j4i.Tones;

/** Siemens melody composer: a list of (note, length) pairs rendered by Melody. */
public class MelodyComposer extends com.siemens.mp.misc.NativeMem {
    public static final int TONE_C0 = 0;
    public static final int TONE_CIS0 = 1;
    public static final int TONE_D0 = 2;
    public static final int TONE_DIS0 = 3;
    public static final int TONE_E0 = 4;
    public static final int TONE_F0 = 5;
    public static final int TONE_FIS0 = 6;
    public static final int TONE_G0 = 7;
    public static final int TONE_GIS0 = 8;
    public static final int TONE_A0 = 9;
    public static final int TONE_AIS0 = 10;
    public static final int TONE_H0 = 11;
    public static final int TONE_C1 = 12;
    public static final int TONE_CIS1 = 13;
    public static final int TONE_D1 = 14;
    public static final int TONE_DIS1 = 15;
    public static final int TONE_E1 = 16;
    public static final int TONE_F1 = 17;
    public static final int TONE_FIS1 = 18;
    public static final int TONE_G1 = 19;
    public static final int TONE_GIS1 = 20;
    public static final int TONE_A1 = 21;
    public static final int TONE_AIS1 = 22;
    public static final int TONE_H1 = 23;
    public static final int TONE_C2 = 24;
    public static final int TONE_CIS2 = 25;
    public static final int TONE_D2 = 26;
    public static final int TONE_DIS2 = 27;
    public static final int TONE_E2 = 28;
    public static final int TONE_F2 = 29;
    public static final int TONE_FIS2 = 30;
    public static final int TONE_G2 = 31;
    public static final int TONE_GIS2 = 32;
    public static final int TONE_A2 = 33;
    public static final int TONE_AIS2 = 34;
    public static final int TONE_H2 = 35;
    public static final int TONE_C3 = 36;
    public static final int TONE_CIS3 = 37;
    public static final int TONE_D3 = 38;
    public static final int TONE_DIS3 = 39;
    public static final int TONE_E3 = 40;
    public static final int TONE_F3 = 41;
    public static final int TONE_FIS3 = 42;
    public static final int TONE_G3 = 43;
    public static final int TONE_GIS3 = 44;
    public static final int TONE_A3 = 45;
    public static final int TONE_AIS3 = 46;
    public static final int TONE_H3 = 47;
    public static final int TONE_C4 = 48;
    public static final int TONE_CIS4 = 49;
    public static final int TONE_D4 = 50;
    public static final int TONE_DIS4 = 51;
    public static final int TONE_E4 = 52;
    public static final int TONE_F4 = 53;
    public static final int TONE_FIS4 = 54;
    public static final int TONE_G4 = 55;
    public static final int TONE_GIS4 = 56;
    public static final int TONE_A4 = 57;
    public static final int TONE_PAUSE = 58;
    public static final int NO_TONE = 59;
    public static final int TONE_STOP = 60;
    public static final int TONE_REPEAT = 61;
    public static final int TONE_REPEV = 62;
    public static final int TONE_REPON = 63;
    public static final int TONE_MARK = 64;
    public static final int TONE_REPEAT_MARK = 65;
    public static final int TONE_REPEV_MARK = 66;
    public static final int TONE_REPON_MARK = 67;
    public static final int TONELENGTH_1_1 = 0;
    public static final int TONELENGTH_1_2 = 1;
    public static final int TONELENGTH_1_4 = 2;
    public static final int TONELENGTH_1_8 = 3;
    public static final int TONELENGTH_1_16 = 4;
    public static final int TONELENGTH_1_32 = 5;
    public static final int TONELENGTH_1_64 = 6;
    public static final int TONELENGTH_DOTTED_1_1 = 7;
    public static final int TONELENGTH_DOTTED_1_2 = 8;
    public static final int TONELENGTH_DOTTED_1_4 = 9;
    public static final int TONELENGTH_DOTTED_1_8 = 10;
    public static final int TONELENGTH_DOTTED_1_16 = 11;
    public static final int TONELENGTH_DOTTED_1_32 = 12;
    public static final int TONELENGTH_DOTTED_1_64 = 13;
    public static final int BPM = 60;

    private static final int MAX_NOTES = 512;
    /** TONE_C0 is mapped to MIDI note 48 (C3, ~131 Hz), so TONE_A4 is ~3520 Hz. */
    private static final int MIDI_BASE = 48;

    private int[] notes = new int[32];
    private int[] lens = new int[32];
    private int count;
    private int bpm = BPM;

    public MelodyComposer() {}

    /** notes holds (note, length) pairs. */
    public MelodyComposer(int[] notes, int bpm) {
        setBPM(bpm);
        if (notes != null) for (int i = 0; i + 1 < notes.length; i += 2) appendNote(notes[i], notes[i + 1]);
    }

    public void appendNote(int note, int length) {
        if (count >= MAX_NOTES) throw new IllegalArgumentException("melody too long");
        if (count == notes.length) {
            int[] n = new int[count * 2], l = new int[count * 2];
            System.arraycopy(notes, 0, n, 0, count);
            System.arraycopy(lens, 0, l, 0, count);
            notes = n;
            lens = l;
        }
        notes[count] = note;
        lens[count] = length;
        count++;
    }

    public Melody getMelody() {
        int cap = 64, n = 0, mark = 0;
        int[] hz = new int[cap], ms = new int[cap];
        boolean loop = false;
        for (int i = 0; i < count; i++) {
            int note = notes[i], len = lens[i];
            if (note == TONE_STOP) break;
            if (note == TONE_MARK || note == TONE_REPEAT_MARK || note == TONE_REPEV_MARK || note == TONE_REPON_MARK) {
                mark = n;
                continue;
            }
            if (note == TONE_REPEV) {
                loop = true;
                break;
            }
            if (note == TONE_REPEAT || note == TONE_REPON) {
                int times = len <= 0 ? 1 : Math.min(len, 16);
                int segEnd = n;
                for (int t = 0; t < times; t++) {
                    for (int k = mark; k < segEnd; k++) {
                        if (n == cap) {
                            cap *= 2;
                            hz = grow(hz, cap);
                            ms = grow(ms, cap);
                        }
                        hz[n] = hz[k];
                        ms[n] = ms[k];
                        n++;
                    }
                }
                continue;
            }
            if (n == cap) {
                cap *= 2;
                hz = grow(hz, cap);
                ms = grow(ms, cap);
            }
            hz[n] = (note >= TONE_C0 && note <= TONE_A4) ? Tones.noteHz(note + MIDI_BASE) : 0;
            ms[n] = duration(len);
            n++;
        }
        return new Melody(hz, ms, n, loop);
    }

    private int duration(int len) {
        boolean dotted = len >= TONELENGTH_DOTTED_1_1;
        if (dotted) len -= TONELENGTH_DOTTED_1_1;
        if (len < 0) len = 0;
        if (len > 6) len = 6;
        int whole = 240000 / (bpm <= 0 ? BPM : bpm);
        int d = whole >> len;
        return dotted ? d + d / 2 : d;
    }

    private static int[] grow(int[] a, int cap) {
        int[] n = new int[cap];
        System.arraycopy(a, 0, n, 0, a.length);
        return n;
    }

    public int length() { return count; }

    public static int maxLength() { return MAX_NOTES; }

    public void resetMelody() { count = 0; }

    public void setBPM(int bpm) {
        if (bpm <= 0) throw new IllegalArgumentException();
        this.bpm = bpm;
    }
}
