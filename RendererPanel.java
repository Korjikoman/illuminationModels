import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import java.util.List;

public final class RendererPanel extends JPanel {
    private static final Color BACKGROUND_COLOR = new Color(25, 28, 34);
    private static final Color TEXT_COLOR = new Color(235, 235, 235);

    /* Параметры простейшей модели освещения. */
    private static final double AMBIENT_STRENGTH = 0.16;
    private static final double DIFFUSE_STRENGTH = 0.70;
    private static final double SPECULAR_STRENGTH = 0.48;
    private static final double SHININESS = 28.0;
    private static final double ATTENUATION = 0.035;
    private static final Vector3 LIGHT_POSITION = new Vector3(-3.0, 4.0, 4.0);

    /* Две точки, между которыми движется камера. */
    private static final Vector3 CAMERA_POINT_A = new Vector3(3.4, 2.0, 4.7);
    private static final Vector3 CAMERA_POINT_B = new Vector3(-3.4, 1.5, 4.7);
    private static final Vector3 SCENE_CENTER = Vector3.ZERO;
    private static final Vector3 WORLD_UP = new Vector3(0.0, 1.0, 0.0);

    /* Крайние точки движения объекта: Z = -1.2 и Z = 1.2. */
    private static final double OBJECT_Z_LIMIT = 2.20;

    private final SurfaceModel model;
    private double previousMouseX;

    public RendererPanel(SurfaceModel model) {
        this.model = model;
        setBackground(BACKGROUND_COLOR);
        installMouseControls();
    }

    private void installMouseControls() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                previousMouseX = event.getX();
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent event) {
                double horizontalShift = event.getX() - previousMouseX;
                model.rotateObject(horizontalShift * 0.012);
                previousMouseX = event.getX();
                repaint();
            }
        });

        addMouseWheelListener(event -> {
            double zoomFactor = Math.pow(1.12, -event.getPreciseWheelRotation());
            model.multiplyZoom(zoomFactor);
            repaint();
        });
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        Arrays.fill(pixels, BACKGROUND_COLOR.getRGB());

        double[] depthBuffer = new double[width * height];
        Arrays.fill(depthBuffer, Double.POSITIVE_INFINITY);

        Camera camera = createMovingCamera();
        ScreenPoint[] screenVertices = projectAllVertices(camera, width, height);

        for (Mesh.Triangle triangle : model.getMesh().getTriangles()) {
            drawTriangle(triangle, screenVertices, camera, pixels, depthBuffer, width, height);
        }

        graphics.drawImage(image, 0, 0, null);
        drawCoordinateAxes(graphics, width, height);
        drawInformation(graphics, height);
    }

    /** Камера плавно проходит путь A -> B -> A. */
    private Camera createMovingCamera() {
        double cyclePart = model.getCameraTime() - Math.floor(model.getCameraTime());
        double interpolation = cyclePart < 0.5
                ? cyclePart * 2.0
                : (1.0 - cyclePart) * 2.0;

        Vector3 position = Vector3.lerp(CAMERA_POINT_A, CAMERA_POINT_B, interpolation);
        Vector3 forward = SCENE_CENTER.subtract(position).normalized();
        Vector3 right = forward.cross(WORLD_UP).normalized();
        Vector3 up = right.cross(forward).normalized();

        return new Camera(position, right, up, forward);
    }

    private ScreenPoint[] projectAllVertices(Camera camera, int width, int height) {
        List<Mesh.Vertex> vertices = model.getMesh().getVertices();
        ScreenPoint[] result = new ScreenPoint[vertices.size()];
        Vector3 objectTranslation = calculateObjectTranslation();

        for (int index = 0; index < vertices.size(); index++) {
            Vector3 rotatedPosition = vertices.get(index).position
                    .rotateAroundY(model.getObjectRotation())
                    .add(objectTranslation);
            result[index] = project(rotatedPosition, camera, width, height);
        }
        return result;
    }

    /**
     * Возвращает смещение объекта вдоль Z.
     * При времени 0 смещение равно нулю, поэтому центр объекта находится
     * в начале координат. Затем объект плавно ходит между двумя крайними точками.
     */
    private Vector3 calculateObjectTranslation() {
        double angle = 2.0 * Math.PI * model.getObjectMotionTime();
        double z = OBJECT_Z_LIMIT * Math.sin(angle);
        return new Vector3(0.0, 0.0, z);
    }

    /** Перспективная проекция трёхмерной точки на плоскость экрана. */
    private ScreenPoint project(Vector3 worldPoint, Camera camera, int width, int height) {
        Vector3 pointFromCamera = worldPoint.subtract(camera.position);

        double cameraX = pointFromCamera.dot(camera.right);
        double cameraY = pointFromCamera.dot(camera.up);
        double cameraZ = pointFromCamera.dot(camera.forward);

        double focalLength = Math.min(width, height) * 0.88 * model.getZoom();
        double safeDepth = Math.max(0.1, cameraZ);

        double screenX = width / 2.0 + focalLength * cameraX / safeDepth;
        double screenY = height / 2.0 - focalLength * cameraY / safeDepth;
        return new ScreenPoint(screenX, screenY, cameraZ);
    }

    private void drawTriangle(
            Mesh.Triangle triangle,
            ScreenPoint[] screenVertices,
            Camera camera,
            int[] pixels,
            double[] depthBuffer,
            int width,
            int height
    ) {
        TriangleData data = prepareTriangle(triangle, screenVertices, camera);

        // Если хотя бы одна вершина находится за камерой, грань пропускается.
        if (data.hasVertexBehindCamera()) {
            return;
        }

        double area = edgeFunction(data.screenA, data.screenB, data.screenC.x, data.screenC.y);
        if (Math.abs(area) < 1e-8) {
            return;
        }

        int minX = Math.max(0, floorMinimum(data.screenA.x, data.screenB.x, data.screenC.x));
        int maxX = Math.min(width - 1, ceilMaximum(data.screenA.x, data.screenB.x, data.screenC.x));
        int minY = Math.max(0, floorMinimum(data.screenA.y, data.screenB.y, data.screenC.y));
        int maxY = Math.min(height - 1, ceilMaximum(data.screenA.y, data.screenB.y, data.screenC.y));

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                drawPixel(x, y, data, area, camera.position, pixels, depthBuffer, width);
            }
        }
    }

    /** Собирает в одном объекте всё, что требуется для закраски треугольника. */
    private TriangleData prepareTriangle(
            Mesh.Triangle triangle,
            ScreenPoint[] screenVertices,
            Camera camera
    ) {
        List<Mesh.Vertex> vertices = model.getMesh().getVertices();
        Mesh.Vertex vertexA = vertices.get(triangle.first);
        Mesh.Vertex vertexB = vertices.get(triangle.second);
        Mesh.Vertex vertexC = vertices.get(triangle.third);

        double rotation = model.getObjectRotation();
        Vector3 translation = calculateObjectTranslation();
        Vector3 positionA = vertexA.position.rotateAroundY(rotation).add(translation);
        Vector3 positionB = vertexB.position.rotateAroundY(rotation).add(translation);
        Vector3 positionC = vertexC.position.rotateAroundY(rotation).add(translation);
        Vector3 normalA = vertexA.normal.rotateAroundY(rotation);
        Vector3 normalB = vertexB.normal.rotateAroundY(rotation);
        Vector3 normalC = vertexC.normal.rotateAroundY(rotation);

        // Метод Гуро использует освещённость, заранее найденную в вершинах.
        double lightA = calculateLight(positionA, normalA, camera.position);
        double lightB = calculateLight(positionB, normalB, camera.position);
        double lightC = calculateLight(positionC, normalC, camera.position);

        // Плоская закраска использует один цвет для всей грани.
        Vector3 center = positionA.add(positionB).add(positionC).multiply(1.0 / 3.0);
        Vector3 faceNormal = triangle.normal.rotateAroundY(rotation);
        double flatLight = calculateLight(center, faceNormal, camera.position);

        return new TriangleData(
                screenVertices[triangle.first],
                screenVertices[triangle.second],
                screenVertices[triangle.third],
                positionA, positionB, positionC,
                normalA, normalB, normalC,
                lightA, lightB, lightC,
                flatLight
        );
    }

    private void drawPixel(
            int x,
            int y,
            TriangleData triangle,
            double triangleArea,
            Vector3 cameraPosition,
            int[] pixels,
            double[] depthBuffer,
            int imageWidth
    ) {
        BarycentricWeights weights = calculateWeights(x + 0.5, y + 0.5, triangle, triangleArea);
        if (!weights.isInsideTriangle()) {
            return;
        }

        double depth = weights.a * triangle.screenA.depth
                + weights.b * triangle.screenB.depth
                + weights.c * triangle.screenC.depth;
        int pixelIndex = y * imageWidth + x;

        // Z-буфер оставляет видимой ближайшую к камере поверхность.
        if (depth >= depthBuffer[pixelIndex]) {
            return;
        }
        depthBuffer[pixelIndex] = depth;

        double intensity = calculatePixelIntensity(triangle, weights, cameraPosition);
        int gray = (int) Math.round(255.0 * clamp(intensity, 0.0, 1.0));
        pixels[pixelIndex] = grayColor(gray);
    }

    /** Барицентрические веса задают вклад каждой вершины в текущую точку. */
    private BarycentricWeights calculateWeights(
            double pixelX,
            double pixelY,
            TriangleData triangle,
            double triangleArea
    ) {
        double weightA = edgeFunction(
                triangle.screenB,
                triangle.screenC,
                pixelX,
                pixelY
        ) / triangleArea;
        double weightB = edgeFunction(
                triangle.screenC,
                triangle.screenA,
                pixelX,
                pixelY
        ) / triangleArea;
        double weightC = 1.0 - weightA - weightB;
        return new BarycentricWeights(weightA, weightB, weightC);
    }

    private double calculatePixelIntensity(
            TriangleData triangle,
            BarycentricWeights weights,
            Vector3 cameraPosition
    ) {
        switch (model.getShadingMode()) {
            case FLAT:
                return triangle.flatLight;

            case GOURAUD:
                // Гуро: интерполируем готовую освещённость трёх вершин.
                return weights.a * triangle.lightA
                        + weights.b * triangle.lightB
                        + weights.c * triangle.lightC;

            case PHONG:
                // Фонг: интерполируем нормаль и считаем свет отдельно для пикселя.
                Vector3 pixelNormal = interpolate(
                        triangle.normalA,
                        triangle.normalB,
                        triangle.normalC,
                        weights
                ).normalized();
                Vector3 pixelPosition = interpolate(
                        triangle.positionA,
                        triangle.positionB,
                        triangle.positionC,
                        weights
                );
                return calculateLight(pixelPosition, pixelNormal, cameraPosition);

            default:
                throw new IllegalStateException("Неизвестный метод закраски");
        }
    }

    private Vector3 interpolate(
            Vector3 first,
            Vector3 second,
            Vector3 third,
            BarycentricWeights weights
    ) {
        return first.multiply(weights.a)
                .add(second.multiply(weights.b))
                .add(third.multiply(weights.c));
    }

    // N = a * Na + b * Nb + y Nc

    /**
     * Простейшая модель освещения
     * I = фоновая + затухание * (диффузная + зеркальная составляющая).
     */
    private double calculateLight(Vector3 point, Vector3 normal, Vector3 cameraPosition) {
        Vector3 unitNormal = normal.normalized();
        Vector3 vectorToLight = LIGHT_POSITION.subtract(point);
        double distanceToLight = vectorToLight.length();
        Vector3 lightDirection = vectorToLight.normalized();
        Vector3 viewDirection = cameraPosition.subtract(point).normalized();

        // Диффузная составляющая
        double diffuse = Math.max(0.0, unitNormal.dot(lightDirection));

        // Зеркальная составляющая
        Vector3 reflectedLight = unitNormal
                .multiply(2.0 * unitNormal.dot(lightDirection))
                .subtract(lightDirection)
                .normalized();
        double specular = Math.pow(
                Math.max(0.0, reflectedLight.dot(viewDirection)),
                SHININESS
        );

        double distanceAttenuation = 1.0
                / (1.0 + ATTENUATION * distanceToLight * distanceToLight);

        return AMBIENT_STRENGTH
                + distanceAttenuation
                * (DIFFUSE_STRENGTH * diffuse + SPECULAR_STRENGTH * specular);
    }

    /**
     * Рисует неподвижные оси через весь экран.
     * Его координаты задаются в пикселях, поэтому камера, объект и масштаб
     * не влияют на положение осей.
     */
    private void drawCoordinateAxes(
            Graphics graphics,
            int width,
            int height
    ) {
        Graphics2D graphics2D = (Graphics2D) graphics.create();
        Stroke originalStroke = graphics2D.getStroke();
        int originX = width / 2;
        int originY = height / 2;

        // Экранная проекция оси Z наклонена к горизонтали на 18 градусов.
        double zAxisSlope = Math.tan(Math.toRadians(18.0));
        int positiveZEndX = 0;
        int positiveZEndY = originY + (int) Math.round(originX * zAxisSlope);
        int negativeZEndX = width - 1;
        int negativeZEndY = originY
                - (int) Math.round((negativeZEndX - originX) * zAxisSlope);

        // Отрицательные направления осей изображаются серым цветом.
        graphics2D.setStroke(new BasicStroke(1.2f));
        graphics2D.setColor(new Color(165, 165, 165, 150));
        graphics2D.drawLine(0, originY, originX, originY);       // -X
        graphics2D.drawLine(originX, originY, originX, height);  // -Y
        graphics2D.drawLine(
                originX, originY,
                negativeZEndX, negativeZEndY
        );                                                       // -Z

        // Положительные направления проходят от центра до краёв экрана.
        graphics2D.setStroke(new BasicStroke(1.8f));
        drawFixedAxis(
                graphics2D,
                originX, originY,
                width - 1, originY,
                new Color(235, 80, 80, 190),
                "X"
        );
        drawFixedAxis(
                graphics2D,
                originX, originY,
                originX, 0,
                new Color(90, 220, 110, 190),
                "Y"
        );
        drawFixedAxis(
                graphics2D,
                originX, originY,
                positiveZEndX, positiveZEndY,
                new Color(90, 150, 245, 190),
                "Z"
        );

        graphics2D.setStroke(originalStroke);
        graphics2D.dispose();
    }

    private void drawFixedAxis(
            Graphics2D graphics,
            int startX,
            int startY,
            int endX,
            int endY,
            Color color,
            String name
    ) {
        graphics.setColor(color);
        graphics.drawLine(startX, startY, endX, endY);
        graphics.fillOval(endX - 3, endY - 3, 6, 6);

        int labelX = Math.max(5, Math.min(endX + 5, getWidth() - 18));
        int labelY = Math.max(14, Math.min(endY - 5, getHeight() - 5));
        graphics.drawString(name, labelX, labelY);
    }

    private void drawInformation(Graphics graphics, int height) {
        double objectZ = calculateObjectTranslation().z;

        graphics.setColor(TEXT_COLOR);
        graphics.drawString(
                String.format(
                        "Градации серого • %s • положение объекта: Z = %.2f",
                        model.getShadingMode(),
                        objectZ
                ),
                14,
                22
        );
        graphics.drawString(
                "Перетащить ЛКМ — вращение объекта вокруг Y; колесо — масштаб",
                14,
                height - 14
        );
    }

    private static double edgeFunction(
            ScreenPoint first,
            ScreenPoint second,
            double pointX,
            double pointY
    ) {
        return (pointX - first.x) * (second.y - first.y)
                - (pointY - first.y) * (second.x - first.x);
    }

    private static int floorMinimum(double first, double second, double third) {
        return (int) Math.floor(Math.min(first, Math.min(second, third)));
    }

    private static int ceilMaximum(double first, double second, double third) {
        return (int) Math.ceil(Math.max(first, Math.max(second, third)));
    }

    private static int grayColor(int gray) {
        return (gray << 16) | (gray << 8) | gray;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class Camera {
        final Vector3 position;
        final Vector3 right;
        final Vector3 up;
        final Vector3 forward;

        Camera(Vector3 position, Vector3 right, Vector3 up, Vector3 forward) {
            this.position = position;
            this.right = right;
            this.up = up;
            this.forward = forward;
        }
    }

    private static final class ScreenPoint {
        final double x;
        final double y;
        final double depth;

        ScreenPoint(double x, double y, double depth) {
            this.x = x;
            this.y = y;
            this.depth = depth;
        }
    }

    private static final class BarycentricWeights {
        final double a;
        final double b;
        final double c;

        BarycentricWeights(double a, double b, double c) {
            this.a = a;
            this.b = b;
            this.c = c;
        }

        boolean isInsideTriangle() {
            return a >= -1e-7 && b >= -1e-7 && c >= -1e-7;
        }
    }

    private static final class TriangleData {
        final ScreenPoint screenA;
        final ScreenPoint screenB;
        final ScreenPoint screenC;
        final Vector3 positionA;
        final Vector3 positionB;
        final Vector3 positionC;
        final Vector3 normalA;
        final Vector3 normalB;
        final Vector3 normalC;
        final double lightA;
        final double lightB;
        final double lightC;
        final double flatLight;

        TriangleData(
                ScreenPoint screenA,
                ScreenPoint screenB,
                ScreenPoint screenC,
                Vector3 positionA,
                Vector3 positionB,
                Vector3 positionC,
                Vector3 normalA,
                Vector3 normalB,
                Vector3 normalC,
                double lightA,
                double lightB,
                double lightC,
                double flatLight
        ) {
            this.screenA = screenA;
            this.screenB = screenB;
            this.screenC = screenC;
            this.positionA = positionA;
            this.positionB = positionB;
            this.positionC = positionC;
            this.normalA = normalA;
            this.normalB = normalB;
            this.normalC = normalC;
            this.lightA = lightA;
            this.lightB = lightB;
            this.lightC = lightC;
            this.flatLight = flatLight;
        }

        boolean hasVertexBehindCamera() {
            return screenA.depth <= 0.05
                    || screenB.depth <= 0.05
                    || screenC.depth <= 0.05;
        }
    }
}
