/** Доступные способы вычисления яркости внутри треугольника. */
public enum ShadingMode {
    FLAT("Плоская"),
    GOURAUD("Гуро"),
    PHONG("Фонг");

    private final String title;

    ShadingMode(String title) {
        this.title = title;
    }

    @Override
    public String toString() {
        return title;
    }
}
