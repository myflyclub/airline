package com.patson.util;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;
import javax.imageio.ImageIO;

public class LogoGenerator {
    private static final Random random = new Random();
    public static final int TARGET_WIDTH = 128;
    public static final int TARGET_HEIGHT = 64;
    public static final int PROCEDURAL_COUNT = 56;

    public static byte[] generateLogo(int patternIndex, int color1Rgb, int color2Rgb) throws IOException {
        BufferedImage image = new BufferedImage(TARGET_WIDTH, TARGET_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        int index = Math.abs(patternIndex % PROCEDURAL_COUNT);
        Color color1 = new Color(color1Rgb);
        Color color2 = new Color(color2Rgb);

        renderProcedural(g2d, index, color1, color2);

        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        baos.flush();
        return baos.toByteArray();
    }

    private static void renderProcedural(Graphics2D g, int index, Color c1, Color c2) {
        // 1. Fill Background
        g.setColor(c1);
        g.fillRect(0, 0, TARGET_WIDTH, TARGET_HEIGHT);

        // 2. Setup Foreground
        g.setColor(c2);
        g.setStroke(new BasicStroke(1));

        int cx = TARGET_WIDTH / 2;
        int cy = TARGET_HEIGHT / 2;

        /*
         * ORGANIZATION KEY
         * 00-09: Basic Geometry & Splits
         * 10-19: Aviation
         * 20-28: Nature
         * 29-38: Objects & Symbols
         * 39-48: Faces & Emotes
         * 49-58: Text & Humor
         */

        switch (index) {
            // ==========================================
            // GROUP 1: BASIC GEOMETRY
            // ==========================================
            case 0: // Horizontal Split
                g.fillRect(0, cy, TARGET_WIDTH, cy);
                break;
            case 1: // Vertical Split
                g.fillRect(cx, 0, cx, TARGET_HEIGHT);
                break;
            case 2: // Diagonal Split \
                g.fillPolygon(new int[]{0, TARGET_WIDTH, TARGET_WIDTH}, new int[]{0, 0, TARGET_HEIGHT}, 3);
                break;
            case 3: // Horizontal Stripes
                int h = TARGET_HEIGHT / 5;
                g.fillRect(0, h, TARGET_WIDTH, h);
                g.fillRect(0, h * 3, TARGET_WIDTH, h);
                break;
            case 4: // Vertical Stripes
                int w = TARGET_WIDTH / 7;
                g.fillRect(w, 0, w, TARGET_HEIGHT);
                g.fillRect(w * 3, 0, w, TARGET_HEIGHT);
                g.fillRect(w * 5, 0, w, TARGET_HEIGHT);
                break;
            case 5: // Center Circle
                int r = 40;
                g.fillOval(cx - r / 2, cy - r / 2, r, r);
                break;
            case 6: // Chevron Right
                g.fillPolygon(new int[]{0, cx, 0}, new int[]{0, cy, cy}, 3);
                g.setColor(c2.darker());
                g.fillPolygon(new int[]{0, cx, 0}, new int[]{TARGET_HEIGHT, cy, cy}, 3);
                break;
            case 7: // Diamond
                g.fillPolygon(new int[]{cx, cx + 25, cx, cx - 25},
                        new int[]{cy - 25, cy, cy + 25, cy}, 4);
                break;
            case 8: // Half sun
                g.setColor(c2.darker());
                g.fillOval(cx - 25, cy - 25, 50, 50);
                g.setColor(c2);
                g.fillArc(cx - 25, cy - 25, 50, 50, 45, 180);
                break;

            // ==========================================
            // GROUP 2: AVIATION
            // ==========================================
            case 9: // Delta
                g.fillPolygon(new int[]{cx - 40, cx + 40, cx - 25},
                        new int[]{cy + 25, cy, cy - 28}, 3);
                g.setColor(c2.darker().darker());
                g.fillPolygon(new int[]{cx - 40, cx + 40, cx - 12},
                        new int[]{cy + 25, cy, cy + 10}, 3);
                break;
            case 10: // Airliner
                g.translate(cx, cy);
                g.rotate(Math.toRadians(-15));
                g.fillRoundRect(-45, -8, 90, 15, 15, 15);
                g.fillPolygon(new int[]{-40, -25, -45}, new int[]{-5, -5, -25}, 3); // Tail
                g.fillPolygon(new int[]{-10, 15, -20}, new int[]{3, 3, 28}, 3); // Wing
                g.rotate(Math.toRadians(15));
                g.translate(-cx, -cy);
                break;
            case 11: // Propeller
                g.translate(cx, cy);
                g.rotate(Math.toRadians(45));
                g.fillOval(-30, -5, 60, 10);
                g.rotate(Math.toRadians(90));
                g.fillOval(-30, -5, 60, 10);
                g.rotate(Math.toRadians(-135));
                g.translate(-cx, -cy);
                g.setColor(c1);
                g.fillOval(cx - 5, cy - 5, 10, 10);
                break;
            case 12: // Tail Fin
                g.fillPolygon(new int[]{50, 80, 70, 40}, new int[]{55, 55, 10, 10}, 4);
                g.setColor(c1);
                g.fillPolygon(new int[]{58, 73, 68, 53}, new int[]{50, 50, 20, 20}, 4);
                break;
            case 13: // Runway
                g.fillPolygon(new int[]{cx - 3, cx + 3, cx + 15, cx - 15},
                        new int[]{5, 5, TARGET_HEIGHT - 5, TARGET_HEIGHT - 5}, 4);
                g.setColor(c1);
                for (int i = 0; i < 4; i++) {
                    g.fillRect(cx - 1, 12 + i * 13, 2, 7);
                }
                break;
            case 14: // Arrow Jet
                g.fillRoundRect(30, cy - 5, 65, 10, 10, 10);
                g.fillPolygon(new int[]{50, 70, 55, 35}, new int[]{cy, cy, 10, 10}, 4);
                g.fillPolygon(new int[]{50, 70, 55, 35}, new int[]{cy, cy, TARGET_HEIGHT - 10, TARGET_HEIGHT - 10}, 4);
                g.fillPolygon(new int[]{30, 40, 30}, new int[]{cy - 3, cy, cy + 3}, 3);
                break;
            case 15: // Radar Sweep
                g.setStroke(new BasicStroke(2));
                g.drawOval(cx - 25, cy - 25, 50, 50);
                g.drawOval(cx - 15, cy - 15, 30, 30);
                g.fillArc(cx - 25, cy - 25, 50, 50, 45, 45);
                break;
            case 16: // Bird Flight
                g.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawPolyline(new int[]{cx - 15, cx, cx + 15}, new int[]{cy - 10, cy + 2, cy - 10}, 3);
                g.drawPolyline(new int[]{cx - 42, cx - 28, cx - 15}, new int[]{cy + 10, cy + 20, cy + 10}, 3);
                g.drawPolyline(new int[]{cx + 15, cx + 28, cx + 42}, new int[]{cy + 10, cy + 20, cy + 10}, 3);
                break;
            case 17: // Connectivity (Orbit)
                g.fillOval(cx - 10, cy - 10, 20, 20);
                g.fillOval(cx - 35, cy - 5, 10, 10);
                g.fillOval(cx + 25, cy - 5, 10, 10);
                g.drawArc(cx - 30, cy - 20, 60, 40, 0, 360);
                break;
            case 18: // Arrow Right
                g.fillRect(20, cy - 8, TARGET_WIDTH - 60, 15);
                g.fillPolygon(new int[]{TARGET_WIDTH - 40, TARGET_WIDTH - 20, TARGET_WIDTH - 40},
                        new int[]{cy - 20, cy, cy + 20}, 3);
                break;

            // ==========================================
            // GROUP 3: NATURE (20-28)
            // ==========================================
            case 19: // Mountains (Shaded)
                g.setColor(c2.darker());
                g.fillPolygon(new int[]{10, 45, 80}, new int[]{TARGET_HEIGHT, 13, TARGET_HEIGHT}, 3);
                g.fillPolygon(new int[]{50, 90, 125}, new int[]{TARGET_HEIGHT, 23, TARGET_HEIGHT}, 3);
                g.setColor(c2); // Snow
                g.fillPolygon(new int[]{35, 45, 55}, new int[]{28, 13, 28}, 3);
                g.fillPolygon(new int[]{78, 90, 103}, new int[]{38, 23, 38}, 3);
                g.setStroke(new BasicStroke(2));
                g.drawLine(10, TARGET_HEIGHT, 45, 13);
                g.drawLine(45, 13, 80, TARGET_HEIGHT);
                g.drawLine(50, TARGET_HEIGHT, 90, 23);
                g.drawLine(90, 23, 125, TARGET_HEIGHT);
                break;
            case 20: // Cloud
                g.fillOval(cx - 30, cy - 5, 30, 30);
                g.fillOval(cx - 5, cy - 10, 35, 35);
                g.fillOval(cx + 15, cy, 25, 25);
                g.fillRect(cx - 20, cy + 15, 45, 10);
                break;
            case 21: // Sunrise
                int sunRadius = 35;
                g.fillOval(cx - sunRadius, TARGET_HEIGHT - sunRadius + 5, sunRadius * 2, sunRadius * 2);
                g.setStroke(new BasicStroke(3));
                for (int i = 0; i <= 8; i++) {
                    double angle = Math.PI + i * Math.PI / 8;
                    int x1 = (int) (cx + Math.cos(angle) * (sunRadius + 5));
                    int y1 = (int) (TARGET_HEIGHT + 5 + Math.sin(angle) * (sunRadius + 5));
                    int x2 = (int) (cx + Math.cos(angle) * (sunRadius + 23));
                    int y2 = (int) (TARGET_HEIGHT + 5 + Math.sin(angle) * (sunRadius + 23));
                    g.drawLine(x1, y1, x2, y2);
                }
                break;
            case 22: // Sun Simple
                int sunR = 30;
                g.fillOval(cx - sunR / 2, cy - sunR / 2, sunR, sunR);
                g.setStroke(new BasicStroke(3));
                for (int i = 0; i < 8; i++) {
                    double angle = i * Math.PI / 4;
                    int x1 = (int) (cx + Math.cos(angle) * (sunR / 2 + 3));
                    int y1 = (int) (cy + Math.sin(angle) * (sunR / 2 + 3));
                    int x2 = (int) (cx + Math.cos(angle) * (sunR / 2 + 10));
                    int y2 = (int) (cy + Math.sin(angle) * (sunR / 2 + 10));
                    g.drawLine(x1, y1, x2, y2);
                }
                break;
            case 23: // Globe Arcs
                g.setStroke(new BasicStroke(2));
                g.drawOval(cx - 25, cy - 25, 50, 50);
                g.drawArc(cx - 25, cy - 10, 50, 20, 0, 360);
                g.drawLine(cx, cy - 25, cx, cy + 25);
                break;
            case 24: // Global Grid
                g.setStroke(new BasicStroke(1));
                int gr = 25;
                g.drawOval(cx - gr, cy - gr, gr * 2, gr * 2);
                for (int i = 1; i < 4; i++) {
                    int offset = i * 13;
                    g.drawOval(cx - gr, cy - gr + offset / 2, gr * 2, gr * 2 - offset);
                }
                g.drawLine(cx - gr, cy, cx + gr, cy);
                break;
            case 25: // Star
                int[] xPoints = new int[10];
                int[] yPoints = new int[10];
                for (int i = 0; i < 10; i++) {
                    double r_star = (i % 2 == 0) ? 25 : 10;
                    double angle = i * Math.PI / 5 - Math.PI / 2;
                    xPoints[i] = (int) (cx + Math.cos(angle) * r_star);
                    yPoints[i] = (int) (cy + Math.sin(angle) * r_star);
                }
                g.fillPolygon(xPoints, yPoints, 10);
                break;
            case 26: // Waves
                g.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (int i = 0; i < 3; i++) {
                    int y = 25 + i * 10;
                    g.drawArc(30, y, 20, 10, 0, 180);
                    g.drawArc(50, y, 20, 10, 180, 180);
                    g.drawArc(70, y, 20, 10, 0, 180);
                }
                break;
            case 27: // Saturn
                g.fillOval(cx - 15, cy - 15, 30, 30);
                g.setStroke(new BasicStroke(3));
                g.drawOval(cx - 30, cy - 8, 60, 15);
                g.setColor(c1);
                g.setStroke(new BasicStroke(4));
                g.drawArc(cx - 30, cy - 8, 60, 15, 45, 90);
                break;

            // ==========================================
            // GROUP 4: OBJECTS & SYMBOLS (29-38)
            // ==========================================
            case 28: // Target
                g.setStroke(new BasicStroke(7));
                g.drawOval(cx - 28, cy - 28, 55, 55);
                g.drawOval(cx - 14, cy - 14, 28, 28);
                g.fillOval(cx - 6, cy - 6, 12, 12);
                break;
            case 29: // Shield
                g.fillPolygon(new int[]{cx - 20, cx + 20, cx + 20, cx, cx - 20},
                        new int[]{15, 15, 40, 55, 40}, 5);
                g.setColor(c1);
                g.fillRect(cx - 3, 20, 5, 25);
                break;
            case 30: // Diamond (Gem)
                g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawPolygon(
                        new int[]{cx - 15, cx + 15, cx + 25, cx, cx - 25},
                        new int[]{cy - 20, cy - 20, cy - 5, cy + 23, cy - 5}, 5
                );
                g.drawLine(cx - 15, cy - 20, cx - 25, cy - 5);
                g.drawLine(cx + 15, cy - 20, cx + 25, cy - 5);
                g.drawLine(cx - 25, cy - 5, cx + 25, cy - 5);
                g.drawLine(cx, cy - 20, cx, cy - 5);
                g.drawLine(cx - 15, cy - 20, cx, cy + 23);
                g.drawLine(cx + 15, cy - 20, cx, cy + 23);
                break;
            case 31: // Luggage
                g.fillRoundRect(cx - 20, cy - 10, 40, 30, 5, 5);
                g.setStroke(new BasicStroke(3));
                g.drawRect(cx - 8, cy - 18, 15, 8);
                g.setColor(c1);
                g.setStroke(new BasicStroke(2));
                g.drawLine(cx - 20, cy, cx + 20, cy);
                break;
            case 32: // Pretzel / Face
                g.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawOval(cx - 25, cy - 20, 20, 20);
                g.drawOval(cx + 5, cy - 20, 20, 20);
                g.drawArc(cx - 25, cy - 10, 50, 35, 190, 160);
                break;
            case 33: // Cross
                g.fillRect(cx - 5, 0, 10, TARGET_HEIGHT);
                g.fillRect(0, cy - 5, TARGET_WIDTH, 10);
                break;
            case 34: // Concentric Squares
                g.setStroke(new BasicStroke(5));
                g.drawRect(cx - 23, cy - 23, 45, 45);
                g.fillRect(cx - 10, cy - 10, 20, 20);
                break;
            case 35: // Radioactive
                int r_rad = 25;
                g.fillOval(cx - 11, cy - 11, 22, 22);
                g.setStroke(new BasicStroke(15));
                g.drawArc(cx - r_rad, cy - r_rad, r_rad * 2, r_rad * 2, 0, 60);
                g.drawArc(cx - r_rad, cy - r_rad, r_rad * 2, r_rad * 2, 120, 60);
                g.drawArc(cx - r_rad, cy - r_rad, r_rad * 2, r_rad * 2, 240, 60);
                break;
            case 36: // No Entry
                g.setStroke(new BasicStroke(4));
                g.drawOval(cx - 25, cy - 25, 50, 50);
                g.fillRect(cx - 18, cy - 4, 35, 8);
                break;
            case 37: // Lightning
                g.fillPolygon(new int[]{cx + 5, cx - 10, cx, cx - 5, cx + 10, cx},
                        new int[]{cy - 25, cy, cy, cy + 25, cy, cy}, 6);
                break;

            // ==========================================
            // GROUP 5: FACES & EMOTES (39-48)
            // ==========================================
            case 38: // Smiley Face
                g.setStroke(new BasicStroke(3));
                g.drawOval(cx - 25, cy - 25, 50, 50);
                g.fillOval(cx - 13, cy - 10, 5, 8);
                g.fillOval(cx + 8, cy - 10, 5, 8);
                g.drawArc(cx - 15, cy - 5, 30, 20, 200, 140);
                break;
            case 39: // Sad Face
                g.setStroke(new BasicStroke(3));
                g.drawOval(cx - 25, cy - 25, 50, 50);
                g.fillOval(cx - 13, cy - 10, 5, 5);
                g.fillOval(cx + 8, cy - 10, 5, 5);
                g.drawArc(cx - 15, cy + 5, 30, 15, 0, 180);
                break;
            case 40: { // Infinity
                g.setStroke(new BasicStroke(3));
                int infN = 100;
                int[] infX = new int[infN];
                int[] infY = new int[infN];
                for (int i = 0; i < infN; i++) {
                    double t = 2 * Math.PI * i / infN;
                    double denom = 1 + Math.sin(t) * Math.sin(t);
                    infX[i] = (int) (cx + 28 * Math.cos(t) / denom);
                    infY[i] = (int) (cy + 22 * Math.sin(t) * Math.cos(t) / denom);
                }
                g.drawPolygon(infX, infY, infN);
                break;
            }
            case 41: // Empty symbol
                g.setStroke(new BasicStroke(3));
                g.drawOval(cx - 16, cy - 16, 32, 32);
                g.drawLine(cx - 14, cy + 14, cx + 14, cy - 14);
                break;
            case 42: { // Cyclone
                g.setStroke(new BasicStroke(3));
                double maxT = 5 * Math.PI;
                int spiralN = 200;
                int[] spX = new int[spiralN];
                int[] spY = new int[spiralN];
                for (int i = 0; i < spiralN; i++) {
                    double t = maxT * i / (spiralN - 1);
                    double rr = 1 + 24 * t / maxT;
                    spX[i] = (int) (cx + rr * Math.cos(t));
                    spY[i] = (int) (cy + rr * Math.sin(t));
                }
                g.drawPolyline(spX, spY, spiralN);
                break;
            }
            case 43: // Anchor
                g.setStroke(new BasicStroke(4));
                g.drawLine(cx, cy - 20, cx, cy + 15);
                g.drawLine(cx - 10, cy - 15, cx + 10, cy - 15);
                g.drawArc(cx - 15, cy, 30, 20, 180, 180);
                break;
            case 44: // Crown
                g.fillPolygon(new int[]{cx - 20, cx - 10, cx, cx + 10, cx + 20, cx + 15, cx - 15},
                        new int[]{cy, cy + 10, cy - 5, cy + 10, cy, cy + 20, cy + 20}, 7);
                break;
            case 45: // Padlock
                g.fillRect(cx - 15, cy, 30, 20);
                g.setStroke(new BasicStroke(4));
                g.drawArc(cx - 10, cy - 15, 20, 25, 0, 180);
                break;
            case 46: // Compass
                g.setStroke(new BasicStroke(2));
                g.drawOval(cx - 20, cy - 20, 40, 40);
                g.fillPolygon(new int[]{cx, cx + 5, cx, cx - 5},
                        new int[]{cy - 18, cy, cy + 18, cy}, 4);
                break;

            // ==========================================
            // GROUP 6: TEXT & HUMOR (49-58)
            // ==========================================
            case 47: // Dollar
                g.setFont(new Font("Serif", Font.BOLD, 75));
                FontMetrics fmD = g.getFontMetrics();
                g.drawString("$", cx - fmD.stringWidth("$") / 2, cy + 25);
                break;
            case 48: // #1
                g.setFont(new Font("SansSerif", Font.BOLD, 65));
                FontMetrics fm1 = g.getFontMetrics();
                g.drawString("#1", cx - fm1.stringWidth("#1") / 2, cy + 23);
                break;
            case 49: // "FLY"
                g.setFont(new Font("Arial", Font.BOLD, 60));
                FontMetrics fmF = g.getFontMetrics();
                g.drawString("FLY", cx - fmF.stringWidth("FLY") / 2, cy + 20);
                break;
            case 50: // "MFC"
                g.setFont(new Font("Arial", Font.BOLD, 60));
                FontMetrics fmMFC = g.getFontMetrics();
                g.drawString("MFC", cx - fmMFC.stringWidth("MFC") / 2, cy + 20);
                break;
            case 51: { // "VIP"
                g.setFont(new Font("Serif", Font.BOLD, 54));
                FontMetrics fmV = g.getFontMetrics();
                int vipW = fmV.stringWidth("VIP");
                int vipX = cx - vipW / 2;
                int vipBaseline = cy + 18;
                g.drawString("VIP", vipX, vipBaseline);
                g.fillRect(vipX, vipBaseline - fmV.getAscent() - 2, vipW, 3);
                g.fillRect(vipX, vipBaseline + fmV.getDescent(), vipW, 3);
                break;
            }
            case 52: // "OK"
                g.setFont(new Font("SansSerif", Font.BOLD, 70));
                FontMetrics fmO = g.getFontMetrics();
                g.drawString("OK", cx - fmO.stringWidth("OK") / 2, cy + 25);
                break;
            case 53: // "A->B"
                g.setFont(new Font("Monospaced", Font.BOLD, 45));
                g.drawString("A", cx - 55, cy + 15);
                g.drawString("B", cx + 30, cy + 15);
                g.setStroke(new BasicStroke(4));
                g.drawLine(cx - 20, cy, cx + 20, cy);
                g.drawLine(cx + 10, cy - 10, cx + 20, cy);
                g.drawLine(cx + 10, cy + 10, cx + 20, cy);
                break;
            case 54: // "low"
                g.setFont(new Font("SansSerif", Font.BOLD, 55));
                FontMetrics fmLow = g.getFontMetrics();
                g.drawString("low", cx - fmLow.stringWidth("low") / 2, cy + 18);
                break;
            case 55: { // "UP"
                g.setFont(new Font("Arial Black", Font.BOLD, 30));
                FontMetrics fmUp = g.getFontMetrics();
                int upW = fmUp.stringWidth("UP");
                int upX = cx - (upW + 15) / 2;
                g.drawString("UP", upX, cy + 10);
                g.setStroke(new BasicStroke(4));
                int arrowX = upX + upW + 8;
                g.drawLine(arrowX, cy + 5, arrowX, cy - 15);
                g.drawLine(arrowX - 5, cy - 10, arrowX, cy - 15);
                g.drawLine(arrowX + 5, cy - 10, arrowX, cy - 15);
                break;
            }
        }
    }

    public static int getTemplateCount() {
        return PROCEDURAL_COUNT;
    }

    public static byte[] generateRandomLogo() throws IOException {
        Color color1 = new Color(random.nextInt(80), random.nextInt(80), random.nextInt(80));
        byte accentValue = (byte) (1 + random.nextInt(7));
        Color color2 = new Color(getColorValue(isAccentColor(accentValue, (byte) 0x4)),
                getColorValue(isAccentColor(accentValue, (byte) 0x2)),
                getColorValue(isAccentColor(accentValue, (byte) 0x1)));

        return generateLogo(random.nextInt(PROCEDURAL_COUNT), color1.getRGB(), color2.getRGB());
    }

    private static boolean isAccentColor(byte accentValue, byte mask) {
        return (accentValue & mask) == mask;
    }

    private static int getColorValue(boolean isAccent) {
        if (isAccent) {
            return 180 + random.nextInt(76);
        } else {
            return 40 + random.nextInt(76);
        }
    }
}
