package dev.corgitaco.worldviewer.client.render;

public class ColorUtils {

    public class ARGB {

        public static int packARGB(int alpha, int red, int green, int blue) {
            // Shift the components to their respective positions and combine them
            return packARGB((byte) alpha, (byte) red, (byte) green, ((byte) blue));
        }

        public static int packARGB(byte alpha, byte red, byte green, byte blue) {
            // Shift the components to their respective positions and combine them
            return  ((alpha << 24) | (red << 16) | (green << 8) | blue);
        }

        public static int tryParseColor(String hexColor) {
            int value = Integer.parseInt(hexColor.substring(2), 16);

            int red = (value >> 16) & 0xFF;
            int green = (value >> 8) & 0xFF;
            int blue = value & 0xFF;

            int alpha = 255;

            return packARGB((byte) alpha, (byte) red, (byte) green, (byte) blue);
        }

        public static int _ARGBFromABGR(int abgr) {
            return  (ABGR.unpackAlpha(abgr) | ABGR.unpackRed(abgr) | ABGR.unpackGreen(abgr) | ABGR.unpackBlue(abgr));
        }

        public static byte unpackAlpha(int packedColor) {
            return (byte) ((packedColor >> 24) & 0xFF);
        }

        public static byte unpackRed(int packedColor) {
            return (byte) ((packedColor >> 16) & 0xFF);
        }

        public static byte unpackGreen(int packedColor) {
            return (byte) ((packedColor >> 8) & 0xFF);
        }

        public static byte unpackBlue(int packedColor) {
            return (byte) (packedColor & 0xFF);
        }


    }

    public class ABGR {

        public static int packABGR(int alpha, int blue, int green, int red) {
            return ((alpha << 24) | (blue << 16) | (green << 8) | red);
        }

        public static int tryParseColor(String hexColor) {
            int value = Integer.parseInt(hexColor.substring(2), 16);

            int red = (value >> 16) & 0xFF;
            int green = (value >> 8) & 0xFF;
            int blue = value & 0xFF;

            int alpha = 255;

            return packABGR( alpha,  blue,  green,  red);
        }

        public static byte _ABGRFromARGB(byte argb) {
            return (byte) (ARGB.unpackAlpha(argb) | ARGB.unpackBlue(argb) | ARGB.unpackGreen(argb) | ARGB.unpackRed(argb));
        }

        public static int unpackAlpha(int packedColor) {
            // Shift the alpha component to the rightmost 8 bits and mask out other bits
            return  ((packedColor >> 24) & 0xFF);
        }

        public static int unpackBlue(int packedColor) {
            return ((packedColor >> 16) & 0xFF);
        }

        public static int unpackGreen(int packedColor) {
            return  ((packedColor >> 8) & 0xFF);
        }

        public static int unpackRed(int packedColor) {
            return  (packedColor & 0xFF);
        }
    }
}
