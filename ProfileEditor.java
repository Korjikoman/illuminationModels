import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.List;

/* Панель для создания и изменения образующей кривой. */
public final class ProfileEditor extends JPanel {
    private static final int HORIZONTAL_MARGIN = 35;
    private static final int VERTICAL_MARGIN = 20;
    private static final int POINT_RADIUS = 5;
    private static final int SELECTION_DISTANCE = 14;

    private static final double MIN_RADIUS = 0.03;
    private static final double MAX_RADIUS = 1.00;
    private static final double MIN_HEIGHT = -1.10;
    private static final double MAX_HEIGHT = 1.10;

    private final List<ProfilePoint> profile = new ArrayList<>();
    private int draggedPointIndex = -1;

    public ProfileEditor(List<ProfilePoint> initialProfile) {
        profile.addAll(initialProfile);

        setPreferredSize(new Dimension(480, 400));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createLineBorder(new Color(210, 210, 210)));
        installMouseControls();
    }

    public List<ProfilePoint> getProfile() {
        return new ArrayList<>(profile);
    }

    public void clearProfile() {
        profile.clear();
        repaint();
    }

    private void installMouseControls() {
        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                int nearestPoint = findNearestPoint(event.getX(), event.getY());

                if (SwingUtilities.isRightMouseButton(event)) {
                    removePoint(nearestPoint);
                } else if (SwingUtilities.isLeftMouseButton(event)) {
                    selectOrAddPoint(nearestPoint, event.getX(), event.getY());
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                draggedPointIndex = -1;
            }
        };

        addMouseListener(mouseHandler);
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent event) {
                if (draggedPointIndex >= 0) {
                    profile.set(
                            draggedPointIndex,
                            convertScreenToProfile(event.getX(), event.getY())
                    );
                    repaint();
                }
            }
        });
    }

    private void removePoint(int pointIndex) {
        if (pointIndex >= 0) {
            profile.remove(pointIndex);
            repaint();
        }
    }

    private void selectOrAddPoint(int pointIndex, int mouseX, int mouseY) {
        if (pointIndex >= 0) {
            draggedPointIndex = pointIndex;
        } else {
            profile.add(convertScreenToProfile(mouseX, mouseY));
            draggedPointIndex = profile.size() - 1;
            repaint();
        }
    }

    private int findNearestPoint(int mouseX, int mouseY) {
        int nearestIndex = -1;
        double bestSquaredDistance = SELECTION_DISTANCE * SELECTION_DISTANCE;

        for (int index = 0; index < profile.size(); index++) {
            ProfilePoint point = profile.get(index);
            double deltaX = convertRadiusToScreen(point.radius) - mouseX;
            double deltaY = convertHeightToScreen(point.height) - mouseY;
            double squaredDistance = deltaX * deltaX + deltaY * deltaY;

            if (squaredDistance < bestSquaredDistance) {
                bestSquaredDistance = squaredDistance;
                nearestIndex = index;
            }
        }
        return nearestIndex;
    }

    private ProfilePoint convertScreenToProfile(int screenX, int screenY) {
        double radius = (screenX - HORIZONTAL_MARGIN)
                / (double) (getWidth() - 2 * HORIZONTAL_MARGIN);
        radius = clamp(radius, MIN_RADIUS, MAX_RADIUS);

        double verticalPart = (screenY - VERTICAL_MARGIN)
                / (double) (getHeight() - 2 * VERTICAL_MARGIN);
        double height = MAX_HEIGHT - verticalPart * (MAX_HEIGHT - MIN_HEIGHT);
        height = clamp(height, MIN_HEIGHT, MAX_HEIGHT);

        return new ProfilePoint(radius, height);
    }

    private int convertRadiusToScreen(double radius) {
        return (int) Math.round(
                HORIZONTAL_MARGIN + radius * (getWidth() - 2.0 * HORIZONTAL_MARGIN)
        );
    }

    private int convertHeightToScreen(double height) {
        double verticalPart = (MAX_HEIGHT - height) / (MAX_HEIGHT - MIN_HEIGHT);
        return (int) Math.round(
                VERTICAL_MARGIN + verticalPart * (getHeight() - 2.0 * VERTICAL_MARGIN)
        );
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        drawAxes(graphics);
        drawProfileLines(graphics);
        drawProfilePoints(graphics);
    }

    private void drawAxes(Graphics graphics) {
        graphics.setColor(new Color(220, 220, 220));
        graphics.drawLine(
                HORIZONTAL_MARGIN,
                VERTICAL_MARGIN,
                HORIZONTAL_MARGIN,
                getHeight() - VERTICAL_MARGIN
        );
        graphics.drawLine(
                HORIZONTAL_MARGIN,
                getHeight() / 2,
                getWidth() - HORIZONTAL_MARGIN,
                getHeight() / 2
        );

        graphics.setColor(Color.DARK_GRAY);
        graphics.drawString("Высота", 4, 14);
        graphics.drawString("Радиус", getWidth() - 65, getHeight() - 6);
    }

    private void drawProfileLines(Graphics graphics) {
        graphics.setColor(new Color(70, 100, 150));

        for (int index = 1; index < profile.size(); index++) {
            ProfilePoint previous = profile.get(index - 1);
            ProfilePoint current = profile.get(index);

            graphics.drawLine(
                    convertRadiusToScreen(previous.radius),
                    convertHeightToScreen(previous.height),
                    convertRadiusToScreen(current.radius),
                    convertHeightToScreen(current.height)
            );
        }
    }

    private void drawProfilePoints(Graphics graphics) {
        graphics.setColor(new Color(180, 55, 45));

        for (ProfilePoint point : profile) {
            int x = convertRadiusToScreen(point.radius);
            int y = convertHeightToScreen(point.height);

            graphics.fillOval(
                    x - POINT_RADIUS,
                    y - POINT_RADIUS,
                    2 * POINT_RADIUS,
                    2 * POINT_RADIUS
            );
        }
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
