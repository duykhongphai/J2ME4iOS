package com.siemens.mp.lcdui;

import java.io.IOException;

/** Siemens image helpers (MIDP-1 Siemens phones, SDK 2.x). */
public class Image extends com.siemens.mp.ui.Image {
    public Image(int imageWidth, int imageHeight) { super(imageWidth, imageHeight); }

    public Image(byte[] imageData, int imageWidth, int imageHeight) { super(imageData, imageWidth, imageHeight); }

    public Image(byte[] imageData, int imageWidth, int imageHeight, boolean transparent) {
        super(imageData, imageWidth, imageHeight, transparent);
    }

    public Image(javax.microedition.lcdui.Image image) { super(image); }

    public static javax.microedition.lcdui.Image createImageFromFile(String resname, boolean scaleToFullScreen) throws IOException {
        return javax.microedition.lcdui.Image.createImage(resname);
    }

    public static javax.microedition.lcdui.Image createImageFromFile(String resname, int scaleToWidth, int scaleToHeight) throws IOException {
        return javax.microedition.lcdui.Image.createImage(resname);
    }

    /* Re-declared so that both class names resolve these statics directly. */
    public static javax.microedition.lcdui.Image createImageFromBitmap(byte[] imageData, int imageWidth, int imageHeight) {
        return com.siemens.mp.ui.Image.createImageFromBitmap(imageData, imageWidth, imageHeight);
    }

    public static javax.microedition.lcdui.Image createImageFromBitmap(byte[] imageData, byte[] alpha, int imageWidth, int imageHeight) {
        return com.siemens.mp.ui.Image.createImageFromBitmap(imageData, alpha, imageWidth, imageHeight);
    }

    public static javax.microedition.lcdui.Image createTransparentImageFromBitmap(byte[] imageData, int imageWidth, int imageHeight) {
        return com.siemens.mp.ui.Image.createTransparentImageFromBitmap(imageData, imageWidth, imageHeight);
    }

    public static javax.microedition.lcdui.Image createImageWithScaling(String name) throws IOException {
        return javax.microedition.lcdui.Image.createImage(name);
    }

    public static javax.microedition.lcdui.Image createImageWithoutScaling(String name) throws IOException {
        return javax.microedition.lcdui.Image.createImage(name);
    }

    public static javax.microedition.lcdui.Image mirrorImageHorizontally(javax.microedition.lcdui.Image image) {
        return com.siemens.mp.ui.Image.mirrorImageHorizontally(image);
    }

    public static javax.microedition.lcdui.Image mirrorImageVertically(javax.microedition.lcdui.Image image) {
        return com.siemens.mp.ui.Image.mirrorImageVertically(image);
    }

    public static int getPixelColor(javax.microedition.lcdui.Image image, int x, int y) {
        return com.siemens.mp.ui.Image.getPixelColor(image, x, y);
    }

    public static void setPixelColor(javax.microedition.lcdui.Image image, int x, int y, int color) {
        com.siemens.mp.ui.Image.setPixelColor(image, x, y, color);
    }
}
