package ashlib.data.plugins.ui.models.resizable.map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.combat.CombatViewport;
import com.fs.starfarer.combat.entities.terrain.Planet;
import data.kaysaar.aotd.vok.campaign.econ.conditions.NidavelirComplex;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

public class PlanetRenderResizableComponent extends MapEntityComponent {
    String planetType;
    float originalSize;
    boolean isStar;
    float angle = 0f;
    PlanetAPI planet;
    boolean discovered = true;


    private transient Planet cachedPlanetRenderer;
    private transient Planet cachedShipyardRenderer;
    private transient String cachedPlanetType;
    private transient String cachedShipyardType;
    private transient float cachedPlanetRadius;
    private transient float cachedShipyardRadius;


    public static void renderPlanet(SectorEntityToken token, String spec, Vector2f point,
                                    float size, float facing, float pitch, float surfaceAngle,
                                    float atmoAngle, float alpha, boolean isStar,
                                    float tilt, float scale) {
        renderPlanet(token, spec, point, size, facing, pitch, surfaceAngle,
                atmoAngle, alpha, isStar, tilt, scale, null);
    }

    public static void renderPlanet(SectorEntityToken token, String spec, Vector2f point,
                                    float size, float facing, float pitch, float surfaceAngle,
                                    float atmoAngle, float alpha, boolean isStar,
                                    float tilt, float scale, Color color) {
        Planet renderer = new Planet(spec, size, 0, point);
        renderExistingPlanet(renderer, point, size, facing, pitch, surfaceAngle,
                atmoAngle, alpha, tilt, scale, color);

        // Preserve the overlay behavior for external callers of the static helper.
        if (token != null
                && Global.getSettings().getModManager().isModEnabled("aotd_vok")
                && token.getMarket() != null
                && token.getMarket().hasCondition("aotd_nidavelir_complex")) {
            renderPlanet(
                    null,
                    NidavelirComplex.getComplexCondition(token.getMarket()).getShipyardVisual().type,
                    point,
                    size + 35f,
                    facing - 90f,
                    pitch,
                    0f,
                    surfaceAngle / 2f,
                    alpha,
                    isStar,
                    tilt,
                    scale,
                    color
            );
        }
    }

    /**
     * Applies current frame data to an existing combat Planet, then draws it.
     */
    private static void renderExistingPlanet(Planet renderer, Vector2f point, float size,
                                             float facing, float pitch, float surfaceAngle,
                                             float atmoAngle, float alpha,
                                             float tilt, float scale, Color color) {
        renderer.getLocation().set(point);
        renderer.setScale(scale);
        renderer.setRadius(size);
        renderer.setAngle(surfaceAngle);
        renderer.setCloudAngle(atmoAngle);
        renderer.setFacing(facing - 90f);
        renderer.setTilt(tilt);
        renderer.setPitch(pitch);
        if (color != null) {
            renderer.setLightColorOverride(color);
        }

        CombatViewport viewport = new CombatViewport(point.x, point.y, 200, 200);
        viewport.setAlphaMult(alpha);
        renderer.renderSphere(viewport);
        renderer.renderStarGlow(viewport);
    }

    public PlanetRenderResizableComponent(float size, String type, boolean isStar) {
        super(null);
        this.originalSize = size;
        this.planetType = type;
        this.isStar = isStar;
        this.componentPanel = Global.getSettings().createCustom(size * 2f, size * 2f, this);
    }

    public PlanetRenderResizableComponent(PlanetAPI reference, boolean discovered) {
        super(reference);
        this.originalSize = reference.getRadius();
        this.planet = reference;
        this.isStar = reference.isStar();
        this.discovered = discovered;
        this.componentPanel = Global.getSettings().createCustom(
                reference.getRadius() * 2f, reference.getRadius() * 2f, this);
    }

    @Override
    public void render(float alphaMult) {
        super.render(alphaMult);
        float cx = componentPanel.getPosition().getCenterX();
        float cy = componentPanel.getPosition().getCenterY();
        renderImpl(alphaMult, cx, cy);
    }

    private void renderImpl(float alphaMult, float cx, float cy) {
        if (!discovered && !isStar) {
            drawPlaceholderCircle(cx, cy, originalSize * scale, alphaMult);
            return;
        }

        if (planet == null) {
            // Safe defaults when the component was constructed from a type string.
            Planet renderer = getCachedRenderer(planetType, originalSize, cx, cy, false);
            renderExistingPlanet(renderer, renderer.getLocation(), originalSize,
                    90f, 0f, angle, 0f, alphaMult, 0f, scale, null);
            return;
        }

        Planet renderer = getCachedRenderer(planet.getTypeId(), originalSize, cx, cy, false);
        renderExistingPlanet(renderer, renderer.getLocation(), originalSize,
                planet.getFacing(), planet.getSpec().getPitch(), angle, angle / 2f,
                alphaMult, planet.getSpec().getTilt(), scale, null);

        // Draw the shipyard exactly once, reusing a separate cached renderer.
        if (Global.getSettings().getModManager().isModEnabled("aotd_vok")
                && planet.getMarket() != null
                && planet.getMarket().hasCondition("aotd_nidavelir_complex")) {
            String shipyardType = NidavelirComplex.getComplexCondition(planet.getMarket())
                    .getShipyardVisual().type;
            float shipyardSize = originalSize + 35f;
            Planet shipyardRenderer = getCachedRenderer(shipyardType, shipyardSize, cx, cy, true);
            renderExistingPlanet(shipyardRenderer, shipyardRenderer.getLocation(), shipyardSize,
                    planet.getFacing(), planet.getSpec().getPitch(), 0f, angle / 2f,
                    alphaMult, planet.getSpec().getTilt(), scale, null);
        }
    }

    /**
     * Separate transient caches for the planet surface and its optional shipyard.
     * The cache is recreated if the visual specification or radius changes.
     */
    private Planet getCachedRenderer(String spec, float radius, float cx, float cy,
                                     boolean shipyard) {
        Planet renderer = shipyard ? cachedShipyardRenderer : cachedPlanetRenderer;
        String cachedType = shipyard ? cachedShipyardType : cachedPlanetType;
        float cachedRadius = shipyard ? cachedShipyardRadius : cachedPlanetRadius;

        if (renderer == null || !spec.equals(cachedType) || radius != cachedRadius) {
            renderer = new Planet(spec, radius, 0, new Vector2f(cx, cy));
            if (shipyard) {
                cachedShipyardRenderer = renderer;
                cachedShipyardType = spec;
                cachedShipyardRadius = radius;
            } else {
                cachedPlanetRenderer = renderer;
                cachedPlanetType = spec;
                cachedPlanetRadius = radius;
            }
        } else {
            // The UI panel may move or resize; the cached renderer must follow it.
            renderer.getLocation().set(cx, cy);
        }

        return renderer;
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        float rot = (planet != null && planet.getSpec() != null)
                ? planet.getSpec().getRotation() : 10f;
        angle += rot * amount;
        if (angle > 360f) {
            angle -= 360f;
        }
    }

    public float getHaloRadius(PlanetAPI planet) {
        float rad = Math.max(
                planet.getRadius() * planet.getSpec().getAtmosphereThickness(),
                planet.getSpec().getAtmosphereThicknessMin() + planet.getRadius());
        if (planet.isStar()) {
            rad = planet.getRadius() * planet.getSpec().getCoronaSize();
        }
        return rad;
    }

    /** Draws a filled gray circle for undiscovered non-star planets. */
    private void drawPlaceholderCircle(float cx, float cy, float radius, float alphaMult) {
        if (radius <= 0f) return;

        final Color fill = new Color(120, 120, 120, 220);
        final Color stroke = new Color(90, 90, 90, 255);
        int segments = Math.max(24, Math.min(256,
                (int) (2 * Math.PI * radius / 6f)));

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LINE_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            float af = (fill.getAlpha() / 255f) * alphaMult;
            GL11.glColor4f(fill.getRed() / 255f, fill.getGreen() / 255f,
                    fill.getBlue() / 255f, af);

            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glVertex2f(cx, cy);
            for (int i = 0; i <= segments; i++) {
                double t = i * (2.0 * Math.PI / segments);
                GL11.glVertex2f(
                        cx + (float) Math.cos(t) * radius,
                        cy + (float) Math.sin(t) * radius);
            }
            GL11.glEnd();

            float ao = (stroke.getAlpha() / 255f) * alphaMult;
            GL11.glColor4f(stroke.getRed() / 255f, stroke.getGreen() / 255f,
                    stroke.getBlue() / 255f, ao);
            GL11.glLineWidth(Math.max(1f, 1f * scale));
            GL11.glBegin(GL11.GL_LINE_LOOP);
            for (int i = 0; i < segments; i++) {
                double t = i * (2.0 * Math.PI / segments);
                GL11.glVertex2f(
                        cx + (float) Math.cos(t) * radius,
                        cy + (float) Math.sin(t) * radius);
            }
            GL11.glEnd();
        } finally {
            GL11.glPopAttrib();
        }
    }

    public PlanetAPI getPlanet() {
        return planet;
    }
}
