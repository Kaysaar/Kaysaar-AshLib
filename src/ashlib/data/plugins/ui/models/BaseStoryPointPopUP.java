package ashlib.data.plugins.ui.models;

import ashlib.data.plugins.misc.AshMisc;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;

public class BaseStoryPointPopUP extends BasePopUpDialog {
    private final int cost;
    private final Runnable onConfirm;
    private LabelAPI balance;
    private int displayedPoints = -1;
    private boolean spent;
    public boolean closed;

    public BaseStoryPointPopUP(int cost, Runnable onConfirm) {
        super(null);
        if (cost < 1 || onConfirm == null) throw new IllegalArgumentException();
        this.cost = cost;
        this.onConfirm = onConfirm;
    }

    public void show() {
        AshMisc.initPopUpDialog(this, 590, 135);
    }

    @Override
    public void createUI(CustomPanelAPI panel) {
        TooltipMakerAPI text = panel.createUIElement(goalSizeX - 32, 65, false);
        text.setParaFont(Fonts.INSIGNIA_LARGE);
        String amount = cost == 1 ? "a story point" : cost + " story points";
        text.addPara("This action requires %s and does not grant any bonus experience.", 0f, Misc.getStoryBrightColor(), amount);
        addTooltip(text);
        panel.addUIElement(text).inTL(16, 16);

        TooltipMakerAPI points = panel.createUIElement(195, 25, false);
        balance = points.addPara("", 0f);
        addTooltip(points);
        panel.addUIElement(points).inTL(16, goalSizeY - 36);
        createConfirmAndCancelSection(panel);
        updateBalance();
    }

    @Override
    public ButtonAPI generateConfirmButton(TooltipMakerAPI tooltip) {
        confirmButton = tooltip.addButton("Confirm", "confirm", Misc.getStoryBrightColor(), Misc.getStoryDarkColor(),
                Alignment.MID, CutStyle.TL_BR, buttonConfirmWidth, 25, 0f);
        confirmButton.setShortcut(Keyboard.KEY_G, true);
        return confirmButton;
    }

    public void updateBalance() {
        int points = Global.getSector().getPlayerStats().getStoryPoints();
        if (points != displayedPoints) {
            displayedPoints = points;
            balance.setText("Story points: " + points);
            balance.setHighlight(String.valueOf(points));
            balance.setHighlightColor(Misc.getStoryBrightColor());
        }
        boolean enabled = points >= cost && !spent;
        if (confirmButton.isEnabled() != enabled) confirmButton.setEnabled(enabled);
    }

    @Override
    public void advance(float amount) {
        if (balance != null && !pressedConfirmCancel) updateBalance();
        super.advance(amount);
    }

    @Override
    public void applyConfirmScript() {
        MutableCharacterStatsAPI stats = Global.getSector().getPlayerStats();
        if (spent || stats.getStoryPoints() < cost) return;
        spent = true;
        stats.spendStoryPoints(cost, false, null, false, 0f, null);
        onConfirm.run();
    }

    @Override
    public void onExit() {
        closed = true;
    }
}
