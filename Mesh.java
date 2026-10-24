import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Mesh {
    private final List<Vertex> vertices = new ArrayList<>();
    private final List<Triangle> triangles = new ArrayList<>();

    public void rebuild(List<ProfilePoint> profile, int radialSegments) {
        vertices.clear();
        triangles.clear();

        createVertexRings(profile, radialSegments);
        connectRings(profile.size(), radialSegments);
        normalizeVertexNormals();
    }

    public List<Vertex> getVertices() {
        return Collections.unmodifiableList(vertices);
    }

    public List<Triangle> getTriangles() {
        return Collections.unmodifiableList(triangles);
    }

    private void createVertexRings(List<ProfilePoint> profile, int radialSegments) {
        for (ProfilePoint profilePoint : profile) {
            for (int segment = 0; segment < radialSegments; segment++) {
                double angle = 2.0 * Math.PI * segment / radialSegments;

                double x = profilePoint.radius * Math.cos(angle);
                double y = profilePoint.height;
                double z = profilePoint.radius * Math.sin(angle);

                vertices.add(new Vertex(new Vector3(x, y, z)));
            }
        }
    }

    /** Соединяет каждую пару соседних колец двумя треугольниками на сегмент. */
    private void connectRings(int ringCount, int radialSegments) {
        for (int ring = 0; ring < ringCount - 1; ring++) {
            for (int segment = 0; segment < radialSegments; segment++) {
                int nextSegment = (segment + 1) % radialSegments;

                int lowerLeft = vertexIndex(ring, segment, radialSegments);
                int lowerRight = vertexIndex(ring, nextSegment, radialSegments);
                int upperLeft = vertexIndex(ring + 1, segment, radialSegments);
                int upperRight = vertexIndex(ring + 1, nextSegment, radialSegments);

                addTriangle(lowerLeft, upperLeft, lowerRight);
                addTriangle(lowerRight, upperLeft, upperRight);
            }
        }
    }

    private int vertexIndex(int ring, int segment, int radialSegments) {
        return ring * radialSegments + segment;
    }

    /**
     * Вычисляет нормаль треугольника и добавляет её трём вершинам.
     * Усреднённые нормали вершин нужны для закраски Гуро и Фонга.
     */
    private void addTriangle(int firstIndex, int secondIndex, int thirdIndex) {
        Vertex first = vertices.get(firstIndex);
        Vertex second = vertices.get(secondIndex);
        Vertex third = vertices.get(thirdIndex);

        Vector3 firstEdge = second.position.subtract(first.position);
        Vector3 secondEdge = third.position.subtract(first.position);
        Vector3 faceNormal = firstEdge.cross(secondEdge).normalized();

        // Проверяем, что нормаль направлена наружу от оси вращения.
        Vector3 center = first.position
                .add(second.position)
                .add(third.position)
                .multiply(1.0 / 3.0);
        Vector3 outwardDirection = new Vector3(center.x, 0.0, center.z);

        if (faceNormal.dot(outwardDirection) < 0.0) {
            faceNormal = faceNormal.multiply(-1.0);
        }

        first.addToNormal(faceNormal);
        second.addToNormal(faceNormal);
        third.addToNormal(faceNormal);

        triangles.add(new Triangle(firstIndex, secondIndex, thirdIndex, faceNormal));
    }

    private void normalizeVertexNormals() {
        for (Vertex vertex : vertices) {
            vertex.normalizeNormal();
        }
    }

    /* Вершина хранит положение и усреднённую нормаль. */
    public static final class Vertex {
        public final Vector3 position;
        public Vector3 normal = Vector3.ZERO;

        Vertex(Vector3 position) {
            this.position = position;
        }

        void addToNormal(Vector3 faceNormal) {
            normal = normal.add(faceNormal);
        }

        void normalizeNormal() {
            normal = normal.normalized();
        }
    }

    /** Треугольник хранит индексы трёх вершин и нормаль своей плоскости. */
    public static final class Triangle {
        public final int first;
        public final int second;
        public final int third;
        public final Vector3 normal;

        Triangle(int first, int second, int third, Vector3 normal) {
            this.first = first;
            this.second = second;
            this.third = third;
            this.normal = normal;
        }
    }
}
