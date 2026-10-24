/** Простой неизменяемый трёхмерный вектор. */
public final class Vector3 {
    public static final Vector3 ZERO = new Vector3(0.0, 0.0, 0.0);

    public final double x;
    public final double y;
    public final double z;

    public Vector3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vector3 add(Vector3 other) {
        return new Vector3(x + other.x, y + other.y, z + other.z);
    }

    public Vector3 subtract(Vector3 other) {
        return new Vector3(x - other.x, y - other.y, z - other.z);
    }

    public Vector3 multiply(double number) {
        return new Vector3(x * number, y * number, z * number);
    }

    public double dot(Vector3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public Vector3 cross(Vector3 other) {
        return new Vector3(
                y * other.z - z * other.y,
                z * other.x - x * other.z,
                x * other.y - y * other.x
        );
    }

    public double length() {
        return Math.sqrt(dot(this));
    }

    public Vector3 normalized() {
        double length = length();
        if (length < 1e-12) {
            return ZERO;
        }
        return multiply(1.0 / length);
    }

    /** Линейная интерполяция*/
    public static Vector3 lerp(Vector3 start, Vector3 end, double amount) {
        return start.multiply(1.0 - amount).add(end.multiply(amount));
    }

    /** Поворот точки или направления вокруг вертикальной оси Y. */
    public Vector3 rotateAroundY(double angle) {
        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);

        return new Vector3(
                cosine * x + sine * z,
                y,
                -sine * x + cosine * z
        );
    }
}
