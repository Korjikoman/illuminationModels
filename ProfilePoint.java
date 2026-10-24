/**
 * Одна точка образующей кривой.
 * radius — расстояние от оси вращения Y;
 * height — положение точки по высоте.
 */
public final class ProfilePoint {
    public final double radius;
    public final double height;

    public ProfilePoint(double radius, double height) {
        this.radius = radius;
        this.height = height;
    }
}
