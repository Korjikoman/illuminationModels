import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;

/**
 * Главное окно лабораторной работы.
 *
 * Остальной код разделён по назначению:
 * SurfaceModel — состояние сцены;
 * Mesh — построение треугольной сетки;
 * RendererPanel — проекция, освещение и закраска;
 * ProfileEditor — редактирование образующей кривой;
 * Vector3 — векторная математика.
 */
public final class RevolutionStudio extends JFrame {
    private static final int WINDOW_WIDTH = 950;
    private static final int WINDOW_HEIGHT = 700;
    private static final int CAMERA_FRAME_DELAY_MS = 16;
    private static final double CAMERA_CYCLE_MS = 5_000.0;
    private static final double OBJECT_CYCLE_MS = 4_000.0;

    private final SurfaceModel model = new SurfaceModel();
    private final RendererPanel renderer = new RendererPanel(model);
    private final JLabel statusLabel = new JLabel(
            "ЛКМ — вращение вокруг Y; движение объекта выполняется вдоль Z."
    );
    private final JButton cameraButton = new JButton("Запустить камеру");
    private final JButton objectButton = new JButton("Двигать объект по Z");

    private Timer cameraTimer;
    private Timer objectTimer;
    private long cameraAnimationStartTime;
    private long objectAnimationStartTime;

    public RevolutionStudio() {
        super("Поверхность вращения — Гуро и Фонг");

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        add(createControlPanel(), BorderLayout.NORTH);
        add(renderer, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        setPreferredSize(new Dimension(WINDOW_WIDTH, WINDOW_HEIGHT));
        pack();
        setLocationRelativeTo(null);

        // Движение объекта видно сразу после запуска программы.
        startObjectAnimation();
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 1));
        JPanel sceneSettings = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JPanel movementSettings = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton profileButton = new JButton("Профиль");
        profileButton.addActionListener(event -> openProfileEditor());

        JComboBox<ShadingMode> shadingBox = new JComboBox<>(ShadingMode.values());
        shadingBox.setSelectedItem(model.getShadingMode());
        shadingBox.addActionListener(event -> {
            model.setShadingMode((ShadingMode) shadingBox.getSelectedItem());
            renderer.repaint();
        });

        JSlider segmentSlider = new JSlider(8, 96, model.getRadialSegments());
        segmentSlider.setPreferredSize(new Dimension(130, 28));
        segmentSlider.setToolTipText("Количество частей окружности");
        segmentSlider.addChangeListener(event -> {
            model.setRadialSegments(segmentSlider.getValue());
            renderer.repaint();
        });

        cameraButton.addActionListener(event -> toggleCameraAnimation());
        objectButton.addActionListener(event -> toggleObjectAnimation());

        JButton resetButton = new JButton("Сбросить движение");
        resetButton.addActionListener(event -> {
            stopCameraAnimation();
            stopObjectAnimation();
            model.setCameraTime(0.0);
            model.setObjectMotionTime(0.0);
            renderer.repaint();
        });

        sceneSettings.add(profileButton);
        sceneSettings.add(new JLabel("Закраска:"));
        sceneSettings.add(shadingBox);
        sceneSettings.add(new JLabel("Сегментов:"));
        sceneSettings.add(segmentSlider);

        movementSettings.add(new JLabel("Движение:"));
        movementSettings.add(objectButton);
        movementSettings.add(cameraButton);
        movementSettings.add(resetButton);

        panel.add(sceneSettings);
        panel.add(movementSettings);

        return panel;
    }

    /** Запускает или останавливает движение камеры между двумя точками. */
    private void toggleCameraAnimation() {
        if (cameraTimer == null) {
            startCameraAnimation();
        } else {
            stopCameraAnimation();
        }
    }

    private void startCameraAnimation() {
        stopObjectAnimation();
        model.setObjectMotionTime(0.0);

        cameraAnimationStartTime = System.currentTimeMillis();
        cameraTimer = new Timer(CAMERA_FRAME_DELAY_MS, event -> {
            long elapsedTime = System.currentTimeMillis() - cameraAnimationStartTime;
            model.setCameraTime(elapsedTime / CAMERA_CYCLE_MS);
            renderer.repaint();
        });
        cameraTimer.start();
        cameraButton.setText("Остановить камеру");
    }

    private void stopCameraAnimation() {
        if (cameraTimer != null) {
            cameraTimer.stop();
            cameraTimer = null;
        }
        cameraButton.setText("Запустить камеру");
    }

    /** Запускает движение объекта по оси Z между двумя точками. */
    private void toggleObjectAnimation() {
        if (objectTimer == null) {
            startObjectAnimation();
        } else {
            stopObjectAnimation();
        }
    }

    private void startObjectAnimation() {
        stopCameraAnimation();
        model.setCameraTime(0.0);

        objectAnimationStartTime = System.currentTimeMillis();
        objectTimer = new Timer(CAMERA_FRAME_DELAY_MS, event -> {
            long elapsedTime = System.currentTimeMillis() - objectAnimationStartTime;
            model.setObjectMotionTime(elapsedTime / OBJECT_CYCLE_MS);
            renderer.repaint();
        });
        objectTimer.start();
        objectButton.setText("Остановить объект");
    }

    private void stopObjectAnimation() {
        if (objectTimer != null) {
            objectTimer.stop();
            objectTimer = null;
        }
        objectButton.setText("Двигать объект по Z");
    }

    private void openProfileEditor() {
        ProfileEditor editor = new ProfileEditor(model.getProfile());

        JButton clearButton = new JButton("Начать новый профиль");
        clearButton.addActionListener(event -> editor.clearProfile());

        JPanel dialog = new JPanel(new BorderLayout(0, 6));
        dialog.add(clearButton, BorderLayout.NORTH);
        dialog.add(editor, BorderLayout.CENTER);

        int answer = JOptionPane.showConfirmDialog(
                this,
                dialog,
                "Профиль: ЛКМ — добавить/переместить, ПКМ — удалить",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (answer == JOptionPane.OK_OPTION) {
            applyNewProfile(editor);
        }
    }

    private void applyNewProfile(ProfileEditor editor) {
        if (editor.getProfile().size() < 2) {
            JOptionPane.showMessageDialog(
                    this,
                    "Для построения поверхности нужны хотя бы две точки.",
                    "Недостаточно точек",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        model.setProfile(editor.getProfile());
        renderer.repaint();

        statusLabel.setText(
                "Профиль: " + model.getProfile().size()
                        + " точек; сетка: "
                        + model.getMesh().getTriangles().size()
                        + " треугольников."
        );
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new RevolutionStudio().setVisible(true));
    }
}
