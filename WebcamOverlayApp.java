import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.*;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * WebcamOverlayApp
 * 
 * Dieses Programm zeigt den Webcam-Stream und überlagert ihn mit einem Bild. Oben im Fenster
 * befinden sich Steuerelemente für:
 * - Auswahl der Webcam
 * - Auswahl des Überlagerungsbilds (Bilder im Verzeichnis images/)
 * - Einstellung der Deckkraft
 * - Zoom (Skalierung) über Zoom+ und Zoom- Buttons
 * - Rotation über Rot+ und Rot- Buttons
 * - Auswahl des Überlagerungsmodus (Normal, Multiply, Add, Screen)
 * 
 * Voraussetzung: Die Webcam Capture Library (https://github.com/sarxos/webcam-capture) muss eingebunden sein.
 */
public class WebcamOverlayApp extends JFrame {

    // Enum für Überlagerungsmodi
    public enum CompositeMode {
        NORMAL, MULTIPLY, ADD, SCREEN
    }

    // GUI-Komponenten und Einstellungen
    private Webcam webcam;
    private VideoPanel videoPanel;
    private JComboBox<Webcam> webcamCombo;
    private JComboBox<String> imageCombo;
    private JSlider opacitySlider;
    private JButton zoomInBtn, zoomOutBtn;
    private JButton rotatePlusBtn, rotateMinusBtn;
    private JComboBox<String> modeCombo;

    // Parameter für das Overlay
    private BufferedImage overlayImage = null;
    private float overlayOpacity = 0.5f; // 0.0 - 1.0
    private float scale = 1.0f;
    private float rotation = 0.0f; // in Grad
    private CompositeMode compositeMode = CompositeMode.NORMAL;

    public WebcamOverlayApp() {
        super("Webcam-Overlay Anwendung");

        // Erzeuge GUI-Komponenten
        initComponents();

        // Frame konfigurieren
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);

        // Starte regelmäßiges Neuzeichnen (ca. 30 FPS)
        Timer timer = new Timer(33, e -> videoPanel.repaint());
        timer.start();
    }

    private void initComponents() {
        // Obere Bedienleiste als Panel
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        // 1. Webcam-Auswahl
        webcamCombo = new JComboBox<>();
        for (Webcam cam : Webcam.getWebcams()) {
            webcamCombo.addItem(cam);
        }
        webcamCombo.addActionListener(e -> {
            Webcam selected = (Webcam) webcamCombo.getSelectedItem();
            if (selected != null && selected != webcam) {
                if (webcam != null) {
                    webcam.close();
                }
                webcam = selected;
                webcam.setViewSize(WebcamResolution.VGA.getSize());
                webcam.open();
            }
        });
        controlPanel.add(new JLabel("Webcam:"));
        controlPanel.add(webcamCombo);

        // 2. Bild-Auswahl (Verzeichnis images/)
        imageCombo = new JComboBox<>();
        File imgDir = new File("images");
        if (imgDir.exists() && imgDir.isDirectory()) {
            for (File file : imgDir.listFiles((dir, name) -> {
                String lname = name.toLowerCase();
                return lname.endsWith(".jpg") || lname.endsWith(".png") || lname.endsWith(".gif");
            })) {
                imageCombo.addItem(file.getName());
            }
        }
        imageCombo.addActionListener(e -> {
            String fileName = (String) imageCombo.getSelectedItem();
            if (fileName != null) {
                try {
                    overlayImage = ImageIO.read(new File("images" + File.separator + fileName));
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });
        controlPanel.add(new JLabel("Bild:"));
        controlPanel.add(imageCombo);

        // 3. Opacity Slider (Deckkraft)
        opacitySlider = new JSlider(0, 100, 50);
        opacitySlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                overlayOpacity = opacitySlider.getValue() / 100f;
            }
        });
        controlPanel.add(new JLabel("Deckkraft:"));
        controlPanel.add(opacitySlider);

        // 4. Zoom Buttons
        zoomInBtn = new JButton("Zoom+");
        zoomOutBtn = new JButton("Zoom-");
        zoomInBtn.addActionListener(e -> {
            scale += 0.1f;
        });
        zoomOutBtn.addActionListener(e -> {
            scale = Math.max(0.1f, scale - 0.1f);
        });
        controlPanel.add(zoomInBtn);
        controlPanel.add(zoomOutBtn);

        // 5. Rotation Buttons
        rotatePlusBtn = new JButton("Rot+");
        rotateMinusBtn = new JButton("Rot-");
        rotatePlusBtn.addActionListener(e -> {
            rotation += 5f;
        });
        rotateMinusBtn.addActionListener(e -> {
            rotation -= 5f;
        });
        controlPanel.add(rotatePlusBtn);
        controlPanel.add(rotateMinusBtn);

        // 6. Überlagerungsmodus Auswahl
        modeCombo = new JComboBox<>(new String[] {"Normal", "Multiply", "Add", "Screen"});
        modeCombo.addActionListener(e -> {
            String sel = (String) modeCombo.getSelectedItem();
            if (sel != null) {
                switch (sel) {
                    case "Normal":
                        compositeMode = CompositeMode.NORMAL;
                        break;
                    case "Multiply":
                        compositeMode = CompositeMode.MULTIPLY;
                        break;
                    case "Add":
                        compositeMode = CompositeMode.ADD;
                        break;
                    case "Screen":
                        compositeMode = CompositeMode.SCREEN;
                        break;
                    default:
                        compositeMode = CompositeMode.NORMAL;
                }
            }
        });
        controlPanel.add(new JLabel("Modus:"));
        controlPanel.add(modeCombo);

        // Füge Bedienleiste oben hinzu
        getContentPane().add(controlPanel, BorderLayout.NORTH);

        // Erzeuge Panel für Webcam und Überlagerung
        videoPanel = new VideoPanel();
        videoPanel.setPreferredSize(new Dimension(640, 480));
        getContentPane().add(videoPanel, BorderLayout.CENTER);

        // Initialisiere Webcam: wähle das erste Gerät aus
        if (webcamCombo.getItemCount() > 0) {
            webcam = (Webcam) webcamCombo.getItemAt(0);
            webcam.setViewSize(WebcamResolution.VGA.getSize());
            webcam.open();
        }
    }

    /**
     * VideoPanel:
     * Dieses Panel zeichnet den Webcam-Stream und überlagert das ggf. transformierte Overlay-Bild.
     */
    private class VideoPanel extends JPanel {

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            if (webcam == null || !webcam.isOpen()) {
                return;
            }

            // Hole aktuelles Bild der Webcam
            BufferedImage camImage = webcam.getImage();
            if (camImage == null) {
                return;
            }

            // Skaliere das Webcam-Bild auf die Fenstergröße
            Graphics2D g2d = (Graphics2D) g;
            g2d.drawImage(camImage, 0, 0, getWidth(), getHeight(), null);

            // Falls ein Overlay-Bild geladen ist, transformiere es (Scale, Rotation)
            if (overlayImage != null) {
                BufferedImage transOverlay = getTransformedOverlay();
                if (transOverlay != null) {
                    // Positioniere das Overlay zentriert im Fenster
                    int x = (getWidth() - transOverlay.getWidth()) / 2;
                    int y = (getHeight() - transOverlay.getHeight()) / 2;

                    if (compositeMode == CompositeMode.NORMAL) {
                        // Normaler Modus: Java-Standard AlphaComposite verwenden
                        Composite original = g2d.getComposite();
                        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, overlayOpacity));
                        g2d.drawImage(transOverlay, x, y, null);
                        g2d.setComposite(original);
                    } else {
                        // Andere Modi: Pixelweise Berechnung
                        BufferedImage combined = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
                        Graphics2D gc = combined.createGraphics();
                        gc.drawImage(camImage, 0, 0, getWidth(), getHeight(), null);
                        // Überlagere das transformierte Bild
                        for (int i = 0; i < transOverlay.getWidth(); i++) {
                            for (int j = 0; j < transOverlay.getHeight(); j++) {
                                int overlayPixel = transOverlay.getRGB(i, j);
                                int posX = x + i;
                                int posY = y + j;
                                if (posX >= 0 && posX < combined.getWidth() && posY >= 0 && posY < combined.getHeight()) {
                                    int basePixel = combined.getRGB(posX, posY);
                                    int blended = blendPixel(basePixel, overlayPixel, compositeMode, overlayOpacity);
                                    combined.setRGB(posX, posY, blended);
                                }
                            }
                        }
                        gc.dispose();
                        g2d.drawImage(combined, 0, 0, null);
                    }
                }
            }
        }

        /**
         * Wendet die Transformationen (Skalierung, Rotation) auf das Overlay-Bild an.
         */
        private BufferedImage getTransformedOverlay() {
            int w = overlayImage.getWidth();
            int h = overlayImage.getHeight();
            // Zielbildgröße nach Skalierung (für Rotation wird das Bild ggf. größer)
            int newW = (int) (w * scale);
            int newH = (int) (h * scale);
            BufferedImage transformed = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = transformed.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            // Transformation: Zunächst Skalierung, dann Rotation um das Bildzentrum
            AffineTransform at = new AffineTransform();
            at.translate(newW / 2.0, newH / 2.0);
            at.rotate(Math.toRadians(rotation));
            at.translate(-w * scale / 2.0, -h * scale / 2.0);
            at.scale(scale, scale);
            g2.drawImage(overlayImage, at, null);
            g2.dispose();
            return transformed;
        }
    }

    /**
     * Mischfunktion für einzelne Pixel. 
     * Extrahiert die ARGB-Komponenten, wendet den gewählten Überlagerungsmodus an
     * und mischt basierend auf der effektiven Overlay-Deckkraft.
     */
    private int blendPixel(int base, int overlay, CompositeMode mode, float opacity) {
        int aBase = (base >> 24) & 0xff;
        int rBase = (base >> 16) & 0xff;
        int gBase = (base >> 8) & 0xff;
        int bBase = base & 0xff;

        int aOverlay = (overlay >> 24) & 0xff;
        int rOverlay = (overlay >> 16) & 0xff;
        int gOverlay = (overlay >> 8) & 0xff;
        int bOverlay = overlay & 0xff;

        // Effektive Overlay-Deckkraft (berücksichtigt auch den Alpha-Wert des Bildes)
        float alpha = (aOverlay / 255f) * opacity;

        int rFinal = blendChannel(rBase, rOverlay, mode, alpha);
        int gFinal = blendChannel(gBase, gOverlay, mode, alpha);
        int bFinal = blendChannel(bBase, bOverlay, mode, alpha);

        // Den Basis-Alpha beibehalten (alternativ könnte auch eine Neuberechnung erfolgen)
        int aFinal = aBase;
        return (aFinal << 24) | (rFinal << 16) | (gFinal << 8) | bFinal;
    }

    /**
     * Berechnet den gemischten Kanalwert abhängig vom gewählten CompositeMode.
     * Der Wert wird normalisiert und anschließend wieder in 0-255 skaliert.
     */
    private int blendChannel(int base, int overlay, CompositeMode mode, float alpha) {
        float b = base / 255f;
        float o = overlay / 255f;
        float result = 0f;
        switch (mode) {
            case MULTIPLY:
                result = b * o;
                break;
            case ADD:
                result = Math.min(1f, b + o);
                break;
            case SCREEN:
                result = 1 - (1 - b) * (1 - o);
                break;
            default:
                // Im "Normal"-Fall (sollte hier nicht eintreten, da separat behandelt)
                result = o;
        }
        // Lineares Interpolieren: Jeweils (1 - alpha)*Basis + alpha * Ergebnis des Modus
        float finalVal = (1 - alpha) * b + alpha * result;
        return Math.min(255, Math.max(0, (int) (finalVal * 255)));
    }

    public static void main(String[] args) {
        // Im Event Dispatch Thread starten
        SwingUtilities.invokeLater(() -> {
            WebcamOverlayApp app = new WebcamOverlayApp();
            // Schließe die Webcam beim Schließen des Fensters
            app.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    if (app.webcam != null) {
                        app.webcam.close();
                    }
                }
            });
        });
    }
}
