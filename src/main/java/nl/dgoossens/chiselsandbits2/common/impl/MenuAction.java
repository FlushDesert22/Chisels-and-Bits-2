package nl.dgoossens.chiselsandbits2.common.impl;

import net.minecraft.util.Direction;
import nl.dgoossens.chiselsandbits2.ChiselsAndBits2;
import nl.dgoossens.chiselsandbits2.api.item.IMenuAction;
import nl.dgoossens.chiselsandbits2.common.network.client.CRotateItemPacket;
import nl.dgoossens.chiselsandbits2.common.utils.ClientItemPropertyUtil;
import nl.dgoossens.chiselsandbits2.common.utils.ItemPropertyUtil;

public enum MenuAction implements IMenuAction {
    //General
    UNDO,
    REDO,

    //Chisel
    PLACE,
    SWAP,

    //Chiseled Block / Pattern
    ROLL_X,
    ROLL_Z,
    ;

    @Override
    public boolean hasIcon() {
        return true;
    }

    @Override
    public boolean hasHotkey() {
        return true;
    }

    @Override
    public void trigger() {
        switch (this) {
            case UNDO:
                ChiselsAndBits2.getInstance().getUndoTracker().undo();
                break;
            case REDO:
                ChiselsAndBits2.getInstance().getUndoTracker().redo();
                break;
            case ROLL_X:
                ChiselsAndBits2.getInstance().getNetworkRouter().sendToServer(new CRotateItemPacket(Direction.Axis.X));
                break;
            case ROLL_Z:
                ChiselsAndBits2.getInstance().getNetworkRouter().sendToServer(new CRotateItemPacket(Direction.Axis.Z));
                break;
            case PLACE:
            case SWAP:
                ClientItemPropertyUtil.setItemState(this.equals(MenuAction.PLACE));
                break;
        }
    }
}
