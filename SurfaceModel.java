import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Хранит профиль, построенную сетку и изменяемые параметры сцены. */
public final class SurfaceModel {
    private List<ProfilePoint> profile = new ArrayList<>(Arrays.asList(
            new ProfilePoint(0.12, -1.00),
            new ProfilePoint(0.42, -0.92),
            new ProfilePoint(0.68, -0.62),
            new ProfilePoint(0.72, -0.20),
            new ProfilePoint(0.55, 0.08),
            new ProfilePoint(0.42, 0.35),
            new ProfilePoint(0.50, 0.68),
            new ProfilePoint(0.75, 0.90),
            new ProfilePoint(0.70, 1.00)
    ));

    private final Mesh mesh = new Mesh();
    private int radialSegments = 40;
    private ShadingMode shadingMode = ShadingMode.GOURAUD;
    private double cameraTime;
    private double objectMotionTime;
    private double objectRotation;
    private double zoom = 1.0;

    public SurfaceModel() {
        rebuildMesh();
    }

    public void rebuildMesh() {
        mesh.rebuild(profile, radialSegments);
    }

    public List<ProfilePoint> getProfile() {
        return new ArrayList<>(profile);
    }

    public void setProfile(List<ProfilePoint> profile) {
        this.profile = new ArrayList<>(profile);
        rebuildMesh();
    }

    public Mesh getMesh() {
        return mesh;
    }

    public int getRadialSegments() {
        return radialSegments;
    }

    public void setRadialSegments(int radialSegments) {
        this.radialSegments = radialSegments;
        rebuildMesh();
    }

    public ShadingMode getShadingMode() {
        return shadingMode;
    }

    public void setShadingMode(ShadingMode shadingMode) {
        this.shadingMode = shadingMode;
    }

    public double getCameraTime() {
        return cameraTime;
    }

    public void setCameraTime(double cameraTime) {
        this.cameraTime = cameraTime;
    }

    public double getObjectMotionTime() {
        return objectMotionTime;
    }

    public void setObjectMotionTime(double objectMotionTime) {
        this.objectMotionTime = objectMotionTime;
    }

    public double getObjectRotation() {
        return objectRotation;
    }

    public void rotateObject(double angle) {
        objectRotation += angle;
    }

    public double getZoom() {
        return zoom;
    }

    public void multiplyZoom(double factor) {
        zoom = clamp(zoom * factor, 0.45, 2.80);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
